package dev.hypershot.mixin;

import dev.hypershot.HyperShotClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void hypershot$notificationClick(long handle, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS && HyperShotClient.isInitialized()
                && HyperShotClient.notifications().handleMouseClick(button.button())) {
            ci.cancel();
        }
    }
}
