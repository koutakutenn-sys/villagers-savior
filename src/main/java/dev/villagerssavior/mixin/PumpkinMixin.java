package dev.villagerssavior.mixin;
import dev.villagerssavior.CreatorContext;
import dev.villagerssavior.SaviorEvents;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CarvedPumpkinBlock.class)
public abstract class PumpkinMixin {
    @WrapOperation(method = "spawnGolemInWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean savior$constructed(Level level, Entity entity, Operation<Boolean> original) {
        boolean added = original.call(level, entity);
        var player = CreatorContext.current();
        if (added && entity instanceof IronGolem golem && player != null && player.level() == level) SaviorEvents.constructed(golem, player);
        return added;
    }
}
