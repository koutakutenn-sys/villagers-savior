package dev.villagerssavior.profession;

import dev.villagerssavior.SaviorState;
import dev.villagerssavior.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.Collection;

/**
 * The farmer's population supply: while a village has more reachable beds than villagers it has room to grow,
 * so a farmer that just finished restocking keeps one breeding reserve of the crop the village really grows.
 *
 * <p><b>Gameplay trade-off:</b> this is the one place where the mod deliberately conjures items instead of
 * moving real ones, because vanilla farmers are far too weak to sustain a village. The conjuring is bounded by
 * the farmer's production model (one per restock, four per in-game day, stop at the breeding reserve, hard cap
 * 48) and the crop type must come from evidence: the farmer's real stock first, then crops that are really
 * planted around it. Bread is never produced on its own.
 */
public final class PopulationSupply {
    /** One vanilla breeding reserve: twelve of a crop, the same as three bread. */
    public static final int RESERVE = 12;
    /** How far around the farmer planted crops are inspected. */
    private static final int SCAN_HORIZONTAL = 32;
    private static final int SCAN_VERTICAL = 4;
    /** Villagers up to this far outside the village POI cluster still count as its population. */
    private static final int VILLAGE_MARGIN = 32;
    /** Kept in a nested holder so the pure helpers above never need the item registry to be bootstrapped. */
    private static final class Crops {
        static final Item[] ITEMS = {Items.WHEAT, Items.CARROT, Items.POTATO, Items.BEETROOT};
        static final Block[] BLOCKS = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS};
    }
    private PopulationSupply() {}
    /** The village only needs supplies while it has room to grow: population below reachable beds. */
    public static boolean needsSupply(int population, int beds) { return population < beds; }
    /** How much has to be added to own exactly one breeding reserve. */
    public static int topUp(int held) { return Math.max(0, RESERVE - held); }
    /** Index of the most plentiful entry, or -1 when nothing was found; ties keep the earliest entry. */
    public static int pickIndex(int[] counts) {
        int best = -1;
        for (int i = 0; i < counts.length; i++)
            if (counts[i] > 0 && (best < 0 || counts[i] > counts[best])) best = i;
        return best;
    }
    /** The crop item a chosen index refers to. */
    public static Item cropItem(int index) { return Crops.ITEMS[index]; }
    /**
     * The farmer's production step: only with spare beds, only a crop the village really grows, never bread.
     *
     * @return how many items were actually added (0 when nothing was warranted)
     */
    public static int produce(ServerLevel level, Villager villager, SaviorState state, long now) {
        if (villager.getVillagerData().profession().unwrapKey()
            .filter(VillagerProfession.FARMER::equals).isEmpty()) return 0;
        var own = Villages.positions(level, villager.blockPosition());
        if (own.isEmpty()) return 0;
        Collection<String> positions = own.get();
        if (!needsSupply(countPopulation(level, positions), countBeds(level, positions))) return 0;
        int crop = chooseCrop(level, villager);
        if (crop < 0) return 0;
        var resource = ProfessionResources.find(VillagerProfession.FARMER, cropItem(crop));
        if (resource == null) return 0;
        return ProfessionProduction.produce(villager, state, now, resource);
    }
    /** Reachable valid beds: the home POIs that belong to this village's connected POI cluster. */
    private static int countBeds(ServerLevel level, Collection<String> positions) {
        PoiManager manager = level.getPoiManager();
        int beds = 0;
        for (String encoded : positions) {
            BlockPos pos = BlockPos.of(Long.parseLong(encoded));
            if (manager.existsAtPosition(PoiTypes.HOME, pos)) beds++;
        }
        return beds;
    }
    /** Village population: living villagers around the village's connected POI cluster. */
    private static int countPopulation(ServerLevel level, Collection<String> positions) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (String encoded : positions) {
            BlockPos pos = BlockPos.of(Long.parseLong(encoded));
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        AABB area = new AABB(
            minX - VILLAGE_MARGIN, Math.max(level.getMinY(), minY - VILLAGE_MARGIN), minZ - VILLAGE_MARGIN,
            maxX + 1 + VILLAGE_MARGIN, Math.min(level.getMaxY() + 1, maxY + 1 + VILLAGE_MARGIN), maxZ + 1 + VILLAGE_MARGIN);
        return level.getEntitiesOfClass(Villager.class, area, Entity::isAlive).size();
    }
    /** What the village grows: the farmer's real stock first, then what is really planted around it. */
    private static int chooseCrop(ServerLevel level, Villager villager) {
        int[] held = new int[Crops.ITEMS.length];
        for (int i = 0; i < Crops.ITEMS.length; i++) held[i] = ServiceItems.count(villager.getInventory(), Crops.ITEMS[i]);
        int fromStock = pickIndex(held);
        if (fromStock >= 0) return fromStock;
        int[] planted = new int[Crops.ITEMS.length];
        scanCrops(level, villager.blockPosition(), planted);
        return pickIndex(planted);
    }
    private static void scanCrops(ServerLevel level, BlockPos center, int[] counts) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN_HORIZONTAL; dx <= SCAN_HORIZONTAL; dx++)
            for (int dz = -SCAN_HORIZONTAL; dz <= SCAN_HORIZONTAL; dz++)
                for (int dy = -SCAN_VERTICAL; dy <= SCAN_VERTICAL; dy++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!level.isLoaded(cursor)) continue;
                    BlockState state = level.getBlockState(cursor);
                    for (int i = 0; i < Crops.BLOCKS.length; i++)
                        if (state.is(Crops.BLOCKS[i])) counts[i]++;
                }
    }
}
