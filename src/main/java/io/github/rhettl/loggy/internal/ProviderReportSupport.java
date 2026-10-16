package io.github.rhettl.loggy.internal;

import io.github.rhettl.loggy.api.DiagnosticFinding;
import io.github.rhettl.loggy.api.DiagnosticReport;
import io.github.rhettl.loggy.api.DiagnosticSection;
import io.github.rhettl.loggy.api.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/** Pure selection/status helpers plus failure isolation for one provider call. */
final class ProviderReportSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger("Loggy");
    private ProviderReportSupport() {}

    static List<ProviderRegistry.Registration> select(String target, List<ProviderRegistry.Registration> providers) {
        if (target.equals("minecraft")) return List.of();
        return providers.stream().filter(p -> target.equals("all") || p.id().equals(target))
                .sorted(Comparator.comparing(ProviderRegistry.Registration::id)).toList();
    }

    static String status(List<DiagnosticFinding> findings) {
        if (findings.stream().anyMatch(f -> f.severity() == Severity.ERROR)) return "ERROR";
        if (findings.stream().anyMatch(f -> f.severity() == Severity.WARN)) return "WARN";
        return "OK";
    }

    static ReportCollection.ProviderResult isolate(String id, String name, Supplier<DiagnosticReport> collect) {
        try {
            DiagnosticReport report = collect.get();
            if (report == null || report.sections() == null || report.findings() == null
                    || report.sections().stream().anyMatch(java.util.Objects::isNull)
                    || report.findings().stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("Provider returned an invalid report");
            }
            List<String> sections = new ArrayList<>();
            for (DiagnosticSection section : report.sections()) {
                sections.add("### " + section.title());
                sections.addAll(section.lines());
            }
            List<String> findings = report.findings().stream()
                    .map(f -> f.severity().name() + ": " + f.message()).toList();
            return new ReportCollection.ProviderResult(id, name, sections, findings, status(report.findings()));
        } catch (RuntimeException failure) {
            LOGGER.error("Loggy diagnostic provider '{}' failed", id, failure);
            return new ReportCollection.ProviderResult(id, name,
                    List.of("Collection failed; see the local server log for provider ID " + id + "."),
                    List.of("Provider collection failed."), "ERROR");
        }
    }
}
