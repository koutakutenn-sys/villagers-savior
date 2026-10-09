package dev.villagerssavior;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import dev.villagerssavior.debug.ReputationScan;
import dev.villagerssavior.mixin.InspectionLevelAccess;
import dev.villagerssavior.mixin.InspectionEntityManagerAccess;
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
    /**
     * True for a vanilla leader zombie: the spawn-time leader bonus on its reinforcement chance attribute.
     * Vanilla rolls {@code Zombie.ZOMBIE_LEADER_CHANCE} (0.05) at spawn and adds the
     * {@code minecraft:leader_zombie_bonus} modifier to that attribute and to max health.
     */
    public static boolean isLeader(Zombie zombie) {
        var attribute = zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
        return attribute != null && attribute.hasModifier(Identifier.withDefaultNamespace("leader_zombie_bonus"));
    }
    public static int threat(Entity entity) {
        var type = entity.getType();
        if (type == EntityTypes.RAVAGER || type == EntityTypes.WARDEN || type == EntityTypes.WITHER || type == EntityTypes.ENDER_DRAGON) return 3;
        if (type == EntityTypes.CREEPER || type == EntityTypes.ENDERMAN || type == EntityTypes.EVOKER || type == EntityTypes.VINDICATOR || type == EntityTypes.PIGLIN_BRUTE) return 2;
        return 1;
    }
    /** Keeps vanilla witness gossip, then adds the Nitwit penalty to all living loaded village residents. */
    public static void nitwitKilled(Villager victim, Entity killer) {
        if (!(victim.level() instanceof ServerLevel level) || !(killer instanceof ServerPlayer player)
            || !victim.getVillagerData().profession().is(VillagerProfession.NITWIT)) return;
        final Optional<ReputationScan.Region> village;
        try { village = ReputationScan.village(level, victim.blockPosition()); }
        catch (IllegalArgumentException tooLarge) { return; }
        if (village.isEmpty()) return;
        // Roll once per death, so every resident records the same additional minor penalty.
        int minor = 10 + victim.getRandom().nextInt(11);
        var sections = ((InspectionEntityManagerAccess) ((InspectionLevelAccess) level).savior$entityManager()).savior$sections();
        for (var chunk : village.get().chunks()) sections.getExistingSectionsInChunk(chunk.pack())
            .flatMap(section -> section.getEntities()).forEach(entity -> {
                if (!(entity instanceof Villager resident) || resident == victim || !resident.isAlive()
                    || !village.get().contains(resident.position())) return;
                SaviorGossip.add(resident, player.getUUID(), GossipType.MAJOR_NEGATIVE, 1);
                SaviorGossip.add(resident, player.getUUID(), GossipType.MINOR_NEGATIVE, minor);
            });
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
            int weight = threat(entity);
            Vec3 death = entity.position();
            var nearest = level.getPoiManager().findClosest(t -> t.is(PoiTypeTags.VILLAGE), entity.blockPosition(),
                256, PoiManager.Occupancy.ANY);
            var positions = nearest.flatMap(poi -> Villages.positions(level, poi));
            // A kill inside a village's reach (centre within 128 blocks) is heard by the whole village, and the
            // villagers right next to it hear it twice as strongly. Outside every village the 24 block sphere
            // still applies, so wilderness kills keep working exactly as before.
            if (positions.flatMap(Villages::center).map(center -> center.distanceToSqr(death) <= 128.0 * 128.0).orElse(false)) {
                var village = positions.orElseThrow();
                for (var v : Villages.residents(level, village, 32)) {
                    if (!Villages.member(level, village, v.blockPosition())) continue;
                    int applied = death.distanceToSqr(v.position()) <= 12.0 * 12.0 ? weight * 2 : weight;
                    int grant = state.dailyGrant("kill", id, v.getUUID(), now, applied, 5);
                    SaviorGossip.add(v, id, GossipType.MINOR_POSITIVE, grant);
                }
                // A leader zombie killed inside the village counts as repelling one raid for its killer: the
                // very same reward path a raid victory uses (villagers within 64 of the village centre gain
                // MAJOR_POSITIVE 2, once per player and village per week). Nothing here touches vanilla raid
                // code, so no Hero of the Village is ever granted.
                if (entity instanceof Zombie zombie && isLeader(zombie)) {
                    Villages.center(village).ifPresent(center -> raidWon(level, BlockPos.containing(center), Set.of(id)));
                }
            } else {
                for (var v : villagers(level, death, 24)) {
                    int grant = state.dailyGrant("kill", id, v.getUUID(), now, weight, 5);
                    SaviorGossip.add(v, id, GossipType.MINOR_POSITIVE, grant);
                }
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
