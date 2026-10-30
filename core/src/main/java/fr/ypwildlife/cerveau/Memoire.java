package fr.ypwildlife.cerveau;

import java.util.*;

public final class Memoire {
  private final Map<UUID, Observation> cibles = new HashMap<>();

  public void noter(Observation o) {
    cibles.put(o.id(), o);
  }

  public void retirer(UUID id) {
    cibles.remove(id);
  }

  public List<Observation> lire(long tick, int ttl) {
    cibles.values().removeIf(o -> !o.recente(tick, ttl));
    return cibles.values().stream()
        .map(o -> o.confirmeAu() == tick ? o : o.souvenir())
        .sorted(Comparator.comparing(Observation::id))
        .toList();
  }

  public Observation precedente(UUID id) {
    return cibles.get(id);
  }

  public static boolean visible(
      Position origine,
      Position cible,
      double regardX,
      double regardZ,
      boolean los,
      double fov,
      double rayon) {
    if (!los || origine.distance(cible) > rayon) return false;
    double x = cible.x() - origine.x(),
        z = cible.z() - origine.z(),
        l = Math.hypot(x, z) * Math.hypot(regardX, regardZ);
    return l < .01 || (x * regardX + z * regardZ) / l >= Math.cos(Math.toRadians(fov / 2));
  }

  public static double audition(double distance, double rayon, boolean obstacle, double sens) {
    return Math.max(0, 1 - distance / rayon) * (obstacle ? .25 : 1) * sens;
  }
}
