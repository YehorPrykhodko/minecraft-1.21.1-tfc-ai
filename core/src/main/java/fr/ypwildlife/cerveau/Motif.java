package fr.ypwildlife.cerveau;

public enum Motif {
  NONE,
  HUNGER_HUNT,
  DEFENSIVE,
  OFFSPRING_DEFENSE,
  HOLD_POSITION,
  PACK_HUNT,
  RETALIATION,
  SURVIVAL,
  FAMILY;

  public boolean chasse() {
    return this == HUNGER_HUNT || this == PACK_HUNT;
  }
}
