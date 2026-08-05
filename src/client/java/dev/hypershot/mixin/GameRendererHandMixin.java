package dev.hypershot.mixin;

import dev.hypershot.HyperShotClient;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererHandMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void hypershot$hideHand(CameraRenderState cameraState, float deltaPartialTick, Matrix4fc modelViewMatrix, CallbackInfo ci) {
        if (HyperShotClient.isInitialized() && HyperShotClient.captureManager().shouldHideHand()) ci.cancel();
    }
}
