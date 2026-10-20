package com.felpz.refugio.ability;

import com.felpz.refugio.Refugio;
import com.felpz.refugio.data.RefugioPlayerData;
import com.felpz.refugio.ui.RefugioUiPayload;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent, server-authoritative skill ranks and activation state. */
public final class RefugioAbilityData {
    public enum Branch {
        BLADE("blade", "Mestre da Lâmina", true), BASTION("bastion", "Resistência", true),
        REFLEXES("reflexes", "Reflexos", false), SURVIVOR("survivor", "Sobrevivente", true);
        private final String id;
        private final String name;
        private final boolean hasSubcategories;
        Branch(String id, String name, boolean hasSubcategories) {
            this.id = id;
            this.name = name;
            this.hasSubcategories = hasSubcategories;
        }
        public String id() { return id; }
        public String displayName() { return name; }
        public boolean hasSubcategories() { return hasSubcategories; }
    }

    public enum Track {
        SWORD(Branch.BLADE, "sword", "Espada", "minecraft:iron_sword"),
        AXE(Branch.BLADE, "axe", "Machado", "minecraft:iron_axe"),
        BOW(Branch.BLADE, "bow", "Arco", "minecraft:bow"),
        EXTRAS(Branch.BLADE, "extras", "Extras", "minecraft:shield"),
        VITALITY(Branch.BASTION, "vitality", "Vitalidade", "minecraft:golden_apple"),
        ACTIVE_DEFENSE(Branch.BASTION, "active_defense", "Defesa Ativa", "minecraft:iron_chestplate"),
        REFLEXES(Branch.REFLEXES, "reflexes", "Reflexos", "minecraft:feather"),
        MINING(Branch.SURVIVOR, "mining", "Mineração", "minecraft:iron_pickaxe"),
        ALCHEMY(Branch.SURVIVOR, "alchemy", "Alquimia", "minecraft:potion"),
        EXPLORATION(Branch.SURVIVOR, "exploration", "Exploração", "minecraft:compass");

        private final Branch branch;
        private final String id;
        private final String name;
        private final String icon;
        Track(Branch branch, String id, String name, String icon) {
            this.branch = branch;
            this.id = id;
            this.name = name;
            this.icon = icon;
        }
        public Branch branch() { return branch; }
        public String id() { return id; }
        public String displayName() { return name; }
        public String icon() { return icon; }
    }

    public record Skill(String id, String name, String description, Track track, String icon) {
        public Branch branch() { return track.branch(); }
    }

    private static final List<Skill> SKILLS = List.of(
            skill("sword_critical_chance", "Chance Crítica", "+10 pontos percentuais por rank, até +50%; chance de crítico em golpes com espadas contra inimigos.", Track.SWORD, "minecraft:iron_sword"),
            skill("sword_critical_damage", "Dano Crítico", "+20% ao multiplicador crítico por rank, até +100% no rank 5; afeta críticos de salto e os gerados por Chance Crítica com espadas.", Track.SWORD, "minecraft:diamond_sword"),
            skill("sword_first_cut", "Primeiro Corte", "Após 5 s sem causar dano de combate, o próximo golpe com espada contra um inimigo ganha +15% por rank (máx. +75%).", Track.SWORD, "minecraft:golden_sword"),
            skill("sword_mastery", "Maestria com Espadas", "+10% de dano com espadas por rank, até +50% no rank 5.", Track.SWORD, "minecraft:netherite_sword"),

            skill("axe_critical_chance", "Chance Crítica", "+10 pontos percentuais por rank, até +50%; chance de crítico em golpes com machados contra inimigos.", Track.AXE, "minecraft:iron_axe"),
            skill("axe_critical_damage", "Dano Crítico", "+20% ao multiplicador crítico por rank, até +100% no rank 5; afeta críticos de salto e os gerados por Chance Crítica com machados.", Track.AXE, "minecraft:diamond_axe"),
            skill("axe_first_cut", "Primeiro Corte", "Após 5 s sem causar dano de combate, o próximo golpe com machado contra um inimigo ganha +15% por rank (máx. +75%).", Track.AXE, "minecraft:golden_axe"),
            skill("axe_mastery", "Maestria com Machados", "+10% de dano com machados por rank, até +50% no rank 5.", Track.AXE, "minecraft:iron_axe"),

            skill("bow_critical_chance", "Chance Crítica", "+10 pontos percentuais por rank, até +50%; chance de crítico em flechas de arco contra inimigos.", Track.BOW, "minecraft:bow"),
            skill("bow_critical_damage", "Dano Crítico", "+20% ao multiplicador crítico por rank, até +100% no rank 5; afeta críticos naturais e os gerados por Chance Crítica com arco.", Track.BOW, "minecraft:tipped_arrow"),
            skill("bow_first_shot", "Primeiro Tiro", "Após 5 s sem causar dano de combate, a próxima flecha que acertar um inimigo ganha +15% por rank (máx. +75%).", Track.BOW, "minecraft:arrow"),
            skill("bow_mastery", "Maestria com Arcos", "+10% de dano de flechas de arco por rank, até +50% no rank 5; só contra inimigos.", Track.BOW, "minecraft:bow"),
            skill("steady_aim", "Mira Estável", "Reduz em 10% por rank a dispersão das flechas, até 50%.", Track.BOW, "minecraft:target"),

            skill("retaliation", "Retaliação", "Após bloquear com escudo, o 1º ataque em até 4 s ganha +15% de dano por rank (máx. +75%); recarga de 10 s.", Track.EXTRAS, "minecraft:shield"),
            skill("horde_vigor", "Vigor da Horda", "Ao matar um inimigo, ativa +6% de dano por rank (máx. +30%) por 5 s. Não renova durante o efeito; volta ao abater outro inimigo após acabar.", Track.EXTRAS, "minecraft:rotten_flesh"),
            skill("area_defense", "Defesa em Área", "Ao matar um inimigo (inclusive com flechas), ele explode e causa 5–25 de dano base a até 1–5 inimigos visíveis num raio de 3–5 blocos; recarga de 60–30 s.", Track.EXTRAS, "minecraft:iron_chestplate"),

            skill("firm_stance", "Postura Firme", "Reduz em 5% por rank (máx. 25%) o dano recebido de inimigos.", Track.ACTIVE_DEFENSE, "minecraft:shield"),
            skill("last_reserve", "Última Reserva", "Quando um golpe inimigo deixaria sua vida em 20% ou menos, reduz o golpe em 20–50% conforme o rank (evitando golpe fatal) com efeito na tela; recarga de 60–20 s.", Track.ACTIVE_DEFENSE, "minecraft:totem_of_undying"),
            skill("anchor", "Âncora", "+8 pontos percentuais de resistência a empurrão por rank, até +40%.", Track.ACTIVE_DEFENSE, "minecraft:iron_boots"),

            skill("vitality_health", "Coração Robusto", "+1 coração de vida máxima por rank, até +10 corações no rank 10. Só aplica enquanto ativa; custo do próximo rank: 500 × rank.", Track.VITALITY, "minecraft:golden_apple"),
            skill("natural_regeneration", "Regeneração Natural", "Após 10 s sem causar ou receber dano de inimigo, recupera meio coração a cada 2 s. Os limites usam somente os 20 pontos de vida base: R1 30→50%, R2 30→60%, R3 40→60%, R4 50→70%, R5 60→80%.", Track.VITALITY, "minecraft:ghast_tear"),
            skill("vitality_on_kill", "Sede de Vida", "Ao derrotar um inimigo hostil, recupera 1% da vida máxima total por rank, até 5% no rank 5.", Track.VITALITY, "minecraft:heart_of_the_sea"),

            skill("light_step", "Passo Leve", "Reduz o dano de queda em 10% por rank, até 50%.", Track.REFLEXES, "minecraft:feather"),
            skill("burst", "Arranque", "Impulso horizontal curto na direção da mira, sem teleporte; cresce por rank, mas usa força reduzida para encurtar a distância. Recarga: 30/20/10/10/5 s; 2 cargas a partir do rank 4.", Track.REFLEXES, "minecraft:sugar"),
            skill("momentum", "Ímpeto", "+7% de velocidade de movimento por rank, até +30% no rank 5.", Track.REFLEXES, "minecraft:rabbit_foot"),
            skill("super_jump", "Super Pulo", "Pressione Espaço uma vez no chão para um salto vertical alto (força de 0,53 a 0,65 por rank); não há barra de carga. O dano de queda só conta a descida abaixo do bloco de partida.", Track.REFLEXES, "minecraft:slime_block"),
            skill("double_jump", "Pulo Duplo", "Salte normalmente e pressione Espaço uma vez no ar para um segundo impulso vertical (0,54 a 0,70 por rank). Renova ao tocar o chão; não ativa montado nem na água. A queda é medida desde o bloco do primeiro salto.", Track.REFLEXES, "minecraft:phantom_membrane"),

            skill("mining", "Escavação Ágil", "+6% de velocidade de mineração por rank, até +30%; sem revelar minérios.", Track.MINING, "minecraft:iron_pickaxe"),
            skill("preparation", "Preparação", "Cada alimento concede +0,5 de saturação por rank, até +2,5; não amplia poções.", Track.ALCHEMY, "minecraft:potion"),
            skill("collector", "Coletor", "Atrai itens visíveis gradualmente num raio de 2 a 6 blocos conforme o rank; não cria nem duplica drops.", Track.EXPLORATION, "minecraft:bundle"),
            skill("exploration", "Exploração", "Reduz a exaustão em 6% por rank, até 30% no rank 5.", Track.EXPLORATION, "minecraft:map"),
            skill("logistics", "Logística", "Cada rank dá 8% de chance de ignorar 1 ponto de dano de durabilidade, até 40% no rank 5.", Track.EXPLORATION, "minecraft:chest")
    );

    private static final AttachmentType<Map<String, Integer>> RANKS = AttachmentRegistry.create(
            Refugio.id("ability_ranks"), builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
                    .initializer(HashMap::new).copyOnDeath());
    private static final AttachmentType<Map<String, Boolean>> ACTIVE = AttachmentRegistry.create(
            Refugio.id("ability_active"), builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.BOOL))
                    .initializer(HashMap::new).copyOnDeath());
    private static final AttachmentType<String> FREE_CHOICE = AttachmentRegistry.create(
            Refugio.id("ability_free_choice"), builder -> builder.persistent(Codec.STRING)
                    .initializer(() -> "").copyOnDeath());
    private static final AttachmentType<Long> LAST_COMBAT_TICK = AttachmentRegistry.create(
            Refugio.id("ability_last_combat_tick"), builder -> builder.persistent(Codec.LONG)
                    .initializer(() -> Long.MIN_VALUE).copyOnDeath());
    private static final AttachmentType<Boolean> NATURAL_REGEN_ACTIVE = AttachmentRegistry.create(
            Refugio.id("ability_natural_regen_active"), builder -> builder.initializer(() -> false));
    private static final AttachmentType<Long> RETALIATION_READY_UNTIL = AttachmentRegistry.create(
            Refugio.id("ability_retaliation_ready"), builder -> builder.initializer(() -> 0L));
    private static final AttachmentType<Long> RETALIATION_COOLDOWN_UNTIL = AttachmentRegistry.create(
            Refugio.id("ability_retaliation_cooldown"), builder -> builder.initializer(() -> 0L));
    private static final AttachmentType<Long> HORDE_VIGOR_UNTIL = AttachmentRegistry.create(
            Refugio.id("ability_horde_vigor_until"), builder -> builder.initializer(() -> 0L));
    private static final AttachmentType<Long> LAST_RESERVE_COOLDOWN_UNTIL = AttachmentRegistry.create(
            Refugio.id("ability_last_reserve_cooldown"), builder -> builder.initializer(() -> 0L));
    private static final AttachmentType<Long> BURST_COOLDOWN_UNTIL = AttachmentRegistry.create(
            Refugio.id("ability_burst_cooldown"), builder -> builder.initializer(() -> 0L));
    private static final AttachmentType<Integer> BURST_CHARGES_USED = AttachmentRegistry.create(
            Refugio.id("ability_burst_charges_used"), builder -> builder.initializer(() -> 0));
    private static final AttachmentType<Boolean> SUPER_JUMP_USED = AttachmentRegistry.create(
            Refugio.id("ability_super_jump_used"), builder -> builder.initializer(() -> false));
    private static final AttachmentType<Long> SUPER_JUMP_LAUNCHED_AT = AttachmentRegistry.create(
            Refugio.id("ability_super_jump_launched_at"), builder -> builder.initializer(() -> 0L));
    private static final AttachmentType<Boolean> DOUBLE_JUMP_USED = AttachmentRegistry.create(
            Refugio.id("ability_double_jump_used"), builder -> builder.initializer(() -> false));
    private static final AttachmentType<Boolean> JUMP_TRACKING = AttachmentRegistry.create(
            Refugio.id("ability_jump_tracking"), builder -> builder.initializer(() -> false));
    private static final AttachmentType<Double> JUMP_ORIGIN_Y = AttachmentRegistry.create(
            Refugio.id("ability_jump_origin_y"), builder -> builder.initializer(() -> 0.0));
    private static final AttachmentType<Long> JUMP_STARTED_AT = AttachmentRegistry.create(
            Refugio.id("ability_jump_started_at"), builder -> builder.initializer(() -> 0L));
    private static final AttachmentType<String> JUMP_DIMENSION = AttachmentRegistry.create(
            Refugio.id("ability_jump_dimension"), builder -> builder.initializer(() -> ""));
    private static final AttachmentType<Boolean> JUMP_LEFT_GROUND = AttachmentRegistry.create(
            Refugio.id("ability_jump_left_ground"), builder -> builder.initializer(() -> false));
    private static final AttachmentType<Double> LAST_GROUNDED_Y = AttachmentRegistry.create(
            Refugio.id("ability_last_grounded_y"), builder -> builder.initializer(() -> 0.0));
    private static final AttachmentType<String> LAST_GROUNDED_DIMENSION = AttachmentRegistry.create(
            Refugio.id("ability_last_grounded_dimension"), builder -> builder.initializer(() -> ""));
    private static final AttachmentType<Double> LAST_TICK_X = AttachmentRegistry.create(
            Refugio.id("ability_last_tick_x"), builder -> builder.initializer(() -> 0.0));
    private static final AttachmentType<Double> LAST_TICK_Y = AttachmentRegistry.create(
            Refugio.id("ability_last_tick_y"), builder -> builder.initializer(() -> 0.0));
    private static final AttachmentType<Double> LAST_TICK_Z = AttachmentRegistry.create(
            Refugio.id("ability_last_tick_z"), builder -> builder.initializer(() -> 0.0));
    private static final AttachmentType<String> LAST_TICK_DIMENSION = AttachmentRegistry.create(
            Refugio.id("ability_last_tick_dimension"), builder -> builder.initializer(() -> ""));
    private static final AttachmentType<Long> AREA_DEFENSE_COOLDOWN_UNTIL = AttachmentRegistry.create(
            Refugio.id("ability_area_defense_cooldown"), builder -> builder.initializer(() -> 0L));

    private static final Set<UUID> APPLYING_AREA_DEFENSE = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> LAST_SYNCED_MOVEMENT_MASK = new ConcurrentHashMap<>();

    private RefugioAbilityData() { }

    private static Skill skill(String id, String name, String description, Track track, String icon) {
        return new Skill(id, name, description, track, icon);
    }

    public static List<Skill> skills() { return SKILLS; }
    public static List<Skill> skills(Branch branch) { return SKILLS.stream().filter(s -> s.branch() == branch).toList(); }
    public static List<Skill> skills(Track track) { return SKILLS.stream().filter(s -> s.track() == track).toList(); }
    public static List<Track> tracks(Branch branch) { return List.of(Track.values()).stream().filter(t -> t.branch() == branch).toList(); }
    public static Skill skill(String id) { return SKILLS.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null); }
    public static Track track(String id) { return java.util.Arrays.stream(Track.values()).filter(t -> t.id().equals(id)).findFirst().orElse(null); }
    public static int costForRank(int rank) { return RefugioAbilityRules.costForRank(rank); }
    public static int costForRank(String id, int rank) { return RefugioAbilityRules.costForRank(id, rank); }
    public static int maxRank() { return RefugioAbilityRules.maxRank(); }
    public static int maxRank(String id) { return RefugioAbilityRules.maxRank(id); }
    public static String criticalChanceSkillId(ItemStack weapon) {
        String prefix = weaponSkillPrefix(weapon);
        return prefix == null ? null : prefix + "_critical_chance";
    }
    public static String criticalDamageSkillId(ItemStack weapon) {
        String prefix = weaponSkillPrefix(weapon);
        return prefix == null ? null : prefix + "_critical_damage";
    }
    private static String firstStrikeSkillId(ItemStack weapon) {
        String prefix = weaponSkillPrefix(weapon);
        return prefix == null ? null : prefix.equals("bow") ? "bow_first_shot" : prefix + "_first_cut";
    }
    private static String weaponSkillPrefix(ItemStack weapon) {
        if (weapon.is(ItemTags.SWORDS)) return "sword";
        if (weapon.is(ItemTags.AXES)) return "axe";
        if (weapon.is(ItemTags.BOW_ENCHANTABLE)) return "bow";
        return null;
    }
    public static int rank(ServerPlayer player, String id) {
        return Math.max(0, Math.min(maxRank(id), player.getAttachedOrCreate(RANKS).getOrDefault(id, 0)));
    }
    public static boolean isActive(ServerPlayer player, String id) { return player.getAttachedOrCreate(ACTIVE).getOrDefault(id, false); }
    public static String freeChoice(ServerPlayer player) {
        String choice = player.getAttachedOrCreate(FREE_CHOICE);
        return (choice == null || skill(choice) == null) ? "" : choice;
    }
    public static boolean hasFreeChoice(ServerPlayer player) { return !freeChoice(player).isEmpty(); }

    public static void registerEvents() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_SYNCED_MOVEMENT_MASK.clear());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (player.onGround()) {
                player.setAttached(LAST_GROUNDED_Y, RefugioAbilityRules.normalizeSurfaceCoordinate(player.getY()));
                player.setAttached(LAST_GROUNDED_DIMENSION, player.level().dimension().toString());
            }
            recordTickPosition(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LAST_SYNCED_MOVEMENT_MASK.remove(handler.player.getUUID());
            clearJumpTracking(handler.player);
        });
        ServerTickEvents.START_SERVER_TICK.register(server ->
                server.getPlayerList().getPlayers().forEach(player -> {
                    if (player.onGround() && !player.getAttachedOrCreate(JUMP_TRACKING)) {
                        player.setAttached(LAST_GROUNDED_Y, RefugioAbilityRules.normalizeSurfaceCoordinate(player.getY()));
                        player.setAttached(LAST_GROUNDED_DIMENSION, player.level().dimension().toString());
                    }
                }));
        ServerTickEvents.END_SERVER_TICK.register(server ->
                server.getPlayerList().getPlayers().forEach(player -> {
                    enforceJumpExclusivity(player);
                    syncMovementStateIfNeeded(player);
                    refreshAttributeSkills(player);
                    refreshVitalitySkills(player);
                    refreshJumpState(player);
                    refreshUtilitySkills(player);
                }));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (entity instanceof ServerPlayer victim) {
                if (source.getEntity() instanceof Enemy && baseDamage > 0.0f) recordCombatActivity(victim);
                if (blocked && source.getEntity() instanceof Enemy && isActive(victim, "retaliation")) {
                    long now = gameTime(victim);
                    if (now >= victim.getAttachedOrCreate(RETALIATION_COOLDOWN_UNTIL)) {
                        victim.setAttached(RETALIATION_READY_UNTIL, now + RefugioAbilityRules.retaliationReadyTicks());
                        victim.setAttached(RETALIATION_COOLDOWN_UNTIL, now + RefugioAbilityRules.retaliationCooldownTicks());
                    }
                }
            }
            if (entity instanceof Enemy && source.getEntity() instanceof ServerPlayer attacker) {
                if (damageTaken > 0.0f) {
                    recordCombatActivity(attacker);
                    consumeRetaliation(attacker);
                }
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer deadPlayer) {
                clearJumpTracking(deadPlayer);
            }
            if (!(source.getEntity() instanceof ServerPlayer killer)) return;
            recordCombatActivity(killer);
            if (entity instanceof Enemy) {
                healOnHostileKill(killer);
                long now = gameTime(killer);
                if (isActive(killer, "horde_vigor") && now >= killer.getAttachedOrCreate(HORDE_VIGOR_UNTIL)) {
                    killer.setAttached(HORDE_VIGOR_UNTIL, now + RefugioAbilityRules.hordeVigorDurationTicks());
                }
                triggerAreaDefenseOnKill(killer, entity);
            }
        });
    }

    private static void enforceJumpExclusivity(ServerPlayer player) {
        if (isActive(player, "super_jump") && isActive(player, "double_jump")) {
            Map<String, Boolean> states = new HashMap<>(player.getAttachedOrCreate(ACTIVE));
            states.put("double_jump", false);
            player.setAttached(ACTIVE, states);
        }
    }

    public static void syncMovementState(ServerPlayer player) {
        if (player == null) return;
        int sj = isActive(player, "super_jump") ? rank(player, "super_jump") : 0;
        int dj = isActive(player, "double_jump") ? rank(player, "double_jump") : 0;
        int burst = isActive(player, "burst") ? rank(player, "burst") : 0;
        int mask = (sj & 0xF) | ((dj & 0xF) << 4) | ((burst & 0xF) << 8);
        LAST_SYNCED_MOVEMENT_MASK.put(player.getUUID(), mask);
        ServerPlayNetworking.send(player, new RefugioUiPayload("MOVEMENT_STATE\t" + mask));
    }

    private static void syncMovementStateIfNeeded(ServerPlayer player) {
        int sj = isActive(player, "super_jump") ? rank(player, "super_jump") : 0;
        int dj = isActive(player, "double_jump") ? rank(player, "double_jump") : 0;
        int burst = isActive(player, "burst") ? rank(player, "burst") : 0;
        int mask = (sj & 0xF) | ((dj & 0xF) << 4) | ((burst & 0xF) << 8);
        Integer prev = LAST_SYNCED_MOVEMENT_MASK.get(player.getUUID());
        if (prev == null || prev != mask) {
            LAST_SYNCED_MOVEMENT_MASK.put(player.getUUID(), mask);
            ServerPlayNetworking.send(player, new RefugioUiPayload("MOVEMENT_STATE\t" + mask));
        }
    }

    /** Returns the equipped weapon's opening-strike rank after a 5-second window without dealing combat damage. */
    public static int beginAttack(ServerPlayer player) {
        return beginTimedFirstStrike(player, firstStrikeSkillId(player.getMainHandItem()));
    }

    /** Returns First Shot's rank when an enemy is hit after the same 5-second quiet window as First Cut. */
    public static int beginBowFirstShot(ServerPlayer player) {
        return beginTimedFirstStrike(player, "bow_first_shot");
    }

    private static int beginTimedFirstStrike(ServerPlayer player, String skillId) {
        if (skillId == null || !isActive(player, skillId)) return 0;
        long now = gameTime(player);
        long last = player.getAttachedOrCreate(LAST_COMBAT_TICK);
        return last == Long.MIN_VALUE || now - last >= RefugioAbilityRules.firstCutQuietTicks()
                ? rank(player, skillId) : 0;
    }

    public static int retaliationRankForAttack(ServerPlayer player) {
        long now = gameTime(player);
        long readyUntil = player.getAttachedOrCreate(RETALIATION_READY_UNTIL);
        if (readyUntil <= now || !isActive(player, "retaliation")) return 0;
        return rank(player, "retaliation");
    }

    private static void consumeRetaliation(ServerPlayer player) {
        if (player.getAttachedOrCreate(RETALIATION_READY_UNTIL) > gameTime(player)) {
            player.setAttached(RETALIATION_READY_UNTIL, 0L);
        }
    }

    public static float hordeVigorBonus(ServerPlayer player) {
        if (!isActive(player, "horde_vigor")
                || player.getAttachedOrCreate(HORDE_VIGOR_UNTIL) <= gameTime(player)) return 0.0f;
        return RefugioAbilityRules.hordeVigorBonus(rank(player, "horde_vigor"));
    }

    /** Returns Last Reserve's one-hit damage reduction and triggers the minimalist screen effect when activated. */
    public static float tryLastReserve(ServerPlayer player, float incomingDamage) {
        if (!isActive(player, "last_reserve") || incomingDamage <= 0.0f) return 0.0f;
        long now = gameTime(player);
        if (player.getAttachedOrCreate(LAST_RESERVE_COOLDOWN_UNTIL) > now
                || player.getHealth() - incomingDamage > player.getMaxHealth() * RefugioAbilityRules.lastReserveHealthThresholdRatio()) {
            return 0.0f;
        }
        int rank = rank(player, "last_reserve");
        if (rank <= 0) return 0.0f;
        player.setAttached(LAST_RESERVE_COOLDOWN_UNTIL, now + RefugioAbilityRules.lastReserveCooldownTicks(rank));
        float reduction = RefugioAbilityRules.lastReserveReduction(rank);
        int reductionPct = Math.round(reduction * 100.0f);

        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, true, true,
                player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.35, 0.45, 0.35, 0.10);
        player.sendSystemMessage(Component.translatable("message.refugio.ability.last_reserve_triggered", reductionPct)
                .withStyle(Style.EMPTY.withColor(0xFFFFD369).withBold(true)), true);
        ServerPlayNetworking.send(player, new RefugioUiPayload("LAST_RESERVE_FX\t" + reductionPct));
        return reduction;
    }

    /** Triggers Area Defense explosion when the player kills an enemy (melee or bow). */
    public static void triggerAreaDefenseOnKill(ServerPlayer player, LivingEntity killedTarget) {
        if (!isActive(player, "area_defense") || killedTarget == null) return;
        if (!APPLYING_AREA_DEFENSE.add(player.getUUID())) return;
        try {
            long now = gameTime(player);
            if (player.getAttachedOrCreate(AREA_DEFENSE_COOLDOWN_UNTIL) > now) return;
            int rank = rank(player, "area_defense");
            if (rank <= 0) return;

            double radius = RefugioAbilityRules.areaRadius(rank);
            double radiusSq = radius * radius;
            int maxTargets = RefugioAbilityRules.areaTargetCount(rank);
            float baseDamage = RefugioAbilityRules.areaBaseDamage(rank);

            ServerLevel level = player.level();
            double ex = killedTarget.getX();
            double ey = killedTarget.getY() + killedTarget.getBbHeight() * 0.5;
            double ez = killedTarget.getZ();
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, ex, ey, ez, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.EXPLOSION, true, true, ex, ey, ez, 10, 0.6, 0.4, 0.6, 0.05);
            level.sendParticles(ParticleTypes.FLAME, true, true, ex, ey, ez, 16, 0.5, 0.35, 0.5, 0.08);

            player.setAttached(AREA_DEFENSE_COOLDOWN_UNTIL, now + RefugioAbilityRules.areaCooldownTicks(rank));

            var nearby = level.getEntitiesOfClass(LivingEntity.class,
                            killedTarget.getBoundingBox().inflate(radius), entity -> entity != killedTarget
                                    && entity instanceof Enemy && entity.isAlive()
                                    && killedTarget.distanceToSqr(entity) <= radiusSq
                                    && player.hasLineOfSight(entity))
                    .stream()
                    .sorted((left, right) -> Double.compare(killedTarget.distanceToSqr(left),
                            killedTarget.distanceToSqr(right)))
                    .limit(maxTargets)
                    .toList();

            for (LivingEntity secondary : nearby) {
                secondary.hurtServer(level, player.damageSources().playerAttack(player), baseDamage);
            }
        } finally {
            APPLYING_AREA_DEFENSE.remove(player.getUUID());
        }
    }

    public static void applyAreaDefense(ServerPlayer player, LivingEntity primaryTarget, float primaryDamage) {
        if (primaryTarget != null && !primaryTarget.isAlive() && primaryDamage > 0.0f) {
            triggerAreaDefenseOnKill(player, primaryTarget);
        }
    }

    public static void activateMovement(ServerPlayer player) {
        activateMovement(player, "JUMP_PASSIVE");
    }

    public static void activateMovement(ServerPlayer player, String mode) {
        if (player == null || !player.isAlive()) return;
        switch (mode) {
            case "SUPER_JUMP" -> activateSuperJump(player);
            case "DOUBLE_JUMP" -> {
                if (isActive(player, "double_jump") && !player.onGround()) {
                    activateDoubleJump(player);
                }
            }
            case "JUMP_PASSIVE" -> {
                if (isActive(player, "super_jump") && player.onGround()) {
                    activateSuperJump(player);
                } else if (isActive(player, "double_jump") && !player.onGround()) {
                    activateDoubleJump(player);
                }
            }
            case "JUMP_EXPLICIT" -> {
                boolean activated = false;
                if (isActive(player, "super_jump")) {
                    activated = activateSuperJump(player);
                } else if (isActive(player, "double_jump")) {
                    activated = activateDoubleJump(player);
                }
                if (!activated && !isActive(player, "super_jump") && !isActive(player, "double_jump")) {
                    sendNoMovementSkillMessage(player);
                }
            }
            case "BURST_PASSIVE" -> {
                if (isActive(player, "burst")) {
                    activateBurst(player, true);
                }
            }
            case "BURST_EXPLICIT" -> {
                if (isActive(player, "burst")) {
                    activateBurst(player, true);
                } else {
                    sendNoMovementSkillMessage(player);
                }
            }
            default -> { }
        }
    }

    private static boolean activateSuperJump(ServerPlayer player) {
        if (!canActivateSuperJump(player)) return false;
        int rank = rank(player, "super_jump");
        if (rank <= 0) return false;

        long now = gameTime(player);
        beginJumpTracking(player);
        player.setAttached(SUPER_JUMP_USED, true);
        player.setAttached(SUPER_JUMP_LAUNCHED_AT, now);
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(velocity.x, Math.max(velocity.y, RefugioAbilityRules.superJumpVelocity(rank)), velocity.z);
        player.hurtMarked = true;
        return true;
    }

    private static boolean canActivateSuperJump(ServerPlayer player) {
        return player != null
                && player.isAlive()
                && !player.isPassenger()
                && !player.isInWater()
                && !player.isFallFlying()
                && !player.getAbilities().flying
                && player.onGround()
                && !player.getAttachedOrCreate(SUPER_JUMP_USED)
                && isActive(player, "super_jump")
                && rank(player, "super_jump") > 0;
    }

    private static boolean activateDoubleJump(ServerPlayer player) {
        if (player.isPassenger() || player.isInWater() || player.isFallFlying()
                || player.getAbilities().flying || player.onGround()
                || player.getAttachedOrCreate(DOUBLE_JUMP_USED)) return false;
        int rank = rank(player, "double_jump");
        if (rank <= 0) return false;
        beginJumpTracking(player);
        player.setAttached(DOUBLE_JUMP_USED, true);
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(velocity.x, Math.max(velocity.y, RefugioAbilityRules.doubleJumpVelocity(rank)), velocity.z);
        player.hurtMarked = true;
        return true;
    }

    private static boolean activateBurst(ServerPlayer player, boolean notifyCooldown) {
        if (player.isPassenger() || player.isInWater()) return false;
        int rank = rank(player, "burst");
        if (rank <= 0) return false;
        long now = gameTime(player);
        long cooldownUntil = player.getAttachedOrCreate(BURST_COOLDOWN_UNTIL);
        if (cooldownUntil > now) {
            if (notifyCooldown) {
                long remainingSeconds = Math.max(1L, (long) Math.ceil((cooldownUntil - now) / 20.0));
                sendBurstCooldownMessage(player, remainingSeconds);
            }
            return false;
        }
        double yaw = Math.toRadians(player.getYRot());
        double dx = -Math.sin(yaw);
        double dz = Math.cos(yaw);
        double strength = RefugioAbilityRules.burstImpulse(rank);
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(velocity.x + dx * strength, Math.max(velocity.y, 0.12), velocity.z + dz * strength);

        int maxCharges = RefugioAbilityRules.burstMaxCharges(rank);
        int chargesUsed = player.getAttachedOrCreate(BURST_CHARGES_USED) + 1;
        if (chargesUsed < maxCharges) {
            player.setAttached(BURST_CHARGES_USED, chargesUsed);
        } else {
            player.setAttached(BURST_CHARGES_USED, 0);
            player.setAttached(BURST_COOLDOWN_UNTIL, now + RefugioAbilityRules.burstCooldownTicks(rank));
        }
        player.hurtMarked = true;
        return true;
    }

    private static void sendBurstCooldownMessage(ServerPlayer player, long remainingSeconds) {
        MutableComponent prefix = Component.literal("✦ [Refúgio] ")
                .withStyle(Style.EMPTY.withColor(0xFFD4A34F).withBold(true));
        MutableComponent body = Component.translatable("message.refugio.ability.burst_cooldown", remainingSeconds)
                .withStyle(Style.EMPTY.withColor(0xFFFF7A66).withBold(true));
        player.sendSystemMessage(prefix.append(body));
    }

    private static void sendNoMovementSkillMessage(ServerPlayer player) {
        MutableComponent prefix = Component.literal("✦ [Refúgio] ")
                .withStyle(Style.EMPTY.withColor(0xFFD4A34F).withBold(true));
        MutableComponent body = Component.translatable("message.refugio.ability.no_movement_skill")
                .withStyle(Style.EMPTY.withColor(0xFFFFE6B8));
        player.sendSystemMessage(prefix.append(body));
    }

    private static void beginJumpTracking(ServerPlayer player) {
        String dimension = player.level().dimension().toString();
        boolean onGround = player.onGround();
        boolean alreadyTrackingInDim = player.getAttachedOrCreate(JUMP_TRACKING)
                && dimension.equals(player.getAttachedOrCreate(JUMP_DIMENSION));
        boolean hasLastGroundedInDim = dimension.equals(player.getAttachedOrCreate(LAST_GROUNDED_DIMENSION));
        double originY = RefugioAbilityRules.resolveJumpOriginY(
                onGround,
                alreadyTrackingInDim,
                player.getAttachedOrCreate(JUMP_ORIGIN_Y),
                hasLastGroundedInDim,
                player.getAttachedOrCreate(LAST_GROUNDED_Y),
                player.getY()
        );
        if (onGround) {
            player.setAttached(LAST_GROUNDED_Y, originY);
            player.setAttached(LAST_GROUNDED_DIMENSION, dimension);
        }
        player.setAttached(JUMP_TRACKING, true);
        player.setAttached(JUMP_ORIGIN_Y, originY);
        player.setAttached(JUMP_STARTED_AT, gameTime(player));
        player.setAttached(JUMP_DIMENSION, dimension);
        player.setAttached(JUMP_LEFT_GROUND, !onGround);
        recordTickPosition(player);
    }

    /**
     * Returns vanilla fall damage adjusted to only count vertical descent below the jump's launch surface.
     * Untracked falls are returned completely unchanged.
     */
    public static float adjustTrackedJumpFallDamage(ServerPlayer player, float vanillaDamage) {
        boolean tracking = player.getAttachedOrCreate(JUMP_TRACKING);
        if (!tracking) {
            return vanillaDamage;
        }
        String currentDim = player.level().dimension().toString();
        if (!currentDim.equals(player.getAttachedOrCreate(JUMP_DIMENSION))) {
            clearJumpTracking(player);
            return vanillaDamage;
        }
        double originY = player.getAttachedOrCreate(JUMP_ORIGIN_Y);
        double landingY = player.getY();
        float adjusted = RefugioAbilityRules.adjustTrackedJumpFallDamage(
                true, originY, landingY, player.fallDistance, vanillaDamage);
        clearJumpTracking(player);
        return adjusted;
    }

    private static void refreshJumpState(ServerPlayer player) {
        long now = gameTime(player);
        String currentDimension = player.level().dimension().toString();
        boolean teleported = hasSuddenTeleportJump(player, currentDimension);
        recordTickPosition(player);

        if (player.getAttachedOrCreate(SUPER_JUMP_USED)) {
            long launchedAt = player.getAttachedOrCreate(SUPER_JUMP_LAUNCHED_AT);
            if (!player.onGround()) {
                player.setAttached(SUPER_JUMP_LAUNCHED_AT, Math.min(launchedAt, now - 10L));
            } else if (now - launchedAt > 8L) {
                player.setAttached(SUPER_JUMP_USED, false);
            }
        }
        if (player.getAttachedOrCreate(DOUBLE_JUMP_USED) && player.onGround()) {
            player.setAttached(DOUBLE_JUMP_USED, false);
        }

        if (player.getAttachedOrCreate(JUMP_TRACKING)) {
            long startedAt = player.getAttachedOrCreate(JUMP_STARTED_AT);
            if (!player.onGround()) {
                player.setAttached(JUMP_LEFT_GROUND, true);
            }
            boolean invalid = teleported
                    || !player.isAlive()
                    || player.isPassenger()
                    || player.isInWater()
                    || player.isInLava()
                    || player.onClimbable()
                    || player.isFallFlying()
                    || player.getAbilities().flying
                    || !currentDimension.equals(player.getAttachedOrCreate(JUMP_DIMENSION))
                    || now - startedAt > 1200L;
            boolean landedAfterAirborne = player.onGround()
                    && (player.getAttachedOrCreate(JUMP_LEFT_GROUND) || now - startedAt > 6L);
            if (invalid || landedAfterAirborne) {
                clearJumpTracking(player);
            }
        }

        if (player.onGround() && !player.getAttachedOrCreate(JUMP_TRACKING)) {
            player.setAttached(LAST_GROUNDED_Y, RefugioAbilityRules.normalizeSurfaceCoordinate(player.getY()));
            player.setAttached(LAST_GROUNDED_DIMENSION, currentDimension);
        }
    }

    private static boolean hasSuddenTeleportJump(ServerPlayer player, String currentDimension) {
        String lastDim = player.getAttachedOrCreate(LAST_TICK_DIMENSION);
        if (lastDim == null || lastDim.isEmpty() || !lastDim.equals(currentDimension)) {
            return true;
        }
        double dx = player.getX() - player.getAttachedOrCreate(LAST_TICK_X);
        double dy = player.getY() - player.getAttachedOrCreate(LAST_TICK_Y);
        double dz = player.getZ() - player.getAttachedOrCreate(LAST_TICK_Z);
        return (dx * dx + dz * dz) > 64.0 || Math.abs(dy) > 16.0;
    }

    private static void recordTickPosition(ServerPlayer player) {
        player.setAttached(LAST_TICK_X, player.getX());
        player.setAttached(LAST_TICK_Y, player.getY());
        player.setAttached(LAST_TICK_Z, player.getZ());
        player.setAttached(LAST_TICK_DIMENSION, player.level().dimension().toString());
    }

    public static void clearJumpTracking(ServerPlayer player) {
        player.setAttached(JUMP_TRACKING, false);
        player.setAttached(JUMP_ORIGIN_Y, 0.0);
        player.setAttached(JUMP_STARTED_AT, 0L);
        player.setAttached(JUMP_DIMENSION, "");
        player.setAttached(JUMP_LEFT_GROUND, false);
    }

    private static void refreshUtilitySkills(ServerPlayer player) {
        if (isActive(player, "collector")) {
            int rank = rank(player, "collector");
            double range = RefugioAbilityRules.collectorRadius(rank);
            for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class,
                    player.getBoundingBox().inflate(range), entity -> !entity.isRemoved() && !entity.getItem().isEmpty())) {
                Vec3 towardPlayer = player.position().subtract(item.position());
                double distance = towardPlayer.length();
                if (distance > 0.15 && distance <= range && player.hasLineOfSight(item)) {
                    item.addDeltaMovement(towardPlayer.scale(RefugioAbilityRules.collectorPull(rank) / distance));
                    item.hurtMarked = true;
                }
            }
        }
    }

    private static void refreshVitalitySkills(ServerPlayer player) {
        int regenRank = rank(player, "natural_regeneration");
        if (!player.isAlive() || player.isSpectator() || regenRank <= 0
                || !isActive(player, "natural_regeneration")) {
            player.setAttached(NATURAL_REGEN_ACTIVE, false);
            return;
        }

        long now = gameTime(player);
        long lastCombat = player.getAttachedOrCreate(LAST_COMBAT_TICK);
        if (lastCombat != Long.MIN_VALUE && now - lastCombat < RefugioAbilityRules.naturalRegenerationOutOfCombatTicks()) {
            player.setAttached(NATURAL_REGEN_ACTIVE, false);
            return;
        }

        float currentHealth = player.getHealth();
        float triggerHealth = RefugioAbilityRules.naturalRegenerationTriggerHealth(regenRank);
        float targetHealth = RefugioAbilityRules.naturalRegenerationTargetHealth(regenRank);
        if (targetHealth <= 0.0f || currentHealth >= targetHealth) {
            player.setAttached(NATURAL_REGEN_ACTIVE, false);
            return;
        }

        if (!player.getAttachedOrCreate(NATURAL_REGEN_ACTIVE)) {
            if (currentHealth >= triggerHealth) return;
            player.setAttached(NATURAL_REGEN_ACTIVE, true);
        }
        if (now % RefugioAbilityRules.naturalRegenerationIntervalTicks() == 0L) {
            player.heal(Math.min(RefugioAbilityRules.naturalRegenerationAmount(), targetHealth - currentHealth));
        }
    }

    private static void healOnHostileKill(ServerPlayer player) {
        if (!isActive(player, "vitality_on_kill")) return;
        int skillRank = rank(player, "vitality_on_kill");
        if (skillRank <= 0) return;
        float amount = (float) player.getMaxHealth() * RefugioAbilityRules.vitalityOnKillHealRatio(skillRank);
        if (amount > 0.0f) player.heal(amount);
    }

    public static void recordCombatActivity(ServerPlayer player) {
        player.setAttached(LAST_COMBAT_TICK, gameTime(player));
        player.setAttached(NATURAL_REGEN_ACTIVE, false);
    }

    private static long gameTime(ServerPlayer player) {
        return player.level().getServer().overworld().getGameTime();
    }

    public static void refreshAttributeSkills(ServerPlayer player) {
        updateModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), Refugio.id("skill_momentum"),
                isActive(player, "momentum") ? RefugioAbilityRules.movementSpeedBonus(rank(player, "momentum")) : 0.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        updateModifier(player.getAttribute(Attributes.BLOCK_BREAK_SPEED), Refugio.id("skill_mining"),
                isActive(player, "mining") ? RefugioAbilityRules.miningSpeedBonus(rank(player, "mining")) : 0.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        updateModifier(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE), Refugio.id("skill_anchor"),
                isActive(player, "anchor") ? RefugioAbilityRules.anchorKnockbackBonus(rank(player, "anchor")) : 0.0,
                AttributeModifier.Operation.ADD_VALUE);
        updateModifier(player.getAttribute(Attributes.MAX_HEALTH), Refugio.id("skill_vitality_health"),
                isActive(player, "vitality_health")
                        ? RefugioAbilityRules.vitalityMaxHealthBonus(rank(player, "vitality_health")) : 0.0,
                AttributeModifier.Operation.ADD_VALUE);
        if (player.getHealth() > player.getMaxHealth()) player.setHealth((float) player.getMaxHealth());
    }

    private static void updateModifier(AttributeInstance instance, Identifier id, double amount,
                                       AttributeModifier.Operation operation) {
        if (instance == null) return;
        AttributeModifier current = instance.getModifier(id);
        if (amount <= 0.0) {
            if (current != null) instance.removeModifier(id);
            return;
        }
        if (current != null && current.amount() == amount && current.operation() == operation) return;
        if (current != null) instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, amount, operation));
    }

    public static boolean isUnlocked(ServerPlayer player, String id) {
        Skill skill = skill(id);
        if (skill == null) return false;
        List<Skill> trackSkills = skills(skill.track());
        int index = trackSkills.indexOf(skill);
        boolean previousRankOwned = index > 0 && rank(player, trackSkills.get(index - 1).id()) > 0;
        return RefugioAbilityRules.trackNodeUnlocked(index, previousRankOwned);
    }

    public static boolean isFreeChoiceCandidate(ServerPlayer player, String id) {
        Skill skill = skill(id);
        return skill != null && RefugioAbilityRules.canClaimFreeChoice(hasFreeChoice(player), rank(player, id),
                !skills(skill.track()).isEmpty() && skills(skill.track()).getFirst().id().equals(id));
    }

    /** Claims Kael's one-time gift or purchases exactly the next rank; all checks run on the server. */
    public static PurchaseResult buyNextRank(ServerPlayer player, String id) {
        Skill skill = skill(id);
        if (skill == null) return PurchaseResult.INVALID;
        int currentRank = rank(player, id);
        if (currentRank >= maxRank(id)) return PurchaseResult.MAX_RANK;
        if (!isUnlocked(player, id)) return PurchaseResult.LOCKED;

        if (isFreeChoiceCandidate(player, id)) {
            Map<String, Integer> ranks = new HashMap<>(player.getAttachedOrCreate(RANKS));
            ranks.put(id, 1);
            player.setAttached(RANKS, ranks);
            player.setAttached(FREE_CHOICE, id);
            setActive(player, id, true);
            syncMovementState(player);
            return PurchaseResult.FREE;
        }
        if (currentRank == 0 && !hasFreeChoice(player)) return PurchaseResult.CHOOSE_FREE_FIRST;

        int price = costForRank(id, currentRank + 1);
        if (!RefugioPlayerData.spendCoins(player, price)) return PurchaseResult.NO_COINS;
        Map<String, Integer> ranks = new HashMap<>(player.getAttachedOrCreate(RANKS));
        ranks.put(id, currentRank + 1);
        player.setAttached(RANKS, ranks);
        if (currentRank == 0) setActive(player, id, true);
        syncMovementState(player);
        return PurchaseResult.PURCHASED;
    }

    public static boolean setActive(ServerPlayer player, String id, boolean active) {
        if (skill(id) == null || rank(player, id) == 0) return false;
        Map<String, Boolean> states = new HashMap<>(player.getAttachedOrCreate(ACTIVE));
        if (active && (id.equals("super_jump") || id.equals("double_jump"))) {
            states.put(id.equals("super_jump") ? "double_jump" : "super_jump", false);
        }
        states.put(id, active);
        player.setAttached(ACTIVE, states);
        syncMovementState(player);
        return true;
    }

    public enum PurchaseResult { FREE, PURCHASED, NO_COINS, LOCKED, MAX_RANK, CHOOSE_FREE_FIRST, INVALID }
}
