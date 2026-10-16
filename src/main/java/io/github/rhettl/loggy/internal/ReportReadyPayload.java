package io.github.rhettl.loggy.internal;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.regex.Pattern;

/** Report completion only; never accepts arbitrary clipboard contents or URL destinations. */
public record ReportReadyPayload(String url) implements CustomPacketPayload {
    private static final Pattern SHARE_URL = Pattern.compile("https://mclo\\.gs/[A-Za-z0-9]{1,128}");
    public static final Type<ReportReadyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("loggy", "report_ready"));
    public static final StreamCodec<ByteBuf, ReportReadyPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), ReportReadyPayload::url, ReportReadyPayload::new);

    public ReportReadyPayload {
        if (url == null || !SHARE_URL.matcher(url).matches()) {
            throw new IllegalArgumentException("Invalid mclo.gs report link");
        }
    }

    @Override public Type<ReportReadyPayload> type() { return TYPE; }
}
