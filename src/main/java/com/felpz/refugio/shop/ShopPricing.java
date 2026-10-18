package com.felpz.refugio.shop;

import java.util.Map;
import java.util.Set;

/** Tabela econômica central: compra e venda são independentes e a venda é uma allowlist. */
public final class ShopPricing {
    private static final Map<String, Integer> BUY = Map.ofEntries(
            Map.entry("minecraft:bread", 4), Map.entry("minecraft:cooked_beef", 5),
            Map.entry("minecraft:cooked_porkchop", 5), Map.entry("minecraft:cooked_chicken", 4),
            Map.entry("minecraft:cooked_mutton", 5), Map.entry("minecraft:cooked_cod", 4),
            Map.entry("minecraft:cooked_salmon", 5), Map.entry("minecraft:baked_potato", 3),
            Map.entry("minecraft:golden_carrot", 8), Map.entry("minecraft:golden_apple", 100),
            Map.entry("minecraft:enchanted_golden_apple", 1500), Map.entry("minecraft:totem_of_undying", 1200),
            Map.entry("minecraft:wheat", 1), Map.entry("minecraft:carrot", 2), Map.entry("minecraft:potato", 2),
            Map.entry("minecraft:beetroot", 1), Map.entry("minecraft:beetroot_seeds", 1),
            Map.entry("minecraft:wheat_seeds", 1), Map.entry("minecraft:pumpkin_seeds", 1),
            Map.entry("minecraft:melon_seeds", 1), Map.entry("minecraft:sugar_cane", 2),
            Map.entry("minecraft:cocoa_beans", 2), Map.entry("minecraft:pumpkin", 3),
            Map.entry("minecraft:melon", 4), Map.entry("minecraft:bamboo", 1),
            Map.entry("minecraft:cactus", 2), Map.entry("minecraft:nether_wart", 4),
            Map.entry("minecraft:torch", 1), Map.entry("minecraft:lantern", 4),
            Map.entry("minecraft:bucket", 12), Map.entry("minecraft:water_bucket", 15),
            Map.entry("minecraft:milk_bucket", 16), Map.entry("minecraft:shield", 40),
            Map.entry("minecraft:white_bed", 18), Map.entry("minecraft:campfire", 12),
            Map.entry("minecraft:fishing_rod", 30), Map.entry("minecraft:honey_bottle", 4),
            Map.entry("minecraft:coal", 5), Map.entry("minecraft:charcoal", 4),
            Map.entry("minecraft:dirt", 1), Map.entry("minecraft:coarse_dirt", 2),
            Map.entry("minecraft:rooted_dirt", 2), Map.entry("minecraft:podzol", 2),
            Map.entry("minecraft:mycelium", 3), Map.entry("minecraft:grass_block", 2),
            Map.entry("minecraft:cobblestone", 1), Map.entry("minecraft:stone", 1),
            Map.entry("minecraft:gravel", 1), Map.entry("minecraft:sand", 1),
            Map.entry("minecraft:red_sand", 2), Map.entry("minecraft:clay", 3),
            Map.entry("minecraft:glass", 3), Map.entry("minecraft:glass_pane", 2),
            Map.entry("minecraft:bricks", 3), Map.entry("minecraft:obsidian", 12),
            Map.entry("minecraft:chest", 12), Map.entry("minecraft:barrel", 12),
            Map.entry("minecraft:crafting_table", 5), Map.entry("minecraft:furnace", 10),
            Map.entry("minecraft:smoker", 12), Map.entry("minecraft:blast_furnace", 15),
            Map.entry("minecraft:anvil", 20), Map.entry("minecraft:enchanting_table", 20),
            Map.entry("minecraft:ender_chest", 150), Map.entry("minecraft:hopper", 30),
            Map.entry("minecraft:piston", 18), Map.entry("minecraft:sticky_piston", 25),
            Map.entry("minecraft:observer", 25), Map.entry("minecraft:repeater", 12),
            Map.entry("minecraft:comparator", 15), Map.entry("minecraft:dispenser", 12),
            Map.entry("minecraft:dropper", 12), Map.entry("minecraft:redstone", 5),
            Map.entry("minecraft:redstone_block", 50), Map.entry("minecraft:iron_block", 100),
            Map.entry("minecraft:raw_iron_block", 80), Map.entry("minecraft:gold_block", 210),
            Map.entry("minecraft:raw_gold_block", 162), Map.entry("minecraft:copper_block", 50),
            Map.entry("minecraft:raw_copper_block", 27), Map.entry("minecraft:diamond_block", 1100),
            Map.entry("minecraft:emerald_block", 320), Map.entry("minecraft:netherite_block", 18000),
            Map.entry("minecraft:coal_block", 50), Map.entry("minecraft:lapis_block", 50),
            Map.entry("minecraft:amethyst_block", 40), Map.entry("minecraft:quartz_block", 30),
            Map.entry("minecraft:hay_block", 12), Map.entry("minecraft:shulker_box", 400),
            Map.entry("minecraft:shulker_shell", 80), Map.entry("minecraft:diamond", 120),
            Map.entry("minecraft:emerald", 35), Map.entry("minecraft:raw_iron", 8),
            Map.entry("minecraft:iron_ingot", 10), Map.entry("minecraft:raw_copper", 3),
            Map.entry("minecraft:copper_ingot", 5), Map.entry("minecraft:raw_gold", 18),
            Map.entry("minecraft:gold_ingot", 22), Map.entry("minecraft:netherite_scrap", 420),
            Map.entry("minecraft:netherite_ingot", 1750), Map.entry("minecraft:ancient_debris", 280),
            Map.entry("minecraft:nether_star", 2500), Map.entry("minecraft:beacon", 5000),
            Map.entry("minecraft:elytra", 5000), Map.entry("minecraft:ender_pearl", 12),
            Map.entry("minecraft:ender_eye", 30), Map.entry("minecraft:blaze_rod", 45),
            Map.entry("minecraft:blaze_powder", 12), Map.entry("minecraft:ghast_tear", 60),
            Map.entry("minecraft:firework_rocket", 3), Map.entry("minecraft:saddle", 100),
            Map.entry("minecraft:name_tag", 70), Map.entry("minecraft:lead", 8),
            Map.entry("minecraft:dragon_breath", 60), Map.entry("minecraft:netherite_upgrade_smithing_template", 1500),
            Map.entry("minecraft:experience_bottle", 20), Map.entry("minecraft:conduit", 600),
            Map.entry("minecraft:nautilus_shell", 25), Map.entry("minecraft:heart_of_the_sea", 350),
            Map.entry("minecraft:recovery_compass", 350), Map.entry("minecraft:compass", 25),
            Map.entry("minecraft:spyglass", 60), Map.entry("minecraft:quartz", 6),
            Map.entry("minecraft:amethyst_shard", 8), Map.entry("minecraft:lapis_lazuli", 5),
            Map.entry("minecraft:arrow", 2), Map.entry("minecraft:axolotl_bucket", 500),
            Map.entry("minecraft:tadpole_bucket", 30), Map.entry("minecraft:potion", 45),
            Map.entry("minecraft:splash_potion", 55), Map.entry("minecraft:lingering_potion", 80),
            Map.entry("minecraft:carrot_on_a_stick", 25), Map.entry("minecraft:warped_fungus_on_a_stick", 25),
            Map.entry("minecraft:goat_horn", 35), Map.entry("minecraft:brush", 35),
            Map.entry("minecraft:stonecutter", 10),
            Map.entry("minecraft:loom", 10), Map.entry("minecraft:cartography_table", 12),
            Map.entry("minecraft:fletching_table", 12), Map.entry("minecraft:smithing_table", 15),
            Map.entry("minecraft:grindstone", 12), Map.entry("minecraft:composter", 8),
            Map.entry("minecraft:trapped_chest", 15), Map.entry("minecraft:bookshelf", 8),
            Map.entry("minecraft:chiseled_bookshelf", 12), Map.entry("minecraft:honey_block", 20),
            Map.entry("minecraft:dried_kelp_block", 20), Map.entry("minecraft:redstone_torch", 5),
            Map.entry("minecraft:lever", 5), Map.entry("minecraft:tripwire_hook", 8),
            Map.entry("minecraft:white_shulker_box", 400), Map.entry("minecraft:dried_kelp", 2)
    );

    private static final Map<String, Integer> SELL = Map.ofEntries(
            Map.entry("minecraft:wheat", 1), Map.entry("minecraft:wheat_seeds", 1),
            Map.entry("minecraft:beetroot", 1), Map.entry("minecraft:beetroot_seeds", 1),
            Map.entry("minecraft:carrot", 1), Map.entry("minecraft:potato", 1),
            Map.entry("minecraft:poisonous_potato", 1), Map.entry("minecraft:sugar_cane", 1),
            Map.entry("minecraft:cocoa_beans", 1), Map.entry("minecraft:pumpkin", 1),
            Map.entry("minecraft:melon", 1), Map.entry("minecraft:pumpkin_seeds", 1),
            Map.entry("minecraft:melon_seeds", 1), Map.entry("minecraft:bamboo", 1),
            Map.entry("minecraft:cactus", 1), Map.entry("minecraft:sweet_berries", 1),
            Map.entry("minecraft:glow_berries", 1), Map.entry("minecraft:nether_wart", 1),
            Map.entry("minecraft:apple", 1), Map.entry("minecraft:melon_slice", 1),
            Map.entry("minecraft:dried_kelp", 1), Map.entry("minecraft:egg", 1),
            Map.entry("minecraft:red_mushroom", 1),
            Map.entry("minecraft:brown_mushroom", 1), Map.entry("minecraft:honey_bottle", 1),
            Map.entry("minecraft:honeycomb", 1), Map.entry("minecraft:hay_block", 4),
            Map.entry("minecraft:coal", 2), Map.entry("minecraft:charcoal", 1),
            Map.entry("minecraft:raw_iron", 4), Map.entry("minecraft:iron_ingot", 5),
            Map.entry("minecraft:raw_copper", 2), Map.entry("minecraft:copper_ingot", 3),
            Map.entry("minecraft:raw_gold", 6), Map.entry("minecraft:gold_ingot", 8),
            Map.entry("minecraft:redstone", 2), Map.entry("minecraft:lapis_lazuli", 2),
            Map.entry("minecraft:quartz", 2), Map.entry("minecraft:amethyst_shard", 2),
            Map.entry("minecraft:emerald", 10), Map.entry("minecraft:diamond", 30),
            Map.entry("minecraft:obsidian", 1), Map.entry("minecraft:ancient_debris", 50),
            Map.entry("minecraft:netherite_scrap", 80), Map.entry("minecraft:netherite_ingot", 250),
            Map.entry("minecraft:ender_pearl", 2), Map.entry("minecraft:blaze_rod", 8),
            Map.entry("minecraft:ghast_tear", 10), Map.entry("minecraft:shulker_shell", 12),
            Map.entry("minecraft:bone", 1), Map.entry("minecraft:arrow", 1),
            Map.entry("minecraft:string", 1), Map.entry("minecraft:rotten_flesh", 1),
            Map.entry("minecraft:spider_eye", 1), Map.entry("minecraft:gunpowder", 1),
            Map.entry("minecraft:feather", 1), Map.entry("minecraft:leather", 1),
            Map.entry("minecraft:slime_ball", 1), Map.entry("minecraft:phantom_membrane", 2),
            Map.entry("minecraft:magma_cream", 1), Map.entry("minecraft:prismarine_shard", 1),
            Map.entry("minecraft:prismarine_crystals", 1), Map.entry("minecraft:cod", 1),
            Map.entry("minecraft:salmon", 1), Map.entry("minecraft:tropical_fish", 1),
            Map.entry("minecraft:pufferfish", 1), Map.entry("minecraft:cooked_cod", 1),
            Map.entry("minecraft:cooked_salmon", 1), Map.entry("minecraft:cooked_beef", 1),
            Map.entry("minecraft:cooked_porkchop", 1), Map.entry("minecraft:cooked_chicken", 1),
            Map.entry("minecraft:cooked_mutton", 1), Map.entry("minecraft:cooked_rabbit", 1),
            Map.entry("minecraft:beef", 1), Map.entry("minecraft:porkchop", 1),
            Map.entry("minecraft:chicken", 1), Map.entry("minecraft:mutton", 1),
            Map.entry("minecraft:rabbit", 1), Map.entry("minecraft:bread", 1),
            Map.entry("minecraft:cookie", 1), Map.entry("minecraft:pumpkin_pie", 1)
    );

    private static final Set<String> EXACT_BUILDING = Set.of(
            "dirt", "coarse_dirt", "rooted_dirt", "podzol", "mycelium", "grass_block", "mud", "packed_mud",
            "cobblestone", "mossy_cobblestone", "stone", "smooth_stone", "stone_bricks", "mossy_stone_bricks",
            "cobbled_deepslate", "polished_deepslate", "deepslate_bricks", "deepslate_tiles", "gravel", "sand",
            "red_sand", "sandstone", "red_sandstone", "clay", "terracotta", "glass", "glass_pane", "bricks",
            "brick_block", "bookshelf", "chiseled_bookshelf", "crafting_table", "furnace", "smoker", "blast_furnace",
            "anvil", "chipped_anvil", "damaged_anvil", "enchanting_table", "ender_chest", "chest", "trapped_chest",
            "barrel", "lantern", "soul_lantern", "torch", "soul_torch", "ladder", "scaffolding", "rail",
            "powered_rail", "detector_rail", "activator_rail", "redstone", "redstone_block", "repeater", "comparator",
            "observer", "piston", "sticky_piston", "hopper", "dispenser", "dropper", "redstone_lamp", "redstone_torch",
            "redstone_wire", "white_wool", "black_wool", "red_wool", "blue_wool", "green_wool", "yellow_wool",
            "white_concrete", "white_concrete_powder", "white_terracotta", "obsidian", "crying_obsidian", "beacon",
            "nether_star", "iron_block", "raw_iron_block", "gold_block", "raw_gold_block", "copper_block",
            "raw_copper_block", "diamond_block", "emerald_block", "netherite_block", "coal_block", "lapis_block",
            "amethyst_block", "quartz_block", "hay_block", "shulker_box", "white_shulker_box", "conduit",
            "stonecutter", "loom", "cartography_table", "fletching_table", "smithing_table", "grindstone",
            "composter", "bee_nest", "beehive", "honey_block", "slime_block", "dried_kelp_block"
    );

    private static final Set<String> ADVENTURE_ITEMS = Set.of(
            "ender_pearl", "blaze_rod", "blaze_powder", "ender_eye", "firework_rocket", "elytra",
            "shulker_shell", "shulker_box", "saddle", "name_tag", "lead", "totem_of_undying",
            "dragon_breath", "netherite_upgrade_smithing_template", "enchanted_golden_apple", "experience_bottle",
            "nautilus_shell", "heart_of_the_sea", "recovery_compass", "compass", "spyglass", "diamond",
            "emerald", "raw_iron", "iron_ingot", "raw_copper", "copper_ingot", "raw_gold", "gold_ingot",
            "netherite_scrap", "netherite_ingot", "ancient_debris", "nether_star", "beacon", "ender_chest",
            "ghast_tear", "quartz", "amethyst_shard", "lapis_lazuli", "obsidian", "prismarine_shard",
            "prismarine_crystals", "trident", "netherite_block"
    );

    private static final Set<String> PACKAGE_ITEMS = Set.of(
            "dirt", "cobblestone", "stone", "sand", "gravel", "oak_log", "spruce_log", "birch_log",
            "jungle_log", "acacia_log", "dark_oak_log", "mangrove_log", "cherry_log", "pale_oak_log",
            "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks",
            "mangrove_planks", "cherry_planks", "glass", "white_wool", "white_concrete", "terracotta"
    );

    private static final Set<String> TECHNICAL = Set.of(
            "spawner", "trial_spawner", "ominous_trial_spawner", "vault", "command_block", "chain_command_block",
            "repeating_command_block", "command_block_minecart", "structure_block", "structure_void", "jigsaw",
            "test_block", "test_instance_block", "debug_stick", "knowledge_book", "barrier", "bedrock", "light",
            "allow", "deny", "border", "end_gateway", "end_portal", "nether_portal", "reinforced_deepslate"
    );

    private ShopPricing() { }

    public static int purchasePrice(String itemId, int category, String path, boolean blockItem) {
        Integer fixed = BUY.get(itemId);
        if (fixed != null) return fixed;
        if (category == 0 && (isSurvivalItem(path) || isCommonFood(path))) {
            if (path.endsWith("_seeds") || path.equals("wheat") || path.equals("beetroot") || path.endsWith("_sapling")) return 1;
            if (path.equals("carrot_on_a_stick") || path.equals("warped_fungus_on_a_stick")) return 25;
            if (path.equals("fishing_rod")) return 30;
            if (path.equals("goat_horn") || path.equals("brush")) return 35;
            if (path.equals("axolotl_bucket")) return 500;
            if (path.equals("tadpole_bucket")) return 30;
            if (path.equals("potion")) return 45;
            if (path.equals("splash_potion")) return 55;
            if (path.equals("lingering_potion")) return 80;
            if (path.endsWith("_bed")) return 18;
            if (path.endsWith("_log") || path.endsWith("_wood")) return 2;
            return 2;
        }
        if (category == 1 && blockItem && isAllowedBuilding(path)) return buildingPrice(path);
        if (category == 2 && isEquipment(path)) return equipmentPrice(path);
        if (category == 3 && path.endsWith("_shulker_box")) return 400;
        if (category == 3 && path.startsWith("music_disc_")) {
            return Set.of("music_disc_5", "music_disc_11", "music_disc_otherside", "music_disc_pigstep",
                    "music_disc_relic", "music_disc_creator", "music_disc_creator_music_box",
                    "music_disc_precipice", "music_disc_tears", "music_disc_lava_chicken").contains(path) ? 300 : 60;
        }
        if (category == 3 && ADVENTURE_ITEMS.contains(path)) return 15;
        return 0;
    }

    public static int salePrice(String itemId) {
        Integer fixed = SELL.get(itemId);
        if (fixed != null) return fixed;
        if (!itemId.startsWith("minecraft:")) return 0;
        String path = itemId.substring("minecraft:".length());
        if (isSellableBuilding(path) || isSellableFarmProduct(path) || isSellableFood(path)) return 1;
        return 0;
    }

    public static Map<String, Integer> explicitSellPrices() { return SELL; }

    public static boolean isAllowedBuilding(String path) {
        if (TECHNICAL.contains(path) || path.contains("spawn_egg") || path.contains("command_block")) return false;
        if (EXACT_BUILDING.contains(path)) return true;
        return path.endsWith("_planks") || path.endsWith("_log") || path.endsWith("_wood")
                || path.endsWith("_hyphae") || path.endsWith("_stairs") || path.endsWith("_slab")
                || path.endsWith("_wall") || path.endsWith("_fence") || path.endsWith("_fence_gate")
                || path.endsWith("_door") || path.endsWith("_trapdoor") || path.endsWith("_button")
                || path.endsWith("_pressure_plate") || path.endsWith("_sign") || path.endsWith("_hanging_sign")
                || path.endsWith("_wool") || path.endsWith("_carpet") || path.endsWith("_concrete")
                || path.endsWith("_concrete_powder") || path.endsWith("_terracotta") || path.endsWith("_glass")
                || path.endsWith("_glass_pane") || path.endsWith("_leaves") || path.endsWith("_sapling")
                || path.endsWith("_candle") || path.endsWith("_coral_block") || path.endsWith("_coral_fan")
                || path.endsWith("_coral") || path.endsWith("_banner") || path.endsWith("_bed")
                || path.endsWith("_shulker_box") || path.equals("bamboo_block") || path.equals("stripped_bamboo_block")
                || path.endsWith("_mushroom_block") || isFlower(path);
    }

    public static boolean isSurvivalItem(String path) {
        return isSellableFarmProduct(path) || isSellableFood(path)
                || path.endsWith("_sapling") || isFlower(path)
                || Set.of("potion", "splash_potion", "lingering_potion", "tipped_arrow", "ominous_bottle").contains(path)
                || Set.of("red_mushroom", "brown_mushroom", "crimson_fungus", "warped_fungus", "cactus",
                "bamboo", "vine", "kelp", "seagrass", "dead_bush", "fern", "grass", "torch", "soul_torch",
                "coal", "charcoal", "dried_kelp", "bucket", "water_bucket", "milk_bucket", "lava_bucket",
                "powder_snow_bucket", "white_bed", "campfire", "soul_campfire", "arrow", "azalea",
                "flowering_azalea", "saddle", "name_tag", "lead", "fishing_rod", "shield",
                "lantern", "soul_lantern",
                "candle", "bone_meal", "compass", "spyglass").contains(path)
                || path.endsWith("_leaves") || path.endsWith("_roots") || path.endsWith("_lantern")
                || path.equals("candle") || path.endsWith("_candle") || path.endsWith("_bed");
    }

    public static boolean isAdventureItem(String path) { return ADVENTURE_ITEMS.contains(path); }

    public static boolean isProgressionSpecial(String path) {
        return path.startsWith("music_disc_") || path.endsWith("_shulker_box") || Set.of("axolotl_bucket", "tadpole_bucket", "elytra", "shulker_shell", "totem_of_undying", "nether_star", "beacon",
                "netherite_upgrade_smithing_template", "enchanted_golden_apple", "netherite_scrap",
                "netherite_ingot", "netherite_block", "ancient_debris", "diamond", "diamond_block",
                "emerald", "emerald_block", "raw_iron", "iron_ingot", "raw_copper", "copper_ingot",
                "raw_gold", "gold_ingot", "ender_pearl", "ender_eye", "blaze_rod", "blaze_powder",
                "ghast_tear", "dragon_breath", "experience_bottle", "heart_of_the_sea", "conduit",
                "recovery_compass", "nautilus_shell", "ender_chest", "trident",
                "prismarine_shard", "prismarine_crystals", "amethyst_shard", "lapis_lazuli", "quartz").contains(path);
    }

    public static boolean isExcluded(String path) {
        return TECHNICAL.contains(path) || path.equals("air") || path.equals("cave_air") || path.equals("void_air")
                || path.contains("spawn_egg") || path.contains("command_block") || path.startsWith("test_")
                || path.startsWith("debug_") || path.equals("end_portal_frame");
    }

    public static boolean isEquipment(String id) {
        return id.endsWith("_pickaxe") || id.endsWith("_axe") || id.endsWith("_shovel")
                || id.endsWith("_hoe") || id.endsWith("_sword") || id.endsWith("_helmet")
                || id.endsWith("_chestplate") || id.endsWith("_leggings") || id.endsWith("_boots")
                || id.endsWith("_horse_armor") || id.equals("bow") || id.equals("crossbow")
                || id.equals("shield") || id.equals("trident") || id.equals("elytra")
                || id.equals("flint_and_steel") || id.equals("shears");
    }

    public static long packageTotal(String itemId, int unitPrice, int quantity) {
        if (unitPrice < 1 || quantity < 1) return 0;
        long regular = (long) unitPrice * quantity;
        String path = itemId.startsWith("minecraft:") ? itemId.substring(10) : itemId;
        if (!PACKAGE_ITEMS.contains(path) || quantity < 8) return regular;
        int discountPercent = quantity >= 64 ? 8 : quantity >= 32 ? 5 : quantity >= 16 ? 3 : 1;
        long discounted = Math.max(1, regular * (100 - discountPercent) / 100);
        int resale = salePrice(itemId);
        long conversionValue = 0;
        if (path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_hyphae")) {
            conversionValue = 4L * quantity * salePrice("minecraft:" + path.replace("_log", "_planks")
                    .replace("_wood", "_planks").replace("_hyphae", "_planks"));
        } else if (path.endsWith("_planks")) {
            conversionValue = Math.max(2L * quantity,
                    2L * quantity * salePrice("minecraft:" + path.replace("_planks", "_slab")));
        } else if (path.equals("glass")) {
            conversionValue = (8L * quantity + 2) / 3;
        } else if (path.equals("bamboo_block")) {
            conversionValue = 9L * quantity * salePrice("minecraft:bamboo");
        }
        long noArbitrageFloor = resale > 0 ? (long) resale * quantity + 1 : 1;
        noArbitrageFloor = Math.max(noArbitrageFloor, conversionValue);
        noArbitrageFloor = Math.min(regular, noArbitrageFloor);
        return Math.min(regular, Math.max(discounted, noArbitrageFloor));
    }

    public static long convertedSaleTotal(String id, int amount) {
        return (long) salePrice(id) * amount;
    }

    private static int buildingPrice(String path) {
        if (path.endsWith("_shulker_box")) return 400;
        if (path.equals("dried_kelp_block") || path.equals("honey_block")) return 20;
        if (path.equals("slime_block")) return 12;
        if (path.equals("bamboo_block") || path.equals("stripped_bamboo_block")) return 12;
        if (path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_hyphae")) return 4;
        if (path.endsWith("_planks") || path.endsWith("_stairs") || path.endsWith("_slab")
                || path.endsWith("_wall") || path.endsWith("_fence") || path.endsWith("_fence_gate")
                || path.endsWith("_button") || path.endsWith("_pressure_plate") || path.endsWith("_sign")
                || path.endsWith("_hanging_sign") || path.endsWith("_door") || path.endsWith("_trapdoor")) return 2;
        if (path.endsWith("_glass") || path.endsWith("_glass_pane")) return 4;
        if (path.endsWith("_wool")
                || path.endsWith("_carpet") || path.endsWith("_concrete") || path.endsWith("_concrete_powder")
                || path.endsWith("_terracotta")) return 3;
        if (path.endsWith("_leaves") || path.endsWith("_sapling") || path.endsWith("_candle")) return 2;
        if (path.contains("coral")) return 3;
        return 2;
    }

    private static int equipmentPrice(String id) {
        if (id.contains("netherite")) {
            if (id.endsWith("_chestplate")) return 2500;
            if (id.endsWith("_leggings")) return 2300;
            if (id.endsWith("_helmet")) return 2100;
            if (id.endsWith("_boots")) return 1900;
            if (id.endsWith("_pickaxe")) return 2400;
            if (id.endsWith("_axe")) return 2200;
            if (id.endsWith("_sword")) return 2000;
            return 1500;
        }
        if (id.contains("diamond")) {
            if (id.endsWith("_chestplate")) return 650;
            if (id.endsWith("_leggings")) return 550;
            if (id.endsWith("_helmet")) return 420;
            if (id.endsWith("_boots")) return 380;
            if (id.endsWith("_pickaxe")) return 600;
            if (id.endsWith("_axe")) return 500;
            if (id.endsWith("_sword")) return 450;
            return 350;
        }
        if (id.contains("iron")) {
            if (id.endsWith("_chestplate")) return 140;
            if (id.endsWith("_leggings")) return 120;
            if (id.endsWith("_helmet")) return 100;
            if (id.endsWith("_boots")) return 85;
            if (id.endsWith("_pickaxe")) return 120;
            if (id.endsWith("_axe")) return 110;
            if (id.endsWith("_sword")) return 100;
            return 80;
        }
        if (id.equals("elytra")) return 5000;
        if (id.equals("trident")) return 900;
        if (id.equals("bow") || id.equals("crossbow")) return 100;
        return 40;
    }

    private static boolean isSellableFarmProduct(String path) {
        return Set.of("wheat", "wheat_seeds", "beetroot", "beetroot_seeds", "carrot", "potato",
                "poisonous_potato", "sugar_cane", "cocoa_beans", "pumpkin", "melon", "pumpkin_seeds",
                "melon_seeds", "bamboo", "cactus", "sweet_berries", "glow_berries", "nether_wart",
                "apple", "melon_slice", "dried_kelp", "egg", "red_mushroom", "brown_mushroom",
                "honey_bottle", "honeycomb", "hay_block").contains(path);
    }

    private static boolean isSellableFood(String path) {
        return Set.of("bread", "cookie", "pumpkin_pie", "cod", "salmon", "tropical_fish", "pufferfish",
                "cooked_cod", "cooked_salmon", "beef", "porkchop", "chicken", "mutton", "rabbit",
                "cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton", "cooked_rabbit").contains(path);
    }

    private static boolean isSellableBuilding(String path) {
        return path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_planks")
                || path.endsWith("_leaves") || path.endsWith("_sapling") || isFlower(path)
                || path.endsWith("_wool") || path.endsWith("_concrete") || path.endsWith("_concrete_powder")
                || path.endsWith("_terracotta") || path.endsWith("_glass") || path.endsWith("_glass_pane")
                || path.endsWith("_stairs") || path.endsWith("_slab") || path.endsWith("_wall")
                || path.endsWith("_fence") || path.endsWith("_fence_gate") || path.endsWith("_door")
                || path.endsWith("_trapdoor") || path.endsWith("_button") || path.endsWith("_pressure_plate")
                || path.endsWith("_sign") || path.endsWith("_hanging_sign")
                || path.equals("dirt") || path.equals("stone") || path.equals("cobblestone")
                || path.equals("sand") || path.equals("gravel") || path.equals("clay") || path.equals("glass")
                || path.equals("bricks") || path.equals("terracotta") || path.equals("obsidian")
                || Set.of("red_sand", "sandstone", "red_sandstone", "mud", "packed_mud", "stone_bricks",
                "mossy_stone_bricks", "cobbled_deepslate", "polished_deepslate", "deepslate_bricks",
                "deepslate_tiles", "coarse_dirt", "rooted_dirt", "podzol", "mycelium", "grass_block").contains(path);
    }

    public static boolean isCommonFood(String path) {
        return Set.of("bread", "cookie", "pumpkin_pie", "apple", "golden_apple", "enchanted_golden_apple",
                "golden_carrot", "carrot", "potato", "poisonous_potato", "baked_potato", "beetroot",
                "beetroot_soup", "melon_slice", "sweet_berries", "glow_berries", "honey_bottle",
                "beef", "cooked_beef", "porkchop", "cooked_porkchop", "chicken", "cooked_chicken",
                "mutton", "cooked_mutton", "rabbit", "cooked_rabbit", "cod", "cooked_cod", "salmon",
                "cooked_salmon", "tropical_fish", "pufferfish", "mushroom_stew", "rabbit_stew",
                "suspicious_stew", "dried_kelp").contains(path);
    }

    private static boolean isFlower(String path) {
        return path.endsWith("_flower") || Set.of("dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip",
                "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower",
                "lily_of_the_valley", "sunflower", "lilac", "rose_bush", "peony", "torchflower",
                "pitcher_plant", "pink_petals").contains(path);
    }
}
