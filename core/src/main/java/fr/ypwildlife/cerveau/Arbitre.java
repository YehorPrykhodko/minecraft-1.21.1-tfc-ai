package fr.ypwildlife.cerveau;

import static fr.ypwildlife.cerveau.Parametre.*;

import java.util.*;

/** Seul propriétaire de la décision et de ses horloges. Les propositions restent immuables. */
public final class Arbitre {
  public record Refus(Proposition proposition, String veto) {}

  public record Resultat(
      Decision avant,
      Decision decision,
      Evaluation evaluation,
      List<Proposition> propositions,
      List<Refus> refus) {}

  private Decision decision = Decision.initiale();
  private long dernierTick = -1,
      avertiAu = -1,
      dernierPas = -100000,
      calmeApres = 0,
      bloqueJusqua = 0;
  private boolean departFait;
  private long rencontreAu;
  private Resultat dernier;
  private final MotifsEspece regles;

  public Arbitre() {
    this(new MotifsPredateur());
  }

  public Arbitre(MotifsEspece regles) {
    this.regles = regles;
  }

  public Decision decision() {
    return decision;
  }

  public void nouvelleRencontre() {
    nouvelleRencontre(0);
  }

  public void nouvelleRencontre(long tick) {
    avertiAu = -1;
    departFait = false;
    rencontreAu = tick;
  }

  public Resultat avancer(Perception p, ProfilEspece c) {
    if (p.tick() == dernierTick) return dernier;
    if (p.tick() < dernierTick) throw new IllegalArgumentException("Tick décroissant");
    Decision avant = decision;
    var e = Evaluation.calculer(p, c);
    var candidats =
        new ArrayList<>(regles.proposer(p, c, avant, e, avertiAu, departFait, dernierPas));
    if (avant.phase().combat() && p.echecsRoute() >= c.i(retreatMaxAttempts))
      candidats.add(
          new Proposition(
              Phase.HOLD_GROUND,
              Motif.HOLD_POSITION,
              "combat_path_blocked",
              "navigation_feedback",
              avant.cible(),
              960,
              true));
    candidats.sort(Comparator.comparingDouble(Proposition::poids).reversed());
    var refus = new ArrayList<Refus>();
    Proposition choisi = null;
    boolean cibleValide =
        p.trouver(avant.cible())
            .filter(o -> o.recente(p.tick(), c.i(visionMemoryTicks)))
            .isPresent();
    boolean annulerChasse =
        c.famille() == ProfilEspece.Famille.HYENE
            && p.groupe().joueurs() >= 2
            && avant.motif().chasse()
            && p.trouver(avant.cible()).map(Observation::joueur).orElse(false);
    for (var q : candidats) {
      String veto = null;
      if (q.motif().chasse() && p.tick() - rencontreAu < c.i(assessmentTicks))
        veto = "initial_assessment";
      else if (q.phase().combat() && p.tick() < bloqueJusqua) veto = "path_retry_cooldown";
      else if (p.enfant() && q.phase().combat()) veto = "cub_no_combat";
      else if (q.phase().combat()
          && (!p.trouver(q.cible())
              .map(o -> o.recente(p.tick(), c.i(visionMemoryTicks)))
              .orElse(false))) veto = "target_unconfirmed";
      else if (q.phase().combat() && p.tick() < calmeApres && !q.urgence()) veto = "panic_recovery";
      else if (q.raison().equals("hyena_multiple_players")
          && !avant.motif().chasse()
          && avant.phase().combat()) veto = "defense_not_hunt";
      else if (avant.phase() == Phase.FLEE && p.tick() < avant.verrou() && q.phase() != Phase.FLEE)
        veto = "panic_hold";
      else if (avant.phase().combat()
          && cibleValide
          && !annulerChasse
          && !q.urgence()
          && !(q.phase().combat() && q.motif() == Motif.RETALIATION && avant.motif().chasse())
          && (q.phase() != avant.phase()
              || !Objects.equals(q.cible(), avant.cible())
              || q.motif() != avant.motif())) veto = "combat_commitment";
      else if (!q.urgence()
          && !q.phase().combat()
          && cibleValide
          && p.tick() < avant.verrou()
          && q.phase() != avant.phase()) veto = "phase_hold";
      if (veto != null) refus.add(new Refus(q, veto));
      else if (choisi == null) choisi = q;
      else refus.add(new Refus(q, "lower_priority"));
    }
    if (choisi != null) {
      if (choisi.phase() == Phase.FLEE && (e.panique() || e.critique()))
        calmeApres = Math.max(calmeApres, p.tick() + c.i(panicRecoveryTicks));
      if (choisi.raison().equals("combat_path_blocked"))
        bloqueJusqua = p.tick() + c.i(retreatCooldownTicks);
      boolean conserver =
          avant.phase().combat()
              && cibleValide
              && !annulerChasse
              && !choisi.urgence()
              && !(choisi.motif() == Motif.RETALIATION && avant.motif().chasse());
      if (!conserver
          && (choisi.phase() != avant.phase()
              || choisi.motif() != avant.motif()
              || !Objects.equals(choisi.cible(), avant.cible()))) {
        long duree =
            choisi.phase() == Phase.FLEE
                ? c.i(panicHoldTicks)
                : choisi.phase().combat()
                    ? c.i(combatHoldTicks)
                    : choisi.phase() == Phase.DRIVE_OFF
                        ? c.i(driveOffDurationTicks)
                        : c.i(minimumPhaseTicks);
        decision =
            new Decision(
                choisi.phase(),
                choisi.motif(),
                choisi.raison(),
                choisi.cible(),
                p.tick(),
                p.tick() + duree);
        if (choisi.phase() == Phase.WARN && avertiAu < 0) avertiAu = p.tick();
        if (choisi.phase() == Phase.RETREAT) departFait = true;
        if (choisi.phase() == Phase.DRIVE_OFF) dernierPas = p.tick();
        if (choisi.phase() == Phase.FLEE)
          calmeApres = p.tick() + c.i(panicHoldTicks) + c.i(panicRecoveryTicks);
      }
    } else if (!cibleValide && avant.phase() != Phase.FLEE)
      decision =
          new Decision(
              Phase.IDLE,
              Motif.NONE,
              "target_expired",
              null,
              p.tick(),
              p.tick() + c.i(minimumPhaseTicks));
    // Une source de panique persistante ne remet pas le délai à zéro à chaque tick.
    dernierTick = p.tick();
    dernier = new Resultat(avant, decision, e, List.copyOf(candidats), List.copyOf(refus));
    return dernier;
  }
}
