package io.github.rhettl.loggy.internal;

import io.github.rhettl.loggy.api.DiagnosticContext;
import io.github.rhettl.loggy.api.Loggy;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.Pack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Collects each requested provider exactly once on the Minecraft server thread. */
final class ReportCollector {
    private static final Logger LOGGER = LoggerFactory.getLogger("Loggy");
    private ReportCollector() {}

    static ReportCollection collect(MinecraftServer server, String target) {
        Instant timestamp = Instant.now();
        List<ReportCollection.ProviderResult> results = new ArrayList<>();
        List<ProviderRegistry.Registration> registrations = Loggy.registry().snapshot();
        try {
            results.add(builtIn(server));
        } catch (RuntimeException failure) {
            LOGGER.error("Loggy built-in Minecraft diagnostics failed", failure);
            results.add(new ReportCollection.ProviderResult("minecraft", "Minecraft and environment",
                    List.of("Built-in collection failed; see the local server log."),
                    List.of("ERROR: Built-in diagnostic collection failed."), "ERROR"));
        }
        for (ProviderRegistry.Registration registration : ProviderReportSupport.select(target, registrations)) {
            long started = System.nanoTime();
            try {
                results.add(ProviderReportSupport.isolate(registration.id(), registration.displayName(),
                        () -> registration.provider().collect(new DiagnosticContext(server, timestamp))));
            } finally {
                long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
                if (elapsedMs > 250) LOGGER.warn("Loggy provider '{}' took {} ms on the server thread", registration.id(), elapsedMs);
            }
        }
        results.sort(java.util.Comparator.comparing(ReportCollection.ProviderResult::id));
        return new ReportCollection(target, server.isDedicatedServer() ? "dedicated server" : "integrated server",
                timestamp, results);
    }

    private static ReportCollection.ProviderResult builtIn(MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        List<String> findings = new ArrayList<>();
        lines.add("Minecraft: " + SharedConstants.getCurrentVersion().getName());
        lines.add("Fabric Loader: " + FabricLoader.getInstance().getModContainer("fabricloader")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown"));
        lines.add("Loggy: " + FabricLoader.getInstance().getModContainer("loggy")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown"));
        lines.add("Java: " + Runtime.version().toString() + " (" + System.getProperty("java.vendor", "unknown") + ")");
        lines.add("OS: " + System.getProperty("os.name", "unknown") + " / " + System.getProperty("os.arch", "unknown"));
        lines.add("Available processors: " + Runtime.getRuntime().availableProcessors());
        Runtime runtime = Runtime.getRuntime();
        lines.add("JVM memory bytes (used / committed / max): " + (runtime.totalMemory() - runtime.freeMemory()) + " / "
                + runtime.totalMemory() + " / " + runtime.maxMemory());
        lines.add("Loaded mods:");
        FabricLoader.getInstance().getAllMods().stream()
                .sorted(java.util.Comparator.comparing(m -> m.getMetadata().getId()))
                .forEach(mod -> lines.add("- " + mod.getMetadata().getId() + " | " + mod.getMetadata().getName()
                        + " | " + mod.getMetadata().getVersion().getFriendlyString()));
        lines.add("Enabled datapacks:");
        for (Pack pack : server.getPackRepository().getSelectedPacks()) {
            lines.add("- " + pack.getId() + " | " + pack.getTitle().getString());
        }
        if (server.getPackRepository().getSelectedPacks().isEmpty()) findings.add("INFO: No enabled datapacks were reported.");
        List<String> section = new ArrayList<>();
        section.add("### Environment");
        section.addAll(lines);
        return new ReportCollection.ProviderResult("minecraft", "Minecraft and environment",
                section, findings, "OK");
    }
}
