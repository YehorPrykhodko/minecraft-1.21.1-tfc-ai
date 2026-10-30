package fr.ypwildlife.cerveau;
import java.util.*;
import static fr.ypwildlife.cerveau.Parametre.*;
/** Un profil produit des motifs; il ne possède jamais l'état engagé. */
public record ProfilEspece(String id, Famille famille, Map<Parametre,Double> valeurs, String son,
                           boolean avertissementAvecLos, boolean attaqueConfirmee) {
    public enum Famille { OURS, CANIDE, HYENE, FELIN, AMPHIBIE, PROIE, NEUTRE }
    public ProfilEspece {
        valeurs=Map.copyOf(valeurs);
        for(var p:Parametre.values()) p.verifier(valeurs.getOrDefault(p,p.defaut));
        if(valeurs.getOrDefault(packBreakRadius,30.0)<valeurs.getOrDefault(packJoinRadius,20.0)) throw new IllegalArgumentException("packBreakRadius < packJoinRadius");
        if(valeurs.getOrDefault(retreatTargetSeparation,28.0)<valeurs.getOrDefault(earlyAvoidanceRadius,24.0)) throw new IllegalArgumentException("retreatTargetSeparation < earlyAvoidanceRadius");
        if(valeurs.getOrDefault(extremeHungerThreshold,0.94)<valeurs.getOrDefault(hungerHuntThreshold,0.68)) throw new IllegalArgumentException("extremeHungerThreshold < hungerHuntThreshold");
        if(valeurs.getOrDefault(driveOffMaxAdvance,4.0)<valeurs.getOrDefault(driveOffStepLength,1.5)) throw new IllegalArgumentException("driveOffMaxAdvance < driveOffStepLength");
    }
    public double n(Parametre p){ return valeurs.getOrDefault(p,p.defaut); }
    public int i(Parametre p){return (int)n(p);}
    public boolean social(){return famille==Famille.CANIDE||famille==Famille.HYENE;}
    public ProfilEspece avec(Parametre p,double v){var m=new EnumMap<Parametre,Double>(Parametre.class);m.putAll(valeurs);m.put(p,v);return new ProfilEspece(id,famille,m,son,avertissementAvecLos,attaqueConfirmee);}
    public static Map<String,ProfilEspece> defauts(){
        var m=new LinkedHashMap<String,ProfilEspece>();
        ajouter(m,"grizzly_bear",Famille.OURS,"bear",12,24,.65,.7,.35,2);
        ajouter(m,"black_bear",Famille.OURS,"bear",10,26,.35,.4,.8,2);
        ajouter(m,"polar_bear",Famille.OURS,"bear",14,25,.75,.8,.25,2);
        ajouter(m,"wolf",Famille.CANIDE,"tfc_wolf",11,20,.5,.5,.55,1.2);
        ajouter(m,"direwolf",Famille.CANIDE,"tfc_wolf",13,22,.7,.75,.35,1.2);
        ajouter(m,"hyena",Famille.HYENE,"hyena",10,22,.45,.45,.65,1.15);
        for(var nom:List.of("cougar","panther","lion","sabertooth","tiger"))
            ajouter(m,nom,Famille.FELIN,nom.equals("panther")?"cougar":nom,nom.equals("lion")?12:9,23,.55,.55,.6,1.5);
        ajouter(m,"crocodile",Famille.AMPHIBIE,"crocodile",8,16,.7,.7,.25,1.2);
        return Collections.unmodifiableMap(m);
    }
    private static void ajouter(Map<String,ProfilEspece> m,String nom,Famille f,String son,double warn,double avoid,double agg,double cour,double prud,double speed){
        var v=new EnumMap<Parametre,Double>(Parametre.class);for(var p:Parametre.values())v.put(p,p.defaut);
        v.put(warningRadius,warn);v.put(earlyAvoidanceRadius,avoid);v.put(retreatTargetSeparation,avoid+5);
        v.put(driveOffStartRadius,warn-2);v.put(aggression,agg);v.put(courage,cour);v.put(caution,prud);v.put(chaseSpeed,speed);
        if(f==Famille.FELIN||f==Famille.AMPHIBIE)v.put(driveOffMaxSteps,0.0);
        if(f==Famille.CANIDE){v.put(walkSpeed,1.2);v.put(fleeSpeed,1.44);v.put(driveOffStepLength,1.0);}
        m.put("tfc:"+nom,new ProfilEspece("tfc:"+nom,f,v,"tfc:entity."+son+".attack",true,true));
    }
}
