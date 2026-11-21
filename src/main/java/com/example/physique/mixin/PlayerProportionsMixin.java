package com.example.physique.mixin;

import com.example.physique.Physique;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Пропорции тела: плечи, руки, ноги меняются по отдельности, а не просто увеличиваются. */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerProportionsMixin<T extends LivingEntity> extends BipedEntityModel<T> {
    @Shadow @Final public ModelPart leftSleeve;
    @Shadow @Final public ModelPart rightSleeve;
    @Shadow @Final public ModelPart leftPants;
    @Shadow @Final public ModelPart rightPants;
    @Shadow @Final public ModelPart jacket;

    public PlayerProportionsMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void physique$proportions(LivingEntity entity, float limbAngle, float limbDistance,
                                      float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        Physique.Body b = entity instanceof PlayerEntity ? Physique.get(entity.getUuid()) : Physique.Body.DEFAULT;
        float tx = b.torsoX(), tz = b.torsoZ(), axz = b.armXZ(), ay = b.armY(), lxz = b.legXZ();

        scale(this.body, tx, 1f, tz);
        scale(this.jacket, tx, 1f, tz);
        scale(this.rightArm, axz, ay, axz);
        scale(this.leftArm, axz, ay, axz);
        scale(this.rightSleeve, axz, ay, axz);
        scale(this.leftSleeve, axz, ay, axz);
        scale(this.rightLeg, lxz, 1f, lxz);
        scale(this.leftLeg, lxz, 1f, lxz);
        scale(this.rightPants, lxz, 1f, lxz);
        scale(this.leftPants, lxz, 1f, lxz);

        // руки и ноги сдвигаются вместе с шириной корпуса
        this.rightArm.pivotX = -5.0f * tx;
        this.leftArm.pivotX = 5.0f * tx;
        this.rightLeg.pivotX = -1.9f * lxz;
        this.leftLeg.pivotX = 1.9f * lxz;

        this.rightSleeve.copyTransform(this.rightArm);
        this.leftSleeve.copyTransform(this.leftArm);
        this.rightPants.copyTransform(this.rightLeg);
        this.leftPants.copyTransform(this.leftLeg);
        this.jacket.copyTransform(this.body);

        scale(this.rightSleeve, axz, ay, axz);
        scale(this.leftSleeve, axz, ay, axz);
        scale(this.rightPants, lxz, 1f, lxz);
        scale(this.leftPants, lxz, 1f, lxz);
        scale(this.jacket, tx, 1f, tz);
    }

    private static void scale(ModelPart part, float x, float y, float z) {
        part.xScale = x;
        part.yScale = y;
        part.zScale = z;
    }
}
