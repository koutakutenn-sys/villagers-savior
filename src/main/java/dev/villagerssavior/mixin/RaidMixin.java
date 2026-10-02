package dev.villagerssavior.mixin;
import dev.villagerssavior.SaviorEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import java.util.Set;
import java.util.UUID;

@Mixin(Raid.class)
public abstract class RaidMixin {
    @Shadow @Final private Set<UUID> heroesOfTheVillage;
    @WrapMethod(method = "tick")
    private void savior$victory(ServerLevel level, Operation<Void> original) {
        var raid = (Raid)(Object)this;
        boolean won = raid.isVictory();
        original.call(level);
        if (!won && raid.isVictory()) SaviorEvents.raidWon(level, raid.getCenter(), heroesOfTheVillage);
    }
}
