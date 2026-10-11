package dev.villagerssavior.mixin;

import net.minecraft.world.entity.ai.gossip.GossipContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla decays gossip once per day; the mod replaces that with one point every three days (FavorDecay). */
@Mixin(GossipContainer.class)
public abstract class GossipDecayMixin {
    @Inject(method = "decay", at = @At("HEAD"), cancellable = true)
    private void savior$slowerDecay(CallbackInfo ci) {
        ci.cancel();
    }
}
