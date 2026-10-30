package fr.ypwildlife.cerveau;
/** Valeurs initiales proposées, jamais des constantes dans les règles de décision. */
public enum Parametre {
    visionDetectionRadius(32,4,128), visionFovDegrees(230,30,360), visionMemoryTicks(160,10,2400),
    hearingStepRadius(12,1,256), hearingSprintRadius(22,1,256), hearingLandingRadius(26,1,256), hearingGunshotRadius(96,1,256),
    earlyAvoidanceRadius(24,1,128), retreatTargetSeparation(28,1,160), retreatStepLength(8,1,32), retreatMaxAttempts(3,1,12), retreatCooldownTicks(60,10,1200),
    warningRadius(12,1,128), driveOffStartRadius(10,1,128), driveOffStepLength(1.5,0.25,8), driveOffMaxAdvance(4,0.25,24), driveOffStopDistance(3.5,2,12),
    driveOffMaxSteps(3,0,12), driveOffCooldownTicks(180,20,2400), driveOffSpeed(0.65,0.1,2), driveOffPauseTicks(20,10,200), driveOffDurationTicks(100,20,600),
    aggressionRadius(5,1,64), offspringDefenseRadius(15,1,128), warningGrowlCooldownTicks(80,20,1200), warningMaxGrowlsPerEncounter(2,0,20), driveOffGrowlCooldownTicks(60,20,1200),
    hungerHuntThreshold(0.68,0,1), extremeHungerThreshold(0.94,0,1), fearOverrideThreshold(0.9,0.1,1), combatHoldTicks(120,100,1200),
    packJoinRadius(20,2,128), packAssistRadius(24,2,128), packBreakRadius(30,2,160), packBreakDelayTicks(80,10,600),
    aggression(0.6,0,1), courage(0.65,0,1), caution(0.4,0,1), positionDefensiveness(0.75,0,1),
    huntChance(0.55,0,1), soloHuntChance(0.08,0,0.3), playerHpHuntThreshold(0.7,0.1,1), packConfidenceWeight(0.35,0,2),
    criticalHp(0.22,0.01,0.5), criticalStamina(0.08,0,0.3), panicHoldTicks(100,20,1200), panicRecoveryTicks(160,20,2400),
    zoneHysteresis(1.5,0.5,8), minimumPhaseTicks(30,10,200), assessmentTicks(20,5,200), encounterCooldownTicks(600,100,12000),
    chaseSpeed(2,0.1,3), fleeSpeed(1.25,0.1,3), walkSpeed(1,0.1,2), cubSpeed(0.85,0.1,1.5), cubFleeSpeed(1.105,0.1,2),
    fearDecay(0.003,0.0001,0.1), hungerPerTick(0.00001,0,0.01), staminaDrain(0.0006,0,0.1), staminaRecovery(0.001,0,0.1);
    public final double defaut, min, max;
    Parametre(double d,double min,double max){this.defaut=d;this.min=min;this.max=max;}
    public void verifier(double n){ if(!Double.isFinite(n)||n<min||n>max) throw new IllegalArgumentException(name()+" hors limites: "+n); }
}
