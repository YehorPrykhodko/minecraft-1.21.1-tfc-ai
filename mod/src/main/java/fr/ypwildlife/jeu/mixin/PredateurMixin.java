package fr.ypwildlife.jeu.mixin;

import com.mojang.serialization.Dynamic;
import fr.ypwildlife.jeu.*;
import net.dries007.tfc.common.entities.predator.Predator;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = Predator.class, remap = false)
public abstract class PredateurMixin {
  @Inject(method = "makeBrain", at = @At("RETURN"))
  private void faune$cerveau(Dynamic<?> donnees, CallbackInfoReturnable<Brain<?>> retour) {
    if (fr.ypwildlife.jeu.Configuration.profil(
            fr.ypwildlife.jeu.MondeFaune.type((net.minecraft.world.entity.Entity) (Object) this))
        != null) CerveauTfc.configurer(retour.getReturnValue());
  }

  @Inject(method = "finalizeSpawn", at = @At("RETURN"))
  private void faune$cohorte(
      ServerLevelAccessor niveau,
      DifficultyInstance difficulte,
      MobSpawnType type,
      SpawnGroupData groupe,
      CallbackInfoReturnable<SpawnGroupData> retour) {
    Familles.naissance((Mob) (Object) this, retour.getReturnValue());
  }

  @Inject(method = "onOffspringSpawnedFromEgg", at = @At("RETURN"))
  private void faune$enfant(Player joueur, Mob enfant, CallbackInfo retour) {
    Familles.oeuf((Mob) (Object) this, enfant);
  }
}
