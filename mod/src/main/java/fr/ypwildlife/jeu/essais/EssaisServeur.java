package fr.ypwildlife.jeu.essais;

import fr.ypwildlife.cerveau.*;
import fr.ypwildlife.jeu.*;
import java.util.*;
import net.dries007.tfc.common.entities.TFCEntities;
import net.dries007.tfc.common.entities.predator.Predator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Faune.ID)
@PrefixGameTestTemplate(false)
public final class EssaisServeur {
  private static Player joueur(GameTestHelper h, int x, int z) {
    var p =
        new net.neoforged.neoforge.common.util.FakePlayer(
            h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "wildlife-test"));
    p.setGameMode(GameType.SURVIVAL);
    var pos = h.absolutePos(new BlockPos(x, 2, z));
    p.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 90, 0);
    h.getLevel().addFreshEntity(p);
    return p;
  }

  private static void regarder(Predator m, Entity cible) {
    m.getLookControl().setLookAt(cible, 180, 180);
    m.setYRot(-90);
    m.setYHeadRot(-90);
  }

  private static void finir(GameTestHelper h, Player... joueurs) {
    for (var p : joueurs) p.discard();
    h.killAllEntities();
    h.succeed();
  }

  private static void config(String id, Parametre p, double valeur) {
    ModConfigSpec.DoubleValue v =
        Configuration.SPEC.getValues().get(List.of("species", id, p.name()));
    v.set(valeur);
    Configuration.charger();
  }

  @GameTest(template = "smoke", batch = "v4_01_registry", timeoutTicks = 30)
  public static void registresEtMigration(GameTestHelper h) {
    int i = 0;
    for (var c : Configuration.profils().values()) {
      var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(c.id()));
      var e = h.spawn(type, 3 + (i % 4) * 8, 2, 3 + (i / 4) * 8);
      i++;
      h.assertTrue(e instanceof Predator && MondeFaune.gere(e), "Prédateur non géré: " + c.id());
      h.assertTrue(
          BuiltInRegistries.SOUND_EVENT.containsKey(ResourceLocation.parse(c.son())),
          "Son manquant: " + c.son());
    }
    var ours = TFCEntities.GRIZZLY_BEAR.get().create(h.getLevel());
    ours.getPersistentData().putDouble("tfc_wildlife_ai.satiety", 60.3);
    ours.getPersistentData().putDouble("tfc_wildlife_ai.stamina", 80);
    var v = Sauvegarde.lire(ours);
    h.assertTrue(Math.abs(v.satiete() - .603) < 1e-9, "Échelle NBT ancienne incorrecte");
    Sauvegarde.ecrire(ours, v);
    h.assertTrue(
        ours.getPersistentData().getDouble("tfc_wildlife_ai.satiety") == 60.3,
        "Ancien NBT détruit");
    var cerf = h.spawn(TFCEntities.DEER.get(), 40, 2, 40);
    h.assertFalse(MondeFaune.gere(cerf), "La proie doit conserver son IA TFC");
    h.runAtTickTime(10, () -> finir(h));
  }

  @GameTest(template = "smoke", batch = "v4_02_melee", timeoutTicks = 180)
  public static void combatReelEtContinu(GameTestHelper h) {
    var ours = h.spawn(TFCEntities.GRIZZLY_BEAR.get(), 25, 2, 25);
    var cible = h.spawn(EntityType.ZOMBIE, 27, 2, 25);
    cible.setNoAi(true);
    cible
        .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
        .setBaseValue(1000);
    cible.setHealth(1000);
    cible.setItemSlot(
        EquipmentSlot.HEAD,
        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
    h.assertTrue(h.getLevel().getEntity(cible.getUUID()) == cible, "Cible refusée par TFC");
    regarder(ours, cible);
    ours.hurt(h.getLevel().damageSources().mobAttack(cible), 1);
    h.runAtTickTime(
        20,
        () ->
            h.assertTrue(
                Faune.MONDE.etat(ours).arbitre.decision().phase() == Phase.ATTACK,
                "Défense absente"));
    h.runAtTickTime(
        125,
        () -> {
          h.assertTrue(cible.getHealth() < 1000, "Pas de dégât réel");
          h.assertTrue(
              Faune.MONDE.etat(ours).arbitre.decision().phase() == Phase.ATTACK,
              "Combat interrompu avant 100 ticks");
          finir(h);
        });
  }

  @GameTest(template = "smoke", batch = "v4_03_drive", timeoutTicks = 140)
  public static void avertirRugirEtAvancerSansFrapper(GameTestHelper h) {
    config("tfc:grizzly_bear", Parametre.courage, 1);
    config("tfc:grizzly_bear", Parametre.caution, 0);
    var ours = h.spawn(TFCEntities.GRIZZLY_BEAR.get(), 20, 2, 20);
    var p = joueur(h, 29, 20);
    regarder(ours, p);
    double depart = ours.distanceToSqr(p);
    float hp = p.getHealth();
    h.runAtTickTime(
        15,
        () -> {
          h.assertTrue(Faune.MONDE.etat(ours).sons.appels > 0, "playSound non appelé");
          h.assertTrue(
              Faune.MONDE.etat(ours).arbitre.decision().phase() == Phase.WARN,
              "Avertissement absent");
        });
    h.runAtTickTime(
        70,
        () -> {
          h.assertTrue(ours.distanceToSqr(p) < depart - 1, "DRIVE_OFF sans déplacement physique");
          h.assertTrue(p.getHealth() == hp, "DRIVE_OFF a infligé un dégât");
          h.assertTrue(ours.distanceTo(p) >= 3.5, "Distance de sécurité violée");
          config("tfc:grizzly_bear", Parametre.courage, .7);
          config("tfc:grizzly_bear", Parametre.caution, .35);
          finir(h, p);
        });
  }

  @GameTest(template = "smoke", batch = "v4_04_families", timeoutTicks = 100)
  public static void rayonsFamiliauxIndependantsEtToml(GameTestHelper h) {
    config("tfc:grizzly_bear", Parametre.offspringDefenseRadius, 8);
    config("tfc:wolf", Parametre.offspringDefenseRadius, 15);
    config("tfc:hyena", Parametre.offspringDefenseRadius, 22);
    Configuration.SPEC.save();
    try {
      String toml =
          java.nio.file.Files.readString(
              net.neoforged.fml.loading.FMLPaths.CONFIGDIR
                  .get()
                  .resolve("tfc_wildlife_ai-v4-server.toml"));
      var lu = new com.electronwill.nightconfig.toml.TomlParser().parse(toml);
      h.assertTrue(
          ((Number) lu.get(List.of("species", "tfc:grizzly_bear", "offspringDefenseRadius")))
                  .doubleValue()
              == 8,
          "TOML grizzly incorrect");
      h.assertTrue(
          ((Number) lu.get(List.of("species", "tfc:wolf", "offspringDefenseRadius"))).doubleValue()
              == 15,
          "TOML loup incorrect");
      h.assertTrue(
          ((Number) lu.get(List.of("species", "tfc:hyena", "offspringDefenseRadius"))).doubleValue()
              == 22,
          "TOML hyène incorrect");
    } catch (java.io.IOException ex) {
      throw new IllegalStateException(ex);
    }
    var ours = h.spawn(TFCEntities.GRIZZLY_BEAR.get(), 15, 2, 20);
    var enfant = h.spawn(TFCEntities.GRIZZLY_BEAR.get(), 14, 2, 20);
    enfant.setBaby(true);
    Familles.oeuf(ours, enfant);
    var p = joueur(h, 27, 20);
    regarder(ours, p);
    regarder(enfant, p);
    h.runAtTickTime(
        5,
        () -> {
          h.assertFalse(
              Faune.MONDE.etat(ours).arbitre.decision().motif() == Motif.OFFSPRING_DEFENSE,
              "Rayon 8 ignoré à distance 12");
          config("tfc:grizzly_bear", Parametre.offspringDefenseRadius, 22);
          Configuration.SPEC.save();
        });
    h.runAtTickTime(
        8,
        () -> {
          h.assertTrue(
              Faune.MONDE.etat(ours).arbitre.decision().motif() == Motif.OFFSPRING_DEFENSE,
              "Rayon 22 sans réaction réelle");
          h.assertTrue(
              Configuration.profil("tfc:wolf").n(Parametre.offspringDefenseRadius) == 15,
              "Profil loup modifié indirectement");
          h.assertTrue(
              Faune.MONDE.etat(enfant).arbitre.decision().phase() == Phase.FLEE,
              "L'enfant doit fuir");
          for (String id : List.of("tfc:grizzly_bear", "tfc:wolf", "tfc:hyena"))
            config(id, Parametre.offspringDefenseRadius, 15);
          Configuration.SPEC.save();
          finir(h, p);
        });
  }

  @GameTest(template = "smoke", batch = "v4_05_sleep", timeoutTicks = 160)
  public static void sommeilNatifEtReveil(GameTestHelper h) {
    long heure = net.dries007.tfc.util.calendar.Calendars.SERVER.getCalendarTicks();
    net.dries007.tfc.util.calendar.Calendars.SERVER.skipForwardBy(
        24000 - Math.floorMod(heure, 24000));
    var ours = h.spawn(TFCEntities.GRIZZLY_BEAR.get(), 20, 2, 20);
    h.runAtTickTime(
        40,
        () -> h.assertTrue(ours.isSleeping(), "Le comportement de sommeil TFC ne s'exécute pas"));
    h.runAtTickTime(
        45,
        () -> {
          var cible = h.spawn(EntityType.ZOMBIE, 23, 2, 20);
          cible.setNoAi(true);
          ours.hurt(h.getLevel().damageSources().mobAttack(cible), 1);
        });
    h.runAtTickTime(
        50,
        () -> {
          h.assertFalse(ours.isSleeping(), "Le dégât ne réveille pas le prédateur");
          net.dries007.tfc.util.calendar.Calendars.SERVER.skipForwardBy(
              heure - net.dries007.tfc.util.calendar.Calendars.SERVER.getCalendarTicks());
          finir(h);
        });
  }

  @GameTest(template = "smoke", batch = "v4_06_shots", timeoutTicks = 180)
  public static void tirTaczFuitePhysiqueEtRecuperation(GameTestHelper h) {
    var ours = h.spawn(TFCEntities.GRIZZLY_BEAR.get(), 25, 2, 25);
    var cible = h.spawn(EntityType.ZOMBIE, 27, 2, 25);
    cible.setNoAi(true);
    cible
        .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
        .setBaseValue(1000);
    cible.setHealth(1000);
    ours.hurt(h.getLevel().damageSources().mobAttack(cible), 1);
    double[] distance = {0};
    h.runAtTickTime(
        10,
        () -> {
          h.assertTrue(
              Faune.MONDE.etat(ours).arbitre.decision().phase() == Phase.ATTACK,
              "Combat préalable absent");
          cible.moveTo(ours.getX() + 1, ours.getY(), ours.getZ());
          distance[0] = ours.distanceToSqr(cible);
          net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
              new com.tacz.guns.api.event.common.GunFireEvent(
                  cible,
                  net.minecraft.world.item.ItemStack.EMPTY,
                  net.neoforged.fml.LogicalSide.SERVER));
        });
    h.runAtTickTime(
        35,
        () -> {
          h.assertTrue(
              Faune.MONDE.etat(ours).arbitre.decision().phase() == Phase.FLEE, "Panique absente");
          h.assertTrue(ours.distanceToSqr(cible) > distance[0] + 4, "Fuite sans déplacement réel");
        });
    h.runAtTickTime(
        80,
        () -> {
          h.assertFalse(
              Faune.MONDE.etat(ours).arbitre.decision().phase().combat(),
              "Reprise prématurée après le tir");
          finir(h);
        });
  }

  @GameTest(template = "smoke", batch = "v4_07_hyenas", timeoutTicks = 80)
  public static void deuxJoueursInterdisentChasseMaisPasDefense(GameTestHelper h) {
    var hyenes = new ArrayList<Predator>();
    var p = joueur(h, 28, 20);
    var q = joueur(h, 28, 24);
    p.setHealth(2);
    q.setHealth(2);
    for (int i = 0; i < 4; i++) {
      var m = h.spawn(TFCEntities.HYENA.get(), 18, 2, 18 + i * 3);
      regarder(m, p);
      Faune.MONDE.etat(m).ressources = new Ressources(0, 1, 0);
      hyenes.add(m);
    }
    h.runAtTickTime(
        10,
        () -> {
          for (var m : hyenes)
            h.assertFalse(
                Faune.MONDE.etat(m).arbitre.decision().motif().chasse(),
                "Chasse humaine malgré deux joueurs");
          hyenes.getFirst().hurt(h.getLevel().damageSources().playerAttack(p), 1);
        });
    h.runAtTickTime(
        15,
        () -> {
          h.assertTrue(
              Faune.MONDE.etat(hyenes.getFirst()).arbitre.decision().phase() == Phase.ATTACK,
              "La défense doit rester permise");
          finir(h, p, q);
        });
  }

  private static void rayonReel(
      GameTestHelper h, EntityType<? extends Predator> type, String id, double rayon) {
    config(id, Parametre.offspringDefenseRadius, rayon);
    var adulte = h.spawn(type, 12, 2, 20);
    var enfant = h.spawn(type, 11, 2, 20);
    enfant.setBaby(true);
    Familles.oeuf(adulte, enfant);
    var p = joueur(h, 24, 20);
    regarder(adulte, p);
    regarder(enfant, p);
    h.runAtTickTime(
        3,
        () -> {
          h.assertTrue(
              Faune.MONDE.etat(adulte).arbitre.decision().motif() == Motif.OFFSPRING_DEFENSE,
              "Le rayon propre à " + id + " ne commande pas la défense");
          config(id, Parametre.offspringDefenseRadius, 15);
          finir(h, p);
        });
  }

  @GameTest(template = "smoke", batch = "v4_08_wolf_family", timeoutTicks = 40)
  public static void familleLoupRayon15(GameTestHelper h) {
    rayonReel(h, TFCEntities.WOLF.get(), "tfc:wolf", 15);
  }

  @GameTest(template = "smoke", batch = "v4_09_hyena_family", timeoutTicks = 40)
  public static void familleHyeneRayon22(GameTestHelper h) {
    rayonReel(h, TFCEntities.HYENA.get(), "tfc:hyena", 22);
  }

  @GameTest(template = "smoke", batch = "v4_10_unknown_family", timeoutTicks = 40)
  public static void voisinageNeCreePasDeParente(GameTestHelper h) {
    var adulte = h.spawn(TFCEntities.GRIZZLY_BEAR.get(), 15, 2, 20);
    var enfant = h.spawn(TFCEntities.GRIZZLY_BEAR.get(), 14, 2, 20);
    enfant.setBaby(true);
    var p = joueur(h, 27, 20);
    regarder(adulte, p);
    h.runAtTickTime(
        5,
        () -> {
          h.assertTrue(Sauvegarde.parent(enfant) == null, "Adoption implicite interdite");
          h.assertFalse(
              Faune.MONDE.etat(adulte).arbitre.decision().motif() == Motif.OFFSPRING_DEFENSE,
              "Fausse défense d'un enfant inconnu");
          finir(h, p);
        });
  }

  @GameTest(template = "smoke", batch = "v4_11_load", timeoutTicks = 160)
  public static void cinquanteAnimauxAvecTrace(GameTestHelper h) {
    var p = joueur(h, 40, 40);
    var animaux = new ArrayList<Predator>();
    for (int i = 0; i < 50; i++) {
      var m = h.spawn(TFCEntities.GRIZZLY_BEAR.get(), 8 + (i % 7) * 4, 2, 8 + (i / 7) * 4);
      regarder(m, p);
      animaux.add(m);
    }
    long[] mesures = {0, 0, 0};
    for (int t = 10; t < 90; t++)
      h.runAtTickTime(
          t,
          () -> {
            mesures[0] += Faune.MONDE.dernierCoutNanos;
            mesures[1]++;
            mesures[2] = Math.max(mesures[2], Faune.MONDE.dernierCoutNanos);
          });
    h.runAtTickTime(
        95,
        () -> {
          double moyenne = mesures[0] / (double) mesures[1] / 1_000_000;
          Journal.ecrire(
              h.getLevel(),
              animaux.getFirst(),
              "load-test",
              "HEARTBEAT",
              "load_measurement",
              Map.of(
                  "animals",
                  50,
                  "samples",
                  mesures[1],
                  "aiAndTraceMeanMs",
                  moyenne,
                  "aiAndTraceMaxMs",
                  mesures[2] / 1_000_000.0));
          h.assertTrue(Faune.MONDE.dernierNombre >= 50, "Population de test incomplète");
          h.assertTrue(
              moyenne < 50, "IA et trace dépassent ensemble 50 ms/tick sur ce serveur de test");
          finir(h, p);
        });
  }

  @GameTest(template = "smoke", batch = "v4_12_pack_commitment", timeoutTicks = 100)
  public static void chasseCollectivePuisSecondJoueur(GameTestHelper h) {
    config("tfc:hyena", Parametre.packHuntChanceMax, 1);
    config("tfc:hyena", Parametre.packConfidenceWeight, 2);
    var p = joueur(h, 35, 20);
    p.setHealth(3);
    var hyenes = new ArrayList<Predator>();
    Player[] autre = {null};
    for (int i = 0; i < 4; i++) {
      var m = h.spawn(TFCEntities.HYENA.get(), 18, 2, 17 + i * 2);
      regarder(m, p);
      hyenes.add(m);
    }
    h.runAtTickTime(
        30,
        () -> {
          for (var m : hyenes)
            h.assertTrue(
                Faune.MONDE.etat(m).arbitre.decision().motif() == Motif.PACK_HUNT,
                "La meute n'engage pas ensemble sa cible confirmée");
          autre[0] = joueur(h, 35, 23);
        });
    h.runAtTickTime(
        34,
        () -> {
          for (var m : hyenes)
            h.assertFalse(
                Faune.MONDE.etat(m).arbitre.decision().motif().chasse(),
                "La seconde personne n'annule pas la chasse");
        });
    h.runAtTickTime(
        50,
        () -> {
          for (var m : hyenes)
            h.assertFalse(
                Faune.MONDE.etat(m).arbitre.decision().motif().chasse(),
                "Reprise de chasse après un seul tick");
          hyenes.getFirst().hurt(h.getLevel().damageSources().playerAttack(p), 1);
        });
    h.runAtTickTime(
        55,
        () -> {
          for (var m : hyenes)
            h.assertTrue(
                Faune.MONDE.etat(m).arbitre.decision().phase() == Phase.ATTACK,
                "Aide défensive d'un adulte sain absente");
          config("tfc:hyena", Parametre.packHuntChanceMax, .95);
          config("tfc:hyena", Parametre.packConfidenceWeight, .35);
          finir(h, p, autre[0]);
        });
  }

  @GameTest(template = "smoke", batch = "v4_13_hunger", timeoutTicks = 80)
  public static void oursAffameChasseSansProvocation(GameTestHelper h) {
    config("tfc:grizzly_bear", Parametre.huntChance, 1);
    config("tfc:grizzly_bear", Parametre.courage, 1);
    config("tfc:grizzly_bear", Parametre.aggression, 1);
    UUID id = null;
    for (int i = 1; i < 10000; i++) {
      UUID candidat = new UUID(712, i);
      var traits = Personnalite.creer(candidat);
      if (traits.aggression() > .8 && traits.courage() > .8) {
        id = candidat;
        break;
      }
    }
    h.assertTrue(id != null, "Personnalité déterministe introuvable");
    var ours = TFCEntities.GRIZZLY_BEAR.get().create(h.getLevel());
    ours.setUUID(id);
    var pos = h.absolutePos(new BlockPos(15, 2, 20));
    ours.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
    h.getLevel().addFreshEntity(ours);
    var p = joueur(h, 31, 20);
    regarder(ours, p);
    Faune.MONDE.etat(ours).ressources = new Ressources(0, 1, 0);
    h.runAtTickTime(
        10,
        () ->
            h.assertFalse(
                Faune.MONDE.etat(ours).arbitre.decision().motif().chasse(),
                "Évaluation initiale omise"));
    h.runAtTickTime(
        35,
        () -> {
          h.assertTrue(
              Faune.MONDE.etat(ours).arbitre.decision().motif() == Motif.HUNGER_HUNT,
              "Pas de chasse sans dégât préalable");
          config("tfc:grizzly_bear", Parametre.huntChance, .55);
          config("tfc:grizzly_bear", Parametre.courage, .7);
          config("tfc:grizzly_bear", Parametre.aggression, .65);
          finir(h, p);
        });
  }
}
