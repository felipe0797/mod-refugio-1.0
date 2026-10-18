package com.felpz.refugio.ability;

import com.felpz.refugio.Refugio;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client request to activate the currently enabled movement skill; the server validates ownership and state. */
public record ActivateMovementPayload() implements CustomPacketPayload {
    public static final Type<ActivateMovementPayload> TYPE = new Type<>(Refugio.id("activate_movement"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActivateMovementPayload> CODEC =
            StreamCodec.unit(new ActivateMovementPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
