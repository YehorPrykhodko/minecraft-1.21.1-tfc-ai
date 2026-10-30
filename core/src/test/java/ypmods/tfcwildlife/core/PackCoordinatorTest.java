package ypmods.tfcwildlife.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static ypmods.tfcwildlife.core.Fixtures.*;

class PackCoordinatorTest {
    static PackCoordinator.Member member(int id, double x, long tick, double playerHp, boolean safe) {
        return new PackCoordinator.Member(new UUID(0, id), "tfc:hyena", new Point(x, 64, 0), true, true, safe,
            Optional.of(player(tick, playerHp, 10)));
    }
    @Test void hungryPackDoesNotHuntHealthyPlayer() {
        var social = new PackCoordinator().coordinate(100, List.of(member(1, 0, 100, 1, true), member(2, 4, 100, 1, true)), CFG);
        for (var context : social.values()) {
            assertTrue(context.pack());
            assertFalse(context.hunt());
            var p = perception(100, Species.HYENA, 1, 1, 0, context, 0);
            var d = new DecisionReducer().reduce(Decision.idle(0), p, SpeciesRules.propose(p, Decision.idle(0), CFG), CFG);
            assertFalse(d.decision().intent().combat());
        }
    }
    @Test void packSynchronizesWeakPlayerRegardlessOfHungerOrOrder() {
        var members = new ArrayList<PackCoordinator.Member>();
        for (int i = 1; i <= 5; i++) members.add(member(i, i, 100, 0.69, true));
        var contexts = new PackCoordinator().coordinate(100, members, CFG);
        Collections.reverse(members);
        assertEquals(contexts, new PackCoordinator().coordinate(100, members, CFG));
        contexts.forEach((id, social) -> {
            assertTrue(social.hunt());
            assertEquals(PLAYER, social.target().orElseThrow().id());
            var p = perception(100, Species.HYENA, 1, 0, 0, social, 1);
            var d = new DecisionReducer().reduce(Decision.idle(0), p, SpeciesRules.propose(p, Decision.idle(0), CFG), CFG);
            assertEquals(Intent.ATTACK, d.decision().intent());
            assertEquals("pack_player_hp", d.decision().reason());
        });
    }
    @Test void exactThresholdDoesNotStartHunt() {
        var map = new PackCoordinator().coordinate(1, List.of(member(1, 0, 1, 0.7, true), member(2, 2, 1, 0.7, true)), CFG);
        assertTrue(map.values().stream().noneMatch(Perception.Social::hunt));
    }
    @Test void woundedMemberEscapesWhileHealthyMembersHunt() {
        var map = new PackCoordinator().coordinate(100, List.of(member(1, 0, 100, 0.5, true), member(2, 3, 100, 0.5, true), member(3, 4, 100, 0.5, false)), CFG);
        assertTrue(map.get(new UUID(0, 1)).hunt());
        var context = map.get(new UUID(0, 3));
        assertFalse(context.hunt());
        var p = perception(100, Species.HYENA, 0.1, 1, 0, context, 0);
        assertEquals(Intent.FLEE, new DecisionReducer().reduce(Decision.idle(0), p,
            SpeciesRules.propose(p, Decision.idle(0), CFG), CFG).decision().intent());
    }
    @Test void oneSafeAdultCannotStartPackHunt() {
        var map = new PackCoordinator().coordinate(100, List.of(member(1, 0, 100, 0.5, true), member(2, 3, 100, 0.5, false)), CFG);
        assertTrue(map.values().stream().noneMatch(Perception.Social::hunt));
    }
    @Test void radiusJitterDoesNotSwitchSoloPackEveryTick() {
        var pack = new PackCoordinator();
        double[] distances = {17, 23, 25, 23, 17};
        UUID packId = null;
        for (int i = 0; i < distances.length; i++) {
            var map = pack.coordinate(100 + i, List.of(member(1, 0, 100 + i, 1, true), member(2, distances[i], 100 + i, 1, true)), CFG);
            var social = map.get(ANIMAL);
            assertTrue(social.pack());
            if (packId != null) assertEquals(packId, social.packId());
            packId = social.packId();
        }
    }
    @Test void prolongedSeparationAndDeathRemoveMembership() {
        var pack = new PackCoordinator();
        pack.coordinate(1, List.of(member(1, 0, 1, 1, true), member(2, 2, 1, 1, true)), CFG);
        var separated = pack.coordinate(102, List.of(member(1, 0, 102, 1, true), member(2, 50, 102, 1, true)), CFG);
        assertTrue(separated.values().stream().noneMatch(Perception.Social::pack));
        var dead = pack.coordinate(103, List.of(member(1, 0, 103, 1, true)), CFG);
        assertFalse(dead.get(ANIMAL).pack());
    }
    @Test void differentSpeciesNeverSharePack() {
        var wolf = new PackCoordinator.Member(new UUID(0, 2), "tfc:wolf", new Point(2, 64, 0), true, true, true, Optional.of(player(100, 0.5, 10)));
        assertFalse(new PackCoordinator().coordinate(100, List.of(member(1, 0, 100, 0.5, true), wolf), CFG).get(ANIMAL).pack());
    }
    @Test void babiesCannotEstablishPack() {
        var cub = new PackCoordinator.Member(new UUID(0, 2), "tfc:hyena", new Point(2, 64, 0), false, true, true, Optional.empty());
        assertFalse(new PackCoordinator().coordinate(100, List.of(member(1, 0, 100, 0.5, true), cub), CFG).get(ANIMAL).pack());
    }
    @Test void unseenPlayerIsNotSelectedOmnisciently() {
        var noVision = new PackCoordinator.Member(new UUID(0, 2), "tfc:hyena", new Point(2, 64, 0), true, true, true, Optional.empty());
        var noVision2 = new PackCoordinator.Member(ANIMAL, "tfc:hyena", new Point(0, 64, 0), true, true, true, Optional.empty());
        assertTrue(new PackCoordinator().coordinate(100, List.of(noVision, noVision2), CFG).values().stream().noneMatch(Perception.Social::hunt));
    }
    @Test void sharedCommitmentExpiresAfterPlayerRecovers() {
        var pack = new PackCoordinator();
        var initial = pack.coordinate(100, List.of(member(1, 0, 100, 0.5, true), member(2, 2, 100, 0.5, true)), CFG);
        assertTrue(initial.get(ANIMAL).hunt());
        assertTrue(pack.coordinate(101, List.of(member(1, 0, 101, 1, true), member(2, 2, 101, 1, true)), CFG).get(ANIMAL).hunt());
        assertFalse(pack.coordinate(201, List.of(member(1, 0, 201, 1, true), member(2, 2, 201, 1, true)), CFG).get(ANIMAL).hunt());
    }
}
