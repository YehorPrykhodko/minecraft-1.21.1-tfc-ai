package ypmods.tfcwildlife.core;

public enum Intent {
    IDLE, ASSESS, WARN, RETREAT, FLEE, ATTACK, HUNT, AMBUSH, REST, FOLLOW;

    public boolean combat() { return this == ATTACK || this == HUNT; }
    public boolean escape() { return this == RETREAT || this == FLEE; }
    public boolean moving() { return combat() || escape() || this == FOLLOW || this == IDLE; }
}
