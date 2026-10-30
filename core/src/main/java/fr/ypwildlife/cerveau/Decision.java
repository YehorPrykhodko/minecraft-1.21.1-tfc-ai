package fr.ypwildlife.cerveau;

import java.util.UUID;

public record Decision(
    Phase phase, Motif motif, String raison, UUID cible, long depuis, long verrou) {
  public static Decision initiale() {
    return new Decision(Phase.IDLE, Motif.NONE, "no_encounter", null, 0, 0);
  }
}
