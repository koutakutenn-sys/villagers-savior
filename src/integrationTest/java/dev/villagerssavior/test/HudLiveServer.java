package dev.villagerssavior.test;

import dev.villagerssavior.SaviorGossip;
import dev.villagerssavior.profession.ProfessionInteractions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

public final class HudLiveServer {
    private static Villager first, second;
    private static int ticks;
    private static boolean done;
    public static void tick(MinecraftServer server) { if (done || ++ticks > 4000) server.halt(false); }
    public static void control(ServerPlayer player, int code) {
        var level = player.level();
        if (code == 99) { done = true; return; }
        if (first == null) {
            level.getChunk(0, 0);
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++)
                level.setBlock(new BlockPos(x, 3, z), Blocks.STONE.defaultBlockState(), 3);
            first = new Villager(EntityTypes.VILLAGER, level); first.snapTo(0.5, 4, 0.5);
            first.setNoAi(true); first.setNoGravity(true);
            first.setVillagerData(first.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FLETCHER));
            level.addFreshEntity(first); first.getInventory().setItem(0, new ItemStack(Items.POTATO, 40));
            SaviorGossip.add(first, player.getUUID(), GossipType.MAJOR_POSITIVE, 5);
            second = new Villager(EntityTypes.VILLAGER, level); second.snapTo(2.0, 4, 1.0);
            second.setNoAi(true); second.setNoGravity(true);
            second.setVillagerData(second.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.NITWIT));
            level.addFreshEntity(second);
            level.getPoiManager().add(new BlockPos(0, 4, 0), level.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(PoiTypes.HOME));
            player.getInventory().clearContent(); player.getFoodData().setFoodLevel(10);
            player.getInventory().setItem(9, new ItemStack(Items.FLINT, 2));
            player.getInventory().setItem(10, new ItemStack(Items.STICK, 2));
            player.getInventory().setItem(11, new ItemStack(Items.FEATHER, 2));
            player.teleportTo(0.5, 4, 2.5);
        }
        if (code >= 20 && Boolean.getBoolean("savior.audit.scan")) { ScanLiveServer.control(player, code); return; }
        if (code == 1) {
            first.getGossips().clear(); SaviorGossip.add(first, player.getUUID(), GossipType.MAJOR_POSITIVE, 15);
            player.getFoodData().setFoodLevel(20);
        }
        if (code == 2) ProfessionInteractions.serve(first, player);
        int entity = code == 3 ? second.getId() : first.getId();
        var reply = new AuditNet.Reply(code, entity, first.getInventory().countItem(Items.POTATO),
            player.getInventory().countItem(Items.FLINT), player.getInventory().countItem(Items.ARROW), false);
        System.out.println("HUD_LIVE_SERVER " + reply);
        ServerPlayNetworking.send(player, reply);
    }
}
