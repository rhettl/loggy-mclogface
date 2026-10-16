package io.github.rhettl.loggy;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import io.github.rhettl.loggy.internal.ReportReadyPayload;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import io.github.rhettl.loggy.internal.LoggyCommands;
import io.github.rhettl.loggy.api.Loggy;

/** Loggy runtime entrypoint. */
public final class LoggyMod implements ModInitializer {
    @Override public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(ReportReadyPayload.TYPE, ReportReadyPayload.CODEC);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                LoggyCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> Loggy.registry().freeze());
        ServerLifecycleEvents.SERVER_STOPPING.register(LoggyCommands::serverStopping);
    }
}
