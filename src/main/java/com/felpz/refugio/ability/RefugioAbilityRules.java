package com.felpz.refugio.ability;

/** Pure skill-tree balance rules, intentionally independent from the Minecraft runtime. */
public final class RefugioAbilityRules {
    private static final int[] RANK_COSTS = {500, 1000, 2000, 3000, 4000};

    private RefugioAbilityRules() { }

    public static int maxRank() { return RANK_COSTS.length; }

    public static int maxRank(String skillId) { return "vitality_health".equals(skillId) ? 10 : maxRank(); }

    public static int costForRank(int rank) {
        return rank >= 1 && rank <= RANK_COSTS.length ? RANK_COSTS[rank - 1] : -1;
    }

    public static int costForRank(String skillId, int rank) {
        if ("vitality_health".equals(skillId)) return rank >= 1 && rank <= 10 ? rank * 500 : -1;
        return costForRank(rank);
    }

    public static float criticalChance(int rank) { return Math.min(0.50f, boundedRank(rank) * 0.10f); }
    public static float criticalDamageBonus(int rank) { return Math.min(1.00f, boundedRank(rank) * 0.20f); }
    public static float firstCutBonus(int rank) { return Math.min(0.75f, boundedRank(rank) * 0.15f); }
    public static long firstCutQuietTicks() { return 100L; }
    public static float retaliationBonus(int rank) { return Math.min(0.75f, boundedRank(rank) * 0.15f); }
    public static long retaliationReadyTicks() { return 80L; }
    public static long retaliationCooldownTicks() { return 200L; }
    public static float hordeVigorBonus(int rank) { return Math.min(0.30f, boundedRank(rank) * 0.06f); }
    public static long hordeVigorDurationTicks() { return 100L; }
    public static float swordMasteryBonus(int rank) { return Math.min(0.50f, boundedRank(rank) * 0.10f); }
    public static float axeMasteryBonus(int rank) { return Math.min(0.50f, boundedRank(rank) * 0.10f); }
    public static float bowMasteryBonus(int rank) { return Math.min(0.50f, boundedRank(rank) * 0.10f); }
    public static float steadyAimReduction(int rank) { return Math.min(0.50f, boundedRank(rank) * 0.10f); }
    public static float lightStepReduction(int rank) { return Math.min(0.50f, boundedRank(rank) * 0.10f); }
    public static float firmStanceReduction(int rank) { return Math.min(0.25f, boundedRank(rank) * 0.05f); }
    public static float lastReserveHealthThresholdRatio() { return 0.20f; }
    public static float lastReserveReduction(int rank) {
        return rank <= 0 ? 0.0f : Math.min(0.50f, 0.10f + boundedRank(rank) * 0.10f);
    }
    public static long lastReserveCooldownTicks(int rank) {
        return rank <= 0 ? 0L : Math.max(400L, 1200L - (boundedRank(rank) - 1L) * 200L);
    }
    public static double movementSpeedBonus(int rank) { return Math.min(0.30, boundedRank(rank) * 0.07); }
    public static double miningSpeedBonus(int rank) { return Math.min(0.30, boundedRank(rank) * 0.06); }
    public static double anchorKnockbackBonus(int rank) { return Math.min(0.40, boundedRank(rank) * 0.08); }
    public static double vitalityMaxHealthBonus(int rank) { return Math.max(0, Math.min(10, rank)) * 2.0; }
    public static float naturalRegenerationTriggerHealth(int rank) {
        return switch (boundedRank(rank)) {
            case 1, 2 -> 6.0f;
            case 3 -> 8.0f;
            case 4 -> 10.0f;
            case 5 -> 12.0f;
            default -> 0.0f;
        };
    }
    public static float naturalRegenerationTargetHealth(int rank) {
        return switch (boundedRank(rank)) {
            case 1 -> 10.0f;
            case 2, 3 -> 12.0f;
            case 4 -> 14.0f;
            case 5 -> 16.0f;
            default -> 0.0f;
        };
    }
    public static long naturalRegenerationOutOfCombatTicks() { return 200L; }
    public static long naturalRegenerationIntervalTicks() { return 40L; }
    public static float naturalRegenerationAmount() { return 1.0f; }
    public static float vitalityOnKillHealRatio(int rank) { return Math.min(0.05f, boundedRank(rank) * 0.01f); }
    public static double superJumpVelocity(int rank) { return 0.50 + boundedRank(rank) * 0.03; }
    public static double doubleJumpVelocity(int rank) { return 0.50 + boundedRank(rank) * 0.04; }
    public static double burstVelocity(int rank) { return boundedRank(rank) * 1.0; }
    public static double burstImpulse(int rank) { return burstVelocity(rank) * 0.20; }
    public static int burstMaxCharges(int rank) { return boundedRank(rank) >= 4 ? 2 : (boundedRank(rank) > 0 ? 1 : 0); }
    public static long burstCooldownTicks(int rank) {
        return switch (boundedRank(rank)) {
            case 1 -> 600L;
            case 2 -> 400L;
            case 3, 4 -> 200L;
            case 5 -> 100L;
            default -> 0L;
        };
    }
    public static double areaRadius(int rank) {
        return switch (boundedRank(rank)) {
            case 1, 2, 3 -> 3.0;
            case 4 -> 4.0;
            case 5 -> 5.0;
            default -> 0.0;
        };
    }
    public static int areaTargetCount(int rank) { return boundedRank(rank); }
    public static float areaBaseDamage(int rank) { return boundedRank(rank) * 5.0f; }
    public static float areaDamageRatio(int rank) { return areaBaseDamage(rank); }
    public static long areaCooldownTicks(int rank) {
        return switch (boundedRank(rank)) {
            case 1 -> 1200L;
            case 2 -> 1000L;
            case 3, 4 -> 800L;
            case 5 -> 600L;
            default -> 0L;
        };
    }
    public static double collectorRadius(int rank) { return rank <= 0 ? 0.0 : 1.0 + boundedRank(rank) * 1.0; }
    public static double collectorPull(int rank) { return 0.01 + boundedRank(rank) * 0.004; }
    public static float explorationExhaustionReduction(int rank) { return Math.min(0.30f, boundedRank(rank) * 0.06f); }
    public static float preparationSaturation(int rank) { return boundedRank(rank) * 0.5f; }
    public static float logisticsSaveChance(int rank) { return Math.min(0.40f, boundedRank(rank) * 0.08f); }
    public static boolean trackNodeUnlocked(int trackIndex, boolean previousRankOwned) {
        return trackIndex == 0 || previousRankOwned;
    }
    public static boolean canClaimFreeChoice(boolean freeChoiceAlreadyUsed, int currentRank, boolean firstInTrack) {
        return !freeChoiceAlreadyUsed && currentRank == 0 && firstInTrack;
    }

    private static int boundedRank(int rank) { return Math.max(0, Math.min(maxRank(), rank)); }
}
