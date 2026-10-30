package ypmods.tfcwildlife.core;

public record Point(double x, double y, double z) {
    public Point {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Position non finie");
        }
    }
    public Point add(Point p) { return new Point(x + p.x, y + p.y, z + p.z); }
    public Point subtract(Point p) { return new Point(x - p.x, y - p.y, z - p.z); }
    public Point scale(double n) { return new Point(x * n, y * n, z * n); }
    public double lengthSquared() { return x * x + y * y + z * z; }
    public double distanceSquared(Point p) { return subtract(p).lengthSquared(); }
    public Point horizontalUnit() {
        double length = Math.hypot(x, z);
        return length < 1e-9 ? new Point(1, 0, 0) : new Point(x / length, 0, z / length);
    }
    public double horizontalAngle(Point other) {
        double n = Math.hypot(x, z) * Math.hypot(other.x, other.z);
        return n < 1e-9 ? 0 : Math.toDegrees(Math.acos(Math.clamp((x * other.x + z * other.z) / n, -1, 1)));
    }
}
