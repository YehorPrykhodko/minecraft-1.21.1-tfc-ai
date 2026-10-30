package fr.ypwildlife.jeu;

import static fr.ypwildlife.cerveau.Parametre.*;

import fr.ypwildlife.cerveau.*;
import java.util.*;
import net.dries007.tfc.common.entities.ai.TFCBrain;
import net.dries007.tfc.common.entities.predator.Predator;
import net.dries007.tfc.common.entities.prey.Prey;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class MondeFaune {
  private static final UUID DANGER_SONORE = new UUID(0, 1);

  public static final class Animal {
    public final Arbitre arbitre = new Arbitre();
    public final Navigation navigation = new Navigation();
    public final Sons sons = new Sons();
    final Memoire memoire = new Memoire();
    final Personnalite personnalite;
    public Ressources ressources;
    final Map<UUID, Observation> trajectoires = new HashMap<>();
    final Map<String, Long> sonsEntendus = new HashMap<>();
    UUID agresseur;
    long agresseAu = -100000,
        enfantBlesseAu = -100000,
        derniereAttaque = -100000,
        dernierActif = -100000;
    String rencontre = "none";
    double tirage = .99;
    int episode, configRevision = -1;
    boolean actif, limiteAnnoncee;
    Perception vue;
    final List<Map<String, Object>> sondages = new ArrayList<>();
    Perception.Groupe groupePrecedent;
    Set<UUID> enfantsPrecedents = Set.of();
    Navigation.Bilan bilan;
    long avertissement;
    Position positionAvant, balade;
    long baladeAu;
    float angleAvant;

    Animal(Predator m) {
      personnalite = Personnalite.creer(m.getUUID());
      ressources = Sauvegarde.lire(m);
    }
  }

  private static final class Monde {
    final Map<UUID, Predator> animaux = new TreeMap<>();
    final Map<UUID, Animal> etats = new HashMap<>();
    final Meutes meutes = new Meutes();
  }

  public long dernierCoutNanos;
  public int dernierNombre;
  private final Map<ServerLevel, Monde> mondes = new HashMap<>();

  public static String type(Entity e) {
    return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
  }

  public static boolean gere(Entity e) {
    return e instanceof Predator m
        && Configuration.profil(type(m)) != null
        && Sauvegarde.supporte(m)
        && !m.isNoAi()
        && !m.isLeashed()
        && !m.isPassenger();
  }

  public Animal etat(Predator m) {
    return mondes
        .computeIfAbsent((ServerLevel) m.level(), k -> new Monde())
        .etats
        .computeIfAbsent(m.getUUID(), k -> new Animal(m));
  }

  private static double sante(LivingEntity e) {
    return Math.clamp(e.getHealth() / Math.max(1, e.getMaxHealth()), 0, 1);
  }

  private static boolean attaquable(LivingEntity e) {
    return e.isAlive() && (!(e instanceof Player p) || !p.isCreative() && !p.isSpectator());
  }

  public void entree(EntityJoinLevelEvent e) {
    if (e.getEntity() instanceof Player p) Journal.joueur(p.getUUID());
    if (e.getLevel() instanceof ServerLevel l && e.getEntity() instanceof Predator m) {
      if (Configuration.profil(type(m)) == null) {
        Journal.ecrire(l, m, "none", "ERROR", "unsupported_predator_id", Map.of("id", type(m)));
        return;
      }
      mondes.computeIfAbsent(l, k -> new Monde()).animaux.put(m.getUUID(), m);
      CerveauTfc.synchroniser(m, gere(m));
    }
  }

  public void sortie(EntityLeaveLevelEvent e) {
    if (e.getLevel() instanceof ServerLevel l && e.getEntity() instanceof Predator m) {
      var monde = mondes.get(l);
      if (monde != null) {
        var a = monde.etats.remove(m.getUUID());
        monde.animaux.remove(m.getUUID());
        if (a != null) {
          Sauvegarde.ecrire(m, a.ressources);
          Journal.ecrire(
              l,
              m,
              a.rencontre,
              "ENCOUNTER_SUMMARY",
              "entity_unloaded",
              Map.of("decision", a.arbitre.decision()));
        }
      }
    }
  }

  public void decharger(LevelEvent.Unload e) {
    if (e.getLevel() instanceof ServerLevel l) mondes.remove(l);
  }

  public void arreter(ServerStoppedEvent e) {
    mondes.clear();
    Journal.terminer();
    Familles.vider();
  }

  public void demarrer(ServerStartedEvent e) {
    var log = com.mojang.logging.LogUtils.getLogger();
    for (var c : Configuration.profils().values()) {
      if (!BuiltInRegistries.ENTITY_TYPE.containsKey(
          net.minecraft.resources.ResourceLocation.parse(c.id())))
        throw new IllegalStateException("Espèce TFC absente: " + c.id());
      if (!BuiltInRegistries.SOUND_EVENT.containsKey(
          net.minecraft.resources.ResourceLocation.parse(c.son())))
        throw new IllegalStateException("Son TFC absent: " + c.son());
    }
    log.info(
        "Wildlife v4: 12 profils TFC vérifiés. TaCZ GunFireEvent est préalable: une tentative non"
            + " annulée peut échouer ensuite dans le script de l'arme.");
  }

  public void tick(LevelTickEvent.Pre e) {
    if (!(e.getLevel() instanceof ServerLevel l)) return;
    var monde = mondes.get(l);
    if (monde == null) return;
    long debut = System.nanoTime();
    long tick = l.getServer().getTickCount();
    var liste =
        monde.animaux.values().stream()
            .filter(m -> m.isAlive() && l.isPositionEntityTicking(m.blockPosition()))
            .toList();
    for (var m : liste) {
      CerveauTfc.synchroniser(m, gere(m));
      if (!gere(m)) continue;
      observer(l, m, etat(m), tick);
    }
    var membres =
        liste.stream()
            .filter(MondeFaune::gere)
            .map(
                m -> {
                  var a = etat(m);
                  return new Meutes.Membre(
                      m.getUUID(),
                      Configuration.profil(type(m)),
                      PortNavigation.position(m.position()),
                      m.isBaby(),
                      sante(m),
                      a.ressources.peur(),
                      a.navigation.echecs(),
                      a.memoire.lire(tick, Configuration.profil(type(m)).i(visionMemoryTicks)),
                      a.agresseur);
                })
            .toList();
    var groupes = monde.meutes.calculer(tick, membres);
    for (var m : liste)
      if (gere(m))
        agir(
            l,
            m,
            etat(m),
            groupes.getOrDefault(m.getUUID(), Perception.Groupe.seul(0)),
            tick,
            monde);
    dernierCoutNanos = System.nanoTime() - debut;
    dernierNombre = liste.size();
  }

  private void observer(ServerLevel l, Predator m, Animal a, long tick) {
    var c = Configuration.profil(type(m));
    a.ressources = a.ressources.avancer(a.arbitre.decision().phase(), c);
    a.positionAvant = PortNavigation.position(m.position());
    a.angleAvant = m.getYRot();
    for (var joueur : l.players()) Journal.joueur(joueur.getUUID());
    a.sondages.clear();
    double rayon = c.n(visionDetectionRadius);
    var voisins =
        l.getEntitiesOfClass(
            LivingEntity.class,
            m.getBoundingBox().inflate(rayon),
            v ->
                v != m
                    && attaquable(v)
                    && (v instanceof Player
                        || v instanceof Prey
                        || v.getUUID().equals(a.agresseur)
                        || v.getUUID().equals(a.arbitre.decision().cible())));
    for (var cible : voisins) {
      double portee = rayon * (cible.isCrouching() ? .65 : 1);
      boolean los = m.hasLineOfSight(cible);
      boolean visible =
          Memoire.visible(
              PortNavigation.position(m.getEyePosition()),
              PortNavigation.position(cible.getEyePosition()),
              m.getLookAngle().x,
              m.getLookAngle().z,
              los,
              c.n(visionFovDegrees),
              portee);
      a.sondages.add(
          Map.of(
              "target",
              cible.getUUID(),
              "distance",
              m.distanceTo(cible),
              "los",
              los,
              "detected",
              visible,
              "crouching",
              cible.isCrouching(),
              "playerYaw",
              cible.getYRot(),
              "velocity",
              PortNavigation.position(cible.getDeltaMovement()),
              "radius",
              portee));
      if (!visible) continue;
      Observation precedente = a.trajectoires.get(cible.getUUID());
      double approche = 0;
      if (precedente != null && tick - precedente.confirmeAu() >= 5) {
        // Déplacement de la cible seulement: notre propre marche n'est pas une provocation.
        approche =
            precedente.position().distance(PortNavigation.position(m.position()))
                - cible.distanceTo(m);
      }
      var o = observation(m, cible, tick, true, false, approche);
      a.memoire.noter(o);
      if (precedente == null || tick - precedente.confirmeAu() >= 10)
        a.trajectoires.put(cible.getUUID(), o);
    }
    a.trajectoires.values().removeIf(o -> tick - o.confirmeAu() > c.i(visionMemoryTicks));
    for (var o : a.memoire.lire(tick, c.i(visionMemoryTicks))) {
      var cible = l.getEntity(o.id());
      if (!o.id().equals(DANGER_SONORE)
          && (cible == null
              || !cible.isAlive()
              || cible instanceof Player p && (p.isCreative() || p.isSpectator())))
        a.memoire.retirer(o.id());
    }
    if (tick - a.agresseAu > c.i(visionMemoryTicks)
        || a.agresseur != null && a.memoire.precedente(a.agresseur) == null) a.agresseur = null;
    a.sonsEntendus.entrySet().removeIf(v -> tick - v.getValue() > 200);
  }

  private void agir(
      ServerLevel l, Predator m, Animal a, Perception.Groupe groupe, long tick, Monde monde) {
    var c = Configuration.profil(type(m));
    var observations = new ArrayList<>(a.memoire.lire(tick, c.i(visionMemoryTicks)));
    UUID parent = Sauvegarde.parent(m), famille = Sauvegarde.famille(m);
    var enfants = new HashSet<UUID>();
    if (famille != null) {
      var proches =
          monde.animaux.values().stream()
              .filter(
                  v ->
                      v != m
                          && v.isAlive()
                          && v.getType() == m.getType()
                          && famille.equals(Sauvegarde.famille(v)))
              .toList();
      if (m.isBaby() && parent == null) {
        parent =
            proches.stream()
                .filter(v -> !v.isBaby())
                .map(Entity::getUUID)
                .min(UUID::compareTo)
                .orElse(null);
        if (parent != null) Sauvegarde.lier(m, famille, parent, "spawn_group_guardian");
      }
      if (!m.isBaby())
        for (var petit : proches)
          if (petit.isBaby()
              && (m.getUUID().equals(Sauvegarde.parent(petit)) || Sauvegarde.parent(petit) == null)
              && petit.distanceTo(m) <= c.n(offspringDefenseRadius) * 2)
            enfants.add(petit.getUUID());
    }
    if (parent != null
        && l.getEntity(parent) instanceof LivingEntity adulte
        && m.distanceTo(adulte) <= c.n(visionDetectionRadius)
        && m.hasLineOfSight(adulte)) observations.add(observation(m, adulte, tick, true, false, 0));
    // La tentation TFC garde ses interactions; son déplacement passe par le même exécuteur.
    Optional<Player> tentation =
        m instanceof net.dries007.tfc.common.entities.ai.predator.PackPredator
            ? m.getBrain().getMemory(MemoryModuleType.TEMPTING_PLAYER)
            : Optional.empty();
    if (tentation.isPresent()
        && observations.stream().noneMatch(o -> o.id().equals(tentation.get().getUUID())))
      observations.add(observation(m, tentation.get(), tick, true, false, 0));
    boolean rencontre =
        observations.stream().anyMatch(o -> !o.id().equals(Sauvegarde.parent(m)))
            || a.ressources.peur() > .1
            || a.agresseur != null;
    if (rencontre && !a.actif && tick - a.dernierActif > c.i(encounterCooldownTicks)) {
      a.episode++;
      a.rencontre = m.getUUID() + ":" + a.episode;
      a.tirage = new SplittableRandom(m.getUUID().getLeastSignificantBits() ^ tick).nextDouble();
      a.arbitre.nouvelleRencontre(tick);
      a.sons.rencontre();
      Journal.ecrire(
          l,
          m,
          a.rencontre,
          "PERCEPTION",
          "encounter_started",
          Map.of("personality", a.personnalite, "roll", a.tirage));
    }
    if (rencontre) a.dernierActif = tick;
    if (a.actif && !rencontre)
      Journal.ecrire(
          l,
          m,
          a.rencontre,
          "ENCOUNTER_SUMMARY",
          "target_memory_expired",
          Map.of("decision", a.arbitre.decision()));
    a.actif = rencontre;
    if (a.configRevision != Configuration.revision) {
      a.configRevision = Configuration.revision;
      Journal.ecrire(l, m, a.rencontre, "CONFIG", "applied_species_profile", c);
    }
    if (!rencontre && !m.isSleeping() && tick - a.baladeAu > 200) {
      var point = net.minecraft.world.entity.ai.util.LandRandomPos.getPos(m, 8, 4);
      a.balade = point == null ? null : PortNavigation.position(point);
      a.baladeAu = tick;
    }
    if (rencontre
        || tick - a.baladeAu > 100
        || a.balade != null && a.balade.distance(PortNavigation.position(m.position())) < 1)
      a.balade = null;
    boolean sortie = a.navigation.echecs() < c.i(retreatMaxAttempts);
    var vue =
        new Perception(
            tick,
            m.getUUID(),
            PortNavigation.position(m.position()),
            m.isBaby(),
            sante(m),
            1 - a.ressources.satiete(),
            a.ressources.endurance(),
            a.ressources.peur(),
            m.isInLava() || m.isOnFire() || m.getAirSupply() < 0,
            m.isSleeping(),
            m.getBrain().getSchedule().getActivityAt((int) (l.getDayTime() % 24000))
                == Activity.REST,
            sortie,
            observations,
            a.agresseur,
            parent,
            tentation.map(Entity::getUUID).orElse(null),
            enfants,
            tick - a.enfantBlesseAu < 100,
            groupe,
            a.personnalite,
            a.tirage,
            a.navigation.echecs(),
            a.balade);
    a.vue = vue;
    var resultat = a.arbitre.avancer(vue, c);
    var d = resultat.decision();
    Observation cible = vue.trouver(d.cible()).orElse(null);
    if (d.phase() != Phase.SLEEP && d.phase() != Phase.IDLE && d.phase() != Phase.REST) {
      m.setSleeping(false);
      m.getBrain().setMemory(TFCBrain.WAKEUP_TICKS.get(), 40);
    }
    m.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    m.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    if (d.phase() == Phase.SLEEP || d.phase() == Phase.IDLE || d.phase() == Phase.REST) {
      if (!m.getBrain().hasMemoryValue(MemoryModuleType.HOME))
        m.getBrain()
            .setMemory(MemoryModuleType.HOME, GlobalPos.of(l.dimension(), m.blockPosition()));
    } else m.getBrain().eraseMemory(MemoryModuleType.HOME);
    LivingEntity vivant =
        cible == null ? null : l.getEntity(cible.id()) instanceof LivingEntity v ? v : null;
    boolean combat = d.phase().combat() && vivant != null && attaquable(vivant);
    if (combat && cible.visible()) {
      m.setTarget(vivant);
      m.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, vivant);
      m.setAggressive(true);
    } else {
      m.setTarget(null);
      m.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
      m.setAggressive(false);
    }
    Position point =
        cible == null ? (d.phase() == Phase.WANDER ? a.balade : null) : cible.position();
    // Un regard invisible suit la dernière position connue, jamais l'entité actuelle.
    if (point != null) m.getLookControl().setLookAt(point.x(), point.y() + 1, point.z(), 15, 15);
    a.bilan =
        a.navigation.avancer(
            tick, d, c, m.isBaby(), point, new PortNavigation(m, a.personnalite.mouvement()));
    if (combat
        && cible.visible()
        && m.hasLineOfSight(vivant)
        && m.isWithinMeleeAttackRange(vivant)
        && tick - a.derniereAttaque >= 40) {
      float avant = vivant.getHealth();
      boolean touche = m.doHurtTarget(vivant);
      a.derniereAttaque = tick;
      Journal.ecrire(
          l,
          m,
          a.rencontre,
          "COMBAT",
          "melee_result",
          Map.of(
              "phase",
              d.phase(),
              "target",
              vivant.getUUID(),
              "accepted",
              touche,
              "hpBefore",
              avant,
              "hpAfter",
              vivant.getHealth()));
    }
    a.sons.avancer(m, d, c, cible, a.rencontre, tick);
    if (m.isInWater() && !m.isSleeping() && m.getRandom().nextFloat() < .8f)
      m.getJumpControl().jump();
    if (tick % 100 == 0) Sauvegarde.ecrire(m, a.ressources);
    boolean proche =
        l.players().stream().anyMatch(p -> p.distanceTo(m) <= Configuration.RAYON_TRACE.get());
    // Les GameTests sans joueur restent observables lorsqu'ils provoquent une vraie rencontre.
    boolean tracer =
        proche || l.getServer() instanceof net.minecraft.gametest.framework.GameTestServer;
    if ((a.actif || l.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)
        && tracer) {
      var donnees = new LinkedHashMap<String, Object>();
      donnees.put("perception", vue);
      donnees.put("sensorChecks", a.sondages);
      var positionsEnfants = new LinkedHashMap<UUID, Object>();
      for (var id : enfants) {
        var petit = l.getEntity(id);
        if (petit != null)
          positionsEnfants.put(
              id,
              Map.of(
                  "distance",
                  m.distanceTo(petit),
                  "position",
                  PortNavigation.position(petit.position())));
      }
      donnees.put("offspringPositions", positionsEnfants);
      donnees.put("arbitration", resultat);
      donnees.put("navigation", a.bilan);
      donnees.put("configRevision", a.configRevision);
      donnees.put("offspringDefenseRadius", c.n(offspringDefenseRadius));
      donnees.put("actualSleeping", m.isSleeping());
      donnees.put("attackAbsentReason", combat ? "range_los_or_cooldown" : "phase_not_combat");
      Journal.ecrire(l, m, a.rencontre, "ARBITRATION", d.raison(), donnees);
      if (!groupe.equals(a.groupePrecedent)) {
        Journal.ecrire(l, m, a.rencontre, "PACK", groupe.veto(), groupe);
        a.groupePrecedent = groupe;
      }
      if (!enfants.equals(a.enfantsPrecedents)) {
        Journal.ecrire(l, m, a.rencontre, "CHILD", "family_membership", positionsEnfants);
        a.enfantsPrecedents = Set.copyOf(enfants);
      }
      if (!resultat.avant().equals(d))
        Journal.ecrire(
            l,
            m,
            a.rencontre,
            d.phase() == Phase.DRIVE_OFF ? "DRIVE_OFF" : d.phase() == Phase.WARN ? "WARN" : "STATE",
            d.raison(),
            d);
      if (!a.bilan.action().equals("reuse") && !a.bilan.action().equals("hold"))
        Journal.ecrire(l, m, a.rencontre, "NAV", a.bilan.action(), a.bilan);
      if (tick % 200 == 0)
        Journal.ecrire(
            l,
            m,
            a.rencontre,
            "ENCOUNTER_SUMMARY",
            "periodic",
            Map.of("decision", d, "pack", groupe, "children", enfants));
      a.limiteAnnoncee = false;
    } else if (a.actif && !a.limiteAnnoncee) {
      Journal.ecrire(
          l,
          m,
          a.rencontre,
          "ERROR",
          "trace_radius_limit",
          Map.of("radius", Configuration.RAYON_TRACE.get()));
      a.limiteAnnoncee = true;
    } else if (!a.actif && proche && tick % 200 == 0)
      Journal.ecrire(
          l, m, a.rencontre, "HEARTBEAT", "idle", Map.of("decision", d, "resources", a.ressources));
  }

  public void physique(LevelTickEvent.Post e) {
    if (!(e.getLevel() instanceof ServerLevel l)) return;
    var monde = mondes.get(l);
    if (monde == null) return;
    for (var m : monde.animaux.values()) {
      var a = monde.etats.get(m.getUUID());
      if (a == null || !a.actif || a.limiteAnnoncee || a.positionAvant == null || !gere(m))
        continue;
      Journal.ecrire(
          l,
          m,
          a.rencontre,
          "PHYSICAL",
          "observed_movement",
          Map.of(
              "position",
              PortNavigation.position(m.position()),
              "velocity",
              PortNavigation.position(m.getDeltaMovement()),
              "yaw",
              m.getYRot(),
              "yawBefore",
              a.angleAvant,
              "distanceMoved",
              a.positionAvant.distance(PortNavigation.position(m.position())),
              "navDone",
              m.getNavigation().isDone(),
              "speed",
              m.getSpeed()));
    }
  }

  private static Observation observation(
      Predator m, LivingEntity cible, long tick, boolean vu, boolean entendu, double approche) {
    Vec3 vers = m.position().subtract(cible.position()).normalize();
    return new Observation(
        cible.getUUID(),
        PortNavigation.position(cible.position()),
        sante(cible),
        cible instanceof Player,
        vu,
        entendu,
        tick,
        approche,
        cible.isSprinting(),
        cible.getLookAngle().dot(vers) > .5,
        cible instanceof Prey);
  }

  public void degat(LivingDamageEvent.Post e) {
    if (e.getNewDamage() <= 0
        || !(e.getEntity() instanceof Predator m)
        || !(m.level() instanceof ServerLevel l)
        || !gere(m)) return;
    long tick = l.getServer().getTickCount();
    var a = etat(m);
    var source = e.getSource().getEntity();
    String cause = "environment_" + e.getSource().getMsgId();
    if (source instanceof LivingEntity ennemi && attaquable(ennemi)) {
      a.agresseur = ennemi.getUUID();
      a.agresseAu = tick;
      a.memoire.noter(observation(m, ennemi, tick, m.hasLineOfSight(ennemi), true, 0));
      cause = ennemi instanceof Player ? "player_damage" : "mob_damage";
    } else if (e.getNewDamage() >= m.getMaxHealth() * .15)
      a.ressources = a.ressources.entendre(.35);
    Journal.ecrire(
        l,
        m,
        a.rencontre,
        "FEAR",
        cause,
        Map.of("amount", e.getNewDamage(), "hp", sante(m), "resources", a.ressources));
    if (m.isBaby()
        && Sauvegarde.parent(m) != null
        && l.getEntity(Sauvegarde.parent(m)) instanceof Predator parent
        && gere(parent)
        && source instanceof LivingEntity ennemi) {
      var adulte = etat(parent);
      adulte.enfantBlesseAu = tick;
      adulte.memoire.noter(
          observation(parent, ennemi, tick, parent.hasLineOfSight(ennemi), true, 0));
      Journal.ecrire(
          l,
          parent,
          adulte.rencontre,
          "CHILD",
          "own_child_damaged",
          Map.of("child", m.getUUID(), "attacker", ennemi.getUUID()));
    }
  }

  public void mort(LivingDeathEvent e) {
    if (e.getSource().getEntity() instanceof Predator m
        && gere(m)
        && e.getEntity() instanceof Prey) {
      var a = etat(m);
      a.ressources =
          new Ressources(
              Math.min(1, a.ressources.satiete() + .4),
              a.ressources.endurance(),
              a.ressources.peur());
    }
  }

  public void son(VanillaGameEvent e) {
    if (!(e.getLevel() instanceof ServerLevel l)
        || !(e.getCause() instanceof Player p)
        || !attaquable(p)) return;
    if (e.getVanillaEvent().is(GameEvent.STEP))
      entendre(
          l,
          e.getEventPosition(),
          p,
          p.isSprinting() ? "sprint" : "step",
          p.isSprinting() ? hearingSprintRadius : hearingStepRadius,
          0);
    else if (e.getVanillaEvent().is(GameEvent.HIT_GROUND))
      entendre(l, e.getEventPosition(), p, "landing", hearingLandingRadius, .04);
  }

  public void explosion(ExplosionEvent.Detonate e) {
    if (e.getLevel() instanceof ServerLevel l)
      entendre(l, e.getExplosion().center(), null, "explosion", hearingGunshotRadius, 1.2);
  }

  public void tir(LivingEntity tireur) {
    if (tireur.level() instanceof ServerLevel l)
      entendre(l, tireur.getEyePosition(), tireur, "gunshot", hearingGunshotRadius, 1.15);
  }

  private void entendre(
      ServerLevel l, Vec3 point, LivingEntity source, String genre, Parametre rayon, double force) {
    long tick = l.getServer().getTickCount();
    for (var m :
        l.getEntitiesOfClass(
            Predator.class, new AABB(point, point).inflate(256), v -> gere(v) && v != source)) {
      var c = Configuration.profil(type(m));
      var a = etat(m);
      String cle = genre + ":" + (source == null ? "world" : source.getUUID());
      if (tick - a.sonsEntendus.getOrDefault(cle, -100000L) < (force > 0 ? 4 : 10)) continue;
      boolean obstacle =
          l.clip(
                      new ClipContext(
                          point,
                          m.getEyePosition(),
                          ClipContext.Block.COLLIDER,
                          ClipContext.Fluid.NONE,
                          m))
                  .getType()
              != HitResult.Type.MISS;
      double intensite =
          Memoire.audition(
              point.distanceTo(m.getEyePosition()), c.n(rayon), obstacle, a.personnalite.sens());
      if (intensite <= .02) continue;
      a.sonsEntendus.put(cle, tick);
      a.ressources = a.ressources.entendre(force * intensite);
      if (source == null)
        a.memoire.noter(
            new Observation(
                DANGER_SONORE,
                PortNavigation.position(point),
                1,
                false,
                false,
                true,
                tick,
                0,
                false,
                false,
                false));
      if (source != null) {
        var o =
            new Observation(
                source.getUUID(),
                PortNavigation.position(point),
                sante(source),
                source instanceof Player,
                false,
                true,
                tick,
                0,
                source.isSprinting(),
                false,
                false);
        a.memoire.noter(o);
      }
      Journal.ecrire(
          l,
          m,
          a.rencontre,
          "PERCEPTION",
          "heard_" + genre,
          Map.of(
              "intensity",
              intensite,
              "occluded",
              obstacle,
              "position",
              PortNavigation.position(point),
              "fear",
              a.ressources.peur(),
              "radius",
              c.n(rayon)));
    }
  }
}
