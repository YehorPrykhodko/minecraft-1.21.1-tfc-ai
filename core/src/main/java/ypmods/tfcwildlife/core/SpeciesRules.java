package ypmods.tfcwildlife.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SpeciesRules {
    private SpeciesRules() {}

    public static List<Proposal> propose(Perception p, Decision previous, Settings cfg) {
        var result = new ArrayList<Proposal>();
        var threat = p.knownThreat(cfg);
        if (p.lethalDanger() || (p.ownHp() <= cfg.criticalHp() && threat.isPresent())) {
            result.add(new Proposal(Intent.FLEE, p.lethalDanger() ? "lethal_danger" : "critical_injury",
                "survival", 1000, cfg.panicHold(), true, threat));
        }
        if (p.fear() >= cfg.panicEnter() * p.personality().courage()) {
            result.add(new Proposal(Intent.FLEE, "verified_panic", "survival", 950, cfg.panicHold(), true, threat));
        }
        if (p.stamina() < 0.12 && threat.isPresent()) {
            result.add(new Proposal(Intent.RETREAT, "exhausted", "survival", 850, cfg.retreatHold(), true, threat));
        }
        if (threat.isPresent()) {
            Perception.Threat t = threat.get();
            double distance = p.distance(t);
            if (!p.baby() && p.species().predator() && p.provoked()) {
                result.add(candidate(Intent.ATTACK, "direct_self_defense", "defense", 800, cfg.combatHold(), t));
            }
            if (!p.baby() && p.social().defending() && p.social().hunt() && p.social().until() > p.tick()) {
                p.social().target().ifPresent(tgt -> result.add(candidate(Intent.ATTACK, "pack_self_defense",
                    "pack", 780, cfg.combatHold(), tgt)));
            }
            if (!p.baby() && p.offspringNearby() && t.visible() && distance < 6 && p.species() != Species.PREY) {
                result.add(candidate(Intent.ATTACK, "offspring_defense", "defense", 760, cfg.combatHold(), t));
            }
            if (p.species() == Species.HYENA && !p.baby()) {
                if (p.social().hunt() && p.social().until() > p.tick()) {
                    p.social().target().ifPresent(shared -> result.add(candidate(Intent.ATTACK,
                        "pack_player_hp", "pack", 700, cfg.combatHold(), shared)));
                } else if (!p.social().pack() && p.hunger() >= cfg.soloHunger()
                    && p.huntRoll() < cfg.soloChance() * (0.5 + p.personality().aggression())
                    && t.visible() && distance < 24) {
                    result.add(candidate(Intent.ATTACK, "solo_rare_hunger", "hyena", 650, cfg.combatHold(), t));
                }
            } else if (!p.baby() && p.species().predator() && p.hunger() > 0.8
                && p.personality().aggression() > 0.65 && t.visible() && distance < 16) {
                result.add(candidate(Intent.ATTACK, "individual_hunger", "species", 620, cfg.combatHold(), t));
            }
            if (p.baby() || p.species() == Species.PREY) {
                result.add(new Proposal(Intent.FLEE, "vulnerable_avoidance", "species", 500,
                    cfg.panicHold(), true, Optional.of(t)));
            } else if (p.species() == Species.FELINE && t.visible() && distance > 5) {
                long elapsed = p.tick() - previous.since();
                if (previous.intent() == Intent.ASSESS && elapsed >= 30) {
                    Intent next = !t.watching() && p.hunger() > 0.6 ? Intent.AMBUSH : Intent.RETREAT;
                    result.add(candidate(next, next == Intent.AMBUSH ? "cat_ambush" : "cat_distance",
                        "feline", 420, cfg.retreatHold(), t));
                } else if (previous.intent() == Intent.AMBUSH && !t.watching()) {
                    result.add(candidate(Intent.AMBUSH, "cat_ambush", "feline", 420, cfg.retreatHold(), t));
                } else {
                    result.add(candidate(Intent.ASSESS, "cat_pause", "feline", 400, 30, t));
                }
            } else if (p.species() == Species.FELINE && previous.intent() == Intent.AMBUSH
                && p.hunger() > 0.6 && !t.watching() && t.visible()) {
                result.add(candidate(Intent.ATTACK, "cat_ambush_strike", "feline", 660, cfg.combatHold(), t));
            } else if (p.species() == Species.RAMMER && p.provoked() && distance < 6) {
                result.add(candidate(Intent.ATTACK, "rammer_defense", "defense", 750, cfg.combatHold(), t));
            }
            if (distance < 3 || (t.approaching() && distance < 12)) {
                result.add(candidate(Intent.RETREAT, distance < 3 ? "human_too_close" : "player_pursuing",
                    "perception", 350, cfg.retreatHold(), t));
            }
            if (distance < 6 && t.visible()) result.add(candidate(Intent.WARN, "personal_space", "perception", 300, 30, t));
            result.add(candidate(Intent.ASSESS, t.visible() ? "human_detected" : "heard_or_remembered",
                "perception", 200, 20, t));
        }
        if (!p.baby() && p.species().predator() && p.hunger() >= 0.48 && p.stamina() > 0.4) {
            p.prey().filter(t -> t.visible() && t.attackable() && p.tick() - t.observedTick() <= cfg.memoryTicks())
                .ifPresent(t -> result.add(candidate(Intent.HUNT, "natural_prey", "hunger", 250, cfg.combatHold(), t)));
        }
        if (p.baby()) p.parent().filter(t -> p.distance(t) > 5 && p.distance(t) < 24)
            .ifPresent(t -> result.add(candidate(Intent.FOLLOW, "follow_adult", "family", 150, 20, t)));
        result.add(new Proposal(p.stamina() < 0.3 ? Intent.REST : Intent.IDLE,
            p.stamina() < 0.3 ? "recover_stamina" : "no_urgent_need", "baseline", 0, 20, false, Optional.empty()));
        return List.copyOf(result);
    }
    private static Proposal candidate(Intent intent, String reason, String source, int priority, int hold,
                                      Perception.Threat target) {
        return new Proposal(intent, reason, source, priority, hold, false, Optional.of(target));
    }
}
