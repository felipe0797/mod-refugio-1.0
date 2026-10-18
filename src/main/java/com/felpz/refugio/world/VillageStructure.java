package com.felpz.refugio.world;

import com.felpz.refugio.Refugio;
import com.felpz.refugio.npc.RefugioTrainingDummy;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/** Shared cleanup for the legacy dimension and the imported Stonehill map. */
public final class VillageStructure {
    private static final AttachmentType<Boolean> MOUNTAINS_BUILT = AttachmentRegistry.create(
            Refugio.id("mountains_built"), builder -> builder.persistent(Codec.BOOL).initializer(() -> false));

    private VillageStructure() { }

    public static void removeHostileMobs(ServerLevel world) {
        List<Entity> hostiles = new ArrayList<>();
        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof Enemy && !RefugioTrainingDummy.isTrainingDummy(entity)) hostiles.add(entity);
        }
        for (Entity hostile : hostiles) hostile.discard();
    }

    public static void removeBottomBedrock(ServerLevel world, LevelChunk chunk) {
        int y = world.getMinY();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                BlockPos position = new BlockPos(x, y, z);
                if (chunk.getBlockState(position).is(Blocks.BEDROCK)) {
                    world.setBlock(position, air, 2);
                }
            }
        }
    }

    public static void sanitizeChunk(ServerLevel world, LevelChunk chunk) {
        removeBottomBedrock(world, chunk);
        List<BlockPos> spawners = new ArrayList<>();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            BlockState state = blockEntity.getBlockState();
            if (state.is(Blocks.SPAWNER) || state.is(Blocks.TRIAL_SPAWNER)) {
                spawners.add(blockEntity.getBlockPos());
            }
        }
        for (BlockPos position : spawners) {
            world.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    /** Removes only blocks matching the old generated rim, preserving later edits. */
    public static void removePreviousMountainRim(ServerLevel world) {
        if (!world.getAttachedOrCreate(MOUNTAINS_BUILT)) return;
        int changed = 0;
        for (int x = -180; x <= 180; x++) {
            for (int z = -180; z <= 180; z++) {
                int edge = Math.max(Math.abs(x), Math.abs(z));
                if (edge < 168 || edge > 176) continue;
                double ridge = 1.0 - Math.abs(edge - 172.0) / 5.0;
                double variation = Math.sin(x * 0.083) * 0.38
                        + Math.cos(z * 0.071) * 0.34
                        + Math.sin((x + z) * 0.043) * 0.28;
                int height = 16 + (int) Math.round(Math.max(0.0, ridge) * (47.0 + variation * 14.0));
                for (int y = 0; y <= height; y++) {
                    BlockPos position = new BlockPos(x, y, z);
                    if (world.getBlockState(position).equals(previousMountainState(x, z, y, height))
                            && world.setBlock(position, Blocks.AIR.defaultBlockState(), 2)) {
                        changed++;
                    }
                }
            }
        }
        world.setAttached(MOUNTAINS_BUILT, false);
        Refugio.LOGGER.info("Anel de montanhas antigo removido ({} blocos alterados).", changed);
    }

    private static BlockState previousMountainState(int x, int z, int y, int height) {
        if (y == height) {
            return height > 58 ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
        }
        if (y >= height - 3) return Blocks.DIRT.defaultBlockState();
        if (y < 5 && ((x * 31 + z * 17 + y) & 7) == 0) return Blocks.COBBLESTONE.defaultBlockState();
        if (((x * 13 + z * 7 + y * 3) & 15) < 3) return Blocks.ANDESITE.defaultBlockState();
        return Blocks.STONE.defaultBlockState();
    }
}
