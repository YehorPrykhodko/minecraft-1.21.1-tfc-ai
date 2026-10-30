package ypmods.tfcwildlife.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import ypmods.tfcwildlife.config.WildlifeConfig;
import ypmods.tfcwildlife.core.Intent;
import ypmods.tfcwildlife.core.NavigationExecutor;
import ypmods.tfcwildlife.core.Point;
import ypmods.tfcwildlife.core.Species;
import java.util.Optional;

public final class MinecraftNavigation implements NavigationExecutor.Port {
    private final PathfinderMob mob;
    private final Species species;
    private Path plannedPath;

    public MinecraftNavigation(PathfinderMob mob, Species species) { this.mob = mob; this.species = species; }
    public static Point point(Vec3 vector) { return new Point(vector.x, vector.y, vector.z); }
    public static Vec3 vector(Point point) { return new Vec3(point.x(), point.y(), point.z()); }
    @Override public Point position() { return point(mob.position()); }
    @Override public boolean pathValid() {
        Path path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) return false;
        BlockPos next = path.getNextNodePos();
        return mob.level().getBlockState(next).getCollisionShape(mob.level(), next).isEmpty();
    }
    @Override public boolean pathDone() { return mob.getNavigation().isDone(); }
    @Override public Optional<Point> destination(Intent intent, Optional<Point> anchor, Optional<Point> previous) {
        plannedPath = null;
        if (intent.combat() || intent == Intent.FOLLOW) return anchor.flatMap(this::reachable);
        if (intent.escape() && anchor.isPresent()) {
            Point away = position().subtract(anchor.get()).horizontalUnit();
            // Le solveur limite la recherche à FOLLOW_RANGE ; progresser par étapes atteignables.
            double distance = Math.min(intent == Intent.FLEE ? 24 : 14,
                Math.max(4, mob.getAttributeValue(Attributes.FOLLOW_RANGE) * 0.75));
            // Choix déterministe : ne changer de côté qu'en présence d'un obstacle.
            for (double angle : new double[]{0, 30, -30, 60, -60}) {
                double radians = Math.toRadians(angle);
                Point direction = new Point(away.x() * Math.cos(radians) - away.z() * Math.sin(radians), 0,
                    away.x() * Math.sin(radians) + away.z() * Math.cos(radians));
                for (int rise : new int[]{0, 1, -1, 2, -2, 3, -3}) {
                    Point candidate = position().add(direction.scale(distance)).add(new Point(0, rise, 0));
                    var possible = reachable(candidate);
                    if (possible.isPresent()) return possible;
                }
            }
            return Optional.empty();
        }
        Vec3 random = LandRandomPos.getPos(mob, 10, 4);
        return random == null ? Optional.empty() : reachable(point(random));
    }
    private Optional<Point> reachable(Point point) {
        BlockPos block = BlockPos.containing(point.x(), point.y(), point.z());
        if (!mob.level().hasChunkAt(block) || !mob.level().getFluidState(block).isEmpty()
            || !mob.level().getBlockState(block.below()).isSolidRender(mob.level(), block.below())) return Optional.empty();
        Path path = mob.getNavigation().createPath(block, 1);
        if (path == null || !path.canReach()) return Optional.empty();
        plannedPath = path;
        return Optional.of(point);
    }
    @Override public boolean moveTo(Point destination, double speed) {
        Intent intent = currentIntent;
        if (species == Species.BEAR && !mob.isBaby()) {
            if (intent == Intent.ATTACK) speed = speed / 2.0 * WildlifeConfig.BEAR_CHASE.get();
            if (intent == Intent.FLEE) speed = speed / 1.25 * WildlifeConfig.BEAR_FLEE.get();
        }
        return plannedPath != null && mob.getNavigation().moveTo(plannedPath, speed);
    }
    private Intent currentIntent = Intent.IDLE;
    public void intent(Intent intent) { currentIntent = intent; }
    @Override public void stop() { mob.getNavigation().stop(); }
}
