/** Standalone consumer: nested JAR inspection cannot establish dev classpath availability. */
public final class ConsumerSmoke {
    public static void main(String[] args) throws Exception {
        Class<?> client = Class.forName("gs.mclo.api.MclogsClient");
        client.getConstructor(String.class, String.class, String.class)
                .newInstance("Loggy-consumer-verification", "test", "1.21.1");
        Class.forName("io.github.rhettl.loggy.internal.MclogsGateway");
        System.out.println("Published consumer loads mclo.gs and Loggy gateway; no network request sent.");
    }
}
