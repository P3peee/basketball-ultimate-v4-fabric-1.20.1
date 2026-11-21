package com.example.physique;

import com.example.basketball.api.BodyApi;
import com.example.basketball.item.SneakerStats;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Рост и телосложение игроков. Рост 180 см + atletic = обычный игрок без изменений. */
public final class Physique {
    public static final int MIN_CM = 150, MAX_CM = 230, DEFAULT_CM = 180;
    public static final int LEAN = 0, ATHLETIC = 1, POWER = 2;
    public static final Identifier SYNC = new Identifier("physique", "sync");
    private static final UUID SPEED_ID = UUID.fromString("5b1f3a52-7d0e-4f4e-9d3c-0a6a2d9c1b77");

    public record Body(int cm, int build) {
        public static final Body DEFAULT = new Body(DEFAULT_CM, ATHLETIC);

        public static Body of(int cm, int build) {
            return new Body(Math.max(MIN_CM, Math.min(MAX_CM, cm)), Math.max(0, Math.min(2, build)));
        }

        public float scale() { return cm / 180f; }

        /** -0.67 (150 см) ... 1.0 (225+ см). */
        public double hh() { return Math.max(-0.67, Math.min(1.0, (cm - 180) / 45.0)); }

        public float widthFactor() { return build == LEAN ? 0.92f : build == POWER ? 1.1f : 1.0f; }
        public float torsoX() { return build == LEAN ? 0.82f : build == POWER ? 1.2f : 1.0f; }
        public float torsoZ() { return build == LEAN ? 0.85f : build == POWER ? 1.15f : 1.0f; }
        public float armXZ() { return build == LEAN ? 0.78f : build == POWER ? 1.3f : 1.0f; }
        public float legXZ() { return build == LEAN ? 0.84f : build == POWER ? 1.18f : 1.0f; }
        /** Размах рук: высокие игроки с более длинными руками. */
        public float armY() { return (float) (1.0 + 0.08 * hh()); }

        public double jump() {
            double base = build == LEAN ? 1.04 : build == POWER ? 0.95 : 1.0;
            return base * (1.0 - 0.05 * hh());
        }
        public double speedBonus() {
            double base = build == LEAN ? 0.03 : build == POWER ? -0.03 : 0.0;
            return base - 0.04 * hh();
        }
        public int shotTolerance() { return build == LEAN ? 1 : build == POWER ? -1 : 0; }
        public double shotRange() { return build == LEAN ? 14.0 : build == POWER ? 10.0 : 12.0; }
    }

    private static final Map<UUID, Body> BODIES = new ConcurrentHashMap<>();

    public static Body get(UUID id) {
        if (id == null) return Body.DEFAULT;
        return BODIES.getOrDefault(id, Body.DEFAULT);
    }

    public static void put(UUID id, Body b) { BODIES.put(id, b); }
    public static void clear() { BODIES.clear(); }

    // ---------- сервер ----------
    public static void init() {
        BodyApi.register(new BodyApi.Provider() {
            public double scale(UUID id) { return get(id).scale(); }
            public double reach(UUID id) { return get(id).scale(); }
            public double jump(UUID id) { return get(id).jump(); }
            public int shotTolerance(UUID id) { return get(id).shotTolerance(); }
            public double shotRange(UUID id) { return get(id).shotRange(); }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.player;
            Body b = PhysiqueState.get(server).get(p.getUuid());
            if (b != null) BODIES.put(p.getUuid(), b);
            apply(p);
            for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
                if (other != p) send(p, other.getUuid(), get(other.getUuid()));
            }
            broadcast(server, p.getUuid(), get(p.getUuid()));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BODIES.remove(handler.player.getUuid()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> apply(newP));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BODIES.clear());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 10 != 0) return;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                syncDimensions(p);
                refreshSpeed(p);
            }
        });

        PhysiqueCommand.register();
    }

    public static void syncDimensions(net.minecraft.entity.player.PlayerEntity p) {
        EntityDimensions d = p.getDimensions(p.getPose());
        if (Math.abs(p.getBoundingBox().getYLength() - d.height) > 0.01) p.calculateDimensions();
    }

    private static void refreshSpeed(ServerPlayerEntity p) {
        EntityAttributeInstance inst = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (inst == null) return;
        EntityAttributeModifier cur = inst.getModifier(SPEED_ID);
        double now = cur == null ? 0 : cur.getValue();
        double want = get(p.getUuid()).speedBonus() + SneakerStats.speed(p);
        if (Math.abs(now - want) > 1e-6) apply(p);
    }

    public static void set(ServerPlayerEntity p, Body b) {
        MinecraftServer server = p.getServer();
        BODIES.put(p.getUuid(), b);
        if (server != null) {
            PhysiqueState.get(server).set(p.getUuid(), b);
            apply(p);
            broadcast(server, p.getUuid(), b);
        }
    }

    public static void apply(ServerPlayerEntity p) {
        Body b = get(p.getUuid());
        EntityAttributeInstance inst = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (inst != null) {
            inst.removeModifier(SPEED_ID);
            double amt = b.speedBonus() + SneakerStats.speed(p);
            if (amt != 0) {
                inst.addPersistentModifier(new EntityAttributeModifier(SPEED_ID, "physique_speed", amt,
                        EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
        p.calculateDimensions();
    }

    private static void broadcast(MinecraftServer server, UUID id, Body b) {
        for (ServerPlayerEntity pl : server.getPlayerManager().getPlayerList()) send(pl, id, b);
    }

    private static void send(ServerPlayerEntity to, UUID id, Body b) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(id);
        buf.writeVarInt(b.cm());
        buf.writeByte(b.build());
        ServerPlayNetworking.send(to, SYNC, buf);
    }

    private Physique() { }
}
