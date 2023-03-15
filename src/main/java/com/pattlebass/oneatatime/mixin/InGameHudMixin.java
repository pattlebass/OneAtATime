package com.pattlebass.oneatatime.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InGameHud.class)
abstract class InGameHudMixin {
    @Shadow
    MinecraftClient client;

    @Redirect(at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;getCurrentGameMode()" +
                    "Lnet/minecraft/world/GameMode;"),
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;F)V"
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

    @Redirect(at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;hasStatusBars()Z"),
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;F)V"
    )
    private boolean hasStatusBarsInjected(ClientPlayerInteractionManager interactionManager) {
        return this.client.interactionManager.hasStatusBars() || isSpectatingPlayer();
    }

    @Redirect(at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;hasExperienceBar()Z"),
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;F)V"
    )
    private boolean hasExperienceBarInjected(ClientPlayerInteractionManager interactionManager) {
        return this.client.interactionManager.hasStatusBars() || isSpectatingPlayer();
    }

    private boolean isSpectatingPlayer() {
        return this.client.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR &&
                this.client.getCameraEntity() instanceof OtherClientPlayerEntity;
    }
}
