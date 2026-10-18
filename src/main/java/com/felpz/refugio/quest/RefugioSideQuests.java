package com.felpz.refugio.quest;

import com.felpz.refugio.Refugio;
import com.felpz.refugio.data.RefugioPlayerData;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

/** Optional Refuge quest tracks; item deliveries are charged only when the player claims the current quest. */
public final class RefugioSideQuests {
    public enum Track { GARDEN, EXPEDITION }
    private enum Objective { DELIVERY, ADVANCEMENT, SLEEP, TRADE, BOAT, ENCHANTED_BOW }
    private record Requirement(List<String> itemIds, int count) { }
    private record Reward(String itemId, int count, String enchantment, int level, String potion) { }
    private record Mission(String titleKey, String descriptionKey, int coins, Objective objective,
                           String advancement, List<Requirement> requirements, List<Reward> rewards) { }

    private static final AttachmentType<Integer> GARDEN_PROGRESS = AttachmentRegistry.create(
            Refugio.id("garden_quest_progress"), b -> b.persistent(Codec.INT).initializer(() -> 0));
    private static final AttachmentType<Integer> EXPEDITION_PROGRESS = AttachmentRegistry.create(
            Refugio.id("expedition_quest_progress"), b -> b.persistent(Codec.INT).initializer(() -> 0));

    private static final List<Mission> GARDEN = List.of(
            delivery("garden", 0, 50, req(16, "minecraft:wheat"), reward("minecraft:bread", 16), reward("minecraft:potato", 5)),
            delivery("garden", 1, 70, req(12, "@cooked_food"), reward("minecraft:coal", 16), reward("minecraft:carrot", 5)),
            delivery("garden", 2, 100, req(16, "minecraft:potato", "minecraft:carrot"), reward("minecraft:golden_carrot", 16)),
            advancement("garden", 3, 150, "husbandry/breed_an_animal", reward("minecraft:golden_apple", 10)),
            delivery("garden", 4, 150, req(12, "minecraft:leather"), reward("minecraft:lead", 2)),
            advancement("garden", 5, 100, "story/obtain_armor", enchanted("minecraft:iron_sword", 1, "minecraft:sharpness", 2)),
            delivery("garden", 6, 150, req(16, "minecraft:rotten_flesh"), reward("minecraft:bone_meal", 30)),
            delivery("garden", 7, 250, req(30, "minecraft:bread"), req(32, "minecraft:torch"),
                    reward("minecraft:torch", 16), enchanted("minecraft:diamond_pickaxe", 1, "minecraft:efficiency", 3))
    );
    private static final List<Mission> EXPEDITION = List.of(
            objective("expedition", 0, 50, Objective.SLEEP, null, reward("minecraft:bread", 8)),
            delivery("expedition", 1, 100, req(5, "minecraft:coal"), req(5, "minecraft:raw_iron"),
                    reward("minecraft:torch", 16), enchanted("minecraft:iron_pickaxe", 1, "minecraft:efficiency", 2)),
            delivery("expedition", 2, 200, req(12, "minecraft:lapis_lazuli"), req(5, "minecraft:gold_ingot"),
                    reward("minecraft:golden_apple", 16)),
            objective("expedition", 3, 200, Objective.TRADE, null, reward("minecraft:emerald_block", 1)),
            delivery("expedition", 4, 250, req(1, "@potion"),
                    potion("minecraft:potion", 10, "minecraft:long_fire_resistance"),
                    potion("minecraft:potion", 2, "minecraft:strong_strength")),
            objective("expedition", 5, 100, Objective.BOAT, null,
                    reward("minecraft:golden_apple", 5), enchanted("minecraft:diamond_helmet", 1, "minecraft:protection", 3)),
            advancement("expedition", 6, 200, "nether/obtain_ancient_debris",
                    reward("minecraft:ender_eye", 2), reward("minecraft:ender_pearl", 10),
                    enchanted("minecraft:diamond_axe", 1, "minecraft:sharpness", 3)),
            objective("expedition", 7, 500, Objective.ENCHANTED_BOW, null,
                    reward("minecraft:enchanted_golden_apple", 12), reward("minecraft:spectral_arrow", 64),
                    enchanted("minecraft:enchanted_book", 1, "minecraft:power", 5))
    );

    private RefugioSideQuests() { }

    public static void initialize() { /* Loads and registers persistent player attachments at mod startup. */ }

    public static int count(Track track) { return missions(track).size(); }
    public static int progress(ServerPlayer player, Track track) {
        return Math.max(0, Math.min(count(track), player.getAttachedOrCreate(progressType(track))));
    }
    public static MissionView mission(Track track, int index) {
        Mission m = missions(track).get(index);
        return new MissionView(m.titleKey(), m.descriptionKey());
    }
    public record MissionView(String titleKey, String descriptionKey) { }

    public static boolean isComplete(ServerPlayer player, Track track, int index) {
        if (index < 0 || index >= count(track)) return false;
        Mission mission = missions(track).get(index);
        return switch (mission.objective()) {
            case DELIVERY -> hasRequirements(player, mission.requirements());
            case ADVANCEMENT -> advancementComplete(player, mission.advancement());
            case SLEEP -> player.getStats().getValue(Stats.CUSTOM.get(Stats.SLEEP_IN_BED)) > 0;
            case TRADE -> player.getStats().getValue(Stats.CUSTOM.get(Stats.TRADED_WITH_VILLAGER)) > 0;
            case BOAT -> player.getStats().getValue(Stats.CUSTOM.get(Stats.BOAT_ONE_CM)) >= 100_000;
            case ENCHANTED_BOW -> hasEnchantedBow(player);
        };
    }

    public static boolean claimNext(ServerPlayer player, Track track) {
        int index = progress(player, track);
        if (!isComplete(player, track, index)) return false;
        Mission mission = missions(track).get(index);
        if (mission.objective() == Objective.DELIVERY && !consumeRequirements(player, mission.requirements())) return false;
        player.setAttached(progressType(track), index + 1);
        RefugioPlayerData.addCoins(player, mission.coins());
        for (Reward reward : mission.rewards()) giveReward(player, reward);
        player.sendSystemMessage(Component.translatable("message.refugio.side_quest_claimed",
                Component.translatable(mission.titleKey()), mission.coins()));
        return true;
    }

    public static List<Component> objectiveLore(ServerPlayer player, Track track, int index) {
        Mission mission = missions(track).get(index);
        List<Component> lore = new ArrayList<>();
        if (mission.objective() == Objective.DELIVERY) {
            for (Requirement req : mission.requirements()) {
                int found = countMatching(player, req.itemIds());
                lore.add(Component.translatable("gui.refugio.side_quest_delivery_progress", found, req.count(),
                        req.itemIds().size() == 1 && !req.itemIds().getFirst().startsWith("@")
                                ? itemName(req.itemIds().getFirst()) : Component.translatable("gui.refugio.side_quest_delivery_group")));
            }
        } else if (mission.objective() == Objective.BOAT) {
            long blocks = player.getStats().getValue(Stats.CUSTOM.get(Stats.BOAT_ONE_CM)) / 100L;
            lore.add(Component.translatable("gui.refugio.side_quest_boat_progress", Math.min(1000, blocks), 1000));
        }
        boolean complete = isComplete(player, track, index);
        if (complete || index < progress(player, track)) {
            lore.add(Component.translatable("gui.refugio.side_quest_reward", mission.coins(), rewardNames(mission)));
        } else {
            lore.add(Component.translatable("gui.refugio.side_quest_reward_hidden"));
        }
        lore.add(Component.translatable(index < progress(player, track) ? "gui.refugio.quest_done"
                : complete ? "gui.refugio.quest_claim_ready" : index == progress(player, track)
                ? "gui.refugio.quest_in_progress" : "gui.refugio.quest_locked"));
        return lore;
    }

    private static boolean advancementComplete(ServerPlayer player, String path) {
        var holder = player.level().getServer().getAdvancements().get(Identifier.parse("minecraft:" + path));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static boolean hasEnchantedBow(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(Items.BOW) && !stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()) return true;
        }
        return false;
    }

    private static boolean hasRequirements(ServerPlayer player, List<Requirement> requirements) {
        for (Requirement req : requirements) if (countMatching(player, req.itemIds()) < req.count()) return false;
        return true;
    }

    private static int countMatching(ServerPlayer player, List<String> itemIds) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && matches(stack, itemIds)) total += stack.getCount();
        }
        return total;
    }

    private static boolean matches(ItemStack stack, List<String> itemIds) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (String allowed : itemIds) {
            if (allowed.equals("@cooked_food") && isCookedFood(id)) return true;
            if (allowed.equals("@potion") && (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION))) return true;
            if (allowed.equals(id)) return true;
        }
        return false;
    }

    private static boolean isCookedFood(String id) {
        return List.of("minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_chicken",
                "minecraft:cooked_mutton", "minecraft:cooked_cod", "minecraft:cooked_salmon",
                "minecraft:cooked_rabbit", "minecraft:baked_potato").contains(id);
    }

    private static boolean consumeRequirements(ServerPlayer player, List<Requirement> requirements) {
        if (!hasRequirements(player, requirements)) return false;
        for (Requirement req : requirements) {
            int remaining = req.count();
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                if (stack.isEmpty() || !matches(stack, req.itemIds())) continue;
                int moved = Math.min(remaining, stack.getCount());
                stack.shrink(moved);
                remaining -= moved;
                if (remaining == 0) break;
            }
        }
        player.getInventory().setChanged();
        return true;
    }

    private static void giveReward(ServerPlayer player, Reward reward) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(reward.itemId()));
        int remaining = reward.count();
        while (remaining > 0) {
            int amount = Math.min(remaining, item.getDefaultMaxStackSize());
            ItemStack stack;
            if (reward.potion() != null) {
                Holder<net.minecraft.world.item.alchemy.Potion> potion = switch (reward.potion()) {
                    case "minecraft:fire_resistance" -> Potions.FIRE_RESISTANCE;
                    case "minecraft:long_fire_resistance" -> Potions.LONG_FIRE_RESISTANCE;
                    case "minecraft:strong_strength" -> Potions.STRONG_STRENGTH;
                    default -> Potions.FIRE_RESISTANCE;
                };
                stack = PotionContents.createItemStack(item, potion);
                amount = 1;
            } else {
                stack = new ItemStack(item, amount);
                if (reward.enchantment() != null) applyEnchantment(player, stack, reward.enchantment(), reward.level());
            }
            CompoundTag marker = new CompoundTag();
            marker.putBoolean("refugio_quest_reward", true);
            CustomData.set(DataComponents.CUSTOM_DATA, stack, marker);
            if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
            remaining -= amount;
        }
    }

    private static void applyEnchantment(ServerPlayer player, ItemStack stack, String id, int level) {
        var registry = player.level().getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> enchantment = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(id)));
        if (stack.is(Items.ENCHANTED_BOOK)) {
            ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            stored.set(enchantment, level);
            stack.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        } else stack.enchant(enchantment, level);
    }

    private static String itemName(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id))).getHoverName().getString();
    }
    private static int rewardCount(Mission mission) { return mission.rewards().stream().mapToInt(Reward::count).sum(); }
    private static Component rewardNames(Mission mission) {
        return Component.literal(mission.rewards().stream().map(r -> r.count() + "x " + itemName(r.itemId())).reduce((a,b) -> a + ", " + b).orElse(""));
    }
    private static List<Mission> missions(Track track) { return track == Track.GARDEN ? GARDEN : EXPEDITION; }
    private static AttachmentType<Integer> progressType(Track track) { return track == Track.GARDEN ? GARDEN_PROGRESS : EXPEDITION_PROGRESS; }
    private static Requirement req(int count, String... ids) { return new Requirement(List.of(ids), count); }
    private static Reward reward(String id, int count) { return new Reward(id, count, null, 0, null); }
    private static Reward enchanted(String id, int count, String enchant, int level) { return new Reward(id, count, enchant, level, null); }
    private static Reward potion(String id, int count, String potion) { return new Reward(id, count, null, 0, potion); }
    private static Mission delivery(String line, int index, int coins, Requirement a, Reward... rewards) {
        return new Mission(key(line, index, "title"), key(line, index, "description"), coins, Objective.DELIVERY, null, List.of(a), List.of(rewards));
    }
    private static Mission delivery(String line, int index, int coins, Requirement a, Requirement b, Reward... rewards) {
        return new Mission(key(line, index, "title"), key(line, index, "description"), coins, Objective.DELIVERY, null, List.of(a, b), List.of(rewards));
    }
    private static Mission advancement(String line, int index, int coins, String advancement, Reward... rewards) {
        return objective(line, index, coins, Objective.ADVANCEMENT, advancement, rewards);
    }
    private static Mission objective(String line, int index, int coins, Objective type, String advancement, Reward... rewards) {
        return new Mission(key(line, index, "title"), key(line, index, "description"), coins, type, advancement, List.of(), List.of(rewards));
    }
    private static String key(String line, int index, String field) { return "quest.refugio." + line + "." + field + "." + index; }
}
