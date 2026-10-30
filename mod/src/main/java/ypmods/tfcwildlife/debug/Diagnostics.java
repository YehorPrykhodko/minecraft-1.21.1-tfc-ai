package ypmods.tfcwildlife.debug;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import ypmods.tfcwildlife.TFCWildlifeAI;
import ypmods.tfcwildlife.config.WildlifeConfig;
import ypmods.tfcwildlife.core.*;

public final class Diagnostics {
    private final Set<UUID> selected = new HashSet<>();
    public void select(UUID id) { selected.add(id); }
    public void off() { selected.clear(); }
    public void emit(Perception p, Decision before, DecisionReducer.Result result, NavigationExecutor.Outcome nav,
                     Settings settings, String event) {
        if (!selected.contains(p.animal()) && !WildlifeConfig.DEBUG.get()) return;
        boolean transition = !before.equals(result.decision());
        if (transition || (!nav.success() && !nav.cause().equals("repath_cooldown")) || nav.turnDegrees() > 90 || p.tick() % 20 == 0) {
            TFCWildlifeAI.LOGGER.info("[TFC-WILDLIFE] {}", DecisionTrace.json(p, before, result, nav, settings, event));
        }
    }
}
