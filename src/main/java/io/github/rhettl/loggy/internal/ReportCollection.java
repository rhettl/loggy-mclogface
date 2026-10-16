package io.github.rhettl.loggy.internal;

import java.time.Instant;
import java.util.List;

/** Immutable snapshot safe to hand to the report worker. */
record ReportCollection(String target, String side, Instant collectedAt, List<ProviderResult> providers) {
    ReportCollection { providers = List.copyOf(providers); }

    record ProviderResult(String id, String name, List<String> sections, List<String> findings, String status) {
        ProviderResult {
            sections = List.copyOf(sections);
            findings = List.copyOf(findings);
        }
    }
}
