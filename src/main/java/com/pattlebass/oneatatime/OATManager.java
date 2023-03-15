package com.pattlebass.oneatatime;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import static com.pattlebass.oneatatime.Util.convertSeconds;

public class OATManager {
    public static final Logger LOGGER = LoggerFactory.getLogger("OAT");
    public static final WrapAroundList<ServerPlayerEntity> queue = new WrapAroundList<>();
    public static final ArrayList<ServerPlayerEntity> moddedPlayers = new ArrayList<>();
    public final ServerBossBar switchProgress = new ServerBossBar(Text.literal("Time left"), BossBar.Color.GREEN,
            BossBar.Style.PROGRESS);
    public int MAX_TIME = 1000;
    public int time = 0;
    public ServerPlayerEntity selectedPlayer;

    public OATManager() {
        registerCommands();

        ServerPlayNetworking.registerGlobalReceiver(ClientSync.HANDSHAKE, (server, player, handler, buf,
                                                                           responseSender) -> {
            String playerVersion = buf.readString();
            if (!Objects.equals(playerVersion, Main.VERSION)) {
                server.execute(() -> {
                    player.sendMessage(Text.of("One At a Time version mismatch. v%s (you) vs v%s (server)".formatted(playerVersion, Main.VERSION)));
                });
            } else {
                moddedPlayers.add(player);
                LOGGER.info("Added %s to modded list".formatted(player.getName().getString()));
            }
        });
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralCommandNode<ServerCommandSource> oatNode = CommandManager
                    .literal("oat")
                    .build();

            LiteralCommandNode<ServerCommandSource> nextNode = CommandManager
                    .literal("next")
                    .requires(source -> source.hasPermissionLevel(3))
                    .executes(context -> {
                        if (queue.size() > 1 && selectedPlayer != null) {
                            switchPlayer(selectedPlayer, queue.getNext(selectedPlayer));
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
                                switchPlayer(_player, queue.getNext(_player));
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

        ServerPlayNetworking.send(serverPlayer, ClientSync.HANDSHAKE, PacketByteBufs.create());
    }

    public void playerLeft(ServerPlayerEntity serverPlayer) {
        if (selectedPlayer == serverPlayer) {
            LOGGER.info("Player that left was selectedPlayer");
            if (queue.size() > 1) {
                LOGGER.info("Queue was bigger than 1");
                switchPlayer(selectedPlayer, queue.getNext(selectedPlayer));
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
            switchPlayer(selectedPlayer, queue.getNext(selectedPlayer));
            time = 0;
        }

        queue.forEach((player) -> {
            if (player != selectedPlayer) {
                player.interactionManager.changeGameMode(GameMode.SPECTATOR);

                player.teleport(selectedPlayer.getWorld(), selectedPlayer.getX(), selectedPlayer.getY(),
                        selectedPlayer.getZ(), selectedPlayer.getYaw(), selectedPlayer.getPitch());
                player.sendMessage(Text.literal(Formatting.WHITE + "Spectating " + Formatting.AQUA +
                        selectedPlayer.getDisplayName().getString()), true);
            }
        });
    }

    // Sync variables
    private int lastScreenId = -1;
    private int lastSelectedSlot = -1;
    private int lastFoodLevel = -1;
    private float lastSaturationLevel = (float) -1.0;
    private Collection<ItemStack> lastHotbar;

    public void tick() {
        queue.forEach((player) -> {
            if (player != selectedPlayer) {
                // Hotbar slot
                if (lastSelectedSlot != selectedPlayer.getInventory().selectedSlot ||
                        player.getInventory().selectedSlot != selectedPlayer.getInventory().selectedSlot) {
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeInt(selectedPlayer.getId());
                    buf.writeInt(selectedPlayer.getInventory().selectedSlot);
                    ServerPlayNetworking.send(player, ClientSync.CHANGE_HOTBAR_SLOT, buf);

                    lastSelectedSlot = selectedPlayer.getInventory().selectedSlot;
                }

                // Hotbar items
                {
                    Collection<ItemStack> hotbar = selectedPlayer.getInventory().main.subList(0,
                            PlayerInventory.getHotbarSize());
                    Collection<ItemStack> invToSend = new ArrayList<>(hotbar);
                    invToSend.add(selectedPlayer.getInventory().getStack(PlayerInventory.OFF_HAND_SLOT));

                    if (lastHotbar != invToSend) {
                        PacketByteBuf buf = PacketByteBufs.create();
                        buf.writeInt(selectedPlayer.getId());
                        buf.writeCollection(invToSend, PacketByteBuf::writeItemStack);
                        ServerPlayNetworking.send(player, ClientSync.CHANGE_HOTBAR_INVENTORY, buf);

                        lastHotbar = invToSend;
                    }
                }

                // Level
                {
                    int exp1 = player.experienceLevel;
                    int exp2 = selectedPlayer.experienceLevel;
                    if (exp1 != exp2 || player.experienceProgress != selectedPlayer.experienceProgress) {
                        player.experienceProgress = selectedPlayer.experienceProgress;
                        player.setExperienceLevel(selectedPlayer.experienceLevel);
                    }
                }

                // Food
                int selectedFood = selectedPlayer.getHungerManager().getFoodLevel();
                int playerFood = player.getHungerManager().getFoodLevel();
                float selectedSaturation = player.getHungerManager().getFoodLevel();
                float playerSaturation = player.getHungerManager().getFoodLevel();
                if (lastFoodLevel != selectedFood || lastSaturationLevel != selectedSaturation ||
                        playerFood != selectedFood || playerSaturation != selectedSaturation) {
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeInt(selectedPlayer.getId());
                    buf.writeInt(selectedPlayer.getHungerManager().getFoodLevel());
                    buf.writeFloat(selectedPlayer.getHungerManager().getSaturationLevel());
                    ServerPlayNetworking.send(player, ClientSync.UPDATE_FOOD, buf);

                    lastFoodLevel = selectedPlayer.getHungerManager().getFoodLevel();
                    lastSaturationLevel = selectedPlayer.getHungerManager().getSaturationLevel();
                }

                if (player.getCameraEntity() != selectedPlayer) {
                    player.setCameraEntity(selectedPlayer);
                }

                if (!ClientSync.sameInventory(selectedPlayer, player)) {
                    ClientSync.copyInventory(selectedPlayer, player);
                }

                if (!ClientSync.sameEffects(selectedPlayer, player)) {
                    ClientSync.copyEffects(selectedPlayer, player);
                }

                if (lastScreenId != selectedPlayer.currentScreenHandler.syncId) {
                    ClientSync.copyScreen(selectedPlayer, player);
                    lastScreenId = selectedPlayer.currentScreenHandler.syncId;
                }

                if (!moddedPlayers.contains(player)) {
                    player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket
                            .GAME_MODE_CHANGED, GameMode.SURVIVAL.getId()));
                }
            }
        });
    }

    private void switchPlayer(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        ServerWorld world = oldPlayer.getWorld();
        Vec3d pos = oldPlayer.getPos();
        float yaw = oldPlayer.getYaw();
        float pitch = oldPlayer.getPitch();
        float health = oldPlayer.getHealth();
        int food = oldPlayer.getHungerManager().getFoodLevel();
        float saturation = oldPlayer.getHungerManager().getSaturationLevel();

        oldPlayer.changeGameMode(GameMode.SPECTATOR);
        oldPlayer.getAbilities().flying = true;
        oldPlayer.closeHandledScreen();

        selectedPlayer = newPlayer;

        ClientSync.copyInventory(oldPlayer, newPlayer);
        newPlayer.teleport(world, pos.x, pos.y, pos.z, yaw, pitch);
        newPlayer.changeGameMode(GameMode.SURVIVAL);
        newPlayer.getAbilities().flying = false;
        newPlayer.setHealth(health);
        newPlayer.getHungerManager().setFoodLevel(food);
        newPlayer.getHungerManager().setSaturationLevel(saturation);
        newPlayer.sendMessage(Text.literal(Formatting.GOLD + "You are playing!"), true);
    }
}
