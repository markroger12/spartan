package dev.aegisac.common.collision;
/** Finite immutable coordinates; no platform dependency. */
public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public Vec3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Non-finite vector");
    }
    public Vec3 add(Vec3 v) { return new Vec3(x + v.x, y + v.y, z + v.z); }
    public Vec3 subtract(Vec3 v) { return new Vec3(x - v.x, y - v.y, z - v.z); }
    public double horizontalSquared() { return x * x + z * z; }
    public double lengthSquared() { return x * x + y * y + z * z; }
}
