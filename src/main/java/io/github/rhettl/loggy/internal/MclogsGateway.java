package io.github.rhettl.loggy.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import gs.mclo.api.Log;
import gs.mclo.api.MclogsClient;
import gs.mclo.api.internal.RequestBuilder;
import gs.mclo.api.internal.filter.Filter;
import gs.mclo.api.internal.filter.FilterList;
import gs.mclo.api.internal.filter.RegexFilter;
import gs.mclo.api.response.Limits;
import gs.mclo.api.response.UploadLogResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * mclogs-java 6.2.1 adapter. It intentionally uses that pinned artifact's
 * internal FilterList and RequestBuilder APIs to verify filters before upload
 * and to send the library-compressed request through a bounded HttpClient.
 */
final class MclogsGateway {
    private static final Duration DEADLINE = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final String MINECRAFT_VERSION = "1.21.1";
    private static final MclogsGateway SHARED = new MclogsGateway(new LibraryTransport());
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Loggy upload deadline");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean busy = new AtomicBoolean();
    private final Transport transport;
    private final Duration deadline;

    MclogsGateway(Transport transport) { this(transport, DEADLINE); }
    MclogsGateway(Transport transport, Duration deadline) {
        this.transport = Objects.requireNonNull(transport);
        this.deadline = Objects.requireNonNull(deadline);
    }

    static CompletableFuture<Lease> prepare(ReportDraft draft) { return SHARED.prepareOperation(draft); }

    CompletableFuture<Lease> prepareOperation(ReportDraft draft) {
        Objects.requireNonNull(draft, "draft");
        if (!busy.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("A Loggy report upload is already in progress."));
        }
        long started = System.nanoTime();
        CompletableFuture<RemoteConfig> config;
        try { config = transport.fetchConfig(); }
        catch (RuntimeException failure) {
            busy.set(false);
            return CompletableFuture.failedFuture(new IllegalStateException("Could not verify mclo.gs filters or limits."));
        }
        CompletableFuture<Lease> result = new CompletableFuture<>();
        config.whenComplete((remote, failure) -> {
            if (failure != null) {
                busy.set(false);
                result.completeExceptionally(new IllegalStateException("Could not verify mclo.gs filters or limits."));
                return;
            }
            try {
                if (remote == null || remote.filters() == null || remote.filters().getFilters() == null
                        || Arrays.stream(remote.filters().getFilters()).noneMatch(RegexFilter.class::isInstance)) {
                    throw new IllegalStateException("Remote filters contain no redaction rules");
                }
                String content = ReportText.fit(draft.diagnostics(), draft.logNote(), draft.log(),
                        remote.limits().getMaxLength(), remote.limits().getMaxLines());
                content = LocalRedactor.redact(content);
                content = apply(remote.filters(), content);
                content = ReportText.enforceFiltered(content, remote.limits().getMaxLength(), remote.limits().getMaxLines());
                if (!content.startsWith("Loggy McLogface server report")) throw new IllegalStateException("Report heading was lost");
                Lease lease = new Lease(content, remote, started);
                if (!result.complete(lease)) lease.close();
            } catch (Exception invalidFilters) {
                busy.set(false);
                result.completeExceptionally(new IllegalStateException("Could not safely apply mclo.gs filters."));
            }
        });
        TIMER.schedule(() -> result.completeExceptionally(new java.util.concurrent.TimeoutException("mclo.gs preparation exceeded its deadline")),
                deadline.toMillis(), TimeUnit.MILLISECONDS);
        return result;
    }

    private static String apply(FilterList filters, String content) throws java.io.IOException {
        Log log = new Log(content).setSource("Loggy McLogface");
        String filtered = log.getContent(filters);
        if (filtered == null || filtered.isBlank() || !filtered.startsWith("Loggy McLogface server report")) {
            throw new IllegalStateException("Filter response removed the report heading");
        }
        return filtered;
    }

    record RemoteConfig(FilterList filters, Limits limits) {}
    record ReportDraft(String diagnostics, String logNote, String log) {}

    interface Transport {
        CompletableFuture<RemoteConfig> fetchConfig();
        CompletableFuture<String> upload(String content, RemoteConfig config, Duration timeout);
    }

    final class Lease implements AutoCloseable {
        private final String content;
        private final RemoteConfig config;
        private final long started;
        private final AtomicBoolean finished = new AtomicBoolean();

        private Lease(String content, RemoteConfig config, long started) {
            this.content = content;
            this.config = config;
            this.started = started;
        }
        String content() { return content; }

        CompletableFuture<String> upload() {
            if (finished.get()) return CompletableFuture.failedFuture(new IllegalStateException("Upload operation is closed"));
            long remainingNanos = deadline.toNanos() - (System.nanoTime() - started);
            if (remainingNanos <= 0) {
                close();
                return CompletableFuture.failedFuture(new java.util.concurrent.TimeoutException("mclo.gs upload exceeded its deadline"));
            }
            CompletableFuture<String> actual;
            try { actual = transport.upload(content, config, Duration.ofNanos(remainingNanos)); }
            catch (RuntimeException failure) {
                close();
                return CompletableFuture.failedFuture(failure);
            }
            CompletableFuture<String> visible = new CompletableFuture<>();
            TIMER.schedule(() -> {
                if (visible.completeExceptionally(new java.util.concurrent.TimeoutException("mclo.gs upload exceeded its deadline"))) {
                    actual.cancel(true);
                }
            }, remainingNanos, TimeUnit.NANOSECONDS);
            actual.whenComplete((url, failure) -> {
                try {
                    if (failure != null) visible.completeExceptionally(failure);
                    else if (url == null || !url.startsWith("https://mclo.gs/")) {
                        visible.completeExceptionally(new IllegalStateException("mclo.gs returned an invalid report link"));
                    } else visible.complete(url);
                } finally {
                    if (finished.compareAndSet(false, true)) busy.set(false);
                }
            });
            return visible;
        }

        @Override public void close() {
            if (finished.compareAndSet(false, true)) busy.set(false);
        }
    }

    static final class LibraryTransport implements Transport {
        private static final MclogsClient CLIENT = new MclogsClient("Loggy McLogface", "1", MINECRAFT_VERSION);
        private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        @Override public CompletableFuture<RemoteConfig> fetchConfig() {
            CompletableFuture<String> filters = get(CLIENT.getInstance().getFiltersUrl());
            CompletableFuture<String> limits = get(CLIENT.getInstance().getStorageLimitUrl());
            // allOf completes after both request futures, including on failure, so the gate never
            // releases while a sibling config request is still running.
            return CompletableFuture.allOf(filters, limits).thenApply(ignored -> {
                JsonElement filterJson = JsonParser.parseString(filters.join());
                if (!filterJson.isJsonArray()) throw new CompletionException(new IllegalStateException("Malformed filter response"));
                Filter[] filterArray = CLIENT.gson().fromJson(filterJson, Filter[].class);
                if (filterArray == null || filterArray.length == 0
                        || Arrays.stream(filterArray).noneMatch(RegexFilter.class::isInstance)) {
                    throw new CompletionException(new IllegalStateException("Remote filters contain no redaction rules"));
                }
                Limits serviceLimits = CLIENT.gson().fromJson(limits.join(), Limits.class);
                if (serviceLimits == null || serviceLimits.getMaxLength() <= 0 || serviceLimits.getMaxLines() <= 0) {
                    throw new CompletionException(new IllegalStateException("Malformed storage limits"));
                }
                return new RemoteConfig(new FilterList(filterArray), serviceLimits);
            });
        }

        @Override public CompletableFuture<String> upload(String content, RemoteConfig config, Duration timeout) {
            try {
                RequestBuilder builder = new RequestBuilder(CLIENT.gson());
                builder.setProjectName("Loggy McLogface");
                builder.setProjectVersion("1");
                builder.setMinecraftVersion(MINECRAFT_VERSION);
                HttpRequest base = builder.uploadRequest(CLIENT.getInstance().getLogUploadUrl(),
                        new Log(content).setSource("Loggy McLogface"), config.filters());
                HttpRequest request = HttpRequest.newBuilder(base, (name, value) -> true).timeout(timeout).build();
                return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenApply(response -> parseUploadResponse(response.statusCode(), response.body()));
            } catch (Exception error) {
                return CompletableFuture.failedFuture(error);
            }
        }

        static String parseUploadResponse(int status, String body) {
            if (status < 200 || status >= 300) throw new IllegalStateException("mclo.gs returned HTTP " + status);
            JsonElement json;
            try { json = JsonParser.parseString(body); }
            catch (RuntimeException malformed) { throw new IllegalStateException("mclo.gs returned malformed JSON"); }
            if (!json.isJsonObject() || (json.getAsJsonObject().has("success")
                    && (!json.getAsJsonObject().get("success").isJsonPrimitive()
                    || !json.getAsJsonObject().get("success").getAsBoolean()))) {
                throw new IllegalStateException("mclo.gs rejected the report");
            }
            UploadLogResponse parsed;
            try { parsed = CLIENT.gson().fromJson(json, UploadLogResponse.class); }
            catch (RuntimeException malformed) { throw new IllegalStateException("mclo.gs returned an invalid response"); }
            if (parsed == null || parsed.getUrl() == null || !parsed.getUrl().startsWith("https://mclo.gs/")) {
                throw new IllegalStateException("mclo.gs returned an invalid report link");
            }
            return parsed.getUrl();
        }

        private static CompletableFuture<String> get(String url) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Loggy McLogface/1 (Minecraft " + MINECRAFT_VERSION + ")")
                    .GET().build();
            return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new CompletionException(new IllegalStateException("mclo.gs returned HTTP " + response.statusCode()));
                }
                return response.body();
            });
        }
    }
}
