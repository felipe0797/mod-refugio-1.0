package com.felpz.refugio.ability;

import com.felpz.refugio.Refugio;
import com.felpz.refugio.data.RefugioPlayerData;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            skill("sword_critical_damage", "Dano Crítico", "+10% de dano crítico por rank, até +50%; afeta críticos de salto e os gerados por Chance Crítica com espadas.", Track.SWORD, "minecraft:diamond_sword"),
            skill("sword_first_cut", "Primeiro Corte", "Após 10 s sem causar nem receber dano, o próximo golpe com espada contra um inimigo ganha +5% por rank (máx. +25%).", Track.SWORD, "minecraft:golden_sword"),
            skill("sword_mastery", "Maestria com Espadas", "+2% de dano com espadas por rank, até +10%.", Track.SWORD, "minecraft:netherite_sword"),

            skill("axe_critical_chance", "Chance Crítica", "+10 pontos percentuais por rank, até +50%; chance de crítico em golpes com machados contra inimigos.", Track.AXE, "minecraft:iron_axe"),
            skill("axe_critical_damage", "Dano Crítico", "+10% de dano crítico por rank, até +50%; afeta críticos de salto e os gerados por Chance Crítica com machados.", Track.AXE, "minecraft:diamond_axe"),
            skill("axe_first_cut", "Primeiro Corte", "Após 10 s sem causar nem receber dano, o próximo golpe com machado contra um inimigo ganha +5% por rank (máx. +25%).", Track.AXE, "minecraft:golden_axe"),
            skill("axe_mastery", "Maestria com Machados", "+2% de dano com machados por rank, até +10%.", Track.AXE, "minecraft:iron_axe"),

            skill("bow_critical_chance", "Chance Crítica", "+10 pontos percentuais por rank, até +50%; chance de crítico em flechas de arco contra inimigos.", Track.BOW, "minecraft:bow"),
            skill("bow_critical_damage", "Dano Crítico", "+10% de dano crítico por rank, até +50%; afeta críticos naturais e os gerados por Chance Crítica com arco.", Track.BOW, "minecraft:tipped_arrow"),
            skill("bow_first_shot", "Primeiro Tiro", "Após 10 s sem causar nem receber dano, a próxima flecha que acertar um inimigo ganha +5% por rank (máx. +25%).", Track.BOW, "minecraft:arrow"),
            skill("bow_mastery", "Maestria com Arcos", "+5% de dano de flechas de arco por rank, até +25%; só contra inimigos.", Track.BOW, "minecraft:bow"),
            skill("steady_aim", "Mira Estável", "Reduz em 10% por rank a dispersão das flechas, até 50%.", Track.BOW, "minecraft:target"),

            skill("retaliation", "Retaliação", "Após bloquear com escudo, o próximo ataque em 3 s ganha +5% por rank (máx. +25%); recarga de 10 s.", Track.EXTRAS, "minecraft:shield"),
            skill("horde_vigor", "Vigor da Horda", "Abates de inimigos renovam por 5 s +6% de dano por rank, até +30%.", Track.EXTRAS, "minecraft:rotten_flesh"),

            skill("firm_stance", "Postura Firme", "Reduz em 4% por rank (máx. 20%) o dano recebido de inimigos.", Track.ACTIVE_DEFENSE, "minecraft:shield"),
            skill("last_reserve", "Última Reserva", "Ao cair abaixo de 30% de vida, reduz o golpe em 30–70% conforme o rank; recarga de 60–20 s.", Track.ACTIVE_DEFENSE, "minecraft:totem_of_undying"),
            skill("anchor", "Âncora", "+8 pontos percentuais de resistência a empurrão por rank, até +40%.", Track.ACTIVE_DEFENSE, "minecraft:iron_boots"),
            skill("area_defense", "Defesa em Área", "Após acertar um inimigo, atinge até 2–4 inimigos visíveis em 3,4–5 blocos com 15–35% do dano (máx. 8); recarga de 7–3 s.", Track.ACTIVE_DEFENSE, "minecraft:iron_chestplate"),

            skill("light_step", "Passo Leve", "Reduz o dano de queda em 4% por rank, até 20%.", Track.REFLEXES, "minecraft:feather"),
            skill("burst", "Arranque", "Pressione G para avançar na direção da mira; impulso cresce por rank (máx. 0,95) e recarga de 6 s.", Track.REFLEXES, "minecraft:sugar"),
            skill("momentum", "Ímpeto", "+6% de velocidade de movimento por rank, até +30%.", Track.REFLEXES, "minecraft:rabbit_foot"),
            skill("super_jump", "Super Pulo", "Pressione G no chão para saltar mais alto; força aumenta por rank. O dano de queda considera a altura de partida; recarga de 1 s.", Track.REFLEXES, "minecraft:slime_block"),
            skill("double_jump", "Pulo Duplo", "Pressione G uma vez no ar para saltar de novo. Renova ao pousar; dano de queda conta apenas abaixo da altura de ativação.", Track.REFLEXES, "minecraft:phantom_membrane"),

            skill("mining", "Escavação Ágil", "+5% de velocidade de mineração por rank, até +25%; sem revelar minérios.", Track.MINING, "minecraft:iron_pickaxe"),
            skill("preparation", "Preparação", "Cada alimento concede +0,5 de saturação por rank, até +2,5; não amplia poções.", Track.ALCHEMY, "minecraft:potion"),
            skill("tracker", "Rastreador", "Mostra no máximo a ameaça visível mais próxima a cada 2 s; alcance de 18–26 blocos, sem revelar através de paredes.", Track.EXPLORATION, "minecraft:compass"),
            skill("collector", "Coletor", "Atrai itens gradualmente num raio de 2–4 blocos; não cria nem duplica drops.", Track.EXPLORATION, "minecraft:bundle"),
            skill("exploration", "Exploração", "Reduz em 3% por rank a exaustão fora do Refúgio, até 15%.", Track.EXPLORATION, "minecraft:map"),
            skill("logistics", "Logística", "Cada rank dá 5% de chance de ignorar 1 ponto de dano de durabilidade, até 25%.", Track.EXPLORATION, "minecraft:chest")
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
                    .initializer(() -> Long.MIN_VALUE));
    private static final AttachmentType<Long> RETALIATION_READY_UNTIL = AttachmentRegistry.create(
            Refugio.id("ability_retaliation_ready"), builder -> builder.initializer(() -> 0L));
    private static final AttachmentType<Long> RETALIATION_COOLDOWN_UNTIL = AttachmentRegistry.create(
            Refugio.id("ability_retaliation_cooldown"), builder -> builder.initializer(() -> 0L));
    private static final AttachmentType<Long> HORDE_VIGOR_UNTIL = AttachmentRegistry.create(
            Refugio.id("ability_horde_vigor_until"), builder -> builder.initializer(() -> 0L));
    private static final AttachmentType<Long> LAST_RESERVE_COOLDOWN_UNTIL = AttachmentRegistry.create(
            Refugio.id("ability_last_reserve_cooldown"), builder -> builder.initializer(() -> 0L));
    private static final AttachmentType<Long> JUMP_COOLDOWN_UNTIL = AttachmentRegistry.create(
            Refugio.id("ability_jump_cooldown"), builder -> builder.initializer(() -> 0L));
    private static final AttachmentType<Long> BURST_COOLDOWN_UNTIL = AttachmentRegistry.create(
            Refugio.id("ability_burst_cooldown"), builder -> builder.initializer(() -> 0L));
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
    private static final AttachmentType<Long> TRACKER_NEXT_TICK = AttachmentRegistry.create(
            Refugio.id("ability_tracker_next_tick"), builder -> builder.initializer(() -> 0L));
    private static final AttachmentType<Long> AREA_DEFENSE_COOLDOWN_UNTIL = AttachmentRegistry.create(
            Refugio.id("ability_area_defense_cooldown"), builder -> builder.initializer(() -> 0L));

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
        return Math.max(0, Math.min(RefugioAbilityRules.maxRank(), player.getAttachedOrCreate(RANKS).getOrDefault(id, 0)));
    }
    public static boolean isActive(ServerPlayer player, String id) { return player.getAttachedOrCreate(ACTIVE).getOrDefault(id, false); }
    public static String freeChoice(ServerPlayer player) { return player.getAttachedOrCreate(FREE_CHOICE); }
    public static boolean hasFreeChoice(ServerPlayer player) { return !freeChoice(player).isEmpty(); }

    public static void registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(server ->
                server.getPlayerList().getPlayers().forEach(player -> {
                    refreshAttributeSkills(player);
                    refreshJumpState(player);
                    refreshUtilitySkills(player);
                }));
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay()
                .register(ActivateMovementPayload.TYPE, ActivateMovementPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                ActivateMovementPayload.TYPE, (payload, context) -> activateMovement(context.player()));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (entity instanceof ServerPlayer victim) {
                recordCombatActivity(victim);
                if (blocked && source.getEntity() instanceof Enemy && isActive(victim, "retaliation")) {
                    long now = gameTime(victim);
                    if (now >= victim.getAttachedOrCreate(RETALIATION_COOLDOWN_UNTIL)) {
                        victim.setAttached(RETALIATION_READY_UNTIL, now + 60L);
                        victim.setAttached(RETALIATION_COOLDOWN_UNTIL, now + 200L);
                    }
                }
            }
            if (entity instanceof Enemy && source.getEntity() instanceof ServerPlayer attacker) {
                recordCombatActivity(attacker);
                if (damageTaken > 0.0f) consumeRetaliation(attacker);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getEntity() instanceof ServerPlayer killer)) return;
            recordCombatActivity(killer);
            if (entity instanceof Enemy && isActive(killer, "horde_vigor")) {
                killer.setAttached(HORDE_VIGOR_UNTIL, gameTime(killer) + 100L);
            }
        });
    }

    /** Returns the equipped weapon's opening-strike rank after a 10-second quiet window. */
    public static int beginAttack(ServerPlayer player) {
        return beginTimedFirstStrike(player, firstStrikeSkillId(player.getMainHandItem()));
    }

    /** Returns First Shot's rank when an enemy is hit after the same quiet window as First Cut. */
    public static int beginBowFirstShot(ServerPlayer player) {
        return beginTimedFirstStrike(player, "bow_first_shot");
    }

    private static int beginTimedFirstStrike(ServerPlayer player, String skillId) {
        if (skillId == null || !isActive(player, skillId)) return 0;
        long now = gameTime(player);
        long last = player.getAttachedOrCreate(LAST_COMBAT_TICK);
        return last == Long.MIN_VALUE || now - last >= 200L ? rank(player, skillId) : 0;
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

    /** Returns Last Reserve's one-hit damage reduction, or zero when its trigger is unavailable. */
    public static float tryLastReserve(ServerPlayer player, float incomingDamage) {
        if (!isActive(player, "last_reserve") || incomingDamage <= 0.0f) return 0.0f;
        long now = gameTime(player);
        if (player.getAttachedOrCreate(LAST_RESERVE_COOLDOWN_UNTIL) > now
                || player.getHealth() - incomingDamage > player.getMaxHealth() * 0.30f) return 0.0f;
        int rank = rank(player, "last_reserve");
        if (rank <= 0) return 0.0f;
        player.setAttached(LAST_RESERVE_COOLDOWN_UNTIL, now + RefugioAbilityRules.lastReserveCooldownTicks(rank));
        return RefugioAbilityRules.lastReserveReduction(rank);
    }

    public static void applyAreaDefense(ServerPlayer player, LivingEntity primaryTarget, float primaryDamage) {
        if (!isActive(player, "area_defense") || primaryDamage <= 0.0f || !primaryTarget.isAlive()) return;
        long now = gameTime(player);
        if (player.getAttachedOrCreate(AREA_DEFENSE_COOLDOWN_UNTIL) > now) return;
        int rank = rank(player, "area_defense");
        if (rank <= 0) return;
        double radius = RefugioAbilityRules.areaRadius(rank);
        int maxTargets = RefugioAbilityRules.areaTargetCount(rank);
        var nearby = player.level().getEntitiesOfClass(LivingEntity.class,
                        primaryTarget.getBoundingBox().inflate(radius), entity -> entity != primaryTarget
                                && entity instanceof Enemy && entity.isAlive() && player.hasLineOfSight(entity))
                .stream().sorted((left, right) -> Double.compare(primaryTarget.distanceToSqr(left),
                        primaryTarget.distanceToSqr(right))).limit(maxTargets).toList();
        if (nearby.isEmpty()) return;

        player.setAttached(AREA_DEFENSE_COOLDOWN_UNTIL, now + RefugioAbilityRules.areaCooldownTicks(rank));
        float secondaryDamage = Math.min(8.0f, primaryDamage * RefugioAbilityRules.areaDamageRatio(rank));
        for (LivingEntity secondary : nearby) {
            secondary.hurtServer(player.level(), player.damageSources().playerAttack(player), secondaryDamage);
        }
    }

    public static void activateMovement(ServerPlayer player) {
        boolean activated = false;
        if (isActive(player, "super_jump")) activated |= activateSuperJump(player);
        else if (isActive(player, "double_jump")) activated |= activateDoubleJump(player);
        if (isActive(player, "burst")) activated |= activateBurst(player);
        if (!activated) player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "message.refugio.ability.no_movement_skill"));
    }

    private static boolean activateSuperJump(ServerPlayer player) {
        if (player.isPassenger() || player.isInWater() || !player.onGround()) return false;
        long now = gameTime(player);
        if (player.getAttachedOrCreate(JUMP_COOLDOWN_UNTIL) > now) return false;
        int rank = rank(player, "super_jump");
        if (rank <= 0) return false;
        beginJumpTracking(player);
        player.setAttached(JUMP_COOLDOWN_UNTIL, now + 20L);
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(velocity.x, Math.max(velocity.y, RefugioAbilityRules.superJumpVelocity(rank)), velocity.z);
        player.hurtMarked = true;
        return true;
    }

    private static boolean activateDoubleJump(ServerPlayer player) {
        if (player.isPassenger() || player.isInWater() || player.onGround()
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

    private static boolean activateBurst(ServerPlayer player) {
        if (player.isPassenger() || player.isInWater()) return false;
        long now = gameTime(player);
        if (player.getAttachedOrCreate(BURST_COOLDOWN_UNTIL) > now) return false;
        int rank = rank(player, "burst");
        if (rank <= 0) return false;
        double yaw = Math.toRadians(player.getYRot());
        double dx = -Math.sin(yaw);
        double dz = Math.cos(yaw);
        double strength = RefugioAbilityRules.burstVelocity(rank);
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(velocity.x + dx * strength, Math.max(velocity.y, 0.12), velocity.z + dz * strength);
        player.setAttached(BURST_COOLDOWN_UNTIL, now + 120L);
        player.hurtMarked = true;
        return true;
    }

    private static void beginJumpTracking(ServerPlayer player) {
        player.setAttached(JUMP_TRACKING, true);
        player.setAttached(JUMP_ORIGIN_Y, player.getY());
        player.setAttached(JUMP_STARTED_AT, gameTime(player));
        player.setAttached(JUMP_DIMENSION, player.level().dimension().toString());
    }

    /** Returns vanilla fall damage adjusted to only count descent below the jump's launch height. */
    public static float adjustTrackedJumpFallDamage(ServerPlayer player, float vanillaDamage) {
        if (!player.getAttachedOrCreate(JUMP_TRACKING)) return vanillaDamage;
        double belowOrigin = player.getAttachedOrCreate(JUMP_ORIGIN_Y) - player.getY();
        double actualDamageDistance = Math.max(0.0, player.fallDistance - 3.0);
        double intendedDamageDistance = Math.max(0.0, belowOrigin - 3.0);
        clearJumpTracking(player);
        if (intendedDamageDistance <= 0.0) return 0.0f;
        if (actualDamageDistance <= 0.0) return vanillaDamage;
        return vanillaDamage * (float) Math.min(1.0, intendedDamageDistance / actualDamageDistance);
    }

    private static void refreshJumpState(ServerPlayer player) {
        if (player.getAttachedOrCreate(DOUBLE_JUMP_USED) && player.onGround()) {
            player.setAttached(DOUBLE_JUMP_USED, false);
        }
        if (!player.getAttachedOrCreate(JUMP_TRACKING)) return;
        boolean invalid = !player.isAlive() || player.isPassenger() || player.isInWater()
                || player.isFallFlying() || player.getAbilities().flying
                || !player.level().dimension().toString().equals(player.getAttachedOrCreate(JUMP_DIMENSION))
                || gameTime(player) - player.getAttachedOrCreate(JUMP_STARTED_AT) > 1200L;
        if (invalid || player.onGround()) clearJumpTracking(player);
    }

    private static void clearJumpTracking(ServerPlayer player) {
        player.setAttached(JUMP_TRACKING, false);
        player.setAttached(JUMP_ORIGIN_Y, 0.0);
        player.setAttached(JUMP_STARTED_AT, 0L);
        player.setAttached(JUMP_DIMENSION, "");
    }

    private static void refreshUtilitySkills(ServerPlayer player) {
        long now = gameTime(player);
        if (isActive(player, "tracker") && now >= player.getAttachedOrCreate(TRACKER_NEXT_TICK)) {
            player.setAttached(TRACKER_NEXT_TICK, now + 40L);
            int rank = rank(player, "tracker");
            double range = RefugioAbilityRules.trackerRadius(rank);
            LivingEntity nearest = player.level().getEntitiesOfClass(LivingEntity.class,
                            player.getBoundingBox().inflate(range), entity -> entity instanceof Enemy
                                    && entity.isAlive() && player.hasLineOfSight(entity))
                    .stream().min((left, right) -> Double.compare(player.distanceToSqr(left), player.distanceToSqr(right)))
                    .orElse(null);
            if (nearest != null) {
                player.sendSystemMessage(Component.translatable("message.refugio.ability.tracker",
                        nearest.getDisplayName(), Math.round(Math.sqrt(player.distanceToSqr(nearest)))), true);
            }
        }

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

    private static void recordCombatActivity(ServerPlayer player) {
        player.setAttached(LAST_COMBAT_TICK, gameTime(player));
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
        if (currentRank >= RefugioAbilityRules.maxRank()) return PurchaseResult.MAX_RANK;
        if (!isUnlocked(player, id)) return PurchaseResult.LOCKED;

        if (isFreeChoiceCandidate(player, id)) {
            Map<String, Integer> ranks = new HashMap<>(player.getAttachedOrCreate(RANKS));
            ranks.put(id, 1);
            player.setAttached(RANKS, ranks);
            player.setAttached(FREE_CHOICE, id);
            setActive(player, id, true);
            return PurchaseResult.FREE;
        }
        if (currentRank == 0 && !hasFreeChoice(player)) return PurchaseResult.CHOOSE_FREE_FIRST;

        int price = costForRank(currentRank + 1);
        if (!RefugioPlayerData.spendCoins(player, price)) return PurchaseResult.NO_COINS;
        Map<String, Integer> ranks = new HashMap<>(player.getAttachedOrCreate(RANKS));
        ranks.put(id, currentRank + 1);
        player.setAttached(RANKS, ranks);
        if (currentRank == 0) setActive(player, id, true);
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
        return true;
    }

    public enum PurchaseResult { FREE, PURCHASED, NO_COINS, LOCKED, MAX_RANK, CHOOSE_FREE_FIRST, INVALID }
}
