package com.felpz.refugio.world;

import com.felpz.refugio.Refugio;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/** Refuge-only automatic crop/farmland hydration near players. */
public final class RefugioSustainability {
    private static final BlockPos.MutableBlockPos MUTABLE = new BlockPos.MutableBlockPos();

    private RefugioSustainability() { }

    public static void initialize() {
        ServerTickEvents.END_LEVEL_TICK.register(world -> {
            if (!world.dimension().equals(Refugio.DIMENSION)) return;
            if (world.getGameTime() % 20L == 0L) {
                hydrateNearbyFarmland(world);
            }
        });
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
