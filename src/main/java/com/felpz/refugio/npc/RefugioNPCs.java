package com.felpz.refugio.npc;

import com.felpz.refugio.Refugio;
import com.felpz.refugio.story.RefugioStory;
import com.felpz.refugio.ui.RefugioUiService;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class RefugioNPCs {
    public static final String COMMANDER_TAG = "refugio:commander_elias";
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
    private static final float SHOP_YAW = 90.6f;
    private static final float SHOP_PITCH = -4.0f;

    private static final double QUEST_X = -288.306;
    private static final double QUEST_Y = 84.0;
    private static final double QUEST_Z = 107.596;
    private static final float QUEST_YAW = -134.0f;
    private static final float QUEST_PITCH = 1.0f;

    private static final double GARDEN_X = -282.700, GARDEN_Y = 88.0, GARDEN_Z = 121.956;
    private static final float GARDEN_YAW = -89.3f, GARDEN_PITCH = 1.0f;

    private static final double EXPEDITION_X = -279.615, EXPEDITION_Y = 90.0, EXPEDITION_Z = 131.991;
    private static final float EXPEDITION_YAW = -91.6f, EXPEDITION_PITCH = 8.4f;

    private static final double KAEL_ENTRANCE_X = -249.503, KAEL_ENTRANCE_Y = 77.0, KAEL_ENTRANCE_Z = 64.194;
    private static final float KAEL_ENTRANCE_YAW = -128.9f, KAEL_ENTRANCE_PITCH = 6.6f;

    private static final double KAEL_CASTLE_X = -323.750, KAEL_CASTLE_Y = 129.0, KAEL_CASTLE_Z = 211.976;
    private static final float KAEL_CASTLE_YAW = -113.9f, KAEL_CASTLE_PITCH = 10.7f;

    public static final double COMMANDER_X = -327.500, COMMANDER_Y = 129.0, COMMANDER_Z = 209.500;
    public static final float COMMANDER_YAW = -55.0f, COMMANDER_PITCH = 0.0f;

    private static final double LEGACY_KAEL_CASTLE_X = -252.561, LEGACY_KAEL_CASTLE_Y = 131.0, LEGACY_KAEL_CASTLE_Z = 232.750;
    private static final int RECOVERY_DELAY_TICKS = 40;

    private static final AttachmentType<Boolean> KAEL_AT_CASTLE_STATE = AttachmentRegistry.create(
            Refugio.id("kael_at_castle_state"), builder -> builder.persistent(Codec.BOOL).initializer(() -> false));

    private static int pendingRecoveryTicks = -1;

    private RefugioNPCs() { }

    public static void initialize() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || !(entity instanceof Villager villager)) {
                return InteractionResult.PASS;
            }
            if (villager.entityTags().contains(SHOP_TAG)) {
                RefugioUiService.openDialogue(serverPlayer, "entity.refugio.merchant",
                        List.of("dialogue.refugio.baldur.1", "dialogue.refugio.baldur.2"),
                        "OPEN_SHOP", "gui.refugio.dialogue.action.shop");
                return InteractionResult.SUCCESS;
            }
            if (villager.entityTags().contains(QUEST_TAG)) {
                RefugioUiService.openDialogue(serverPlayer, "entity.refugio.quest_giver",
                        List.of("dialogue.refugio.eldrin.1", "dialogue.refugio.eldrin.2"),
                        "OPEN_QUESTS", "gui.refugio.dialogue.action.quests");
                return InteractionResult.SUCCESS;
            }
            if (villager.entityTags().contains(GARDEN_TAG)) {
                RefugioUiService.openDialogue(serverPlayer, "entity.refugio.garden_quest_giver",
                        List.of("dialogue.refugio.marta.1", "dialogue.refugio.marta.2"),
                        "OPEN_GARDEN_QUESTS", "gui.refugio.dialogue.action.garden");
                return InteractionResult.SUCCESS;
            }
            if (villager.entityTags().contains(EXPEDITION_TAG)) {
                RefugioUiService.openDialogue(serverPlayer, "entity.refugio.expedition_quest_giver",
                        List.of("dialogue.refugio.dario.1", "dialogue.refugio.dario.2"),
                        "OPEN_EXPEDITION_QUESTS", "gui.refugio.dialogue.action.expedition");
                return InteractionResult.SUCCESS;
            }
            if (villager.entityTags().contains(COMMANDER_TAG)) {
                RefugioUiService.openDialogue(serverPlayer, "entity.refugio.commander_elias",
                        List.of("dialogue.refugio.elias.1", "dialogue.refugio.elias.2"),
                        "OPEN_COMMANDER", "gui.refugio.dialogue.action.commander");
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

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!isRefugeNpc(entity)) return true;
            return source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (isRefugeNpc(entity)) {
                pendingRecoveryTicks = RECOVERY_DELAY_TICKS;
            }
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!world.dimension().equals(Refugio.DIMENSION) || !(entity instanceof Villager villager)) return;
            enforceNpcProtectionOnLoad(world, villager);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(RefugioNPCs::spawnInRefuge);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> pendingRecoveryTicks = -1);

        ServerTickEvents.END_LEVEL_TICK.register(world -> {
            if (!world.dimension().equals(Refugio.DIMENSION)) return;
            if (pendingRecoveryTicks > 0) {
                pendingRecoveryTicks--;
                if (pendingRecoveryTicks == 0) {
                    pendingRecoveryTicks = -1;
                    ensureAllNpcs(world);
                }
                return;
            }
            if (!world.players().isEmpty() && world.getGameTime() % RECOVERY_DELAY_TICKS == 0L) {
                ensureAllNpcs(world);
            }
        });
    }

    public static boolean isRefugeNpc(Entity entity) {
        if (!(entity instanceof Villager villager)) return false;
        var tags = villager.entityTags();
        return tags.contains(SHOP_TAG)
                || tags.contains(QUEST_TAG)
                || tags.contains(GARDEN_TAG)
                || tags.contains(EXPEDITION_TAG)
                || tags.contains(COMMANDER_TAG)
                || tags.contains(KAEL_ENTRANCE_TAG)
                || tags.contains(KAEL_CASTLE_TAG);
    }

    private static void enforceNpcProtectionOnLoad(ServerLevel world, Villager villager) {
        var tags = villager.entityTags();
        if (tags.contains(SHOP_TAG)) {
            configureVillager(villager, SHOP_X, SHOP_Y, SHOP_Z, SHOP_TAG,
                    "entity.refugio.merchant", VillagerProfession.TOOLSMITH, SHOP_YAW, SHOP_PITCH);
        } else if (tags.contains(QUEST_TAG)) {
            configureVillager(villager, QUEST_X, QUEST_Y, QUEST_Z, QUEST_TAG,
                    "entity.refugio.quest_giver", VillagerProfession.LIBRARIAN, QUEST_YAW, QUEST_PITCH);
        } else if (tags.contains(GARDEN_TAG)) {
            configureVillager(villager, GARDEN_X, GARDEN_Y, GARDEN_Z, GARDEN_TAG,
                    "entity.refugio.garden_quest_giver", VillagerProfession.FARMER, GARDEN_YAW, GARDEN_PITCH);
        } else if (tags.contains(EXPEDITION_TAG)) {
            configureVillager(villager, EXPEDITION_X, EXPEDITION_Y, EXPEDITION_Z, EXPEDITION_TAG,
                    "entity.refugio.expedition_quest_giver", VillagerProfession.CLERIC, EXPEDITION_YAW, EXPEDITION_PITCH);
        } else if (tags.contains(COMMANDER_TAG)) {
            configureVillager(villager, COMMANDER_X, COMMANDER_Y, COMMANDER_Z, COMMANDER_TAG,
                    "entity.refugio.commander_elias", VillagerProfession.WEAPONSMITH, COMMANDER_YAW, COMMANDER_PITCH);
        } else if (tags.contains(KAEL_CASTLE_TAG) || tags.contains(KAEL_MOVED_TAG)) {
            world.setAttached(KAEL_AT_CASTLE_STATE, true);
            configureKael(villager, KAEL_CASTLE_X, KAEL_CASTLE_Y, KAEL_CASTLE_Z,
                    KAEL_CASTLE_TAG, KAEL_CASTLE_YAW, KAEL_CASTLE_PITCH);
        } else if (tags.contains(KAEL_ENTRANCE_TAG)) {
            if (world.getAttachedOrCreate(KAEL_AT_CASTLE_STATE)) {
                configureKael(villager, KAEL_CASTLE_X, KAEL_CASTLE_Y, KAEL_CASTLE_Z,
                        KAEL_CASTLE_TAG, KAEL_CASTLE_YAW, KAEL_CASTLE_PITCH);
            } else {
                configureKael(villager, KAEL_ENTRANCE_X, KAEL_ENTRANCE_Y, KAEL_ENTRANCE_Z,
                        KAEL_ENTRANCE_TAG, KAEL_ENTRANCE_YAW, KAEL_ENTRANCE_PITCH);
            }
        }
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
            if (villager.entityTags().contains(GARDEN_TAG)
                    && villager.distanceToSqr(GARDEN_X, GARDEN_Y, GARDEN_Z) > 64.0) {
                villager.discard();
            } else if (villager.entityTags().contains(EXPEDITION_TAG)
                    && villager.distanceToSqr(EXPEDITION_X, EXPEDITION_Y, EXPEDITION_Z) > 64.0) {
                villager.discard();
            }
        }

        ensureAllNpcs(world);
        Refugio.LOGGER.info("NPCs do Refúgio: Baldur, Eldrin, Marta, Dário, Comandante Elias Varga e Kael.");
    }

    private static void ensureAllNpcs(ServerLevel world) {
        BlockPos shopChunk = new BlockPos((int) Math.floor(SHOP_X), (int) Math.floor(SHOP_Y), (int) Math.floor(SHOP_Z));
        BlockPos questChunk = new BlockPos((int) Math.floor(QUEST_X), (int) Math.floor(QUEST_Y), (int) Math.floor(QUEST_Z));
        BlockPos gardenChunk = new BlockPos((int) Math.floor(GARDEN_X), (int) Math.floor(GARDEN_Y), (int) Math.floor(GARDEN_Z));
        BlockPos expeditionChunk = new BlockPos((int) Math.floor(EXPEDITION_X), (int) Math.floor(EXPEDITION_Y), (int) Math.floor(EXPEDITION_Z));
        BlockPos commanderChunk = new BlockPos((int) Math.floor(COMMANDER_X), (int) Math.floor(COMMANDER_Y), (int) Math.floor(COMMANDER_Z));
        BlockPos kaelEntranceChunk = new BlockPos((int) Math.floor(KAEL_ENTRANCE_X), (int) Math.floor(KAEL_ENTRANCE_Y), (int) Math.floor(KAEL_ENTRANCE_Z));
        BlockPos kaelCastleChunk = new BlockPos((int) Math.floor(KAEL_CASTLE_X), (int) Math.floor(KAEL_CASTLE_Y), (int) Math.floor(KAEL_CASTLE_Z));
        BlockPos legacyKaelCastleChunk = new BlockPos((int) Math.floor(LEGACY_KAEL_CASTLE_X), (int) Math.floor(LEGACY_KAEL_CASTLE_Y), (int) Math.floor(LEGACY_KAEL_CASTLE_Z));
        world.getChunkAt(shopChunk);
        world.getChunkAt(questChunk);
        world.getChunkAt(gardenChunk);
        world.getChunkAt(expeditionChunk);
        world.getChunkAt(commanderChunk);
        world.getChunkAt(kaelEntranceChunk);
        world.getChunkAt(kaelCastleChunk);
        world.getChunkAt(legacyKaelCastleChunk);

        spawnOrUpdate(world, SHOP_TAG, SHOP_X, SHOP_Y, SHOP_Z,
                "entity.refugio.merchant", VillagerProfession.TOOLSMITH, SHOP_YAW, SHOP_PITCH);
        spawnOrUpdate(world, QUEST_TAG, QUEST_X, QUEST_Y, QUEST_Z,
                "entity.refugio.quest_giver", VillagerProfession.LIBRARIAN, QUEST_YAW, QUEST_PITCH);
        spawnOrUpdate(world, GARDEN_TAG, GARDEN_X, GARDEN_Y, GARDEN_Z,
                "entity.refugio.garden_quest_giver", VillagerProfession.FARMER, GARDEN_YAW, GARDEN_PITCH);
        spawnOrUpdate(world, EXPEDITION_TAG, EXPEDITION_X, EXPEDITION_Y, EXPEDITION_Z,
                "entity.refugio.expedition_quest_giver", VillagerProfession.CLERIC, EXPEDITION_YAW, EXPEDITION_PITCH);
        spawnOrUpdate(world, COMMANDER_TAG, COMMANDER_X, COMMANDER_Y, COMMANDER_Z,
                "entity.refugio.commander_elias", VillagerProfession.WEAPONSMITH, COMMANDER_YAW, COMMANDER_PITCH);
        spawnOrUpdateKael(world);
    }

    private static void spawnOrUpdateKael(ServerLevel world) {
        List<Villager> candidates = new ArrayList<>();
        for (Villager v : world.getEntitiesOfClass(Villager.class, npcArea(KAEL_ENTRANCE_X, KAEL_ENTRANCE_Y, KAEL_ENTRANCE_Z),
                v -> v.isAlive() && (v.entityTags().contains(KAEL_ENTRANCE_TAG) || v.entityTags().contains(KAEL_CASTLE_TAG)))) {
            if (!candidates.contains(v)) candidates.add(v);
        }
        for (Villager v : world.getEntitiesOfClass(Villager.class, npcArea(KAEL_CASTLE_X, KAEL_CASTLE_Y, KAEL_CASTLE_Z),
                v -> v.isAlive() && (v.entityTags().contains(KAEL_ENTRANCE_TAG) || v.entityTags().contains(KAEL_CASTLE_TAG)))) {
            if (!candidates.contains(v)) candidates.add(v);
        }
        for (Villager v : world.getEntitiesOfClass(Villager.class,
                npcArea(LEGACY_KAEL_CASTLE_X, LEGACY_KAEL_CASTLE_Y, LEGACY_KAEL_CASTLE_Z),
                v -> v.isAlive() && (v.entityTags().contains(KAEL_ENTRANCE_TAG) || v.entityTags().contains(KAEL_CASTLE_TAG)))) {
            if (!candidates.contains(v)) candidates.add(v);
        }

        boolean playerAdvancedKael = world.getServer().getPlayerList().getPlayers().stream()
                .anyMatch(p -> RefugioStory.hasSeenArrivalIntro(p) || RefugioStory.hasSeenCastleIntro(p));
        Villager movedKael = candidates.stream()
                .filter(v -> v.entityTags().contains(KAEL_MOVED_TAG) || v.entityTags().contains(KAEL_CASTLE_TAG))
                .findFirst().orElse(null);

        boolean shouldBeAtCastle = world.getAttachedOrCreate(KAEL_AT_CASTLE_STATE)
                || movedKael != null
                || playerAdvancedKael;

        if (shouldBeAtCastle) {
            world.setAttached(KAEL_AT_CASTLE_STATE, true);
            Villager chosen = movedKael != null ? movedKael : (candidates.isEmpty() ? null : candidates.getFirst());
            for (Villager candidate : candidates) {
                if (candidate != chosen) candidate.discard();
            }
            if (chosen == null) {
                spawnVillager(world, KAEL_CASTLE_X, KAEL_CASTLE_Y, KAEL_CASTLE_Z, KAEL_CASTLE_TAG,
                        "entity.refugio.kael", VillagerProfession.CLERIC, KAEL_CASTLE_YAW, KAEL_CASTLE_PITCH);
            } else {
                configureKael(chosen, KAEL_CASTLE_X, KAEL_CASTLE_Y, KAEL_CASTLE_Z,
                        KAEL_CASTLE_TAG, KAEL_CASTLE_YAW, KAEL_CASTLE_PITCH);
            }
            return;
        }

        Villager entranceKael = candidates.stream().filter(v -> v.entityTags().contains(KAEL_ENTRANCE_TAG)).findFirst().orElse(null);
        for (Villager candidate : candidates) if (candidate != entranceKael) candidate.discard();
        if (entranceKael == null) {
            spawnVillager(world, KAEL_ENTRANCE_X, KAEL_ENTRANCE_Y, KAEL_ENTRANCE_Z, KAEL_ENTRANCE_TAG,
                    "entity.refugio.kael", VillagerProfession.CLERIC, KAEL_ENTRANCE_YAW, KAEL_ENTRANCE_PITCH);
        } else {
            configureKael(entranceKael, KAEL_ENTRANCE_X, KAEL_ENTRANCE_Y, KAEL_ENTRANCE_Z,
                    KAEL_ENTRANCE_TAG, KAEL_ENTRANCE_YAW, KAEL_ENTRANCE_PITCH);
        }
    }

    public static boolean isKaelAtCastle(ServerLevel world) {
        if (!world.dimension().equals(Refugio.DIMENSION)) return false;
        world.getChunkAt(BlockPos.containing(KAEL_CASTLE_X, KAEL_CASTLE_Y, KAEL_CASTLE_Z));
        boolean present = !world.getEntitiesOfClass(Villager.class, npcArea(KAEL_CASTLE_X, KAEL_CASTLE_Y, KAEL_CASTLE_Z),
                v -> v.isAlive() && v.entityTags().contains(KAEL_CASTLE_TAG) && v.entityTags().contains(KAEL_MOVED_TAG)).isEmpty();
        if (present) {
            world.setAttached(KAEL_AT_CASTLE_STATE, true);
            return true;
        }
        if (world.getAttachedOrCreate(KAEL_AT_CASTLE_STATE)) {
            spawnOrUpdateKael(world);
            return !world.getEntitiesOfClass(Villager.class, npcArea(KAEL_CASTLE_X, KAEL_CASTLE_Y, KAEL_CASTLE_Z),
                    v -> v.isAlive() && v.entityTags().contains(KAEL_CASTLE_TAG) && v.entityTags().contains(KAEL_MOVED_TAG)).isEmpty();
        }
        return false;
    }

    public static boolean moveKaelToCastle(ServerPlayer player) {
        ServerLevel world = (ServerLevel) player.level();
        if (!world.dimension().equals(Refugio.DIMENSION)) return false;
        if (isKaelAtCastle(world)) return true;

        world.getChunkAt(BlockPos.containing(KAEL_ENTRANCE_X, KAEL_ENTRANCE_Y, KAEL_ENTRANCE_Z));
        Villager entranceKael = world.getEntitiesOfClass(Villager.class,
                        npcArea(KAEL_ENTRANCE_X, KAEL_ENTRANCE_Y, KAEL_ENTRANCE_Z),
                        v -> v.isAlive() && v.entityTags().contains(KAEL_ENTRANCE_TAG))
                .stream().findFirst().orElse(null);

        double oldX = entranceKael != null ? entranceKael.getX() : KAEL_ENTRANCE_X;
        double oldY = entranceKael != null ? entranceKael.getY() : KAEL_ENTRANCE_Y;
        double oldZ = entranceKael != null ? entranceKael.getZ() : KAEL_ENTRANCE_Z;
        world.setAttached(KAEL_AT_CASTLE_STATE, true);
        Villager castleKael = spawnVillager(world, KAEL_CASTLE_X, KAEL_CASTLE_Y, KAEL_CASTLE_Z,
                KAEL_CASTLE_TAG, "entity.refugio.kael", VillagerProfession.CLERIC, KAEL_CASTLE_YAW, KAEL_CASTLE_PITCH);
        if (castleKael == null) return false;

        sendKaelMagicParticles(world, oldX, oldY + 1.0, oldZ);
        sendKaelMagicParticles(world, KAEL_CASTLE_X, KAEL_CASTLE_Y + 1.0, KAEL_CASTLE_Z);
        if (entranceKael != null) {
            entranceKael.discard();
        }
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
        if (KAEL_CASTLE_TAG.equals(locationTag)) {
            villager.addTag(KAEL_MOVED_TAG);
        }
        configureVillager(villager, x, y, z, locationTag,
                "entity.refugio.kael", VillagerProfession.CLERIC, yaw, pitch);
    }

    private static void configureVillager(Villager villager, double x, double y, double z, String tag,
                                          String nameKey, ResourceKey<VillagerProfession> profession,
                                          float yaw, float pitch) {
        orient(villager, x, y, z, yaw, pitch);
        villager.setVillagerData(villager.getVillagerData().withProfession(villager.level().registryAccess(), profession));
        villager.setCustomName(Component.translatable(nameKey));
        villager.setCustomNameVisible(true);
        villager.setNoAi(true);
        villager.setPersistenceRequired();
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.clearFire();
        if (villager.getHealth() < villager.getMaxHealth()) {
            villager.setHealth(villager.getMaxHealth());
        }
        villager.addTag(tag);
        if (KAEL_CASTLE_TAG.equals(tag)) {
            villager.addTag(KAEL_MOVED_TAG);
        }
    }

    private static AABB npcArea(double x, double y, double z) {
        return new AABB(x - 8.0, y - 5.0, z - 8.0, x + 8.0, y + 5.0, z + 8.0);
    }

    private static void spawnOrUpdate(ServerLevel world, String tag, double x, double y, double z,
                                      String nameKey, ResourceKey<VillagerProfession> profession,
                                      float yaw, float pitch) {
        List<Villager> matches = world.getEntitiesOfClass(Villager.class, npcArea(x, y, z),
                villager -> villager.isAlive() && villager.entityTags().contains(tag));
        if (matches.isEmpty()) {
            spawnVillager(world, x, y, z, tag, nameKey, profession, yaw, pitch);
        } else {
            Villager primary = matches.getFirst();
            for (int i = 1; i < matches.size(); i++) {
                matches.get(i).discard();
            }
            configureVillager(primary, x, y, z, tag, nameKey, profession, yaw, pitch);
        }
    }

    private static Villager spawnVillager(ServerLevel world, double x, double y, double z, String tag,
                                          String nameKey, ResourceKey<VillagerProfession> profession,
                                          float yaw, float pitch) {
        var villagerType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:villager"));
        var created = villagerType.create(world, EntitySpawnReason.MOB_SUMMONED);
        if (!(created instanceof Villager villager)) return null;
        configureVillager(villager, x, y, z, tag, nameKey, profession, yaw, pitch);
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
