package io.github.rhettl.loggy.api;

import java.util.Objects;

/** A safe, actionable finding; exclude secrets and raw exception messages. */
public record DiagnosticFinding(Severity severity, String message) {
    public DiagnosticFinding {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        if (message.isBlank()) throw new IllegalArgumentException("Finding message must not be blank");
    }
}
