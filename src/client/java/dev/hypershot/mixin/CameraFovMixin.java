package dev.hypershot.mixin;

import dev.hypershot.HyperShotClient;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraFovMixin {
    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    private void hypershot$lockCinematicFov(CallbackInfoReturnable<Float> cir) {
        if (!HyperShotClient.isInitialized()) return;
        var lock = HyperShotClient.cameraLockController();
        if (lock != null && lock.fovLocked()) cir.setReturnValue(lock.lockedFov());
    }
}
