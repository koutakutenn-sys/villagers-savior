package dev.villagerssavior.mixin;

import dev.villagerssavior.NitwitInventory;
import dev.villagerssavior.NitwitRelay;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two nitwit rules, both built on the real vanilla gossip container:
 *
 * <ul>
 *   <li>A nitwit's own reputation always reads zero, so trading, the HUD and iron-golem checks can never
 *       treat a nitwit as a reputation holder, while everything stays stored underneath.</li>
 *   <li>A successful villager chat lets a nitwit pass one good word about a player it likes to the other,
 *       ordinary villager.</li>
 * </ul>
 */
@Mixin(Villager.class)
public abstract class VillagerReputationMixin {
    @Inject(method = "getPlayerReputation", at = @At("HEAD"), cancellable = true)
    private void savior$nitwitHasNoStanding(Player player, CallbackInfoReturnable<Integer> info) {
        if (NitwitInventory.isNitwit((Villager) (Object) this)) info.setReturnValue(0);
    }
    @Inject(method = "gossip", at = @At("TAIL"))
    private void savior$nitwitRelay(ServerLevel level, Villager partner, long time, CallbackInfo info) {
        NitwitRelay.onGossip(level, (Villager) (Object) this, partner, time);
    }
}
