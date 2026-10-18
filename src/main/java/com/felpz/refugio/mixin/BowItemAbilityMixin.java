package com.felpz.refugio.mixin;

import com.felpz.refugio.ability.RefugioAbilityData;
import com.felpz.refugio.ability.RefugioAbilityRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BowItem.class)
public abstract class BowItemAbilityMixin {
    @Unique
    private static final ThreadLocal<Integer> refugio$aimRank = ThreadLocal.withInitial(() -> 0);

    @Inject(method = "releaseUsing", at = @At("HEAD"))
    private void refugio$captureAimRank(ItemStack bow, Level level, LivingEntity user, int remainingUseTicks,
                                        CallbackInfoReturnable<Boolean> callback) {
        int rank = user instanceof ServerPlayer player && RefugioAbilityData.isActive(player, "steady_aim")
                ? RefugioAbilityData.rank(player, "steady_aim") : 0;
        refugio$aimRank.set(Math.max(0, Math.min(5, rank)));
    }

    @ModifyConstant(method = "releaseUsing", constant = @Constant(floatValue = 1.0f, ordinal = 0))
    private float refugio$reduceArrowSpread(float vanillaInaccuracy) {
        return vanillaInaccuracy * (1.0f - RefugioAbilityRules.steadyAimReduction(refugio$aimRank.get()));
    }

    @Inject(method = "releaseUsing", at = @At("TAIL"))
    private void refugio$clearAimRank(ItemStack bow, Level level, LivingEntity user, int remainingUseTicks,
                                     CallbackInfoReturnable<Boolean> callback) {
        refugio$aimRank.remove();
    }
}
