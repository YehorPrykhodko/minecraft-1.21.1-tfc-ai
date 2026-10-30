package ypmods.tfcwildlife.core;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

public final class DecisionTrace {
    private DecisionTrace() {}
    public static String json(Perception p, Decision before, DecisionReducer.Result result,
                              NavigationExecutor.Outcome navigation, Settings cfg, String event) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("type", "decision"); fields.put("tick", p.tick()); fields.put("mobUuid", p.animal().toString());
        fields.put("species", p.species().name()); fields.put("event", event); fields.put("prior", before.intent().name());
        fields.put("priorReason", before.reason()); fields.put("committed", result.decision().intent().name());
        fields.put("reason", result.decision().reason()); fields.put("source", result.decision().source());
        fields.put("since", result.decision().since()); fields.put("lockedUntil", result.decision().lockedUntil());
        fields.put("ownHp", p.ownHp()); fields.put("hunger", p.hunger()); fields.put("stamina", p.stamina());
        fields.put("fear", p.fear()); fields.put("playerHp", p.threat().filter(Perception.Threat::player).map(Perception.Threat::hp).orElse(null));
        fields.put("social", p.social().pack() ? "PACK" : "SOLO"); fields.put("packAdults", p.social().adults());
        fields.put("packHunt", p.social().hunt()); fields.put("baby", p.baby()); fields.put("offspring", p.offspringNearby());
        fields.put("thresholds", Map.of("packPlayerHp", cfg.packPlayerHp(), "panicEnter", cfg.panicEnter(),
            "panicExit", cfg.panicExit(), "criticalHp", cfg.criticalHp()));
        fields.put("candidates", result.evaluations().stream().map(e -> Map.of("intent", e.proposal().intent().name(),
            "reason", e.proposal().reason(), "source", e.proposal().source(), "accepted", e.accepted(), "veto", e.veto())).toList());
        fields.put("navigation", Map.of("action", navigation.action(), "cause", navigation.cause(),
            "success", navigation.success(), "turnDegrees", navigation.turnDegrees()));
        fields.put("pathTarget", navigation.route().map(r -> List.of(r.destination().x(), r.destination().y(), r.destination().z())).orElse(null));
        fields.put("pathCreatedTick", navigation.route().map(NavigationExecutor.Route::createdTick).orElse(null));
        fields.put("lastProgressTick", navigation.route().map(NavigationExecutor.Route::lastProgressTick).orElse(null));
        fields.put("pathFailures", navigation.failures());
        return encode(fields);
    }
    private static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) return map.entrySet().stream().map(e -> quote(e.getKey().toString()) + ":" + encode(e.getValue()))
            .collect(Collectors.joining(",", "{", "}"));
        if (value instanceof List<?> list) return list.stream().map(DecisionTrace::encode).collect(Collectors.joining(",", "[", "]"));
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        return quote(value.toString());
    }
    private static String quote(String s) {
        var out = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
                default -> { if (c < 32) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
}
