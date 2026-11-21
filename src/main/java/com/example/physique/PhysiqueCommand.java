package com.example.physique;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class PhysiqueCommand {
    private static final String[] BUILDS = {"lean", "athletic", "power"};

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("height")
                    .then(CommandManager.argument("cm", IntegerArgumentType.integer(Physique.MIN_CM, Physique.MAX_CM))
                            .executes(ctx -> {
                                ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                int cm = IntegerArgumentType.getInteger(ctx, "cm");
                                Physique.set(p, Physique.Body.of(cm, Physique.get(p.getUuid()).build()));
                                ctx.getSource().sendFeedback(() -> Text.translatable("message.physique.height", cm), false);
                                return 1;
                            })));

            dispatcher.register(CommandManager.literal("build")
                    .then(CommandManager.argument("type", StringArgumentType.word())
                            .suggests((ctx, builder) -> CommandSource.suggestMatching(BUILDS, builder))
                            .executes(ctx -> {
                                ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                String t = StringArgumentType.getString(ctx, "type").toLowerCase();
                                int idx = -1;
                                for (int i = 0; i < BUILDS.length; i++) if (BUILDS[i].equals(t)) idx = i;
                                if (idx < 0) {
                                    ctx.getSource().sendError(Text.translatable("message.physique.badbuild"));
                                    return 0;
                                }
                                Physique.set(p, Physique.Body.of(Physique.get(p.getUuid()).cm(), idx));
                                ctx.getSource().sendFeedback(() -> Text.translatable("message.physique.build." + t), false);
                                return 1;
                            })));

            dispatcher.register(CommandManager.literal("body").executes(ctx -> {
                ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                Physique.Body b = Physique.get(p.getUuid());
                ctx.getSource().sendFeedback(() -> Text.translatable("message.physique.info",
                        b.cm(), Text.translatable("message.physique.build." + BUILDS[b.build()])), false);
                return 1;
            }));
        });
    }

    private PhysiqueCommand() { }
}
