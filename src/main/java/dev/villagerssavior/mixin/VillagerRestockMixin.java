package dev.villagerssavior.mixin;

import dev.villagerssavior.profession.ProfessionProduction;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A successful vanilla workstation restock ("补货") also adds a little of the profession's own stock, bounded
 * by that resource's per-restock, daily, target and hard limits. Everything else about restocking is untouched.
 */
@Mixin(Villager.class)
public abstract class VillagerRestockMixin {
    @Inject(method = "restock", at = @At("TAIL"))
    private void savior$professionProduction(CallbackInfo ci) {
        ProfessionProduction.onRestock((Villager) (Object) this);
    }
}
