package com.felpz.refugio.mixin;

import com.felpz.refugio.ability.RefugioAbilityData;
import com.felpz.refugio.ability.RefugioAbilityRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Consumable.class)
public abstract class ConsumableAbilityMixin {
    @Inject(method = "onConsume", at = @At("RETURN"))
    private void refugio$preparedFood(Level level, LivingEntity user, ItemStack stack,
                                      CallbackInfoReturnable<ItemStack> callback) {
        if (level.isClientSide() || !(user instanceof ServerPlayer player)
                || stack.get(DataComponents.FOOD) == null
                || !RefugioAbilityData.isActive(player, "preparation")) return;
        int rank = RefugioAbilityData.rank(player, "preparation");
        if (rank <= 0) return;
        FoodData food = player.getFoodData();
        food.setSaturation(Math.min(food.getFoodLevel(), food.getSaturationLevel()
                + RefugioAbilityRules.preparationSaturation(rank)));
    }
}
