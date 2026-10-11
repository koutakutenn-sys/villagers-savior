package dev.villagerssavior.mixin;

import dev.villagerssavior.profession.ProfessionPickup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Villagers accept their own profession's items; everything else keeps vanilla behaviour. */
@Mixin(Villager.class)
public abstract class ProfessionPickupMixin {
    @Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
    private void savior$professionItems(ServerLevel level, ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
        if (ProfessionPickup.accepts((Villager) (Object) this, stack)) ci.setReturnValue(true);
    }
}
