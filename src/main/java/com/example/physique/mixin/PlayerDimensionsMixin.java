package com.example.physique.mixin;

import com.example.physique.Physique;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Хитбокс и высота глаз по росту: высота растёт пропорционально, ширина слабее. */
@Mixin(PlayerEntity.class)
public abstract class PlayerDimensionsMixin {
    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void physique$dimensions(EntityPose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        Physique.Body b = Physique.get(self.getUuid());
        float s = b.scale();
        if (s == 1.0f && b.widthFactor() == 1.0f) return;
        EntityDimensions base = cir.getReturnValue();
        float wf = (float) Math.max(0.8, Math.min(1.25, Math.sqrt(s) * b.widthFactor()));
        cir.setReturnValue(EntityDimensions.changing(base.width * wf, base.height * s));
    }

    @Inject(method = "getActiveEyeHeight", at = @At("RETURN"), cancellable = true)
    private void physique$eyeHeight(EntityPose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        float s = Physique.get(self.getUuid()).scale();
        if (s != 1.0f) cir.setReturnValue(cir.getReturnValue() * s);
    }
}
