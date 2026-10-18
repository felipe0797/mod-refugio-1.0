package com.felpz.refugio.item;

import com.felpz.refugio.Refugio;
import com.felpz.refugio.world.VillageStructure;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

public final class RefugioItems {
    private record SavedKeySlot(int slot, ItemStack stack) {
        private static final Codec<SavedKeySlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("slot").forGetter(SavedKeySlot::slot),
                ItemStack.CODEC.fieldOf("stack").forGetter(SavedKeySlot::stack)
        ).apply(instance, SavedKeySlot::new));
    }

    private static final ResourceKey<Item> CHAVE_KEY = ResourceKey.create(Registries.ITEM, Refugio.id("chave_refugio"));
    private static final AttachmentType<Boolean> STARTER_KEY_GIVEN = AttachmentRegistry.create(
            Refugio.id("starter_key_given"), builder -> builder.persistent(Codec.BOOL)
                    .initializer(() -> false).copyOnDeath());
    private static final AttachmentType<List<SavedKeySlot>> DEATH_SAVED_KEYS = AttachmentRegistry.create(
            Refugio.id("death_saved_keys"), builder -> builder.persistent(SavedKeySlot.CODEC.listOf())
                    .initializer(List::of).copyOnDeath());

    public static final Item CHAVE = Registry.register(
            BuiltInRegistries.ITEM,
            CHAVE_KEY,
            new RefugioKey(new Item.Properties().setId(CHAVE_KEY).stacksTo(1))
    );

    private RefugioItems() { }

    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.accept(CHAVE));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            restoreKeysAfterRespawn(player);
            if (!player.getAttachedOrCreate(STARTER_KEY_GIVEN)) {
                boolean alreadyHasKey = player.getInventory().contains(new ItemStack(CHAVE));
                boolean delivered = alreadyHasKey || player.getInventory().add(new ItemStack(CHAVE));
                if (delivered) {
                    player.setAttached(STARTER_KEY_GIVEN, true);
                }
            }
            refreshKeyTooltip(player);

            if (player.level().dimension().equals(Refugio.DIMENSION)) {
                VillageStructure.removeHostileMobs(player.level());
            }
        });

        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                restoreKeysAfterRespawn(newPlayer);
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            restoreKeysAfterRespawn(newPlayer);
            refreshKeyTooltip(newPlayer);
        });
    }

    /**
     * Captures any Refuge Key currently in the player's inventory right before death drops occur,
     * removing it from the dying inventory so it is never dropped on the ground or lost on death,
     * while never duplicating keys that the player stored in a chest or container before dying.
     */
    public static void captureKeysBeforeDeathDrop(ServerPlayer player) {
        if (player == null) return;
        Inventory inventory = player.getInventory();
        List<SavedKeySlot> captured = new ArrayList<>(player.getAttachedOrCreate(DEATH_SAVED_KEYS));
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(CHAVE)) {
                captured.add(new SavedKeySlot(slot, stack.copy()));
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        ItemStack carried = player.containerMenu != null ? player.containerMenu.getCarried() : ItemStack.EMPTY;
        if (!carried.isEmpty() && carried.is(CHAVE)) {
            captured.add(new SavedKeySlot(-1, carried.copy()));
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        if (!captured.isEmpty()) {
            player.setAttached(DEATH_SAVED_KEYS, List.copyOf(captured));
            inventory.setChanged();
        }
    }

    /** Restores any Refuge Key captured from the player's inventory upon death. */
    public static void restoreKeysAfterRespawn(ServerPlayer player) {
        if (player == null) return;
        List<SavedKeySlot> saved = player.getAttachedOrCreate(DEATH_SAVED_KEYS);
        if (saved.isEmpty()) return;
        player.setAttached(DEATH_SAVED_KEYS, List.of());

        Inventory inventory = player.getInventory();
        for (SavedKeySlot entry : saved) {
            ItemStack stack = entry.stack().copy();
            if (stack.isEmpty() || !stack.is(CHAVE)) continue;
            int targetSlot = entry.slot();
            if (targetSlot >= 0 && targetSlot < inventory.getContainerSize() && inventory.getItem(targetSlot).isEmpty()) {
                inventory.setItem(targetSlot, stack);
            } else if (!inventory.add(stack) && !stack.isEmpty()) {
                inventory.setItem(0, stack);
            }
        }
        inventory.setChanged();
        refreshKeyTooltip(player);
    }

    public static void refreshKeyTooltip(ServerPlayer player) {
        ItemLore lore = new ItemLore(List.of(
                Component.translatable("item.refugio.chave_refugio.desc"),
                Component.translatable("gui.refugio.balance_value", com.felpz.refugio.data.RefugioPlayerData.getCoins(player))));
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() == CHAVE) {
                stack.set(DataComponents.LORE, lore);
                changed = true;
            }
        }
        if (changed) player.getInventory().setChanged();
    }
}
