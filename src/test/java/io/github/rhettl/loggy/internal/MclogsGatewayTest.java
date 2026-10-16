package io.github.rhettl.loggy.internal;

import gs.mclo.api.internal.filter.Filter;
import gs.mclo.api.internal.filter.FilterList;
import gs.mclo.api.internal.filter.RegexFilter;
import gs.mclo.api.internal.filter.RegexPattern;
import gs.mclo.api.internal.filter.ReplacingRegexPattern;
import gs.mclo.api.response.Limits;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MclogsGatewayTest {
    private static MclogsGateway.ReportDraft draft() {
        return new MclogsGateway.ReportDraft("Loggy McLogface server report\nStatus: OK", "latest.log available", "token=secret\nline2");
    }

    private static MclogsGateway.RemoteConfig config() {
        Filter regex = new RegexFilter(new ReplacingRegexPattern[]{
                new ReplacingRegexPattern("token=[^\\s]+", new char[]{}, "token=<filtered>")
        }, new RegexPattern[0]);
        return new MclogsGateway.RemoteConfig(new FilterList(new Filter[]{regex}), new Limits(3600, 2048, 40));
    }

    @Test void appliesVerifiedRemoteFiltersThenUploadsWithFixedLinkValidation() throws Exception {
        FakeTransport transport = new FakeTransport();
        MclogsGateway gateway = new MclogsGateway(transport, Duration.ofSeconds(2));
        var lease = gateway.prepareOperation(draft()).get(1, TimeUnit.SECONDS);
        assertTrue(lease.content().contains("token=<filtered>"));
        assertFalse(lease.content().contains("token=secret"));
        assertEquals("https://mclo.gs/abc123", lease.upload().get(1, TimeUnit.SECONDS));
        assertEquals(1, transport.uploadCalls);
    }

    @Test void failedFilterRetrievalPreventsUploadAndReleasesTheGate() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.config = CompletableFuture.failedFuture(new IllegalStateException("offline"));
        MclogsGateway gateway = new MclogsGateway(transport, Duration.ofSeconds(2));
        assertThrows(ExecutionException.class, () -> gateway.prepareOperation(draft()).get(1, TimeUnit.SECONDS));
        assertEquals(0, transport.uploadCalls);
        transport.config = CompletableFuture.completedFuture(config());
        assertNotNull(gateway.prepareOperation(draft()).get(1, TimeUnit.SECONDS));
    }

    @Test void rejectsFailedAndMalformedUploadResponses() {
        assertEquals("https://mclo.gs/abc123", MclogsGateway.LibraryTransport.parseUploadResponse(200,
                "{\"success\":true,\"id\":\"abc123\",\"url\":\"https://mclo.gs/abc123\",\"raw\":\"https://mclo.gs/raw/abc123\"}"));
        assertThrows(IllegalStateException.class, () -> MclogsGateway.LibraryTransport.parseUploadResponse(200, "not json"));
        assertThrows(IllegalStateException.class, () -> MclogsGateway.LibraryTransport.parseUploadResponse(200,
                "{\"success\":false,\"error\":\"invalid\"}"));
        assertThrows(IllegalStateException.class, () -> MclogsGateway.LibraryTransport.parseUploadResponse(503, "{}"));
    }

    @Test void overlapIsRejectedUntilTheUnderlyingUploadCompletes() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.upload = new CompletableFuture<>();
        MclogsGateway gateway = new MclogsGateway(transport, Duration.ofSeconds(2));
        var lease = gateway.prepareOperation(draft()).get(1, TimeUnit.SECONDS);
        var upload = lease.upload();
        assertThrows(ExecutionException.class, () -> gateway.prepareOperation(draft()).get(1, TimeUnit.SECONDS));
        transport.upload.complete("https://mclo.gs/xyz789");
        assertEquals("https://mclo.gs/xyz789", upload.get(1, TimeUnit.SECONDS));
        assertNotNull(gateway.prepareOperation(draft()).get(1, TimeUnit.SECONDS));
    }

    @Test void deadlineCancelsTheUnderlyingTransportAndCleansTheGate() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.upload = new CompletableFuture<>();
        CompletableFuture<String> observedCancel = new CompletableFuture<>();
        transport.upload.whenComplete((value, failure) -> { if (transport.upload.isCancelled()) observedCancel.complete("cancelled"); });
        MclogsGateway gateway = new MclogsGateway(transport, Duration.ofMillis(60));
        var lease = gateway.prepareOperation(draft()).get(1, TimeUnit.SECONDS);
        assertThrows(ExecutionException.class, () -> lease.upload().get(1, TimeUnit.SECONDS));
        assertEquals("cancelled", observedCancel.get(1, TimeUnit.SECONDS));
        assertNotNull(gateway.prepareOperation(draft()).get(1, TimeUnit.SECONDS));
    }

    private static final class FakeTransport implements MclogsGateway.Transport {
        private CompletableFuture<MclogsGateway.RemoteConfig> config = CompletableFuture.completedFuture(config());
        private CompletableFuture<String> upload = CompletableFuture.completedFuture("https://mclo.gs/abc123");
        private int uploadCalls;
        @Override public CompletableFuture<MclogsGateway.RemoteConfig> fetchConfig() { return config; }
        @Override public CompletableFuture<String> upload(String content, MclogsGateway.RemoteConfig rules, Duration timeout) {
            uploadCalls++;
            return upload;
        }
    }
}
