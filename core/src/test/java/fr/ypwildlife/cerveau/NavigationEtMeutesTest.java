package fr.ypwildlife.cerveau;

import static fr.ypwildlife.cerveau.Parametre.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class NavigationEtMeutesTest {
  static class Port implements Navigation.Port {
    Position p = new Position(0, 0, 0), but;
    boolean fini = true, succes = true;
    int demandes, arrets;

    public Position position() {
      return p;
    }

    public boolean termine() {
      return fini;
    }

    public boolean chemin(Position v) {
      but = v;
      demandes++;
      return succes;
    }

    public boolean demarrer(double v) {
      fini = false;
      return true;
    }

    public void arreter() {
      fini = true;
      arrets++;
    }

    public double portee() {
      return 16;
    }
  }

  static ProfilEspece ours() {
    return ProfilEspece.defauts().get("tfc:grizzly_bear");
  }

  @Test
  void fuiteReutiliseUnChemin() {
    var n = new Navigation();
    var p = new Port();
    var d = new Decision(Phase.FLEE, Motif.SURVIVAL, "panic", UUID.randomUUID(), 0, 100);
    for (int t = 0; t < 50; t++) n.avancer(t, d, ours(), false, new Position(2, 0, 0), p);
    assertEquals(1, p.demandes);
    assertTrue(p.but.x() < 0);
    assertTrue(p.but.distance(p.p) < 16);
  }

  @Test
  void echecBackoffPuisLimite() {
    var n = new Navigation();
    var p = new Port();
    p.succes = false;
    var d = new Decision(Phase.RETREAT, Motif.SURVIVAL, "avoid", UUID.randomUUID(), 0, 30);
    for (int t = 0; t < 400; t++) n.avancer(t, d, ours(), false, new Position(2, 0, 0), p);
    assertEquals(3, p.demandes);
    assertEquals(3, n.echecs());
  }

  @Test
  void driveOffArreteAvantContactEtLimitePas() {
    var n = new Navigation();
    var p = new Port();
    var d = new Decision(Phase.DRIVE_OFF, Motif.HOLD_POSITION, "warn", UUID.randomUUID(), 0, 100);
    for (int t = 0; t < 200; t++) {
      n.avancer(t, d, ours(), false, new Position(6, 0, 0), p);
      if (!p.fini) {
        p.p = p.but;
        p.fini = true;
      }
    }
    assertTrue(p.demandes <= 3);
    assertTrue(p.p.distance(new Position(6, 0, 0)) >= 3.5 - 1e-9);
    assertTrue(p.p.x() <= 4);
  }

  @Test
  void pasDeCommandePourPropositionRefusee() {
    var p = new Port();
    var n = new Navigation();
    n.avancer(
        10,
        new Decision(Phase.WARN, Motif.HOLD_POSITION, "warning", UUID.randomUUID(), 10, 40),
        ours(),
        false,
        new Position(5, 0, 0),
        p);
    assertEquals(0, p.demandes);
  }

  @Test
  void meuteVoitUnionDesJoueurs() {
    var c = ProfilEspece.defauts().get("tfc:hyena");
    var a = new UUID(0, 1);
    var b = new UUID(0, 2);
    var j = new UUID(1, 1);
    var k = new UUID(1, 2);
    var o =
        new Observation(
            j, new Position(5, 0, 0), .1, true, true, false, 10, 0, false, false, false);
    var q =
        new Observation(
            k, new Position(7, 0, 0), .1, true, true, false, 10, 0, false, false, false);
    var membres =
        List.of(
            new Meutes.Membre(a, c, new Position(0, 0, 0), false, 1, 0, 0, List.of(o), null),
            new Meutes.Membre(b, c, new Position(1, 0, 0), false, 1, 0, 0, List.of(q), null));
    var r = new Meutes().calculer(10, membres);
    assertEquals(2, r.get(a).joueurs());
    assertFalse(r.get(a).chasse());
    assertFalse(r.get(b).chasse());
  }

  @Test
  void defenseEtVetoBlessure() {
    var c = ProfilEspece.defauts().get("tfc:wolf");
    var a = new UUID(0, 1);
    var b = new UUID(0, 2);
    var j = new UUID(1, 1);
    var o =
        new Observation(j, new Position(5, 0, 0), 1, true, true, false, 10, 0, false, false, false);
    var membres =
        List.of(
            new Meutes.Membre(a, c, new Position(0, 0, 0), false, 1, 0, 0, List.of(o), j),
            new Meutes.Membre(b, c, new Position(1, 0, 0), false, .1, 0, 0, List.of(o), null));
    var r = new Meutes().calculer(10, membres);
    assertEquals(j, r.get(a).defense());
    assertNull(r.get(b).defense());
    assertEquals("injured", r.get(b).veto());
  }

  @Test
  void meuteDecisionPartageeEtPasDeReroll() {
    var c = ProfilEspece.defauts().get("tfc:hyena");
    var j = new UUID(1, 1);
    var o =
        new Observation(
            j, new Position(5, 0, 0), .1, true, true, false, 10, 0, false, false, false);
    var membres = new ArrayList<Meutes.Membre>();
    for (int i = 0; i < 4; i++)
      membres.add(
          new Meutes.Membre(
              new UUID(0, i), c, new Position(i, 0, 0), false, 1, 0, 0, List.of(o), null));
    var m = new Meutes();
    var initial = m.calculer(10, membres);
    boolean chasse = initial.values().iterator().next().chasse();
    assertTrue(initial.values().stream().allMatch(g -> g.chasse() == chasse));
    for (int t = 11; t < 100; t++)
      assertTrue(m.calculer(t, membres).values().stream().allMatch(g -> g.chasse() == chasse));
  }

  @Test
  void personnaliteStableEtDistincte() {
    var a = new UUID(1, 1);
    assertEquals(Personnalite.creer(a), Personnalite.creer(a));
    assertNotEquals(Personnalite.creer(a), Personnalite.creer(new UUID(2, 2)));
  }
}
