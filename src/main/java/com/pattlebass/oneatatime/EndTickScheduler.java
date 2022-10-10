package com.pattlebass.oneatatime;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class EndTickScheduler implements ServerTickEvents.EndWorldTick {
    public static final Logger LOGGER = LoggerFactory.getLogger("OAT");
    public OATManager oatManager;
    public long lastTime = -1;

    @Override
    public void onEndTick(ServerWorld world) {
        if (lastTime != world.getTime()) {
            if (world.getTime() % 20 == 0) {
                oatManager.secondPassed();
                lastTime = world.getTime();
            }
            oatManager.tick();
        }
    }
}

