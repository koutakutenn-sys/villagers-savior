package dev.villagerssavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.*;

/** World-owned state: no global static player history; all timestamps use monotonic game ticks. */
public final class SaviorState extends SavedData {
    public static final long WEEK = 168000L;
    /** Emergency relief may repeat after 2 in-game days; raid rewards and kill history keep the 7 day window. */
    public static final long EMERGENCY = 48000L;
    /** Farmer travel rations are limited to one handout per player and village per in-game day. */
    public static final long RATION = 24000L;
    static long window(String event) {
        if ("emergency".equals(event)) return EMERGENCY;
        if ("ration".equals(event)) return RATION;
        return WEEK;
    }
    public record Daily(long day, int count) {
        static final Codec<Daily> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("day").forGetter(Daily::day),
            Codec.INT.fieldOf("count").forGetter(Daily::count)).apply(i, Daily::new));
    }
    public static final Codec<SaviorState> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(Codec.STRING, Daily.CODEC).optionalFieldOf("daily", Map.of()).forGetter(s -> s.daily),
        Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf()).optionalFieldOf("kills", Map.of()).forGetter(s -> s.kills),
        Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("cooldowns", Map.of()).forGetter(s -> s.cooldowns),
        Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("pois", Map.of()).forGetter(s -> s.pois),
        Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("aliases", Map.of()).forGetter(s -> s.aliases)
    ).apply(i, SaviorState::new));
    // The DataFixTypes argument must not be null: vanilla SavedDataStorage.readSavedData passes it straight
    // into DataFixTypes.update, and a null value throws inside its Exception-guarded read, which makes every
    // world load silently drop this file and start from empty state (Fabric has no NeoForge-style null patch).
    // Any non-null constant is safe here: DataFixerUpper returns the payload untouched when the stored
    // DataVersion is not older than the running version, and on a future upgrade a mismatching rule only
    // logs and falls back to the original tag.
    public static final SavedDataType<SaviorState> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("villagers_savior", "history"), SaviorState::new, CODEC,
        DataFixTypes.SAVED_DATA_MAP_DATA);
    private final Map<String, Daily> daily;
    private final Map<String, List<Long>> kills;
    private final Map<String, Long> cooldowns;
    private final Map<String, String> pois;
    private final Map<String, String> aliases;
    private long lastDailyPrune = Long.MIN_VALUE;
    public SaviorState() { this(Map.of(), Map.of(), Map.of(), Map.of(), Map.of()); }
    private SaviorState(Map<String, Daily> daily, Map<String, List<Long>> kills,
                        Map<String, Long> cooldowns, Map<String, String> pois, Map<String, String> aliases) {
        this.daily = new HashMap<>(daily); this.kills = new HashMap<>();
        kills.forEach((k,v) -> this.kills.put(k, new ArrayList<>(v)));
        this.cooldowns = new HashMap<>(cooldowns); this.pois = new HashMap<>(pois); this.aliases = new HashMap<>(aliases);
        normalizeCooldowns();
    }
    public static SaviorState get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public int dailyGrant(String event, UUID player, UUID villager, long now, int amount, int cap) {
        String key = event + ":" + player + ":" + villager;
        long day = Math.floorDiv(now, 24000);
        Daily old = daily.get(key);
        int count = old != null && old.day == day ? old.count : 0;
        int grant = Math.max(0, Math.min(amount, cap - count));
        if (grant > 0) {
            pruneDaily(day);
            daily.put(key, new Daily(day, count + grant)); setDirty();
        }
        return grant;
    }
    /**
     * Daily counter for work that has no player behind it (profession production), keyed by villager only.
     * The {@code v:} prefix keeps these keys disjoint from the player + villager keys above.
     */
    public int dailyVillagerGrant(String event, UUID villager, long now, int amount, int cap) {
        String key = "v:" + event + ":" + villager;
        long day = Math.floorDiv(now, 24000);
        Daily old = daily.get(key);
        int count = old != null && old.day == day ? old.count : 0;
        int grant = Math.max(0, Math.min(amount, cap - count));
        if (grant > 0) {
            pruneDaily(day);
            daily.put(key, new Daily(day, count + grant)); setDirty();
        }
        return grant;
    }
    public int golemKill(UUID player, UUID villager, long now) {
        String key = player + ":" + villager;
        List<Long> history = kills.computeIfAbsent(key, k -> new ArrayList<>());
        history.removeIf(t -> now - t >= WEEK);
        history.add(now); setDirty(); return history.size();
    }
    public String resolve(String id) {
        String next;
        while ((next = aliases.get(id)) != null) id = next;
        return id;
    }
    public String village(Collection<String> positions) {
        SortedSet<String> known = new TreeSet<>();
        for (String pos : positions) if (pois.containsKey(pos)) known.add(resolve(pois.get(pos)));
        String id = known.isEmpty() ? UUID.randomUUID().toString() : known.first();
        boolean merged = false;
        for (String other : known) if (!other.equals(id)) { aliases.put(other, id); merged = true; setDirty(); }
        if (merged) normalizeCooldowns();
        for (String pos : positions) if (!id.equals(pois.put(pos, id))) setDirty();
        return id;
    }
    /** Ready when the event's own window has elapsed since the last record for this player and village. */
    public boolean ready(String event, UUID player, String village, long now) {
        return ready(event, player, village, now, window(event));
    }
    /** Ready when {@code window} ticks have elapsed since the last record for this player and village. */
    public boolean ready(String event, UUID player, String village, long now, long window) {
        Long last = cooldowns.get(event + ":" + player + ":" + resolve(village));
        return last == null || now - last >= window;
    }
    public void cooldown(String event, UUID player, String village, long now) {
        cooldowns.put(event + ":" + player + ":" + resolve(village), now); setDirty();
    }
    /** Read-only allowance; unlike dailyGrant this never books a repair or cleans history. */
    public int dailyRemaining(String event, UUID player, UUID villager, long now, int cap) {
        Daily old = daily.get(event + ":" + player + ":" + villager);
        int count = old != null && old.day == Math.floorDiv(now, 24000) ? old.count : 0;
        return Math.max(0, cap - count);
    }
    /** Read-only production allowance; a failed insertion must not reserve this quota. */
    public int dailyVillagerRemaining(String event, UUID villager, long now, int cap) {
        Daily old = daily.get("v:" + event + ":" + villager);
        int count = old != null && old.day == Math.floorDiv(now, 24000) ? old.count : 0;
        return Math.max(0, cap - count);
    }
    /** Read-only cooldown across every known ID in a connected POI region, including pending merges. */
    public long remainingAt(String event, UUID player, Collection<String> positions, long now, long window) {
        Set<String> ids = new HashSet<>();
        for (String pos : positions) if (pois.containsKey(pos)) ids.add(resolve(pois.get(pos)));
        String prefix = event + ":" + player + ":";
        long last = Long.MIN_VALUE;
        for (String id : ids) {
            Long recorded = cooldowns.get(prefix + id);
            if (recorded != null) last = Math.max(last, recorded);
        }
        return last == Long.MIN_VALUE ? 0 : Math.max(0, window - (now - last));
    }
    /** Canonicalize once on load/merge, retaining the latest record when aliases collide. */
    private void normalizeCooldowns() {
        Map<String, Long> canonical = new HashMap<>();
        cooldowns.forEach((key, time) -> {
            int split = key.lastIndexOf(':');
            String resolved = split < 0 ? key : key.substring(0, split + 1) + resolve(key.substring(split + 1));
            canonical.merge(resolved, time, Math::max);
        });
        if (!canonical.equals(cooldowns)) {
            cooldowns.clear(); cooldowns.putAll(canonical); setDirty();
        }
    }
    private void pruneDaily(long day) {
        if (day <= lastDailyPrune) return;
        if (daily.entrySet().removeIf(e -> e.getValue().day < day - 1)) setDirty();
        lastDailyPrune = day;
    }
    /** Periodic maintenance; POI identities and aliases remain permanent. Future records are retained. */
    public void prune(long now) {
        boolean changed = cooldowns.entrySet().removeIf(e -> now >= e.getValue() && now - e.getValue() >= WEEK);
        for (var history : kills.values())
            if (history.removeIf(t -> now >= t && now - t >= WEEK)) changed = true;
        if (kills.entrySet().removeIf(e -> e.getValue().isEmpty())) changed = true;
        pruneDaily(Math.floorDiv(now, 24000));
        if (changed) setDirty();
    }
}
