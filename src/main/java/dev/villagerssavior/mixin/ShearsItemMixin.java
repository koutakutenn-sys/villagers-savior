package dev.villagerssavior.mixin;
import dev.villagerssavior.CreatorContext;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ShearsItem.class)
public abstract class ShearsItemMixin {
    @WrapMethod(method = "useOn")
    private InteractionResult savior$creator(UseOnContext context, Operation<InteractionResult> original) {
        CreatorContext.enter(context.getPlayer());
        try { return original.call(context); } finally { CreatorContext.exit(context.getPlayer()); }
    }
}
