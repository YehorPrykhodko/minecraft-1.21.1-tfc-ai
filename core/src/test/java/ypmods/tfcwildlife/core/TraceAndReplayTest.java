package ypmods.tfcwildlife.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static ypmods.tfcwildlife.core.Fixtures.*;

class TraceAndReplayTest {
    @Test void rejectedReasonAndPathRemainSeparateInMachineReadableTrace() {
        var p = perception(100, Species.BEAR, 1, 0, 0, Perception.Social.solo(), 1);
        var before = attacking();
        var result = new DecisionReducer().reduce(before, p, List.of(new Proposal(Intent.RETREAT,
            "human_too_close", "perception", 350, 40, false, p.threat())), CFG);
        var nav = new NavigationExecutor.Outcome("reuse", "reuse", Optional.empty(), 0, true);
        var trace = JsonParser.parseString(DecisionTrace.json(p, before, result, nav, CFG, "shot\n\"test\"\\end")).getAsJsonObject();
        assertEquals("combat", trace.get("reason").getAsString());
        assertEquals("shot\n\"test\"\\end", trace.get("event").getAsString());
        var rejected = trace.getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertFalse(rejected.get("accepted").getAsBoolean());
        assertEquals("human_too_close", rejected.get("reason").getAsString());
        assertEquals("combat_commitment", rejected.get("veto").getAsString());
        assertEquals(ANIMAL.toString(), trace.get("mobUuid").getAsString());
        assertEquals(100, trace.get("tick").getAsLong());
        assertFalse(trace.toString().contains(PLAYER.toString()));
    }

    @ParameterizedTest
    @CsvSource({"logs/wildlife_324.txt,644", "logs/wildlife_323.txt,776", "logs/wildlife_321.txt,1709"})
    void legacyRoutineSignalsCannotProduceOneTickCombatReversal(String filename, int entityId) throws Exception {
        var lines = Files.readAllLines(Path.of(filename)).stream()
            .filter(line -> line.contains("grizzly_bear#" + entityId + " "))
            .filter(line -> line.contains("human_too_close") || line.contains("player_pursuing") || line.contains("species_close_defense"))
            .toList();
        assertFalse(lines.isEmpty(), "Le jeu de traces doit réellement contenir le cas demandé");
        Decision decision = attacking();
        long tick = 100;
        for (String observation : lines) {
            double distance = observation.contains("human_too_close") ? 1.9 : 7.2;
            var p = withThreat(perception(tick++, Species.BEAR, 1, 0.4, 0, Perception.Social.solo(), 1),
                Optional.of(player(tick - 1, 1, distance)));
            var result = new DecisionReducer().reduce(decision, p, SpeciesRules.propose(p, decision, CFG), CFG);
            assertSame(decision, result.decision());
            assertEquals("combat", result.decision().reason());
        }
    }
}
