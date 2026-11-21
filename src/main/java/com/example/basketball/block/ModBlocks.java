package com.example.basketball.block;

import com.example.basketball.BasketballMod;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public class ModBlocks {
    public static final Block HOOP = registerBlock("hoop",
            new HoopBlock(FabricBlockSettings.create().strength(2.0f).nonOpaque().sounds(BlockSoundGroup.METAL)));

    private static Block registerBlock(String name, Block block) {
        Identifier id = new Identifier(BasketballMod.MOD_ID, name);
        Registry.register(Registries.ITEM, id, new BlockItem(block, new FabricItemSettings()));
        return Registry.register(Registries.BLOCK, id, block);
    }

    public static void register() { }
}
