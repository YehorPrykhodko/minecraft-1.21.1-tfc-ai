package fr.ypwildlife.cerveau;
import java.util.*;
import static fr.ypwildlife.cerveau.Parametre.*;
/** Commandes de déplacement exclusives; aucune écriture dans l'arbitre. */
public final class Navigation {
    public interface Port {
        Position position(); boolean termine(); boolean chemin(Position but); boolean demarrer(double vitesse); void arreter(); double portee();
    }
    public record Bilan(String action,Position but,boolean cree,boolean accepte,int echecs,int pas,double avance){}
    private Phase phase=Phase.IDLE;
    private Position but,progres,ancre;
    private long demande=-100000,dernierProgres,prochainPas;
    private int echecs,pas;
    private double avance;
    public int echecs(){return echecs;}
    public Bilan avancer(long tick,Decision d,ProfilEspece c,boolean enfant,Position cible,Port port){
        if(phase!=d.phase()){
            if(but!=null||!port.termine())port.arreter();but=null;ancre=null;progres=port.position();dernierProgres=tick;
            phase=d.phase();echecs=0;pas=0;avance=0;prochainPas=tick;demande=tick-10000;
        }
        if(!phase.mobile()||cible==null){if(but!=null){port.arreter();but=null;return bilan("stopped",false,false);}return bilan("hold",false,false);}
        double distance=port.position().distance(cible);
        if((phase==Phase.RETREAT&&distance>=c.n(retreatTargetSeparation))||(phase==Phase.DRIVE_OFF&&distance<=c.n(driveOffStopDistance))
            ||(phase==Phase.FOLLOW_PARENT&&distance<=4)){
            if(but!=null)port.arreter();but=null;return bilan("separation_reached",false,false);
        }
        if(phase==Phase.DRIVE_OFF&&(tick>=d.verrou()||pas>=c.i(driveOffMaxSteps)||avance>=c.n(driveOffMaxAdvance))&&port.termine())return bilan("drive_limit",false,false);
        if(progres==null||port.position().distance(progres)>.4){progres=port.position();dernierProgres=tick;}
        boolean bloque=but!=null&&tick-dernierProgres>60;
        boolean cibleBougee=ancre!=null&&ancre.distance(cible)>4&&tick-demande>=20;
        if(but!=null&&!port.termine()&&!bloque&&!cibleBougee)return bilan("reuse",false,true);
        if(but!=null&&port.termine()&&phase==Phase.DRIVE_OFF){but=null;prochainPas=tick+c.i(driveOffPauseTicks);return bilan("drive_pause",false,false);}
        if(tick<prochainPas||tick-demande<c.i(retreatCooldownTicks)&&echecs>0)return bilan("backoff",false,false);
        if(echecs>=c.i(retreatMaxAttempts))return bilan("attempt_limit",false,false);
        if(bloque){port.arreter();but=null;dernierProgres=tick;echecs++;}
        double longueur=Math.min(c.n(retreatStepLength),Math.max(2,port.portee()*.7));
        Position demandeBut;
        if(phase==Phase.FLEE||phase==Phase.RETREAT){
            var p=port.position();double dx=p.x()-cible.x(),dz=p.z()-cible.z(),norme=Math.hypot(dx,dz);if(norme<.1){dx=1;dz=0;norme=1;}
            double angle=(echecs%3==1?Math.PI/4:echecs%3==2?-Math.PI/4:0);
            demandeBut=new Position(p.x()+longueur*(dx*Math.cos(angle)-dz*Math.sin(angle))/norme,p.y(),p.z()+longueur*(dx*Math.sin(angle)+dz*Math.cos(angle))/norme);
        }else if(phase==Phase.DRIVE_OFF){
            longueur=Math.min(c.n(driveOffStepLength),Math.min(c.n(driveOffMaxAdvance)-avance,distance-c.n(driveOffStopDistance)));
            demandeBut=port.position().vers(cible,Math.max(0,longueur));
        }else demandeBut=port.position().vers(cible,Math.min(longueur,distance));
        demande=tick;ancre=cible;
        boolean cree=port.chemin(demandeBut);
        double vitesse=phase==Phase.DRIVE_OFF?c.n(driveOffSpeed):phase==Phase.FLEE?c.n(enfant?cubFleeSpeed:fleeSpeed):phase.combat()?c.n(chaseSpeed):phase==Phase.TRACK?.65:c.n(walkSpeed);
        if(enfant&&phase!=Phase.FLEE)vitesse*=c.n(cubSpeed);
        boolean accepte=cree&&port.demarrer(vitesse);
        if(accepte){but=demandeBut;dernierProgres=tick;if(phase==Phase.DRIVE_OFF){pas++;avance+=longueur;}}
        else {echecs++;but=null;port.arreter();}
        return bilan(accepte?"move_accepted":cree?"move_rejected":"path_failed",cree,accepte);
    }
    private Bilan bilan(String a,boolean c,boolean ok){return new Bilan(a,but,c,ok,echecs,pas,avance);}
}
