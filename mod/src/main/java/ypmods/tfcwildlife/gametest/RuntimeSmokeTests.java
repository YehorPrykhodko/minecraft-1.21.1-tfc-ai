package ypmods.tfcwildlife.gametest;

import com.tacz.guns.api.event.common.GunFireEvent;
import net.dries007.tfc.common.entities.TFCEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ypmods.tfcwildlife.TFCWildlifeAI;
import ypmods.tfcwildlife.core.Vitals;
import ypmods.tfcwildlife.minecraft.BrainOwner;
import ypmods.tfcwildlife.minecraft.PersistentVitals;
import ypmods.tfcwildlife.minecraft.WildlifeRuntime;

@GameTestHolder(TFCWildlifeAI.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RuntimeSmokeTests {
    private RuntimeSmokeTests() {}

    @GameTest(template = "smoke", batch = "wildlife_brains", timeoutTicks = 40)
    public static void allTfcBrainMixinsOwnLiveEntities(GameTestHelper helper) {
        var bear = helper.spawn(TFCEntities.GRIZZLY_BEAR.get(), 2, 2, 2);
        var hyena = helper.spawn(TFCEntities.HYENA.get(), 5, 2, 2);
        var deer = helper.spawn(TFCEntities.DEER.get(), 8, 2, 2);
        var boar = helper.spawn(TFCEntities.BOAR.get(), 11, 2, 2);
        helper.runAtTickTime(5, () -> {
            for (Mob mob : new Mob[]{bear, hyena, deer, boar}) {
                helper.assertTrue(mob instanceof BrainOwner, "Mixin de brain absent : " + mob.getType());
                helper.assertTrue(WildlifeRuntime.owns(mob), "Le contrôleur doit gérer l'animal vivant");
            }
            ((BrainOwner)bear).wildlife$wake();
            helper.assertFalse(bear.isSleeping(), "L'accesseur de réveil doit appeler TFC");
            helper.killAllEntities();
            helper.succeed();
        });
    }

    @GameTest(template = "smoke", batch = "wildlife_persistence", timeoutTicks = 20)
    public static void persistentDataMigratesWithoutDestroyingLegacy(GameTestHelper helper) {
        var bear = TFCEntities.GRIZZLY_BEAR.get().create(helper.getLevel());
        helper.assertTrue(bear != null, "Création du grizzly TFC impossible");
        bear.getPersistentData().putDouble("tfc_wildlife_ai.satiety", 60.3);
        bear.getPersistentData().putDouble("tfc_wildlife_ai.stamina", 80);
        Vitals migrated = PersistentVitals.read(bear);
        helper.assertTrue(Math.abs(migrated.satiety() - 0.603) < 1e-9, "Échelle de satiété incorrecte");
        PersistentVitals.write(bear, migrated);
        helper.assertTrue(bear.getPersistentData().getDouble("tfc_wildlife_ai.satiety") == 60.3, "Ancienne donnée détruite");
        helper.assertTrue(PersistentVitals.read(bear).equals(migrated), "Lecture après migration incohérente");
        CompoundTag future = new CompoundTag();
        future.putInt("schema", PersistentVitals.SCHEMA + 1);
        future.putString("marker", "preserver");
        bear.getPersistentData().put(PersistentVitals.KEY, future);
        PersistentVitals.write(bear, Vitals.fresh());
        helper.assertTrue(bear.getPersistentData().getCompound(PersistentVitals.KEY).equals(future), "Schéma futur écrasé");
        helper.assertFalse(WildlifeRuntime.owns(bear), "Un schéma futur ne doit pas être pris en charge");
        helper.succeed();
    }

    @GameTest(template = "smoke", batch = "wildlife_combat", timeoutTicks = 100)
    public static void damagedGrizzlyCommitsAndDealsRealMeleeDamage(GameTestHelper helper) {
        var bear = helper.spawn(TFCEntities.GRIZZLY_BEAR.get(), 25, 2, 25);
        var attacker = helper.spawn(EntityType.ZOMBIE, 27, 2, 25);
        attacker.setNoAi(true);
        helper.assertTrue(bear.hurt(helper.getLevel().damageSources().mobAttack(attacker), 1), "Le dégât initial doit être réel");
        float initialHealth = attacker.getHealth();
        helper.runAtTickTime(10, () -> helper.assertTrue(bear.getTarget() == attacker && bear.isAggressive(), "La défense n'a pas engagé le combat"));
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(attacker.getHealth() < initialHealth, "L'exécuteur doit infliger un dégât de mêlée réel");
            helper.assertTrue(bear.getTarget() == attacker, "Le combat ne doit pas être interrompu par la proximité");
            helper.killAllEntities();
            helper.succeed();
        });
    }

    @GameTest(template = "smoke", batch = "wildlife_tacz", timeoutTicks = 80)
    public static void taczServerEventInterruptsCombatWithRealPanic(GameTestHelper helper) {
        var bear = helper.spawn(TFCEntities.GRIZZLY_BEAR.get(), 25, 2, 25);
        var attacker = helper.spawn(EntityType.ZOMBIE, 27, 2, 25);
        attacker.setNoAi(true);
        bear.hurt(helper.getLevel().damageSources().mobAttack(attacker), 1);
        double[] initialSeparation = {0};
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(bear.getTarget() == attacker, "Combat préalable absent");
            attacker.moveTo(bear.getX() + 1, bear.getY(), bear.getZ());
            initialSeparation[0] = bear.distanceToSqr(attacker);
            var shot = NeoForge.EVENT_BUS.post(new GunFireEvent(attacker, ItemStack.EMPTY, LogicalSide.SERVER));
            helper.assertFalse(shot.isCanceled(), "Le tir synthétique a été annulé avant notre écouteur");
        });
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(bear.getTarget() == null && !bear.isAggressive(), "Le tir proche doit interrompre le combat");
            helper.assertFalse(bear.getNavigation().isDone(), "La panique doit produire une commande de fuite réelle");
        });
        helper.runAtTickTime(35, () -> {
            helper.assertTrue(bear.distanceToSqr(attacker) > initialSeparation[0] + 4,
                "La fuite doit éloigner physiquement l'animal, sans se limiter à un chemin calculé");
            helper.killAllEntities();
            helper.succeed();
        });
    }
}
