package ypmods.tfcwildlife.core;

public final class MovementSpeeds {
    private MovementSpeeds() {}
    public static double speed(Species species, boolean baby, Intent intent, Personality personality) {
        double gait = switch (intent) {
            case FLEE -> 1.15; case ATTACK, HUNT -> 1.15; case RETREAT -> 0.75;
            case FOLLOW -> 0.85; case IDLE -> 0.55; default -> 0;
        };
        double factor = 1;
        if (species == Species.BEAR) {
            if (baby) factor = 0.85 * (intent == Intent.FLEE ? 1.30 : 1);
            else if (intent == Intent.ATTACK) factor = 2;
            else if (intent == Intent.FLEE) factor = 1.25;
        } else if (species == Species.WOLF) factor = 1.2;
        return gait * factor * personality.speed();
    }
}
