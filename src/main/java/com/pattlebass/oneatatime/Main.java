package com.pattlebass.oneatatime;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("OAT");

    @Override
    public void onInitialize() {
        //if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) return;

        OATManager oatManager = new OATManager();

        EndTickScheduler endTick = new EndTickScheduler();
        endTick.oatManager = oatManager;

        ServerTickEvents.END_WORLD_TICK.register(endTick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            oatManager.playerJoined(handler.getPlayer());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            oatManager.playerLeft(handler.getPlayer());
        });
        ServerPlayerEvents.COPY_FROM.register(oatManager::playerDied);
    }
}
