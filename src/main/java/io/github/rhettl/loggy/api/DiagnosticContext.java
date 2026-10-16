package io.github.rhettl.loggy.api;

import java.time.Instant;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;

/** Server-thread access for one collection; do not retain this context. */
public record DiagnosticContext(MinecraftServer server, Instant collectedAt) {
    public DiagnosticContext {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(collectedAt, "collectedAt");
    }
}
