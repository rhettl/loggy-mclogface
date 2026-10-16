package io.github.rhettl.loggy.internal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Reads only the most recent bounded part of latest.log. */
final class CurrentLogSnapshot {
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private CurrentLogSnapshot() {}

    static Snapshot read(Path serverRoot, boolean dedicated) {
        Path log = serverRoot.resolve("logs").resolve("latest.log");
        String note = dedicated ? "Current instance logs/latest.log (dedicated server)." :
                "Current instance logs/latest.log (shared integrated process log; includes client and server messages).";
        try (FileChannel channel = FileChannel.open(log, StandardOpenOption.READ)) {
            long size = channel.size();
            int length = (int) Math.min(size, MAX_BYTES);
            ByteBuffer buffer = ByteBuffer.allocate(length);
            long position = size - length;
            while (buffer.hasRemaining() && channel.read(buffer, position + buffer.position()) > 0) {}
            String content = StandardCharsets.UTF_8.decode(buffer.flip()).toString();
            boolean truncated = size > length;
            if (truncated) {
                int newline = content.indexOf('\n');
                content = newline >= 0 ? content.substring(newline + 1) : "";
                note += " Earlier bytes omitted because the source exceeded the 16 MiB read bound.";
            }
            return new Snapshot(content, note, truncated, true);
        } catch (IOException e) {
            return new Snapshot("", note + " The current log could not be read; diagnostic evidence is still included.", false, false);
        }
    }

    record Snapshot(String content, String note, boolean truncated, boolean available) {}
}
