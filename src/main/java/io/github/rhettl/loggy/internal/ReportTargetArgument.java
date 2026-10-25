package io.github.rhettl.loggy.internal;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;

/** Single-token report target that, unlike Brigadier's word(), also accepts the ':' namespace separator. */
final class ReportTargetArgument implements ArgumentType<String> {
    static ReportTargetArgument target() { return new ReportTargetArgument(); }

    static String getTarget(CommandContext<?> context, String name) { return context.getArgument(name, String.class); }

    @Override public String parse(StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead() && isAllowed(reader.peek())) reader.skip();
        return reader.getString().substring(start, reader.getCursor());
    }

    private static boolean isAllowed(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == ':';
    }
}
