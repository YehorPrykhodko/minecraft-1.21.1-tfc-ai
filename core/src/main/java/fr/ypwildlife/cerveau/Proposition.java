package fr.ypwildlife.cerveau;
import java.util.UUID;
public record Proposition(Phase phase, Motif motif, String raison, String source, UUID cible, double poids, boolean urgence) {}
