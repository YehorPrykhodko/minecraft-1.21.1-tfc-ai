package fr.ypwildlife.cerveau;
import java.util.Map;
import static fr.ypwildlife.cerveau.Parametre.*;
public record Ressources(double satiete,double endurance,double peur){
    public Ressources {satiete=borner(satiete);endurance=borner(endurance);peur=borner(peur);}
    private static double borner(double n){return Double.isFinite(n)?Math.clamp(n,0,1):.6;}
    public static Ressources neuves(){return new Ressources(.6,1,0);}
    public Ressources avancer(Phase phase,ProfilEspece c){return new Ressources(satiete-c.n(hungerPerTick),endurance+(phase.mobile()?-c.n(staminaDrain):c.n(staminaRecovery)),peur-c.n(fearDecay));}
    public Ressources entendre(double force){return new Ressources(satiete,endurance,peur+force);}
    public static Ressources restaurer(Map<String,Double> m,boolean ancien){return new Ressources(m.getOrDefault("satiety",ancien?60.0:.6)/(ancien?100:1),m.getOrDefault("stamina",ancien?100.0:1.0)/(ancien?100:1),m.getOrDefault("fear",0.0));}
}
