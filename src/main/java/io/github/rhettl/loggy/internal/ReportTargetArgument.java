package io.github.rhettl.loggy.internal;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

/** Namespaced targets with vanilla command-tree serialization, including clients without Loggy. */
final class ReportTargetArgument {
    private static final SimpleCommandExceptionType INVALID_TARGET =
            new SimpleCommandExceptionType(new LiteralMessage("Expected a single Loggy report target"));

    private ReportTargetArgument() {}

    // word() rejects ':'. greedyString() accepts it and has a built-in Minecraft serializer.
    // This argument is terminal; validate one token before any report handler executes.
    static StringArgumentType target() { return StringArgumentType.greedyString(); }

    static String getTarget(CommandContext<?> context, String name) throws CommandSyntaxException {
        String target = StringArgumentType.getString(context, name);
        if (!target.matches("[a-zA-Z0-9_:-]+")) throw INVALID_TARGET.create();
        return target;
    }
}
