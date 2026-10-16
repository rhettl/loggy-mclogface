package io.github.rhettl.loggy.api;

import java.util.List;
import java.util.Objects;

/** An immutable group of plain-text lines; empty lines are allowed. */
public record DiagnosticSection(String title, List<String> lines) {
    public DiagnosticSection {
        Objects.requireNonNull(title, "title");
        if (title.isBlank()) throw new IllegalArgumentException("Section title must not be blank");
        lines = List.copyOf(lines);
    }
}
