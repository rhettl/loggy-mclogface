package io.github.rhettl.loggy.internal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Permission-gated server commands and isolated report workflow. */
public final class LoggyCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("Loggy");
    private static final Set<MinecraftServer> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Set<MinecraftServer> STOPPED = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "Loggy report worker");
        thread.setDaemon(true);
        return thread;
    });

    private LoggyCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("loggy").requires(source -> source.hasPermission(2))
                .executes(context -> usage(context.getSource()))
                .then(literal("list").executes(context -> list(context.getSource())))
                .then(literal("report").then(argument("target", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(targetIds(), builder))
                        .executes(context -> report(context.getSource(), StringArgumentType.getString(context, "target"))))));
    }

    public static void serverStopping(MinecraftServer server) {
        STOPPED.add(server);
        ACTIVE.remove(server);
    }

    private static List<String> targetIds() {
        List<String> ids = new ArrayList<>();
        ids.add("minecraft");
        LoggyAccess.providers().stream().map(ProviderRegistry.Registration::id).forEach(ids::add);
        ids.add("all");
        return ids;
    }

    private static int usage(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Usage: /loggy list | /loggy report <minecraft|target|all>"), false);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Loggy report targets:"), false);
        source.sendSuccess(() -> Component.literal("minecraft — Minecraft and environment diagnostics"), false);
        for (ProviderRegistry.Registration provider : LoggyAccess.providers()) {
            source.sendSuccess(() -> Component.literal(provider.id() + " — " + provider.displayName() + ": " + provider.description()), false);
        }
        source.sendSuccess(() -> Component.literal("all — Minecraft diagnostics and every registered provider"), false);
        return 1;
    }

    private static int report(CommandSourceStack source, String target) {
        MinecraftServer server = source.getServer();
        if (!target.equals("minecraft") && !target.equals("all")
                && LoggyAccess.providers().stream().noneMatch(provider -> provider.id().equals(target))) {
            source.sendFailure(Component.literal("Unknown Loggy report target: " + target));
            return 0;
        }
        if (!ACTIVE.add(server)) {
            source.sendFailure(Component.literal("A Loggy report is already in progress for this server."));
            return 0;
        }
        if (STOPPED.contains(server)) {
            ACTIVE.remove(server);
            source.sendFailure(Component.literal("This server is stopping; no report was started."));
            return 0;
        }

        ReportCollection collection;
        try {
            collection = ReportCollector.collect(server, target);
        } catch (RuntimeException failure) {
            ACTIVE.remove(server);
            LOGGER.error("Could not collect Loggy report for target {}", target, failure);
            source.sendFailure(Component.literal("Loggy could not collect the report. See the local server log."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Collecting diagnostics and preparing the report…"), false);
        CompletableFuture.runAsync(() -> prepareAndUpload(server, source, collection), WORKERS)
                .exceptionally(failure -> {
                    LOGGER.error("Loggy report worker failed", failure);
                    complete(server, source, "Loggy report failed before upload. See the local server log.");
                    return null;
                });
        return 1;
    }

    private static void prepareAndUpload(MinecraftServer server, CommandSourceStack source, ReportCollection collection) {
        try {
            CurrentLogSnapshot.Snapshot log = CurrentLogSnapshot.read(server.getServerDirectory(), server.isDedicatedServer());
            MclogsGateway.ReportDraft draft = new MclogsGateway.ReportDraft(
                    ReportText.diagnostics(collection, log.available()), log.note(), log.content());
            MclogsGateway.prepare(draft).whenCompleteAsync((lease, preparationFailure) -> {
                try {
                    ReportPersistence.SavedReport saved = null;
                    if (preparationFailure != null) {
                        String fallback = ReportText.fit(draft.diagnostics(), draft.logNote(), draft.log(), 5 * 1024 * 1024, 5_000);
                        fallback = LocalRedactor.redact(fallback);
                        try { saved = ReportPersistence.save(server.getServerDirectory(), collection.collectedAt(), fallback); }
                        catch (Exception saveFailure) { LOGGER.warn("Could not save locally prepared Loggy report", saveFailure); }
                        String location = saved == null ? " Local saving also failed." :
                                " Saved report: " + saved.relativePath().toString().replace('\\', '/');
                        String reason = saved == null ? "Local saving failed and mclo.gs filters or limits could not be verified; upload was skipped."
                                : "Report saved locally; mclo.gs filters or limits could not be verified, so upload was skipped.";
                        complete(server, source, reason + location);
                        return;
                    }

                    try { saved = ReportPersistence.save(server.getServerDirectory(), collection.collectedAt(), lease.content()); }
                    catch (Exception saveFailure) { LOGGER.warn("Could not save prepared Loggy report", saveFailure); }
                    ReportPersistence.SavedReport savedReport = saved;
                    lease.upload().whenCompleteAsync((url, uploadFailure) -> {
                        if (uploadFailure == null) {
                            String path = savedReport == null ? " Local saving failed." :
                                    " Local copy: " + savedReport.relativePath().toString().replace('\\', '/');
                            completeUpload(server, source, url, path);
                        } else {
                            String reason = unwrap(uploadFailure) instanceof TimeoutException ? "The mclo.gs request timed out." :
                                    "mclo.gs could not accept the report.";
                            String path = savedReport == null ? " Local saving also failed." :
                                    " Prepared report saved at " + savedReport.relativePath().toString().replace('\\', '/') + ".";
                            complete(server, source, reason + path);
                        }
                    }, WORKERS).exceptionally(failure -> {
                        LOGGER.error("Loggy upload completion callback failed", failure);
                        complete(server, source, "Loggy upload completion failed. See the local server log.");
                        return null;
                    });
                } catch (RuntimeException callbackFailure) {
                    if (lease != null) lease.close();
                    LOGGER.error("Could not prepare Loggy report persistence", callbackFailure);
                    complete(server, source, "Loggy could not prepare the local report. See the local server log.");
                }
            }, WORKERS).exceptionally(failure -> {
                LOGGER.error("Loggy filter preparation callback failed", failure);
                complete(server, source, "Loggy could not prepare the report. See the local server log.");
                return null;
            });
        } catch (RuntimeException failure) {
            LOGGER.error("Loggy report worker failed", failure);
            complete(server, source, "Loggy report failed before upload. See the local server log.");
        }
    }

    private static Throwable unwrap(Throwable error) {
        while (error.getCause() != null && (error instanceof java.util.concurrent.CompletionException
                || error instanceof java.util.concurrent.ExecutionException)) error = error.getCause();
        return error;
    }

    private static void complete(MinecraftServer server, CommandSourceStack source, String message) {
        dispatch(server, source, () -> Component.literal(message));
    }

    private static void completeUpload(MinecraftServer server, CommandSourceStack source, String url, String path) {
        dispatch(server, source, () -> Component.empty()
                .append(Component.literal("Loggy report uploaded: "))
                .append(Component.literal(url).withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Open report")))))
                .append(Component.literal(" "))
                .append(Component.literal("[copy]").withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, url))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Copy report link")))))
                .append(Component.literal(path)));
    }

    private static void dispatch(MinecraftServer server, CommandSourceStack source,
                                 java.util.function.Supplier<Component> message) {
        if (STOPPED.contains(server)) { ACTIVE.remove(server); STOPPED.remove(server); return; }
        try {
            server.execute(() -> {
                try {
                    if (!STOPPED.contains(server)) {
                        if (source != null) source.sendSuccess(message, false);
                        else LOGGER.info("{}", message.get().getString());
                    }
                } catch (RuntimeException callbackFailure) {
                    LOGGER.debug("Loggy command source was no longer available", callbackFailure);
                } finally {
                    ACTIVE.remove(server);
                    STOPPED.remove(server);
                }
            });
        } catch (RuntimeException serverStopped) {
            ACTIVE.remove(server);
            STOPPED.remove(server);
        }
    }

    /** Isolates internal registry access from the public provider API. */
    private static final class LoggyAccess {
        private static List<ProviderRegistry.Registration> providers() {
            return io.github.rhettl.loggy.api.Loggy.registry().snapshot();
        }
    }
}
