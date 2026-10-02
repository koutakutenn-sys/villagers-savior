package dev.villagerssavior.mixin;
import dev.villagerssavior.FoodGifts;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements FoodGifts.RequestThrottle {
    @Unique private long savior$lastRequest = Long.MIN_VALUE;
    public boolean savior$acceptRequest(long now) {
        if (savior$lastRequest != Long.MIN_VALUE && now - savior$lastRequest < 20) return false;
        savior$lastRequest = now; return true;
    }
}
