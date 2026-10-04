package dev.villagerssavior.test;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import dev.villagerssavior.*;
import dev.villagerssavior.debug.VillagerInspection;
import dev.villagerssavior.mixin.ItemEntityAccess;
import dev.villagerssavior.profession.ServiceItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import java.util.*;
import java.util.function.BiConsumer;

public final class NitwitInventoryChecks {
    public static void run(MinecraftServer server, BiConsumer<Boolean, String> check) {
        var level = server.overworld(); level.getChunk(512, 0);
        var profile = new GameProfile(UUID.randomUUID(), "NitwitItemsAudit");
        var player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
            CommonListenerCookie.createInitial(profile, false));
        player.snapTo(8193, 4, 0);
        var nitwit = new PickupVillager(level); nitwit.snapTo(8192, 4, 0); nitwit.setNoAi(true);
        nitwit.setVillagerData(nitwit.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.NITWIT));
        check.accept(level.addFreshEntity(nitwit), "nitwit inventory: spawn live test entity");
        nitwit.restock();
        check.accept(nitwit.getInventory().isEmpty(), "nitwit inventory: actual restock produces nothing");
        for (var item : List.of(Items.DIAMOND, Items.DIRT, Items.BREAD, Items.IRON_SWORD, Items.POTION, Items.SHULKER_BOX))
            check.accept(nitwit.wantsToPickUp(level, new ItemStack(item)), "nitwit inventory: accepts item class " + item);
        check.accept(!nitwit.wantsToPickUp(level, ItemStack.EMPTY), "nitwit inventory: empty stack is never collectible");
        var ordinary = new Villager(EntityTypes.VILLAGER, level);
        check.accept(!ordinary.wantsToPickUp(level, new ItemStack(Items.DIAMOND)), "nitwit inventory: ordinary villager pickup restrictions remain");

        var sword = new ItemStack(Items.IRON_SWORD); sword.setDamageValue(37);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Collected sword"));
        var swordItem = ground(level, sword.copy()); nitwit.grab(level, swordItem);
        check.accept(swordItem.isRemoved() && ItemStack.isSameItemSameComponents(nitwit.getInventory().getItem(0), sword),
            "nitwit inventory: pickup preserves durability and data components");
        var delayed = ground(level, new ItemStack(Items.DIAMOND, 2)); delayed.setPickUpDelay(20);
        nitwit.grab(level, delayed);
        check.accept(!delayed.isRemoved() && ServiceItems.count(nitwit.getInventory(), Items.DIAMOND) == 0,
            "nitwit inventory: direct pickup respects delay");
        var reserved = ground(level, new ItemStack(Items.DIAMOND, 3)); reserved.setTarget(player.getUUID());
        nitwit.grab(level, reserved);
        check.accept(!reserved.isRemoved() && ServiceItems.count(nitwit.getInventory(), Items.DIAMOND) == 0,
            "nitwit inventory: player-targeted items are protected");
        var thrown = ground(level, new ItemStack(Items.DIAMOND, 4)); thrown.setThrower(player);
        nitwit.grab(level, thrown);
        check.accept(thrown.isRemoved() && ServiceItems.count(nitwit.getInventory(), Items.DIAMOND) == 4,
            "nitwit inventory: ordinary player-thrown items can be collected");
        var natural = ground(level, new ItemStack(Items.STICK, 5));
        nitwit.aiStep();
        check.accept(natural.isRemoved() && ServiceItems.count(nitwit.getInventory(), Items.STICK) == 5,
            "nitwit inventory: vanilla aiStep automatically collects ground items");
        check.accept(!delayed.isRemoved() && !reserved.isRemoved(), "nitwit inventory: automatic pickup also respects delay and recipient");

        boolean originalRule = level.getGameRules().get(GameRules.MOB_GRIEFING);
        level.getGameRules().set(GameRules.MOB_GRIEFING, false, server);
        var griefingItem = ground(level, new ItemStack(Items.COAL)); nitwit.aiStep();
        check.accept(!griefingItem.isRemoved(), "nitwit inventory: automatic pickup respects mobGriefing false");
        level.getGameRules().set(GameRules.MOB_GRIEFING, originalRule, server);

        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        check.accept(nitwit.save(output), "nitwit inventory: collected inventory serializes");
        var restored = new Villager(EntityTypes.VILLAGER, level);
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()));
        check.accept(ItemStack.isSameItemSameComponents(restored.getInventory().getItem(0), sword)
            && ServiceItems.count(restored.getInventory(), Items.DIAMOND) == 4, "nitwit inventory: real entity save/load preserves collected items");

        for (int slot = 0; slot < 8; slot++) nitwit.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        var overflow = ground(level, new ItemStack(Items.DIAMOND, 4)); nitwit.grab(level, overflow);
        check.accept(!nitwit.wantsToPickUp(level, overflow.getItem()) && overflow.getItem().getCount() == 4,
            "nitwit inventory: full inventory leaves ground stack untouched");
        nitwit.getInventory().setItem(7, new ItemStack(Items.DIAMOND, 63)); nitwit.grab(level, overflow);
        check.accept(!overflow.isRemoved() && overflow.getItem().getCount() == 3
            && ServiceItems.count(nitwit.getInventory(), Items.DIAMOND) == 64, "nitwit inventory: partial pickup leaves exact remainder");
        var chestPos = new BlockPos(8193, 4, 1); level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        var chest = (ChestBlockEntity) level.getBlockEntity(chestPos); chest.setItem(0, new ItemStack(Items.EMERALD, 8));
        nitwit.aiStep();
        check.accept(chest.getItem(0).getCount() == 8 && ServiceItems.count(nitwit.getInventory(), Items.EMERALD) == 0,
            "nitwit inventory: nearby chest is never looted");

        nitwit.getInventory().clearContent(); nitwit.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 4));
        nitwit.getInventory().setItem(1, new ItemStack(Items.BREAD, 7)); nitwit.getInventory().setItem(2, sword.copy());
        SaviorGossip.add(nitwit, player.getUUID(), GossipType.MINOR_NEGATIVE, 200);
        player.getFoodData().setFoodLevel(0); player.getInventory().clearContent();
        var state = SaviorState.get(level);
        var before = SaviorState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        var preview = VillagerInspection.inspect(nitwit, player);
        check.accept(HudInspectionChecks.has(preview, "nitwit_items")
            && !HudInspectionChecks.has(preview, "trust_required") && !HudInspectionChecks.has(preview, "food_offer"),
            "nitwit inventory: HUD previews all items without ordinary food or reputation gates");
        check.accept(ServiceItems.count(nitwit.getInventory(), Items.BREAD) == 7, "nitwit inventory: HUD preview is read-only");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        FoodGifts.request(player, new FoodRequest(nitwit.getId(), InteractionHand.MAIN_HAND));
        check.accept(ServiceItems.count(nitwit.getInventory(), Items.DIAMOND) == 4, "nitwit inventory: request still rejects nonempty hand");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); player.snapTo(8200, 4, 0);
        FoodGifts.request(player, new FoodRequest(nitwit.getId(), InteractionHand.MAIN_HAND));
        check.accept(ServiceItems.count(nitwit.getInventory(), Items.DIAMOND) == 4, "nitwit inventory: request still rejects distant player");
        player.snapTo(8193, 4, 0);
        FoodGifts.request(player, new FoodRequest(nitwit.getId(), InteractionHand.MAIN_HAND));
        check.accept(nitwit.getInventory().isEmpty(), "nitwit inventory: negative reputation and hunger zero still hand over every item");
        var gifts = gifts(level, player);
        check.accept(gifts.stream().mapToInt(item -> item.getItem().getCount()).sum() == 12
            && gifts.stream().anyMatch(item -> ItemStack.isSameItemSameComponents(item.getItem(), sword)),
            "nitwit inventory: handover preserves exact counts and data components");
        check.accept(gifts.stream().allMatch(ItemEntity::hasPickUpDelay), "nitwit inventory: handover applies normal pickup delay");
        player.getFoodData().setFoodLevel(20); nitwit.getInventory().setItem(0, new ItemStack(Items.EMERALD, 2));
        FoodGifts.request(player, new FoodRequest(nitwit.getId(), InteractionHand.MAIN_HAND));
        check.accept(nitwit.getInventory().isEmpty() && gifts(level, player).stream().mapToInt(item -> item.getItem().getCount()).sum() == 14,
            "nitwit inventory: immediate second request while full has no cooldown or throttle");
        FoodGifts.request(player, new FoodRequest(nitwit.getId(), InteractionHand.MAIN_HAND));
        check.accept(gifts(level, player).stream().mapToInt(item -> item.getItem().getCount()).sum() == 14,
            "nitwit inventory: repeated empty request cannot duplicate items");
        check.accept(HudInspectionChecks.has(VillagerInspection.inspect(nitwit, player), "nitwit_empty"),
            "nitwit inventory: HUD reports empty after handover");
        check.accept(before.equals(SaviorState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow()),
            "nitwit inventory: preview and handover never write cooldown or quota state");
        nitwit.aiStep();
        check.accept(ServiceItems.count(nitwit.getInventory(), Items.EMERALD) == 0,
            "nitwit inventory: targeted handover items cannot be collected back");
    }
    private static List<ItemEntity> gifts(ServerLevel level, ServerPlayer player) {
        return level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8),
            item -> player.getUUID().equals(((ItemEntityAccess) item).savior$pickupTarget()) && item.getOwner() instanceof Villager);
    }
    private static ItemEntity ground(ServerLevel level, ItemStack stack) {
        var item = new ItemEntity(level, 8192, 4, 0, stack); item.setNoPickUpDelay(); level.addFreshEntity(item); return item;
    }
    private static final class PickupVillager extends Villager {
        PickupVillager(ServerLevel level) { super(EntityTypes.VILLAGER, level); }
        void grab(ServerLevel level, ItemEntity item) { super.pickUpItem(level, item); }
    }
}
