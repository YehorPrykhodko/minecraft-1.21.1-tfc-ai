package fr.ypwildlife.cerveau;

import static fr.ypwildlife.cerveau.Motif.*;
import static fr.ypwildlife.cerveau.Parametre.*;
import static fr.ypwildlife.cerveau.Phase.*;

import java.util.*;

public final class MotifsPredateur implements MotifsEspece {
  public List<Proposition> proposer(
      Perception p,
      ProfilEspece c,
      Decision avant,
      Evaluation e,
      long avertiAu,
      boolean departFait,
      long dernierPas) {
    var r = new ArrayList<Proposition>();
    if (p.tentateur() != null) {
      r.add(
          new Proposition(
              FOLLOW_PARENT,
              FAMILY,
              "native_temptation",
              "interaction",
              p.tentateur(),
              950,
              false));
    }
    Observation t =
        p.trouver(avant.cible())
            .orElseGet(
                () ->
                    p.trouver(p.agresseur())
                        .orElseGet(
                            () ->
                                p.trouver(p.groupe().cible())
                                    .orElseGet(
                                        () ->
                                            p.observations().stream()
                                                .filter(o -> !o.id().equals(p.parent()))
                                                .filter(
                                                    o ->
                                                        o.joueur()
                                                            || o.proie()
                                                                && p.faim()
                                                                    >= c.n(hungerHuntThreshold)
                                                            || e.critique()
                                                            || e.panique())
                                                .min(
                                                    Comparator.comparingDouble(
                                                        o ->
                                                            o.position().distance(p.position())
                                                                - (o.proie()
                                                                        && p.faim()
                                                                            >= c.n(
                                                                                hungerHuntThreshold)
                                                                    ? c.n(visionDetectionRadius)
                                                                    : 0)))
                                                .orElse(null))));
    UUID cible = t == null ? null : t.id();
    if (e.critique() || e.panique())
      r.add(
          new Proposition(
              FLEE,
              SURVIVAL,
              e.critique() ? "critical_survival" : "confirmed_panic",
              "risk",
              cible,
              1000,
              true));
    if (p.enfant()) {
      if (t != null && (t.joueur() || p.agresseur() != null))
        r.add(new Proposition(FLEE, SURVIVAL, "cub_threat", "family", cible, 900, true));
      else if (p.parent() != null)
        r.add(
            new Proposition(
                FOLLOW_PARENT, FAMILY, "known_parent", "family", p.parent(), 300, false));
      r.add(
          new Proposition(p.dort() ? SLEEP : IDLE, NONE, "cub_no_hunt", "family", null, 1, false));
      return List.copyOf(r);
    }
    if (p.agresseur() != null)
      r.add(
          new Proposition(
              ATTACK, RETALIATION, "direct_damage", "damage", p.agresseur(), 850, false));
    if (p.groupe().defense() != null)
      r.add(
          new Proposition(
              ATTACK, DEFENSIVE, "pack_assistance", "pack", p.groupe().defense(), 800, false));
    if (t == null) {
      r.add(
          new Proposition(
              p.dort() || p.reposNatif()
                  ? SLEEP
                  : p.endurance() < .5 ? REST : p.balade() != null ? WANDER : IDLE,
              NONE,
              "no_confirmed_target",
              "lifecycle",
              null,
              1,
              false));
      return List.copyOf(r);
    }
    double d = t.position().distance(p.position());
    boolean confirme = t.visible() || t.entendu();
    if (t.joueur()
        && !p.enfants().isEmpty()
        && (d <= c.n(offspringDefenseRadius) || p.enfantBlesse())
        && confirme)
      r.add(
          new Proposition(
              ATTACK,
              OFFSPRING_DEFENSE,
              p.enfantBlesse() ? "offspring_damaged" : "offspring_radius",
              "family",
              cible,
              880,
              false));
    boolean interdit =
        c.famille() == ProfilEspece.Famille.HYENE && t.joueur() && p.groupe().joueurs() >= 2;
    if (interdit)
      r.add(
          new Proposition(
              HOLD_GROUND,
              HOLD_POSITION,
              "hyena_multiple_players",
              "pack_policy",
              cible,
              700,
              true));
    boolean chasse = false;
    String cause = e.faimExtreme() ? "hunger_over_ordinary_fear" : "hunger_hunt";
    if (c.famille() == ProfilEspece.Famille.HYENE && t.joueur()) {
      if (p.groupe().adultes() >= 2) {
        chasse = p.groupe().chasse();
        cause = "pack_player_hp_and_ratio";
      } else chasse = p.faim() >= c.n(hungerHuntThreshold) && p.tirage() < c.n(soloHuntChance);
    } else
      chasse =
          p.faim() >= c.n(hungerHuntThreshold)
              && p.tirage() < c.n(huntChance) * Math.min(1, e.desirChasse());
    if (chasse && !interdit && confirme && p.tick() - avant.depuis() >= c.i(assessmentTicks)) {
      Phase phase =
          c.famille() == ProfilEspece.Famille.FELIN && d > 6 && !t.regarde() ? TRACK : HUNT;
      r.add(
          new Proposition(
              phase,
              p.groupe().chasse() ? PACK_HUNT : HUNGER_HUNT,
              cause,
              "hunger",
              cible,
              600 + (t.proie() ? 30 : 0),
              false));
    }
    if (!t.joueur()) {
      r.add(
          new Proposition(
              t.visible() ? ASSESS : OBSERVE,
              NONE,
              "evaluate_nonplayer_target",
              "perception",
              cible,
              50,
              false));
      return List.copyOf(r);
    }
    boolean approche = t.rapprochement() > .15 || t.sprint();
    boolean averti = avertiAu >= 0;
    double assurance =
        e.confiance() + (c.n(positionDefensiveness) + p.personnalite().defensePosition()) * .2;
    if (averti
        && confirme
        && d <= c.n(aggressionRadius)
        && p.tick() - avertiAu >= c.i(assessmentTicks)
        && assurance > .45)
      r.add(new Proposition(ATTACK, DEFENSIVE, "warning_ignored", "warning", cible, 750, false));
    if (averti
        && d <= c.n(driveOffStartRadius)
        && d > c.n(driveOffStopDistance)
        && confirme
        && p.tick() - avertiAu >= c.i(minimumPhaseTicks)
        && p.tick() - dernierPas >= c.i(driveOffCooldownTicks)
        && c.i(driveOffMaxSteps) > 0
        && assurance > .65
        && t.rapprochement() >= -.15)
      r.add(
          new Proposition(
              DRIVE_OFF, HOLD_POSITION, "limited_intimidation", "warning", cible, 450, false));
    if (d <= c.n(warningRadius) + (averti ? c.n(zoneHysteresis) : 0)
        && (!c.avertissementAvecLos() || t.visible())
        && t.rapprochement() >= -.15)
      r.add(
          new Proposition(
              averti ? HOLD_GROUND : WARN,
              HOLD_POSITION,
              p.sortieSure() ? "position_warning" : "escape_blocked_hold",
              "warning",
              cible,
              400,
              false));
    if (!departFait
        && p.sortieSure()
        && d <= c.n(earlyAvoidanceRadius)
        && !averti
        && e.confiance() < (c.n(caution) + p.personnalite().prudence()) / 2 + .25)
      r.add(
          new Proposition(
              RETREAT, SURVIVAL, "early_safe_avoidance", "distance", cible, 420, false));
    if (avant.phase() == RETREAT && p.sortieSure() && d < c.n(retreatTargetSeparation) && !approche)
      r.add(
          new Proposition(
              RETREAT, SURVIVAL, "separation_in_progress", "distance", cible, 430, false));
    r.add(
        new Proposition(
            t.visible() ? ASSESS : OBSERVE,
            NONE,
            t.visible() ? "evaluate_confirmed_target" : "last_known_position",
            "perception",
            cible,
            50,
            false));
    return List.copyOf(r);
  }
}
