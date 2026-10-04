package dev.villagerssavior.test;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import dev.villagerssavior.*;
import dev.villagerssavior.debug.*;
import dev.villagerssavior.profession.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.item.*;

/** Real-server checks: compare previews with actual operations and verify inspection leaves state intact. */
public final class HudInspectionChecks {
    private static TranslatableContents find(Component line, String key) {
        if (line.getContents() instanceof TranslatableContents contents) {
            if (contents.getKey().equals("hud.villagers_savior." + key)) return contents;
            for (Object arg : contents.getArgs()) if (arg instanceof Component component) {
                var found = find(component, key); if (found != null) return found;
            }
        }
        for (var sibling : line.getSiblings()) { var found = find(sibling, key); if (found != null) return found; }
        return null;
    }
    public static boolean has(VillagerDetails details, String key) {
        return details.offers().stream().anyMatch(line -> find(line, key) != null);
    }
    private static int offered(VillagerDetails details) {
        for (var line : details.offers()) {
            var count = find(line, "item_count");
            if (count != null) return ((Number) count.getArgs()[1]).intValue();
        }
        return 0;
    }
    private static String inventory(net.minecraft.world.Container inventory) {
        List<String> contents = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            contents.add(stack.getItem().getDescriptionId() + ":" + stack.getCount() + ":" + stack.getDamageValue());
        }
        return contents.toString();
    }
    private static String history(SaviorState state) {
        return SaviorState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow().toString();
    }
    public static void run(MinecraftServer server, BiConsumer<Boolean, String> check) {
        var level = server.overworld();
        var homePos = new BlockPos(0, 4, 0);
        level.getPoiManager().add(homePos, level.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(PoiTypes.HOME));
        var profile = new GameProfile(UUID.randomUUID(), "HudInspection");
        var player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
            CommonListenerCookie.createInitial(profile, false));
        player.snapTo(1.5, 4, 0.5);
        var villager = new Villager(EntityTypes.VILLAGER, level); villager.snapTo(0.5, 4, 0.5);
        villager.setNoAi(true); level.addFreshEntity(villager);
        var state = SaviorState.get(level);
        villager.getInventory().setItem(0, new ItemStack(Items.POTATO, 40));
        player.getFoodData().setFoodLevel(10);
        String beforeHistory = history(state), beforeInventory = inventory(villager.getInventory());
        int drops = level.getEntitiesOfClass(ItemEntity.class, villager.getBoundingBox().inflate(5)).size();
        var beforeGossip = ReputationDebug.inspect(villager, player);
        var details = VillagerInspection.inspect(villager, player);
        check.accept(has(details, "food_offer") && offered(details) == 3, "HUD predicts exactly three ordinary potatoes");
        for (int i = 0; i < 25; i++) VillagerInspection.inspect(villager, player);
        check.accept(beforeHistory.equals(history(state)), "25 HUD polls do not create village IDs or change history");
        check.accept(beforeInventory.equals(inventory(villager.getInventory())), "HUD polls do not consume food");
        check.accept(beforeGossip.equals(ReputationDebug.inspect(villager, player)), "HUD polls do not change gossip");
        check.accept(drops == level.getEntitiesOfClass(ItemEntity.class, villager.getBoundingBox().inflate(5)).size(),
            "HUD polls do not spawn gift entities");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            VillagerDetails.CODEC.encode(buffer, details);
            check.accept(details.equals(VillagerDetails.CODEC.decode(buffer)), "HUD packet round-trip retains localized components and reputation");
        } finally { buffer.release(); }
        int stock = villager.getInventory().countItem(Items.POTATO);
        check.accept(FoodGifts.give(villager, player, InteractionHand.MAIN_HAND).equals("gift")
            && stock - villager.getInventory().countItem(Items.POTATO) == offered(details), "HUD ordinary offer matches actual delivery");
        player.getFoodData().setFoodLevel(5); villager.getInventory().clearContent();
        villager.getInventory().setItem(0, new ItemStack(Items.POTATO));
        villager.getInventory().setItem(1, new ItemStack(Items.BREAD));
        details = VillagerInspection.inspect(villager, player);
        check.accept(has(details, "food_emergency") && offered(details) == 1, "HUD predicts emergency relief");
        check.accept(FoodGifts.give(villager, player, InteractionHand.MAIN_HAND).equals("emergency"), "HUD emergency offer matches actual relief");
        villager.getInventory().setItem(0, new ItemStack(Items.POTATO));
        beforeHistory = history(state);
        check.accept(has(VillagerInspection.inspect(villager, player), "food_cooldown"), "HUD shows village relief cooldown");
        check.accept(beforeHistory.equals(history(state)), "reading relief cooldown does not renew it");
        player.getFoodData().setFoodLevel(20);
        check.accept(has(VillagerInspection.inspect(villager, player), "food_full"), "HUD identifies full hunger");
        var ledger = new SaviorState();
        String a = ledger.village(List.of("a")), b = ledger.village(List.of("b"));
        ledger.cooldown("emergency", player.getUUID(), a, 100); ledger.cooldown("emergency", player.getUUID(), b, 200);
        beforeHistory = history(ledger);
        check.accept(ledger.remainingAt("emergency", player.getUUID(), List.of("a", "b"), 201, 48000) == 47999,
            "read-only preview honors latest cooldown across a pending village merge");
        check.accept(beforeHistory.equals(history(ledger)), "pending merge preview does not merge ledger IDs");
        ledger.dailyGrant("repair", player.getUUID(), villager.getUUID(), 0, 2, 2);
        beforeHistory = history(ledger);
        check.accept(ledger.dailyRemaining("repair", player.getUUID(), villager.getUUID(), 1, 2) == 0
            && ledger.dailyRemaining("repair", player.getUUID(), villager.getUUID(), 24000, 2) == 2,
            "HUD repair quota follows real day boundaries");
        check.accept(beforeHistory.equals(history(ledger)), "previewing next-day quota does not reset history");
        for (var profession : ProfessionInteractions.implemented()) {
            var pProfile = new GameProfile(UUID.randomUUID(), "HudService");
            var p = new ServerPlayer(server, level, pProfile, ClientInformation.createDefault());
            p.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), p,
                CommonListenerCookie.createInitial(pProfile, false));
            p.snapTo(1.5, 4, 0.5); p.getFoodData().setFoodLevel(20);
            var v = new Villager(EntityTypes.VILLAGER, level); v.snapTo(0.5, 4, 0.5); v.setNoAi(true);
            v.setVillagerData(v.getVillagerData().withProfession(level.registryAccess(), profession)); level.addFreshEntity(v);
            SaviorGossip.add(v, p.getUUID(), GossipType.MAJOR_POSITIVE, 20);
            v.getInventory().setItem(0, new ItemStack(Items.POTATO, 40));
            List<ItemStack> materials = List.of(new ItemStack(Items.FLINT, 4), new ItemStack(Items.STICK, 4),
                new ItemStack(Items.FEATHER, 4), new ItemStack(Items.STONE, 8), new ItemStack(Items.COAL),
                new ItemStack(Items.BEEF, 3), new ItemStack(Items.COD, 3), new ItemStack(Items.GOLD_INGOT),
                new ItemStack(Items.WOOL.pick(DyeColor.WHITE), 4), new ItemStack(Items.IRON_INGOT, 8), new ItemStack(Items.LEATHER, 8));
            for (int i = 0; i < materials.size(); i++) p.getInventory().setItem(i, materials.get(i).copy());
            Item target = switch (profession.identifier().getPath()) {
                case "weaponsmith" -> Items.IRON_SWORD;
                case "armorer" -> Items.IRON_CHESTPLATE;
                case "leatherworker" -> Items.LEATHER_CHESTPLATE;
                default -> Items.IRON_PICKAXE;
            };
            var damaged = new ItemStack(target); damaged.setDamageValue(80); p.getInventory().setItem(15, damaged);
            beforeHistory = history(state); beforeInventory = inventory(p.getInventory());
            String villagerStock = inventory(v.getInventory());
            var offer = VillagerInspection.inspect(v, p);
            String name = profession.identifier().getPath();
            check.accept(has(offer, "available"), "HUD offers available " + name + " service");
            for (int i = 0; i < 3; i++) VillagerInspection.inspect(v, p);
            check.accept(beforeHistory.equals(history(state)) && beforeInventory.equals(inventory(p.getInventory()))
                && villagerStock.equals(inventory(v.getInventory())) && !p.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION),
                "HUD leaves inventories, durability, effects and history unchanged for " + name);
            check.accept(ProfessionInteractions.serve(v, p).isPresent(), "real " + name + " service succeeds after read-only preview");
            if (!name.equals("librarian") && !name.equals("cartographer"))
                check.accept(has(VillagerInspection.inspect(v, p), "cooldown"), "HUD displays actual " + name + " cooldown");
        }
        villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.CLERIC));
        villager.getGossips().clear(); player.getInventory().clearContent();
        check.accept(has(VillagerInspection.inspect(villager, player), "trust_required"), "HUD shows insufficient reputation");
        SaviorGossip.add(villager, player.getUUID(), GossipType.MAJOR_POSITIVE, 20);
        check.accept(has(VillagerInspection.inspect(villager, player), "materials"), "HUD shows missing gold");
        player.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT)); player.getFoodData().setFoodLevel(10);
        villager.getInventory().setItem(0, new ItemStack(Items.POTATO, 40));
        check.accept(has(VillagerInspection.inspect(villager, player), "after_food"), "HUD explains gift priority over services");
        villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FARMER));
        player.getFoodData().setFoodLevel(20); villager.getInventory().clearContent();
        villager.getInventory().setItem(0, new ItemStack(Items.WHEAT, 24)); beforeInventory = inventory(villager.getInventory());
        details = VillagerInspection.inspect(villager, player);
        check.accept(has(details, "ration_stock"), "HUD does not promise bread when processed wheat has insufficient shareable surplus");
        check.accept(beforeInventory.equals(inventory(villager.getInventory())), "farmer preview leaves real wheat unprocessed");
        check.accept(ProfessionInteractions.serve(villager, player).isEmpty(), "real wheat-only farmer agrees with unavailable ration preview");
        villager.getInventory().clearContent();
        villager.getInventory().setItem(0, new ItemStack(Items.POTATO, 20));
        villager.getInventory().setItem(1, new ItemStack(Items.WHEAT, 24));
        beforeInventory = inventory(villager.getInventory());
        details = VillagerInspection.inspect(villager, player);
        check.accept(has(details, "available") && offered(details) == 3, "HUD simulates wheat processing and predicts three spare potatoes");
        check.accept(beforeInventory.equals(inventory(villager.getInventory())), "mixed farmer stock remains unchanged during preview");
        check.accept(ProfessionInteractions.serve(villager, player).orElse("").equals("ration")
            && villager.getInventory().countItem(Items.POTATO) == 17, "mixed farmer preview matches actual ration quantity");
        villager.setTradingPlayer(new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "Other"), ClientInformation.createDefault()));
        check.accept(has(VillagerInspection.inspect(villager, player), "busy"), "HUD blocks busy villagers");
        villager.setTradingPlayer(null); villager.startSleeping(homePos);
        check.accept(has(VillagerInspection.inspect(villager, player), "sleeping"), "HUD blocks sleeping villagers");
        villager.stopSleeping(); villager.snapTo(4096, 4, 4096);
        check.accept(has(VillagerInspection.inspect(villager, player), "village_required"), "HUD blocks village-scoped services outside village");
        villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.NITWIT));
        check.accept(has(VillagerInspection.inspect(villager, player), "service_none"), "HUD identifies villagers without profession services");
    }
}
