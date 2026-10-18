package com.felpz.refugio.world;

import com.felpz.refugio.Refugio;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Refuge-only crop hydration and delayed refill for map chests with an initial stock. */
public final class RefugioSustainability {
    private record SavedStack(int slot, ItemStack stack) {
        private static final Codec<SavedStack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("slot").forGetter(SavedStack::slot),
                ItemStack.CODEC.fieldOf("stack").forGetter(SavedStack::stack)
        ).apply(instance, SavedStack::new));
    }
    private static final long REFILL_DELAY_TICKS = 12_000L; // 10 minutes
    private static final AttachmentType<List<SavedStack>> INITIAL_STOCK = AttachmentRegistry.create(
            Refugio.id("chest_initial_stock"), builder -> builder.persistent(SavedStack.CODEC.listOf()).initializer(List::of));
    private static final AttachmentType<Boolean> STOCK_CAPTURED = AttachmentRegistry.create(
            Refugio.id("chest_stock_captured"), builder -> builder.persistent(Codec.BOOL).initializer(() -> false));
    private static final AttachmentType<Long> REFILL_AT = AttachmentRegistry.create(
            Refugio.id("chest_refill_at"), builder -> builder.persistent(Codec.LONG).initializer(() -> 0L));
    private static final Set<BlockPos> TRACKED_CHESTS = ConcurrentHashMap.newKeySet();
    private static final BlockPos.MutableBlockPos MUTABLE = new BlockPos.MutableBlockPos();

    private RefugioSustainability() { }

    public static void initialize() {
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk, fromDisk) -> {
            if (!world.dimension().equals(Refugio.DIMENSION)) return;
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (!(blockEntity instanceof ChestBlockEntity chest)) continue;
                if (!chest.getAttachedOrCreate(STOCK_CAPTURED)) {
                    List<SavedStack> initial = snapshot(chest);
                    chest.setAttached(INITIAL_STOCK, initial);
                    chest.setAttached(REFILL_AT, 0L);
                    chest.setAttached(STOCK_CAPTURED, true);
                    chest.setChanged();
                }
                List<SavedStack> stock = chest.getAttachedOrCreate(INITIAL_STOCK);
                if (!stock.isEmpty()) TRACKED_CHESTS.add(chest.getBlockPos().immutable());
            }
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            if (!world.dimension().equals(Refugio.DIMENSION)) return;
            int chunkX = chunk.getPos().x();
            int chunkZ = chunk.getPos().z();
            TRACKED_CHESTS.removeIf(pos -> (pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ);
        });
        ServerTickEvents.END_LEVEL_TICK.register(world -> {
            if (!world.dimension().equals(Refugio.DIMENSION)) return;
            if (world.getGameTime() % 20L == 0L) {
                refillEmptyChests(world);
                hydrateNearbyFarmland(world);
            }
        });
    }

    /** Restores initial contents for tracked refillable chests in currently loaded Refuge chunks. */
    public static int resetLoadedChests(ServerLevel world) {
        if (!world.dimension().equals(Refugio.DIMENSION)) return 0;
        int reset = 0;
        for (BlockPos position : TRACKED_CHESTS) {
            if (!world.hasChunkAt(position)) continue;
            BlockEntity blockEntity = world.getBlockEntity(position);
            if (!(blockEntity instanceof ChestBlockEntity chest)) continue;
            List<SavedStack> stock = chest.getAttachedOrCreate(INITIAL_STOCK);
            if (stock.isEmpty()) continue;
            chest.clearContent();
            for (SavedStack saved : stock) {
                if (saved.slot() >= 0 && saved.slot() < chest.getContainerSize()) {
                    chest.setItem(saved.slot(), saved.stack().copy());
                }
            }
            chest.setAttached(REFILL_AT, 0L);
            chest.setChanged();
            reset++;
        }
        return reset;
    }

    private static List<SavedStack> snapshot(ChestBlockEntity chest) {
        List<SavedStack> contents = new ArrayList<>();
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (!stack.isEmpty()) contents.add(new SavedStack(slot, stack.copy()));
        }
        return List.copyOf(contents);
    }

    private static void refillEmptyChests(ServerLevel world) {
        long now = world.getGameTime();
        for (BlockPos position : TRACKED_CHESTS) {
            if (!world.hasChunkAt(position)) {
                TRACKED_CHESTS.remove(position);
                continue;
            }
            BlockEntity blockEntity = world.getBlockEntity(position);
            if (!(blockEntity instanceof ChestBlockEntity chest)) {
                TRACKED_CHESTS.remove(position);
                continue;
            }
            List<SavedStack> stock = chest.getAttachedOrCreate(INITIAL_STOCK);
            if (stock.isEmpty()) {
                TRACKED_CHESTS.remove(position);
                continue;
            }
            boolean empty = true;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                if (!chest.getItem(slot).isEmpty()) {
                    empty = false;
                    break;
                }
            }
            long refillAt = chest.getAttachedOrCreate(REFILL_AT);
            if (!empty) {
                if (refillAt != 0L) chest.setAttached(REFILL_AT, 0L);
                continue;
            }
            if (refillAt == 0L) {
                chest.setAttached(REFILL_AT, now + REFILL_DELAY_TICKS);
                chest.setChanged();
            } else if (now >= refillAt) {
                chest.clearContent();
                for (SavedStack saved : stock) {
                    if (saved.slot() >= 0 && saved.slot() < chest.getContainerSize()) {
                        chest.setItem(saved.slot(), saved.stack().copy());
                    }
                }
                chest.setAttached(REFILL_AT, 0L);
                chest.setChanged();
            }
        }
    }

    private static void hydrateNearbyFarmland(ServerLevel world) {
        Set<BlockPos> checked = new HashSet<>();
        for (ServerPlayer player : world.players()) {
            BlockPos center = player.blockPosition();
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) {
                    for (int y = -4; y <= 4; y++) {
                        MUTABLE.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                        BlockPos position = MUTABLE.immutable();
                        if (!checked.add(position) || !world.hasChunkAt(position)) continue;
                        BlockState state = world.getBlockState(position);
                        if (state.is(Blocks.FARMLAND) && state.getValue(FarmlandBlock.MOISTURE) < 7) {
                            world.setBlock(position, state.setValue(FarmlandBlock.MOISTURE, 7), 2);
                        }
                    }
                }
            }
        }
    }
}
