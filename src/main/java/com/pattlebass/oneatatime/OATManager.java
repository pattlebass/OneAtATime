package com.pattlebass.oneatatime;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.MessageArgumentType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static net.minecraft.server.command.CommandManager.literal;

public class OATManager {
    public static final Logger LOGGER = LoggerFactory.getLogger("OAT");
    public static final List<ServerPlayerEntity> queue = new ArrayList<>();
    public final ServerBossBar switchProgress = new ServerBossBar(Text.literal("Time left"), BossBar.Color.GREEN,
            BossBar.Style.PROGRESS);
    public int MAX_TIME = 1000;
    public int time = 0;
    public ServerPlayerEntity selectedPlayer;

    public OATManager() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralCommandNode<ServerCommandSource> oatNode = CommandManager
                    .literal("oat")
                    .build();

            LiteralCommandNode<ServerCommandSource> nextNode = CommandManager
                    .literal("next")
                    .requires(source -> source.hasPermissionLevel(3))
                    .executes(context -> {
                        if (queue.size() > 1 && selectedPlayer != null) {
                            switchPlayer(selectedPlayer, getNext(selectedPlayer));
                            time = 0;
                            context.getSource().sendMessage(Text.literal(
                                    "Switched to " + selectedPlayer.getDisplayName().getString()));
                            return 1;
                        } else {
                            context.getSource().sendMessage(Text.literal(
                                    "There are not enough players to switch"));
                            return -1;
                        }
                    })
                    .build();

            LiteralCommandNode<ServerCommandSource> switchNode = CommandManager
                    .literal("switch")
                    .requires(source -> source.hasPermissionLevel(3))
                    .executes(context -> {
                        ServerCommandSource source = context.getSource();

                        if (!source.isExecutedByPlayer()) {
                            context.getSource().sendMessage(Text.literal("No player specified"));
                            return -1;
                        }

                        ServerPlayerEntity _player = context.getSource().getPlayer();
                        if (queue.size() <= 1 || selectedPlayer == null) {
                            context.getSource().sendMessage(Text.literal("There are not enough players to switch"));
                            return -1;
                        }
                        if (_player == selectedPlayer) {
                            context.getSource().sendMessage(Text.literal("Cannot switch to already selected player"));
                            return -1;
                        }
                        if (!queue.contains(_player)) {
                            context.getSource().sendMessage(Text.literal("Player not in queue"));
                            return -1;
                        }

                        Collections.swap(queue, queue.indexOf(selectedPlayer), queue.indexOf(_player));
                        switchPlayer(selectedPlayer, _player);
                        time = 0;
                        context.getSource().sendMessage(Text.literal(
                                "Switched to " + selectedPlayer.getDisplayName().getString()));

                        return 1;
                    })
                    .build();

            var switchPlayer = CommandManager.argument("player", EntityArgumentType.player())
                    .executes(context -> {
                        ServerPlayerEntity _player = EntityArgumentType.getPlayer(context, "player");

                        if (queue.size() <= 1 || selectedPlayer == null) {
                            context.getSource().sendMessage(Text.literal("There are not enough players to switch"));
                            return -1;
                        }
                        if (_player == selectedPlayer) {
                            context.getSource().sendMessage(Text.literal("Cannot switch to already selected player"));
                            return -1;
                        }
                        if (!queue.contains(_player)) {
                            context.getSource().sendMessage(Text.literal("Player not in queue"));
                            return -1;
                        }

                        Collections.swap(queue, queue.indexOf(selectedPlayer), queue.indexOf(_player));
                        switchPlayer(selectedPlayer, _player);
                        time = 0;
                        context.getSource().sendMessage(Text.literal(
                                "Switched to " + selectedPlayer.getDisplayName().getString()));

                        return 1;
                    })
                    .build();

            LiteralCommandNode<ServerCommandSource> maxTimeNode = CommandManager
                    .literal("maxtime")
                    .requires(source -> source.hasPermissionLevel(3))
                    .build();

            var maxTimeSeconds = CommandManager.argument("seconds", IntegerArgumentType.integer(5))
                    .executes(context -> {
                        int _maxTime = IntegerArgumentType.getInteger(context, "seconds");
                        MAX_TIME = _maxTime;
                        context.getSource().sendMessage(Text.literal("Max time set to " + _maxTime));
                        return 1;
                    })
                    .build();

            LiteralCommandNode<ServerCommandSource> resetTimeNode = CommandManager
                    .literal("reset")
                    .requires(source -> source.hasPermissionLevel(3))
                    .executes(context -> {
                        context.getSource().sendMessage(Text.literal("Time has been reset"));
                        time = 0;
                        return 1;
                    })
                    .build();

            LiteralCommandNode<ServerCommandSource> queueNode = CommandManager
                    .literal("queue")
                    .build();

            LiteralCommandNode<ServerCommandSource> listNode = CommandManager
                    .literal("list")
                    .executes(context -> {
                        String message = Formatting.UNDERLINE + "Queue:\n";

                        for (int i = 0; i < queue.size(); i++) {
                            ServerPlayerEntity player = queue.get(i);

                            if (player == selectedPlayer)
                                message += Formatting.AQUA;
                            else
                                message += Formatting.WHITE;

                            message += "● " + player.getDisplayName().getString();
                            message += i == queue.size() - 1 ? "" : "\n";
                        }

                        context.getSource().sendMessage(Text.of(message));
                        return 1;
                    })
                    .build();

            LiteralCommandNode<ServerCommandSource> shuffleNode = CommandManager
                    .literal("shuffle")
                    .requires(source -> source.hasPermissionLevel(3))
                    .executes(context -> {
                        Collections.shuffle(queue);
                        context.getSource().sendMessage(Text.of("Shuffled queue"));
                        return 1;
                    })
                    .build();

            LiteralCommandNode<ServerCommandSource> addNode = CommandManager
                    .literal("add")
                    .requires(source -> source.hasPermissionLevel(3))
                    .build();

            var addPlayer = CommandManager.argument("player", EntityArgumentType.player())
                    .executes(context -> {
                        ServerPlayerEntity _player = EntityArgumentType.getPlayer(context, "player");

                        if (queue.contains(_player)) {
                            context.getSource().sendMessage(Text.literal("Player already in queue"));
                            return -1;
                        }

                        queue.add(_player);

                        if (selectedPlayer == null) {
                            selectedPlayer = _player;
                        }

                        context.getSource().sendMessage(Text.literal(String.format("Added %s to the queue",
                                _player.getDisplayName().getString())));

                        return 1;
                    })
                    .build();

            LiteralCommandNode<ServerCommandSource> removeNode = CommandManager
                    .literal("remove")
                    .requires(source -> source.hasPermissionLevel(3))
                    .build();

            var removePlayer = CommandManager.argument("player", EntityArgumentType.player())
                    .executes(context -> {
                        ServerPlayerEntity _player = EntityArgumentType.getPlayer(context, "player");

                        if (!queue.contains(_player)) {
                            context.getSource().sendMessage(Text.literal("Player not in queue"));
                            return -1;
                        }

                        if (selectedPlayer == _player) {
                            if (queue.size() == 1)
                                selectedPlayer = null;
                            else
                                switchPlayer(_player, getNext(_player));
                        }

                        queue.remove(_player);

                        _player.getInventory().clear();
                        _player.changeGameMode(GameMode.SURVIVAL);
                        _player.setCameraEntity(null);

                        context.getSource().sendMessage(Text.literal(String.format("Removed %s from the queue",
                                _player.getDisplayName().getString())));

                        return 1;
                    })
                    .build();

            dispatcher.getRoot().addChild(oatNode);

            //usage: /oat next
            oatNode.addChild(nextNode);

            //usage: /oat switch [player]
            oatNode.addChild(switchNode);
            switchNode.addChild(switchPlayer);

            //usage: /oat maxtime <seconds>
            oatNode.addChild(maxTimeNode);
            maxTimeNode.addChild(maxTimeSeconds);

            //usage: /oat reset
            oatNode.addChild(resetTimeNode);

            //usage: /oat queue [list | shuffle | add <player> | remove <player>]
            oatNode.addChild(queueNode);
            queueNode.addChild(listNode);
            queueNode.addChild(shuffleNode);
            queueNode.addChild(addNode);
            addNode.addChild(addPlayer);
            queueNode.addChild(removeNode);
            removeNode.addChild(removePlayer);
        });
    }

    public void playerJoined(ServerPlayerEntity serverPlayer) {
        switchProgress.addPlayer(serverPlayer);
        queue.add(serverPlayer);
        if (selectedPlayer == null) switchPlayer(serverPlayer, serverPlayer);
    }

    public void playerLeft(ServerPlayerEntity serverPlayer) {
        if (selectedPlayer == serverPlayer) {
            LOGGER.info("Player that left was selectedPlayer");
            if (queue.size() > 1) {
                LOGGER.info("Queue was bigger than 1");
                switchPlayer(selectedPlayer, getNext(selectedPlayer));
            } else {
                LOGGER.info("Queue was too small. Set selectedPlayer to null");
                selectedPlayer = null;
            }
        }
        queue.remove(serverPlayer);
    }

    public void playerDied(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        queue.set(queue.indexOf(oldPlayer), newPlayer);
        if (oldPlayer == selectedPlayer && !alive) {
            selectedPlayer = newPlayer;
        }
    }

    public void secondPassed() {
        if (selectedPlayer != null && queue.size() > 1) time++;
        switchProgress.setPercent(1 - (float) time / MAX_TIME);

        if (switchProgress.getPercent() <= 0.25) {
            switchProgress.setColor(BossBar.Color.RED);
        } else if (switchProgress.getPercent() <= 0.5) {
            switchProgress.setColor(BossBar.Color.YELLOW);
        } else {
            switchProgress.setColor(BossBar.Color.GREEN);
        }

        switchProgress.setName(Text.literal("Switch in: " + convertSeconds(MAX_TIME - time)));

        if (time >= MAX_TIME && selectedPlayer != null) {
            switchPlayer(selectedPlayer, getNext(selectedPlayer));
            time = 0;
        }

        queue.forEach((player) -> {
            if (player != selectedPlayer) {
                copyEffects(selectedPlayer, player);
                player.sendMessage(Text.literal(Formatting.WHITE + "Spectating " + Formatting.AQUA +
                        selectedPlayer.getDisplayName().getString()), true);
            }
        });

        //LOGGER.info(String.valueOf(selectedPlayer));
    }

    public void tick() {
        queue.forEach((player) -> {
            if (player != selectedPlayer) {
                player.teleport(selectedPlayer.getWorld(), selectedPlayer.getX(), selectedPlayer.getY(),
                        selectedPlayer.getZ(), selectedPlayer.getYaw(), selectedPlayer.getPitch());
                player.setCameraEntity(selectedPlayer);
                copyInventory(selectedPlayer, player);
                player.changeGameMode(GameMode.SPECTATOR);
                player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_MODE_CHANGED, GameMode.SURVIVAL.getId()));
            }
        });
    }

    private void switchPlayer(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        ServerWorld world = oldPlayer.getWorld();
        Vec3d pos = oldPlayer.getPos();
        float yaw = oldPlayer.getYaw();
        float pitch = oldPlayer.getPitch();

        oldPlayer.changeGameMode(GameMode.SPECTATOR);

        selectedPlayer = newPlayer;

        newPlayer.teleport(world, pos.x, pos.y, pos.z, yaw, pitch);
        newPlayer.changeGameMode(GameMode.SURVIVAL);
        newPlayer.sendMessage(Text.literal(Formatting.GOLD + "You are playing!"), true);
    }

    private void copyInventory(ServerPlayerEntity from, ServerPlayerEntity to) {
        for (int i = 0; i < 41; i++) {
            ItemStack stack = from.getInventory().getStack(i);
            to.getInventory().setStack(i, stack);
        }
    }

    private void copyEffects(ServerPlayerEntity from, ServerPlayerEntity to) {
        to.clearStatusEffects();
        from.getStatusEffects().forEach((effect) -> {
            to.addStatusEffect(new StatusEffectInstance(effect.getEffectType(), effect.getDuration(),
                    effect.getAmplifier()));
        });
    }

    public ServerPlayerEntity getNext(ServerPlayerEntity uid) {
        int idx = queue.indexOf(uid);
        if (idx + 1 == queue.size()) return queue.get(0);
        return queue.get(idx + 1);
    }

    public ServerPlayerEntity getPrevious(ServerPlayerEntity uid) {
        int idx = queue.indexOf(uid);
        if (idx == 0) return queue.get(queue.size() - 1);
        return queue.get(idx - 1);
    }

    public static String convertSeconds(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        String sh = (h > 0 ? String.valueOf(h) + " " + "h" : "");
        String sm = (m < 10 && m > 0 && h > 0 ? "0" : "") +
                (m > 0 ? (h > 0 && s == 0 ? String.valueOf(m) : String.valueOf(m) + " " + "min") : "");
        String ss = (
                s == 0 && (h > 0 || m > 0) ? "" :
                        (s < 10 && (h > 0 || m > 0) ? "0" : "") + String.valueOf(s) + " " + "sec");
        return sh + (h > 0 ? " " : "") + sm + (m > 0 ? " " : "") + ss;
    }
}
