package com.felpz.refugio.item;

import com.felpz.refugio.Refugio;
import com.felpz.refugio.world.VillageStructure;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import com.mojang.serialization.Codec;

import java.util.List;

public final class RefugioItems {
    private static final ResourceKey<Item> CHAVE_KEY = ResourceKey.create(Registries.ITEM, Refugio.id("chave_refugio"));
    private static final AttachmentType<Boolean> STARTER_KEY_GIVEN = AttachmentRegistry.create(
            Refugio.id("starter_key_given"), builder -> builder.persistent(Codec.BOOL).initializer(() -> false));

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
