package com.example.physique.mixin;

import com.example.basketball.item.SneakerStats;
import com.example.physique.Physique;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingJumpMixin {
    @Inject(method = "getJumpVelocity", at = @At("RETURN"), cancellable = true)
    private void physique$jump(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof PlayerEntity p) {
            cir.setReturnValue(cir.getReturnValue() * (float) (Physique.get(p.getUuid()).jump() * SneakerStats.jump(p)));
        }
    }
}
