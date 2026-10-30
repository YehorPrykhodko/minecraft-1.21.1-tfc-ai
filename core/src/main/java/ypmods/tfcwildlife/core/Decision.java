package ypmods.tfcwildlife.core;

import java.util.Optional;
import java.util.UUID;

public record Decision(Intent intent, String reason, String source, long since, long lockedUntil,
                       Optional<UUID> target) {
    public static Decision idle(long tick) { return new Decision(Intent.IDLE, "init", "reducer", tick, tick, Optional.empty()); }
}
