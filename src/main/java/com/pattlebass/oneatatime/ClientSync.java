package com.pattlebass.oneatatime;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.screen.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ClientSync {
    public static final Logger LOGGER = LoggerFactory.getLogger("OAT");
    public static final Identifier HANDSHAKE = new Identifier("oat:handshake");

    public static final Identifier CHANGE_HOTBAR_SLOT = new Identifier("oat:change_hotbar_slot");
    public static final Identifier CHANGE_HOTBAR_INVENTORY = new Identifier("oat:change_hotbar_inventory");
    public static final Identifier UPDATE_FOOD = new Identifier("oat:update_food");

    ClientSync() {
        ClientPlayNetworking.registerGlobalReceiver(HANDSHAKE, (client, handler, buf, responseSender) -> {
            PacketByteBuf buf2 = PacketByteBufs.create();
            buf2.writeString(Main.VERSION);
            responseSender.sendPacket(HANDSHAKE, buf2);
        });

        ClientPlayNetworking.registerGlobalReceiver(CHANGE_HOTBAR_SLOT, (client, handler, buf, responseSender) -> {
            if (client.world == null)
                return;

            OtherClientPlayerEntity player = (OtherClientPlayerEntity) client.world.getEntityById(buf.readInt());

            if (player != null) {
                player.getInventory().selectedSlot = buf.readInt();
                client.player.getInventory().selectedSlot = player.getInventory().selectedSlot;
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(CHANGE_HOTBAR_INVENTORY, (client, handler, buf, responseSender) -> {
            if (client.world == null)
                return;

            OtherClientPlayerEntity player = (OtherClientPlayerEntity) client.world.getEntityById(buf.readInt());
            List<ItemStack> items = buf.readCollection(DefaultedList::ofSize, PacketByteBuf::readItemStack);

            if (player != null) {
                for (int i = 0; i < PlayerInventory.getHotbarSize(); i++) {
                    player.getInventory().setStack(i, items.get(i));
                }
                player.getInventory().setStack(PlayerInventory.OFF_HAND_SLOT,
                        items.get(PlayerInventory.getHotbarSize()));
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(UPDATE_FOOD, (client, handler, buf, responseSender) -> {
            if (client.world == null)
                return;

            OtherClientPlayerEntity player = (OtherClientPlayerEntity) client.world.getEntityById(buf.readInt());

            if (player != null) {
                player.getHungerManager().setFoodLevel(buf.readInt());
                player.getHungerManager().setSaturationLevel(buf.readFloat());
            }
        });
    }

    public static void copyInventory(ServerPlayerEntity from, ServerPlayerEntity to) {
        for (int i = 0; i < 41; i++) {
            ItemStack stack = from.getInventory().getStack(i);
            to.getInventory().setStack(i, stack);
        }
    }

    public static boolean sameInventory(ServerPlayerEntity from, ServerPlayerEntity to) {
        for (int i = 0; i < 41; i++) {
            if (from.getInventory().getStack(i) != to.getInventory().getStack(i)) return false;
        }
        return true;
    }

    public static void copyEffects(ServerPlayerEntity from, ServerPlayerEntity to) {
        to.clearStatusEffects();
        from.getStatusEffects().forEach((effect) -> {
            to.addStatusEffect(new StatusEffectInstance(effect.getEffectType(), effect.getDuration(),
                    effect.getAmplifier()));
        });
    }

    public static boolean sameEffects(ServerPlayerEntity from, ServerPlayerEntity to) {
        return from.getStatusEffects().containsAll(to.getStatusEffects()) &&
                to.getStatusEffects().containsAll(from.getStatusEffects());
    }

    public static void copyScreen(ServerPlayerEntity from, ServerPlayerEntity to) {
        if (from.currentScreenHandler == from.playerScreenHandler) {
            to.closeHandledScreen();
            return;
        }

        if (from.currentScreenHandler instanceof CraftingScreenHandler) {
            to.openHandledScreen(
                    new SimpleNamedScreenHandlerFactory((i, playerInventory, playerEntity) -> {
                        return new CraftingScreenHandler(i, playerInventory);
                    }, Text.translatable("container.crafting"))
            );
        } else if (from.currentScreenHandler instanceof FurnaceScreenHandler) {
            to.openHandledScreen(
                    new SimpleNamedScreenHandlerFactory((i, playerInventory, playerEntity) -> {
                        return new FurnaceScreenHandler(i, playerInventory);
                    }, Text.translatable("container.furnace"))
            );
        } else if (from.currentScreenHandler instanceof ShulkerBoxScreenHandler) {
            to.openHandledScreen(
                    new SimpleNamedScreenHandlerFactory((i, playerInventory, playerEntity) -> {
                        return new ShulkerBoxScreenHandler(i, playerInventory);
                    }, Text.translatable("container.shulkerBox"))
            );
        } else if (from.currentScreenHandler instanceof AnvilScreenHandler) {
            to.openHandledScreen(
                    new SimpleNamedScreenHandlerFactory((i, playerInventory, playerEntity) -> {
                        return new AnvilScreenHandler(i, playerInventory);
                    }, Text.translatable("container.repair"))
            );
        } else if (from.currentScreenHandler instanceof MerchantScreenHandler) {
            to.openHandledScreen(
                    new SimpleNamedScreenHandlerFactory((i, playerInventory, playerEntity) -> {
                        return new MerchantScreenHandler(i, playerInventory);
                    }, Text.translatable("merchant.trades"))
            );
        } else if (from.currentScreenHandler instanceof GenericContainerScreenHandler) {
            Inventory inventory =
                    ((GenericContainerScreenHandler) from.currentScreenHandler).getInventory();
            if (from.currentScreenHandler.getType() == ScreenHandlerType.GENERIC_9X3) {
                to.openHandledScreen(
                        new SimpleNamedScreenHandlerFactory((i, playerInventory, playerEntity) -> {
                            return GenericContainerScreenHandler.createGeneric9x3(i, playerInventory, inventory);
                        }, Text.of("Container"))
                );
            } else if (from.currentScreenHandler.getType() == ScreenHandlerType.GENERIC_9X6) {
                to.openHandledScreen(
                        new SimpleNamedScreenHandlerFactory((i, playerInventory, playerEntity) -> {
                            return GenericContainerScreenHandler.createGeneric9x6(i, playerInventory, inventory);
                        }, Text.of(""))
                );
            }
        } else {
            ScreenHandler screenHandler = from.currentScreenHandler;

            to.networkHandler.sendPacket(new OpenScreenS2CPacket(screenHandler.syncId, screenHandler.getType(),
                    Text.of("Container")));
            to.currentScreenHandler = screenHandler;
        }
    }
}
