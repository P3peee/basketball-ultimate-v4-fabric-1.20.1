package com.example.basketball.net;

import com.example.basketball.logic.BallHandler;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.LinkedHashSet;
import java.util.Set;

public final class BallNet {
    /** Клиент -> сервер: byte действие, int аргумент. */
    public static final Identifier ACTION = new Identifier("basketball", "action");
    /** Сервер -> клиенты: varint id игрока, byte поза. */
    public static final Identifier POSE = new Identifier("basketball", "pose");

    public static final int A_DRIBBLE = 0, A_SHOOT_START = 1, A_SHOOT_RELEASE = 2, A_PASS = 3, A_BLOCK = 4, A_STEAL = 5,
            A_CROSS = 6, A_SPIN = 7, A_STEPBACK = 8, A_MOVE = 9;

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(ACTION, (server, player, handler, buf, sender) -> {
            int action = buf.readByte();
            int arg = buf.readInt();
            server.execute(() -> BallHandler.handleAction(player, action, arg));
        });
    }

    public static void sendPose(ServerPlayerEntity player, int pose) {
        Set<ServerPlayerEntity> targets = new LinkedHashSet<>(PlayerLookup.tracking(player));
        targets.add(player);
        for (ServerPlayerEntity t : targets) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(player.getId());
            buf.writeByte(pose);
            ServerPlayNetworking.send(t, POSE, buf);
        }
    }

    private BallNet() { }
}
