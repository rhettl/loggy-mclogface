package io.github.rhettl.loggy.internal;

import java.util.regex.Pattern;

/** Conservative local redaction used before persistence and as protection when remote filters are unavailable. */
final class LocalRedactor {
    private static final Pattern IPV4 = Pattern.compile("(?<![0-9A-Za-z_-])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9A-Za-z_-])");
    private static final Pattern IPV6 = Pattern.compile("(?i)(?<![0-9a-f])(?:[0-9a-f]{0,4}:){2,7}[0-9a-f]{0,4}(?![0-9a-f])");
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]+=*");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile("(?i)(password|passwd|token|secret|api[_-]?key)(\\s*[=:]\\s*)[^\\s,;]+\\s*([,;]?)");
    private LocalRedactor() {}

    static String redact(String input) {
        String output = IPV4.matcher(input).replaceAll("<redacted-ipv4>");
        output = redactIpv6(output);
        output = BEARER.matcher(output).replaceAll("$1<redacted>");
        return SECRET_ASSIGNMENT.matcher(output).replaceAll("$1$2<redacted>$3");
    }

    private static String redactIpv6(String input) {
        var matcher = IPV6.matcher(input);
        StringBuilder result = new StringBuilder(input.length());
        int end = 0;
        while (matcher.find()) {
            String candidate = matcher.group();
            long colons = candidate.chars().filter(ch -> ch == ':').count();
            boolean timestamp = colons == 2 && !candidate.contains("::")
                    && candidate.matches("[0-9]{1,2}:[0-9]{1,2}:[0-9]{1,2}");
            result.append(input, end, matcher.start()).append(timestamp ? candidate : "<redacted-ipv6>");
            end = matcher.end();
        }
        return result.append(input, end, input.length()).toString();
    }
}
