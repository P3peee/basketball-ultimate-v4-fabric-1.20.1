package com.example.basketball.item;

import com.example.basketball.BasketballMod;
import com.example.basketball.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Отдельная вкладка мода в творческом инвентаре. */
public final class ModItemGroups {
    public static final ItemGroup MAIN = Registry.register(Registries.ITEM_GROUP,
            new Identifier(BasketballMod.MOD_ID, "main"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(ModItems.BASKETBALL))
                    .displayName(Text.translatable("itemGroup.basketball"))
                    .entries((ctx, entries) -> {
                        entries.add(ModItems.BASKETBALL);
                        entries.add(ModBlocks.HOOP);
                        entries.add(ModItems.SNEAKERS_FROST);
                        entries.add(ModItems.SNEAKERS_BLAZE);
                        entries.add(ModItems.SNEAKERS_ROYAL);
                    })
                    .build());

    public static void register() { }

    private ModItemGroups() { }
}
