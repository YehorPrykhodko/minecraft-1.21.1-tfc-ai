package ypmods.tfcwildlife.core;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static ypmods.tfcwildlife.core.Fixtures.*;

class SensesAndPersistenceTest {
    @Test void gunshotOcclusionAndDistanceAttenuateFear() {
        var source = new Point(80, 64, 0); var listener = new Point(0, 64, 0);
        var clear = new Senses().hear(PLAYER, Senses.Sound.GUNSHOT, source, listener, false, 1, 100);
        var blocked = new Senses().hear(PLAYER, Senses.Sound.GUNSHOT, source, listener, true, 1, 100);
        assertTrue(clear.accepted()); assertTrue(blocked.accepted()); assertTrue(blocked.fear() < clear.fear());
        assertTrue(clear.fear() < CFG.panicEnter());
        assertEquals(0, new Senses().hear(PLAYER, Senses.Sound.STEP, listener, listener, false, 1, 100).fear());
    }
    @Test void closeLoudShotCanCauseRealPanic() {
        var location = new Point(0, 64, 0);
        assertTrue(new Senses().hear(PLAYER, Senses.Sound.GUNSHOT, location, location, false, 1, 100).fear() >= CFG.panicEnter());
    }
    @Test void repeatedSoundsAreDeduplicatedButDistinctShootersAreNot() {
        var senses = new Senses(); var location = new Point(0, 64, 0);
        assertTrue(senses.hear(PLAYER, Senses.Sound.GUNSHOT, location, location, false, 1, 100).accepted());
        assertFalse(senses.hear(PLAYER, Senses.Sound.GUNSHOT, location, location, false, 1, 101).accepted());
        assertTrue(senses.hear(new UUID(1, 2), Senses.Sound.GUNSHOT, location, location, false, 1, 101).accepted());
    }
    @Test void visionRequiresLosAndFovAndRange() {
        var observer = new Point(0, 64, 0); var facing = new Point(1, 0, 0);
        assertTrue(Senses.visible(observer, facing, new Point(10, 64, 0), true, 230, 32));
        assertFalse(Senses.visible(observer, facing, new Point(10, 64, 0), false, 230, 32));
        assertFalse(Senses.visible(observer, facing, new Point(-10, 64, 0), true, 230, 32));
        assertFalse(Senses.visible(observer, facing, new Point(40, 64, 0), true, 230, 32));
    }
    @Test void legacyVitalsMigrateReadOnlyAndClampCorruptValues() {
        var old = Map.of("tfc_wildlife_ai.satiety", 60.3, "tfc_wildlife_ai.stamina", 100.0);
        var migrated = Vitals.restore(old);
        assertEquals(0.603, migrated.satiety(), 1e-9); assertEquals(1, migrated.stamina());
        assertEquals(60.3, old.get("tfc_wildlife_ai.satiety"));
        var corrupt = Vitals.restore(Map.of("tfc_wildlife_ai.satiety", Double.NaN, "tfc_wildlife_ai.stamina", 200));
        assertEquals(0.65, corrupt.satiety()); assertEquals(1, corrupt.stamina());
    }
    @Test void currentVitalsUseNormalizedScaleAndFearDoesNotPersistAcrossReload() {
        assertEquals(new Vitals(0.4, 0.6, 0), Vitals.restore(Map.of("schema", 2, "satiety", 0.4, "stamina", 0.6)));
    }
    @Test void feedingAndRestRecoverResources() {
        var exhausted = new Vitals(0, 0.1, 1);
        assertTrue(exhausted.fed().satiety() >= 0.78);
        var rest = exhausted.advance(Intent.REST, 200);
        assertTrue(rest.stamina() > exhausted.stamina()); assertTrue(rest.fear() < exhausted.fear());
    }
    @Test void personalityIsStableAndIndividual() {
        assertEquals(Personality.from(ANIMAL), Personality.from(ANIMAL));
        assertNotEquals(Personality.from(ANIMAL), Personality.from(PLAYER));
    }
    @Test void speedDoesNotStackAttributeAndNavigationModifiers() {
        assertEquals(2.3, MovementSpeeds.speed(Species.BEAR, false, Intent.ATTACK, TRAITS), 1e-9);
        assertEquals(1.15 * 1.25, MovementSpeeds.speed(Species.BEAR, false, Intent.FLEE, TRAITS), 1e-9);
        assertEquals(1.15 * 0.85 * 1.3, MovementSpeeds.speed(Species.BEAR, true, Intent.FLEE, TRAITS), 1e-9);
        assertEquals(1.15 * 1.2, MovementSpeeds.speed(Species.WOLF, false, Intent.ATTACK, TRAITS), 1e-9);
    }
    @Test void petsLivestockAndMarineAnimalsRemainOutsideManagedSet() {
        for (String id : new String[]{"tfc:dog", "tfc:cat", "tfc:musk_ox", "tfc:cow", "tfc:crocodile", "minecraft:wolf"}) {
            assertTrue(Species.fromId(id).isEmpty(), id);
        }
    }
}
