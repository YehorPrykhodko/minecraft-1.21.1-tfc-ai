package ypmods.tfcwildlife.minecraft;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Mob;
import ypmods.tfcwildlife.core.Vitals;
import java.util.HashMap;

public final class PersistentVitals {
    public static final String KEY = "tfc_wildlife_ai.remaster";
    public static final int SCHEMA = 2;
    private PersistentVitals() {}

    public static boolean supported(Mob mob) {
        CompoundTag root = mob.getPersistentData();
        return !root.contains(KEY, Tag.TAG_COMPOUND) || root.getCompound(KEY).getInt("schema") <= SCHEMA;
    }
    public static Vitals read(Mob mob) {
        CompoundTag root = mob.getPersistentData();
        var numbers = new HashMap<String, Number>();
        if (root.contains(KEY, Tag.TAG_COMPOUND)) {
            var current = root.getCompound(KEY);
            numbers.put("schema", current.getInt("schema"));
            for (String key : new String[]{"satiety", "stamina"}) {
                if (current.contains(key, Tag.TAG_ANY_NUMERIC)) numbers.put(key, current.getDouble(key));
            }
        } else {
            for (String key : new String[]{"tfc_wildlife_ai.satiety", "tfc_wildlife_ai.stamina"}) {
                if (root.contains(key, Tag.TAG_ANY_NUMERIC)) numbers.put(key, root.getDouble(key));
            }
        }
        return Vitals.restore(numbers);
    }
    public static void write(Mob mob, Vitals vitals) {
        if (!supported(mob)) return;
        var current = new CompoundTag();
        current.putInt("schema", SCHEMA);
        current.putDouble("satiety", vitals.satiety());
        current.putDouble("stamina", vitals.stamina());
        mob.getPersistentData().put(KEY, current);
    }
}
