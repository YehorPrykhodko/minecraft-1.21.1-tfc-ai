package ypmods.tfcwildlife.core;

import java.util.Optional;
import java.util.UUID;

public final class NavigationExecutor {
    public interface Port {
        Point position();
        boolean pathValid();
        boolean pathDone();
        Optional<Point> destination(Intent intent, Optional<Point> anchor, Optional<Point> previous);
        boolean moveTo(Point destination, double speed);
        void stop();
    }
    public record Route(Intent owner, Optional<UUID> target, Point destination, Optional<Point> threatAnchor,
                        long createdTick, long lastProgressTick, int failureCount) {}
    public record Outcome(String action, String cause, Optional<Route> route, double turnDegrees, boolean success, int failures) {
        public Outcome(String action, String cause, Optional<Route> route, double turnDegrees, boolean success) {
            this(action, cause, route, turnDegrees, success, 0);
        }
    }
    private Route route;
    private Point lastProgressPosition;
    private long nextRequest;
    private int failures;
    private boolean lastSuccess = true;
    private Intent requestedOwner;
    private Optional<UUID> requestedTarget = Optional.empty();

    public Outcome execute(Perception p, Decision decision, Optional<Point> anchor, Port port, Settings cfg) {
        if (!decision.intent().moving()) return stop(port, "hold_position");
        if (decision.intent() != Intent.IDLE && decision.intent() != Intent.FLEE && anchor.isEmpty()) return stop(port, "missing_anchor");
        boolean ownerChanged = route == null || route.owner != decision.intent() || !route.target.equals(decision.target());
        double progress = lastProgressPosition == null ? 0 : port.position().distanceSquared(lastProgressPosition);
        if (route != null && progress > 0.25) {
            route = new Route(route.owner, route.target, route.destination, route.threatAnchor,
                route.createdTick, p.tick(), route.failureCount);
            lastProgressPosition = port.position();
        }
        boolean stuck = route != null && p.tick() - route.lastProgressTick >= cfg.stuckTicks();
        boolean reached = route != null && port.position().distanceSquared(route.destination) < 2.25;
        boolean movedThreat = route != null && anchor.isPresent() && route.threatAnchor.isPresent()
            && anchor.get().distanceSquared(route.threatAnchor.get()) > (decision.intent().escape() ? 64 : 9);
        boolean pathInvalid = !port.pathValid() || port.pathDone();
        String cause = ownerChanged ? "owner_changed" : stuck ? "stuck" : reached ? "reached"
            : pathInvalid ? "path_invalid" : movedThreat ? "threat_moved" : "reuse";
        if (cause.equals("reuse")) return outcome("reuse", cause, 0, true);
        boolean newRequestOwner = requestedOwner != decision.intent() || !requestedTarget.equals(decision.target());
        if (p.tick() < nextRequest && !newRequestOwner) return outcome("reuse", "repath_cooldown", 0, lastSuccess);
        requestedOwner = decision.intent();
        requestedTarget = decision.target();
        Optional<Point> target = port.destination(decision.intent(), anchor,
            route == null ? Optional.empty() : Optional.of(route.destination));
        nextRequest = p.tick() + cfg.repathTicks() * Math.min(5, failures + 1);
        if (target.isEmpty()) {
            failures++;
            lastSuccess = false;
            return outcome("rejected", "no_reachable_destination", 0, false);
        }
        double angle = route == null ? 0 : route.destination.subtract(port.position()).horizontalAngle(target.get().subtract(port.position()));
        boolean success = port.moveTo(target.get(), MovementSpeeds.speed(p.species(), p.baby(), decision.intent(), p.personality()));
        lastSuccess = success;
        if (success) {
            failures = 0;
            route = new Route(decision.intent(), decision.target(), target.get(), anchor, p.tick(), p.tick(), 0);
            lastProgressPosition = port.position();
        } else {
            failures++;
            if (route != null) route = new Route(route.owner, route.target, route.destination, route.threatAnchor,
                route.createdTick, route.lastProgressTick, failures);
        }
        return outcome("request", cause, angle, success);
    }
    private Outcome stop(Port port, String reason) {
        if (route != null || !port.pathDone()) port.stop();
        route = null;
        lastProgressPosition = null;
        requestedOwner = null;
        requestedTarget = Optional.empty();
        nextRequest = 0;
        failures = 0;
        lastSuccess = true;
        return outcome("stop", reason, 0, true);
    }
    private Outcome outcome(String action, String cause, double angle, boolean success) {
        return new Outcome(action, cause, Optional.ofNullable(route), angle, success, failures);
    }
}
