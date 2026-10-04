package dev.villagerssavior.mixin;

import dev.villagerssavior.SaviorEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerDeathMixin {
    @Unique private boolean savior$nitwitPenaltyRecorded;

    @Inject(method = "tellWitnessesThatIWasMurdered", at = @At("RETURN"))
    private void savior$nitwitPenalty(Entity killer, CallbackInfo ci) {
        if (savior$nitwitPenaltyRecorded) return;
        savior$nitwitPenaltyRecorded = true;
        SaviorEvents.nitwitKilled((Villager) (Object) this, killer);
    }
}
