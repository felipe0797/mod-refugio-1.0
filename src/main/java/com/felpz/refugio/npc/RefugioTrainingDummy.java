package com.felpz.refugio.npc;

import com.felpz.refugio.Refugio;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** A stationary zombie target positioned beside Kael in the castle. */
public final class RefugioTrainingDummy {
    public static final String TAG = "refugio:training_dummy";
    private static final EntityType<?> ZOMBIE_TYPE = BuiltInRegistries.ENTITY_TYPE
            .getValue(Identifier.parse("minecraft:zombie"));
    private static final int MAX_HEALTH = 100;
    private static final int RESPAWN_DELAY_TICKS = 40;
    private static final Vec3 CASTLE_POSITION = new Vec3(-320.75, 129.0, 211.976);
    private static final float CASTLE_YAW = 90.0f;
    private static final List<PendingRespawn> PENDING_RESPAWNS = new ArrayList<>();

    private RefugioTrainingDummy() { }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(RefugioTrainingDummy::ensureAtCastle);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.getType() != ZOMBIE_TYPE || !isTrainingDummy(entity)) return;
            PENDING_RESPAWNS.add(new PendingRespawn(Refugio.DIMENSION, CASTLE_POSITION, CASTLE_YAW));
        });
        ServerTickEvents.END_SERVER_TICK.register(RefugioTrainingDummy::tickRespawns);
    }

    public static boolean isTrainingDummy(Entity entity) {
        return entity.getType() == ZOMBIE_TYPE && entity.entityTags().contains(TAG);
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
        var nearbyDummies = level.getEntitiesOfClass(Mob.class,
                new net.minecraft.world.phys.AABB(-329.0, 124.0, 204.0, -312.0, 135.0, 220.0),
                RefugioTrainingDummy::isTrainingDummy);
        if (!nearbyDummies.isEmpty()) return true;
        return spawn(level, CASTLE_POSITION, CASTLE_YAW);
    }

    private static boolean spawn(ServerLevel level, Vec3 position, float yaw) {
        Entity entity = ZOMBIE_TYPE.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (!(entity instanceof Mob zombie)) return false;

        zombie.setPos(position);
        zombie.setYRot(yaw);
        zombie.setXRot(0.0f);
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
        zombie.setHealth(MAX_HEALTH);
        zombie.setDeltaMovement(Vec3.ZERO);
        return level.addFreshEntity(zombie);
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
            spawn(level, pending.position, pending.yaw);
        }
    }

    private static final class PendingRespawn {
        private final ResourceKey<Level> dimension;
        private final Vec3 position;
        private final float yaw;
        private int ticksRemaining = RESPAWN_DELAY_TICKS;

        private PendingRespawn(ResourceKey<Level> dimension, Vec3 position, float yaw) {
            this.dimension = dimension;
            this.position = position;
            this.yaw = yaw;
        }
    }
}
