package dev.villagerssavior.mixin;
import dev.villagerssavior.CreatorContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PumpkinBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Carving a pumpkin with shears swaps the block for a carved pumpkin in place, which can already
 * complete an iron golem pattern. That spawn happens inside this call rather than inside
 * BlockItem.place, so the creator must be published here for the golem to earn its reputation.
 */
@Mixin(PumpkinBlock.class)
public abstract class PumpkinBlockMixin {
    @WrapMethod(method = "useItemOn")
    private InteractionResult savior$carve(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hit,
                                           Operation<InteractionResult> original) {
        if (!stack.is(Items.SHEARS)) return original.call(stack, state, level, pos, player, hand, hit);
        CreatorContext.enter(player);
        try {
            return original.call(stack, state, level, pos, player, hand, hit);
        } finally {
            CreatorContext.exit(player);
        }
    }
}
