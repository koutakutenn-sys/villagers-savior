package dev.villagerssavior.mixin;

import dev.villagerssavior.NitwitInventory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class NitwitPickupMixin {
    @Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
    private void savior$acceptAnyItem(ServerLevel level, ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
        var villager = (Villager) (Object) this;
        if (NitwitInventory.isNitwit(villager))
            ci.setReturnValue(!stack.isEmpty() && villager.getInventory().canAddItem(stack));
    }

    @Inject(method = "pickUpItem", at = @At("HEAD"), cancellable = true)
    private void savior$respectItemRestrictions(ServerLevel level, ItemEntity item, CallbackInfo ci) {
        var villager = (Villager) (Object) this;
        if (!NitwitInventory.isNitwit(villager)) return;
        var target = ((ItemEntityAccess) item).savior$pickupTarget();
        if (item.hasPickUpDelay() || (target != null && !target.equals(villager.getUUID()))) ci.cancel();
    }
}
