package fr.ypwildlife.jeu;

import static fr.ypwildlife.cerveau.Parametre.*;

import fr.ypwildlife.cerveau.*;
import net.dries007.tfc.common.entities.predator.Predator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public final class Sons {
  private long prochain;
  private int nombre;
  public int appels;

  public void rencontre() {
    nombre = 0;
    prochain = 0;
  }

  public void avancer(
      Predator a, Decision d, ProfilEspece c, Observation cible, String rencontre, long tick) {
    boolean posture =
        d.phase() == Phase.WARN || d.phase() == Phase.HOLD_GROUND || d.phase() == Phase.DRIVE_OFF;
    if (!posture
        || cible == null
        || !cible.visible()
        || cible.rapprochement() < -.15
        || tick < prochain
        || nombre >= c.i(warningMaxGrowlsPerEncounter)) return;
    var id = ResourceLocation.tryParse(c.son());
    boolean inscrit = id != null && BuiltInRegistries.SOUND_EVENT.containsKey(id);
    if (inscrit) {
      a.playSound(BuiltInRegistries.SOUND_EVENT.get(id), 1, 1);
      appels++;
      nombre++;
    }
    prochain =
        tick
            + c.i(
                d.phase() == Phase.DRIVE_OFF
                    ? driveOffGrowlCooldownTicks
                    : warningGrowlCooldownTicks);
    Journal.ecrire(
        (ServerLevel) a.level(),
        a,
        rencontre,
        "SOUND",
        inscrit ? "playSound_called" : "sound_not_registered",
        java.util.Map.of(
            "resource",
            c.son(),
            "registered",
            inscrit,
            "called",
            inscrit,
            "count",
            nombre,
            "phase",
            d.phase()));
  }
}
