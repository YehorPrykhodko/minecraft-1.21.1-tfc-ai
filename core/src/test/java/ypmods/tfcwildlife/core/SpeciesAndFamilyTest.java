package ypmods.tfcwildlife.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ypmods.tfcwildlife.core.Fixtures.*;

class SpeciesAndFamilyTest {
    private Decision decide(Decision before, Perception p) {
        return new DecisionReducer().reduce(before, p, SpeciesRules.propose(p, before, CFG), CFG).decision();
    }
    @Test void babyFollowsAdultButThreatInterruptsFollowing() {
        var adult = new Perception.Threat(new UUID(0, 3), new Point(10, 64, 0), 1, false, true, false, 100, false, false, false, true);
        var p = new Perception(100, ANIMAL, Species.BEAR, new Point(0, 64, 0), true, 1, 0, 1, 0,
            false, false, false, TRAITS, Optional.empty(), Optional.empty(), Optional.of(adult), Perception.Social.solo(), 1);
        var follow = decide(Decision.idle(0), p);
        assertEquals(Intent.FOLLOW, follow.intent());
        var danger = new Perception(101, ANIMAL, Species.BEAR, p.position(), true, 1, 0, 1, 0,
            false, false, false, TRAITS, Optional.of(player(101, 1, 8)), Optional.empty(), Optional.of(adult), Perception.Social.solo(), 1);
        assertEquals(Intent.FLEE, decide(follow, danger).intent());
    }
    @Test void offspringDefenseIsDistinctFromUnprovokedHunger() {
        var p = new Perception(100, ANIMAL, Species.BEAR, new Point(0, 64, 0), false, 1, 0, 1, 0,
            false, false, true, TRAITS, Optional.of(player(100, 1, 4)), Optional.empty(), Optional.empty(), Perception.Social.solo(), 1);
        assertEquals("offspring_defense", decide(Decision.idle(0), p).reason());
    }
    @Test void felinePausesThenAmbushesWhenPlayerLooksAway() {
        var target = new Perception.Threat(PLAYER, new Point(10, 64, 0), 1, true, true, false, 100, false, false, false, true);
        var p = new Perception(100, ANIMAL, Species.FELINE, new Point(0, 64, 0), false, 1, 0.7, 1, 0,
            false, false, false, TRAITS, Optional.of(target), Optional.empty(), Optional.empty(), Perception.Social.solo(), 1);
        var pause = decide(Decision.idle(0), p);
        assertEquals(Intent.ASSESS, pause.intent());
        var later = new Perception(131, ANIMAL, Species.FELINE, p.position(), false, 1, 0.7, 1, 0,
            false, false, false, TRAITS, Optional.of(target), Optional.empty(), Optional.empty(), Perception.Social.solo(), 1);
        assertEquals(Intent.AMBUSH, decide(pause, later).intent());
    }
    @Test void directHyenaDefenseDoesNotRequireHungerOrLowPlayerHp() {
        var p = new Perception(100, ANIMAL, Species.HYENA, new Point(0, 64, 0), false, 1, 0, 1, 0,
            false, true, false, TRAITS, Optional.of(player(100, 1, 4)), Optional.empty(), Optional.empty(), Perception.Social.solo(), 1);
        assertEquals("direct_self_defense", decide(Decision.idle(0), p).reason());
    }
    @Test void packDefenseCoordinatesAgainstHealthyAttacker() {
        var m1 = new PackCoordinator.Member(ANIMAL, "tfc:hyena", new Point(0, 64, 0), true, true, true,
            Optional.of(player(100, 1, 4)), Optional.of(player(100, 1, 4)));
        var m2 = PackCoordinatorTest.member(2, 3, 100, 1, true);
        var social = new PackCoordinator().coordinate(100, List.of(m1, m2), CFG);
        assertTrue(social.get(new UUID(0, 2)).defending());
        var p = perception(100, Species.HYENA, 1, 0, 0, social.get(new UUID(0, 2)), 1);
        assertEquals("pack_self_defense", decide(Decision.idle(0), p).reason());
    }
    @Test void deadPackTargetInvalidatesSharedCommitment() {
        var pack = new PackCoordinator();
        pack.coordinate(100, List.of(PackCoordinatorTest.member(1, 0, 100, 0.5, true), PackCoordinatorTest.member(2, 2, 100, 0.5, true)), CFG);
        pack.invalidateTarget(PLAYER);
        var empty1 = new PackCoordinator.Member(ANIMAL, "tfc:hyena", new Point(0, 64, 0), true, true, true, Optional.empty());
        var empty2 = new PackCoordinator.Member(new UUID(0, 2), "tfc:hyena", new Point(2, 64, 0), true, true, true, Optional.empty());
        assertTrue(pack.coordinate(101, List.of(empty1, empty2), CFG).values().stream().noneMatch(Perception.Social::hunt));
    }
    @Test void exhaustedCombatCanCautiouslyRetreatWithoutInventingPanic() {
        var p = new Perception(100, ANIMAL, Species.BEAR, new Point(0, 64, 0), false, 1, 0, 0.1, 0,
            false, false, false, TRAITS, Optional.of(player(100, 1, 4)), Optional.empty(), Optional.empty(), Perception.Social.solo(), 1);
        var retreat = decide(attacking(), p);
        assertEquals(Intent.RETREAT, retreat.intent());
        assertEquals("exhausted", retreat.reason());
    }
}
