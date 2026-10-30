package fr.ypwildlife.cerveau;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static fr.ypwildlife.cerveau.Parametre.*;
import static fr.ypwildlife.cerveau.Phase.*;
class ComportementTest {
    static final UUID ANIMAL=new UUID(1,1), JOUEUR=new UUID(2,2), SECOND=new UUID(3,3);
    static final Personnalite BRAVE=new Personnalite(.8,.9,.1,1,1,.9);
    static ProfilEspece profil(String nom){return ProfilEspece.defauts().get("tfc:"+nom);}
    static class Vue {
        long tick=100; double distance=10,faim=.2,peur=0,sante=1,endurance=1,hp=1,tirage=.99; boolean enfant=false,mortel=false,sortie=true;
        UUID agresseur=null,parent=null; Set<UUID> enfants=Set.of(); int joueurs=1,adultes=1;boolean chasse=false;
        boolean vu=true,entendu=false;long confirme=-1;Personnalite personne=BRAVE;
        Perception p(){var o=new Observation(JOUEUR,new Position(distance,0,0),hp,true,vu,entendu,confirme<0?tick:confirme,.3,false,true,false);
            return new Perception(tick,ANIMAL,new Position(0,0,0),enfant,sante,faim,endurance,peur,mortel,false,false,sortie,List.of(o),agresseur,parent,null,enfants,false,
                new Perception.Groupe(ANIMAL,adultes,joueurs,JOUEUR,chasse,null,"eligible",.8),personne,tirage,0);}
    }
    @Test void unSeulEngagementParTick(){var a=new Arbitre();var v=new Vue();var r=a.avancer(v.p(),profil("grizzly_bear"));v.peur=1;assertSame(r,a.avancer(v.p(),profil("grizzly_bear")));}
    @Test void refuseLeTempsInverse(){var a=new Arbitre();var v=new Vue();a.avancer(v.p(),profil("grizzly_bear"));v.tick--;assertThrows(IllegalArgumentException.class,()->a.avancer(v.p(),profil("grizzly_bear")));}
    @ParameterizedTest @ValueSource(strings={"grizzly_bear","black_bear","polar_bear","wolf","direwolf","hyena","cougar","panther","lion","sabertooth","tiger","crocodile"})
    void centVingtTicksDeCombatSansMicroRetrait(String espece){var a=new Arbitre();var v=new Vue();v.agresseur=JOUEUR;
        Decision d=a.avancer(v.p(),profil(espece)).decision();assertEquals(ATTACK,d.phase());v.agresseur=null;
        for(int i=1;i<=150;i++){v.tick++;v.distance=4+(i%2==0?.5:-.5);assertEquals(d,a.avancer(v.p(),profil(espece)).decision());}}
    @Test void refusNeChangeNiRaisonNiHorloge(){var v=new Vue();v.agresseur=JOUEUR;var a=new Arbitre();var d=a.avancer(v.p(),profil("grizzly_bear")).decision();
        v.agresseur=null;v.tick++;var r=a.avancer(v.p(),profil("grizzly_bear"));assertEquals(d,r.decision());assertTrue(r.refus().stream().anyMatch(q->q.veto().equals("combat_commitment")));}
    @Test void urgenceInterromptEtMaintientLaPanique(){var a=new Arbitre();var v=new Vue();v.agresseur=JOUEUR;a.avancer(v.p(),profil("grizzly_bear"));v.tick++;v.sante=.1;
        assertEquals(FLEE,a.avancer(v.p(),profil("grizzly_bear")).decision().phase());v.sante=1;v.peur=0;
        for(int i=0;i<90;i++){v.tick++;assertEquals(FLEE,a.avancer(v.p(),profil("grizzly_bear")).decision().phase());}}
    @Test void faimExtremeDepassePeurOrdinaire(){var a=new Arbitre();var v=new Vue();v.faim=.99;v.peur=.6;v.tirage=0;
        var r=a.avancer(v.p(),profil("grizzly_bear"));assertEquals(HUNT,r.decision().phase());assertEquals("hunger_over_ordinary_fear",r.decision().raison());}
    @Test void faimNeDepassePasDangerMortel(){var a=new Arbitre();var v=new Vue();v.faim=1;v.peur=1;v.tirage=0;v.mortel=true;assertEquals(FLEE,a.avancer(v.p(),profil("grizzly_bear")).decision().phase());}
    @Test void avertissementPuisPasPuisDefense(){var a=new Arbitre();var v=new Vue();var c=profil("grizzly_bear");
        assertEquals(WARN,a.avancer(v.p(),c).decision().phase());v.tick+=31;v.distance=8;assertEquals(DRIVE_OFF,a.avancer(v.p(),c).decision().phase());
        v.tick++;v.distance=4;assertEquals(ATTACK,a.avancer(v.p(),c).decision().phase());}
    @Test void bordureNeFaitPasOsciller(){var a=new Arbitre();var v=new Vue();var c=profil("grizzly_bear");v.distance=11.5;
        assertEquals(WARN,a.avancer(v.p(),c).decision().phase());for(int i=0;i<28;i++){v.tick++;v.distance=i%2==0?12.5:11.5;assertEquals(WARN,a.avancer(v.p(),c).decision().phase());}}
    @Test void retraiteAtteintDistancePuisNeRecommencePas(){var a=new Arbitre();var v=new Vue();var c=profil("black_bear");v.personne=new Personnalite(.1,.1,.9,1,1,.1);v.distance=23;
        assertEquals(RETREAT,a.avancer(v.p(),c).decision().phase());v.tick+=40;v.distance=34;assertEquals(ASSESS,a.avancer(v.p(),c).decision().phase());v.tick+=40;v.distance=25;assertNotEquals(RETREAT,a.avancer(v.p(),c).decision().phase());}
    @Test void murProduitAvertissement(){var a=new Arbitre();var v=new Vue();v.sortie=false;assertEquals(WARN,a.avancer(v.p(),profil("grizzly_bear")).decision().phase());}
    @ParameterizedTest @ValueSource(doubles={8,15,22})
    void protectionLitLeProfil(double rayon){var a=new Arbitre();var v=new Vue();v.enfants=Set.of(SECOND);v.distance=rayon-.1;
        var c=profil("grizzly_bear").avec(offspringDefenseRadius,rayon);assertEquals(Motif.OFFSPRING_DEFENSE,a.avancer(v.p(),c).decision().motif());
        v.distance=rayon+.1;assertNotEquals(Motif.OFFSPRING_DEFENSE,new Arbitre().avancer(v.p(),c).decision().motif());}
    @Test void profilsIndependants(){var grizzly=profil("grizzly_bear").avec(offspringDefenseRadius,8);var loup=profil("wolf").avec(offspringDefenseRadius,15);var hyene=profil("hyena").avec(offspringDefenseRadius,22);
        var v=new Vue();v.distance=12;v.enfants=Set.of(SECOND);assertNotEquals(Motif.OFFSPRING_DEFENSE,new Arbitre().avancer(v.p(),grizzly).decision().motif());assertEquals(Motif.OFFSPRING_DEFENSE,new Arbitre().avancer(v.p(),loup).decision().motif());assertEquals(Motif.OFFSPRING_DEFENSE,new Arbitre().avancer(v.p(),hyene).decision().motif());}
    @Test void enfantNeChasseJamais(){var v=new Vue();v.enfant=true;v.faim=1;v.tirage=0;v.agresseur=JOUEUR;assertEquals(FLEE,new Arbitre().avancer(v.p(),profil("wolf")).decision().phase());}
    @Test void deuxJoueursInterdisentChasseHyene(){var v=new Vue();v.joueurs=2;v.adultes=20;v.hp=.1;v.faim=1;v.chasse=true;v.tirage=0;
        assertEquals(HOLD_GROUND,new Arbitre().avancer(v.p(),profil("hyena")).decision().phase());v.adultes=1;assertFalse(new Arbitre().avancer(v.p(),profil("hyena")).decision().phase().combat());}
    @Test void secondJoueurTermineMotifChasseMaisPasDefense(){var a=new Arbitre();var v=new Vue();v.adultes=4;v.chasse=true;v.hp=.2;
        assertEquals(HUNT,a.avancer(v.p(),profil("hyena")).decision().phase());v.tick++;v.joueurs=2;assertEquals(HOLD_GROUND,a.avancer(v.p(),profil("hyena")).decision().phase());
        v.tick++;v.agresseur=JOUEUR;assertEquals(ATTACK,a.avancer(v.p(),profil("hyena")).decision().phase());v.tick++;v.agresseur=null;assertEquals(ATTACK,a.avancer(v.p(),profil("hyena")).decision().phase());}
    @Test void faimSeuleNeDeclenchePasLaMeute(){var v=new Vue();v.adultes=4;v.faim=1;v.tirage=0;assertFalse(new Arbitre().avancer(v.p(),profil("hyena")).decision().phase().combat());}
    @Test void soloRepueOuTirageRateNeChassePas(){var v=new Vue();v.tirage=0;assertFalse(new Arbitre().avancer(v.p(),profil("hyena")).decision().phase().combat());v.faim=1;v.tirage=.5;assertFalse(new Arbitre().avancer(v.p(),profil("hyena")).decision().phase().combat());v.tirage=.01;assertEquals(HUNT,new Arbitre().avancer(v.p(),profil("hyena")).decision().phase());}
    @Test void souvenirExpireSansPositionMagique(){var m=new Memoire();m.noter(new Observation(JOUEUR,new Position(10,0,0),1,true,true,false,100,0,false,false,false));assertEquals(new Position(10,0,0),m.lire(150,100).getFirst().position());assertFalse(m.lire(150,100).getFirst().visible());assertTrue(m.lire(201,100).isEmpty());}
    @Test void auditionAttenueEtVisionRespecteMur(){assertTrue(Memoire.audition(5,96,true,1)<Memoire.audition(5,96,false,1));assertEquals(0,Memoire.audition(100,96,false,1));assertFalse(Memoire.visible(new Position(0,0,0),new Position(5,0,0),1,0,false,230,32));}
    @Test void migrationConserveEchelle(){assertEquals(.603,Ressources.restaurer(Map.of("satiety",60.3,"stamina",80.0),true).satiete(),1e-9);assertEquals(.8,Ressources.restaurer(Map.of("stamina",.8),false).endurance());}
    @Test void replayDeterministe(){var a=new Arbitre();var b=new Arbitre();var v=new Vue();for(int i=0;i<500;i++){v.tick++;v.distance=12-Math.sin(i/20.0)*4;v.peur=i>300?.97:0;assertEquals(a.avancer(v.p(),profil("grizzly_bear")),b.avancer(v.p(),profil("grizzly_bear")));}}
    @ParameterizedTest @ValueSource(strings={"grizzly_bear","black_bear","polar_bear","wolf","direwolf","hyena","cougar","panther","lion","sabertooth","tiger","crocodile"})
    void configValidee(String espece){var c=profil(espece);assertThrows(IllegalArgumentException.class,()->c.avec(offspringDefenseRadius,Double.NaN));assertThrows(IllegalArgumentException.class,()->c.avec(offspringDefenseRadius,-1));assertThrows(IllegalArgumentException.class,()->c.avec(packBreakRadius,2));assertThrows(IllegalArgumentException.class,()->c.avec(retreatTargetSeparation,2));}
}
