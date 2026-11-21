package com.example.basketball;

import com.example.basketball.client.BallRenderer;
import com.example.basketball.block.ModBlocks;
import com.example.basketball.entity.ModEntities;
import com.example.basketball.item.ModItems;
import com.example.basketball.net.BallNet;
import com.example.basketball.pose.PoseState;
import com.example.physique.PhysiqueClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import com.example.basketball.client.SneakerModel;
import com.example.basketball.client.SneakerRenderer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class BasketballClient implements ClientModInitializer {
    private static final String CATEGORY = "category.basketball";
    private static KeyBinding dribbleKey, shootKey, passKey, blockKey, stealKey, crossKey, spinKey, stepbackKey,
            btlKey, behindKey, hesiKey, fakeKey, euroKey;

    private static boolean shootDown = false;
    private static boolean charging = false;
    private static int charge = 0;
    private static final int METER_MAX = 30;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.BASKETBALL, BallRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.HOOP, RenderLayer.getCutout());
        PhysiqueClient.init();

        EntityModelLayerRegistry.registerModelLayer(SneakerModel.LAYER, SneakerModel::createLayer);
        ArmorRenderer.register(new SneakerRenderer("frost"), ModItems.SNEAKERS_FROST);
        ArmorRenderer.register(new SneakerRenderer("blaze"), ModItems.SNEAKERS_BLAZE);
        ArmorRenderer.register(new SneakerRenderer("royal"), ModItems.SNEAKERS_ROYAL);

        dribbleKey = key("key.basketball.dribble", GLFW.GLFW_KEY_R);
        shootKey = key("key.basketball.shoot", GLFW.GLFW_KEY_V);
        passKey = key("key.basketball.pass", GLFW.GLFW_KEY_G);
        blockKey = key("key.basketball.block", GLFW.GLFW_KEY_B);
        stealKey = key("key.basketball.steal", GLFW.GLFW_KEY_C);
        crossKey = key("key.basketball.cross", GLFW.GLFW_KEY_Z);
        spinKey = key("key.basketball.spin", GLFW.GLFW_KEY_X);
        stepbackKey = key("key.basketball.stepback", GLFW.GLFW_KEY_H);
        btlKey = key("key.basketball.btl", GLFW.GLFW_KEY_J);
        behindKey = key("key.basketball.behind", GLFW.GLFW_KEY_K);
        hesiKey = key("key.basketball.hesi", GLFW.GLFW_KEY_L);
        fakeKey = key("key.basketball.fake", GLFW.GLFW_KEY_U);
        euroKey = key("key.basketball.euro", GLFW.GLFW_KEY_I);

        ClientPlayNetworking.registerGlobalReceiver(BallNet.POSE, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            int pose = buf.readByte();
            client.execute(() -> {
                if (client.world != null) PoseState.setTimed(id, pose, client.world.getTime());
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(BasketballClient::tick);
        HudRenderCallback.EVENT.register(BasketballClient::renderMeter);
    }

    private static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(name, InputUtil.Type.KEYSYM, code, CATEGORY));
    }

    private static void send(int action, int arg) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(action);
        buf.writeInt(arg);
        ClientPlayNetworking.send(BallNet.ACTION, buf);
    }

    private static void moveKey(MinecraftClient mc, KeyBinding k, int type, boolean screen) {
        while (k.wasPressed()) {
            if (screen) continue;
            int dir = mc.options.rightKey.isPressed() ? 1 : mc.options.leftKey.isPressed() ? -1 : 0;
            send(BallNet.A_MOVE, type * 4 + dir + 1);
        }
    }

    private static boolean hasBall(MinecraftClient mc) {
        return PoseState.dribbling(mc.player.getId(), mc.world.getTime())
                || mc.player.getMainHandStack().isOf(ModItems.BASKETBALL)
                || mc.player.getOffHandStack().isOf(ModItems.BASKETBALL);
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            charging = false;
            shootDown = false;
            return;
        }
        boolean screen = mc.currentScreen != null;

        while (dribbleKey.wasPressed()) if (!screen) send(BallNet.A_DRIBBLE, 0);
        while (passKey.wasPressed()) if (!screen) send(BallNet.A_PASS, 0);
        while (blockKey.wasPressed()) if (!screen) send(BallNet.A_BLOCK, 0);
        while (stealKey.wasPressed()) if (!screen) send(BallNet.A_STEAL, 0);
        while (spinKey.wasPressed()) if (!screen) send(BallNet.A_SPIN, 0);
        while (stepbackKey.wasPressed()) if (!screen) send(BallNet.A_STEPBACK, 0);
        while (crossKey.wasPressed()) {
            if (screen) continue;
            int dir = mc.options.rightKey.isPressed() ? 1 : mc.options.leftKey.isPressed() ? -1 : 0;
            send(BallNet.A_CROSS, dir);
        }

        moveKey(mc, btlKey, 3, screen);
        moveKey(mc, behindKey, 4, screen);
        moveKey(mc, hesiKey, 5, screen);
        moveKey(mc, fakeKey, 6, screen);
        moveKey(mc, euroKey, 7, screen);

        boolean down = shootKey.isPressed() && !screen;
        if (down && !shootDown && hasBall(mc)) {
            charging = true;
            charge = 0;
            send(BallNet.A_SHOOT_START, 0);
        } else if (down && charging) {
            charge++;
        } else if (!down && charging) {
            send(BallNet.A_SHOOT_RELEASE, charge);
            charging = false;
        }
        shootDown = down;
    }

    /** Шкала броска: отпустите клавишу в зелёной зоне. */
    private static void renderMeter(DrawContext ctx, float tickDelta) {
        if (!charging) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        int bw = 120, bh = 8;
        int x = w / 2 - bw / 2;
        int y = h - 72;

        ctx.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, 0xFF000000);
        ctx.fill(x, y, x + bw, y + bh, 0xFF333333);
        ctx.fill(x + bw * 11 / METER_MAX, y, x + bw * 21 / METER_MAX, y + bh, 0xFF8A7A1A);
        ctx.fill(x + bw * 14 / METER_MAX, y, x + bw * 18 / METER_MAX, y + bh, 0xFF1FBF3F);

        int pos = Math.min(charge, METER_MAX) * bw / METER_MAX;
        ctx.fill(x + pos - 1, y - 3, x + pos + 1, y + bh + 3, 0xFFFFFFFF);
        ctx.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.basketball.meter"), x, y - 14, 0xFFFFFFFF);
    }
}
