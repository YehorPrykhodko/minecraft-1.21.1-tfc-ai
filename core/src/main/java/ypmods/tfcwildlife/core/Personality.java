package ypmods.tfcwildlife.core;

import java.util.SplittableRandom;
import java.util.UUID;

public record Personality(double courage, double aggression, double speed) {
    public static Personality from(UUID id) {
        var random = new SplittableRandom(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 19));
        return new Personality(random.nextDouble(0.8, 1.2), random.nextDouble(0.0, 1.0), random.nextDouble(0.96, 1.04));
    }
}
