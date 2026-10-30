package ypmods.tfcwildlife.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Senses {
    public enum Sound { STEP, SPRINT, LANDING, GUNSHOT, EXPLOSION }
    public record Heard(boolean accepted, double intensity, double fear, Sound sound, Point position, long tick) {}
    private record Key(UUID source, Sound sound) {}
    private final Map<Key, Long> lastHeard = new HashMap<>();

    public Heard hear(UUID source, Sound sound, Point location, Point listener, boolean occluded,
                      double sensitivity, long tick) {
        var key = new Key(source, sound);
        int cooldown = sound == Sound.GUNSHOT ? 2 : 8;
        long prior = lastHeard.getOrDefault(key, Long.MIN_VALUE / 2);
        double range = switch (sound) {
            case STEP -> 14; case SPRINT -> 24; case LANDING -> 28; case GUNSHOT -> 96; case EXPLOSION -> 112;
        };
        double distance = Math.sqrt(location.distanceSquared(listener));
        double intensity = Math.clamp((1 - distance / range) * sensitivity * (occluded ? 0.3 : 1), 0, 1);
        if (tick - prior < cooldown || intensity <= 0) return new Heard(false, 0, 0, sound, location, tick);
        lastHeard.put(key, tick);
        lastHeard.entrySet().removeIf(e -> tick - e.getValue() > 200);
        double fear = switch (sound) {
            case GUNSHOT, EXPLOSION -> intensity;
            default -> 0;
        };
        return new Heard(true, intensity, fear, sound, location, tick);
    }

    public static boolean visible(Point observer, Point facing, Point target, boolean lineOfSight,
                                  double fovDegrees, double radius) {
        Point delta = target.subtract(observer);
        return lineOfSight && delta.lengthSquared() <= radius * radius
            && (delta.lengthSquared() < 2.25 || facing.horizontalAngle(delta) <= fovDegrees / 2);
    }
}
