> Исторический материал v2/v3. Текущее задание — docs/V4_SPEC_RU.md; актуальные выводы — docs/GAP_ANALYSIS.md и docs/VALIDATION_RU.md.

# Target architecture (design proposal, not recovered source)

```
Tick event (server only)
  → PerceptionSnapshot (immutable, one snapshot per decision cycle)
    → Sensory events + short-term memory
    → SpeciesRule(s): list of CandidateIntent, NO mutations
    → PriorityResolver: safety rules + hysteresis + encounter commitment
       (one CommittedDecision per animal per tick)
    → NavigationExecutor: owner of path/target/movement mode
    → Observability: structured DecisionTrace + optional detailed samples
```

## Required distinctions

- **Observation**: fact known about environment (`player sprinting`, distance, own HP, heard gunshot, line of sight).
- **Pressure/score**: scalar indicating risk or motivation, not an order by itself.
- **Candidate**: optional suggestion `{intent, reason, urgency, expiresTick, source}`.
- **CommittedDecision**: authoritative `{intent, reason, lockedUntil, targetEntity, navigationPolicy}`.
- **Execution**: movement goals, path and speed; must be free of decision logic except reachability/stuck status reporting.

## Priority rules

1. Immediate critical threat or injury / verified panic may interrupt a chase.
2. Committed combat is not interrupted by routine proximity, mild sounds or re-detection.
3. Pack-hunt commitment is shared among eligible pack members while participation remains safe.
4. Cautious retreat and panic flee are distinct phases; a brief retreat must not be disguised as true fear.
5. No proposal is allowed to mutate the active reason when vetoed; veto trace is independent.
6. A species-specific rule supplies *candidates*, not direct writes to state.
7. Avoid hardcoded permanent immunity: serious risk can always supersede combat commitments.

## Navigation

- Track **current destination**, **path creation tick**, **threat anchor**, **intent owner**, **last progress tick**, and **failure count**.
- Repath when target reached, stuck, path invalid, obstacle detected, or threat direction changed significantly; not simply because `tick()` called.
- Reusing destination should apply to both `RETREAT` and `FLEE` and take threat movement into account.
- Avoid running two goals / navigation controllers that overwrite each other; audit vanilla TFC goals and injected mixins.
- Emit direction changes >90° (or configurable threshold) with cause and old/new destinations.

## Diagnostic output

Suggested JSONL events to a separate optional file (or concise readable log):

```
{ "type":"decision", "tick":508071, "mobUuid":"...", "species":"tfc:grizzly_bear", "current":"DEFEND", "candidates":[{"source":"danger","intent":"RETREAT","reason":"human_too_close","accepted":false,"veto":"combat_lock"}], "committed":"ATTACK", "reason":"grizzly_hold_defensive_engagement", "ownHPFraction":1.0, "playerHPFraction":0.99, "fear":0.0 }
```

This line is a schema **example**, not proof the old system already wrote this exact data. Privacy: omit user UUID, token and username from exported traces where possible.

## Dependency boundaries

- `core/` is pure Java and unit-testable without Minecraft.
- `minecraft/` adapters map server events and entity metadata into core snapshots.
- `tfc/` contains TFC compatibility interfaces/goals, and `tacz/` sound integration.
- `debug/` receives trace objects from core, may be disabled or filtered by entity.
- `config/` stores tunable thresholds with safe defaults.
