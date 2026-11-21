package com.example.basketball;

import com.example.basketball.block.ModBlocks;
import com.example.basketball.entity.ModEntities;
import com.example.basketball.item.ModItemGroups;
import com.example.basketball.item.ModItems;
import com.example.basketball.logic.BallHandler;
import com.example.basketball.logic.BasketballCommand;
import com.example.physique.Physique;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;

public class BasketballMod implements ModInitializer {
    public static final String MOD_ID = "basketball";

    @Override
    public void onInitialize() {
        ModItems.register();
        ModBlocks.register();
        ModEntities.register();
        BallHandler.register();
        BasketballCommand.register();
        Physique.init();

        ModItemGroups.register();
    }
}
