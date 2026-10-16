package io.github.rhettl.loggy.internal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalRedactorTest {
    @Test void redactsAddressAndCredentialPatternsWithoutDamagingTimestampsOrPaths() {
        String raw = "time=18:09:37 host=2001:db8::1 token=abc123 Bearer xYz ";
        raw += "mac=/Users/alice/server Windows=C:\\Users\\alice\\server";
        String safe = LocalRedactor.redact(raw);
        assertTrue(safe.contains("time=18:09:37"));
        assertTrue(safe.contains("host=<redacted-ipv6>"));
        assertTrue(safe.contains("token=<redacted>"));
        assertTrue(safe.contains("Bearer <redacted>"));
        assertTrue(safe.contains("/Users/alice/server"));
        assertTrue(safe.contains("C:\\Users\\alice\\server"));
    }
}
