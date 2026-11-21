package com.example.basketball.client;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/** Рисует объёмный кроссовок на каждой ноге вместо обычного ботинка. */
public class SneakerRenderer implements ArmorRenderer {
    private final Identifier texture;
    private ModelPart root;

    public SneakerRenderer(String name) {
        this.texture = new Identifier("basketball", "textures/entity/sneakers_" + name + ".png");
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, ItemStack stack,
                       LivingEntity entity, EquipmentSlot slot, int light, BipedEntityModel<LivingEntity> contextModel) {
        if (slot != EquipmentSlot.FEET) return;
        if (root == null) root = MinecraftClient.getInstance().getEntityModelLoader().getModelPart(SneakerModel.LAYER);
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getArmorCutoutNoCull(texture));
        draw(matrices, vc, light, contextModel.leftLeg);
        draw(matrices, vc, light, contextModel.rightLeg);
    }

    private void draw(MatrixStack matrices, VertexConsumer vc, int light, ModelPart leg) {
        root.copyTransform(leg);
        root.xScale = leg.xScale;
        root.yScale = leg.yScale;
        root.zScale = leg.zScale;
        root.render(matrices, vc, light, OverlayTexture.DEFAULT_UV);
    }
}
