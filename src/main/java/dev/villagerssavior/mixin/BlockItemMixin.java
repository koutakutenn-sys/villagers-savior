package dev.villagerssavior.mixin;
import dev.villagerssavior.CreatorContext;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionResult;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @WrapMethod(method = "place")
    private InteractionResult savior$creator(BlockPlaceContext context, Operation<InteractionResult> original) {
        CreatorContext.enter(context.getPlayer());
        try { return original.call(context); } finally { CreatorContext.exit(context.getPlayer()); }
    }
}
