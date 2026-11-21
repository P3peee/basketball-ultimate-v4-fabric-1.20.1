package com.example.physique;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

public final class PhysiqueClient {
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Physique.SYNC, (client, handler, buf, sender) -> {
            UUID id = buf.readUuid();
            int cm = buf.readVarInt();
            int build = buf.readByte();
            client.execute(() -> {
                Physique.put(id, Physique.Body.of(cm, build));
                if (client.world != null) {
                    PlayerEntity p = client.world.getPlayerByUuid(id);
                    if (p != null) p.calculateDimensions();
                }
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;
            for (PlayerEntity p : client.world.getPlayers()) Physique.syncDimensions(p);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Physique.clear());
    }

    private PhysiqueClient() { }
}
