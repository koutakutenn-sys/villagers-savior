package dev.villagerssavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.*;

/** World-owned state: no global static player history; all timestamps use monotonic game ticks. */
public final class SaviorState extends SavedData {
    public static final long WEEK = 168000L;
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
    public static final SavedDataType<SaviorState> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("villagers_savior", "history"), SaviorState::new, CODEC, null);
    private final Map<String, Daily> daily;
    private final Map<String, List<Long>> kills;
    private final Map<String, Long> cooldowns;
    private final Map<String, String> pois;
    private final Map<String, String> aliases;
    public SaviorState() { this(Map.of(), Map.of(), Map.of(), Map.of(), Map.of()); }
    private SaviorState(Map<String, Daily> daily, Map<String, List<Long>> kills,
                        Map<String, Long> cooldowns, Map<String, String> pois, Map<String, String> aliases) {
        this.daily = new HashMap<>(daily); this.kills = new HashMap<>();
        kills.forEach((k,v) -> this.kills.put(k, new ArrayList<>(v)));
        this.cooldowns = new HashMap<>(cooldowns); this.pois = new HashMap<>(pois); this.aliases = new HashMap<>(aliases);
    }
    public static SaviorState get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public int dailyGrant(String event, UUID player, UUID villager, long now, int amount, int cap) {
        String key = event + ":" + player + ":" + villager;
        long day = Math.floorDiv(now, 24000);
        Daily old = daily.get(key);
        int count = old != null && old.day == day ? old.count : 0;
        int grant = Math.max(0, Math.min(amount, cap - count));
        if (grant > 0) {
            daily.entrySet().removeIf(e -> e.getValue().day < day - 1);
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
        for (String other : known) if (!other.equals(id)) { aliases.put(other, id); setDirty(); }
        for (String pos : positions) if (!id.equals(pois.put(pos, id))) setDirty();
        return id;
    }
    public boolean ready(String event, UUID player, String village, long now) {
        String suffix = ":" + player + ":";
        String prefix = event + suffix;
        long last = Long.MIN_VALUE;
        for (var entry : cooldowns.entrySet())
            if (entry.getKey().startsWith(prefix) && resolve(entry.getKey().substring(prefix.length())).equals(resolve(village)))
                last = Math.max(last, entry.getValue());
        return last == Long.MIN_VALUE || now - last >= WEEK;
    }
    public void cooldown(String event, UUID player, String village, long now) {
        cooldowns.put(event + ":" + player + ":" + resolve(village), now); setDirty();
    }
}
