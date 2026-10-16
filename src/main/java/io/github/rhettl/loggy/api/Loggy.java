package io.github.rhettl.loggy.api;

import io.github.rhettl.loggy.internal.ProviderRegistry;

/** Public registration entry point. Command and upload implementation is owned by Loggy. */
public final class Loggy {
    private static final ProviderRegistry REGISTRY = new ProviderRegistry();
    private Loggy() {}

    public static void register(DiagnosticProvider provider) {
        REGISTRY.register(provider);
    }

    /** Internal implementation access, not part of the provider API. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static ProviderRegistry registry() {
        return REGISTRY;
    }
}
