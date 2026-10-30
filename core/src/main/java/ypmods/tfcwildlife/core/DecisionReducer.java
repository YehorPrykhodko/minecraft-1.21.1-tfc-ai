package ypmods.tfcwildlife.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class DecisionReducer {
    public record Evaluation(Proposal proposal, boolean accepted, String veto) {}
    public record Result(Decision decision, List<Evaluation> evaluations) {}

    public Result reduce(Decision before, Perception p, List<Proposal> proposals, Settings cfg) {
        var sorted = proposals.stream().sorted(Comparator.comparingInt(Proposal::priority).reversed()
            .thenComparing(Proposal::source).thenComparing(Proposal::reason)).toList();
        var evaluations = new ArrayList<Evaluation>();
        Proposal winner = null;
        for (Proposal proposal : sorted) {
            String veto = veto(before, p, proposal, cfg);
            if (veto.isEmpty() && winner != null) veto = "lower_priority";
            boolean accepted = veto.isEmpty();
            evaluations.add(new Evaluation(proposal, accepted, veto));
            if (accepted) winner = proposal;
        }
        if (winner == null) return new Result(before, List.copyOf(evaluations));
        Optional<java.util.UUID> target = winner.target().map(Perception.Threat::id);
        // Une proposition identique ne renouvelle pas le verrou ni sa raison.
        if (before.intent() == winner.intent() && before.target().equals(target)) {
            return new Result(before, List.copyOf(evaluations));
        }
        return new Result(new Decision(winner.intent(), winner.reason(), winner.source(), p.tick(),
            p.tick() + winner.holdTicks(), target), List.copyOf(evaluations));
    }

    private String veto(Decision before, Perception p, Proposal candidate, Settings cfg) {
        if (candidate.intent().combat() && (candidate.target().isEmpty() || !candidate.target().get().attackable())) {
            return "invalid_target";
        }
        if (candidate.interrupts()) return "";
        boolean validTarget = before.target().isPresent() && (
            p.knownThreat(cfg).map(t -> before.target().get().equals(t.id())).orElse(false)
            || p.prey().filter(t -> t.attackable() && p.tick() - t.observedTick() <= cfg.memoryTicks())
                .map(t -> before.target().get().equals(t.id())).orElse(false)
            || p.social().target().filter(t -> t.attackable() && p.social().until() > p.tick())
                .map(t -> before.target().get().equals(t.id())).orElse(false));
        boolean packExpired = before.source().equals("pack") && (!p.social().hunt() || p.social().until() <= p.tick());
        if (before.intent() == Intent.FLEE && candidate.intent() != Intent.FLEE
            && (p.tick() < before.lockedUntil() || p.fear() > cfg.panicExit()
                || (p.ownHp() <= cfg.criticalHp() && p.knownThreat(cfg).isPresent()))) return "panic_persistence";
        if (before.intent().combat() && validTarget && !packExpired) {
            if (candidate.intent() != before.intent() || !candidate.target().map(Perception.Threat::id).equals(before.target())) {
                return "combat_commitment";
            }
        }
        if (before.intent() != Intent.FLEE && p.tick() < before.lockedUntil()
            && (!before.intent().combat() || validTarget) && !packExpired
            && candidate.priority() < 600 && candidate.intent() != before.intent()) return "minimum_hold";
        return "";
    }
}
