package com.example.basketball.pose;

import java.util.HashMap;
import java.util.Map;

/** Клиентское хранилище поз игроков. Только данные - безопасно грузить на сервере. */
public final class PoseState {
    public static final int NONE = 0, SHOOT = 2, PASS = 3, BLOCK = 4, STEAL = 5, DUNK = 6, CATCH = 7,
            STUMBLE = 10, SPIN = 11, STEPBACK = 12, CROSS_R = 13, CROSS_L = 14,
            DUNK_TOMA = 16, DUNK_WIND = 17,
            BTL = 18, BACK = 19, HESI = 20, FAKE = 21, EURO = 22;

    public record Timed(int pose, long start) { }
    private record Drib(long time, float side) { }

    private static final Map<Integer, Timed> TIMED = new HashMap<>();
    private static final Map<Integer, Drib> DRIBBLE = new HashMap<>();

    public static void setTimed(int entityId, int pose, long now) { TIMED.put(entityId, new Timed(pose, now)); }
    public static Timed timed(int entityId) { return TIMED.get(entityId); }

    public static void markDribble(int entityId, long now, float side) { DRIBBLE.put(entityId, new Drib(now, side)); }

    public static boolean dribbling(int entityId, long now) {
        Drib d = DRIBBLE.get(entityId);
        return d != null && now - d.time() <= 2;
    }

    public static float dribbleSide(int entityId) {
        Drib d = DRIBBLE.get(entityId);
        return d == null ? 1f : d.side();
    }

    public static boolean isDunk(int pose) { return pose == DUNK || pose == DUNK_TOMA || pose == DUNK_WIND; }

    /** Профиль отскока (период 10 тиков): 1 - мяч у руки, 0 - у пола. Падает ускоряясь, у руки замедляется. */
    public static float bounce(double time) {
        double phase = (((time % 10) + 10) % 10) / 10.0;
        double q = 2.0 * Math.min(phase, 1.0 - phase);
        return (float) (1.0 - q * q);
    }

    public static int duration(int pose) {
        if (isDunk(pose)) return 28;
        return switch (pose) {
            case SHOOT -> 14;
            case PASS -> 8;
            case BLOCK -> 14;
            case STEAL -> 8;
            case CATCH -> 6;
            case STUMBLE -> 24;
            case SPIN -> 12;
            case STEPBACK -> 12;
            case CROSS_R, CROSS_L -> 10;
            case BTL, BACK -> 10;
            case HESI, FAKE -> 12;
            case EURO -> 14;
            default -> 1;
        };
    }

    private PoseState() { }
}
