package com.example.basketball.mixin;

import com.example.basketball.pose.PoseState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Движение всего тела только там, где оно нужно: оборот спина и падение после анкл-брейкера.
 * При дриблинге и обычных финтах корпус не качается и не наклоняется.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerTransformsMixin {
    @Inject(method = "setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFF)V",
            at = @At("TAIL"))
    private void basketball$transforms(AbstractClientPlayerEntity player, MatrixStack m, float animationProgress,
                                       float bodyYaw, float tickDelta, CallbackInfo ci) {
        PoseState.Timed t = PoseState.timed(player.getId());
        if (t == null) return;
        long now = player.getWorld().getTime();
        float p = ((now - t.start()) - 1 + tickDelta) / PoseState.duration(t.pose());
        if (p < 0f || p > 1f) return;

        if (t.pose() == PoseState.SPIN) {
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(360f * p));
        } else if (t.pose() == PoseState.STUMBLE) {
            double s = Math.sin(Math.PI * p);
            m.translate(0.0, -0.25 * s, 0.0);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) (75.0 * s)));
        } else if (PoseState.isDunk(t.pose()) && p < 0.30f) {
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-8f * (p / 0.30f)));
        }
    }
}
