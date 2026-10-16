package io.github.rhettl.loggy.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticReportTest {
    @Test void copiesAllListsAndExposesImmutableEvidence() {
        var lines = new ArrayList<>(List.of("Enabled: true", ""));
        var section = new DiagnosticSection("Config", lines);
        var sections = new ArrayList<>(List.of(section));
        var findings = new ArrayList<>(List.of(new DiagnosticFinding(Severity.WARN, "Missing optional setting")));
        var report = new DiagnosticReport(sections, findings);
        lines.clear(); sections.clear(); findings.clear();
        assertEquals(2, report.sections().getFirst().lines().size());
        assertEquals(1, report.findings().size());
        assertThrows(UnsupportedOperationException.class, () -> section.lines().clear());
        assertThrows(UnsupportedOperationException.class, () -> report.sections().clear());
        assertThrows(UnsupportedOperationException.class, () -> report.findings().clear());
    }

    @Test void rejectsMissingOrBlankEvidence() {
        assertThrows(NullPointerException.class, () -> new DiagnosticReport(null, List.of()));
        assertThrows(NullPointerException.class, () -> new DiagnosticReport(List.of(), null));
        assertThrows(NullPointerException.class, () -> new DiagnosticReport(Arrays.asList((DiagnosticSection)null), List.of()));
        assertThrows(NullPointerException.class, () -> new DiagnosticSection("Config", Arrays.asList((String)null)));
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticSection(" ", List.of()));
        assertThrows(NullPointerException.class, () -> new DiagnosticFinding(null, "message"));
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticFinding(Severity.ERROR, " "));
    }
}
