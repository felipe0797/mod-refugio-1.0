package com.felpz.refugio;

import com.felpz.refugio.data.RefugioPlayerData;
import com.felpz.refugio.ability.RefugioAbilityData;
import com.felpz.refugio.command.RefugioCommands;
import com.felpz.refugio.item.RefugioItems;
import com.felpz.refugio.npc.RefugioNPCs;
import com.felpz.refugio.npc.RefugioTrainingDummy;
import com.felpz.refugio.quest.RefugioCommanderData;
import com.felpz.refugio.world.StonehillMap;
import com.felpz.refugio.world.RefugioSustainability;
import com.felpz.refugio.world.VillageStructure;
import com.felpz.refugio.quest.RefugioSideQuests;
import com.felpz.refugio.story.RefugioStory;
import com.felpz.refugio.ui.RefugioUiService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Refugio implements ModInitializer {
    public static final String MOD_ID = "refugio";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, id("stonehill"));
    public static final ResourceKey<Level> LEGACY_DIMENSION = ResourceKey.create(Registries.DIMENSION, id("refugio"));
    public static final Vec3 ARRIVAL = new Vec3(-243.606, 76.0, 57.858);
    public static final float ARRIVAL_YAW = 44.5f;
    public static final float ARRIVAL_PITCH = -12.9f;

    public static final int MAP_CENTER_X = -336;
    public static final int MAP_CENTER_Z = 224;
    public static final int MAP_RADIUS = 600;
    public static final int MAP_MIN_X = MAP_CENTER_X - MAP_RADIUS;
    public static final int MAP_MAX_X = MAP_CENTER_X + MAP_RADIUS;
    public static final int MAP_MIN_Z = MAP_CENTER_Z - MAP_RADIUS;
    public static final int MAP_MAX_Z = MAP_CENTER_Z + MAP_RADIUS;

    @Override
    public void onInitialize() {
        RefugioItems.initialize();
        RefugioPlayerData.initialize();
        RefugioAbilityData.registerEvents();
        RefugioUiService.initialize();
        RefugioStory.initialize();
        RefugioSideQuests.initialize();
        RefugioCommanderData.initialize();
        RefugioCommands.initialize();
        RefugioNPCs.initialize();
        RefugioTrainingDummy.initialize();
        RefugioSustainability.initialize();
        ServerLifecycleEvents.SERVER_STARTING.register(StonehillMap::install);
        ServerEntityEvents.ALLOW_LOAD.register((entity, world, spawnReason, loadedFromDisk) ->
                !world.dimension().equals(DIMENSION) || !(entity instanceof Enemy)
                        || RefugioTrainingDummy.isTrainingDummy(entity));
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk, fromDisk) -> {
            if (world.dimension().equals(DIMENSION) || world.dimension().equals(LEGACY_DIMENSION)) {
                VillageStructure.sanitizeChunk(world, chunk);
            }
        });
        ServerTickEvents.END_LEVEL_TICK.register(world -> {
            if (world.dimension().equals(LEGACY_DIMENSION)) {
                VillageStructure.removePreviousMountainRim(world);
                return;
            }
            if (!world.dimension().equals(DIMENSION)) return;

            VillageStructure.removeHostileMobs(world);
            for (ServerPlayer player : world.players()) {
                if (player.getX() < MAP_MIN_X || player.getX() > MAP_MAX_X
                        || player.getZ() < MAP_MIN_Z || player.getZ() > MAP_MAX_Z) {
                    player.sendSystemMessage(Component.translatable("message.refugio.valley_boundary"));
                    player.teleport(new TeleportTransition(world, ARRIVAL, Vec3.ZERO,
                            ARRIVAL_YAW, ARRIVAL_PITCH, TeleportTransition.DO_NOTHING));
                }
            }
        });
        LOGGER.info("Refúgio carregado: dimensão Stonehill, terreno importado e sem inimigos hostis.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
