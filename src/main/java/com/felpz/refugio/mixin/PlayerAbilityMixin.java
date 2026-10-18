package com.felpz.refugio.mixin;

import com.felpz.refugio.ability.RefugioAbilityData;
import com.felpz.refugio.ability.RefugioAbilityRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerAbilityMixin {
    @Unique
    private boolean refugio$attackTargetIsEnemy;
    @Unique
    private int refugio$firstCutRank;
    @Unique
    private int refugio$retaliationRank;

    @Inject(method = "attack", at = @At("HEAD"))
    private void refugio$resetCriticalState(Entity target, CallbackInfo callback) {
        refugio$attackTargetIsEnemy = target instanceof Enemy;
        refugio$firstCutRank = 0;
        refugio$retaliationRank = 0;
        if (refugio$attackTargetIsEnemy && (Object) this instanceof ServerPlayer player) {
            refugio$firstCutRank = RefugioAbilityData.beginAttack(player);
            refugio$retaliationRank = RefugioAbilityData.retaliationRankForAttack(player);
        }
    }

    @Inject(method = "attack", at = @At("TAIL"))
    private void refugio$clearCriticalState(Entity target, CallbackInfo callback) {
        refugio$attackTargetIsEnemy = false;
        refugio$firstCutRank = 0;
        refugio$retaliationRank = 0;
    }

    @ModifyVariable(method = "attack", at = @At(value = "STORE", ordinal = 0), index = 2)
    private float refugio$applyCombatBonuses(float baseDamage) {
        if (!refugio$attackTargetIsEnemy || !((Object) this instanceof ServerPlayer player)) return baseDamage;
        float bonus = RefugioAbilityData.hordeVigorBonus(player);
        if (refugio$firstCutRank > 0) bonus += RefugioAbilityRules.firstCutBonus(refugio$firstCutRank);
        if (refugio$retaliationRank > 0) bonus += RefugioAbilityRules.retaliationBonus(refugio$retaliationRank);
        if (player.getMainHandItem().is(ItemTags.SWORDS)
                && RefugioAbilityData.isActive(player, "sword_mastery")) {
            bonus += RefugioAbilityRules.swordMasteryBonus(RefugioAbilityData.rank(player, "sword_mastery"));
        }
        if (player.getMainHandItem().is(ItemTags.AXES)
                && RefugioAbilityData.isActive(player, "axe_mastery")) {
            bonus += RefugioAbilityRules.axeMasteryBonus(RefugioAbilityData.rank(player, "axe_mastery"));
        }
        return baseDamage * (1.0f + bonus);
    }

    @ModifyVariable(method = "attack", at = @At("STORE"), index = 9)
    private boolean refugio$rollCriticalChance(boolean critical) {
        if (critical || !refugio$attackTargetIsEnemy || !((Object) this instanceof ServerPlayer player)) return critical;
        String skillId = RefugioAbilityData.criticalChanceSkillId(player.getMainHandItem());
        if (skillId == null || !RefugioAbilityData.isActive(player, skillId)) return false;
        int rank = RefugioAbilityData.rank(player, skillId);
        return rank > 0 && player.getRandom().nextFloat() < RefugioAbilityRules.criticalChance(rank);
    }

    @ModifyConstant(method = "attack", constant = @Constant(floatValue = 1.5f))
    private float refugio$criticalDamage(float vanillaMultiplier) {
        if (!refugio$attackTargetIsEnemy || !((Object) this instanceof ServerPlayer player)) return vanillaMultiplier;
        String skillId = RefugioAbilityData.criticalDamageSkillId(player.getMainHandItem());
        if (skillId == null || !RefugioAbilityData.isActive(player, skillId)) return vanillaMultiplier;
        int rank = RefugioAbilityData.rank(player, skillId);
        return vanillaMultiplier * (1.0f + RefugioAbilityRules.criticalDamageBonus(rank));
    }

    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true, index = 1)
    private float refugio$reduceExplorationExhaustion(float exhaustion) {
        if (exhaustion <= 0.0f || !((Object) this instanceof ServerPlayer player)
                || !RefugioAbilityData.isActive(player, "exploration")) return exhaustion;
        int rank = RefugioAbilityData.rank(player, "exploration");
        return exhaustion * (1.0f - RefugioAbilityRules.explorationExhaustionReduction(rank));
    }
}
