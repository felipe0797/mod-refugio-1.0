package com.felpz.refugio.data;

import com.felpz.refugio.Refugio;
import com.felpz.refugio.item.RefugioItems;
import com.felpz.refugio.story.RefugioStory;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

public final class RefugioPlayerData {
    public static final int MODE_UNSET = 0;
    public static final int MODE_FREE = 1;
    public static final int MODE_PROGRESSION = 2;
    public static final int STARTING_COINS = 50;

    private static final AttachmentType<Integer> COINS = AttachmentRegistry.create(
            Refugio.id("coins"), builder -> builder.persistent(Codec.INT).initializer(() -> 0).copyOnDeath());
    private static final AttachmentType<Integer> MODE = AttachmentRegistry.create(
            Refugio.id("game_mode"), builder -> builder.persistent(Codec.INT).initializer(() -> MODE_UNSET));
    private static final AttachmentType<Integer> CLAIMED_QUESTS = AttachmentRegistry.create(
            Refugio.id("claimed_quests"), builder -> builder.persistent(Codec.INT).initializer(() -> 0));
    private static final AttachmentType<Integer> QUEST_SEQUENCE_VERSION = AttachmentRegistry.create(
            Refugio.id("quest_sequence_version"), builder -> builder.persistent(Codec.INT).initializer(() -> 0));
    private static final AttachmentType<Boolean> STARTER_COINS_GIVEN = AttachmentRegistry.create(
            Refugio.id("starter_coins_given"), builder -> builder.persistent(Codec.BOOL).initializer(() -> false));
    private static final AttachmentType<Boolean> ADMIN_ALL_SHOP_CATEGORIES = AttachmentRegistry.create(
            Refugio.id("admin_all_shop_categories"), builder -> builder.persistent(Codec.BOOL)
                    .initializer(() -> false).copyOnDeath());

    private RefugioPlayerData() { }

    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (!player.getAttachedOrCreate(STARTER_COINS_GIVEN)) {
                addCoins(player, STARTING_COINS);
                player.setAttached(STARTER_COINS_GIVEN, true);
            }
            getMode(player);
            RefugioItems.refreshKeyTooltip(player);
            RefugioStory.scheduleStarterPackageDialogue(player);
        });
    }

    public static int getCoins(ServerPlayer player) {
        return Math.max(0, player.getAttachedOrCreate(COINS));
    }

    public static void addCoins(ServerPlayer player, int amount) {
        if (amount <= 0) return;
        int current = getCoins(player);
        player.setAttached(COINS, (int) Math.min(Integer.MAX_VALUE, (long) current + amount));
        RefugioItems.refreshKeyTooltip(player);
    }

    public static boolean spendCoins(ServerPlayer player, int amount) {
        if (amount < 0 || getCoins(player) < amount) return false;
        player.setAttached(COINS, getCoins(player) - amount);
        RefugioItems.refreshKeyTooltip(player);
        return true;
    }

    public static boolean setCoins(ServerPlayer player, int amount) {
        if (amount < 0) return false;
        player.setAttached(COINS, amount);
        RefugioItems.refreshKeyTooltip(player);
        return true;
    }

    public static int getMode(ServerPlayer player) {
        int savedMode = player.getAttachedOrCreate(MODE);
        if (savedMode != MODE_PROGRESSION) {
            player.setAttached(MODE, MODE_PROGRESSION);
            return MODE_PROGRESSION;
        }
        return savedMode;
    }

    public static boolean hasAllShopCategoriesUnlocked(ServerPlayer player) {
        return player.getAttachedOrCreate(ADMIN_ALL_SHOP_CATEGORIES);
    }

    public static void setAllShopCategoriesUnlocked(ServerPlayer player, boolean unlocked) {
        player.setAttached(ADMIN_ALL_SHOP_CATEGORIES, unlocked);
    }

    public static int getClaimedQuestCount(ServerPlayer player) {
        int claimed = Math.max(0, player.getAttachedOrCreate(CLAIMED_QUESTS));
        if (player.getAttachedOrCreate(QUEST_SEQUENCE_VERSION) < 1) {
            // The obsidian quest was inserted after the second mission in the old sequence.
            if (claimed >= 3) claimed++;
            claimed = Math.min(8, claimed);
            player.setAttached(CLAIMED_QUESTS, claimed);
            player.setAttached(QUEST_SEQUENCE_VERSION, 1);
        }
        return Math.min(8, claimed);
    }

    public static boolean markQuestClaimed(ServerPlayer player, int questIndex) {
        if (questIndex != getClaimedQuestCount(player) || questIndex >= 8) return false;
        player.setAttached(CLAIMED_QUESTS, questIndex + 1);
        return true;
    }
}
