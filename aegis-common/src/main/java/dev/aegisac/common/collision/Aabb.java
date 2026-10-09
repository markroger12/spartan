package dev.aegisac.common.collision;
/** Half-open geometry: touching faces are contact, not overlap. */
public record Aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    public Aabb {
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                || minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Invalid box");
    }
    public static Aabb player(Vec3 feet, double height) {
        return new Aabb(feet.x() - .3, feet.y(), feet.z() - .3, feet.x() + .3, feet.y() + height, feet.z() + .3);
    }
    public Aabb move(Vec3 d) { return new Aabb(minX+d.x(), minY+d.y(), minZ+d.z(), maxX+d.x(), maxY+d.y(), maxZ+d.z()); }
    public Aabb expand(double x, double y, double z) { return new Aabb(minX-x,minY-y,minZ-z,maxX+x,maxY+y,maxZ+z); }
    public Aabb sweep(Vec3 d) {
        return new Aabb(minX+Math.min(0,d.x()),minY+Math.min(0,d.y()),minZ+Math.min(0,d.z()),
                maxX+Math.max(0,d.x()),maxY+Math.max(0,d.y()),maxZ+Math.max(0,d.z()));
    }
    public boolean intersects(Aabb b) {
        return maxX>b.minX && minX<b.maxX && maxY>b.minY && minY<b.maxY && maxZ>b.minZ && minZ<b.maxZ;
    }
    public boolean contains(Aabb b) {
        return minX<=b.minX && minY<=b.minY && minZ<=b.minZ && maxX>=b.maxX && maxY>=b.maxY && maxZ>=b.maxZ;
    }
    /** Clip one axis against this obstacle, after the preceding axes have been resolved. */
    public double clip(Aabb moving, double delta, int axis) {
        if (axis == 0 && !(moving.maxY>minY && moving.minY<maxY && moving.maxZ>minZ && moving.minZ<maxZ)) return delta;
        if (axis == 1 && !(moving.maxX>minX && moving.minX<maxX && moving.maxZ>minZ && moving.minZ<maxZ)) return delta;
        if (axis == 2 && !(moving.maxX>minX && moving.minX<maxX && moving.maxY>minY && moving.minY<maxY)) return delta;
        double low = axis==0?minX:axis==1?minY:minZ, high = axis==0?maxX:axis==1?maxY:maxZ;
        double from = axis==0?moving.minX:axis==1?moving.minY:moving.minZ;
        double to = axis==0?moving.maxX:axis==1?moving.maxY:moving.maxZ;
        if (delta>0 && to<=low) return Math.min(delta, low-to);
        if (delta<0 && from>=high) return Math.max(delta, high-from);
        return delta;
    }
}
