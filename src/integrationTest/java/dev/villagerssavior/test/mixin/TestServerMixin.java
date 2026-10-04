package dev.villagerssavior.test.mixin;
import dev.villagerssavior.test.IntegrationChecks;
import dev.villagerssavior.test.ClientServerAudit;
import dev.villagerssavior.test.HudLiveServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class TestServerMixin {
    @Unique private boolean savior$ran;
    @Inject(method="tickServer", at=@At("HEAD"))
    private void savior$test(BooleanSupplier time, CallbackInfo ci) {
        if (Boolean.getBoolean("savior.audit.hud")) { HudLiveServer.tick((MinecraftServer)(Object)this); return; }
        if (Boolean.getBoolean("savior.audit.client")) { ClientServerAudit.tick((MinecraftServer)(Object)this); return; }
        if (savior$ran) return;
        savior$ran=true;
        var server=(MinecraftServer)(Object)this;
        IntegrationChecks.run(server); server.halt(false);
    }
}
