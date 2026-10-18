package com.felpz.refugio.item;

import com.felpz.refugio.Refugio;
import com.felpz.refugio.world.StonehillMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

public final class RefugioKey extends Item {
    private record ReturnPoint(String dimension, double x, double y, double z, float yaw, float pitch) {
        private static final Codec<ReturnPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("dimension").forGetter(ReturnPoint::dimension),
                Codec.DOUBLE.fieldOf("x").forGetter(ReturnPoint::x),
                Codec.DOUBLE.fieldOf("y").forGetter(ReturnPoint::y),
                Codec.DOUBLE.fieldOf("z").forGetter(ReturnPoint::z),
                Codec.FLOAT.fieldOf("yaw").forGetter(ReturnPoint::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(ReturnPoint::pitch)
        ).apply(instance, ReturnPoint::new));
    }

    private static final AttachmentType<ReturnPoint> RETURN_POINT = AttachmentRegistry.create(
            Refugio.id("return_point"), builder -> builder.persistent(ReturnPoint.CODEC).copyOnDeath());

    public RefugioKey(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player user, InteractionHand hand) {
        if (!(user instanceof ServerPlayer player)) return InteractionResult.SUCCESS;

        ServerLevel originWorld = player.level();
        var server = originWorld.getServer();
        if (originWorld.dimension().equals(Refugio.DIMENSION)
                || originWorld.dimension().equals(Refugio.LEGACY_DIMENSION)) {
            ReturnPoint point = player.getAttached(RETURN_POINT);
            ServerLevel destination;
            Vec3 position;
            float yaw;
            float pitch;
            if (point == null) {
                destination = server.overworld();
                var spawn = destination.getRespawnData().pos();
                position = Vec3.atBottomCenterOf(spawn.above());
                yaw = player.getYRot();
                pitch = player.getXRot();
                player.sendSystemMessage(Component.translatable("message.refugio.return_missing"));
            } else {
                ResourceKey<Level> originKey = ResourceKey.create(Registries.DIMENSION,
                        net.minecraft.resources.Identifier.parse(point.dimension()));
                destination = server.getLevel(originKey);
                if (destination == null) {
                    destination = server.overworld();
                    var spawn = destination.getRespawnData().pos();
                    position = Vec3.atBottomCenterOf(spawn.above());
                    yaw = player.getYRot();
                    pitch = player.getXRot();
                    player.sendSystemMessage(Component.translatable("message.refugio.return_invalid"));
                } else {
                    position = new Vec3(point.x(), point.y(), point.z());
                    yaw = point.yaw();
                    pitch = point.pitch();
                }
            }
            player.sendSystemMessage(Component.translatable("message.refugio.leaving"));
            player.teleport(new TeleportTransition(destination, position, Vec3.ZERO, yaw, pitch,
                    TeleportTransition.DO_NOTHING));
            player.setAttached(RETURN_POINT, null);
            return InteractionResult.SUCCESS;
        }

        if (!StonehillMap.isInstalled()) {
            player.sendSystemMessage(Component.translatable("message.refugio.dimension_unavailable"));
            return InteractionResult.FAIL;
        }

        player.setAttached(RETURN_POINT, new ReturnPoint(originWorld.dimension().identifier().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
        enterRefuge(player);
        return InteractionResult.SUCCESS;
    }

    public static void enterRefuge(ServerPlayer player) {
        if (!StonehillMap.isInstalled()) {
            player.sendSystemMessage(Component.translatable("message.refugio.dimension_unavailable"));
            return;
        }
        var server = player.level().getServer();
        ServerLevel refuge = server.getLevel(Refugio.DIMENSION);
        if (refuge == null) {
            player.sendSystemMessage(Component.translatable("message.refugio.dimension_unavailable"));
            return;
        }
        player.closeContainer();
        player.sendSystemMessage(Component.translatable("message.refugio.entering"));
        player.teleport(new TeleportTransition(refuge, Refugio.ARRIVAL, Vec3.ZERO,
                Refugio.ARRIVAL_YAW, Refugio.ARRIVAL_PITCH, TeleportTransition.PLAY_PORTAL_SOUND));
    }
}
