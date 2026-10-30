package fr.ypwildlife.jeu;

import fr.ypwildlife.cerveau.*;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class PortNavigation implements Navigation.Port {
  private final PathfinderMob animal;
  private Path chemin;
  private final double mouvement;

  public PortNavigation(PathfinderMob a) {
    this(a, 1);
  }

  public PortNavigation(PathfinderMob a, double mouvement) {
    animal = a;
    this.mouvement = mouvement;
  }

  public static Position position(Vec3 v) {
    return new Position(v.x, v.y, v.z);
  }

  public Position position() {
    return position(animal.position());
  }

  public boolean termine() {
    return animal.getNavigation().isDone();
  }

  public boolean chemin(Position p) {
    chemin = animal.getNavigation().createPath(p.x(), p.y(), p.z(), 0);
    return chemin != null && chemin.canReach();
  }

  public boolean demarrer(double vitesse) {
    return animal.getNavigation().moveTo(chemin, vitesse * mouvement);
  }

  public void arreter() {
    animal.getNavigation().stop();
  }

  public double portee() {
    return animal.getAttributeValue(Attributes.FOLLOW_RANGE);
  }
}
