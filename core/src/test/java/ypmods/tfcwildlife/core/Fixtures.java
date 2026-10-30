package ypmods.tfcwildlife.core;

import java.util.Optional;
import java.util.UUID;

final class Fixtures {
    static final UUID ANIMAL = new UUID(0, 1), PLAYER = new UUID(0, 100);
    static final Settings CFG = Settings.defaults();
    static final Personality TRAITS = new Personality(1, 0.5, 1);
    static Perception.Threat player(long tick, double hp, double distance) {
        return new Perception.Threat(PLAYER, new Point(distance, 64, 0), hp, true, true,
            false, tick, true, false, true, true);
    }
    static Perception perception(long tick, Species species, double hp, double hunger, double fear,
                                 Perception.Social social, double roll) {
        return new Perception(tick, ANIMAL, species, new Point(0, 64, 0), false, hp, hunger, 1, fear,
            false, false, false, TRAITS, Optional.of(player(tick, 1, 7.2)), Optional.empty(), Optional.empty(), social, roll);
    }
    static Perception withThreat(Perception p, Optional<Perception.Threat> t) {
        return new Perception(p.tick(), p.animal(), p.species(), p.position(), p.baby(), p.ownHp(), p.hunger(),
            p.stamina(), p.fear(), p.lethalDanger(), p.provoked(), p.offspringNearby(), p.personality(), t, p.prey(), p.parent(), p.social(), p.huntRoll());
    }
    static Decision attacking() {
        return new Decision(Intent.ATTACK, "combat", "defense", 90, 190, Optional.of(PLAYER));
    }
}
