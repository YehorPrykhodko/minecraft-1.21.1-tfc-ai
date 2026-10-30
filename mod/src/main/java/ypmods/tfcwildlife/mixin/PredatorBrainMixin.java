package ypmods.tfcwildlife.mixin;

import net.minecraft.world.entity.Mob;
import net.dries007.tfc.common.entities.predator.Predator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ypmods.tfcwildlife.minecraft.BrainOwner;
import ypmods.tfcwildlife.minecraft.WildlifeRuntime;

@Mixin(value = Predator.class, remap = false)
public abstract class PredatorBrainMixin implements BrainOwner {
    @Shadow public abstract void setSleeping(boolean asleep);
    @Override public void wildlife$wake() { setSleeping(false); }

    @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
    private void wildlife$ownBrain(CallbackInfo ci) {
        if (WildlifeRuntime.owns((Mob)(Object)this)) ci.cancel();
    }
}
