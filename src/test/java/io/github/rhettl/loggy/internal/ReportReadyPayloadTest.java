package io.github.rhettl.loggy.internal;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReportReadyPayloadTest {
    @Test void codecRoundTripsAReportLink() {
        var buffer = Unpooled.buffer();
        try {
            var payload = new ReportReadyPayload("https://mclo.gs/hcABr37");
            ReportReadyPayload.CODEC.encode(buffer, payload);
            assertEquals(payload, ReportReadyPayload.CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }

    @Test void rejectsArbitraryClipboardContentsAndOtherDestinations() {
        for (String url : new String[]{"hello", "http://mclo.gs/hcABr37", "https://evil.example/report",
                "https://mclo.gs.evil.example/id", "https://mclo.gs/id\ncommand", "https://mclo.gs/id?token=secret"}) {
            assertThrows(IllegalArgumentException.class, () -> new ReportReadyPayload(url));
        }
        assertThrows(IllegalArgumentException.class, () -> new ReportReadyPayload(null));
    }
}
