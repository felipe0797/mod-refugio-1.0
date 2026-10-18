package com.felpz.refugio.command;

import com.felpz.refugio.Refugio;
import com.felpz.refugio.data.RefugioPlayerData;
import com.felpz.refugio.menu.RefugioMenu;
import com.felpz.refugio.npc.RefugioTrainingDummy;
import com.felpz.refugio.world.RefugioSustainability;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerPlayer;

public final class RefugioCommands {
    private RefugioCommands() { }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = Commands.literal("refugio")
                    .then(Commands.literal("quantidade")
                            .then(Commands.argument("valor", IntegerArgumentType.integer(1, 2304))
                                    .executes(context -> {
                                        ServerPlayer player = context.getSource().getPlayerOrException();
                                        int quantity = IntegerArgumentType.getInteger(context, "valor");
                                        if (!RefugioMenu.applyCustomQuantity(player, quantity)) {
                                            player.sendSystemMessage(Component.translatable("message.refugio.custom_quantity_no_request"));
                                            return 0;
                                        }
                                        return 1;
                                    })))
                    .then(Commands.literal("vender")
                            .then(Commands.argument("valor", IntegerArgumentType.integer(1, 2304))
                                    .executes(context -> {
                                        ServerPlayer player = context.getSource().getPlayerOrException();
                                        int quantity = IntegerArgumentType.getInteger(context, "valor");
                                        if (!RefugioMenu.applyCustomSellQuantity(player, quantity)) {
                                            player.sendSystemMessage(Component.translatable("message.refugio.custom_sell_quantity_invalid"));
                                            return 0;
                                        }
                                        return 1;
                                    })))
                    .then(Commands.literal("buscar")
                            .then(Commands.argument("termo", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        ServerPlayer player = context.getSource().getPlayerOrException();
                                        String query = StringArgumentType.getString(context, "termo");
                                        if (!RefugioMenu.applySearch(player, query)) {
                                            player.sendSystemMessage(Component.translatable("message.refugio.search_no_request"));
                                            return 0;
                                        }
                                        return 1;
                                    })));

            var admin = Commands.literal("admin")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN));
            admin.then(Commands.literal("boneco_treino").executes(context -> {
                var source = context.getSource();
                if (!(source.getEntity() instanceof ServerPlayer player)) {
                    source.sendFailure(Component.translatable("command.refugio.training_dummy.player_only"));
                    return 0;
                }
                if (!RefugioTrainingDummy.ensureAtCastle(source.getServer())) {
                    source.sendFailure(Component.translatable("command.refugio.training_dummy.spawn_failed"));
                    return 0;
                }
                player.sendSystemMessage(Component.translatable("message.refugio.training_dummy.spawned", 100));
                return 1;
            }));
            admin.then(Commands.literal("resetar_baus").executes(context -> {
                var world = context.getSource().getServer().getLevel(Refugio.DIMENSION);
                if (world == null) {
                    context.getSource().sendFailure(Component.translatable("command.refugio.chest_reset_dimension_missing"));
                    return 0;
                }
                int count = RefugioSustainability.resetLoadedChests(world);
                context.getSource().sendSuccess(() -> Component.translatable(
                        "command.refugio.chest_reset_complete", count), true);
                return count;
            }));
            admin.then(Commands.literal("moedas")
                    .then(Commands.argument("jogador", EntityArgument.player())
                            .then(Commands.argument("quantidade", IntegerArgumentType.integer(0, Integer.MAX_VALUE))
                                    .executes(context -> {
                                        ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
                                        int amount = IntegerArgumentType.getInteger(context, "quantidade");
                                        RefugioPlayerData.setCoins(target, amount);
                                        context.getSource().sendSuccess(() -> Component.translatable(
                                                "command.refugio.coins_set", target.getGameProfile().name(), amount), true);
                                        return 1;
                                    }))));
            admin.then(Commands.literal("desbloquear_tudo")
                    .then(Commands.argument("jogador", EntityArgument.player())
                            .executes(context -> {
                                ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
                                RefugioPlayerData.setAllShopCategoriesUnlocked(target, true);
                                context.getSource().sendSuccess(() -> Component.translatable(
                                        "command.refugio.shop_all_unlocked", target.getGameProfile().name()), true);
                                return 1;
                            })));
            admin.then(Commands.literal("restaurar_progressao")
                    .then(Commands.argument("jogador", EntityArgument.player())
                            .executes(context -> {
                                ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
                                RefugioPlayerData.setAllShopCategoriesUnlocked(target, false);
                                context.getSource().sendSuccess(() -> Component.translatable(
                                        "command.refugio.shop_progression_restored", target.getGameProfile().name()), true);
                                return 1;
                            })));
            root.then(admin);
            dispatcher.register(root);
        });
    }
}
