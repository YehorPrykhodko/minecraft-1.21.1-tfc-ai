package ypmods.tfcwildlife.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static ypmods.tfcwildlife.core.Fixtures.*;

class AcceptanceCasesTest {
    @TestFactory Stream<DynamicTest> suppliedScenariosExecuteAgainstNewEngine() throws Exception {
        var cases = JsonParser.parseString(Files.readString(Path.of("tests/acceptance_cases.json"))).getAsJsonObject().getAsJsonArray("cases");
        Set<String> ids = new HashSet<>();
        return java.util.stream.StreamSupport.stream(cases.spliterator(), false).map(element -> {
            var scenario = element.getAsJsonObject();
            String id = scenario.get("id").getAsString();
            assertTrue(ids.add(id), "Identifiant dupliqué");
            return DynamicTest.dynamicTest(id, () -> execute(id));
        });
    }
    private void execute(String id) throws Exception {
        var tests = new DecisionReducerTest();
        switch (id) {
            case "grizzly-approach-calm" -> tests.calmBearAssessesBeforeEngagement();
            case "grizzly-attack-committed" -> tests.routineSignalsCannotCancelCombatOrMutateReason(1.9);
            case "grizzly-attack-pursued" -> tests.routineSignalsCannotCancelCombatOrMutateReason(7.2);
            case "grizzly-critical-injury" -> tests.criticalInjuryInterruptsCombat();
            case "grizzly-real-fear" -> tests.realFearInterruptsAndDoesNotBounceBack();
            case "grizzly-retreat-target" -> new NavigationExecutorTest().stableEscapeDoesNotRechooseEveryTick(Intent.RETREAT);
            case "immutability-veto" -> tests.sameProposalDoesNotExtendCooldownOrReplaceReason();
            case "hyena-solo-satiated" -> tests.soloSatiatedDoesNotHuntWeakPlayer();
            case "hyena-solo-hungry" -> tests.soloHungerRequiresSuccessfulRareRoll();
            case "hyena-pack-healthy-player" -> new PackCoordinatorTest().hungryPackDoesNotHuntHealthyPlayer();
            case "hyena-pack-weak-player" -> new PackCoordinatorTest().packSynchronizesWeakPlayerRegardlessOfHungerOrOrder();
            case "hyena-pack-wounded-member" -> new PackCoordinatorTest().woundedMemberEscapesWhileHealthyMembersHunt();
            case "hyena-pack-stability" -> new PackCoordinatorTest().radiusJitterDoesNotSwitchSoloPackEveryTick();
            case "hyena-flee-hunger" -> tests.injuredHyenaCannotFollowHungerOutOfFlee();
            case "sound-occlusion" -> new SensesAndPersistenceTest().gunshotOcclusionAndDistanceAttenuateFear();
            case "trace-reason" -> new TraceAndReplayTest().rejectedReasonAndPathRemainSeparateInMachineReadableTrace();
            default -> fail("Scénario sans vérification exécutable : " + id);
        }
    }
}
