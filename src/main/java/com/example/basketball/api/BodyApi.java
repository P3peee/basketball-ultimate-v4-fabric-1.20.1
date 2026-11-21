package com.example.basketball.api;

import com.example.basketball.item.SneakerStats;
import net.minecraft.entity.Entity;

import java.util.UUID;

/** Мост между баскетболом и модом роста. Без роста все значения нейтральные. */
public final class BodyApi {
    public interface Provider {
        double scale(UUID id);
        double reach(UUID id);
        double jump(UUID id);
        int shotTolerance(UUID id);
        double shotRange(UUID id);
    }

    private static final Provider DEFAULT = new Provider() {
        public double scale(UUID id) { return 1.0; }
        public double reach(UUID id) { return 1.0; }
        public double jump(UUID id) { return 1.0; }
        public int shotTolerance(UUID id) { return 0; }
        public double shotRange(UUID id) { return 12.0; }
    };

    private static Provider provider = DEFAULT;

    public static void register(Provider p) { provider = p == null ? DEFAULT : p; }

    public static double scale(Entity e) { return provider.scale(e.getUuid()); }
    public static double reach(Entity e) { return provider.reach(e.getUuid()); }
    public static double jump(Entity e) { return provider.jump(e.getUuid()); }
    public static int shotTolerance(Entity e) { return provider.shotTolerance(e.getUuid()) + SneakerStats.tolerance(e); }
    public static double shotRange(Entity e) { return provider.shotRange(e.getUuid()); }

    private BodyApi() { }
}
