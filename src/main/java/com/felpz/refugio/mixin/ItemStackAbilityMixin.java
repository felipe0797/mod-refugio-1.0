package com.felpz.refugio.mixin;

import com.felpz.refugio.ability.RefugioAbilityData;
import com.felpz.refugio.ability.RefugioAbilityRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class ItemStackAbilityMixin {
    @Unique
    private boolean refugio$reducingDurabilityDamage;

    @Inject(method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;)V",
            at = @At("HEAD"), cancellable = true)
    private void refugio$saveHandDurability(int amount, LivingEntity user, InteractionHand hand, CallbackInfo callback) {
        if (!refugio$shouldSave(amount, user)) return;
        if (amount > 1) {
            refugio$reducingDurabilityDamage = true;
            try {
                ((ItemStack) (Object) this).hurtAndBreak(amount - 1, user, hand);
            } finally {
                refugio$reducingDurabilityDamage = false;
            }
        }
        callback.cancel();
    }

    @Inject(method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V",
            at = @At("HEAD"), cancellable = true)
    private void refugio$saveSlotDurability(int amount, LivingEntity user, EquipmentSlot slot, CallbackInfo callback) {
        if (!refugio$shouldSave(amount, user)) return;
        if (amount > 1) {
            refugio$reducingDurabilityDamage = true;
            try {
                ((ItemStack) (Object) this).hurtAndBreak(amount - 1, user, slot);
            } finally {
                refugio$reducingDurabilityDamage = false;
            }
        }
        callback.cancel();
    }

    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true)
    private void refugio$saveServerDurability(int amount, ServerLevel level, ServerPlayer user,
                                               Consumer<Item> onBroken, CallbackInfo callback) {
        if (!refugio$shouldSave(amount, user)) return;
        if (amount > 1) {
            refugio$reducingDurabilityDamage = true;
            try {
                ((ItemStack) (Object) this).hurtAndBreak(amount - 1, level, user, onBroken);
            } finally {
                refugio$reducingDurabilityDamage = false;
            }
        }
        callback.cancel();
    }

    @Unique
    private boolean refugio$shouldSave(int amount, LivingEntity user) {
        if (refugio$reducingDurabilityDamage || amount <= 0 || !(user instanceof ServerPlayer player)
                || !RefugioAbilityData.isActive(player, "logistics")) return false;
        int rank = RefugioAbilityData.rank(player, "logistics");
        return rank > 0 && player.getRandom().nextFloat() < RefugioAbilityRules.logisticsSaveChance(rank);
    }
}
