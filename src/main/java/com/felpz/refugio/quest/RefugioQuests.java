package com.felpz.refugio.quest;

import com.felpz.refugio.Refugio;
import com.felpz.refugio.data.RefugioPlayerData;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;

public final class RefugioQuests {
    public record Mission(String advancement, String titleKey, String descriptionKey, int coins,
                          String rewardItem, int rewardCount) { }

    private static final List<Mission> MISSIONS = List.of(
            new Mission("story/mine_stone", "quest.refugio.title.0", "quest.refugio.description.0", 40, "minecraft:torch", 16),
            new Mission("story/iron_tools", "quest.refugio.title.1", "quest.refugio.description.1", 60, "minecraft:iron_chestplate", 1),
            new Mission("story/form_obsidian", "quest.refugio.title.2", "quest.refugio.description.2", 100, "minecraft:diamond_leggings", 1),
            new Mission("story/enter_the_nether", "quest.refugio.title.3", "quest.refugio.description.3", 200, "minecraft:golden_apple", 16),
            new Mission("nether/obtain_blaze_rod", "quest.refugio.title.4", "quest.refugio.description.4", 500, "minecraft:ender_eye", 20),
            new Mission("story/follow_ender_eye", "quest.refugio.title.5", "quest.refugio.description.5", 500, "minecraft:ender_eye", 12),
            new Mission("story/enter_the_end", "quest.refugio.title.6", "quest.refugio.description.6", 1000, "minecraft:firework_rocket", 64),
            new Mission("end/kill_dragon", "quest.refugio.title.7", "quest.refugio.description.7", 10000, "minecraft:dragon_breath", 32)
    );

    private RefugioQuests() { }

    public static int count() { return MISSIONS.size(); }
    public static Mission mission(int index) { return MISSIONS.get(index); }

    public static boolean isAdvancementComplete(ServerPlayer player, int index) {
        if (index < 0 || index >= MISSIONS.size()) return false;
        Mission mission = MISSIONS.get(index);
        AdvancementHolder holder = player.level().getServer().getAdvancements()
                .get(Identifier.parse("minecraft:" + mission.advancement()));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    public static boolean claimNext(ServerPlayer player) {
        int index = RefugioPlayerData.getClaimedQuestCount(player);
        if (index >= MISSIONS.size() || !isAdvancementComplete(player, index)) return false;
        Mission mission = MISSIONS.get(index);
        if (!RefugioPlayerData.markQuestClaimed(player, index)) return false;

        RefugioPlayerData.addCoins(player, mission.coins());
        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(mission.rewardItem()));
        ItemStack reward = new ItemStack(item, mission.rewardCount());
        if (item == Items.FIREWORK_ROCKET) {
            reward.set(net.minecraft.core.component.DataComponents.FIREWORKS, new Fireworks(3, List.of()));
        }
        CompoundTag marker = new CompoundTag();
        marker.putBoolean("refugio_quest_reward", true);
        CustomData.set(DataComponents.CUSTOM_DATA, reward, marker);
        player.getInventory().add(reward);
        if (!reward.isEmpty()) player.drop(reward, false);
        player.sendSystemMessage(Component.translatable("message.refugio.quest_claimed",
                Component.translatable(mission.titleKey()), mission.coins()));
        return true;
    }

    public static boolean isCategoryUnlocked(ServerPlayer player, int categoryIndex) {
        if (RefugioPlayerData.hasAllShopCategoriesUnlocked(player)) return true;
        int mode = RefugioPlayerData.getMode(player);
        if (mode != RefugioPlayerData.MODE_PROGRESSION) return false;
        int claims = RefugioPlayerData.getClaimedQuestCount(player);
        return switch (categoryIndex) {
            case 0 -> true;
            case 1 -> claims >= 1;
            case 2 -> claims >= 2;
            case 3 -> claims >= 4;
            case 4 -> claims >= 8;
            default -> false;
        };
    }

    public static int claimsRequiredForCategory(int categoryIndex) {
        return switch (categoryIndex) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 4;
            case 4 -> 8;
            default -> Integer.MAX_VALUE;
        };
    }
}
