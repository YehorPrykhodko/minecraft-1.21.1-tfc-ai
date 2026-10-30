package fr.ypwildlife.jeu;

import fr.ypwildlife.cerveau.*;
import java.util.*;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Configuration {
  public static final ModConfigSpec SPEC;
  public static final ModConfigSpec.BooleanValue TRACE;
  public static final ModConfigSpec.DoubleValue RAYON_TRACE;
  public static final ModConfigSpec.IntValue LIMITE_TRACE;

  private record Entree(
      ProfilEspece defaut,
      Map<Parametre, ModConfigSpec.DoubleValue> nombres,
      ModConfigSpec.ConfigValue<String> son,
      ModConfigSpec.BooleanValue los,
      ModConfigSpec.BooleanValue confirme) {}

  private static final Map<String, Entree> ENTREES = new LinkedHashMap<>();
  private static Map<String, ProfilEspece> actifs = ProfilEspece.defauts();
  public static int revision;

  static {
    var b = new ModConfigSpec.Builder();
    b.comment("Diagnostic alpha automatique dans latest.log").push("diagnostics");
    TRACE = b.define("enabled", true);
    RAYON_TRACE = b.defineInRange("activeRadius", 96.0, 16, 256);
    LIMITE_TRACE = b.defineInRange("maxEventsPerTick", 2000, 50, 20000);
    b.pop();
    b.push("species");
    for (var c : ProfilEspece.defauts().values()) {
      b.push(c.id());
      var valeurs = new EnumMap<Parametre, ModConfigSpec.DoubleValue>(Parametre.class);
      for (var n : Parametre.values())
        valeurs.put(
            n,
            b.comment("Valeur proposée; calibrer en jeu. Limites: " + n.min + ".." + n.max)
                .defineInRange(n.name(), c.n(n), n.min, n.max));
      var son = b.define("warningSound", c.son());
      var los = b.define("visualWarnRequiresLos", true);
      var confirme = b.define("physicalAggressionRequiresConfirmation", true);
      ENTREES.put(c.id(), new Entree(c, valeurs, son, los, confirme));
      b.pop();
    }
    b.pop();
    SPEC = b.build();
  }

  public static void charger() {
    var prochains = new LinkedHashMap<String, ProfilEspece>();
    for (var e : ENTREES.entrySet()) {
      var c = e.getValue();
      var n = new EnumMap<Parametre, Double>(Parametre.class);
      c.nombres().forEach((k, v) -> n.put(k, v.get()));
      prochains.put(
          e.getKey(),
          new ProfilEspece(
              e.getKey(),
              c.defaut().famille(),
              n,
              c.son().get(),
              c.los().get(),
              c.confirme().get()));
    }
    actifs = Map.copyOf(prochains);
    revision++;
  }

  public static ProfilEspece profil(String id) {
    return actifs.get(id);
  }

  public static Map<String, ProfilEspece> profils() {
    return actifs;
  }
}
