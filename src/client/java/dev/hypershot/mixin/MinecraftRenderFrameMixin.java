package dev.hypershot.mixin;

import dev.hypershot.HyperShotClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftRenderFrameMixin {
    @Inject(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V",
                    shift = At.Shift.AFTER
            )
    )
    private void hypershot$renderCapturePass(boolean advanceGameTime, CallbackInfo ci) {
        HyperShotClient.captureManager().renderCapturePass((Minecraft) (Object) this, advanceGameTime);
    }
}
