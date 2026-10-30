package ypmods.tfcwildlife.minecraft;

import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import ypmods.tfcwildlife.TFCWildlifeAI;
import ypmods.tfcwildlife.config.WildlifeConfig;
import ypmods.tfcwildlife.core.*;
import ypmods.tfcwildlife.debug.Diagnostics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;

public final class WildlifeRuntime {
    private static final class Animal {
        final AnimalController controller;
        final NavigationExecutor navigation = new NavigationExecutor();
        final Senses senses = new Senses();
        final Personality personality;
        Vitals vitals;
        Perception.Threat memory, observation, attacker, preyMemory;
        long lastTick, provokedUntil, lastMelee;
        String event = "spawn_or_load";
        Animal(PathfinderMob mob, long tick) {
            var rng = new SplittableRandom(mob.getUUID().getLeastSignificantBits() ^ tick);
            controller = new AnimalController(mob.getUUID(), rng::nextDouble);
            personality = Personality.from(mob.getUUID());
            vitals = PersistentVitals.read(mob);
            lastTick = tick;
        }
    }
    private static final class World {
        final Map<UUID, Animal> animals = new HashMap<>();
        final PackCoordinator packs = new PackCoordinator();
    }
    private final Map<ServerLevel, World> worlds = new HashMap<>();
    private final Diagnostics diagnostics = new Diagnostics();

    public static boolean owns(Mob mob) {
        return mob instanceof BrainOwner && mob instanceof PathfinderMob && Species.fromId(id(mob)).isPresent()
            && !mob.isNoAi() && !mob.isLeashed() && !mob.isPassenger() && PersistentVitals.supported(mob);
    }
    private static String id(Entity entity) { return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(); }
    private static boolean attackable(LivingEntity entity) {
        return entity.isAlive() && (!(entity instanceof Player p) || (!p.isCreative() && !p.isSpectator()));
    }
    private static double hp(LivingEntity entity) { return Math.clamp(entity.getHealth() / Math.max(1, entity.getMaxHealth()), 0, 1); }
    private Animal state(PathfinderMob mob, long tick) {
        return worlds.computeIfAbsent((ServerLevel)mob.level(), key -> new World()).animals
            .computeIfAbsent(mob.getUUID(), key -> new Animal(mob, tick));
    }

    public void onLevelTick(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long tick = level.getGameTime();
        Settings settings = WildlifeConfig.settings();
        var animals = new ArrayList<PathfinderMob>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof PathfinderMob mob && owns(mob) && mob.isAlive()
                && level.isPositionEntityTicking(mob.blockPosition())) animals.add(mob);
        }
        animals.sort(Comparator.comparing(Entity::getUUID));
        World world = worlds.computeIfAbsent(level, key -> new World());
        for (PathfinderMob mob : animals) perceive(level, mob, state(mob, tick), tick, settings);
        var members = animals.stream().filter(mob -> Species.fromId(id(mob)).orElseThrow().social()).map(mob -> {
            Animal a = state(mob, tick);
            return new PackCoordinator.Member(mob.getUUID(), id(mob), MinecraftNavigation.point(mob.position()),
                !mob.isBaby(), true, hp(mob) > 0.4 && a.vitals.fear() < settings.panicEnter(),
                Optional.ofNullable(a.observation).filter(Perception.Threat::player),
                Optional.ofNullable(a.attacker).filter(t -> a.provokedUntil > tick && t.attackable()));
        }).toList();
        var social = world.packs.coordinate(tick, members, settings);
        for (PathfinderMob mob : animals) {
            Animal animal = state(mob, tick);
            var context = social.getOrDefault(mob.getUUID(), Perception.Social.solo());
            step(level, mob, animal, context, tick, settings);
        }
    }

    private void perceive(ServerLevel level, PathfinderMob mob, Animal animal, long tick, Settings settings) {
        animal.vitals = animal.vitals.advance(animal.controller.decision().intent(), tick - animal.lastTick);
        animal.lastTick = tick;
        Species species = Species.fromId(id(mob)).orElseThrow();
        animal.observation = null;
        var players = level.getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(32), WildlifeRuntime::attackable);
        Player closest = players.stream().filter(p -> visible(mob, p, species))
            .min(Comparator.<Player>comparingDouble(mob::distanceToSqr).thenComparing(Entity::getUUID)).orElse(null);
        // Une cible engagée conserve la priorité sur un passant plus proche.
        Optional<UUID> engaged = animal.controller.decision().target();
        if (engaged.isPresent() && level.getEntity(engaged.get()) instanceof Player current && attackable(current)
            && visible(mob, current, species)) closest = current;
        if (closest != null) {
            animal.observation = threat(mob, closest, tick, true, false, animal.memory);
            animal.memory = animal.observation;
        }
        if (animal.memory != null) {
            Entity remembered = level.getEntity(animal.memory.id());
            if (!(remembered instanceof LivingEntity living) || !attackable(living)
                || tick - animal.memory.observedTick() > settings.memoryTicks()) animal.memory = null;
        }
        if (animal.attacker != null) {
            Entity entity = level.getEntity(animal.attacker.id());
            if (!(entity instanceof LivingEntity living) || !attackable(living) || animal.provokedUntil <= tick) animal.attacker = null;
            else if (mob.hasLineOfSight(living)) animal.attacker = threat(mob, living, tick, true, false, animal.attacker);
        }
        if (animal.controller.decision().intent() == Intent.HUNT && engaged.isPresent()) {
            Entity entity = level.getEntity(engaged.get());
            if (entity instanceof LivingEntity target && attackable(target)) {
                if (visible(mob, target, species)) animal.preyMemory = threat(mob, target, tick, true, false, animal.preyMemory);
                else if (animal.preyMemory != null) animal.preyMemory = remembered(animal.preyMemory);
            } else animal.preyMemory = null;
        }
        if (animal.preyMemory != null && tick - animal.preyMemory.observedTick() > settings.memoryTicks()) animal.preyMemory = null;
    }

    private void step(ServerLevel level, PathfinderMob mob, Animal a, Perception.Social social, long tick, Settings cfg) {
        Species species = Species.fromId(id(mob)).orElseThrow();
        Perception.Threat danger = a.attacker != null ? a.attacker : social.hunt() ? social.target().orElse(a.memory) : a.memory;
        if (danger != null && danger.observedTick() != tick) danger = remembered(danger);
        Optional<Perception.Threat> prey = Optional.empty();
        Optional<Perception.Threat> parent = Optional.empty();
        var nearby = level.getEntitiesOfClass(PathfinderMob.class, mob.getBoundingBox().inflate(24),
            candidate -> candidate != mob && candidate.isAlive());
        var same = nearby.stream().filter(candidate -> candidate.getType() == mob.getType()).toList();
        boolean offspring = !mob.isBaby() && same.stream().anyMatch(child -> child.isBaby() && child.distanceToSqr(mob) < 100);
        if (mob.isBaby()) parent = same.stream().filter(adult -> !adult.isBaby())
            .min(Comparator.comparingDouble(mob::distanceToSqr)).map(adult -> threat(mob, adult, tick, true, false, null));
        if (species.predator() && a.vitals.satiety() < 0.52) {
            prey = a.preyMemory != null ? Optional.of(a.preyMemory) : nearby.stream().filter(candidate -> Species.fromId(id(candidate)).map(s -> s == Species.PREY || s == Species.RAMMER).orElse(false))
                .filter(candidate -> !candidate.isInWater() && visible(mob, candidate, species))
                .min(Comparator.comparingDouble(mob::distanceToSqr)).map(target -> threat(mob, target, tick, true, false, null));
            a.preyMemory = prey.orElse(null);
        }
        if (!species.predator() && danger == null) {
            danger = nearby.stream().filter(candidate -> Species.fromId(id(candidate)).map(Species::predator).orElse(false))
                .filter(candidate -> visible(mob, candidate, species)).min(Comparator.comparingDouble(mob::distanceToSqr))
                .map(target -> threat(mob, target, tick, true, false, null)).orElse(null);
        }
        boolean eligibleRoll = species == Species.HYENA && !mob.isBaby() && !social.pack()
            && a.vitals.satiety() <= 1 - cfg.soloHunger() && danger != null && danger.visible();
        var perception = new Perception(tick, mob.getUUID(), species, MinecraftNavigation.point(mob.position()), mob.isBaby(),
            hp(mob), 1 - a.vitals.satiety(), a.vitals.stamina(), a.vitals.fear(), mob.isOnFire() || mob.isInLava(),
            a.provokedUntil > tick && a.attacker != null, offspring, a.personality, Optional.ofNullable(danger), prey, parent,
            social, a.controller.encounterRoll(tick, eligibleRoll, cfg));
        Decision before = a.controller.decision();
        var result = a.controller.tick(perception, cfg);
        Decision decision = result.decision();
        if (decision.intent() != Intent.IDLE && decision.intent() != Intent.REST && mob instanceof BrainOwner owner) owner.wildlife$wake();
        // Le brain TFC ne s'exécute plus pour ces animaux ; aucune mémoire de marche concurrente.
        mob.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        mob.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
        Optional<Perception.Threat> selected = java.util.stream.Stream.of(perception.threat(), perception.prey(), perception.parent(), social.target())
            .flatMap(Optional::stream).filter(t -> decision.target().filter(t.id()::equals).isPresent()).findFirst();
        Optional<Point> anchor = selected.map(Perception.Threat::position);
        if (decision.intent() == Intent.FLEE && anchor.isEmpty()) anchor = perception.threat().map(Perception.Threat::position);
        var port = new MinecraftNavigation(mob, species);
        port.intent(decision.intent());
        var navigation = a.navigation.execute(perception, decision, anchor, port, cfg);
        executeCombat(level, mob, a, decision, selected, tick);
        if (mob.isInWater() && mob.getRandom().nextFloat() < 0.8f) mob.getJumpControl().jump();
        if (tick % 100 == 0) PersistentVitals.write(mob, a.vitals);
        diagnostics.emit(perception, before, result, navigation, cfg, a.event);
        a.event = "tick";
    }

    private void executeCombat(ServerLevel level, PathfinderMob mob, Animal a, Decision decision,
                               Optional<Perception.Threat> selected, long tick) {
        LivingEntity target = selected.map(Perception.Threat::id).map(level::getEntity)
            .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).orElse(null);
        if (decision.intent().combat() && target != null && attackable(target)) {
            mob.setTarget(target);
            mob.setAggressive(true);
            mob.getLookControl().setLookAt(target, 30, 30);
            if (selected.orElseThrow().visible() && mob.hasLineOfSight(target) && mob.isWithinMeleeAttackRange(target)
                && tick >= a.lastMelee + 40) {
                mob.doHurtTarget(target);
                a.lastMelee = tick;
            }
        } else {
            mob.setTarget(null);
            mob.setAggressive(false);
            if (target != null) mob.getLookControl().setLookAt(target, 30, 30);
        }
    }

    private static boolean visible(PathfinderMob observer, LivingEntity target, Species species) {
        double radius = target instanceof Player p && p.isCrouching() ? 20 : 32;
        return Senses.visible(MinecraftNavigation.point(observer.position()), MinecraftNavigation.point(observer.getLookAngle()),
            MinecraftNavigation.point(target.position()), observer.hasLineOfSight(target), species.predator() ? 230 : 285, radius);
    }
    private static Perception.Threat remembered(Perception.Threat t) {
        return new Perception.Threat(t.id(), t.position(), t.hp(), t.player(), false, t.heard(), t.observedTick(),
            false, t.sprinting(), t.watching(), t.attackable());
    }
    private static Perception.Threat threat(PathfinderMob observer, LivingEntity target, long tick, boolean visible,
                                             boolean heard, Perception.Threat previous) {
        Point position = MinecraftNavigation.point(target.position());
        boolean approach = previous != null && previous.id().equals(target.getUUID())
            && position.distanceSquared(MinecraftNavigation.point(observer.position()))
                < previous.position().distanceSquared(MinecraftNavigation.point(observer.position())) - 0.03;
        Vec3 towards = observer.position().subtract(target.position()).normalize();
        boolean watching = target.getLookAngle().dot(towards) > 0.5;
        return new Perception.Threat(target.getUUID(), position, hp(target), target instanceof Player, visible, heard, tick,
            approach, target.isSprinting(), watching, attackable(target));
    }

    public void onDamage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0 || !(event.getEntity() instanceof PathfinderMob mob) || !(mob.level() instanceof ServerLevel) || !owns(mob)) return;
        Entity source = event.getSource().getEntity();
        if (source instanceof LivingEntity attacker && attacker != mob && attackable(attacker)) {
            long tick = mob.level().getGameTime();
            Animal animal = state(mob, tick);
            animal.attacker = threat(mob, attacker, tick, mob.hasLineOfSight(attacker), false, animal.attacker);
            animal.provokedUntil = tick + WildlifeConfig.settings().memoryTicks();
            animal.event = "direct_damage";
        }
    }
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            World world = worlds.get(level);
            if (world != null) world.packs.invalidateTarget(event.getEntity().getUUID());
        }
        if (event.getSource().getEntity() instanceof PathfinderMob killer && killer.level() instanceof ServerLevel && owns(killer)
            && !(event.getEntity() instanceof Player)) {
            Animal animal = state(killer, killer.level().getGameTime());
            animal.vitals = animal.vitals.fed();
            animal.event = "prey_kill";
        }
    }
    public void onSound(VanillaGameEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getCause() instanceof Player player) || !attackable(player)) return;
        Senses.Sound kind;
        if (event.getVanillaEvent().is(GameEvent.STEP)) kind = player.isSprinting() ? Senses.Sound.SPRINT : Senses.Sound.STEP;
        else if (event.getVanillaEvent().is(GameEvent.HIT_GROUND)) kind = Senses.Sound.LANDING;
        else return;
        sound(level, event.getEventPosition(), player.getUUID(), kind);
    }
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) sound(level, event.getExplosion().center(), new UUID(0, 0), Senses.Sound.EXPLOSION);
    }
    public void gunshot(LivingEntity shooter) {
        if (shooter.level() instanceof ServerLevel level) sound(level, shooter.getEyePosition(), shooter.getUUID(), Senses.Sound.GUNSHOT);
    }
    private void sound(ServerLevel level, Vec3 location, UUID source, Senses.Sound sound) {
        long tick = level.getGameTime();
        for (PathfinderMob mob : level.getEntitiesOfClass(PathfinderMob.class,
            new net.minecraft.world.phys.AABB(location, location).inflate(112), WildlifeRuntime::owns)) {
            if (mob.getUUID().equals(source)) continue;
            Animal animal = state(mob, tick);
            boolean occluded = level.clip(new ClipContext(location, mob.getEyePosition(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, mob)).getType() != HitResult.Type.MISS;
            var heard = animal.senses.hear(source, sound, MinecraftNavigation.point(location), MinecraftNavigation.point(mob.position()), occluded, 1, tick);
            if (!heard.accepted()) continue;
            animal.vitals = animal.vitals.heard(heard.fear());
            animal.event = "heard_" + sound.name().toLowerCase(java.util.Locale.ROOT);
            if (level.getEntity(source) instanceof Player player && attackable(player) && animal.observation == null) {
                animal.memory = new Perception.Threat(source, MinecraftNavigation.point(location), hp(player), true,
                    false, true, tick, false, player.isSprinting(), false, true);
            }
        }
    }
    public void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        World world = worlds.get(level);
        if (world != null && event.getEntity() instanceof PathfinderMob mob) {
            Animal removed = world.animals.remove(mob.getUUID());
            if (removed != null) PersistentVitals.write(mob, removed.vitals);
        }
    }
    public void onJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Mob mob
            && Species.fromId(id(mob)).isPresent() && !(mob instanceof BrainOwner)) {
            throw new IllegalStateException("Mixin de propriété du brain absent : " + id(mob));
        }
    }
    public void onServerStopped(ServerStoppedEvent event) { worlds.clear(); diagnostics.off(); }
    public void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) worlds.remove(level);
    }
    public void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wildlife").requires(source -> source.hasPermission(2))
            .then(Commands.literal("debug").then(Commands.literal("off").executes(context -> {
                diagnostics.off(); context.getSource().sendSuccess(() -> Component.literal("Diagnostic ciblé désactivé."), false); return 1;
            })).then(Commands.literal("nearest").executes(context -> {
                var source = context.getSource();
                var nearest = source.getLevel().getEntitiesOfClass(PathfinderMob.class,
                    new net.minecraft.world.phys.AABB(source.getPosition(), source.getPosition()).inflate(32), WildlifeRuntime::owns)
                    .stream().min(Comparator.comparingDouble(mob -> mob.distanceToSqr(source.getPosition())));
                if (nearest.isEmpty()) { source.sendFailure(Component.literal("Aucun animal géré à proximité.")); return 0; }
                diagnostics.select(nearest.get().getUUID());
                source.sendSuccess(() -> Component.literal("Diagnostic activé pour " + nearest.get().getUUID()), false);
                return 1;
            }))));
    }
}
