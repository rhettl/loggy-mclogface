package io.github.rhettl.loggy.internal;

import io.github.rhettl.loggy.api.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ProviderRegistryTest {
    private static DiagnosticProvider provider(String id) {
        return new DiagnosticProvider() {
            public String id() { return id; }
            public String displayName() { return "Example"; }
            public String description() { return "Example diagnostics"; }
            public DiagnosticReport collect(DiagnosticContext c) { return new DiagnosticReport(List.of(), List.of()); }
        };
    }

    @Test void acceptsAndSortsProvidersWithoutCollecting() {
        var registry = new ProviderRegistry();
        registry.register(provider("z_mod"));
        registry.register(provider("a-mod2"));
        assertEquals(List.of("a-mod2", "z_mod"), registry.snapshot().stream().map(ProviderRegistry.Registration::id).toList());
        assertThrows(UnsupportedOperationException.class, () -> registry.snapshot().clear());
    }

    @Test void duplicateKeepsFirst() {
        var registry = new ProviderRegistry();
        var first = provider("crests");
        registry.register(first);
        assertThrows(IllegalArgumentException.class, () -> registry.register(provider("crests")));
        assertSame(first, registry.snapshot().getFirst().provider());
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {"minecraft", "all", "list", "report", "UPPER", "a b", "1first", "namespace:id", " crests"})
    void rejectsInvalidOrReservedIds(String id) {
        assertThrows(IllegalArgumentException.class, () -> new ProviderRegistry().register(provider(id)));
    }

    @Test void metadataIsCapturedOnce() {
        var registry = new ProviderRegistry();
        var changing = new DiagnosticProvider() {
            int count;
            public String id() { return ++count == 1 ? "example" : "changed"; }
            public String displayName() { return "Example"; }
            public String description() { return "Description"; }
            public DiagnosticReport collect(DiagnosticContext c) { throw new AssertionError("Registration must not collect"); }
        };
        registry.register(changing);
        assertEquals("example", registry.snapshot().getFirst().id());
        assertEquals("example", registry.snapshot().getFirst().id());
    }

    @Test void blankMetadataDoesNotRegister() {
        var registry = new ProviderRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.register(new DiagnosticProvider() {
            public String id() { return "example"; }
            public String displayName() { return " "; }
            public String description() { return "Description"; }
            public DiagnosticReport collect(DiagnosticContext c) { return null; }
        }));
        assertTrue(registry.snapshot().isEmpty());
    }

    @Test void freezeIsIdempotentAndPreventsLaterRegistration() {
        var registry = new ProviderRegistry();
        registry.register(provider("example"));
        registry.freeze();
        registry.freeze();
        assertThrows(IllegalStateException.class, () -> registry.register(provider("later")));
        assertEquals(1, registry.snapshot().size());
    }
}
