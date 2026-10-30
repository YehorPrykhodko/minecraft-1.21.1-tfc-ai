package ypmods.tfcwildlife.core;

import java.util.Map;

public record Vitals(double satiety, double stamina, double fear) {
    public Vitals {
        for (double n : new double[]{satiety, stamina, fear}) {
            if (!Double.isFinite(n) || n < 0 || n > 1) throw new IllegalArgumentException("Ressource invalide");
        }
    }
    public static Vitals fresh() { return new Vitals(0.65, 1, 0); }
    public Vitals advance(Intent intent, long elapsedTicks) {
        double elapsed = Math.clamp(elapsedTicks, 0, 200);
        double drain = intent.combat() || intent == Intent.FLEE ? -0.0005 : 0.00035;
        return new Vitals(Math.clamp(satiety - elapsed * 0.0000075, 0, 1),
            Math.clamp(stamina + drain * elapsed, 0, 1), Math.max(0, fear - elapsed * 0.003));
    }
    public Vitals heard(double intensity) { return new Vitals(satiety, stamina, Math.max(fear, Math.clamp(intensity, 0, 1))); }
    public Vitals fed() { return new Vitals(Math.min(1, Math.max(0.78, satiety + 0.62)), stamina, fear); }

    public static Vitals restore(Map<String, ? extends Number> data) {
        boolean current = data.getOrDefault("schema", null) != null;
        String satietyKey = current ? "satiety" : "tfc_wildlife_ai.satiety";
        String staminaKey = current ? "stamina" : "tfc_wildlife_ai.stamina";
        double factor = current ? 1 : 0.01;
        return new Vitals(number(data, satietyKey, 0.65, factor), number(data, staminaKey, 1, factor), 0);
    }
    private static double number(Map<String, ? extends Number> data, String key, double fallback, double factor) {
        Number value = data.get(key);
        double n = value == null ? fallback : value.doubleValue() * factor;
        return Double.isFinite(n) ? Math.clamp(n, 0, 1) : fallback;
    }
}
