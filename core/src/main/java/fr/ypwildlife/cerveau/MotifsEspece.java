package fr.ypwildlife.cerveau;

import java.util.List;

/** Point d'extension également utilisable par les proies et les animaux neutres. */
@FunctionalInterface
public interface MotifsEspece {
  List<Proposition> proposer(
      Perception p,
      ProfilEspece profil,
      Decision avant,
      Evaluation risque,
      long avertiAu,
      boolean departFait,
      long dernierPas);
}
