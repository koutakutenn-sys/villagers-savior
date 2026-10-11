package dev.villagerssavior.profession;

import dev.villagerssavior.SaviorState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import java.util.List;

/**
 * Automatic cleric aid: when a cleric notices wounded villagers around it, every one of them inside the
 * radius gains Regeneration I. It is free, heals villagers only (never the player), and one cleric can do
 * it once per cooldown.
 */
public final class ClericAid {
    public static final String EVENT = "cleric_aid";
    /** Detection radius of the wounded-villager scan, in blocks. */
    public static final double RADIUS = 16.0;
    /** Regeneration I for ten seconds. */
    public static final int DURATION = 200;
    /** Thirty seconds per cleric. */
    public static final long COOLDOWN = 600L;
    /** The scan runs at most once per second. */
    private static final int PERIOD = 20;

    private ClericAid() {}

    /** Thin gate called from Villager#customServerAiStep: sleeping villagers and non-clerics do nothing. */
    public static void tick(Villager villager, ServerLevel level) {
        if (villager.isSleeping() || !isCleric(villager)) return;
        if ((level.getGameTime() + villager.getId()) % PERIOD != 0) return;
        aid(villager, level);
    }

    /**
     * The aid itself: every other villager inside the radius that is below full health and not already
     * regenerating gains Regeneration I for {@link #DURATION} ticks. The cooldown is booked only when at
     * least one villager was actually helped, so an idle cleric never burns it.
     */
    public static void aid(Villager cleric, ServerLevel level) {
        if (!cleric.isAlive() || !isCleric(cleric)) return;
        long now = level.getGameTime();
        var state = SaviorState.get(level);
        if (!state.villagerReady(EVENT, cleric.getUUID(), now, COOLDOWN)) return;
        List<Villager> wounded = level.getEntitiesOfClass(Villager.class, cleric.getBoundingBox().inflate(RADIUS),
            other -> other != cleric && other.isAlive() && other.getHealth() < other.getMaxHealth()
                && !other.hasEffect(MobEffects.REGENERATION)
                && other.distanceToSqr(cleric) <= RADIUS * RADIUS);
        if (wounded.isEmpty()) return;
        for (Villager other : wounded) other.addEffect(new MobEffectInstance(MobEffects.REGENERATION, DURATION, 0));
        state.villagerCooldown(EVENT, cleric.getUUID(), now);
    }

    public static boolean isCleric(Villager villager) {
        return villager.getVillagerData().profession().is(VillagerProfession.CLERIC);
    }
}
