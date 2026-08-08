package dev.hypershot.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.hypershot.HyperShotClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;

@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {
    @Inject(method = "grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private static void hypershot$replaceVanillaF2(File workDir, RenderTarget target, Consumer<Component> callback, CallbackInfo ci) {
        if (!HyperShotClient.isInitialized() || !HyperShotClient.config().replaceVanillaF2) return;
        Minecraft minecraft = Minecraft.getInstance();
        // Only intercept the real configured screenshot key. Programmatic Screenshot.grab callers remain untouched.
        if (!minecraft.options.keyScreenshot.isDown()) return;
        HyperShotClient.onVanillaScreenshotKeyPressed();
        ci.cancel();
    }
}
