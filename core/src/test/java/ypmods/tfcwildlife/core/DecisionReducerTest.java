package ypmods.tfcwildlife.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static ypmods.tfcwildlife.core.Fixtures.*;

class DecisionReducerTest {
    private final DecisionReducer reducer = new DecisionReducer();
    private DecisionReducer.Result tick(Decision before, Perception p) {
        return reducer.reduce(before, p, SpeciesRules.propose(p, before, CFG), CFG);
    }

    @ParameterizedTest
    @ValueSource(doubles = {1.9, 7.2, 15.18})
    void routineSignalsCannotCancelCombatOrMutateReason(double distance) {
        Decision original = attacking(), state = original;
        for (int i = 100; i < 500; i++) {
            var p = withThreat(perception(i, Species.BEAR, 1, 0.4, 0, Perception.Social.solo(), 1),
                Optional.of(player(i, 1, distance)));
            var result = tick(state, p);
            assertSame(original, result.decision());
            assertTrue(result.evaluations().stream().anyMatch(e -> e.veto().equals("combat_commitment")));
            state = result.decision();
        }
    }
    @Test void criticalInjuryInterruptsCombat() {
        var result = tick(attacking(), perception(100, Species.BEAR, 0.1, 1, 0, Perception.Social.solo(), 0));
        assertEquals(Intent.FLEE, result.decision().intent());
        assertEquals("critical_injury", result.decision().reason());
    }
    @Test void realFearInterruptsAndDoesNotBounceBack() {
        Decision state = tick(attacking(), perception(100, Species.BEAR, 1, 1, 0.9, Perception.Social.solo(), 0)).decision();
        assertEquals(Intent.FLEE, state.intent());
        for (int i = 101; i < 180; i++) {
            var p = perception(i, Species.BEAR, 1, 1, 0, Perception.Social.solo(), 0);
            assertSame(state, tick(state, p).decision());
        }
        assertNotEquals(Intent.FLEE, tick(state, perception(181, Species.BEAR, 1, 0.4, 0, Perception.Social.solo(), 1)).decision().intent());
    }
    @Test void fearAboveExitThresholdPersistsAfterMinimumHold() {
        Decision flee = new Decision(Intent.FLEE, "panic", "survival", 0, 80, Optional.of(PLAYER));
        assertSame(flee, tick(flee, perception(1000, Species.HYENA, 1, 1, 0.5, Perception.Social.solo(), 0)).decision());
    }
    @Test void injuredHyenaCannotFollowHungerOutOfFlee() {
        Decision flee = new Decision(Intent.FLEE, "critical_injury", "survival", 0, 80, Optional.of(PLAYER));
        assertEquals(Intent.FLEE, tick(flee, perception(500, Species.HYENA, 0.1, 1, 0, Perception.Social.solo(), 0)).decision().intent());
    }
    @Test void expiredThreatEndsCombat() {
        var p = perception(500, Species.BEAR, 1, 0, 0, Perception.Social.solo(), 1);
        p = withThreat(p, Optional.of(player(0, 1, 7)));
        assertEquals(Intent.IDLE, tick(attacking(), p).decision().intent());
    }
    @Test void sameProposalDoesNotExtendCooldownOrReplaceReason() {
        var d = attacking();
        var p = perception(100, Species.BEAR, 1, 0, 0, Perception.Social.solo(), 1);
        var proposal = new Proposal(Intent.ATTACK, "new_reason", "other", 900, 900, false, p.threat());
        assertSame(d, reducer.reduce(d, p, List.of(proposal), CFG).decision());
        assertEquals(190, d.lockedUntil());
    }
    @Test void onlyHighestPriorityProposalIsAcceptedRegardlessOfInputOrder() {
        var p = perception(100, Species.BEAR, 0.1, 1, 1, Perception.Social.solo(), 0);
        var proposals = SpeciesRules.propose(p, attacking(), CFG);
        var reversed = new java.util.ArrayList<>(proposals);
        java.util.Collections.reverse(reversed);
        var a = reducer.reduce(attacking(), p, proposals, CFG);
        assertEquals(a, reducer.reduce(attacking(), p, reversed, CFG));
        assertEquals(1, a.evaluations().stream().filter(DecisionReducer.Evaluation::accepted).count());
    }
    @Test void controllerRejectsDuplicateOrOutOfOrderTick() {
        var agent = new AnimalController(ANIMAL, () -> 0.5);
        var p = perception(100, Species.BEAR, 1, 0, 0, Perception.Social.solo(), 1);
        agent.tick(p, CFG);
        assertThrows(IllegalStateException.class, () -> agent.tick(p, CFG));
        assertThrows(IllegalStateException.class, () -> agent.tick(perception(99, Species.BEAR, 1, 0, 0, Perception.Social.solo(), 1), CFG));
    }
    @Test void rareRollOccursOncePerCooldownAndCannotBeRerolledEveryTick() {
        int[] calls = {0};
        var agent = new AnimalController(ANIMAL, () -> { calls[0]++; return 0; });
        assertEquals(0, agent.encounterRoll(100, true, CFG));
        for (int tick = 101; tick < 1300; tick++) assertEquals(1, agent.encounterRoll(tick, true, CFG));
        assertEquals(1, calls[0]);
        assertEquals(0, agent.encounterRoll(1300, true, CFG));
        assertEquals(2, calls[0]);
    }
    @Test void calmBearAssessesBeforeEngagement() {
        var p = withThreat(perception(100, Species.BEAR, 1, 0, 0, Perception.Social.solo(), 1), Optional.of(player(100, 1, 12)));
        assertEquals(Intent.ASSESS, tick(Decision.idle(0), p).decision().intent());
    }
    @Test void soloSatiatedDoesNotHuntWeakPlayer() {
        var p = withThreat(perception(100, Species.HYENA, 1, 0, 0, Perception.Social.solo(), 0), Optional.of(player(100, 0.2, 10)));
        assertFalse(tick(Decision.idle(0), p).decision().intent().combat());
    }
    @Test void soloHungerRequiresSuccessfulRareRoll() {
        var common = perception(100, Species.HYENA, 1, 1, 0, Perception.Social.solo(), 0.8);
        assertFalse(tick(Decision.idle(0), common).decision().intent().combat());
        var rare = perception(100, Species.HYENA, 1, 1, 0, Perception.Social.solo(), 0);
        assertEquals(Intent.ATTACK, tick(Decision.idle(0), rare).decision().intent());
    }
}
