package dev.villagerssavior.test;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Raid.class)
public interface RaidAccess {
    @Accessor("numGroups") int savior$groups();
    @Accessor("groupsSpawned") void savior$groupsSpawned(int n);
    @Accessor("started") void savior$started(boolean value);
    @Accessor("postRaidTicks") void savior$postTicks(int n);
    @Accessor("raidCooldownTicks") void savior$cooldown(int n);
}
