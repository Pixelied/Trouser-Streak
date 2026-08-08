package dev.hypershot.core.camera;

public record CameraSceneSignature(double x, double y, double z, float yaw, float pitch, float fov) {
    private static final double POSITION_EPSILON = 0.001;
    private static final float ANGLE_EPSILON = 0.02f;
    private static final float FOV_EPSILON = 0.02f;

    public CameraSceneSignature {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(fov)) {
            throw new IllegalArgumentException("Camera signature values must be finite");
        }
    }

    public boolean meaningfullyDiffers(CameraSceneSignature other) {
        if (other == null) return true;
        return Math.abs(x - other.x) > POSITION_EPSILON
                || Math.abs(y - other.y) > POSITION_EPSILON
                || Math.abs(z - other.z) > POSITION_EPSILON
                || angularDistance(yaw, other.yaw) > ANGLE_EPSILON
                || Math.abs(pitch - other.pitch) > ANGLE_EPSILON
                || Math.abs(fov - other.fov) > FOV_EPSILON;
    }

    private static float angularDistance(float a, float b) {
        float delta = Math.abs(a - b) % 360.0f;
        return delta > 180.0f ? 360.0f - delta : delta;
    }
}
