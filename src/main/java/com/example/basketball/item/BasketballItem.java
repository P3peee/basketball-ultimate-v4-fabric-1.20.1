package com.example.basketball.item;

import com.example.basketball.logic.BallHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class BasketballItem extends Item {
    public BasketballItem(Settings settings) {
        super(settings);
    }

    /** ПКМ с мячом в руке - начать вести мяч (то же, что клавиша R). */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient && user instanceof ServerPlayerEntity sp) {
            BallHandler.toggleDribble(sp);
        }
        user.getItemCooldownManager().set(this, 6);
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
    }
}
