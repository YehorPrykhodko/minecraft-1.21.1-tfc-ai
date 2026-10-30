package fr.ypwildlife.cerveau;
import static fr.ypwildlife.cerveau.Parametre.*;
public record Evaluation(boolean critique, boolean panique, boolean faimExtreme, double risque, double confiance, double desirChasse) {
    public static Evaluation calculer(Perception p,ProfilEspece c){
        boolean critique=p.mortel()||p.sante()<c.n(criticalHp)||p.endurance()<c.n(criticalStamina);
        boolean extreme=p.faim()>=c.n(extremeHungerThreshold);
        double confiance=(c.n(courage)+p.personnalite().courage())/2 + Math.min(1,(p.groupe().adultes()-1)/3.0)*c.n(packConfidenceWeight);
        double risque=p.peur()+Math.max(0,p.groupe().joueurs()-1)*.2+(1-p.sante())*.3;
        boolean panique=p.peur()>=c.n(fearOverrideThreshold) || (!extreme&&p.peur()>.65&&risque>confiance+.25);
        double desir=p.faim()*.75+(c.n(aggression)+p.personnalite().aggression())*.125+confiance*.1-(extreme?0:risque*.35);
        return new Evaluation(critique,panique,extreme,risque,confiance,desir);
    }
}
