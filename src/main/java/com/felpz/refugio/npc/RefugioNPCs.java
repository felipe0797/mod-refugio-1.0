package com.felpz.refugio.npc;

import com.felpz.refugio.Refugio;
import com.felpz.refugio.story.RefugioStory;
import com.felpz.refugio.ui.RefugioUiService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class RefugioNPCs {
    private static final String SHOP_TAG = "refugio:merchant";
    private static final String QUEST_TAG = "refugio:quest_giver";
    private static final String GARDEN_TAG = "refugio:garden_quest_giver";
    private static final String EXPEDITION_TAG = "refugio:expedition_quest_giver";
    private static final String KAEL_ENTRANCE_TAG = "refugio:kael_entrance";
    private static final String KAEL_CASTLE_TAG = "refugio:kael_castle";
    private static final String KAEL_MOVED_TAG = "refugio:kael_moved_to_castle";

    private static final double SHOP_X = -274.645;
    private static final double SHOP_Y = 84.0;
    private static final double SHOP_Z = 100.843;
    private static final double QUEST_X = -288.306;
    private static final double QUEST_Y = 84.0;
    private static final double QUEST_Z = 107.596;
    private static final double GARDEN_X = -282.678, GARDEN_Y = 88.0, GARDEN_Z = 122.700;
    private static final double EXPEDITION_X = -279.675, EXPEDITION_Y = 90.0, EXPEDITION_Z = 132.639;
    private static final double KAEL_ENTRANCE_X = -249.503, KAEL_ENTRANCE_Y = 77.0, KAEL_ENTRANCE_Z = 64.194;
    private static final double KAEL_CASTLE_X = -323.750, KAEL_CASTLE_Y = 129.0, KAEL_CASTLE_Z = 211.976;
    private static final double LEGACY_KAEL_CASTLE_X = -252.561, LEGACY_KAEL_CASTLE_Y = 131.0, LEGACY_KAEL_CASTLE_Z = 232.750;

    private RefugioNPCs() { }

    public static void initialize() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || !(entity instanceof Villager villager)) {
                return InteractionResult.PASS;
            }
            if (villager.entityTags().contains(SHOP_TAG)) {
                RefugioUiService.openDialogue(serverPlayer, "entity.refugio.merchant",
                        java.util.List.of("dialogue.refugio.baldur.1", "dialogue.refugio.baldur.2"),
                        "OPEN_SHOP", "gui.refugio.dialogue.action.shop");
                return InteractionResult.SUCCESS;
            }
            if (villager.entityTags().contains(QUEST_TAG)) {
                RefugioUiService.openDialogue(serverPlayer, "entity.refugio.quest_giver",
                        java.util.List.of("dialogue.refugio.eldrin.1", "dialogue.refugio.eldrin.2"),
                        "OPEN_QUESTS", "gui.refugio.dialogue.action.quests");
                return InteractionResult.SUCCESS;
            }
            if (villager.entityTags().contains(GARDEN_TAG)) {
                RefugioUiService.openDialogue(serverPlayer, "entity.refugio.garden_quest_giver",
                        java.util.List.of("dialogue.refugio.marta.1", "dialogue.refugio.marta.2"),
                        "OPEN_GARDEN_QUESTS", "gui.refugio.dialogue.action.garden");
                return InteractionResult.SUCCESS;
            }
            if (villager.entityTags().contains(EXPEDITION_TAG)) {
                RefugioUiService.openDialogue(serverPlayer, "entity.refugio.expedition_quest_giver",
                        java.util.List.of("dialogue.refugio.dario.1", "dialogue.refugio.dario.2"),
                        "OPEN_EXPEDITION_QUESTS", "gui.refugio.dialogue.action.expedition");
                return InteractionResult.SUCCESS;
            }
            if (villager.entityTags().contains(KAEL_ENTRANCE_TAG)) {
                RefugioStory.talkToKael(serverPlayer, false);
                return InteractionResult.SUCCESS;
            }
            if (villager.entityTags().contains(KAEL_CASTLE_TAG)) {
                RefugioStory.talkToKael(serverPlayer, true);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        ServerLifecycleEvents.SERVER_STARTED.register(RefugioNPCs::spawnInRefuge);
    }

    private static void spawnInRefuge(MinecraftServer server) {
        ServerLevel world = server.getLevel(Refugio.DIMENSION);
        if (world == null) {
            Refugio.LOGGER.warn("Não foi possível criar os NPCs: a dimensão Stonehill não está disponível.");
            return;
        }

        // Carrega a área anterior para poder retirar os NPCs do primeiro posicionamento.
        world.getChunkAt(new BlockPos(-245, 76, 59));
        AABB oldArea = new AABB(-320, 65, 30, -225, 105, 145);
        for (Villager villager : world.getEntitiesOfClass(Villager.class, oldArea,
                entity -> entity.entityTags().contains(SHOP_TAG) || entity.entityTags().contains(QUEST_TAG)
                        || entity.entityTags().contains(GARDEN_TAG) || entity.entityTags().contains(EXPEDITION_TAG))) {
            // Current tags are respawned/renamed below; legacy copies are discarded only when far from target.
            if (villager.entityTags().contains(GARDEN_TAG) || villager.entityTags().contains(EXPEDITION_TAG)) villager.discard();
        }

        BlockPos shopChunk = new BlockPos((int) Math.floor(SHOP_X), (int) Math.floor(SHOP_Y), (int) Math.floor(SHOP_Z));
        BlockPos questChunk = new BlockPos((int) Math.floor(QUEST_X), (int) Math.floor(QUEST_Y), (int) Math.floor(QUEST_Z));
        BlockPos gardenChunk = new BlockPos((int) Math.floor(GARDEN_X), (int) Math.floor(GARDEN_Y), (int) Math.floor(GARDEN_Z));
        BlockPos expeditionChunk = new BlockPos((int) Math.floor(EXPEDITION_X), (int) Math.floor(EXPEDITION_Y), (int) Math.floor(EXPEDITION_Z));
        BlockPos kaelEntranceChunk = new BlockPos((int) Math.floor(KAEL_ENTRANCE_X), (int) Math.floor(KAEL_ENTRANCE_Y), (int) Math.floor(KAEL_ENTRANCE_Z));
        BlockPos kaelCastleChunk = new BlockPos((int) Math.floor(KAEL_CASTLE_X), (int) Math.floor(KAEL_CASTLE_Y), (int) Math.floor(KAEL_CASTLE_Z));
        BlockPos legacyKaelCastleChunk = new BlockPos((int) Math.floor(LEGACY_KAEL_CASTLE_X), (int) Math.floor(LEGACY_KAEL_CASTLE_Y), (int) Math.floor(LEGACY_KAEL_CASTLE_Z));
        world.getChunkAt(shopChunk);
        world.getChunkAt(questChunk);
        world.getChunkAt(gardenChunk);
        world.getChunkAt(expeditionChunk);
        world.getChunkAt(kaelEntranceChunk);
        world.getChunkAt(kaelCastleChunk);
        world.getChunkAt(legacyKaelCastleChunk);

        if (!hasNpc(world, SHOP_TAG, SHOP_X, SHOP_Y, SHOP_Z)) {
            spawnVillager(world, SHOP_X, SHOP_Y, SHOP_Z, SHOP_TAG,
                    "entity.refugio.merchant", VillagerProfession.TOOLSMITH, 90.6f, -4.0f);
        } else {
            setNpcRotation(world, SHOP_TAG, SHOP_X, SHOP_Y, SHOP_Z, 90.6f, -4.0f);
            renameNpc(world, SHOP_TAG, SHOP_X, SHOP_Y, SHOP_Z, "entity.refugio.merchant");
        }
        if (!hasNpc(world, QUEST_TAG, QUEST_X, QUEST_Y, QUEST_Z)) {
            spawnVillager(world, QUEST_X, QUEST_Y, QUEST_Z, QUEST_TAG,
                    "entity.refugio.quest_giver", VillagerProfession.LIBRARIAN, -134.0f, 1.0f);
        } else {
            setNpcRotation(world, QUEST_TAG, QUEST_X, QUEST_Y, QUEST_Z, -134.0f, 1.0f);
            renameNpc(world, QUEST_TAG, QUEST_X, QUEST_Y, QUEST_Z, "entity.refugio.quest_giver");
        }
        spawnOrUpdate(world, GARDEN_TAG, GARDEN_X, GARDEN_Y, GARDEN_Z,
                "entity.refugio.garden_quest_giver", VillagerProfession.FARMER, -94.6f, 0.9f);
        spawnOrUpdate(world, EXPEDITION_TAG, EXPEDITION_X, EXPEDITION_Y, EXPEDITION_Z,
                "entity.refugio.expedition_quest_giver", VillagerProfession.CLERIC, -100.0f, 6.2f);
        spawnOrUpdateKael(world);
        Refugio.LOGGER.info("NPCs do Refúgio: Baldur, Eldrin, Marta, Dário e Kael (um único local por vez).");
    }

    private static void spawnOrUpdateKael(ServerLevel world) {
        List<Villager> candidates = new ArrayList<>();
        candidates.addAll(world.getEntitiesOfClass(Villager.class, npcArea(KAEL_ENTRANCE_X, KAEL_ENTRANCE_Y, KAEL_ENTRANCE_Z),
                v -> v.entityTags().contains(KAEL_ENTRANCE_TAG) || v.entityTags().contains(KAEL_CASTLE_TAG)));
        candidates.addAll(world.getEntitiesOfClass(Villager.class, npcArea(KAEL_CASTLE_X, KAEL_CASTLE_Y, KAEL_CASTLE_Z),
                v -> v.entityTags().contains(KAEL_ENTRANCE_TAG) || v.entityTags().contains(KAEL_CASTLE_TAG)));
        candidates.addAll(world.getEntitiesOfClass(Villager.class,
                npcArea(LEGACY_KAEL_CASTLE_X, LEGACY_KAEL_CASTLE_Y, LEGACY_KAEL_CASTLE_Z),
                v -> v.entityTags().contains(KAEL_ENTRANCE_TAG) || v.entityTags().contains(KAEL_CASTLE_TAG)));

        Villager movedKael = candidates.stream().filter(v -> v.entityTags().contains(KAEL_MOVED_TAG)).findFirst().orElse(null);
        if (movedKael != null) {
            for (Villager candidate : candidates) if (candidate != movedKael) candidate.discard();
            configureKael(movedKael, KAEL_CASTLE_X, KAEL_CASTLE_Y, KAEL_CASTLE_Z,
                    KAEL_CASTLE_TAG, -113.9f, 10.7f);
            return;
        }

        Villager entranceKael = candidates.stream().filter(v -> v.entityTags().contains(KAEL_ENTRANCE_TAG)).findFirst().orElse(null);
        for (Villager candidate : candidates) if (candidate != entranceKael) candidate.discard();
        if (entranceKael == null) {
            spawnVillager(world, KAEL_ENTRANCE_X, KAEL_ENTRANCE_Y, KAEL_ENTRANCE_Z, KAEL_ENTRANCE_TAG,
                    "entity.refugio.kael", VillagerProfession.CLERIC, -128.9f, 6.6f);
        } else {
            configureKael(entranceKael, KAEL_ENTRANCE_X, KAEL_ENTRANCE_Y, KAEL_ENTRANCE_Z,
                    KAEL_ENTRANCE_TAG, -128.9f, 6.6f);
        }
    }

    public static boolean isKaelAtCastle(ServerLevel world) {
        if (!world.dimension().equals(Refugio.DIMENSION)) return false;
        world.getChunkAt(BlockPos.containing(KAEL_CASTLE_X, KAEL_CASTLE_Y, KAEL_CASTLE_Z));
        return !world.getEntitiesOfClass(Villager.class, npcArea(KAEL_CASTLE_X, KAEL_CASTLE_Y, KAEL_CASTLE_Z),
                v -> v.entityTags().contains(KAEL_CASTLE_TAG) && v.entityTags().contains(KAEL_MOVED_TAG)).isEmpty();
    }

    public static boolean moveKaelToCastle(ServerPlayer player) {
        ServerLevel world = (ServerLevel) player.level();
        if (!world.dimension().equals(Refugio.DIMENSION)) return false;
        if (isKaelAtCastle(world)) return true;

        world.getChunkAt(BlockPos.containing(KAEL_ENTRANCE_X, KAEL_ENTRANCE_Y, KAEL_ENTRANCE_Z));
        Villager entranceKael = world.getEntitiesOfClass(Villager.class,
                        npcArea(KAEL_ENTRANCE_X, KAEL_ENTRANCE_Y, KAEL_ENTRANCE_Z),
                        v -> v.entityTags().contains(KAEL_ENTRANCE_TAG))
                .stream().findFirst().orElse(null);
        if (entranceKael == null) return false;

        double oldX = entranceKael.getX();
        double oldY = entranceKael.getY();
        double oldZ = entranceKael.getZ();
        Villager castleKael = spawnVillager(world, KAEL_CASTLE_X, KAEL_CASTLE_Y, KAEL_CASTLE_Z,
                KAEL_CASTLE_TAG, "entity.refugio.kael", VillagerProfession.CLERIC, -113.9f, 10.7f);
        if (castleKael == null) return false;

        sendKaelMagicParticles(world, oldX, oldY + 1.0, oldZ);
        sendKaelMagicParticles(world, KAEL_CASTLE_X, KAEL_CASTLE_Y + 1.0, KAEL_CASTLE_Z);
        entranceKael.discard();
        player.sendSystemMessage(Component.translatable("message.refugio.kael.departed"));
        Refugio.LOGGER.info("Kael foi transferido da entrada para o castelo após conversar com {}.", player.getGameProfile().name());
        return true;
    }

    private static void sendKaelMagicParticles(ServerLevel world, double x, double y, double z) {
        world.sendParticles(ParticleTypes.PORTAL, true, true, x, y, z, 55, 0.45, 0.9, 0.45, 0.15);
    }

    private static void configureKael(Villager villager, double x, double y, double z,
                                       String locationTag, float yaw, float pitch) {
        villager.removeTag(KAEL_ENTRANCE_TAG);
        villager.removeTag(KAEL_CASTLE_TAG);
        villager.addTag(locationTag);
        if (KAEL_CASTLE_TAG.equals(locationTag)) villager.addTag(KAEL_MOVED_TAG);
        orient(villager, x, y, z, yaw, pitch);
        villager.setVillagerData(villager.getVillagerData().withProfession(villager.level().registryAccess(), VillagerProfession.CLERIC));
        villager.setCustomName(Component.translatable("entity.refugio.kael"));
        villager.setCustomNameVisible(true);
        villager.setNoAi(true);
        villager.setPersistenceRequired();
        villager.setInvulnerable(true);
        villager.setSilent(true);
    }

    private static AABB npcArea(double x, double y, double z) {
        return new AABB(x - 8.0, y - 5.0, z - 8.0, x + 8.0, y + 5.0, z + 8.0);
    }

    private static void spawnOrUpdate(ServerLevel world, String tag, double x, double y, double z,
                                      String nameKey, net.minecraft.resources.ResourceKey<VillagerProfession> profession,
                                      float yaw, float pitch) {
        if (!hasNpc(world, tag, x, y, z)) {
            spawnVillager(world, x, y, z, tag, nameKey, profession, yaw, pitch);
        } else {
            setNpcRotation(world, tag, x, y, z, yaw, pitch);
            renameNpc(world, tag, x, y, z, nameKey);
        }
    }

    private static void renameNpc(ServerLevel world, String tag, double x, double y, double z, String nameKey) {
        AABB area = new AABB(x - 8.0, y - 5.0, z - 8.0, x + 8.0, y + 5.0, z + 8.0);
        world.getEntitiesOfClass(Villager.class, area, v -> v.entityTags().contains(tag))
                .forEach(v -> v.setCustomName(Component.translatable(nameKey)));
    }

    private static boolean hasNpc(ServerLevel world, String tag, double x, double y, double z) {
        AABB area = new AABB(x - 8.0, y - 5.0, z - 8.0, x + 8.0, y + 5.0, z + 8.0);
        return !world.getEntitiesOfClass(Villager.class, area,
                villager -> villager.entityTags().contains(tag)).isEmpty();
    }

    private static void setNpcRotation(ServerLevel world, String tag, double x, double y, double z,
                                       float yaw, float pitch) {
        AABB area = new AABB(x - 8.0, y - 5.0, z - 8.0, x + 8.0, y + 5.0, z + 8.0);
        world.getEntitiesOfClass(Villager.class, area, villager -> villager.entityTags().contains(tag))
                .forEach(villager -> orient(villager, x, y, z, yaw, pitch));
    }

    private static Villager spawnVillager(ServerLevel world, double x, double y, double z, String tag,
                                      String nameKey, net.minecraft.resources.ResourceKey<VillagerProfession> profession,
                                      float yaw, float pitch) {
        var villagerType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getValue(net.minecraft.resources.Identifier.parse("minecraft:villager"));
        var created = villagerType.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (!(created instanceof Villager villager)) return null;
        orient(villager, x, y, z, yaw, pitch);
        villager.setVillagerData(villager.getVillagerData().withProfession(world.registryAccess(), profession));
        villager.setCustomName(Component.translatable(nameKey));
        villager.setCustomNameVisible(true);
        villager.setNoAi(true);
        villager.setPersistenceRequired();
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.addTag(tag);
        if (KAEL_CASTLE_TAG.equals(tag)) villager.addTag(KAEL_MOVED_TAG);
        if (!world.addFreshEntity(villager)) return null;
        return villager;
    }

    private static void orient(Villager villager, double x, double y, double z, float yaw, float pitch) {
        villager.setPos(x, y, z);
        villager.setYRot(yaw);
        villager.setXRot(pitch);
        villager.setYHeadRot(yaw);
        villager.setYBodyRot(yaw);
    }
}
