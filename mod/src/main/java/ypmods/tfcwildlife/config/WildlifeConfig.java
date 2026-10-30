package ypmods.tfcwildlife.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import ypmods.tfcwildlife.core.Settings;

public final class WildlifeConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue PACK_PLAYER_HP, SOLO_HUNGER, SOLO_CHANCE, BEAR_CHASE, BEAR_FLEE;
    public static final ModConfigSpec.DoubleValue CRITICAL_HP, PANIC_ENTER, PANIC_EXIT, PACK_ENTER_RADIUS, PACK_EXIT_RADIUS;
    public static final ModConfigSpec.IntValue PANIC_HOLD, COMBAT_HOLD, RETREAT_HOLD, MEMORY_TICKS, ENCOUNTER_COOLDOWN,
        PACK_GRACE, REPATH_TICKS, STUCK_TICKS;
    public static final ModConfigSpec.BooleanValue DEBUG;
    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("behavior");
        PACK_PLAYER_HP = builder.comment("Seuil initial hérité ; la faim ne déclenche pas la chasse en meute.")
            .defineInRange("hyenaPackPlayerHp", 0.70, 0.01, 1);
        SOLO_HUNGER = builder.defineInRange("hyenaSoloHunger", 0.80, 0, 1);
        SOLO_CHANCE = builder.comment("Probabilité par rencontre, au plus une fois toutes les 60 secondes.")
            .defineInRange("hyenaSoloChance", 0.02, 0, 1);
        BEAR_CHASE = builder.comment("Multiplicateur de vitesse de navigation, sans modification d'attribut.")
            .defineInRange("adultBearChaseMultiplier", 2.0, 0.5, 2.0);
        BEAR_FLEE = builder.defineInRange("adultBearFleeMultiplier", 1.25, 0.5, 1.5);
        CRITICAL_HP = builder.defineInRange("criticalHp", 0.25, 0.01, 0.9);
        PANIC_ENTER = builder.defineInRange("panicEnter", 0.8, 0.1, 1);
        PANIC_EXIT = builder.defineInRange("panicExit", 0.35, 0, 0.9);
        PANIC_HOLD = builder.defineInRange("panicHoldTicks", 80, 1, 1200);
        COMBAT_HOLD = builder.defineInRange("combatHoldTicks", 100, 1, 1200);
        RETREAT_HOLD = builder.defineInRange("retreatHoldTicks", 40, 1, 1200);
        MEMORY_TICKS = builder.defineInRange("memoryTicks", 160, 1, 2400);
        ENCOUNTER_COOLDOWN = builder.defineInRange("soloEncounterCooldownTicks", 1200, 20, 72000);
        PACK_GRACE = builder.defineInRange("packGraceTicks", 100, 0, 1200);
        PACK_ENTER_RADIUS = builder.defineInRange("packEnterRadius", 18.0, 1, 64);
        PACK_EXIT_RADIUS = builder.defineInRange("packExitRadius", 24.0, 1, 96);
        REPATH_TICKS = builder.defineInRange("repathTicks", 12, 1, 200);
        STUCK_TICKS = builder.defineInRange("stuckTicks", 60, 1, 1200);
        builder.pop();
        DEBUG = builder.comment("Traces structurées ; utiliser /wildlife debug pour choisir un animal.").define("debug", false);
        SPEC = builder.build();
    }
    private WildlifeConfig() {}
    public static Settings settings() {
        return new Settings(PACK_PLAYER_HP.get(), SOLO_HUNGER.get(), SOLO_CHANCE.get(), CRITICAL_HP.get(),
            PANIC_ENTER.get(), PANIC_EXIT.get(), PANIC_HOLD.get(), COMBAT_HOLD.get(), RETREAT_HOLD.get(), MEMORY_TICKS.get(),
            ENCOUNTER_COOLDOWN.get(), PACK_GRACE.get(), PACK_ENTER_RADIUS.get(), PACK_EXIT_RADIUS.get(), REPATH_TICKS.get(), STUCK_TICKS.get());
    }
}
