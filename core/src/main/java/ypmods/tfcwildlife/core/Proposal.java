package ypmods.tfcwildlife.core;

import java.util.Objects;
import java.util.Optional;

public record Proposal(Intent intent, String reason, String source, int priority, int holdTicks,
                       boolean interrupts, Optional<Perception.Threat> target) {
    public Proposal {
        Objects.requireNonNull(intent); Objects.requireNonNull(reason); Objects.requireNonNull(source);
        Objects.requireNonNull(target);
        if (holdTicks < 0) throw new IllegalArgumentException("Durée négative");
    }
}
