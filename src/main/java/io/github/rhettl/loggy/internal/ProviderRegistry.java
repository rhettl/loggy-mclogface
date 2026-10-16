package io.github.rhettl.loggy.internal;

import io.github.rhettl.loggy.api.DiagnosticProvider;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Process-scoped registry. All metadata is captured once, before server startup. */
public final class ProviderRegistry {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*");
    private static final Set<String> RESERVED = Set.of("minecraft", "all", "list", "report");
    private final Map<String, Registration> providers = new TreeMap<>();
    private boolean frozen;

    public synchronized void register(DiagnosticProvider provider) {
        if (frozen) throw new IllegalStateException("Loggy registration closed when the first server started");
        Objects.requireNonNull(provider, "provider");
        String id = provider.id();
        if (id == null || !ID.matcher(id).matches() || RESERVED.contains(id)) {
            throw new IllegalArgumentException("Invalid or reserved Loggy provider ID: " + id);
        }
        if (providers.containsKey(id)) {
            throw new IllegalArgumentException("Loggy provider already registered: " + id);
        }
        String name = requireText(provider.displayName(), "displayName");
        String description = requireText(provider.description(), "description");
        providers.put(id, new Registration(id, name, description, provider));
    }

    public synchronized void freeze() { frozen = true; }
    public synchronized List<Registration> snapshot() { return List.copyOf(providers.values()); }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Provider " + field + " must not be blank");
        return value;
    }

    public record Registration(String id, String displayName, String description, DiagnosticProvider provider) {}
}
