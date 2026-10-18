package com.felpz.refugio.shop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class ShopCatalog {
    public record EnchantmentSpec(String id, int level) { }
    public record Offer(String offerId, String itemId, int amount, int price,
                        List<EnchantmentSpec> enchantments, int requiredClaims) {
        public Offer {
            if (offerId == null || offerId.isBlank() || itemId == null || amount < 1 || price < 1 || requiredClaims < 0) {
                throw new IllegalArgumentException("Oferta inválida: " + offerId);
            }
            enchantments = List.copyOf(enchantments);
        }
        public Offer(String itemId, int amount, int price, List<EnchantmentSpec> enchantments) {
            this(makeOfferId(-1, itemId, enchantments), itemId, amount, price, enchantments, 0);
        }
        public Offer(String itemId, int amount, int price) {
            this(itemId, amount, price, List.of());
        }
    }
    public record Category(String titleKey, String iconItem, List<Offer> offers) { }

    private static final List<Category> BASE_CATEGORIES = List.of(
            new Category("gui.refugio.category.0", "minecraft:bread", List.of(
                    o("minecraft:bread", 16, 10), o("minecraft:cooked_beef", 16, 24),
                    o("minecraft:cooked_porkchop", 16, 24), o("minecraft:cooked_chicken", 16, 18),
                    o("minecraft:cooked_mutton", 16, 20), o("minecraft:cooked_cod", 16, 20),
                    o("minecraft:cooked_salmon", 16, 24), o("minecraft:baked_potato", 16, 18),
                    o("minecraft:carrot", 32, 20), o("minecraft:potato", 32, 20),
                    o("minecraft:golden_carrot", 8, 64), o("minecraft:golden_apple", 1, 45),
                    o("minecraft:arrow", 64, 35), o("minecraft:torch", 64, 12),
                    o("minecraft:water_bucket", 1, 40), o("minecraft:bucket", 1, 18),
                    o("minecraft:shield", 1, 28), o("minecraft:white_bed", 1, 20),
                    o("minecraft:campfire", 1, 18), o("minecraft:fishing_rod", 1, 20),
                    o("minecraft:milk_bucket", 1, 25), o("minecraft:honey_bottle", 8, 40),
                    o("minecraft:coal", 32, 24), o("minecraft:bread", 64, 35)
            )),
            new Category("gui.refugio.category.1", "minecraft:bricks", List.of(
                    o("minecraft:dirt", 64, 6), o("minecraft:cobblestone", 64, 12),
                    o("minecraft:stone", 64, 20), o("minecraft:stone_bricks", 64, 28),
                    o("minecraft:mossy_stone_bricks", 64, 48), o("minecraft:cobbled_deepslate", 64, 20),
                    o("minecraft:polished_deepslate", 64, 35), o("minecraft:gravel", 64, 12),
                    o("minecraft:sand", 64, 16), o("minecraft:sandstone", 64, 24),
                    o("minecraft:glass", 64, 32), o("minecraft:glass_pane", 64, 20),
                    o("minecraft:bricks", 32, 48), o("minecraft:clay", 32, 26),
                    o("minecraft:oak_log", 32, 22), o("minecraft:spruce_log", 32, 22),
                    o("minecraft:birch_log", 32, 22), o("minecraft:jungle_log", 32, 26),
                    o("minecraft:acacia_log", 32, 24), o("minecraft:dark_oak_log", 32, 26),
                    o("minecraft:mangrove_log", 32, 28), o("minecraft:cherry_log", 32, 28),
                    o("minecraft:bamboo_block", 32, 30), o("minecraft:oak_planks", 64, 18),
                    o("minecraft:spruce_planks", 64, 18), o("minecraft:birch_planks", 64, 18),
                    o("minecraft:chest", 4, 30), o("minecraft:barrel", 4, 30),
                    o("minecraft:crafting_table", 1, 6), o("minecraft:furnace", 1, 18),
                    o("minecraft:smoker", 1, 25), o("minecraft:blast_furnace", 1, 35),
                    o("minecraft:anvil", 1, 75), o("minecraft:enchanting_table", 1, 120),
                    o("minecraft:ender_chest", 1, 150), o("minecraft:lantern", 8, 35),
                    o("minecraft:ladder", 32, 20), o("minecraft:scaffolding", 32, 30),
                    o("minecraft:rail", 64, 60), o("minecraft:powered_rail", 16, 70),
                    o("minecraft:redstone", 32, 96), o("minecraft:repeater", 8, 42),
                    o("minecraft:comparator", 8, 55), o("minecraft:observer", 8, 55),
                    o("minecraft:piston", 8, 55), o("minecraft:sticky_piston", 4, 50),
                    o("minecraft:hopper", 4, 90), o("minecraft:dispenser", 4, 30),
                    o("minecraft:redstone_lamp", 8, 42), o("minecraft:white_wool", 32, 24),
                    o("minecraft:black_wool", 32, 24), o("minecraft:red_wool", 32, 24),
                    o("minecraft:blue_wool", 32, 24), o("minecraft:green_wool", 32, 24),
                    o("minecraft:yellow_wool", 32, 24), o("minecraft:terracotta", 32, 28),
                    o("minecraft:white_concrete", 32, 44), o("minecraft:obsidian", 16, 160)
            )),
            new Category("gui.refugio.category.2", "minecraft:iron_pickaxe", List.of(
                    o("minecraft:iron_sword", 1, 45), o("minecraft:iron_pickaxe", 1, 90),
                    o("minecraft:iron_axe", 1, 75), o("minecraft:iron_shovel", 1, 35),
                    o("minecraft:iron_hoe", 1, 40), o("minecraft:iron_helmet", 1, 70),
                    o("minecraft:iron_chestplate", 1, 110), o("minecraft:iron_leggings", 1, 95),
                    o("minecraft:iron_boots", 1, 60), o("minecraft:diamond_sword", 1, 220),
                    o("minecraft:diamond_pickaxe", 1, 320), o("minecraft:diamond_axe", 1, 280),
                    o("minecraft:diamond_shovel", 1, 140), o("minecraft:diamond_hoe", 1, 150),
                    o("minecraft:diamond_helmet", 1, 260), o("minecraft:diamond_chestplate", 1, 420),
                    o("minecraft:diamond_leggings", 1, 370), o("minecraft:diamond_boots", 1, 240),
                    o("minecraft:shield", 1, 28), o("minecraft:bow", 1, 90),
                    o("minecraft:crossbow", 1, 120), o("minecraft:trident", 1, 900),
                    o("minecraft:flint_and_steel", 1, 25), o("minecraft:shears", 1, 35),
                    o("minecraft:netherite_sword", 1, 1800), o("minecraft:netherite_pickaxe", 1, 2400),
                    o("minecraft:netherite_helmet", 1, 2100), o("minecraft:netherite_chestplate", 1, 3400),
                    o("minecraft:netherite_leggings", 1, 3000), o("minecraft:netherite_boots", 1, 1950)
            )),
            new Category("gui.refugio.category.3", "minecraft:ender_pearl", List.of(
                    o("minecraft:ender_pearl", 16, 140), o("minecraft:blaze_rod", 16, 110),
                    o("minecraft:blaze_powder", 16, 90), o("minecraft:ender_eye", 12, 360),
                    o("minecraft:firework_rocket", 64, 180), o("minecraft:elytra", 1, 6500),
                    o("minecraft:shulker_shell", 8, 240), o("minecraft:shulker_box", 1, 900),
                    o("minecraft:saddle", 1, 60), o("minecraft:name_tag", 1, 70),
                    o("minecraft:lead", 4, 30), o("minecraft:totem_of_undying", 1, 1200),
                    o("minecraft:dragon_breath", 8, 350), o("minecraft:netherite_upgrade_smithing_template", 1, 2000),
                    o("minecraft:enchanted_golden_apple", 1, 900), o("minecraft:experience_bottle", 16, 250),
                    o("minecraft:conduit", 1, 500), o("minecraft:nautilus_shell", 8, 200),
                    o("minecraft:heart_of_the_sea", 1, 300), o("minecraft:recovery_compass", 1, 350),
                    o("minecraft:compass", 1, 45), o("minecraft:spyglass", 1, 90),
                    o("minecraft:elytra", 1, 6500), o("minecraft:shulker_box", 4, 3200)
            )),
            new Category("gui.refugio.category.4", "minecraft:netherite_sword", List.of(
                    enchanted("minecraft:diamond_sword", 1, 1800,
                            e("minecraft:sharpness", 5), e("minecraft:looting", 3), e("minecraft:unbreaking", 3)),
                    enchanted("minecraft:netherite_sword", 1, 5200,
                            e("minecraft:sharpness", 5), e("minecraft:looting", 3), e("minecraft:unbreaking", 3), e("minecraft:mending", 1)),
                    enchanted("minecraft:diamond_pickaxe", 1, 2200,
                            e("minecraft:efficiency", 5), e("minecraft:fortune", 3), e("minecraft:unbreaking", 3)),
                    enchanted("minecraft:netherite_pickaxe", 1, 7200,
                            e("minecraft:efficiency", 5), e("minecraft:fortune", 3), e("minecraft:unbreaking", 3), e("minecraft:mending", 1)),
                    enchanted("minecraft:diamond_pickaxe", 1, 1800,
                            e("minecraft:efficiency", 5), e("minecraft:silk_touch", 1), e("minecraft:unbreaking", 3)),
                    enchanted("minecraft:netherite_axe", 1, 6000,
                            e("minecraft:efficiency", 5), e("minecraft:sharpness", 5), e("minecraft:unbreaking", 3), e("minecraft:mending", 1)),
                    enchanted("minecraft:netherite_helmet", 1, 6800,
                            e("minecraft:protection", 4), e("minecraft:respiration", 3), e("minecraft:unbreaking", 3), e("minecraft:mending", 1)),
                    enchanted("minecraft:netherite_chestplate", 1, 8500,
                            e("minecraft:protection", 4), e("minecraft:thorns", 3), e("minecraft:unbreaking", 3), e("minecraft:mending", 1)),
                    enchanted("minecraft:netherite_leggings", 1, 7800,
                            e("minecraft:protection", 4), e("minecraft:swift_sneak", 3), e("minecraft:unbreaking", 3), e("minecraft:mending", 1)),
                    enchanted("minecraft:netherite_boots", 1, 7200,
                            e("minecraft:protection", 4), e("minecraft:feather_falling", 4), e("minecraft:depth_strider", 3), e("minecraft:unbreaking", 3), e("minecraft:mending", 1)),
                    enchanted("minecraft:elytra", 1, 9500,
                            e("minecraft:unbreaking", 3), e("minecraft:mending", 1)),
                    enchanted("minecraft:bow", 1, 1400,
                            e("minecraft:power", 5), e("minecraft:flame", 1), e("minecraft:infinity", 1), e("minecraft:unbreaking", 3)),
                    enchanted("minecraft:trident", 1, 2800,
                            e("minecraft:loyalty", 3), e("minecraft:channeling", 1), e("minecraft:unbreaking", 3)),
                    enchanted("minecraft:crossbow", 1, 1500,
                            e("minecraft:quick_charge", 3), e("minecraft:piercing", 4), e("minecraft:unbreaking", 3)),
                    enchanted("minecraft:diamond_boots", 1, 1200,
                            e("minecraft:feather_falling", 4), e("minecraft:depth_strider", 3), e("minecraft:unbreaking", 3)),
                    enchanted("minecraft:diamond_chestplate", 1, 1800,
                            e("minecraft:protection", 4), e("minecraft:thorns", 3), e("minecraft:unbreaking", 3)),
                    enchanted("minecraft:enchanted_book", 1, 900, e("minecraft:efficiency", 5)),
                    enchanted("minecraft:enchanted_book", 1, 900, e("minecraft:fortune", 3)),
                    enchanted("minecraft:enchanted_book", 1, 750, e("minecraft:silk_touch", 1)),
                    enchanted("minecraft:enchanted_book", 1, 750, e("minecraft:mending", 1)),
                    enchanted("minecraft:enchanted_book", 1, 650, e("minecraft:unbreaking", 3)),
                    enchanted("minecraft:enchanted_book", 1, 750, e("minecraft:protection", 4)),
                    enchanted("minecraft:enchanted_book", 1, 900, e("minecraft:sharpness", 5)),
                    enchanted("minecraft:enchanted_book", 1, 850, e("minecraft:looting", 3))
            ))
    );

    private static final Map<Integer, List<Offer>> GENERATED_CATALOG = new HashMap<>();

    private ShopCatalog() { }

    public static List<Category> categories() {
        List<Category> result = new ArrayList<>();
        for (int i = 0; i < categoryCount(); i++) result.add(category(i));
        return List.copyOf(result);
    }

    public static Category category(int index) {
        Category base = BASE_CATEGORIES.get(index);
        return new Category(base.titleKey(), base.iconItem(), offers(index));
    }

    public static int categoryCount() { return BASE_CATEGORIES.size(); }

    public static synchronized List<Offer> offers(int categoryIndex) {
        if (categoryIndex < 0 || categoryIndex >= BASE_CATEGORIES.size()) return List.of();
        return GENERATED_CATALOG.computeIfAbsent(categoryIndex, ShopCatalog::buildCatalog);
    }

    public static List<String> subcategories(int categoryIndex) {
        Set<String> keys = new TreeSet<>();
        for (Offer offer : offers(categoryIndex)) keys.add(subcategoryKey(categoryIndex, offer));
        return List.copyOf(keys);
    }

    public static List<Offer> offersInSubcategory(int categoryIndex, String subcategory) {
        return offers(categoryIndex).stream()
                .filter(offer -> subcategoryKey(categoryIndex, offer).equals(subcategory)).toList();
    }

    public static String subcategoryKey(int categoryIndex, Offer offer) {
        String id = offer.itemId().substring(offer.itemId().indexOf(':') + 1);
        if (categoryIndex == 0) return survivalSubcategory(id);
        if (categoryIndex == 1) return buildingSubcategory(id);
        if (categoryIndex == 2) return equipmentSubcategory(id, !offer.enchantments().isEmpty());
        if (categoryIndex == 3) return adventureSubcategory(id);
        return plusSubcategory(id, !offer.enchantments().isEmpty());
    }

    public static int unitPrice(Offer offer) {
        return offer.price();
    }

    public static int maxStackSize(Offer offer) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(offer.itemId()));
        return new ItemStack(item).getMaxStackSize();
    }

    public static int requiredClaims(int categoryIndex) {
        return switch (categoryIndex) { case 0 -> 0; case 1 -> 1; case 2 -> 2; case 3 -> 4; case 4 -> 8; default -> Integer.MAX_VALUE; };
    }

    public static int requiredClaims(Offer offer) { return offer.requiredClaims(); }

    public static long totalPrice(Offer offer, int quantity) {
        return ShopPricing.packageTotal(offer.itemId(), offer.price(), quantity);
    }

    public static int sellValue(String itemId) {
        return ShopPricing.salePrice(itemId);
    }

    public static Map<String, Integer> sellPrices() {
        Map<String, Integer> result = new java.util.TreeMap<>(ShopPricing.explicitSellPrices());
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            Identifier id = BuiltInRegistries.ITEM.getKey(entry.getValue());
            if (!"minecraft".equals(id.getNamespace())) continue;
            int value = ShopPricing.salePrice(id.toString());
            if (value > 0) result.put(id.toString(), value);
        }
        return Map.copyOf(result);
    }

    private static List<Offer> buildCatalog(int categoryIndex) {
        List<Offer> result = new ArrayList<>();
        Set<String> normalized = new HashSet<>();
        for (Offer offer : BASE_CATEGORIES.get(categoryIndex).offers()) {
            String unique = offer.itemId() + "|" + offer.enchantments();
            if (!normalized.add(unique)) continue;
            Identifier identifier = Identifier.parse(offer.itemId());
            String path = identifier.getPath();
            Item item = BuiltInRegistries.ITEM.getValue(identifier);
            if (item == net.minecraft.world.item.Items.AIR || ShopPricing.isExcluded(path)) continue;
            if (categoryIndex < 4 && categoryFor(path, item) != categoryIndex) continue;
            if (categoryIndex == 4 && !compatiblePlusOffer(offer)) continue;
            int price = categoryIndex == 4 ? offer.price()
                    : ShopPricing.purchasePrice(offer.itemId(), categoryIndex, path, item instanceof BlockItem);
            if (price < 1) continue;
            result.add(new Offer(makeOfferId(categoryIndex, offer.itemId(), offer.enchantments()),
                    offer.itemId(), 1, price, offer.enchantments(), requiredClaimsFor(categoryIndex, path)));
        }
        if (categoryIndex == 4) return List.copyOf(result);
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            Identifier id = BuiltInRegistries.ITEM.getKey(entry.getValue());
            String idString = id.toString();
            if (!"minecraft".equals(id.getNamespace()) || normalized.contains(idString + "|[]")
                    || ShopPricing.isExcluded(id.getPath())) continue;
            int assignedCategory = categoryFor(id.getPath(), entry.getValue());
            if (assignedCategory != categoryIndex) continue;
            int price = ShopPricing.purchasePrice(idString, categoryIndex, id.getPath(), entry.getValue() instanceof BlockItem);
            if (price < 1) continue;
            result.add(new Offer(makeOfferId(categoryIndex, idString, List.of()), idString, 1, price,
                    List.of(), requiredClaimsFor(categoryIndex, id.getPath())));
            normalized.add(idString + "|[]");
        }
        return List.copyOf(result);
    }

    private static int categoryFor(String id, Item item) {
        if (id.equals("elytra")) return 3;
        if (ShopPricing.isEquipment(id)) return 2;
        if (ShopPricing.isSurvivalItem(id) || ShopPricing.isCommonFood(id)) return 0;
        if (ShopPricing.isProgressionSpecial(id)) return 3;
        if (ShopPricing.isAllowedBuilding(id) && (item instanceof BlockItem || id.equals("redstone"))) return 1;
        if (ShopPricing.isAdventureItem(id)) return 3;
        return -1;
    }

    private static int requiredClaimsFor(int category, String path) {
        int base = requiredClaims(category);
        if (category == 4 || path.contains("netherite") || path.equals("elytra")
                || path.endsWith("_shulker_box") || path.equals("shulker_box") || path.equals("shulker_shell") || path.equals("totem_of_undying")
                || path.equals("nether_star") || path.equals("beacon") || path.equals("enchanted_golden_apple")
                || path.equals("netherite_upgrade_smithing_template")) return 8;
        return base;
    }

    private static String makeOfferId(int category, String itemId, List<EnchantmentSpec> enchantments) {
        String path = itemId.replace(':', '/');
        String enchantKey = enchantments.stream().map(e -> e.id().substring(e.id().indexOf(':') + 1) + "_" + e.level())
                .sorted().collect(java.util.stream.Collectors.joining("+"));
        return "refugio:shop/" + category + "/" + path + (enchantKey.isEmpty() ? "" : "/" + enchantKey);
    }

    private static boolean compatiblePlusOffer(Offer offer) {
        if (offer.enchantments().isEmpty()) return false;
        Set<String> ids = new HashSet<>();
        for (EnchantmentSpec enchantment : offer.enchantments()) {
            String id = enchantment.id().startsWith("minecraft:") ? enchantment.id().substring(10) : "";
            if (!Set.of("sharpness", "looting", "unbreaking", "mending", "efficiency", "fortune", "silk_touch",
                    "protection", "respiration", "thorns", "swift_sneak", "feather_falling", "depth_strider",
                    "power", "flame", "infinity", "loyalty", "channeling", "quick_charge", "piercing").contains(id)
                    || enchantment.level() < 1 || enchantment.level() > 5 || !ids.add(id)) return false;
        }
        if (ids.contains("fortune") && ids.contains("silk_touch")) return false;
        if (ids.contains("infinity") && ids.contains("mending")) return false;
        if (ids.contains("sharpness") && (ids.contains("smite") || ids.contains("bane_of_arthropods"))) return false;
        String path = offer.itemId().substring(offer.itemId().indexOf(':') + 1);
        if (path.equals("enchanted_book")) return true;
        Set<String> allowed = path.endsWith("_sword")
                ? Set.of("sharpness", "looting", "unbreaking", "mending")
                : path.endsWith("_pickaxe") ? Set.of("efficiency", "fortune", "silk_touch", "unbreaking", "mending")
                : path.endsWith("_axe") ? Set.of("efficiency", "sharpness", "unbreaking", "mending")
                : path.endsWith("_helmet") ? Set.of("protection", "respiration", "thorns", "unbreaking", "mending")
                : path.endsWith("_chestplate") ? Set.of("protection", "thorns", "unbreaking", "mending")
                : path.endsWith("_leggings") ? Set.of("protection", "swift_sneak", "unbreaking", "mending")
                : path.endsWith("_boots") ? Set.of("protection", "feather_falling", "depth_strider", "unbreaking", "mending")
                : path.equals("elytra") ? Set.of("unbreaking", "mending")
                : path.equals("bow") ? Set.of("power", "flame", "infinity", "unbreaking")
                : path.equals("trident") ? Set.of("loyalty", "channeling", "unbreaking")
                : path.equals("crossbow") ? Set.of("quick_charge", "piercing", "unbreaking") : Set.of();
        return allowed.containsAll(ids);
    }

    private static boolean isSurvivalPlant(String id) {
        return id.contains("sapling") || id.endsWith("_seeds") || id.equals("wheat")
                || id.contains("flower") || id.contains("mushroom") || id.contains("cactus")
                || id.contains("bamboo") || id.contains("vine") || id.contains("leaves")
                || id.contains("azalea") || id.contains("roots") || id.equals("sugar_cane")
                || id.equals("kelp") || id.equals("seagrass") || id.equals("dead_bush")
                || id.equals("fern") || id.equals("grass");
    }

    private static boolean isFoodOrSurvival(String id) {
        return id.contains("beef") || id.contains("porkchop") || id.contains("chicken")
                || id.contains("mutton") || id.contains("rabbit") || id.contains("cod")
                || id.contains("salmon") || id.contains("stew") || id.contains("pie")
                || id.contains("bread") || id.contains("cookie") || id.contains("apple")
                || id.contains("carrot") || id.contains("potato") || id.contains("beetroot")
                || id.contains("melon_slice") || id.contains("berries") || id.contains("honey_bottle")
                || id.contains("seed") || id.contains("sapling") || id.contains("torch")
                || id.contains("lantern") || id.contains("candle") || id.contains("coal")
                || id.contains("charcoal") || id.contains("bucket") || id.equals("bed");
    }

    private static String survivalSubcategory(String id) {
        if (isFoodOrSurvival(id) && !(id.contains("seed") || id.contains("sapling") || id.contains("torch")
                || id.contains("lantern") || id.contains("candle") || id.contains("coal") || id.contains("bucket") || id.equals("bed"))) return "survival_food";
        if (id.contains("seed") || id.equals("wheat") || id.contains("pumpkin") || id.contains("melon")) return "survival_agriculture";
        if (isSurvivalPlant(id)) return "survival_plants";
        if (id.contains("lead") || id.contains("saddle") || id.contains("hay") || id.contains("name_tag")) return "survival_animals";
        if (id.contains("bone_meal") || id.contains("composter") || id.contains("farmland")) return "survival_cultivation";
        if (id.contains("potion") || id.contains("golden_apple") || id.contains("honey_bottle") || id.contains("totem")) return "survival_healing";
        if (id.contains("torch") || id.contains("lantern") || id.contains("candle") || id.contains("campfire")) return "survival_lighting";
        if (id.contains("coal") || id.contains("charcoal") || id.contains("lava_bucket")) return "survival_fuel";
        return "survival_utilities";
    }

    private static String buildingSubcategory(String id) {
        if (id.equals("dirt") || id.contains("sand") || id.contains("gravel") || id.contains("clay")) return "building_terrain";
        if (id.contains("stone") || id.contains("deepslate") || id.contains("cobble")) return "building_stone";
        if (id.contains("brick") || id.contains("quartz") || id.contains("prismarine")) return "building_decor";
        if (id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_hyphae") || id.contains("bamboo_block")) return "building_wood";
        if (id.contains("door") || id.contains("trapdoor") || id.contains("fence") || id.contains("gate")) return "building_doors";
        if (id.contains("glass")) return "building_glass";
        if (id.contains("wool") || id.contains("concrete") || id.contains("terracotta") || id.contains("carpet")) return "building_colors";
        if (id.contains("torch") || id.contains("lantern") || id.contains("candle") || id.contains("sea_lantern")) return "building_lighting";
        if (id.contains("chest") || id.contains("barrel") || id.contains("shulker")) return "building_storage";
        if (id.contains("crafting") || id.contains("furnace") || id.contains("anvil") || id.contains("smithing") || id.contains("stonecutter")) return "building_workstations";
        if (id.contains("rail") || id.contains("minecart") || id.contains("boat")) return "building_transport";
        if (id.contains("redstone") || id.contains("piston") || id.contains("observer") || id.contains("repeater") || id.contains("comparator") || id.contains("hopper")) return "building_redstone";
        if (id.contains("composter") || id.contains("hay") || id.contains("bee") || id.contains("honey")) return "building_farming";
        if (id.contains("obsidian") || id.contains("beacon") || id.contains("end_rod")) return "building_rare";
        if (id.endsWith("_planks") || id.endsWith("_stairs") || id.endsWith("_slab") || id.endsWith("_sign") || id.endsWith("_hanging_sign")) return "building_wood_shapes";
        return "building_decor";
    }

    private static String equipmentSubcategory(String id, boolean enchanted) {
        if (enchanted) return "equipment_enchanted";
        if (id.endsWith("_pickaxe")) return "equipment_pickaxes";
        if (id.endsWith("_axe")) return "equipment_axes";
        if (id.endsWith("_shovel")) return "equipment_shovels";
        if (id.endsWith("_hoe")) return "equipment_hoes";
        if (id.endsWith("_sword")) return "equipment_swords";
        if (id.equals("bow") || id.equals("crossbow")) return "equipment_ranged";
        if (id.equals("shield")) return "equipment_shields";
        if (id.endsWith("_helmet") || id.endsWith("_chestplate") || id.endsWith("_leggings") || id.endsWith("_boots") || id.endsWith("_horse_armor")) return "equipment_armor";
        return "equipment_combat";
    }

    private static String adventureSubcategory(String id) {
        if (id.contains("map") || id.contains("compass") || id.contains("eye_of_ender")) return "adventure_navigation";
        if (id.contains("saddle") || id.contains("horse") || id.contains("boat") || id.contains("minecart")) return "adventure_transport";
        if (id.contains("potion")) return "adventure_potions";
        if (id.contains("blaze") || id.contains("nether") || id.contains("ghast") || id.contains("wither")) return "adventure_nether";
        if (id.contains("ender") || id.contains("dragon") || id.contains("shulker") || id.contains("chorus")) return "adventure_end";
        if (id.contains("firework") || id.equals("elytra")) return "adventure_flight";
        if (id.contains("shell") || id.contains("heart_of_the_sea") || id.contains("totem") || id.contains("disc")) return "adventure_treasures";
        if (id.contains("shulker_box")) return "adventure_storage";
        if (id.contains("torch") || id.contains("lantern") || id.contains("bucket") || id.contains("food")) return "adventure_preparation";
        if (id.contains("arrow") || id.contains("spyglass") || id.contains("telescope")) return "adventure_caves";
        return "adventure_rare";
    }

    private static String plusSubcategory(String id, boolean enchanted) {
        if (id.equals("enchanted_book")) return "plus_books";
        if (id.endsWith("_pickaxe") || id.endsWith("_axe") || id.endsWith("_shovel") || id.endsWith("_hoe")) return "plus_tools";
        if (id.endsWith("_sword") || id.equals("bow") || id.equals("crossbow") || id.equals("trident")) return "plus_weapons";
        if (id.endsWith("_helmet") || id.endsWith("_chestplate") || id.endsWith("_leggings") || id.endsWith("_boots")) return "plus_armor";
        return enchanted ? "plus_specialized" : "plus_kits";
    }

    public static String displayItemName(Offer offer, net.minecraft.world.level.Level level) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(offer.itemId()));
        return new ItemStack(item).getHoverName().getString();
    }

    private static Offer o(String itemId, int amount, int price) {
        return new Offer(itemId, amount, price);
    }

    private static Offer enchanted(String itemId, int amount, int price, EnchantmentSpec... enchantments) {
        return new Offer(itemId, amount, price, List.of(enchantments));
    }

    private static EnchantmentSpec e(String id, int level) {
        return new EnchantmentSpec(id, level);
    }
}
