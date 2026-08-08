package dev.hypershot.shot;

import net.minecraft.client.Minecraft;

import java.util.Objects;

/** Client-side composition lock for Cinematic mode. The lock never writes persistent camera options. */
public final class CameraLockController {
    private boolean locked;
    private boolean lockPosition;
    private boolean lockFov;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private float fov;

    public void lock(Minecraft minecraft, boolean lockPosition, boolean lockFov) {
        Objects.requireNonNull(minecraft);
        if (minecraft.player == null) throw new IllegalStateException("Player is unavailable");
        this.lockPosition = lockPosition;
        this.lockFov = lockFov;
        this.x = minecraft.player.getX();
        this.y = minecraft.player.getY();
        this.z = minecraft.player.getZ();
        this.yaw = minecraft.player.getYRot();
        this.pitch = minecraft.player.getXRot();
        this.fov = minecraft.gameRenderer.mainCamera().getFov();
        this.locked = lockPosition || lockFov;
    }

    public void tick(Minecraft minecraft) {
        if (!locked || minecraft == null || minecraft.player == null) return;
        if (lockPosition) {
            minecraft.player.setPos(x, y, z);
            minecraft.player.setYRot(yaw);
            minecraft.player.setXRot(pitch);
        }
    }

    public void unlock() {
        locked = false;
        lockPosition = false;
        lockFov = false;
    }

    public boolean locked() { return locked; }
    public boolean positionLocked() { return locked && lockPosition; }
    public boolean fovLocked() { return locked && lockFov; }
    public float lockedFov() { return fov; }
}
