package com.felpz.refugio.ability;

/** Pure skill-tree balance rules, intentionally independent from the Minecraft runtime. */
public final class RefugioAbilityRules {
    private static final int[] RANK_COSTS = {500, 1000, 2000, 3000, 4000};

    private RefugioAbilityRules() { }

    public static int maxRank() { return RANK_COSTS.length; }

    public static int costForRank(int rank) {
        return rank >= 1 && rank <= RANK_COSTS.length ? RANK_COSTS[rank - 1] : -1;
    }

    public static float criticalChance(int rank) { return Math.min(0.50f, boundedRank(rank) * 0.10f); }
    public static float criticalDamageBonus(int rank) { return Math.min(0.50f, boundedRank(rank) * 0.10f); }
    public static float firstCutBonus(int rank) { return Math.min(0.25f, boundedRank(rank) * 0.05f); }
    public static float retaliationBonus(int rank) { return Math.min(0.25f, boundedRank(rank) * 0.05f); }
    public static float hordeVigorBonus(int rank) { return Math.min(0.30f, boundedRank(rank) * 0.06f); }
    public static float swordMasteryBonus(int rank) { return Math.min(0.10f, boundedRank(rank) * 0.02f); }
    public static float axeMasteryBonus(int rank) { return Math.min(0.10f, boundedRank(rank) * 0.02f); }
    public static float bowMasteryBonus(int rank) { return Math.min(0.25f, boundedRank(rank) * 0.05f); }
    public static float steadyAimReduction(int rank) { return Math.min(0.50f, boundedRank(rank) * 0.10f); }
    public static float lightStepReduction(int rank) { return Math.min(0.20f, boundedRank(rank) * 0.04f); }
    public static float firmStanceReduction(int rank) { return Math.min(0.20f, boundedRank(rank) * 0.04f); }
    public static float lastReserveReduction(int rank) { return rank <= 0 ? 0.0f : Math.min(0.70f, 0.20f + boundedRank(rank) * 0.10f); }
    public static long lastReserveCooldownTicks(int rank) {
        return rank <= 0 ? 0L : Math.max(400L, 1200L - (boundedRank(rank) - 1L) * 200L);
    }
    public static double movementSpeedBonus(int rank) { return Math.min(0.30, boundedRank(rank) * 0.06); }
    public static double miningSpeedBonus(int rank) { return Math.min(0.25, boundedRank(rank) * 0.05); }
    public static double anchorKnockbackBonus(int rank) { return Math.min(0.40, boundedRank(rank) * 0.08); }
    public static double superJumpVelocity(int rank) { return 0.62 + boundedRank(rank) * 0.06; }
    public static double doubleJumpVelocity(int rank) { return 0.50 + boundedRank(rank) * 0.04; }
    public static double burstVelocity(int rank) { return Math.min(0.95, 0.55 + boundedRank(rank) * 0.08); }
    public static double areaRadius(int rank) { return 3.0 + boundedRank(rank) * 0.4; }
    public static int areaTargetCount(int rank) { return rank <= 0 ? 0 : Math.min(4, 1 + boundedRank(rank)); }
    public static float areaDamageRatio(int rank) { return rank <= 0 ? 0.0f : 0.10f + boundedRank(rank) * 0.05f; }
    public static long areaCooldownTicks(int rank) { return rank <= 0 ? 0L : Math.max(60L, 160L - boundedRank(rank) * 20L); }
    public static double trackerRadius(int rank) { return 16.0 + boundedRank(rank) * 2.0; }
    public static double collectorRadius(int rank) { return 1.5 + boundedRank(rank) * 0.5; }
    public static double collectorPull(int rank) { return 0.01 + boundedRank(rank) * 0.004; }
    public static float explorationExhaustionReduction(int rank) { return Math.min(0.15f, boundedRank(rank) * 0.03f); }
    public static float preparationSaturation(int rank) { return boundedRank(rank) * 0.5f; }
    public static float logisticsSaveChance(int rank) { return Math.min(0.25f, boundedRank(rank) * 0.05f); }
    public static boolean trackNodeUnlocked(int trackIndex, boolean previousRankOwned) {
        return trackIndex == 0 || previousRankOwned;
    }
    public static boolean canClaimFreeChoice(boolean freeChoiceAlreadyUsed, int currentRank, boolean firstInTrack) {
        return !freeChoiceAlreadyUsed && currentRank == 0 && firstInTrack;
    }

    private static int boundedRank(int rank) { return Math.max(0, Math.min(maxRank(), rank)); }
}
