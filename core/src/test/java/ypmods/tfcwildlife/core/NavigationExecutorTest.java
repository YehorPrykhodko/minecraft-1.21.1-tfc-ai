package ypmods.tfcwildlife.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static ypmods.tfcwildlife.core.Fixtures.*;

class NavigationExecutorTest {
    static final class FakePort implements NavigationExecutor.Port {
        Point position = new Point(0, 64, 0);
        Point proposed = new Point(-14, 64, 0);
        boolean valid = true, done, reachable = true, success = true;
        int moves, chooses, stops;
        public Point position() { return position; }
        public boolean pathValid() { return valid; }
        public boolean pathDone() { return done; }
        public Optional<Point> destination(Intent intent, Optional<Point> anchor, Optional<Point> prior) {
            chooses++; return reachable ? Optional.of(proposed) : Optional.empty();
        }
        public boolean moveTo(Point target, double speed) { moves++; return success; }
        public void stop() { stops++; }
    }
    private Perception p(long tick) { return perception(tick, Species.BEAR, 1, 0, 0, Perception.Social.solo(), 1); }
    private Decision d(Intent intent) { return new Decision(intent, "test", "test", 100, 200, Optional.of(PLAYER)); }
    @ParameterizedTest @EnumSource(value = Intent.class, names = {"RETREAT", "FLEE"})
    void stableEscapeDoesNotRechooseEveryTick(Intent intent) {
        var executor = new NavigationExecutor();
        var port = new FakePort();
        var anchor = Optional.of(new Point(7, 64, 0));
        var first = executor.execute(p(100), d(intent), anchor, port, CFG);
        port.proposed = new Point(14, 64, 0);
        for (int i = 101; i < 140; i++) {
            var reused = executor.execute(p(i), d(intent), anchor, port, CFG);
            assertEquals("reuse", reused.action());
            assertEquals(first.route().orElseThrow().destination(), reused.route().orElseThrow().destination());
        }
        assertEquals(1, port.moves);
        assertEquals(1, port.chooses);
    }
    @Test void stuckDetectionReplansAndReportsReversalCause() {
        var executor = new NavigationExecutor(); var port = new FakePort();
        var anchor = Optional.of(new Point(7, 64, 0));
        executor.execute(p(100), d(Intent.RETREAT), anchor, port, CFG);
        port.proposed = new Point(14, 64, 0);
        var replanned = executor.execute(p(161), d(Intent.RETREAT), anchor, port, CFG);
        assertEquals("stuck", replanned.cause());
        assertEquals(180, replanned.turnDegrees(), 0.01);
        assertEquals(2, port.moves);
    }
    @Test void significantThreatMovementRepathsOnlyAfterCooldown() {
        var executor = new NavigationExecutor(); var port = new FakePort();
        executor.execute(p(100), d(Intent.RETREAT), Optional.of(new Point(7, 64, 0)), port, CFG);
        assertEquals("repath_cooldown", executor.execute(p(101), d(Intent.RETREAT), Optional.of(new Point(-7, 64, 0)), port, CFG).cause());
        assertEquals("threat_moved", executor.execute(p(113), d(Intent.RETREAT), Optional.of(new Point(-7, 64, 0)), port, CFG).cause());
    }
    @Test void invalidPathsRetryWithBackoffRatherThanEveryTick() {
        var executor = new NavigationExecutor(); var port = new FakePort();
        port.success = false; port.valid = false;
        for (int i = 100; i < 112; i++) executor.execute(p(i), d(Intent.RETREAT), Optional.of(new Point(7, 64, 0)), port, CFG);
        assertEquals(1, port.moves);
        executor.execute(p(112), d(Intent.RETREAT), Optional.of(new Point(7, 64, 0)), port, CFG);
        assertEquals(2, port.moves);
    }
    @Test void unreachableDestinationIsReportedWithoutUnsafeMove() {
        var executor = new NavigationExecutor(); var port = new FakePort(); port.reachable = false;
        var result = executor.execute(p(100), d(Intent.FLEE), Optional.of(new Point(7, 64, 0)), port, CFG);
        assertFalse(result.success()); assertEquals("no_reachable_destination", result.cause()); assertEquals(0, port.moves);
        assertEquals(1, result.failures());
        assertFalse(executor.execute(p(101), d(Intent.FLEE), Optional.of(new Point(7, 64, 0)), port, CFG).success());
    }
    @Test void holdStopsOnceAndClearsRoute() {
        var executor = new NavigationExecutor(); var port = new FakePort();
        executor.execute(p(100), d(Intent.ATTACK), Optional.of(new Point(7, 64, 0)), port, CFG);
        port.done = true;
        var result = executor.execute(p(101), d(Intent.ASSESS), Optional.of(new Point(7, 64, 0)), port, CFG);
        assertEquals("stop", result.action()); assertTrue(result.route().isEmpty()); assertEquals(1, port.stops);
    }
    @Test void missingTargetStopsCombatNavigation() {
        var executor = new NavigationExecutor(); var port = new FakePort();
        executor.execute(p(100), d(Intent.ATTACK), Optional.of(new Point(7, 64, 0)), port, CFG);
        var result = executor.execute(p(101), d(Intent.ATTACK), Optional.empty(), port, CFG);
        assertEquals("missing_anchor", result.cause()); assertTrue(result.route().isEmpty());
    }
    @Test void rejectedProposalCannotChangeDestinationReasonOrActiveCooldown() {
        var executor = new NavigationExecutor(); var port = new FakePort();
        var anchor = Optional.of(new Point(7, 64, 0));
        var first = executor.execute(p(100), attacking(), anchor, port, CFG);
        var proposal = new Proposal(Intent.RETREAT, "player_pursuing", "perception", 350, 500, false, p(101).threat());
        var reduced = new DecisionReducer().reduce(attacking(), p(101), java.util.List.of(proposal), CFG);
        port.proposed = new Point(14, 64, 0);
        var after = executor.execute(p(101), reduced.decision(), anchor, port, CFG);
        assertEquals(first.route(), after.route());
        assertEquals("combat", reduced.decision().reason());
        assertEquals(190, reduced.decision().lockedUntil());
        assertEquals(1, port.moves);
    }
}
