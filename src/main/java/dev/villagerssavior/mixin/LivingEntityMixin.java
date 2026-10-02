package dev.villagerssavior.mixin;
import dev.villagerssavior.SaviorEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Unique private boolean savior$deathRecorded;
    @Inject(method = "die", at = @At("HEAD"))
    private void savior$death(DamageSource source, CallbackInfo ci) {
        if (!savior$deathRecorded) { savior$deathRecorded = true; SaviorEvents.death((LivingEntity)(Object)this, source); }
    }
}
