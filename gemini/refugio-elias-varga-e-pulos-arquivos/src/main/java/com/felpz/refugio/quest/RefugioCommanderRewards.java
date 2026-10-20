package com.felpz.refugio.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Authoritative, runtime-independent specification of Commander Elias Varga's
 * 60 zombie-kill milestones, coin formula, items, enchantments, and 12 titles ("Última Linha").
 */
public final class RefugioCommanderRewards {
    public static final int TOTAL_MILESTONES = 60;
    public static final int TOTAL_TRACK_COINS = 29_550;
    public static final String DEFAULT_ZOMBIE_ENTITY_ID = "minecraft:zombie";

    public record EnchantSpec(String enchantmentId, int level) { }

    public record RewardItemSpec(String itemId, int count, List<EnchantSpec> enchantments, String fallbackItemId) {
        public RewardItemSpec {
            enchantments = enchantments == null ? List.of() : List.copyOf(enchantments);
            fallbackItemId = (fallbackItemId == null || fallbackItemId.isBlank()) ? "minecraft:diamond" : fallbackItemId;
        }
    }

    public record MilestoneSpec(int number, int requiredKills, String name, String objective,
                                int coins, String iconItemId, List<RewardItemSpec> items,
                                String exclusiveTitle) {
        public MilestoneSpec {
            items = items == null ? List.of() : List.copyOf(items);
            exclusiveTitle = exclusiveTitle == null ? "" : exclusiveTitle;
        }
    }

    public record TitleSpec(int index, int requiredKills, String title) { }

    private static final List<TitleSpec> DEFAULT_TITLES = List.of(
            new TitleSpec(0, 10, "Recruta"),
            new TitleSpec(1, 50, "Soldado"),
            new TitleSpec(2, 100, "Defensor"),
            new TitleSpec(3, 200, "Veterano"),
            new TitleSpec(4, 300, "Caçador de Mortos"),
            new TitleSpec(5, 400, "Executor"),
            new TitleSpec(6, 500, "Guardião"),
            new TitleSpec(7, 600, "Matador de Hordas"),
            new TitleSpec(8, 700, "Campeão"),
            new TitleSpec(9, 800, "Comandante de Campo"),
            new TitleSpec(10, 900, "Herói da Última Linha"),
            new TitleSpec(11, 1000, "Lenda da Última Linha")
    );

    private static final List<MilestoneSpec> DEFAULT_MILESTONES = buildDefaultMilestones();

    private static volatile List<MilestoneSpec> activeMilestones = DEFAULT_MILESTONES;
    private static volatile List<TitleSpec> activeTitles = DEFAULT_TITLES;
    private static volatile Set<String> countedEntityIds = Set.of(DEFAULT_ZOMBIE_ENTITY_ID);
    private static volatile boolean announceLegendGlobally = true;
    private static volatile boolean notifyMilestoneUnlock = true;

    private RefugioCommanderRewards() { }

    /**
     * Authoritative coin formula from specification: 50 + 15 * (n - 1) for n in 1..60.
     */
    public static int coinsForMilestone(int milestoneNumber) {
        if (milestoneNumber < 1 || milestoneNumber > TOTAL_MILESTONES) {
            return 0;
        }
        return 50 + 15 * (milestoneNumber - 1);
    }

    /**
     * Authoritative kill threshold for milestone n (1..60):
     * - n in 1..20: 10 to 200 in intervals of 10
     * - n in 21..60: 220 to 1000 in intervals of 20
     */
    public static int requiredKillsForMilestone(int milestoneNumber) {
        if (milestoneNumber < 1 || milestoneNumber > TOTAL_MILESTONES) {
            return -1;
        }
        if (milestoneNumber <= 20) {
            return milestoneNumber * 10;
        }
        return 200 + (milestoneNumber - 20) * 20;
    }

    public static int totalTrackCoins() {
        int sum = 0;
        for (MilestoneSpec m : activeMilestones) {
            sum += m.coins();
        }
        return sum;
    }

    public static List<MilestoneSpec> defaultMilestones() {
        return DEFAULT_MILESTONES;
    }

    public static List<TitleSpec> defaultTitles() {
        return DEFAULT_TITLES;
    }

    public static List<MilestoneSpec> activeMilestones() {
        return activeMilestones;
    }

    public static MilestoneSpec milestone(int milestoneNumber) {
        List<MilestoneSpec> list = activeMilestones;
        if (milestoneNumber < 1 || milestoneNumber > list.size()) {
            return null;
        }
        return list.get(milestoneNumber - 1);
    }

    public static List<TitleSpec> activeTitles() {
        return activeTitles;
    }

    public static Set<String> countedEntityIds() {
        return countedEntityIds;
    }

    public static boolean isGlobalLegendAnnouncementEnabled() {
        return announceLegendGlobally;
    }

    public static boolean isMilestoneUnlockMessageEnabled() {
        return notifyMilestoneUnlock;
    }

    public static String highestUnlockedTitle(int totalKills) {
        String current = "";
        for (TitleSpec t : activeTitles) {
            if (totalKills >= t.requiredKills()) {
                current = t.title();
            }
        }
        return current;
    }

    public static TitleSpec nextTitle(int totalKills) {
        for (TitleSpec t : activeTitles) {
            if (totalKills < t.requiredKills()) {
                return t;
            }
        }
        return null;
    }

    public static MilestoneSpec nextUnreachedMilestone(int totalKills) {
        for (MilestoneSpec m : activeMilestones) {
            if (totalKills < m.requiredKills()) {
                return m;
            }
        }
        return null;
    }

    public static void applyConfiguration(List<MilestoneSpec> customMilestones,
                                          List<TitleSpec> customTitles,
                                          Set<String> customEntityIds,
                                          boolean legendGlobalAnnouncement,
                                          boolean milestoneUnlockMessages) {
        if (customMilestones != null && customMilestones.size() == TOTAL_MILESTONES) {
            activeMilestones = List.copyOf(customMilestones);
        } else {
            activeMilestones = DEFAULT_MILESTONES;
        }
        if (customTitles != null && !customTitles.isEmpty()) {
            activeTitles = List.copyOf(customTitles);
        } else {
            activeTitles = DEFAULT_TITLES;
        }
        if (customEntityIds != null && !customEntityIds.isEmpty()) {
            Set<String> cleaned = new LinkedHashSet<>();
            for (String id : customEntityIds) {
                if (id != null && !id.isBlank()) {
                    cleaned.add(id.trim());
                }
            }
            countedEntityIds = cleaned.isEmpty()
                    ? Set.of(DEFAULT_ZOMBIE_ENTITY_ID)
                    : Collections.unmodifiableSet(cleaned);
        } else {
            countedEntityIds = Set.of(DEFAULT_ZOMBIE_ENTITY_ID);
        }
        announceLegendGlobally = legendGlobalAnnouncement;
        notifyMilestoneUnlock = milestoneUnlockMessages;
    }

    private static List<MilestoneSpec> buildDefaultMilestones() {
        List<MilestoneSpec> list = new ArrayList<>(TOTAL_MILESTONES);

        list.add(ms(1, "Kit de Caçador",
                "Dar comida, iluminação e uma arma inicial útil para continuar enfrentando as hordas.",
                item("minecraft:cooked_beef", 32),
                item("minecraft:golden_apple", 16),
                enchanted("minecraft:iron_sword", 1, "minecraft:iron_sword", ench("minecraft:sharpness", 2)),
                item("minecraft:torch", 32)));

        list.add(ms(2, "Equipamento de Combate",
                "Aumentar a sobrevivência e oferecer materiais para reparos e fabricação.",
                item("minecraft:iron_helmet", 1),
                item("minecraft:iron_chestplate", 1),
                item("minecraft:iron_leggings", 1),
                item("minecraft:iron_boots", 1),
                item("minecraft:shield", 1),
                item("minecraft:iron_ingot", 16),
                item("minecraft:golden_apple", 4)));

        list.add(ms(3, "Bolsa de Recursos Raros",
                "Fornecer recursos de valor para fabricação, trocas e progressão.",
                item("minecraft:diamond", 8),
                item("minecraft:gold_ingot", 16),
                item("minecraft:emerald", 16),
                item("minecraft:golden_apple", 8)));

        list.add(ms(4, "Arsenal do Soldado",
                "Introduzir armas mais fortes e uma opção de combate à distância.",
                enchanted("minecraft:diamond_sword", 1, "minecraft:diamond_sword", ench("minecraft:sharpness", 2)),
                enchanted("minecraft:bow", 1, "minecraft:bow", ench("minecraft:power", 2)),
                item("minecraft:arrow", 64),
                item("minecraft:ender_pearl", 16)));

        list.add(ms(5, "Armadura de Elite",
                "Marcar a primeira grande conquista defensiva do jogador.",
                enchanted("minecraft:diamond_helmet", 1, "minecraft:diamond_helmet", ench("minecraft:protection", 1)),
                enchanted("minecraft:diamond_chestplate", 1, "minecraft:diamond_chestplate", ench("minecraft:protection", 1)),
                enchanted("minecraft:diamond_leggings", 1, "minecraft:diamond_leggings", ench("minecraft:protection", 1)),
                enchanted("minecraft:diamond_boots", 1, "minecraft:diamond_boots", ench("minecraft:protection", 1)),
                item("minecraft:diamond", 16)));

        list.add(ms(6, "Suprimentos de Campanha",
                "Garantir suprimentos para novas expedições.",
                item("minecraft:golden_apple", 32),
                item("minecraft:cooked_beef", 32)));

        list.add(ms(7, "Ferramenta de Minerador Veterano",
                "Acelerar a coleta de recursos.",
                enchanted("minecraft:diamond_pickaxe", 1, "minecraft:diamond_pickaxe",
                        ench("minecraft:efficiency", 3), ench("minecraft:fortune", 2))));

        list.add(ms(8, "Cofre de Recursos",
                "Apoiar a fabricação de equipamentos e trocas.",
                item("minecraft:diamond", 24),
                item("minecraft:emerald", 16)));

        list.add(ms(9, "Arqueiro de Elite",
                "Melhorar o combate à distância.",
                enchanted("minecraft:bow", 1, "minecraft:bow",
                        ench("minecraft:power", 3), ench("minecraft:flame", 1)),
                item("minecraft:arrow", 64)));

        list.add(ms(10, "Veterano da Última Linha",
                "Oferecer um conjunto de combate sólido para a próxima fase.",
                enchanted("minecraft:diamond_helmet", 1, "minecraft:diamond_helmet", ench("minecraft:protection", 2)),
                enchanted("minecraft:diamond_chestplate", 1, "minecraft:diamond_chestplate", ench("minecraft:protection", 2)),
                enchanted("minecraft:diamond_leggings", 1, "minecraft:diamond_leggings", ench("minecraft:protection", 2)),
                enchanted("minecraft:diamond_boots", 1, "minecraft:diamond_boots", ench("minecraft:protection", 2)),
                enchanted("minecraft:diamond_sword", 1, "minecraft:diamond_sword", ench("minecraft:sharpness", 3))));

        list.add(ms(11, "Sobrevivente Implacável",
                "Oferecer uma chance extra de sobrevivência em situações extremas.",
                itemWithFallback("minecraft:totem_of_undying", 2, "minecraft:golden_apple")));

        list.add(ms(12, "Reserva de Guerra",
                "Fornecer recursos e cura para combates prolongados.",
                item("minecraft:diamond", 32),
                item("minecraft:golden_apple", 16)));

        list.add(ms(13, "Besta do Combatente",
                "Oferecer uma arma de longo alcance diferente do arco.",
                enchanted("minecraft:crossbow", 1, "minecraft:bow",
                        ench("minecraft:piercing", 3), ench("minecraft:quick_charge", 2))));

        list.add(ms(14, "Bolsa do Explorador",
                "Facilitar deslocamentos e recuperação entre combates.",
                item("minecraft:ender_pearl", 32),
                item("minecraft:golden_apple", 16)));

        list.add(ms(15, "Lâmina Incendiária",
                "Oferecer dano elevado e um efeito ofensivo especial.",
                enchanted("minecraft:diamond_sword", 1, "minecraft:diamond_sword",
                        ench("minecraft:sharpness", 4), ench("minecraft:fire_aspect", 2))));

        list.add(ms(16, "Equipamento de Mobilidade",
                "Liberar mobilidade avançada.",
                itemWithFallback("minecraft:elytra", 1, "minecraft:ender_pearl"),
                item("minecraft:firework_rocket", 64)));

        list.add(ms(17, "Ferramentas do Veterano",
                "Reduzir o tempo de coleta de materiais.",
                enchanted("minecraft:diamond_pickaxe", 1, "minecraft:diamond_pickaxe",
                        ench("minecraft:efficiency", 4), ench("minecraft:fortune", 3)),
                enchanted("minecraft:diamond_axe", 1, "minecraft:diamond_axe", ench("minecraft:efficiency", 3)),
                enchanted("minecraft:diamond_shovel", 1, "minecraft:diamond_shovel", ench("minecraft:efficiency", 3))));

        list.add(ms(18, "Conhecimento de Encantamento",
                "Permitir melhorias escolhidas pelo jogador.",
                enchanted("minecraft:enchanted_book", 1, "minecraft:book", ench("minecraft:mending", 1)),
                enchanted("minecraft:enchanted_book", 1, "minecraft:book", ench("minecraft:protection", 4))));

        list.add(ms(19, "Último Recurso",
                "Recompensar o jogador com itens de emergência raros.",
                itemWithFallback("minecraft:enchanted_golden_apple", 1, "minecraft:golden_apple"),
                itemWithFallback("minecraft:totem_of_undying", 1, "minecraft:golden_apple")));

        list.add(ms(20, "Campeão da Primeira Campanha",
                "Concluir a primeira etapa com um conjunto de alto nível.",
                enchanted("minecraft:diamond_helmet", 1, "minecraft:diamond_helmet", ench("minecraft:protection", 4)),
                enchanted("minecraft:diamond_chestplate", 1, "minecraft:diamond_chestplate", ench("minecraft:protection", 4)),
                enchanted("minecraft:diamond_leggings", 1, "minecraft:diamond_leggings", ench("minecraft:protection", 4)),
                enchanted("minecraft:diamond_boots", 1, "minecraft:diamond_boots", ench("minecraft:protection", 4)),
                enchanted("minecraft:diamond_sword", 1, "minecraft:diamond_sword", ench("minecraft:sharpness", 5))));

        list.add(ms(21, "Arsenal de Diamante",
                "Financiar novas melhorias de equipamentos.",
                item("minecraft:diamond", 32),
                item("minecraft:gold_ingot", 16),
                item("minecraft:emerald", 16)));

        list.add(ms(22, "Espadachim Veterano",
                "Melhorar o combate corpo a corpo e fornecer uma arma reserva.",
                enchanted("minecraft:diamond_sword", 1, "minecraft:diamond_sword", ench("minecraft:sharpness", 4)),
                item("minecraft:diamond_sword", 1)));

        list.add(ms(23, "Mestre do Arco",
                "Aumentar a eficiência contra inimigos perigosos à distância.",
                enchanted("minecraft:bow", 1, "minecraft:bow",
                        ench("minecraft:power", 4), ench("minecraft:flame", 1)),
                item("minecraft:arrow", 64)));

        list.add(ms(24, "Minerador de Elite",
                "Melhorar a extração de recursos raros.",
                enchanted("minecraft:diamond_pickaxe", 1, "minecraft:diamond_pickaxe",
                        ench("minecraft:efficiency", 4), ench("minecraft:fortune", 3)),
                item("minecraft:diamond", 16)));

        list.add(ms(25, "Guardião de Diamante",
                "Reforçar a defesa contra grupos de inimigos.",
                enchanted("minecraft:diamond_helmet", 1, "minecraft:diamond_helmet", ench("minecraft:protection", 3)),
                enchanted("minecraft:diamond_chestplate", 1, "minecraft:diamond_chestplate", ench("minecraft:protection", 3)),
                enchanted("minecraft:diamond_leggings", 1, "minecraft:diamond_leggings", ench("minecraft:protection", 3)),
                enchanted("minecraft:diamond_boots", 1, "minecraft:diamond_boots", ench("minecraft:protection", 3)),
                item("minecraft:shield", 1)));

        list.add(ms(26, "Reserva de Suprimentos",
                "Preparar o jogador para expedições longas.",
                item("minecraft:golden_apple", 32),
                item("minecraft:cooked_beef", 64)));

        list.add(ms(27, "Combatente do Tridente",
                "Adicionar uma arma versátil.",
                enchanted("minecraft:trident", 1, "minecraft:diamond_sword", ench("minecraft:loyalty", 2))));

        list.add(ms(28, "Bolsa de Teleporte",
                "Facilitar exploração e deslocamento.",
                item("minecraft:ender_pearl", 32),
                item("minecraft:compass", 1),
                item("minecraft:clock", 1)));

        list.add(ms(29, "Mestre da Besta",
                "Entregar uma arma de longo alcance avançada.",
                enchanted("minecraft:crossbow", 1, "minecraft:bow",
                        ench("minecraft:piercing", 4), ench("minecraft:quick_charge", 3)),
                item("minecraft:arrow", 64)));

        list.add(ms(30, "Guardião Veterano",
                "Fornecer proteção consistente para a etapa avançada.",
                enchanted("minecraft:diamond_helmet", 1, "minecraft:diamond_helmet",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3)),
                enchanted("minecraft:diamond_chestplate", 1, "minecraft:diamond_chestplate",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3)),
                enchanted("minecraft:diamond_leggings", 1, "minecraft:diamond_leggings",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3)),
                enchanted("minecraft:diamond_boots", 1, "minecraft:diamond_boots",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3))));

        list.add(ms(31, "Cofre do Caçador",
                "Fornecer recursos para fabricação e comércio.",
                item("minecraft:diamond", 32),
                item("minecraft:emerald", 32)));

        list.add(ms(32, "Lâmina de Guerra",
                "Aumentar dano e recompensas de criaturas, conforme a mecânica normal do jogo.",
                enchanted("minecraft:diamond_sword", 1, "minecraft:diamond_sword",
                        ench("minecraft:sharpness", 4), ench("minecraft:looting", 3))));

        list.add(ms(33, "Arqueiro de Guerra",
                "Ampliar dano e controle de distância.",
                enchanted("minecraft:bow", 1, "minecraft:bow",
                        ench("minecraft:power", 4), ench("minecraft:flame", 1), ench("minecraft:punch", 2)),
                item("minecraft:arrow", 64)));

        list.add(ms(34, "Ferramenta de Fortuna",
                "Ferramenta de mineração de alto desempenho.",
                enchanted("minecraft:diamond_pickaxe", 1, "minecraft:diamond_pickaxe",
                        ench("minecraft:efficiency", 5), ench("minecraft:fortune", 3))));

        list.add(ms(35, "Arsenal Completo",
                "Celebrar metade do caminho até 1.000 abates com um arsenal completo.",
                enchanted("minecraft:diamond_sword", 1, "minecraft:diamond_sword", ench("minecraft:sharpness", 5)),
                enchanted("minecraft:bow", 1, "minecraft:bow", ench("minecraft:power", 4)),
                enchanted("minecraft:diamond_pickaxe", 1, "minecraft:diamond_pickaxe",
                        ench("minecraft:efficiency", 3), ench("minecraft:unbreaking", 2)),
                enchanted("minecraft:diamond_axe", 1, "minecraft:diamond_axe",
                        ench("minecraft:efficiency", 3), ench("minecraft:unbreaking", 2)),
                enchanted("minecraft:diamond_shovel", 1, "minecraft:diamond_shovel",
                        ench("minecraft:efficiency", 3), ench("minecraft:unbreaking", 2)),
                enchanted("minecraft:diamond_hoe", 1, "minecraft:diamond_hoe",
                        ench("minecraft:efficiency", 3), ench("minecraft:unbreaking", 2))));

        list.add(ms(36, "Reserva de Ouro",
                "Oferecer cura, comida e recursos valiosos.",
                item("minecraft:golden_apple", 32),
                item("minecraft:gold_ingot", 32),
                item("minecraft:cooked_beef", 32)));

        list.add(ms(37, "Armadura de Diamante Suprema",
                "Fornecer uma defesa confiável antes da etapa de netherita.",
                enchanted("minecraft:diamond_helmet", 1, "minecraft:diamond_helmet",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3)),
                enchanted("minecraft:diamond_chestplate", 1, "minecraft:diamond_chestplate",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3)),
                enchanted("minecraft:diamond_leggings", 1, "minecraft:diamond_leggings",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3)),
                enchanted("minecraft:diamond_boots", 1, "minecraft:diamond_boots",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3))));

        list.add(ms(38, "Explorador dos Céus",
                "Recompensar exploração e mobilidade.",
                itemWithFallback("minecraft:elytra", 1, "minecraft:ender_pearl"),
                item("minecraft:firework_rocket", 64)));

        list.add(ms(39, "Mestre dos Encantamentos",
                "Permitir aprimorar várias peças de equipamento.",
                enchanted("minecraft:enchanted_book", 2, "minecraft:book", ench("minecraft:mending", 1)),
                enchanted("minecraft:enchanted_book", 2, "minecraft:book", ench("minecraft:protection", 4))));

        list.add(ms(40, "Primeira Lâmina de Netherita",
                "Introduzir a netherita como marco de alto prestígio.",
                item("minecraft:netherite_sword", 1),
                item("minecraft:gold_ingot", 16)));

        list.add(ms(41, "Fragmentos de Guerra",
                "Contribuir para melhorias de netherita sem entregar um conjunto inteiro de uma vez.",
                item("minecraft:netherite_ingot", 1),
                item("minecraft:diamond", 16)));

        list.add(ms(42, "Arco do Executor",
                "Oferecer um arco de nível máximo.",
                enchanted("minecraft:bow", 1, "minecraft:bow",
                        ench("minecraft:power", 5), ench("minecraft:flame", 1), ench("minecraft:punch", 2)),
                item("minecraft:arrow", 64)));

        list.add(ms(43, "Armadura Reforçada",
                "Oferecer uma alternativa defensiva forte.",
                enchanted("minecraft:diamond_helmet", 1, "minecraft:diamond_helmet",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3), ench("minecraft:thorns", 2)),
                enchanted("minecraft:diamond_chestplate", 1, "minecraft:diamond_chestplate",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3), ench("minecraft:thorns", 2)),
                enchanted("minecraft:diamond_leggings", 1, "minecraft:diamond_leggings",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3), ench("minecraft:thorns", 2)),
                enchanted("minecraft:diamond_boots", 1, "minecraft:diamond_boots",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3), ench("minecraft:thorns", 2))));

        list.add(ms(44, "Núcleo de Energia",
                "Conceder um item de construção avançado.",
                itemWithFallback("minecraft:beacon", 1, "minecraft:diamond_block")));

        list.add(ms(45, "Armadura do Comandante",
                "Marcar a entrada na fase de equipamento máximo.",
                item("minecraft:netherite_helmet", 1),
                item("minecraft:netherite_chestplate", 1),
                item("minecraft:netherite_leggings", 1),
                item("minecraft:netherite_boots", 1)));

        list.add(ms(46, "Espada do Executor",
                "Oferecer uma arma de alto nível.",
                enchanted("minecraft:netherite_sword", 1, "minecraft:netherite_sword", ench("minecraft:sharpness", 5)),
                enchanted("minecraft:enchanted_book", 1, "minecraft:book", ench("minecraft:looting", 3))));

        list.add(ms(47, "Picareta do Comandante",
                "Fornecer uma ferramenta de mineração avançada.",
                enchanted("minecraft:netherite_pickaxe", 1, "minecraft:netherite_pickaxe",
                        ench("minecraft:efficiency", 5), ench("minecraft:fortune", 3))));

        list.add(ms(48, "Última Chance",
                "Reforçar a sobrevivência em combates extremos.",
                itemWithFallback("minecraft:totem_of_undying", 1, "minecraft:golden_apple"),
                item("minecraft:golden_apple", 16)));

        list.add(ms(49, "Tesouro Proibido",
                "Conceder itens muito raros antes da etapa final.",
                itemWithFallback("minecraft:enchanted_golden_apple", 2, "minecraft:golden_apple"),
                item("minecraft:diamond", 16)));

        list.add(ms(50, "Conjunto do Executor",
                "Fornecer um conjunto de combate de elite.",
                enchanted("minecraft:netherite_helmet", 1, "minecraft:netherite_helmet",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3)),
                enchanted("minecraft:netherite_chestplate", 1, "minecraft:netherite_chestplate",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3)),
                enchanted("minecraft:netherite_leggings", 1, "minecraft:netherite_leggings",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3)),
                enchanted("minecraft:netherite_boots", 1, "minecraft:netherite_boots",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3)),
                enchanted("minecraft:netherite_sword", 1, "minecraft:netherite_sword", ench("minecraft:sharpness", 5))));

        list.add(ms(51, "Guardião Imortal",
                "Aumentar as chances de sobrevivência em situações extremas.",
                itemWithFallback("minecraft:totem_of_undying", 2, "minecraft:golden_apple"),
                item("minecraft:golden_apple", 32)));

        list.add(ms(52, "Lâmina Incandescente",
                "Arma corpo a corpo de grande poder.",
                enchanted("minecraft:netherite_sword", 1, "minecraft:netherite_sword",
                        ench("minecraft:sharpness", 5), ench("minecraft:fire_aspect", 2))));

        list.add(ms(53, "Arco da Última Linha",
                "Arma de longo alcance de alto nível.",
                enchanted("minecraft:bow", 1, "minecraft:bow",
                        ench("minecraft:power", 5), ench("minecraft:flame", 1),
                        ench("minecraft:punch", 2), ench("minecraft:infinity", 1))));

        list.add(ms(54, "Muralha de Netherita",
                "Reforçar defesa para o trecho final.",
                enchanted("minecraft:netherite_helmet", 1, "minecraft:netherite_helmet",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3)),
                enchanted("minecraft:netherite_chestplate", 1, "minecraft:netherite_chestplate",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3)),
                enchanted("minecraft:netherite_leggings", 1, "minecraft:netherite_leggings",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3)),
                enchanted("minecraft:netherite_boots", 1, "minecraft:netherite_boots",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3)),
                item("minecraft:shield", 1)));

        list.add(ms(55, "Estrela do Comandante",
                "Entregar um item raro e valioso como reconhecimento.",
                itemWithFallback("minecraft:nether_star", 1, "minecraft:diamond"),
                item("minecraft:diamond", 32)));

        list.add(ms(56, "Ferramenta Perfeita",
                "Entregar uma ferramenta de mineração de nível máximo.",
                enchanted("minecraft:netherite_pickaxe", 1, "minecraft:netherite_pickaxe",
                        ench("minecraft:efficiency", 5), ench("minecraft:fortune", 3), ench("minecraft:mending", 1))));

        list.add(ms(57, "Reserva Lendária",
                "Fornecer consumíveis raros para a fase final.",
                itemWithFallback("minecraft:enchanted_golden_apple", 4, "minecraft:golden_apple"),
                itemWithFallback("minecraft:totem_of_undying", 2, "minecraft:golden_apple")));

        list.add(ms(58, "Tridente da Tempestade",
                "Oferecer uma arma especial de alto nível.",
                enchanted("minecraft:trident", 1, "minecraft:netherite_sword",
                        ench("minecraft:loyalty", 3), ench("minecraft:channeling", 1))));

        list.add(ms(59, "Arsenal Lendário",
                "Preparar o jogador para o prêmio final.",
                enchanted("minecraft:netherite_helmet", 1, "minecraft:netherite_helmet",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3)),
                enchanted("minecraft:netherite_chestplate", 1, "minecraft:netherite_chestplate",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3)),
                enchanted("minecraft:netherite_leggings", 1, "minecraft:netherite_leggings",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3)),
                enchanted("minecraft:netherite_boots", 1, "minecraft:netherite_boots",
                        ench("minecraft:protection", 4), ench("minecraft:unbreaking", 3)),
                enchanted("minecraft:netherite_sword", 1, "minecraft:netherite_sword", ench("minecraft:sharpness", 5)),
                enchanted("minecraft:bow", 1, "minecraft:bow", ench("minecraft:power", 5))));

        list.add(msWithTitle(60, "Baú Lendário da Última Linha",
                "Marcar a conclusão da campanha de recompensas do comandante.",
                "Lenda da Última Linha",
                enchanted("minecraft:netherite_sword", 1, "minecraft:netherite_sword",
                        ench("minecraft:sharpness", 5), ench("minecraft:looting", 3), ench("minecraft:fire_aspect", 2)),
                itemWithFallback("minecraft:totem_of_undying", 1, "minecraft:golden_apple"),
                itemWithFallback("minecraft:enchanted_golden_apple", 4, "minecraft:golden_apple"),
                item("minecraft:diamond", 32)));

        return List.copyOf(list);
    }

    private static MilestoneSpec ms(int number, String name, String objective, RewardItemSpec... items) {
        return msWithTitle(number, name, objective, "", items);
    }

    private static MilestoneSpec msWithTitle(int number, String name, String objective,
                                             String exclusiveTitle, RewardItemSpec... items) {
        List<RewardItemSpec> itemList = List.of(items);
        String icon = itemList.isEmpty() ? "minecraft:iron_sword" : itemList.getFirst().itemId();
        return new MilestoneSpec(
                number,
                requiredKillsForMilestone(number),
                name,
                objective,
                coinsForMilestone(number),
                icon,
                itemList,
                exclusiveTitle
        );
    }

    private static RewardItemSpec item(String itemId, int count) {
        return new RewardItemSpec(itemId, count, List.of(), "minecraft:diamond");
    }

    private static RewardItemSpec itemWithFallback(String itemId, int count, String fallbackItemId) {
        return new RewardItemSpec(itemId, count, List.of(), fallbackItemId);
    }

    private static RewardItemSpec enchanted(String itemId, int count, String fallbackItemId, EnchantSpec... enchants) {
        return new RewardItemSpec(itemId, count, List.of(enchants), fallbackItemId);
    }

    private static EnchantSpec ench(String enchantmentId, int level) {
        return new EnchantSpec(enchantmentId, level);
    }
}
