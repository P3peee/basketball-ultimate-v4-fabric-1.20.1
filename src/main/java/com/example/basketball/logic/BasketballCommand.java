package com.example.basketball.logic;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class BasketballCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("basketball")
                        .then(CommandManager.literal("arena")
                                .requires(s -> s.hasPermissionLevel(2))
                                .executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    BlockPos origin = ArenaBuilder.build(p.getServerWorld(), p.getBlockPos());
                                    ctx.getSource().sendFeedback(() -> Text.translatable("message.basketball.arena_built",
                                            origin.getX(), origin.getY(), origin.getZ()), true);
                                    return 1;
                                }))
                        .then(CommandManager.literal("scores")
                                .executes(ctx -> {
                                    ctx.getSource().sendFeedback(() -> Text.translatable("message.basketball.scores_title"), false);
                                    for (Text line : ScoreKeeper.table()) {
                                        ctx.getSource().sendFeedback(() -> line, false);
                                    }
                                    return 1;
                                }))
                        .then(CommandManager.literal("reset")
                                .requires(s -> s.hasPermissionLevel(2))
                                .executes(ctx -> {
                                    ScoreKeeper.reset();
                                    ctx.getSource().sendFeedback(() -> Text.translatable("message.basketball.scores_reset"), true);
                                    return 1;
                                }))));
    }

    private BasketballCommand() { }
}
