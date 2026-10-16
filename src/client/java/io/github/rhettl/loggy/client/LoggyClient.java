package io.github.rhettl.loggy.client;

import io.github.rhettl.loggy.internal.ReportReadyPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional client companion. No clipboard/client classes are linked by the server entrypoint. */
public final class LoggyClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Loggy");

    @Override public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ReportReadyPayload.TYPE, (payload, context) -> {
            // Fabric invokes this handler on the client thread.
            try {
                context.client().keyboardHandler.setClipboard(payload.url());
                context.player().displayClientMessage(Component.literal("Loggy report link copied to clipboard."), false);
            } catch (RuntimeException clipboardFailure) {
                LOGGER.debug("Could not copy Loggy report link", clipboardFailure);
                context.player().displayClientMessage(Component.literal("Clipboard copy failed; use the report's [copy] button."), false);
            }
        });
    }
}
