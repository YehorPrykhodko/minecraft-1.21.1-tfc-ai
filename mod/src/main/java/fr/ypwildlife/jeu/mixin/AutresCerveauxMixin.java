package fr.ypwildlife.jeu.mixin;

import com.mojang.serialization.Dynamic;
import fr.ypwildlife.jeu.CerveauTfc;
import net.dries007.tfc.common.entities.ai.predator.PackPredator;
import net.dries007.tfc.common.entities.predator.AmphibiousPredator;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
    value = {PackPredator.class, AmphibiousPredator.class},
    remap = false)
public abstract class AutresCerveauxMixin {
  @Inject(method = "makeBrain", at = @At("RETURN"))
  private void faune$cerveau(Dynamic<?> donnees, CallbackInfoReturnable<Brain<?>> retour) {
    if (fr.ypwildlife.jeu.Configuration.profil(
            fr.ypwildlife.jeu.MondeFaune.type((net.minecraft.world.entity.Entity) (Object) this))
        != null) CerveauTfc.configurer(retour.getReturnValue());
  }
}
