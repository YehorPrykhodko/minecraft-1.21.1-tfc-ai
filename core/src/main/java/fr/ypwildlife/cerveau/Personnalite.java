package fr.ypwildlife.cerveau;

import java.util.SplittableRandom;
import java.util.UUID;

public record Personnalite(
    double aggression,
    double courage,
    double prudence,
    double sens,
    double mouvement,
    double defensePosition) {
  public static Personnalite creer(UUID id) {
    var r =
        new SplittableRandom(
            id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 23));
    return new Personnalite(
        r.nextDouble(),
        r.nextDouble(),
        r.nextDouble(),
        0.85 + r.nextDouble() * 0.3,
        0.95 + r.nextDouble() * 0.1,
        r.nextDouble());
  }
}
