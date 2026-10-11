package dev.villagerssavior.mixin;

import dev.villagerssavior.profession.ClericAid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clerics heal the wounded villagers around them; radius, duration and cooldown live in ClericAid. */
@Mixin(Villager.class)
public abstract class ClericAidMixin {
    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void savior$clericAid(ServerLevel level, CallbackInfo ci) {
        ClericAid.tick((Villager) (Object) this, level);
    }
}
