package com.felpz.refugio.shop;

import com.felpz.refugio.ability.RefugioAbilityRules;

public final class ShopEconomyInvariantTest {
    private ShopEconomyInvariantTest() { }

    public static void main(String[] args) {
        saleReferences();
        purchaseReferences();
        compactedBlocksCoverIngredients();
        packagesNeverCreateDirectResaleProfit();
        processingDoesNotCreateResaleProfit();
        technicalItemsStayExcluded();
        abilityRankCostChecks();
        abilityEffectBounds();
        System.out.println("Shop economy invariants: PASS");
    }

    private static void abilityRankCostChecks() {
        int[] expectedCosts = {500, 1000, 2000, 3000, 4000};
        for (int rank = 1; rank <= expectedCosts.length; rank++) {
            require(RefugioAbilityRules.costForRank(rank) == expectedCosts[rank - 1],
                    "ability rank cost mismatch at rank " + rank);
        }
        require(RefugioAbilityRules.costForRank(0) == -1, "rank zero must not have a price");
        require(RefugioAbilityRules.costForRank(6) == -1, "rank six must not have a price");
        require(RefugioAbilityRules.maxRank() == 5, "unexpected maximum ability rank");
        require(RefugioAbilityRules.maxRank("vitality_health") == 10, "vitality health must have ten ranks");
        for (int rank = 1; rank <= 10; rank++) {
            require(RefugioAbilityRules.costForRank("vitality_health", rank) == rank * 500,
                    "vitality health price mismatch at rank " + rank);
        }
        require(RefugioAbilityRules.costForRank("vitality_health", 11) == -1,
                "vitality health rank eleven must not have a price");
    }

    private static void abilityEffectBounds() {
        near(RefugioAbilityRules.criticalChance(1), 0.10, "critical chance rank 1");
        near(RefugioAbilityRules.criticalChance(5), 0.50, "critical chance cap");
        near(RefugioAbilityRules.criticalChance(99), 0.50, "critical chance out-of-range cap");
        near(RefugioAbilityRules.criticalDamageBonus(1), 0.20, "critical damage rank 1");
        near(RefugioAbilityRules.criticalDamageBonus(5), 1.00, "critical damage cap");
        near(RefugioAbilityRules.firstCutBonus(1), 0.15, "first-cut rank 1");
        near(RefugioAbilityRules.firstCutBonus(5), 0.75, "first-cut cap");
        require(RefugioAbilityRules.firstCutQuietTicks() == 100L, "first-cut 5s quiet window");
        near(RefugioAbilityRules.retaliationBonus(1), 0.15, "retaliation rank 1");
        near(RefugioAbilityRules.retaliationBonus(5), 0.75, "retaliation cap");
        require(RefugioAbilityRules.retaliationReadyTicks() == 80L, "retaliation 4s ready window");
        near(RefugioAbilityRules.hordeVigorBonus(5), 0.30, "horde-vigor cap");
        near(RefugioAbilityRules.swordMasteryBonus(1), 0.10, "sword mastery rank 1");
        near(RefugioAbilityRules.swordMasteryBonus(5), 0.50, "sword mastery cap");
        near(RefugioAbilityRules.axeMasteryBonus(1), 0.10, "axe mastery rank 1");
        near(RefugioAbilityRules.axeMasteryBonus(5), 0.50, "axe mastery cap");
        near(RefugioAbilityRules.bowMasteryBonus(1), 0.10, "bow mastery rank 1");
        near(RefugioAbilityRules.bowMasteryBonus(5), 0.50, "bow mastery cap");
        near(RefugioAbilityRules.steadyAimReduction(5), 0.50, "steady aim cap");
        near(RefugioAbilityRules.lightStepReduction(1), 0.10, "light-step rank 1");
        near(RefugioAbilityRules.lightStepReduction(5), 0.50, "light-step cap");
        near(RefugioAbilityRules.firmStanceReduction(1), 0.05, "firm-stance rank 1");
        near(RefugioAbilityRules.firmStanceReduction(5), 0.25, "firm-stance cap");
        near(RefugioAbilityRules.lastReserveReduction(1), 0.20, "last-reserve rank 1");
        near(RefugioAbilityRules.lastReserveReduction(4), 0.50, "last-reserve rank 4");
        near(RefugioAbilityRules.lastReserveReduction(5), 0.50, "last-reserve cap");
        require(RefugioAbilityRules.lastReserveCooldownTicks(1) == 1200L, "last-reserve rank 1 cooldown");
        require(RefugioAbilityRules.lastReserveCooldownTicks(5) == 400L, "last-reserve cooldown floor");
        near(RefugioAbilityRules.movementSpeedBonus(1), 0.07, "momentum rank 1");
        near(RefugioAbilityRules.movementSpeedBonus(5), 0.30, "momentum cap");
        near(RefugioAbilityRules.miningSpeedBonus(1), 0.06, "mining rank 1");
        near(RefugioAbilityRules.miningSpeedBonus(5), 0.30, "mining cap");
        near(RefugioAbilityRules.anchorKnockbackBonus(5), 0.40, "anchor cap");
        near(RefugioAbilityRules.superJumpVelocity(1), 0.53, "super-jump rank 1");
        near(RefugioAbilityRules.superJumpVelocity(5), 0.65, "super-jump rank 5");
        near(RefugioAbilityRules.doubleJumpVelocity(1), 0.54, "double-jump rank 1");
        near(RefugioAbilityRules.doubleJumpVelocity(5), 0.70, "double-jump rank 5");
        near(RefugioAbilityRules.burstVelocity(1), 1.0, "burst rank 1");
        near(RefugioAbilityRules.burstVelocity(5), 5.0, "burst cap");
        near(RefugioAbilityRules.burstImpulse(1), 0.2, "short burst impulse rank 1");
        near(RefugioAbilityRules.burstImpulse(5), 1.0, "short burst impulse rank 5");
        require(RefugioAbilityRules.burstMaxCharges(4) == 2, "burst charges rank 4");
        require(RefugioAbilityRules.burstMaxCharges(5) == 2, "burst double charge rank 5");
        require(RefugioAbilityRules.burstCooldownTicks(1) == 600L, "burst cooldown rank 1");
        require(RefugioAbilityRules.burstCooldownTicks(2) == 400L, "burst cooldown rank 2");
        require(RefugioAbilityRules.burstCooldownTicks(3) == 200L, "burst cooldown rank 3");
        require(RefugioAbilityRules.burstCooldownTicks(4) == 200L, "burst cooldown rank 4");
        require(RefugioAbilityRules.burstCooldownTicks(5) == 100L, "burst cooldown rank 5");
        near(RefugioAbilityRules.vitalityMaxHealthBonus(1), 2.0, "one heart per vitality rank");
        near(RefugioAbilityRules.vitalityMaxHealthBonus(10), 20.0, "ten hearts at vitality rank 10");
        near(RefugioAbilityRules.naturalRegenerationTriggerHealth(1), 6.0, "regen trigger rank 1 (30% of base 20)");
        near(RefugioAbilityRules.naturalRegenerationTargetHealth(1), 10.0, "regen target rank 1 (50% of base 20)");
        near(RefugioAbilityRules.naturalRegenerationTriggerHealth(5), 12.0, "regen trigger rank 5 (60% of base 20)");
        near(RefugioAbilityRules.naturalRegenerationTargetHealth(5), 16.0, "regen target rank 5 (80% of base 20)");
        require(RefugioAbilityRules.naturalRegenerationOutOfCombatTicks() == 200L, "regen requires 10s outside combat");
        require(RefugioAbilityRules.naturalRegenerationIntervalTicks() == 40L, "regen interval 2s");
        near(RefugioAbilityRules.vitalityOnKillHealRatio(1), 0.01, "vitality kill heal rank 1");
        near(RefugioAbilityRules.vitalityOnKillHealRatio(5), 0.05, "vitality kill heal rank 5");
        near(RefugioAbilityRules.areaRadius(1), 3.0, "area radius rank 1");
        near(RefugioAbilityRules.areaRadius(3), 3.0, "area radius rank 3");
        near(RefugioAbilityRules.areaRadius(4), 4.0, "area radius rank 4");
        near(RefugioAbilityRules.areaRadius(5), 5.0, "area radius rank 5");
        require(RefugioAbilityRules.areaTargetCount(1) == 1, "area target count rank 1");
        require(RefugioAbilityRules.areaTargetCount(5) == 5, "area target count cap");
        near(RefugioAbilityRules.areaBaseDamage(1), 5.0, "area base damage rank 1");
        near(RefugioAbilityRules.areaBaseDamage(5), 25.0, "area base damage rank 5");
        require(RefugioAbilityRules.areaCooldownTicks(1) == 1200L, "area cooldown rank 1");
        require(RefugioAbilityRules.areaCooldownTicks(2) == 1000L, "area cooldown rank 2");
        require(RefugioAbilityRules.areaCooldownTicks(3) == 800L, "area cooldown rank 3");
        require(RefugioAbilityRules.areaCooldownTicks(4) == 800L, "area cooldown rank 4");
        require(RefugioAbilityRules.areaCooldownTicks(5) == 600L, "area cooldown floor");
        near(RefugioAbilityRules.collectorRadius(1), 2.0, "collector radius rank 1");
        near(RefugioAbilityRules.collectorRadius(5), 6.0, "collector radius rank 5");
        near(RefugioAbilityRules.explorationExhaustionReduction(1), 0.06, "exploration rank 1");
        near(RefugioAbilityRules.explorationExhaustionReduction(5), 0.30, "exploration cap");
        near(RefugioAbilityRules.preparationSaturation(5), 2.5, "preparation cap");
        near(RefugioAbilityRules.logisticsSaveChance(1), 0.08, "logistics rank 1");
        near(RefugioAbilityRules.logisticsSaveChance(5), 0.40, "logistics cap");
        near(RefugioAbilityRules.logisticsSaveChance(0), 0.0, "unlearned logistics");
        require(RefugioAbilityRules.trackNodeUnlocked(0, false), "first node in every track must be available");
        require(!RefugioAbilityRules.trackNodeUnlocked(1, false), "next track node must wait for prerequisite");
        require(RefugioAbilityRules.trackNodeUnlocked(1, true), "next track node unlocks with prerequisite");
        require(RefugioAbilityRules.canClaimFreeChoice(false, 0, true), "first track node should accept free choice");
        require(!RefugioAbilityRules.canClaimFreeChoice(true, 0, true), "free choice must be one-time");
        require(!RefugioAbilityRules.canClaimFreeChoice(false, 0, false), "free choice must be first node in its track only");
        require(!RefugioAbilityRules.canClaimFreeChoice(false, 1, true), "owned rank cannot be claimed as new free choice");
    }

    private static void near(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 0.00001, message + ": expected " + expected + " got " + actual);
    }

    private static void saleReferences() {
        expectSale("raw_iron", 4); expectSale("iron_ingot", 5);
        expectSale("raw_copper", 2); expectSale("copper_ingot", 3);
        expectSale("raw_gold", 6); expectSale("gold_ingot", 8);
        expectSale("coal", 2); expectSale("diamond", 30); expectSale("emerald", 10);
        expectSale("ancient_debris", 50); expectSale("netherite_scrap", 80);
        expectSale("netherite_ingot", 250); expectSale("ender_pearl", 2);
        expectSale("blaze_rod", 8); expectSale("ghast_tear", 10);
        expectSale("shulker_shell", 12); expectSale("wheat", 1);
        expectSale("hay_block", 4); expectSale("dirt", 1);
    }

    private static void purchaseReferences() {
        expectBuy("dirt", 1, 1, true); expectBuy("stone", 1, 1, true);
        expectBuy("oak_log", 1, 4, true); expectBuy("glass", 1, 3, true);
        expectBuy("raw_iron", 3, 8, false); expectBuy("iron_ingot", 3, 10, false);
        expectBuy("raw_copper", 3, 3, false); expectBuy("copper_ingot", 3, 5, false);
        expectBuy("raw_gold", 3, 18, false); expectBuy("gold_ingot", 3, 22, false);
        expectBuy("diamond", 3, 120, false); expectBuy("emerald", 3, 35, false);
        expectBuy("netherite_ingot", 3, 1750, false); expectBuy("ancient_debris", 3, 280, false);
        expectBuy("nether_star", 3, 2500, false); expectBuy("beacon", 1, 5000, true);
        expectBuy("elytra", 3, 5000, false); expectBuy("shulker_box", 1, 400, true);
        expectBuy("enchanted_golden_apple", 0, 1500, false);
        expectBuy("fishing_rod", 0, 30, false); expectBuy("potion", 0, 45, false);
        expectBuy("axolotl_bucket", 3, 500, false); expectBuy("music_disc_pigstep", 3, 300, false);
        expectBuy("music_disc_cat", 3, 60, false); expectBuy("slime_block", 1, 12, true);
        expectBuy("red_shulker_box", 3, 400, true);
        expectBuy("diamond_pickaxe", 2, 600, false); expectBuy("iron_pickaxe", 2, 120, false);
        expectBuy("netherite_chestplate", 2, 2500, false);
    }

    private static void compactedBlocksCoverIngredients() {
        require(buy("diamond_block", 1, true) >= 9L * buy("diamond", 3, false), "diamond block < 9 diamonds");
        require(buy("emerald_block", 1, true) >= 9L * buy("emerald", 3, false), "emerald block < 9 emeralds");
        require(buy("iron_block", 1, true) >= 9L * buy("iron_ingot", 3, false), "iron block < 9 ingots");
        require(buy("raw_iron_block", 1, true) >= 9L * buy("raw_iron", 3, false), "raw iron block < 9 raw iron");
        require(buy("gold_block", 1, true) >= 9L * buy("gold_ingot", 3, false), "gold block < 9 ingots");
        require(buy("copper_block", 1, true) >= 9L * buy("copper_ingot", 3, false), "copper block < 9 ingots");
        require(buy("netherite_block", 1, true) >= 9L * buy("netherite_ingot", 3, false), "netherite block < 9 ingots");
        require(buy("raw_gold_block", 1, true) >= 9L * buy("raw_gold", 3, false), "raw gold block < 9 raw gold");
        require(buy("raw_copper_block", 1, true) >= 9L * buy("raw_copper", 3, false), "raw copper block < 9 raw copper");
    }

    private static void packagesNeverCreateDirectResaleProfit() {
        String[] items = {"dirt", "stone", "oak_log", "glass", "white_wool", "white_concrete",
                "raw_iron", "iron_ingot", "raw_copper", "copper_ingot", "raw_gold", "gold_ingot"};
        int[] quantities = {1, 8, 16, 32, 64};
        for (String path : items) {
            int category = ShopPricing.isAllowedBuilding(path) ? 1 : 3;
            int unit = buy(path, category, category == 1);
            String id = "minecraft:" + path;
            for (int quantity : quantities) {
                long total = ShopPricing.packageTotal(id, unit, quantity);
                long resale = (long) ShopPricing.salePrice(id) * quantity;
                require(total >= resale, "package resale arbitrage: " + path + " x" + quantity);
                require(total <= (long) unit * quantity, "package more expensive than regular: " + path);
            }
        }
        long glassRegular = (long) buy("glass", 1, true) * 64;
        require(ShopPricing.packageTotal("minecraft:glass", 3, 64) < glassRegular,
                "expected explicit glass bundle discount");
        require(ShopPricing.packageTotal("minecraft:oak_log", 4, 64) == 4L * 64,
                "log bundle discount permits resale of four planks per log");
        require(ShopPricing.packageTotal("minecraft:diamond", 120, 64) == 120L * 64,
                "rare item unexpectedly discounted");
    }

    private static void processingDoesNotCreateResaleProfit() {
        require(buy("raw_iron", 3, false) >= ShopPricing.salePrice("minecraft:iron_ingot"), "iron smelting arbitrage");
        require(buy("raw_copper", 3, false) >= ShopPricing.salePrice("minecraft:copper_ingot"), "copper smelting arbitrage");
        require(buy("raw_gold", 3, false) >= ShopPricing.salePrice("minecraft:gold_ingot"), "gold smelting arbitrage");
        require(buy("ancient_debris", 3, false) >= ShopPricing.salePrice("minecraft:netherite_scrap"), "debris smelting arbitrage");
        require(buy("oak_log", 1, true) >= 4L * ShopPricing.salePrice("minecraft:oak_planks"), "log-to-planks arbitrage");
        require(3L * buy("oak_planks", 1, true) >= 6L * ShopPricing.salePrice("minecraft:oak_slab"), "planks-to-slabs arbitrage");
        require(buy("slime_block", 1, true) >= 9L * ShopPricing.salePrice("minecraft:slime_ball"), "slime block-to-balls arbitrage");
        require(ShopPricing.salePrice("minecraft:diamond_block") == 0, "compressed block should not be sale-authorized");
        require(ShopPricing.salePrice("minecraft:emerald_block") == 0, "compressed block should not be sale-authorized");
    }

    private static void technicalItemsStayExcluded() {
        for (String id : new String[]{"spawner", "trial_spawner", "command_block", "structure_block",
                "structure_void", "jigsaw", "debug_stick", "barrier", "bedrock", "end_portal_frame"}) {
            require(ShopPricing.isExcluded(id), "technical item not excluded: " + id);
            require(ShopPricing.salePrice("minecraft:" + id) == 0, "technical item sale-allowed: " + id);
        }
    }

    private static void expectSale(String path, int expected) {
        require(ShopPricing.salePrice("minecraft:" + path) == expected,
                "sale price mismatch: " + path + " expected " + expected);
    }

    private static void expectBuy(String path, int category, int expected, boolean block) {
        require(buy(path, category, block) == expected,
                "buy price mismatch: " + path + " expected " + expected + " got " + buy(path, category, block));
    }

    private static int buy(String path, int category, boolean block) {
        return ShopPricing.purchasePrice("minecraft:" + path, category, path, block);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
