package io.github.rhettl.loggy.internal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReportTargetArgumentTest {
    private final List<String> seen = new ArrayList<>();
    private final CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();

    ReportTargetArgumentTest() {
        dispatcher.register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<Object>literal("report")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<Object, String>argument("target", ReportTargetArgument.target())
                        .executes(c -> { seen.add(ReportTargetArgument.getTarget(c, "target")); return 1; })));
    }

    @Test void targetUsesVanillaArgumentSerializationForClientCommandSynchronization() {
        assertInstanceOf(StringArgumentType.class, ReportTargetArgument.target(),
                "The command tree must use a built-in argument understood by clients without Loggy");
    }

    @Test void parsesNamespacedAndPlainTargetsUnchanged() throws CommandSyntaxException {
        dispatcher.execute("report bca:crests", new Object());
        dispatcher.execute("report cobble-crests", new Object());
        dispatcher.execute("report all", new Object());
        assertEquals(List.of("bca:crests", "cobble-crests", "all"), seen);
    }

    @Test void stopsAtWhitespaceSoExtraInputIsRejected() {
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("report bca:crests extra", new Object()));
        assertTrue(seen.isEmpty());
    }

    @Test void stockWordArgumentCannotParseColon() {
        var plain = new CommandDispatcher<Object>();
        plain.register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<Object>literal("report")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<Object, String>argument("target", StringArgumentType.word())
                        .executes(c -> 1)));
        assertThrows(CommandSyntaxException.class, () -> plain.execute("report bca:crests", new Object()));
    }
}
