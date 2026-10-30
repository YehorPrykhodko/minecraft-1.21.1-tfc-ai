package fr.ypwildlife.jeu;

import java.util.*;
import net.minecraft.world.entity.*;

/** Une cohorte de naissance explicite, jamais une adoption par seule proximité. */
public final class Familles {
  private static final Map<SpawnGroupData, UUID> COHORTES = new WeakHashMap<>();

  public static void naissance(Mob m, SpawnGroupData donnees) {
    if (donnees != null && Sauvegarde.famille(m) == null)
      Sauvegarde.lier(
          m, COHORTES.computeIfAbsent(donnees, k -> UUID.randomUUID()), null, "spawn_group");
  }

  public static void oeuf(Mob parent, Mob enfant) {
    UUID groupe = Sauvegarde.famille(parent);
    if (groupe == null) groupe = parent.getUUID();
    Sauvegarde.lier(parent, groupe, null, "offspring_egg");
    Sauvegarde.lier(enfant, groupe, parent.getUUID(), "offspring_egg");
  }

  public static void vider() {
    COHORTES.clear();
  }
}
