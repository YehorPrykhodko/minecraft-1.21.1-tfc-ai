package fr.ypwildlife.jeu;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class Journal {
  private static final org.slf4j.Logger LOG = LogUtils.getLogger();
  private static final Gson JSON = new GsonBuilder().serializeNulls().create();
  private static final Map<String, String> ANONYMES = new HashMap<>();
  private static long sequence, dernierTick = -1, perdus;
  private static int comptes;

  private Journal() {}

  public static void joueur(UUID id) {
    ANONYMES.computeIfAbsent(id.toString(), k -> "player-" + (ANONYMES.size() + 1));
  }

  public static void ecrire(
      ServerLevel niveau,
      Entity animal,
      String rencontre,
      String stage,
      String raison,
      Object contenu) {
    if (!Configuration.SPEC.isLoaded() || !Configuration.TRACE.get()) return;
    long tick = niveau.getServer().getTickCount();
    if (tick != dernierTick) {
      if (perdus > 0)
        brut(tick, animal, rencontre, "ERROR", "DROPPED_EVENTS", Map.of("count", perdus));
      perdus = 0;
      comptes = 0;
      dernierTick = tick;
    }
    if (comptes++ >= Configuration.LIMITE_TRACE.get()) {
      perdus++;
      return;
    }
    brut(tick, animal, rencontre, stage, raison, contenu);
  }

  private static void brut(
      long tick, Entity animal, String rencontre, String stage, String raison, Object contenu) {
    var ligne = new LinkedHashMap<String, Object>();
    ligne.put("serverTick", tick);
    ligne.put("eventSequence", ++sequence);
    ligne.put("animalUUID", animal.getUUID().toString());
    ligne.put(
        "species",
        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
            .getKey(animal.getType())
            .toString());
    ligne.put("encounterId", rencontre);
    ligne.put("stage", stage);
    ligne.put("reason", raison);
    ligne.put("source", "v4");
    ligne.put("data", contenu);
    JsonElement arbre = JSON.toJsonTree(ligne);
    anonymiser(arbre);
    LOG.info("[TFC-WILDLIFE-TRACE] {}", JSON.toJson(arbre));
  }

  private static void anonymiser(JsonElement e) {
    if (e.isJsonObject())
      for (var entree : e.getAsJsonObject().entrySet()) {
        var v = entree.getValue();
        if (v.isJsonPrimitive()
            && v.getAsJsonPrimitive().isString()
            && ANONYMES.containsKey(v.getAsString()))
          entree.setValue(new JsonPrimitive(ANONYMES.get(v.getAsString())));
        else anonymiser(v);
      }
    else if (e.isJsonArray())
      for (int i = 0; i < e.getAsJsonArray().size(); i++) {
        var v = e.getAsJsonArray().get(i);
        if (v.isJsonPrimitive()
            && v.getAsJsonPrimitive().isString()
            && ANONYMES.containsKey(v.getAsString()))
          e.getAsJsonArray().set(i, new JsonPrimitive(ANONYMES.get(v.getAsString())));
        else anonymiser(v);
      }
  }

  public static void terminer() {
    ANONYMES.clear();
    sequence = 0;
    dernierTick = -1;
    perdus = 0;
  }
}
