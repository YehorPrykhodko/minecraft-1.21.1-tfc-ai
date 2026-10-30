package fr.ypwildlife.jeu.mixin;

import fr.ypwildlife.cerveau.Parametre;
import fr.ypwildlife.jeu.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** Le contrôleur conserve sa physique; seul son angle maximal est borné par le profil. */
@Mixin(MoveControl.class)
public abstract class RotationMixin {
  @Shadow @Final protected Mob mob;

  @ModifyVariable(method = "rotlerp", at = @At("HEAD"), argsOnly = true, ordinal = 2)
  private float faune$rotation(float maximum) {
    return MondeFaune.gere(mob)
        ? Math.min(
            maximum,
            (float) Configuration.profil(MondeFaune.type(mob)).n(Parametre.turnDegreesPerTick))
        : maximum;
  }
}
