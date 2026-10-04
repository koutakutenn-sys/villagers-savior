package dev.villagerssavior.profession;

import dev.villagerssavior.FoodRules;
import dev.villagerssavior.SaviorState;
import dev.villagerssavior.debug.ReputationDebug;
import dev.villagerssavior.debug.VillagerInspection;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ColorCollection;
import static dev.villagerssavior.debug.VillagerInspection.text;

/** Read-only previews of the existing thirteen services, including resources and shared cooldowns. */
public final class ProfessionOffers {
    private static final Map<Item, Item> FISH = Map.of(Items.COD, Items.COOKED_COD, Items.SALMON, Items.COOKED_SALMON);
    private static final Map<Item, Item> MEAT = Map.of(Items.BEEF, Items.COOKED_BEEF, Items.PORKCHOP, Items.COOKED_PORKCHOP,
        Items.CHICKEN, Items.COOKED_CHICKEN, Items.MUTTON, Items.COOKED_MUTTON, Items.RABBIT, Items.COOKED_RABBIT);
    private ProfessionOffers() {}
    public static List<Component> inspect(Villager villager, ServerPlayer player,
                                          Optional<Collection<String>> positions, boolean foodFirst) {
        String profession = ReputationDebug.profession(villager);
        String service = "service_" + profession;
        if (profession.equals("none") || profession.equals("nitwit")) return List.of(text("service_none"));
        // These two informational services intentionally have no reputation/material/cooldown gate.
        if (profession.equals("librarian") || profession.equals("cartographer"))
            return List.of(status(service, ready(text("information"), foodFirst)));
        if (!Set.of("farmer", "fisherman", "butcher", "fletcher", "shepherd", "mason", "cleric",
                    "toolsmith", "weaponsmith", "armorer", "leatherworker").contains(profession))
            return List.of(text("service_none"));
        if (profession.equals("farmer")) service = "ration";
        if (profession.equals("cleric")) return List.of(status(service, cleric(villager, player, positions, foodFirst)));
        int reputation = ProfessionInteractions.reputation(villager, player);
        if (reputation < ProfessionInteractions.MIN_REPUTATION)
            return List.of(status(service, text("trust_required", ProfessionInteractions.MIN_REPUTATION)));
        if (profession.equals("farmer") && player.getFoodData().getFoodLevel() < 20)
            return List.of(status(service, text("ration_full_required")));
        if (positions.isEmpty()) return List.of(status(service, text("village_required")));
        var state = SaviorState.get(player.level());
        long now = player.level().getGameTime();
        long window = profession.equals("farmer") ? Rations.COOLDOWN : ProfessionInteractions.SERVICE_WINDOW;
        long remaining = state.remainingAt(service, player.getUUID(), positions.get(), now, window);
        if (remaining > 0) return List.of(status(service, text("cooldown", VillagerInspection.seconds(remaining))));
        Component result = switch (profession) {
            case "farmer" -> rations(villager, reputation);
            case "fisherman" -> cooking(villager, FISH, reputation);
            case "butcher" -> cooking(villager, MEAT, reputation);
            case "fletcher" -> conversion(villager, Map.of(Items.FLINT, 1, Items.STICK, 1, Items.FEATHER, 1),
                Items.ARROW, 4, reputation);
            case "mason" -> conversion(villager, Map.of(Items.STONE, 4), Items.STONE_BRICKS, 4, reputation);
            case "shepherd" -> weaving(villager, reputation);
            default -> repair(villager, player, state, service, reputation, now, switch (profession) {
                case "toolsmith" -> RepairService.TOOLS;
                case "weaponsmith" -> RepairService.WEAPONS;
                case "armorer" -> RepairService.ARMOR;
                default -> RepairService.LEATHER_ARMOR;
            });
        };
        // Preview helpers use ready; replace it when a food gift has priority over this service.
        if (foodFirst && !profession.equals("cleric") && result.getContents() instanceof
            net.minecraft.network.chat.contents.TranslatableContents contents
            && contents.getKey().equals("hud.villagers_savior.available"))
            result = text("after_food", contents.getArgs());
        return List.of(status(service, result));
    }
    private static Component status(String service, Component detail) { return text("service", text(service), detail); }
    private static Component ready(Component detail, boolean foodFirst) {
        return text(foodFirst ? "after_food" : "available", detail);
    }
    private static Component cleric(Villager villager, ServerPlayer player,
                                     Optional<Collection<String>> positions, boolean foodFirst) {
        int reputation = ProfessionInteractions.reputation(villager, player);
        var plan = ClericInteraction.blessing(reputation, player.getHealth());
        if (plan.isEmpty()) return text("cleric_health_required", reputation <= -100 ? 2 : 6);
        if (positions.isEmpty()) return text("village_required");
        long remaining = ClericInteraction.remainingAt(SaviorState.get(player.level()), player.getUUID(), positions.get(),
            player.level().getGameTime());
        if (remaining > 0) return text("cooldown", VillagerInspection.seconds(remaining));
        return ClericInteraction.reagent(villager).isPresent()
            ? ready(text("blessing", plan.get().duration() / 20), foodFirst) : text("materials", text("alchemy_cost"));
    }
    private static Component rations(Villager villager, int reputation) {
        var inventory = FarmerInteraction.previewInventory(villager.getInventory());
        int[] nutrition = new int[inventory.getContainerSize()], counts = new int[nutrition.length];
        long total = ServiceItems.nutrition(inventory, nutrition, counts);
        int[] selection = FoodRules.select(nutrition, counts, Rations.budget(Math.max(0L, total - 20), reputation));
        if (Arrays.stream(selection).noneMatch(n -> n > 0)) return text("ration_stock");
        return ready(VillagerInspection.items(inventory, selection), false);
    }
    private static Component conversion(Villager villager, Map<Item, Integer> cost, Item result, int each, int reputation) {
        int batches = Conversions.affordable(villager.getInventory(), cost, Conversions.capFor(reputation));
        var inputs = Component.empty();
        cost.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().getDescriptionId())).forEach(e -> {
            if (!inputs.getSiblings().isEmpty()) inputs.append(Component.literal("、"));
            inputs.append(text("item_count", new ItemStack(e.getKey()).getHoverName(), e.getValue()));
        });
        if (batches <= 0) return text("materials", inputs);
        return ready(text("conversion", inputs, batches, new ItemStack(result).getHoverName(), batches * each), false);
    }
    private static Component cooking(Villager villager, Map<Item, Item> recipes, int reputation) {
        int raw = recipes.keySet().stream().mapToInt(item -> ServiceItems.count(villager.getInventory(), item)).sum();
        int coal = ServiceItems.count(villager.getInventory(), Items.COAL);
        int amount = Math.min(Math.min(raw, Conversions.capFor(reputation)), coal * Cooking.ITEMS_PER_COAL);
        if (amount <= 0) return text("materials", text(recipes == FISH ? "fish_cost" : "meat_cost"));
        var result = Component.empty();
        int left = amount;
        for (int slot = 0; slot < villager.getInventory().getContainerSize() && left > 0; slot++) {
            var stack = villager.getInventory().getItem(slot);
            Item cooked = recipes.get(stack.getItem());
            if (cooked == null) continue;
            int count = Math.min(left, stack.getCount());
            if (count <= 0) continue;
            if (!result.getSiblings().isEmpty()) result.append(Component.literal("、"));
            result.append(text("item_count", new ItemStack(cooked).getHoverName(), count)); left -= count;
        }
        return ready(text("cooking", result, (amount + Cooking.ITEMS_PER_COAL - 1) / Cooking.ITEMS_PER_COAL), false);
    }
    private static Component weaving(Villager villager, int reputation) {
        int[] left = {Conversions.capFor(reputation)};
        var result = Component.empty();
        ColorCollection.zipApply(Items.WOOL, Items.CARPET, (wool, carpet) -> {
            if (wool == null || carpet == null || left[0] <= 0) return;
            int batches = Math.min(ServiceItems.count(villager.getInventory(), wool) / 2, left[0]);
            if (batches <= 0) return;
            if (!result.getSiblings().isEmpty()) result.append(Component.literal("、"));
            result.append(text("item_count", new ItemStack(carpet).getHoverName(), batches * 3)); left[0] -= batches;
        });
        if (result.getSiblings().isEmpty()) return text("materials", text("wool_cost"));
        return ready(text("weaving", result, (Conversions.capFor(reputation) - left[0]) * 2), false);
    }
    private static Component repair(Villager villager, ServerPlayer player, SaviorState state, String service,
                                    int reputation, long now, Predicate<ItemStack> eligible) {
        int quota = state.dailyRemaining(service + "_quota", player.getUUID(), villager.getUUID(), now,
            Repairs.dailyUnits(reputation));
        if (quota == 0) return text("quota");
        var found = Repairs.find(player.getInventory(), villager.getInventory(), eligible);
        if (found.isEmpty()) return text("repair_materials");
        var job = found.get();
        int perUnit = Repairs.amountPerUnit(job.target().getMaxDamage(), reputation);
        int needed = (job.target().getDamageValue() + perUnit - 1) / perUnit;
        int units = Math.min(quota, Math.min(job.available(), needed));
        return ready(text("repair", job.target().getHoverName(), Math.min(job.target().getDamageValue(), units * perUnit),
            new ItemStack(job.material()).getHoverName(), units), false);
    }
}
