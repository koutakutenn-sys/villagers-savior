package dev.villagerssavior.mixin;
import dev.villagerssavior.FoodRequest;
import dev.villagerssavior.client.SaviorClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class ClientInteractionMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void savior$request(Player player, Entity entity, EntityHitResult hit, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        // V is the mod's request key: with an item it asks for the held-item service (repairs). The separate
        // service key (default B, rebindable) asks for the profession service directly and never feeds.
        if (entity instanceof Villager && (SaviorClient.requesting() || SaviorClient.requestingService())) {
            ClientPlayNetworking.send(new FoodRequest(entity.getId(), hand, SaviorClient.requestingService()));
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
