package com.felpz.refugio.npc;

import com.felpz.refugio.Refugio;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Stationary training zombie ("Miojo") positioned beside Kael in the castle. */
public final class RefugioTrainingDummy {
    public static final String TAG = "refugio:training_dummy";
    private static final EntityType<?> ZOMBIE_TYPE = BuiltInRegistries.ENTITY_TYPE
            .getValue(Identifier.parse("minecraft:zombie"));
    private static final int MAX_HEALTH = 100;
    private static final int RESPAWN_DELAY_TICKS = 40;
    private static final long CLICK_COOLDOWN_TICKS = 40L;
    private static final Vec3 CASTLE_POSITION = new Vec3(-322.027, 129.0, 206.169);
    private static final float CASTLE_YAW = -19.3f;
    private static final float CASTLE_PITCH = -3.8f;
    private static final List<String> REPLY_KEYS = List.of(
            "message.refugio.training_dummy.reply.0",
            "message.refugio.training_dummy.reply.1",
            "message.refugio.training_dummy.reply.2",
            "message.refugio.training_dummy.reply.3"
    );
    private static final List<PendingRespawn> PENDING_RESPAWNS = new ArrayList<>();
    private static final Map<UUID, Long> PLAYER_CLICK_COOLDOWN_UNTIL = new ConcurrentHashMap<>();

    private RefugioTrainingDummy() { }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(RefugioTrainingDummy::ensureAtCastle);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PENDING_RESPAWNS.clear();
            PLAYER_CLICK_COOLDOWN_UNTIL.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                PLAYER_CLICK_COOLDOWN_UNTIL.remove(handler.player.getUUID()));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!isTrainingDummy(entity)) {
                return InteractionResult.PASS;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            long now = serverPlayer.level().getServer().overworld().getGameTime();
            PLAYER_CLICK_COOLDOWN_UNTIL.entrySet().removeIf(entry -> entry.getValue() <= now);
            Long cooldownUntil = PLAYER_CLICK_COOLDOWN_UNTIL.get(serverPlayer.getUUID());
            if (cooldownUntil != null && now < cooldownUntil) {
                return InteractionResult.SUCCESS;
            }
            PLAYER_CLICK_COOLDOWN_UNTIL.put(serverPlayer.getUUID(), now + CLICK_COOLDOWN_TICKS);

            String key = REPLY_KEYS.get(serverPlayer.getRandom().nextInt(REPLY_KEYS.size()));
            serverPlayer.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.LIGHT_PURPLE));
            serverPlayer.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.getType() != ZOMBIE_TYPE || !isTrainingDummy(entity)) return;
            PENDING_RESPAWNS.add(new PendingRespawn(Refugio.DIMENSION, CASTLE_POSITION, CASTLE_YAW, CASTLE_PITCH));
        });
        ServerTickEvents.END_SERVER_TICK.register(RefugioTrainingDummy::tickRespawns);
    }

    public static boolean isTrainingDummy(Entity entity) {
        return entity != null && entity.getType() == ZOMBIE_TYPE && entity.entityTags().contains(TAG);
    }

    public static boolean ensureAtCastle(MinecraftServer server) {
        ServerLevel level = server.getLevel(Refugio.DIMENSION);
        if (level == null) {
            Refugio.LOGGER.warn("Não foi possível criar o zumbi de treino: a dimensão Stonehill não está disponível.");
            return false;
        }
        return ensureAtCastle(level);
    }

    public static boolean ensureAtCastle(ServerLevel level) {
        level.getChunkAt(BlockPos.containing(CASTLE_POSITION));
        var nearbyDummies = level.getEntitiesOfClass(Mob.class,
                new AABB(-330.0, 124.0, 198.0, -312.0, 135.0, 220.0),
                RefugioTrainingDummy::isTrainingDummy);
        if (!nearbyDummies.isEmpty()) {
            Mob primary = nearbyDummies.getFirst();
            for (int i = 1; i < nearbyDummies.size(); i++) {
                nearbyDummies.get(i).discard();
            }
            configureDummy(primary, CASTLE_POSITION, CASTLE_YAW, CASTLE_PITCH, false);
            return true;
        }
        return spawn(level, CASTLE_POSITION, CASTLE_YAW, CASTLE_PITCH);
    }

    private static boolean spawn(ServerLevel level, Vec3 position, float yaw, float pitch) {
        Entity entity = ZOMBIE_TYPE.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (!(entity instanceof Mob zombie)) return false;

        configureDummy(zombie, position, yaw, pitch, true);
        return level.addFreshEntity(zombie);
    }

    private static void configureDummy(Mob zombie, Vec3 position, float yaw, float pitch, boolean resetHealth) {
        zombie.setPos(position);
        zombie.setYRot(yaw);
        zombie.setXRot(pitch);
        zombie.setYHeadRot(yaw);
        zombie.setYBodyRot(yaw);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        zombie.setCanPickUpLoot(false);
        zombie.setSilent(true);
        zombie.setCustomName(Component.translatable("entity.refugio.training_dummy"));
        zombie.setCustomNameVisible(true);
        zombie.addTag(TAG);

        AttributeInstance maxHealth = zombie.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(MAX_HEALTH);
        AttributeInstance knockback = zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null) knockback.setBaseValue(1.0);
        if (resetHealth || zombie.getHealth() <= 0.0f) {
            zombie.setHealth(MAX_HEALTH);
        }
        zombie.setDeltaMovement(Vec3.ZERO);
    }

    private static void tickRespawns(MinecraftServer server) {
        Iterator<PendingRespawn> iterator = PENDING_RESPAWNS.iterator();
        while (iterator.hasNext()) {
            PendingRespawn pending = iterator.next();
            pending.ticksRemaining--;
            if (pending.ticksRemaining > 0) continue;
            iterator.remove();
            ServerLevel level = server.getLevel(pending.dimension);
            if (level == null) continue;
            level.getChunkAt(BlockPos.containing(pending.position));
            spawn(level, pending.position, pending.yaw, pending.pitch);
        }
    }

    private static final class PendingRespawn {
        private final ResourceKey<Level> dimension;
        private final Vec3 position;
        private final float yaw;
        private final float pitch;
        private int ticksRemaining = RESPAWN_DELAY_TICKS;

        private PendingRespawn(ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch) {
            this.dimension = dimension;
            this.position = position;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
