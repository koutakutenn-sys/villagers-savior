package dev.villagerssavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class SaviorEvents {
    private SaviorEvents() {}
    public static List<Villager> villagers(ServerLevel level, Vec3 center, int radius) {
        return level.getEntitiesOfClass(Villager.class, new AABB(center, center).inflate(radius),
            v -> v.isAlive() && v.position().distanceToSqr(center) <= (double) radius * radius);
    }
    /**
     * Drops a villager's whole real hidden inventory and clears it. Vanilla villagers do not drop their
     * inventory at all, but the mod's profession stock lives in there, so death must hand it back to the world.
     * No distinction is made between items picked up through vanilla behaviour and items this mod produced.
     */
    public static void dropVillagerInventory(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        var inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) Block.popResource(level, villager.blockPosition(), stack);
        }
        inventory.clearContent();
    }
    public static int threat(Entity entity) {
        var type = entity.getType();
        if (type == EntityTypes.RAVAGER || type == EntityTypes.WARDEN || type == EntityTypes.WITHER || type == EntityTypes.ENDER_DRAGON) return 3;
        if (type == EntityTypes.CREEPER || type == EntityTypes.ENDERMAN || type == EntityTypes.EVOKER || type == EntityTypes.VINDICATOR || type == EntityTypes.PIGLIN_BRUTE) return 2;
        return 1;
    }
    public static void death(LivingEntity entity, DamageSource source) {
        // Real hidden inventory drops for every villager death, regardless of what (or who) killed it.
        if (entity instanceof Villager villager) dropVillagerInventory(villager);
        if (!(entity.level() instanceof ServerLevel level) || !(source.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        long now = level.getGameTime();
        var state = SaviorState.get(level);
        if (entity instanceof IronGolem golem) {
            if (golem.isPlayerCreated()) {
                // Player-built golems skip the village punishment ladder, but killing one still costs
                // the villagers standing nearby. Vanilla only exposes isPlayerCreated(), so this covers
                // any player-built golem killed by a player rather than strictly the builder's own.
                for (var v : villagers(level, golem.position(), 32))
                    SaviorGossip.subtract(v, id, GossipType.MINOR_POSITIVE, 5);
                return;
            }
            if (!level.isVillage(golem.blockPosition())) return;
            var village = Villages.identify(level, golem.blockPosition());
            if (village.isEmpty()) return;
            for (var v : villagers(level, golem.position(), 32)) {
                int n = state.golemKill(id, v.getUUID(), now);
                SaviorGossip.subtract(v, id, GossipType.MINOR_POSITIVE, 10);
                SaviorGossip.add(v, id, GossipType.MINOR_NEGATIVE, (int) Math.min(200L, 10L + 5L * (n - 1)));
                if (n > 5) SaviorGossip.add(v, id, GossipType.MAJOR_NEGATIVE, 1);
            }
        } else if (entity instanceof Enemy) {
            for (var v : villagers(level, entity.position(), 24)) {
                int grant = state.dailyGrant("kill", id, v.getUUID(), now, threat(entity), 5);
                SaviorGossip.add(v, id, GossipType.MINOR_POSITIVE, grant);
            }
        }
    }
    public static void repaired(IronGolem golem, Player player) {
        if (!(golem.level() instanceof ServerLevel level) || !(player instanceof ServerPlayer)) return;
        var state = SaviorState.get(level);
        for (var v : villagers(level, golem.position(), 32)) {
            int grant = state.dailyGrant("repair", player.getUUID(), v.getUUID(), level.getGameTime(), 1, 3);
            SaviorGossip.add(v, player.getUUID(), GossipType.MINOR_POSITIVE, grant);
        }
    }
    public static void constructed(IronGolem golem, Player player) {
        if (!(golem.level() instanceof ServerLevel level) || !(player instanceof ServerPlayer) || !golem.isPlayerCreated()) return;
        for (var v : villagers(level, golem.position(), 32)) {
            int count = level.getEntitiesOfClass(IronGolem.class, v.getBoundingBox().inflate(64),
                g -> g.isAlive() && g.distanceToSqr(v) <= 64.0 * 64).size();
            if (count <= 5) SaviorGossip.add(v, player.getUUID(), GossipType.MINOR_POSITIVE, 5);
        }
    }
    public static void raidWon(ServerLevel level, net.minecraft.core.BlockPos center, Set<UUID> participants) {
        var village = Villages.identify(level, center);
        if (village.isEmpty()) return;
        var state = SaviorState.get(level);
        for (UUID id : participants) {
            if (!state.ready("raid", id, village.get(), level.getGameTime())) continue;
            var nearby = villagers(level, Vec3.atCenterOf(center), 64);
            if (nearby.isEmpty()) continue;
            for (var v : nearby) SaviorGossip.add(v, id, GossipType.MAJOR_POSITIVE, 2);
            state.cooldown("raid", id, village.get(), level.getGameTime());
        }
    }
}
