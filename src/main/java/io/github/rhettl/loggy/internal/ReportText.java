package io.github.rhettl.loggy.internal;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Plain-text serialization and deterministic UTF-8/line budgeting. */
final class ReportText {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);
    private static final String HEADING = "Loggy McLogface server report";
    private static final String LOG_HEADING = "## Current log";
    private ReportText() {}

    static String diagnostics(ReportCollection report, boolean logAvailable) {
        StringBuilder out = new StringBuilder(HEADING).append('\n');
        out.append("Target: ").append(report.target()).append("\nSide: ").append(report.side())
                .append("\nCollected: ").append(TIME.format(report.collectedAt())).append('\n');
        String overall = report.providers().stream().anyMatch(p -> p.status().equals("ERROR")) ? "ERROR"
                : !logAvailable || report.providers().stream().anyMatch(p -> p.status().equals("WARN")) ? "WARN" : "OK";
        out.append("Status: ").append(overall).append("\n\n");
        for (ReportCollection.ProviderResult provider : report.providers()) {
            out.append("## ").append(provider.name()).append(" [").append(provider.id()).append("] — ")
                    .append(provider.status()).append('\n');
            provider.sections().forEach(line -> out.append(line).append('\n'));
            if (!provider.findings().isEmpty()) {
                out.append("Findings:\n");
                provider.findings().forEach(line -> out.append("- ").append(line).append('\n'));
            }
            out.append('\n');
        }
        if (!logAvailable) out.append("## Current log availability\nWARN: latest.log is missing or unreadable; provider diagnostics are retained.\n\n");
        return out.toString();
    }

    static String fit(String diagnostics, String logNote, String log, int byteLimit, int lineLimit) {
        if (byteLimit <= 0 || lineLimit <= 0) throw new IllegalArgumentException("mclo.gs limits must be positive");
        List<String> diagLines = new ArrayList<>(List.of(diagnostics.split("\n", -1)));
        if (diagLines.size() > 1 && diagLines.get(diagLines.size() - 1).isEmpty()) diagLines.remove(diagLines.size() - 1);
        List<String> output = new ArrayList<>();
        int usedBytes = 0;
        int maxLines = lineLimit;

        // The heading is a format invariant; truncate it only when the service limit itself is smaller.
        String heading = fitLine(HEADING, byteLimit);
        usedBytes = add(output, usedBytes, heading, byteLimit, maxLines);
        boolean diagnosticOmitted = false;
        for (int i = 1; i < diagLines.size(); i++) {
            String line = diagLines.get(i);
            if (line.isEmpty()) line = "";
            if (!canAdd(line, output.size(), usedBytes, byteLimit, maxLines)) {
                diagnosticOmitted = true;
                break;
            }
            usedBytes = add(output, usedBytes, line, byteLimit, maxLines);
        }

        String logTitle = fitLine(LOG_HEADING, byteLimit);
        String note = fitLine(logNote, byteLimit);
        String omission = "[Earlier log lines omitted to fit service limits]";
        // Keep the required log context visible even when diagnostics alone nearly fill the allowance.
        while (!canReserveLog(output, usedBytes, logTitle, note, omission, byteLimit, maxLines)) {
            if (output.size() <= 1) break;
            usedBytes -= utf8(output.remove(output.size() - 1)) + 1;
            diagnosticOmitted = true;
        }
        if (diagnosticOmitted && canAdd("[Diagnostic sections shortened to fit service limits]", output.size(),
                usedBytes, byteLimit, maxLines)) {
            usedBytes = add(output, usedBytes, "[Diagnostic sections shortened to fit service limits]", byteLimit, maxLines);
        }
        if (canAdd(logTitle, output.size(), usedBytes, byteLimit, maxLines)) usedBytes = add(output, usedBytes, logTitle, byteLimit, maxLines);
        if (canAdd(note, output.size(), usedBytes, byteLimit, maxLines)) usedBytes = add(output, usedBytes, note, byteLimit, maxLines);

        String[] logLines = log.split("\n", -1);
        int realLogLines = logLines.length > 0 && logLines[logLines.length - 1].isEmpty() ? logLines.length - 1 : logLines.length;
        List<String> tail = new ArrayList<>();
        int tailBytes = 0;
        for (int i = realLogLines - 1; i >= 0; i--) {
            String line = logLines[i];
            int bytes = utf8(line) + 1;
            int extraLines = tail.size() + 1;
            if (output.size() + extraLines > maxLines || usedBytes + tailBytes + bytes > byteLimit) break;
            tail.add(line);
            tailBytes += bytes;
        }
        Collections.reverse(tail);
        boolean logOmitted = tail.size() < realLogLines;
        if (logOmitted) {
            int firstTail = 0;
            while (firstTail < tail.size() && (output.size() + tail.size() - firstTail + 1 > maxLines
                    || usedBytes + tailBytes + utf8(omission) + 1 > byteLimit)) {
                tailBytes -= utf8(tail.get(firstTail++)) + 1;
            }
            if (canAdd(omission, output.size(), usedBytes, byteLimit, maxLines)
                    && output.size() + 1 + tail.size() - firstTail <= maxLines
                    && usedBytes + utf8(omission) + 1 + tailBytes <= byteLimit) {
                usedBytes = add(output, usedBytes, omission, byteLimit, maxLines);
            } else {
                firstTail = tail.size();
                tailBytes = 0;
            }
            for (int i = firstTail; i < tail.size(); i++) {
                String line = tail.get(i);
                if (!canAdd(line, output.size(), usedBytes, byteLimit, maxLines)) break;
                usedBytes = add(output, usedBytes, line, byteLimit, maxLines);
            }
            return String.join("\n", output);
        }
        for (String line : tail) {
            if (!canAdd(line, output.size(), usedBytes, byteLimit, maxLines)) break;
            usedBytes = add(output, usedBytes, line, byteLimit, maxLines);
        }
        return String.join("\n", output);
    }

    static String enforceFiltered(String content, int byteLimit, int lineLimit) {
        if (utf8(content) <= byteLimit && lineCount(content) <= lineLimit) return content;
        List<String> lines = new ArrayList<>(List.of(content.split("\n", -1)));
        if (lines.size() > 1 && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
        int logIndex = lines.indexOf(LOG_HEADING);
        boolean omitted = false;
        while ((bytesWithNewlines(lines) > byteLimit || lines.size() > lineLimit) && logIndex >= 0
                && lines.size() > logIndex + 2) {
            lines.remove(logIndex + 2); // preserve log note and the most recent log tail
            omitted = true;
        }
        while ((bytesWithNewlines(lines) > byteLimit || lines.size() > lineLimit) && lines.size() > 1) {
            int from = logIndex >= 0 && logIndex + 2 < lines.size() ? logIndex + 2 : lines.size() - 1;
            lines.remove(from);
            if (logIndex >= 0 && from < logIndex) logIndex--;
            omitted = true;
        }
        String notice = "[Content shortened after redaction to fit service limits]";
        if (omitted) {
            while (lines.size() > 1 && (lines.size() + 1 > lineLimit
                    || bytesWithNewlines(lines) + utf8(notice) + 1 > byteLimit)) {
                int from = logIndex >= 0 && logIndex + 2 < lines.size() ? logIndex + 2 : lines.size() - 1;
                lines.remove(from);
                if (logIndex >= 0 && from < logIndex) logIndex--;
            }
            if (lines.size() < lineLimit && bytesWithNewlines(lines) + utf8(notice) + 1 <= byteLimit) lines.add(notice);
        }
        if (!lines.isEmpty() && utf8(lines.get(0)) > byteLimit) lines.set(0, fitLine(lines.get(0), byteLimit));
        return String.join("\n", lines);
    }

    private static boolean canReserveLog(List<String> output, int bytes, String title, String note, String omission,
                                         int byteLimit, int lineLimit) {
        return output.size() + 3 <= lineLimit
                && bytes + utf8(title) + utf8(note) + utf8(omission) + 3 <= byteLimit;
    }
    private static boolean canAdd(String line, int lineCount, int bytes, int byteLimit, int lineLimit) {
        return lineCount < lineLimit && bytes + utf8(line) + 1 <= byteLimit;
    }
    private static int add(List<String> lines, int usedBytes, String line, int byteLimit, int lineLimit) {
        if (!canAdd(line, lines.size(), usedBytes, byteLimit, lineLimit)) return usedBytes;
        lines.add(line);
        return usedBytes + utf8(line) + 1;
    }
    private static String fitLine(String line, int byteLimit) {
        if (utf8(line) <= byteLimit) return line;
        StringBuilder out = new StringBuilder();
        int used = 0;
        for (int i = 0; i < line.length();) {
            int cp = line.codePointAt(i);
            String next = new String(Character.toChars(cp));
            if (used + utf8(next) > byteLimit) break;
            out.append(next);
            used += utf8(next);
            i += Character.charCount(cp);
        }
        return out.toString();
    }
    private static int utf8(String text) { return text.getBytes(StandardCharsets.UTF_8).length; }
    private static int lineCount(String text) { return text.isEmpty() ? 0 : text.split("\n", -1).length; }
    private static int bytesWithNewlines(List<String> lines) {
        int bytes = Math.max(0, lines.size() - 1);
        for (String line : lines) bytes += utf8(line);
        return bytes;
    }
}
