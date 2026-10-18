package com.felpz.refugio.mixin;

import com.felpz.refugio.ability.RefugioAbilityData;
import com.felpz.refugio.ability.RefugioAbilityRules;
import com.felpz.refugio.npc.RefugioNPCs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityAbilityMixin {
    @Unique
    private boolean refugio$applyingReducedDamage;

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void refugio$mitigateSkillDamage(ServerLevel level, DamageSource source, float amount,
                                             CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (RefugioNPCs.isRefugeNpc(self)
                && !source.is(DamageTypes.GENERIC_KILL)
                && !source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            cir.setReturnValue(false);
            return;
        }
        if (refugio$applyingReducedDamage || !(self instanceof ServerPlayer player) || amount <= 0.0f) return;

        float adjustedAmount = amount;
        if (source.is(DamageTypes.FALL)) {
            adjustedAmount = RefugioAbilityData.adjustTrackedJumpFallDamage(player, adjustedAmount);
            if (adjustedAmount <= 0.0f) {
                cir.setReturnValue(false);
                return;
            }
        }

        float damageMultiplier = 1.0f;
        if (source.is(DamageTypes.FALL) && RefugioAbilityData.isActive(player, "light_step")) {
            float reduction = RefugioAbilityRules.lightStepReduction(RefugioAbilityData.rank(player, "light_step"));
            damageMultiplier *= 1.0f - reduction;
        } else if (source.getEntity() instanceof Enemy
                && RefugioAbilityData.isActive(player, "firm_stance")) {
            float reduction = RefugioAbilityRules.firmStanceReduction(RefugioAbilityData.rank(player, "firm_stance"));
            damageMultiplier *= 1.0f - reduction;
        }
        float reserveReduction = (source.getEntity() instanceof Enemy)
                ? RefugioAbilityData.tryLastReserve(player, adjustedAmount * damageMultiplier)
                : 0.0f;
        damageMultiplier *= 1.0f - reserveReduction;
        if (damageMultiplier >= 1.0f && adjustedAmount == amount) return;

        float finalDamage = adjustedAmount * damageMultiplier;
        if (reserveReduction > 0.0f && player.getHealth() - finalDamage <= 0.0f) {
            finalDamage = Math.max(0.0f, player.getHealth() - 1.0f);
        }

        refugio$applyingReducedDamage = true;
        try {
            cir.setReturnValue(player.hurtServer(level, source, finalDamage));
        } finally {
            refugio$applyingReducedDamage = false;
        }
    }
}
