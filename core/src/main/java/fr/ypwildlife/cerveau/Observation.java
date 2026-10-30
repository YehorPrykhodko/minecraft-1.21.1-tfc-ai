package fr.ypwildlife.cerveau;
import java.util.UUID;
/** Position mémorisée, jamais remplacée par la position actuelle d'une cible invisible. */
public record Observation(UUID id, Position position, double sante, boolean joueur, boolean visible, boolean entendu,
                          long confirmeAu, double rapprochement, boolean sprint, boolean regarde, boolean proie) {
    public boolean recente(long tick,int ttl){return tick-confirmeAu<=ttl;}
    public Observation souvenir(){return new Observation(id,position,sante,joueur,false,entendu,confirmeAu,0,false,regarde,proie);}
}
