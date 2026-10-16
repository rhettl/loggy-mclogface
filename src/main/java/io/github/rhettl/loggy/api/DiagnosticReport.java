package io.github.rhettl.loggy.api;

import java.util.List;

/** Immutable plain-text evidence. Loggy derives status from findings. */
public record DiagnosticReport(List<DiagnosticSection> sections, List<DiagnosticFinding> findings) {
    public DiagnosticReport {
        sections = List.copyOf(sections);
        findings = List.copyOf(findings);
    }
}
