package ypmods.tfcwildlife.core;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record Perception(long tick, UUID animal, Species species, Point position, boolean baby,
                         double ownHp, double hunger, double stamina, double fear,
                         boolean lethalDanger, boolean provoked, boolean offspringNearby,
                         Personality personality, Optional<Threat> threat,
                         Optional<Threat> prey, Optional<Threat> parent, Social social, double huntRoll) {
    public record Threat(UUID id, Point position, double hp, boolean player, boolean visible,
                         boolean heard, long observedTick, boolean approaching, boolean sprinting,
                         boolean watching, boolean attackable) {
        public Threat { Objects.requireNonNull(id); Objects.requireNonNull(position); fraction(hp); }
    }
    public record Social(UUID packId, int adults, boolean hunt, Optional<Threat> target, long until, boolean defending) {
        public Social(UUID packId, int adults, boolean hunt, Optional<Threat> target, long until) {
            this(packId, adults, hunt, target, until, false);
        }
        public static Social solo() { return new Social(null, 1, false, Optional.empty(), 0); }
        public Social {
            Objects.requireNonNull(target);
            if (adults < 0 || (hunt && (packId == null || adults < 2 || target.isEmpty()))) {
                throw new IllegalArgumentException("Meute invalide");
            }
        }
        public boolean pack() { return packId != null && adults >= 2; }
    }
    public Perception {
        Objects.requireNonNull(animal); Objects.requireNonNull(species); Objects.requireNonNull(position);
        Objects.requireNonNull(personality); Objects.requireNonNull(threat); Objects.requireNonNull(prey);
        Objects.requireNonNull(parent); Objects.requireNonNull(social);
        for (double value : new double[]{ownHp, hunger, stamina, fear, huntRoll}) fraction(value);
    }
    private static void fraction(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Fraction invalide");
    }
    public Optional<Threat> knownThreat(Settings settings) {
        return threat.filter(t -> t.attackable && tick >= t.observedTick && tick - t.observedTick <= settings.memoryTicks());
    }
    public double distance(Threat target) { return Math.sqrt(position.distanceSquared(target.position)); }
}
