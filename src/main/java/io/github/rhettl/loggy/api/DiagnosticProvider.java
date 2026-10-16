package io.github.rhettl.loggy.api;

/** A provider of read-only server diagnostics, registered during common initialization. */
public interface DiagnosticProvider {
    String id();
    String displayName();
    String description();

    /** Runs on the server thread; must not block, perform I/O, mutate state, or retain context. */
    DiagnosticReport collect(DiagnosticContext context);
}
