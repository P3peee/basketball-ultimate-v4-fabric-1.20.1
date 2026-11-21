package com.example.basketball.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;

/** Бонусы кроссовок: Лёд - скорость, Пламя - прыжок, Корона - точность броска. */
public final class SneakerStats {
    private static Item boots(Entity e) {
        return e instanceof PlayerEntity p ? p.getEquippedStack(EquipmentSlot.FEET).getItem() : null;
    }

    public static double speed(Entity e) { return boots(e) == ModItems.SNEAKERS_FROST ? 0.05 : 0.0; }
    public static double jump(Entity e) { return boots(e) == ModItems.SNEAKERS_BLAZE ? 1.06 : 1.0; }
    public static int tolerance(Entity e) { return boots(e) == ModItems.SNEAKERS_ROYAL ? 1 : 0; }

    private SneakerStats() { }
}
