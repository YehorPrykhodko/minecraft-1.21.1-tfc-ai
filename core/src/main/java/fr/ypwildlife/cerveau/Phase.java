package fr.ypwildlife.cerveau;
public enum Phase {
    SLEEP, IDLE, OBSERVE, ASSESS, WARN, HOLD_GROUND, DRIVE_OFF, RETREAT, FLEE, TRACK, HUNT, ATTACK, REST, FOLLOW_PARENT;
    public boolean combat() { return this == ATTACK || this == HUNT; }
    public boolean mobile() { return combat() || this == RETREAT || this == FLEE || this == TRACK || this == DRIVE_OFF || this == FOLLOW_PARENT; }
}
