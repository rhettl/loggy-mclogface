package io.github.rhettl.loggy.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportPersistenceTest {
    @TempDir Path root;

    @Test void missingCurrentLogBecomesExplicitWarnEvidence() {
        var snapshot = CurrentLogSnapshot.read(root, false);
        assertFalse(snapshot.available());
        assertTrue(snapshot.note().contains("shared integrated process log"));
        assertTrue(snapshot.note().contains("could not be read"));
        var collection = new ReportCollection("minecraft", "integrated server", Instant.EPOCH, List.of());
        String report = ReportText.diagnostics(collection, snapshot.available());
        assertTrue(report.contains("Status: WARN"));
        assertTrue(report.contains("WARN: latest.log is missing or unreadable"));
    }

    @Test void savesPreparedReportsAndRetainsOnlyTheNewestTen() throws Exception {
        for (int i = 0; i < 12; i++) {
            ReportPersistence.save(root, Instant.ofEpochMilli(i), "Loggy McLogface server report\nreport=" + i);
        }
        Path directory = root.resolve("logs/loggy");
        try (var entries = Files.list(directory)) {
            List<Path> reports = entries.toList();
            assertEquals(10, reports.size());
            assertTrue(reports.stream().allMatch(p -> p.getFileName().toString().endsWith(".txt")));
        }
        assertFalse(Files.exists(root.resolve("logs/latest.log")));
    }
}
