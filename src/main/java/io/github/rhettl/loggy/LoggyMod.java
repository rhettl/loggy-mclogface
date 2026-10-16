package io.github.rhettl.loggy;

import io.github.rhettl.loggy.api.Loggy;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** API baseline only; runtime reporting is the next implementation assignment. */
public final class LoggyMod implements ModInitializer {
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> Loggy.registry().freeze());
    }
}
