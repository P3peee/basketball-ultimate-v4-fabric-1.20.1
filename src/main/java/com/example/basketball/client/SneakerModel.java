package com.example.basketball.client;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/** Объёмная модель кроссовка: подошва, носок, пятка, воротник, язык, шнуровка, боковые панели. Сгенерировано. */
public final class SneakerModel {
    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier("basketball", "sneaker"), "main");

    /** x, y, z, w, h, d, раздутие. Текстура 128x64: деталь i занимает ячейку 32x16. */
    private static final float[][] PARTS = {
            {-2.5f, 11f, -3.5f, 5f, 1f, 6f, 0.0f},
            {-2f, 9f, -3.5f, 4f, 2f, 3f, 0.25f},
            {-2f, 8f, -0.5f, 4f, 3f, 3f, 0.25f},
            {-2f, 6f, -2f, 4f, 2f, 4f, 0.3f},
            {-1.5f, 6f, -2.6f, 3f, 3f, 1f, 0.1f},
            {-1f, 8f, -3f, 2f, 1f, 2f, 0.1f},
            {2.1f, 8.5f, -0.5f, 1f, 2f, 3f, 0.0f},
            {-3.1f, 8.5f, -0.5f, 1f, 2f, 3f, 0.0f},
            {-1f, 6f, 2.2f, 2f, 3f, 1f, 0.0f}
    };

    public static TexturedModelData createLayer() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        for (int i = 0; i < PARTS.length; i++) {
            float[] p = PARTS[i];
            int u = (i % 4) * 32;
            int v = (i / 4) * 16;
            root.addChild("part" + i,
                    ModelPartBuilder.create().uv(u, v).cuboid(p[0], p[1], p[2], p[3], p[4], p[5], new Dilation(p[6])),
                    ModelTransform.NONE);
        }
        return TexturedModelData.of(data, 128, 64);
    }

    private SneakerModel() { }
}
