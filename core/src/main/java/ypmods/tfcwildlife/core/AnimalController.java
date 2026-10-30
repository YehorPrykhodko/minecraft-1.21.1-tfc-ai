package ypmods.tfcwildlife.core;

import java.util.UUID;
import java.util.function.DoubleSupplier;

public final class AnimalController {
    private final UUID id;
    private final DoubleSupplier random;
    private final DecisionReducer reducer = new DecisionReducer();
    private Decision decision = Decision.idle(0);
    private long lastTick = Long.MIN_VALUE;
    private long nextEncounterRoll;
    private double encounterRoll = 1;

    public AnimalController(UUID id, DoubleSupplier random) { this.id = id; this.random = random; }
    public Decision decision() { return decision; }
    public double encounterRoll(long tick, boolean eligible, Settings cfg) {
        if (eligible && tick >= nextEncounterRoll) {
            encounterRoll = random.getAsDouble();
            if (!Double.isFinite(encounterRoll) || encounterRoll < 0 || encounterRoll >= 1) {
                throw new IllegalStateException("Source aléatoire invalide");
            }
            nextEncounterRoll = tick + cfg.encounterCooldown();
            return encounterRoll;
        }
        return 1;
    }
    public DecisionReducer.Result tick(Perception p, Settings cfg) {
        if (!id.equals(p.animal()) || p.tick() <= lastTick) throw new IllegalStateException("Un seul engagement par animal et par tick");
        lastTick = p.tick();
        var result = reducer.reduce(decision, p, SpeciesRules.propose(p, decision, cfg), cfg);
        decision = result.decision();
        return result;
    }
}
