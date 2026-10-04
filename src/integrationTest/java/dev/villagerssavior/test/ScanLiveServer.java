package dev.villagerssavior.test;

import dev.villagerssavior.SaviorGossip;
import dev.villagerssavior.debug.ReputationScan;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.TagValueOutput;

/** Test-only command fixtures; never included in the release jar or installed into a real save. */
public final class ScanLiveServer {
    private static boolean prepared;
    private static final int[] REPUTATIONS = {-20, 0, 5, 10, 20, 24, 25, 50, 74, 75, 120};
    private static void save(ServerPlayer player, double x, int reputation, String profession) {
        var level = player.level();
        var villager = new Villager(EntityTypes.VILLAGER, level); villager.snapTo(x, 4, 0.5);
        villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(),
            profession.equals("cleric") ? VillagerProfession.CLERIC : VillagerProfession.FLETCHER));
        SaviorGossip.add(villager, player.getUUID(), GossipType.MAJOR_POSITIVE, reputation / 5);
        SaviorGossip.add(villager, player.getUUID(), GossipType.MINOR_POSITIVE, reputation % 5);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        if (!villager.save(output)) throw new IllegalStateException("native scan fixture save failed");
        var entities = new ListTag(); entities.add(output.buildResult());
        var chunk = NbtUtils.addCurrentDataVersion(new CompoundTag()); chunk.put("Entities", entities);
        chunk.store("Position", ChunkPos.CODEC, villager.chunkPosition());
        ReputationScan.storage(level).write(villager.chunkPosition(), chunk).join();
    }
    public static void control(ServerPlayer player, int code) {
        var level = player.level();
        if (code == 20 && !prepared) {
            for (int i = 0; i < REPUTATIONS.length; i++) {
                var villager = new Villager(EntityTypes.VILLAGER, level);
                villager.snapTo(-30 + i * 3, 4, -5.5); villager.setNoAi(true); villager.setNoGravity(true);
                villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(),
                    i == 1 ? VillagerProfession.NONE : VillagerProfession.FARMER));
                if (REPUTATIONS[i] < 0) SaviorGossip.add(villager, player.getUUID(), GossipType.MINOR_NEGATIVE, -REPUTATIONS[i]);
                else {
                    int major = Math.min(GossipType.MAJOR_POSITIVE.max, REPUTATIONS[i] / 5);
                    SaviorGossip.add(villager, player.getUUID(), GossipType.MAJOR_POSITIVE, major);
                    SaviorGossip.add(villager, player.getUUID(), GossipType.MINOR_POSITIVE, REPUTATIONS[i] - major * 5);
                }
                if (!level.addFreshEntity(villager)) throw new IllegalStateException("native scan fixture spawn failed");
            }
            var home = level.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(PoiTypes.HOME);
            for (int x : new int[]{64, 128, 218}) level.getPoiManager().add(new BlockPos(x, 4, 0), home);
            save(player, 160.5, 12, "cleric"); save(player, 198.5, 99, "fletcher");
            prepared = true;
        }
        var source = player.createCommandSourceStack().withPermission(code == 27 ? PermissionSet.NO_PERMISSIONS : PermissionSet.ALL_PERMISSIONS);
        String command = switch (code) {
            case 21 -> "villagerssavior debug nearby 64";
            case 22 -> "villagerssavior debug list 2";
            case 23 -> "villagerssavior debug list 3";
            case 24 -> "villagerssavior debug village";
            case 25 -> "villagerssavior debug nearby 256";
            case 26 -> "villagerssavior debug nearby 0";
            case 27 -> "villagerssavior debug nearby";
            case 28 -> "villagerssavior debug list";
            case 29 -> "villagerssavior debug nearby";
            default -> null;
        };
        if (command != null) level.getServer().getCommands().performPrefixedCommand(source, command);
        if (code == 29) level.getServer().getCommands().performPrefixedCommand(source, command);
        boolean unloaded = !level.areEntitiesLoaded(new ChunkPos(10, 0).pack()) && !level.areEntitiesLoaded(new ChunkPos(12, 0).pack());
        ServerPlayNetworking.send(player, new AuditNet.Reply(code, 0, 0, 0, 0, unloaded));
    }
}
