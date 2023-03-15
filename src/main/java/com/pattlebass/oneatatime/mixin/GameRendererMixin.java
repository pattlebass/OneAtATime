package com.pattlebass.oneatatime.mixin;

import com.pattlebass.oneatatime.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Shadow
    MinecraftClient client;

    @Redirect(at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;getCurrentGameMode()" +
                    "Lnet/minecraft/world/GameMode;"),
            method = "renderHand(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/Camera;F)V"
    )
    private GameMode fakeGamemode(ClientPlayerInteractionManager interactionManager) {
        if (this.client.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR &&
                this.client.getCameraEntity() instanceof OtherClientPlayerEntity spectatedPlayer) {
            if (spectatedPlayer.isSpectator()) {
                return GameMode.SPECTATOR;
            } else if (spectatedPlayer.isCreative()) {
                return GameMode.CREATIVE;
            } else {
                return GameMode.ADVENTURE;
            }
        }
        return this.client.interactionManager.getCurrentGameMode();
    }
}

