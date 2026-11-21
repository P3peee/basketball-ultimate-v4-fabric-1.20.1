package com.example.basketball.logic;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ScoreKeeper {
    private static final Map<UUID, Integer> SCORES = new HashMap<>();
    private static final Map<UUID, String> NAMES = new HashMap<>();

    public static void award(ServerPlayerEntity p, int points, String messageKey) {
        int total = SCORES.merge(p.getUuid(), points, Integer::sum);
        NAMES.put(p.getUuid(), p.getName().getString());
        p.sendMessage(Text.translatable(messageKey, points, total), true);
        for (ServerPlayerEntity o : p.getServerWorld().getPlayers()) {
            if (o != p && o.squaredDistanceTo(p) < 64.0 * 64.0) {
                o.sendMessage(Text.translatable("message.basketball.broadcast", p.getDisplayName(), points, total), false);
            }
        }
    }

    public static List<Text> table() {
        List<Map.Entry<UUID, Integer>> list = new ArrayList<>(SCORES.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<Text> out = new ArrayList<>();
        int i = 1;
        for (Map.Entry<UUID, Integer> e : list) {
            out.add(Text.literal(i++ + ". " + NAMES.getOrDefault(e.getKey(), "?") + " - " + e.getValue()));
            if (i > 10) break;
        }
        return out;
    }

    public static void reset() {
        SCORES.clear();
        NAMES.clear();
    }

    private ScoreKeeper() { }
}
