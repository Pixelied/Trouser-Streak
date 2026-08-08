package dev.hypershot.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.hypershot.capture.CaptureRenderContext;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererTargetMixin {
    @Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void hypershot$useCaptureTarget(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget captureTarget = CaptureRenderContext.currentTarget();
        if (captureTarget != null) cir.setReturnValue(captureTarget);
    }
}
