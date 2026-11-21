package com.example.physique;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Сохраняет рост и телосложение игроков в мире. */
public class PhysiqueState extends PersistentState {
    private final Map<UUID, int[]> data = new HashMap<>();

    public static PhysiqueState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(PhysiqueState::fromNbt, PhysiqueState::new, "physique");
    }

    public static PhysiqueState fromNbt(NbtCompound nbt) {
        PhysiqueState s = new PhysiqueState();
        NbtCompound players = nbt.getCompound("players");
        for (String key : players.getKeys()) {
            int[] a = players.getIntArray(key);
            if (a.length == 2) s.data.put(UUID.fromString(key), a);
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound players = new NbtCompound();
        data.forEach((k, v) -> players.putIntArray(k.toString(), v));
        nbt.put("players", players);
        return nbt;
    }

    public Physique.Body get(UUID id) {
        int[] a = data.get(id);
        return a == null ? null : Physique.Body.of(a[0], a[1]);
    }

    public void set(UUID id, Physique.Body b) {
        data.put(id, new int[]{b.cm(), b.build()});
        markDirty();
    }
}
