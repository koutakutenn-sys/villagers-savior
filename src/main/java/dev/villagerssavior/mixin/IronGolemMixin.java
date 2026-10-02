package dev.villagerssavior.mixin;
import dev.villagerssavior.SaviorEvents;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(IronGolem.class)
public abstract class IronGolemMixin {
    @WrapMethod(method = "mobInteract")
    private InteractionResult savior$repair(Player player, InteractionHand hand, Operation<InteractionResult> original) {
        var golem = (IronGolem)(Object)this;
        float before = golem.getHealth();
        var result = original.call(player, hand);
        if (golem.getHealth() > before) SaviorEvents.repaired(golem, player);
        return result;
    }
}
