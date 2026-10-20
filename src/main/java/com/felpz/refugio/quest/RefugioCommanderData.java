package com.felpz.refugio.quest;

import com.felpz.refugio.Refugio;
import com.felpz.refugio.data.RefugioPlayerData;
import com.felpz.refugio.npc.RefugioNPCs;
import com.felpz.refugio.npc.RefugioTrainingDummy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative persistence, kill attribution, config validation, and atomic reward
 * claiming for Commander Elias Varga ("Última Linha").
 */
public final class RefugioCommanderData {
    public static final int STATE_CLAIMED = 0;
    public static final int STATE_READY = 1;
    public static final int STATE_LOCKED = 2;

    private static final String KILL_COUNTED_TAG = "refugio:commander_kill_counted";
    private static final String CONFIG_FILE_NAME = "refugio_commander_elias.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final AttachmentType<Integer> ZOMBIE_KILLS = AttachmentRegistry.create(
            Refugio.id("commander_zombie_kills"),
            builder -> builder.persistent(Codec.INT).initializer(() -> 0).copyOnDeath());

    private static final AttachmentType<List<Integer>> UNLOCKED_MILESTONES = AttachmentRegistry.create(
            Refugio.id("commander_unlocked_milestones"),
            builder -> builder.persistent(Codec.INT.listOf()).initializer(ArrayList::new).copyOnDeath());

    private static final AttachmentType<List<Integer>> CLAIMED_MILESTONES = AttachmentRegistry.create(
            Refugio.id("commander_claimed_milestones"),
            builder -> builder.persistent(Codec.INT.listOf()).initializer(ArrayList::new).copyOnDeath());

    private static final AttachmentType<List<Integer>> UNLOCKED_TITLES = AttachmentRegistry.create(
            Refugio.id("commander_unlocked_titles"),
            builder -> builder.persistent(Codec.INT.listOf()).initializer(ArrayList::new).copyOnDeath());

    private static final Set<UUID> CLAIMING_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> COUNTED_DEATH_UUIDS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public enum ClaimResult {
        CLAIMED,
        INVENTORY_FULL,
        ALREADY_CLAIMED,
        LOCKED,
        INVALID
    }

    private RefugioCommanderData() { }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            COUNTED_DEATH_UUIDS.clear();
            loadAndValidateConfiguration(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CLAIMING_PLAYERS.clear();
            COUNTED_DEATH_UUIDS.clear();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                reconcileUnlocks(handler.player, false));
        ServerLivingEntityEvents.AFTER_DEATH.register(RefugioCommanderData::onEntityDeath);
    }

    private static void onEntityDeath(LivingEntity entity, DamageSource source) {
        if (entity == null || source == null) return;
        if (!isCountedZombie(entity)) return;
        ServerPlayer killer = resolveKillerPlayer(source);
        if (killer == null) return;
        if (entity.entityTags().contains(KILL_COUNTED_TAG)) return;
        if (!COUNTED_DEATH_UUIDS.add(entity.getUUID())) return;
        entity.addTag(KILL_COUNTED_TAG);
        if (COUNTED_DEATH_UUIDS.size() > 8192) {
            COUNTED_DEATH_UUIDS.clear();
            COUNTED_DEATH_UUIDS.add(entity.getUUID());
        }
        incrementZombieKills(killer, 1);
    }

    public static boolean isCountedZombie(Entity entity) {
        if (entity == null || entity instanceof Player) return false;
        if (RefugioTrainingDummy.isTrainingDummy(entity)) return false;
        if (RefugioNPCs.isRefugeNpc(entity)) return false;
        if (entity.isInvulnerable()) return false;
        for (String tag : entity.entityTags()) {
            if (tag.startsWith("refugio:")) return false;
        }
        Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return typeId != null && RefugioCommanderRewards.countedEntityIds().contains(typeId.toString());
    }

    private static ServerPlayer resolveKillerPlayer(DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer attacker) {
            return attacker;
        }
        if (source.getDirectEntity() instanceof ServerPlayer directAttacker) {
            return directAttacker;
        }
        return null;
    }

    public static int getZombieKills(ServerPlayer player) {
        return Math.max(0, player.getAttachedOrCreate(ZOMBIE_KILLS));
    }

    public static void setZombieKills(ServerPlayer player, int kills) {
        int clamped = Math.max(0, kills);
        player.setAttached(ZOMBIE_KILLS, clamped);
        reconcileUnlocks(player, true);
    }

    public static void incrementZombieKills(ServerPlayer player, int delta) {
        if (delta <= 0) return;
        int current = getZombieKills(player);
        int updated = (int) Math.min(Integer.MAX_VALUE, (long) current + delta);
        player.setAttached(ZOMBIE_KILLS, updated);
        reconcileUnlocks(player, true);
    }

    public static Set<Integer> getUnlockedMilestones(ServerPlayer player) {
        return new LinkedHashSet<>(player.getAttachedOrCreate(UNLOCKED_MILESTONES));
    }

    public static Set<Integer> getClaimedMilestones(ServerPlayer player) {
        return new LinkedHashSet<>(player.getAttachedOrCreate(CLAIMED_MILESTONES));
    }

    public static Set<Integer> getUnlockedTitleIndices(ServerPlayer player) {
        return new LinkedHashSet<>(player.getAttachedOrCreate(UNLOCKED_TITLES));
    }

    public static boolean isMilestoneUnlocked(ServerPlayer player, int milestoneNumber) {
        RefugioCommanderRewards.MilestoneSpec spec = RefugioCommanderRewards.milestone(milestoneNumber);
        if (spec == null) return false;
        return getZombieKills(player) >= spec.requiredKills()
                || player.getAttachedOrCreate(UNLOCKED_MILESTONES).contains(milestoneNumber);
    }

    public static boolean isMilestoneClaimed(ServerPlayer player, int milestoneNumber) {
        return player.getAttachedOrCreate(CLAIMED_MILESTONES).contains(milestoneNumber);
    }

    public static int getMilestoneState(ServerPlayer player, int milestoneNumber) {
        if (isMilestoneClaimed(player, milestoneNumber)) {
            return STATE_CLAIMED;
        }
        if (isMilestoneUnlocked(player, milestoneNumber)) {
            return STATE_READY;
        }
        return STATE_LOCKED;
    }

    public static int getReadyMilestoneCount(ServerPlayer player) {
        int ready = 0;
        for (RefugioCommanderRewards.MilestoneSpec m : RefugioCommanderRewards.activeMilestones()) {
            if (getMilestoneState(player, m.number()) == STATE_READY) {
                ready++;
            }
        }
        return ready;
    }

    public static int getClaimedMilestoneCount(ServerPlayer player) {
        int claimed = 0;
        for (RefugioCommanderRewards.MilestoneSpec m : RefugioCommanderRewards.activeMilestones()) {
            if (isMilestoneClaimed(player, m.number())) {
                claimed++;
            }
        }
        return claimed;
    }

    /**
     * Ensures all milestones and titles up to the player's current kill total are unlocked
     * without skipping intermediate thresholds. Sends event messages only once on new unlocks.
     */
    public static void reconcileUnlocks(ServerPlayer player, boolean notify) {
        if (player == null) return;
        int kills = getZombieKills(player);

        Set<Integer> unlockedMs = new LinkedHashSet<>(player.getAttachedOrCreate(UNLOCKED_MILESTONES));
        int newlyUnlockedMilestones = 0;
        for (RefugioCommanderRewards.MilestoneSpec m : RefugioCommanderRewards.activeMilestones()) {
            if (kills >= m.requiredKills() && unlockedMs.add(m.number())) {
                newlyUnlockedMilestones++;
            }
        }
        if (newlyUnlockedMilestones > 0) {
            player.setAttached(UNLOCKED_MILESTONES, new ArrayList<>(unlockedMs));
        }

        Set<Integer> unlockedTitles = new LinkedHashSet<>(player.getAttachedOrCreate(UNLOCKED_TITLES));
        List<RefugioCommanderRewards.TitleSpec> newlyUnlockedTitles = new ArrayList<>();
        boolean reachedThousandNow = false;
        for (RefugioCommanderRewards.TitleSpec t : RefugioCommanderRewards.activeTitles()) {
            if (kills >= t.requiredKills() && unlockedTitles.add(t.index())) {
                newlyUnlockedTitles.add(t);
                if (t.requiredKills() >= 1000) {
                    reachedThousandNow = true;
                }
            }
        }
        if (!newlyUnlockedTitles.isEmpty()) {
            player.setAttached(UNLOCKED_TITLES, new ArrayList<>(unlockedTitles));
        }

        if (!notify) return;

        if (newlyUnlockedMilestones > 0 && RefugioCommanderRewards.isMilestoneUnlockMessageEnabled()) {
            player.sendSystemMessage(Component.translatable("message.refugio.commander.milestone_unlocked")
                    .withStyle(ChatFormatting.GOLD));
        }
        for (RefugioCommanderRewards.TitleSpec title : newlyUnlockedTitles) {
            player.sendSystemMessage(Component.translatable(
                    "message.refugio.commander.title_unlocked", title.title()).withStyle(ChatFormatting.AQUA));
        }
        if (reachedThousandNow) {
            player.sendSystemMessage(Component.translatable("message.refugio.commander.legend_reached")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            if (RefugioCommanderRewards.isGlobalLegendAnnouncementEnabled()) {
                MinecraftServer server = player.level().getServer();
                if (server != null) {
                    Component broadcast = Component.translatable(
                            "message.refugio.commander.legend_global", player.getGameProfile().name())
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
                    for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                        online.sendSystemMessage(broadcast);
                    }
                }
            }
        }
    }

    /**
     * Atomically validates and claims a milestone on the server.
     * Verifies full inventory space before delivering items or crediting coins.
     */
    public static ClaimResult claimMilestone(ServerPlayer player, int milestoneNumber) {
        if (player == null || !player.isAlive()) return ClaimResult.INVALID;
        RefugioCommanderRewards.MilestoneSpec milestone = RefugioCommanderRewards.milestone(milestoneNumber);
        if (milestone == null) return ClaimResult.INVALID;

        if (!CLAIMING_PLAYERS.add(player.getUUID())) {
            return ClaimResult.INVALID;
        }
        try {
            reconcileUnlocks(player, false);
            if (isMilestoneClaimed(player, milestoneNumber)) {
                return ClaimResult.ALREADY_CLAIMED;
            }
            if (!isMilestoneUnlocked(player, milestoneNumber)) {
                return ClaimResult.LOCKED;
            }

            List<ItemStack> stacksToDeliver = buildRewardStacks(player, milestone);
            List<ItemStack> inventoryList = player.getInventory().getNonEquipmentItems();
            List<ItemStack> simulated = new ArrayList<>(inventoryList.size());
            for (ItemStack slot : inventoryList) {
                simulated.add(slot.isEmpty() ? ItemStack.EMPTY : slot.copy());
            }

            if (!tryInsertAllIntoSlots(simulated, stacksToDeliver)) {
                player.sendSystemMessage(Component.translatable("message.refugio.commander.inventory_full")
                        .withStyle(ChatFormatting.RED));
                return ClaimResult.INVENTORY_FULL;
            }

            // Mark milestone claimed first to guarantee idempotency
            Set<Integer> claimed = new LinkedHashSet<>(player.getAttachedOrCreate(CLAIMED_MILESTONES));
            if (!claimed.add(milestoneNumber)) {
                return ClaimResult.ALREADY_CLAIMED;
            }
            player.setAttached(CLAIMED_MILESTONES, new ArrayList<>(claimed));

            // Commit the verified inventory slots atomically
            for (int i = 0; i < inventoryList.size(); i++) {
                inventoryList.set(i, simulated.get(i));
            }
            player.getInventory().setChanged();

            // Credit coins to the existing Refugio wallet
            RefugioPlayerData.addCoins(player, milestone.coins());

            player.sendSystemMessage(Component.translatable(
                    "message.refugio.commander.claimed", milestone.name(), milestone.coins())
                    .withStyle(ChatFormatting.GREEN));
            return ClaimResult.CLAIMED;
        } finally {
            CLAIMING_PLAYERS.remove(player.getUUID());
        }
    }

    /**
     * Pure slot-packing check for a 36-slot inventory copy. Returns true only if every stack
     * in stacksToDeliver fits completely without dropping a single item.
     */
    public static boolean tryInsertAllIntoSlots(List<ItemStack> targetSlots, List<ItemStack> stacksToDeliver) {
        for (ItemStack original : stacksToDeliver) {
            if (original == null || original.isEmpty()) continue;
            int remaining = original.getCount();
            int maxStack = Math.max(1, original.getMaxStackSize());

            if (maxStack > 1) {
                for (int i = 0; i < targetSlots.size() && remaining > 0; i++) {
                    ItemStack existing = targetSlots.get(i);
                    if (!existing.isEmpty() && existing.getCount() < maxStack
                            && ItemStack.isSameItemSameComponents(existing, original)) {
                        int move = Math.min(remaining, maxStack - existing.getCount());
                        existing.grow(move);
                        remaining -= move;
                    }
                }
            }

            for (int i = 0; i < targetSlots.size() && remaining > 0; i++) {
                if (targetSlots.get(i).isEmpty()) {
                    int move = Math.min(remaining, maxStack);
                    ItemStack placed = original.copy();
                    placed.setCount(move);
                    targetSlots.set(i, placed);
                    remaining -= move;
                }
            }

            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> buildRewardStacks(ServerPlayer player,
                                                     RefugioCommanderRewards.MilestoneSpec milestone) {
        List<ItemStack> result = new ArrayList<>();
        for (RefugioCommanderRewards.RewardItemSpec spec : milestone.items()) {
            Item item = resolveValidatedItem(spec.itemId(), spec.fallbackItemId(), milestone.number());
            if (item == Items.AIR) continue;
            int remaining = Math.max(1, spec.count());
            int maxStack = Math.max(1, item.getDefaultMaxStackSize());
            while (remaining > 0) {
                int amount = Math.min(remaining, maxStack);
                ItemStack stack = new ItemStack(item, amount);
                if (item == Items.FIREWORK_ROCKET) {
                    stack.set(DataComponents.FIREWORKS, new Fireworks(3, List.of()));
                }
                if (!spec.enchantments().isEmpty()) {
                    applyEnchantments(player, stack, spec.enchantments(), milestone.number());
                }
                CompoundTag marker = new CompoundTag();
                marker.putBoolean("refugio_quest_reward", true);
                CustomData.set(DataComponents.CUSTOM_DATA, stack, marker);
                result.add(stack);
                remaining -= amount;
            }
        }
        return result;
    }

    private static Item resolveValidatedItem(String itemId, String fallbackItemId, int milestoneNumber) {
        try {
            Item primary = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
            if (primary != null && primary != Items.AIR) {
                return primary;
            }
        } catch (RuntimeException ignored) {
        }
        Refugio.LOGGER.warn("Marco {} do Comandante Elias: item '{}' ausente; usando alternativa '{}'.",
                milestoneNumber, itemId, fallbackItemId);
        try {
            Item fallback = BuiltInRegistries.ITEM.getValue(Identifier.parse(fallbackItemId));
            if (fallback != null && fallback != Items.AIR) {
                return fallback;
            }
        } catch (RuntimeException ignored) {
        }
        return Items.DIAMOND;
    }

    private static void applyEnchantments(ServerPlayer player, ItemStack stack,
                                          List<RefugioCommanderRewards.EnchantSpec> enchantments,
                                          int milestoneNumber) {
        var registry = player.level().getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        if (stack.is(Items.ENCHANTED_BOOK)) {
            ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(
                    stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY));
            for (RefugioCommanderRewards.EnchantSpec ench : enchantments) {
                if (ench.level() <= 0) continue;
                try {
                    var key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(ench.enchantmentId()));
                    var holderOpt = registry.get(key);
                    if (holderOpt.isPresent()) {
                        stored.set(holderOpt.get(), ench.level());
                    } else {
                        Refugio.LOGGER.warn("Marco {} do Comandante Elias: encantamento '{}' inexistente; ignorado.",
                                milestoneNumber, ench.enchantmentId());
                    }
                } catch (RuntimeException e) {
                    Refugio.LOGGER.warn("Marco {} do Comandante Elias: encantamento '{}' inválido.",
                            milestoneNumber, ench.enchantmentId());
                }
            }
            stack.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        } else {
            for (RefugioCommanderRewards.EnchantSpec ench : enchantments) {
                if (ench.level() <= 0) continue;
                try {
                    var key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(ench.enchantmentId()));
                    var holderOpt = registry.get(key);
                    if (holderOpt.isPresent()) {
                        Holder<Enchantment> holder = holderOpt.get();
                        stack.enchant(holder, ench.level());
                    } else {
                        Refugio.LOGGER.warn("Marco {} do Comandante Elias: encantamento '{}' inexistente; ignorado.",
                                milestoneNumber, ench.enchantmentId());
                    }
                } catch (RuntimeException e) {
                    Refugio.LOGGER.warn("Marco {} do Comandante Elias: encantamento '{}' inválido.",
                            milestoneNumber, ench.enchantmentId());
                }
            }
        }
    }

    private static void loadAndValidateConfiguration(MinecraftServer server) {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
        CommanderConfigFile config = null;
        if (Files.isRegularFile(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, CommanderConfigFile.class);
            } catch (IOException | RuntimeException e) {
                Refugio.LOGGER.warn("Falha ao ler {}; usando tabela padrão do Comandante Elias.", CONFIG_FILE_NAME, e);
            }
        } else {
            CommanderConfigFile defaults = CommanderConfigFile.fromDefaults();
            try {
                Files.createDirectories(configPath.getParent());
                try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                    GSON.toJson(defaults, writer);
                }
            } catch (IOException e) {
                Refugio.LOGGER.warn("Não foi possível gravar o arquivo padrão {}.", CONFIG_FILE_NAME, e);
            }
            config = defaults;
        }

        if (config != null) {
            RefugioCommanderRewards.applyConfiguration(
                    config.milestones,
                    config.titles,
                    config.countedEntityIds != null ? new LinkedHashSet<>(config.countedEntityIds) : null,
                    config.announceLegendGlobally,
                    config.notifyMilestoneUnlock
            );
        }

        // Validate all configured items and enchantments against active registries
        var enchantRegistry = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (RefugioCommanderRewards.MilestoneSpec m : RefugioCommanderRewards.activeMilestones()) {
            for (RefugioCommanderRewards.RewardItemSpec itemSpec : m.items()) {
                resolveValidatedItem(itemSpec.itemId(), itemSpec.fallbackItemId(), m.number());
                for (RefugioCommanderRewards.EnchantSpec ench : itemSpec.enchantments()) {
                    try {
                        var key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(ench.enchantmentId()));
                        if (enchantRegistry.get(key).isEmpty()) {
                            Refugio.LOGGER.warn("Configuração Comandante Elias (Marco {}): encantamento '{}' não encontrado.",
                                    m.number(), ench.enchantmentId());
                        }
                    } catch (RuntimeException e) {
                        Refugio.LOGGER.warn("Configuração Comandante Elias (Marco {}): ID de encantamento inválido '{}'.",
                                m.number(), ench.enchantmentId());
                    }
                }
            }
        }
    }

    private static final class CommanderConfigFile {
        List<String> countedEntityIds = List.of(RefugioCommanderRewards.DEFAULT_ZOMBIE_ENTITY_ID);
        boolean announceLegendGlobally = true;
        boolean notifyMilestoneUnlock = true;
        List<RefugioCommanderRewards.TitleSpec> titles = RefugioCommanderRewards.defaultTitles();
        List<RefugioCommanderRewards.MilestoneSpec> milestones = RefugioCommanderRewards.defaultMilestones();

        static CommanderConfigFile fromDefaults() {
            return new CommanderConfigFile();
        }
    }
}
