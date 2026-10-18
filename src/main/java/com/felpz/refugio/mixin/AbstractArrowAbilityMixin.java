package com.felpz.refugio.mixin;

import com.felpz.refugio.ability.RefugioAbilityData;
import com.felpz.refugio.ability.RefugioAbilityRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowAbilityMixin {
    @Shadow
    private double baseDamage;

    @Unique
    private double refugio$originalArrowDamage;
    @Unique
    private boolean refugio$boostedArrowDamage;

    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void refugio$boostArrowDamage(EntityHitResult hit, CallbackInfo callback) {
        refugio$boostedArrowDamage = false;
        if (!(hit.getEntity() instanceof Enemy)
                || !((Object) this instanceof AbstractArrow arrow)
                || !(arrow.getOwner() instanceof ServerPlayer player)
                || !arrow.getWeaponItem().is(ItemTags.BOW_ENCHANTABLE)) return;

        float bonus = RefugioAbilityData.hordeVigorBonus(player);
        if (RefugioAbilityData.isActive(player, "bow_mastery")) {
            bonus += RefugioAbilityRules.bowMasteryBonus(RefugioAbilityData.rank(player, "bow_mastery"));
        }
        int firstShotRank = RefugioAbilityData.beginBowFirstShot(player);
        if (firstShotRank > 0) bonus += RefugioAbilityRules.firstCutBonus(firstShotRank);
        int retaliationRank = RefugioAbilityData.retaliationRankForAttack(player);
        if (retaliationRank > 0) bonus += RefugioAbilityRules.retaliationBonus(retaliationRank);

        boolean critical = arrow.isCritArrow();
        String chanceId = RefugioAbilityData.criticalChanceSkillId(arrow.getWeaponItem());
        if (!critical && chanceId != null && RefugioAbilityData.isActive(player, chanceId)) {
            int chanceRank = RefugioAbilityData.rank(player, chanceId);
            critical = chanceRank > 0 && player.getRandom().nextFloat()
                    < RefugioAbilityRules.criticalChance(chanceRank);
        }
        double criticalMultiplier = 1.0;
        if (critical) {
            if (!arrow.isCritArrow()) criticalMultiplier = 1.5;
            String damageId = RefugioAbilityData.criticalDamageSkillId(arrow.getWeaponItem());
            if (damageId != null && RefugioAbilityData.isActive(player, damageId)) {
                criticalMultiplier *= 1.0 + RefugioAbilityRules.criticalDamageBonus(
                        RefugioAbilityData.rank(player, damageId));
            }
        }
        if (bonus <= 0.0f && criticalMultiplier == 1.0) return;
        refugio$originalArrowDamage = baseDamage;
        baseDamage *= 1.0 + bonus;
        baseDamage *= criticalMultiplier;
        refugio$boostedArrowDamage = true;
    }

    @Inject(method = "onHitEntity", at = @At("RETURN"))
    private void refugio$restoreArrowDamage(EntityHitResult hit, CallbackInfo callback) {
        if (!refugio$boostedArrowDamage) return;
        baseDamage = refugio$originalArrowDamage;
        refugio$boostedArrowDamage = false;
    }
}
