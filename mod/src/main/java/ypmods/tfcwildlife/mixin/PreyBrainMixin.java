package ypmods.tfcwildlife.mixin;

import net.minecraft.world.entity.Mob;
import net.dries007.tfc.common.entities.prey.Prey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ypmods.tfcwildlife.minecraft.BrainOwner;
import ypmods.tfcwildlife.minecraft.WildlifeRuntime;

@Mixin(value = Prey.class, remap = false)
public abstract class PreyBrainMixin implements BrainOwner {
    @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
    private void wildlife$ownBrain(CallbackInfo ci) {
        if (WildlifeRuntime.owns((Mob)(Object)this)) ci.cancel();
    }
}
