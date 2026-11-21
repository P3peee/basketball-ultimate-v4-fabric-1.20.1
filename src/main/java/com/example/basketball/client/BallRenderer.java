package com.example.basketball.client;

import com.example.basketball.entity.BasketballEntity;
import com.example.basketball.item.ModItems;
import com.example.basketball.pose.PoseState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Объёмный мяч: вращение в полёте, сжатие при ударе об пол во время дриблинга. */
public class BallRenderer extends EntityRenderer<BasketballEntity> {
    private static final ItemStack STACK = new ItemStack(ModItems.BASKETBALL);

    public BallRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.12f;
    }

    @Override
    public Identifier getTexture(BasketballEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }

    @Override
    public void render(BasketballEntity e, float yaw, float tickDelta, MatrixStack m,
                       VertexConsumerProvider vcp, int light) {
        m.push();
        float t = e.age + tickDelta;
        float sy = 1f, sxz = 1f, down = 0f;

        if (e.isDribbling() && !e.isCarry()) {
            float b = PoseState.bounce(e.getWorld().getTime() - 1 + tickDelta);
            float c = Math.max(0f, (0.12f - b) / 0.12f);   // 1 - в момент удара об пол
            sy = 1f - 0.15f * c;
            sxz = 1f + 0.08f * c;
            down = -0.04f * c;
        }

        m.translate(0.0, 0.25 + down, 0.0);
        if (e.isCarry()) {
            // без вращения
        } else if (e.isDribbling()) {
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(t * 12f));
        } else if (e.getVelocity().lengthSquared() > 0.003) {
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(t * 28f));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(t * 15f));
        }
        m.scale(sxz, sy, sxz);

        MinecraftClient.getInstance().getItemRenderer().renderItem(STACK, ModelTransformationMode.GROUND,
                light, OverlayTexture.DEFAULT_UV, m, vcp, e.getWorld(), e.getId());
        m.pop();
        super.render(e, yaw, tickDelta, m, vcp, light);
    }
}
