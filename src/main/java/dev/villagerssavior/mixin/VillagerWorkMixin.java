package dev.villagerssavior.mixin;

import dev.villagerssavior.profession.ProfessionProduction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Villagers at their own job site also produce; see ProfessionProduction#onWork for the cadence and limits. */
@Mixin(Villager.class)
public abstract class VillagerWorkMixin {
    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void savior$workProduction(ServerLevel level, CallbackInfo ci) {
        ProfessionProduction.onWork((Villager) (Object) this, level);
    }
}
