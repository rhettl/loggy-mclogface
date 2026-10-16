package io.github.rhettl.loggy.internal;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportTextTest {
    @Test void fitKeepsHeadingMissingLogNoticeAndRespectsSmallBudgets() {
        String diagnostics = "Loggy McLogface server report\nStatus: WARN\n" + "診断🙂".repeat(300) + "\n";
        String result = ReportText.fit(diagnostics, "Missing or unreadable latest.log", "line 1\nline 2\nline 3", 512, 8);

        assertTrue(result.startsWith("Loggy McLogface server report"));
        assertTrue(result.contains("Missing or unreadable latest.log"));
        assertTrue(result.getBytes(StandardCharsets.UTF_8).length <= 512);
        assertTrue(result.split("\n", -1).length <= 8);
    }

    @Test void postFilterExpansionIsReboundedAtCodePointAndLineBoundaries() {
        String expanded = "Loggy McLogface server report\n## Current log\nsource note\n" + "🙂".repeat(500);
        String result = ReportText.enforceFiltered(expanded, 256, 6);

        assertTrue(result.startsWith("Loggy McLogface server report"));
        assertTrue(result.contains("## Current log"));
        assertTrue(result.contains("source note"));
        assertTrue(result.contains("shortened after redaction"));
        assertTrue(result.getBytes(StandardCharsets.UTF_8).length <= 256);
        assertTrue(result.split("\n", -1).length <= 6);
        assertFalse(result.endsWith("\n"));
    }

    @Test void formatterDerivesErrorStatusAndKeepsProviderEvidence() {
        var report = new ReportCollection("crests", "dedicated server", Instant.parse("2026-01-01T00:00:00Z"), List.of(
                new ReportCollection.ProviderResult("minecraft", "Minecraft", List.of("### Environment", "Java: 21"), List.of(), "OK"),
                new ReportCollection.ProviderResult("crests", "Crests", List.of("### Shops", "Count: 2"), List.of("ERROR: invalid shop"), "ERROR")));
        String content = ReportText.diagnostics(report, true);
        assertTrue(content.contains("Status: ERROR"));
        assertTrue(content.contains("Count: 2"));
    }
}
