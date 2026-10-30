package ypmods.tfcwildlife.core;

public record Settings(double packPlayerHp, double soloHunger, double soloChance,
                       double criticalHp, double panicEnter, double panicExit,
                       int panicHold, int combatHold, int retreatHold, int memoryTicks,
                       int encounterCooldown, int packGrace, double packEnterRadius,
                       double packExitRadius, int repathTicks, int stuckTicks) {
    public static Settings defaults() {
        return new Settings(0.70, 0.80, 0.02, 0.25, 0.80, 0.35,
            80, 100, 40, 160, 1200, 100, 18, 24, 12, 60);
    }
    public Settings {
        for (double v : new double[]{packPlayerHp, soloHunger, soloChance, criticalHp, panicEnter, panicExit}) {
            if (!Double.isFinite(v) || v < 0 || v > 1) throw new IllegalArgumentException("Seuil invalide");
        }
        if (panicExit >= panicEnter || panicHold < 1 || combatHold < 1 || retreatHold < 1 || memoryTicks < 1
            || encounterCooldown < 1 || packGrace < 0 || !Double.isFinite(packEnterRadius)
            || !Double.isFinite(packExitRadius) || packEnterRadius <= 0 || packExitRadius < packEnterRadius
            || repathTicks < 1 || stuckTicks < repathTicks) throw new IllegalArgumentException("Hystérésis invalide");
    }
}
