package com.example.basketball.mixin;

import com.example.basketball.pose.PoseState;
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

/**
 * Анимации в духе майнкрафта: простые чёткие махи руками и ногами, без покачивания корпуса.
 * При дриблинге двигаются только руки.
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> extends BipedEntityModel<T> {
    @Shadow @Final public ModelPart leftSleeve;
    @Shadow @Final public ModelPart rightSleeve;
    @Shadow @Final public ModelPart leftPants;
    @Shadow @Final public ModelPart rightPants;

    public PlayerModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void basketball$poses(LivingEntity entity, float limbAngle, float limbDistance,
                                  float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity)) return;

        long now = entity.getWorld().getTime();
        float delta = animationProgress - (float) Math.floor(animationProgress);
        int id = entity.getId();
        boolean changed = false;

        PoseState.Timed timed = PoseState.timed(id);
        if (timed != null) {
            float p = ((now - timed.start()) - 1 + delta) / PoseState.duration(timed.pose());
            if (p >= 0f && p <= 1f) changed = applyTimed(timed.pose(), p);
        }
        if (!changed && PoseState.dribbling(id, now)) {
            dribbleArms(now, delta, id);
            changed = true;
        }
        // ноги могли быть изменены позой даже если руки остались от дриблинга
        this.leftSleeve.copyTransform(this.leftArm);
        this.rightSleeve.copyTransform(this.rightArm);
        this.leftPants.copyTransform(this.leftLeg);
        this.rightPants.copyTransform(this.rightLeg);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp01(float k) { return Math.max(0f, Math.min(1f, k)); }

    // ---------- дриблинг: двигаются только руки ----------
    private void dribbleArms(long now, float delta, int id) {
        double tt = (now - 1) + delta;
        float b = PoseState.bounce(tt + 1.0);                       // рука чуть опережает мяч
        float wR = clamp01((PoseState.dribbleSide(id) + 1f) / 2f);  // 1 - мяч в правой руке
        float wL = 1f - wR;

        float push = -0.35f - 0.55f * b;                            // ведущая рука качается вниз-вверх
        float guard = -1.0f;                                        // вторая рука прикрывает

        this.rightArm.pitch = lerp(guard, push, wR);
        this.rightArm.yaw = lerp(-0.4f, -0.15f, wR);
        this.leftArm.pitch = lerp(guard, push, wL);
        this.leftArm.yaw = lerp(0.4f, 0.15f, wL);
    }

    // ---------- позы по времени ----------
    /** true - поза управляет руками; false - руки остаются как при дриблинге. */
    private boolean applyTimed(int pose, float p) {
        switch (pose) {
            case PoseState.SHOOT -> {
                // руки вверх, затем кисть вперёд
                float up = p < 0.45f ? p / 0.45f : 1f;
                this.rightArm.pitch = p < 0.45f ? lerp(-1.4f, -3.0f, up) : lerp(-3.0f, -2.2f, (p - 0.45f) / 0.55f);
                this.leftArm.pitch = p < 0.45f ? lerp(-1.4f, -2.4f, up) : lerp(-2.4f, -0.3f, (p - 0.45f) / 0.55f);
                this.rightArm.yaw = -0.1f;
                this.leftArm.yaw = 0.3f;
                return true;
            }
            case PoseState.PASS -> {
                float s = (float) Math.sin(p * Math.PI);
                this.rightArm.pitch = -1.0f - 0.55f * s;
                this.leftArm.pitch = -1.0f - 0.55f * s;
                this.rightArm.yaw = -0.15f;
                this.leftArm.yaw = 0.15f;
                return true;
            }
            case PoseState.BLOCK -> {
                this.rightArm.pitch = -3.1f;
                this.leftArm.pitch = -3.1f;
                this.rightArm.roll = 0.12f;
                this.leftArm.roll = -0.12f;
                return true;
            }
            case PoseState.STEAL -> {
                this.rightArm.pitch = -1.4f - 0.4f * (float) Math.sin(p * Math.PI);
                this.rightArm.yaw = lerp(-0.9f, 0.9f, p);
                this.leftArm.pitch = -0.5f;
                this.leftArm.yaw = 0.4f;
                return true;
            }
            case PoseState.CATCH -> {
                this.rightArm.pitch = -1.3f + 0.3f * p;
                this.leftArm.pitch = -1.3f + 0.3f * p;
                this.rightArm.yaw = -0.2f;
                this.leftArm.yaw = 0.2f;
                return true;
            }
            case PoseState.SPIN -> {
                this.rightArm.pitch = -1.1f;
                this.leftArm.pitch = -1.1f;
                this.rightArm.yaw = -0.5f;
                this.leftArm.yaw = 0.5f;
                return true;
            }
            case PoseState.STUMBLE -> {
                float flail = (float) Math.sin(p * 14.0);
                this.rightArm.pitch = -2.2f + 0.5f * flail;
                this.leftArm.pitch = -2.2f - 0.5f * flail;
                this.rightArm.roll = 0.6f;
                this.leftArm.roll = -0.6f;
                return true;
            }

            // ----- финты -----
            case PoseState.BTL -> {
                // между ног: нога поднимается, руки продолжают дриблинг
                this.rightLeg.pitch = -1.0f * (float) Math.sin(p * Math.PI);
                return false;
            }
            case PoseState.BACK -> {
                // за спиной: обе руки резко назад и обратно
                float s = (float) Math.sin(p * Math.PI);
                this.rightArm.pitch = lerp(-0.6f, 0.7f, s);
                this.leftArm.pitch = lerp(-0.6f, 0.7f, s);
                this.rightArm.yaw = -0.3f;
                this.leftArm.yaw = 0.3f;
                return true;
            }
            case PoseState.HESI -> {
                // гезитейшен: мяч к груди, короткий шаг
                this.rightArm.pitch = -1.45f;
                this.leftArm.pitch = -1.45f;
                this.rightArm.yaw = -0.3f;
                this.leftArm.yaw = 0.3f;
                this.rightLeg.pitch = p < 0.5f ? -0.6f : 0f;
                return true;
            }
            case PoseState.FAKE -> {
                // финт броском: руки вверх и обратно
                float k = p < 0.5f ? p / 0.5f : 1f - (p - 0.5f) / 0.5f;
                this.rightArm.pitch = lerp(-1.0f, -2.8f, k);
                this.leftArm.pitch = lerp(-1.0f, -2.4f, k);
                this.rightArm.yaw = -0.1f;
                this.leftArm.yaw = 0.2f;
                return true;
            }
            case PoseState.EURO -> {
                // евростеп: два широких шага, мяч высоко
                this.rightArm.pitch = -1.4f;
                this.leftArm.pitch = -1.4f;
                this.rightLeg.pitch = p < 0.5f ? -0.9f : 0f;
                this.leftLeg.pitch = p >= 0.5f ? -0.9f : 0f;
                return true;
            }

            // ----- дэнки -----
            case PoseState.DUNK -> { twoHandDunk(p); return true; }
            case PoseState.DUNK_TOMA -> { tomahawkDunk(p); return true; }
            case PoseState.DUNK_WIND -> { windmillDunk(p); return true; }
            default -> {
                return false;
            }
        }
    }

    // ---------- дэнки: замах (0-0.30), удар (0.30-0.42), висим на кольце (до 0.75), отпускаем ----------
    private void legTuck(float p, float right, float left) {
        float k = p < 0.30f ? clamp01(p / 0.30f) : p < 0.75f ? 1f : 1f - clamp01((p - 0.75f) / 0.25f);
        this.rightLeg.pitch = right * k;
        this.leftLeg.pitch = left * k;
    }

    private void twoHandDunk(float p) {
        float r;
        if (p < 0.30f) r = lerp(-1.0f, -3.2f, p / 0.30f);
        else if (p < 0.42f) r = lerp(-3.2f, -2.7f, (p - 0.30f) / 0.12f);
        else if (p < 0.75f) r = -2.75f;
        else r = lerp(-2.75f, -0.4f, (p - 0.75f) / 0.25f);
        this.rightArm.pitch = r;
        this.leftArm.pitch = r;
        this.rightArm.yaw = -0.1f;
        this.leftArm.yaw = 0.1f;
        legTuck(p, -0.9f, -0.5f);
    }

    private void tomahawkDunk(float p) {
        float r, l;
        if (p < 0.30f) {
            r = lerp(-1.0f, -3.7f, p / 0.30f);
            l = lerp(-1.0f, -1.8f, p / 0.30f);
        } else if (p < 0.40f) {
            r = lerp(-3.7f, -2.5f, (p - 0.30f) / 0.10f);
            l = -1.8f;
        } else if (p < 0.75f) {
            r = -2.8f;
            l = -1.8f;
        } else {
            float k = (p - 0.75f) / 0.25f;
            r = lerp(-2.8f, -0.4f, k);
            l = lerp(-1.8f, -0.4f, k);
        }
        this.rightArm.pitch = r;
        this.leftArm.pitch = l;
        this.rightArm.yaw = -0.1f;
        this.leftArm.roll = p < 0.75f ? -0.5f : 0f;
        legTuck(p, -0.8f, 0.4f);
    }

    private void windmillDunk(float p) {
        float r;
        if (p < 0.38f) r = lerp(-0.5f, -9.18f, p / 0.38f);   // полтора оборота руки, в конце над головой
        else if (p < 0.75f) r = -2.9f;
        else r = lerp(-2.9f, -0.4f, (p - 0.75f) / 0.25f);
        this.rightArm.pitch = r;
        this.leftArm.pitch = p < 0.75f ? -1.3f : lerp(-1.3f, -0.4f, (p - 0.75f) / 0.25f);
        this.leftArm.roll = p < 0.75f ? -0.7f : 0f;
        legTuck(p, -0.7f, 0.6f);
    }
}
