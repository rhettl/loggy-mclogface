package io.github.rhettl.loggy.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

final class ReportPersistence {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private ReportPersistence() {}

    static SavedReport save(Path serverRoot, Instant time, String content) throws IOException {
        Path directory = serverRoot.resolve("logs").resolve("loggy");
        Files.createDirectories(directory);
        Path file = directory.resolve("report-" + STAMP.format(time) + "-" + UUID.randomUUID() + ".txt");
        Files.writeString(file, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        retainNewestTen(directory);
        return new SavedReport(serverRoot.relativize(file), file);
    }

    private static void retainNewestTen(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path old : paths.filter(p -> p.getFileName().toString().startsWith("report-")
                            && p.getFileName().toString().endsWith(".txt"))
                    .sorted(Comparator.comparingLong(ReportPersistence::modified).reversed()).skip(10).toList()) {
                Files.deleteIfExists(old);
            }
        }
    }

    private static long modified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException ignored) { return 0; }
    }

    record SavedReport(Path relativePath, Path absolutePath) {}
}
