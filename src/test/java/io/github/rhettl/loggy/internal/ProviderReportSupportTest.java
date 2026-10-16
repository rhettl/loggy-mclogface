package io.github.rhettl.loggy.internal;

import io.github.rhettl.loggy.api.DiagnosticFinding;
import io.github.rhettl.loggy.api.DiagnosticProvider;
import io.github.rhettl.loggy.api.DiagnosticReport;
import io.github.rhettl.loggy.api.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProviderReportSupportTest {
    private static ProviderRegistry.Registration registration(String id) {
        DiagnosticProvider provider = new DiagnosticProvider() {
            public String id() { return id; }
            public String displayName() { return id; }
            public String description() { return "test"; }
            public DiagnosticReport collect(io.github.rhettl.loggy.api.DiagnosticContext context) { return new DiagnosticReport(List.of(), List.of()); }
        };
        return new ProviderRegistry.Registration(id, id, "test", provider);
    }

    @Test void selectsOnlyRequestedProviderOrAllInIdOrder() {
        var providers = List.of(registration("zeta"), registration("alpha"));
        assertEquals(List.of("zeta"), ProviderReportSupport.select("zeta", providers).stream().map(ProviderRegistry.Registration::id).toList());
        assertEquals(List.of("alpha", "zeta"), ProviderReportSupport.select("all", providers).stream().map(ProviderRegistry.Registration::id).toList());
        assertTrue(ProviderReportSupport.select("minecraft", providers).isEmpty());
        assertTrue(ProviderReportSupport.select("unknown", providers).isEmpty());
    }

    @Test void derivesSeverityAndIsolatesFailuresWithoutPublishingExceptionText() {
        assertEquals("OK", ProviderReportSupport.status(List.of(new DiagnosticFinding(Severity.INFO, "informational"))));
        assertEquals("WARN", ProviderReportSupport.status(List.of(new DiagnosticFinding(Severity.INFO, "info"), new DiagnosticFinding(Severity.WARN, "warning"))));
        assertEquals("ERROR", ProviderReportSupport.status(List.of(new DiagnosticFinding(Severity.ERROR, "error"))));

        var failed = ProviderReportSupport.isolate("sample", "Sample", () -> { throw new IllegalStateException("sensitive exception text"); });
        assertEquals("ERROR", failed.status());
        assertTrue(failed.findings().getFirst().contains("Provider collection failed"));
        assertFalse(String.join(" ", failed.sections()).contains("sensitive exception text"));
    }
}
