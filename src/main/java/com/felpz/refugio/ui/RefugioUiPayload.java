package com.felpz.refugio.ui;

import com.felpz.refugio.Refugio;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Small text-encoded UI message; all purchases and toggles are validated server-side. */
public record RefugioUiPayload(String data) implements CustomPacketPayload {
    public static final Type<RefugioUiPayload> TYPE = new Type<>(Refugio.id("ui"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RefugioUiPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeUtf(payload.data(), 8192),
            buffer -> new RefugioUiPayload(buffer.readUtf(8192)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
