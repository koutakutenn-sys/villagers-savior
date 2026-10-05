# Villagers' Savior

A Fabric mod for Minecraft Java Edition **26.2**: protect villagers, earn their trust, and ask them for food
from their real inventory when you are hungry.

[中文说明](README.zh-CN.md) · Full mechanics derivation: [Villagers_Savior_mechanics.md](Villagers_Savior_mechanics.md) (Chinese).

## Requirements

| Component | Version |
| --- | --- |
| Minecraft | 26.2 |
| Java | 25 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.161.0+26.2 or a compatible newer version |

Both the client and the server need this mod and Fabric API; for singleplayer, the client alone is enough.

## Building and installing

```sh
./gradlew build
```

The artifact is `build/libs/villagers-savior-1.0.3+mc26.2.jar` — copy it into the target instance's `mods/` directory.

> Use the jar produced by `./gradlew build`: that task writes the Fabric Loom metadata (mapping namespace,
> Mixin / Loader versions, and so on) and runs the full packaging pipeline, so it is not equivalent to a jar
> hand-assembled with `javac` + `zip`.

## Usage

Hold **V** and right-click a villager with an empty hand to request food. You can rebind the request key in
Options → Controls → Key Binds; once you release the key, right-clicking behaves like vanilla again.

Look at a villager within normal interaction range to automatically show their reputation, food offers
and profession service in a HUD panel. It lists quantities, material requirements and cooldowns; looking
away hides it. This replaces the G debug key. Viewing the panel does not consume items or activate services.

Nitwits collect any ground item that fits their hidden inventory, respecting pickup delays and designated
recipients. They do not produce items or take them from containers. **V + empty-hand right-click** returns
their entire inventory as drops reserved for you, regardless of reputation or hunger, with no cooldown.
The HUD previews all items they can return.

Clerics offer free Regeneration I at reputation ≥ 0 (200 ticks, 12000-tick village cooldown).
Negative reputation allows emergency treatment at health ≤ 6, or ≤ 2 when reputation ≤ -100
(100 ticks, 24000-tick cooldown). Each cast spends one reagent from the cleric's bounded work-restored stock.

### Reputation scans (debug)

Requires cheats or operator level 2. Reports each villager's real reputation toward the calling player.

```mcfunction
/villagerssavior debug nearby       # current dimension, spherical radius 64
/villagerssavior debug nearby 128   # radius 1–256
/villagerssavior debug village      # the connected POI village around the player
/villagerssavior debug list 2       # page 2 of the last result
```

The summary includes count, sum, mean, median, minimum, maximum and relationship distribution.
Details list UUID, coordinates, profession and reputation, sorted from lowest reputation with ten villagers
per page. Results expire after 30 seconds of game time; run another scan to refresh.

Village membership uses the connected POI definition below and the nearest village POI within 64 blocks
to avoid counting neighboring villages twice. Scans include loaded villagers and saved villagers in unloaded
entity chunks, including passengers. Saved rows use the last saved profession and Gossip snapshot and are
clearly labeled. Reads are asynchronous and do not load or generate world chunks. Oversized scans (more
than 4096 candidate chunks) and storage failures are rejected.

## Mechanics

Profession services (farmer travel rations, cooking, fletching, repairs, information) and the reputation
automatic villager panel are documented in [Villagers_Savior_professions.md](Villagers_Savior_professions.md) (Chinese).

All 13 standard professions create bounded amounts of their own resources after a successful workstation
restock, adding them directly to their hidden inventory without consuming external ingredients. Production
has per-restock and per-item daily limits, inventory targets and hard caps. Farmers also need spare village
beds and evidence of a crop type; unemployed villagers and nitwits do not produce resources. Librarians and
cartographers produce paper/books as listed in the profession tables, while their request services only
provide information. Gifts transfer existing stock, and processing services consume the villager's ingredients.
Failed production in a full inventory spends no daily quota; partial room books only the inserted quantity.
The limit remains per villager, so adding workers can still scale total production; there is no village-wide cap.

### Reputation

- **Killing villagers**: ordinary villagers retain vanilla penalties. Directly killing a nitwit additionally
  gives every living, currently loaded resident of its connected village `MAJOR_NEGATIVE +1` and
  `MINOR_NEGATIVE +10–20` (one inclusive random roll per death). Witnessing is not required for this extra
  penalty; other villages and unloaded residents are unaffected. Player-owned projectiles count, pets and
  environmental deaths do not. If no village is recognized, only vanilla penalties apply.
- **Killing hostile mobs**: every villager within 24 blocks of the death position gains `MINOR_POSITIVE` by
  threat weight — ordinary hostile mobs 1; creepers, endermen, evokers, vindicators and piglin brutes 2;
  ravagers, wardens, withers and the ender dragon 3. Each player + villager pair is capped at 5 points per day.
- **Repairing iron golems**: only counts when the golem **actually regains health**; every villager within
  32 blocks gains +1, capped at 3 points per day for each player + villager pair.
- **Building iron golems**: the player who places the carved pumpkin / jack o'lantern that creates an iron
  golem earns a reward — **carving a normal pumpkin in place with shears** counts as well — and every villager
  within 32 blocks gains +5. No reward is given while the villager already has more than 5 iron golems within
  64 blocks; this never reduces negative gossip. Spawns that cannot be attributed to a player (dispensers,
  commands) are not credited to nearby players.
- **Killing a village iron golem**: only golems that are **not player-created**, are inside a vanilla village
  when they die, and whose final damage source is a specific player are processed. Every villager within
  32 blocks loses up to 10 `MINOR_POSITIVE` (floor 0), and the n-th effective kill within 7 in-game days adds
  `MINOR_NEGATIVE = 10 + 5 × (n - 1)`; from the 6th kill on, each kill additionally adds `MAJOR_NEGATIVE +1`.
  Environmental deaths are never punished.
- **Killing a player-built iron golem**: every villager within 32 blocks loses 5 `MINOR_POSITIVE` (floor 0),
  and no `MINOR_NEGATIVE` or `MAJOR_NEGATIVE` is added. Vanilla only records whether a golem was player-built,
  not who built it, so this applies to any player-built golem killed by a player.
- **Winning a raid**: the actual participants recorded in vanilla's `heroesOfTheVillage` gain
  `MAJOR_POSITIVE +2` for every villager within 64 blocks of the raid center; the same player + village pair
  is rewarded only once every 7 in-game days.
- All rewards are written into real vanilla gossip, preserving vanilla caps, decay, spreading and trade-price
  behaviour. Increments such as `MINOR_POSITIVE +1` that fall below vanilla's discard threshold are corrected
  locally so that they actually take effect as designed.

### Requesting food

- The server validates the empty hand, that the entity is alive, the interaction distance, line of sight and
  any ongoing trade; ordinary food requests are limited to once every 20 ticks. Nitwit inventory handover bypasses this throttle.
- Effective reputation is `R = min(real reputation, 100)` with no artificial lower bound. The villager keeps
  `K = 20` nutrition points in reserve, so the surplus is `E = max(0, S - 20)`; it is willing to offer
  `B = floor(E × clamp(0.25 + 0.25 × R / 100, 0, 0.5))`; the player's need is
  `D = clamp(ceil((20 - H) × (0.25 + 0.003 × clamp(R, 0, 100))), 2, 8)`; and the final budget is
  `A = max(0, min(E, B, D))`.
- A bounded-knapsack search picks the item combination with the greatest nutrition that stays within the
  budget, deducts it from the villager's **real inventory**, and throws it toward the requesting player with
  that player set as the pickup target. The handout itself transfers existing stock; that stock may have
  come from vanilla pickups/harvesting or this mod's bounded profession production.
- **Emergency relief**: when hunger is below 6 and the normal gift cannot provide any food at all, reputation
  and the reserve are ignored and a single food item with the lowest nutrition is given (even if it is the
  villager's last one). Only food with **nutrition greater than 0** is considered — modded food with a
  nutrition value of 0 is skipped, so relief always restores at least one hunger point. The cooldown is shared
  per player + village for 48000 ticks (2 in-game days) and is only recorded after the item entity was
  actually spawned; a villager holding no usable food fails the relief without starting the cooldown.

## Villages and persistence

- Beds, workstations, bells and other POIs tagged with vanilla's `PoiTypeTags.VILLAGE` form a connected area
  when they are no more than 64 blocks apart; lookups start from the nearest POI within 64 blocks of the
  interaction or event position.
- POI areas keep a stable identity; when areas merge, the later cooldown records are preserved, and removing
  a registered POI or splitting an area never clears existing cooldowns. Abnormally large connected networks
  with more than 4096 POIs grant no rewards that depend on a village identity.
- Villagers that are not recognised as part of a village can still give normal gifts, but cannot use the
  village-scoped emergency relief.
- Daily caps are counted from the server's monotonic `gameTime / 24000`; sleeping or `/time set` cannot bypass
  them, and time does not pass while the server is down.
- History is stored in each dimension's world saved data `villagers_savior:history`
  (`<world>/data/villagers_savior/history.dat`) and contains daily rewards, the 7-day rolling kill history,
  cooldowns and village identities. It survives server restarts, and dimensions are independent of each other.
- Cooldown lookups use canonical village IDs; loading older history or merging villages preserves the latest
  timestamp. Every 1200 ticks per dimension, maintenance removes cooldown/kill records at least 7 game days
  old and empty kill keys. Daily counters keep the current and previous game day; POI IDs and aliases remain permanent.
- HUD topology snapshots are shared for 100 ticks, with at most 8192 POI keys per dimension. Real requests,
  death penalties and debug scans still inspect current topology. Empty farmers share nearby planted-crop
  evidence for 200 ticks (up to 256 villages per dimension); live inventory evidence always takes priority.
  The first crop scan still visits up to 38025 coordinates. Cache checks do not establish large-server TPS.

## Building and verification

```sh
./gradlew build                                            # compile and package the release jar
./gradlew runIntegrationTest -PacceptMinecraftEula=true    # requires accepting the Minecraft EULA
```

- You must accept the [Minecraft EULA](https://www.minecraft.net/eula) before running the test server. It uses
  the dedicated flat world in `run-test/`, binds only to the local address on a random port, and never touches
  a real save; the test mod is not packaged into the release jar.
- The automated checks cover: budget boundaries and knapsack optimality (including 2000 randomized
  brute-force comparisons), the death / repair / construction hooks, spherical ranges, daily caps, escalating
  rolling penalties, cooldowns and serialization, real inventory deduction, emergency relief and its shared
  cooldown, network request validation, raid victory, and a world-saved-data disk round trip
  (`PersistenceCheck`).
- Verification records:
  - Latest release-jar check (2026-10-05, logic and lookup costs): **40037 server assertions passed**, SHA-256
    `2a4a1991d85b44d1744b00a626649b77f268b33561076862670db01966fc0ab6`.
    The previous release failed 12 assertions in the new logic regression suite. The final run includes
    wheat rations, full/partial inventory production, bounded cache reuse/expiry, history maintenance and
    five additional real disk-reload checks. No GUI client, third-party combination or large-server TPS test
    was rerun. See [the review fixes audit](audit-reports/REVIEW-FIXES-AUDIT-2026-10-05.md).
  - Earlier release-jar check (2026-10-05, repair target priority): **39970 server assertions passed**,
    SHA-256 `26b1468ef0e47c7402dde0cc8b5a30ad74a55017688e9f21af141ffd34d0cccb`.
    Repairs and HUD previews now select the eligible equipment with the highest damage ratio.
    The previous jar failed 27 regression assertions; all passed with the fixed jar. Server logic and HUD
    payloads were tested; no GUI client or third-party combination was rerun.
    See [the repair priority audit](audit-reports/REPAIR-PRIORITY-AUDIT-2026-10-05.md).
  - Earlier Nitwit and cleric release-jar check (2026-10-05): **39925 server assertions passed**,
    SHA-256 `9b6378d5b5c1e0de47eb9549ec36ac96dacd91f23440dfe6f6cb1ddb673e1659`.
    This run covered server logic and HUD payloads; no GUI client or third-party combination was rerun.
    See [the final audit](audit-reports/CLERIC-AND-NITWIT-AUDIT-2026-10-05.md).
  - `./gradlew build`: passed (Gradle 9.5.1 + Fabric Loom 1.17.21, offline).
  - The local [build workflow](.github/workflows/build.yml) uses JDK 25 and runs `build integrationTestClasses`
    on push/PR. It compiles the integration suite without running a Minecraft server or accepting its EULA.
    Its build command passed locally; this new workflow has not yet run on GitHub.
  - `FoodRulesChecks`: passed, 35456 assertions.
  - `PersistenceCheck`: passed, 4 disk round-trip checks; a control run against the pre-fix compiled classes
    reproduces the "cooldown lost after reload" failure, confirming the check is effective.
  - The earlier scan-build dedicated-server suite passed via the isolated `hudServer` audit task: 39401 assertions,
    including 79 HUD inspection checks, 48 scan checks and all vanilla professions.
  - Native client automation passed 15 checks with the minimal setup and 16 checks with the installed
    mod combination using the release jar. Chinese HUD screenshots were inspected. One earlier direct
    shutdown hit a JVM native crash; the normal disconnect-and-exit rerun exited cleanly.
    See [the HUD audit](audit-reports/HUD-AUDIT-2026-10-04.md) for evidence and limits; long-term play feel is not covered.
  - The earlier 1.0.3 scan release jar plus the instance's other mods passed 51 native client checks, covering scan
    aggregates, individual professions/reputations, paging, permissions, cache expiry and HUD regression.
    Client and server exited with code 0. A separate rerun hit a JVM compiler-thread native crash with an
    unconfirmed root cause. See [the scan audit](audit-reports/SCAN-AUDIT-2026-10-04.md) for evidence and limits.

Audit reports and scripts linked above are local files ignored by Git; a fresh checkout may not contain them.

## Known limitations

- Kill credit only counts kills whose final damage source is a specific player: kills by player pets and
  environmental damage (lava, suffocation, falling, cacti, and so on) do not count.
- Registered POI positions accumulate in the world save, so the record grows slowly in very large,
  long-running worlds.
- Villages are identified through vanilla POIs; whether a structure that does not use vanilla village
  mechanics counts as one village depends on the vanilla `PoiTypeTags.VILLAGE` connectivity result.

## License

Released under **MIT OR Unlicense** — pick either one:

- [Unlicense](LICENSE) (public domain)
- [MIT](LICENSE-MIT)
