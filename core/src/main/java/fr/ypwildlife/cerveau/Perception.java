package fr.ypwildlife.cerveau;

import java.util.*;

public record Perception(
    long tick,
    UUID animal,
    Position position,
    boolean enfant,
    double sante,
    double faim,
    double endurance,
    double peur,
    boolean mortel,
    boolean dort,
    boolean reposNatif,
    boolean sortieSure,
    List<Observation> observations,
    UUID agresseur,
    UUID parent,
    UUID tentateur,
    Set<UUID> enfants,
    boolean enfantBlesse,
    Groupe groupe,
    Personnalite personnalite,
    double tirage,
    int echecsRoute,
    Position balade) {
  public Perception {
    observations = List.copyOf(observations);
    enfants = Set.copyOf(enfants);
  }

  public record Groupe(
      UUID id,
      int adultes,
      int joueurs,
      UUID cible,
      boolean chasse,
      UUID defense,
      String veto,
      double chance) {
    public static Groupe seul(int joueurs) {
      return new Groupe(null, 1, joueurs, null, false, null, "solo", 0);
    }
  }

  public Optional<Observation> trouver(UUID id) {
    return observations.stream().filter(o -> o.id().equals(id)).findFirst();
  }
}
