package com.example.basketball.item;

import com.example.basketball.BasketballMod;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item BASKETBALL = reg("basketball", new BasketballItem(new FabricItemSettings().maxCount(16)));

    public static final Item SNEAKERS_FROST = reg("sneakers_frost",
            new ArmorItem(SneakerMaterial.FROST, ArmorItem.Type.BOOTS, new FabricItemSettings().maxCount(1)));
    public static final Item SNEAKERS_BLAZE = reg("sneakers_blaze",
            new ArmorItem(SneakerMaterial.BLAZE, ArmorItem.Type.BOOTS, new FabricItemSettings().maxCount(1)));
    public static final Item SNEAKERS_ROYAL = reg("sneakers_royal",
            new ArmorItem(SneakerMaterial.ROYAL, ArmorItem.Type.BOOTS, new FabricItemSettings().maxCount(1)));

    private static Item reg(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(BasketballMod.MOD_ID, name), item);
    }

    public static void register() { }
}
