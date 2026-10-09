# Brainage Minigames

A server-side mod for Minecraft 26.2 that runs minigames on an ordinary server: UHC and a set of kit duels, with any team layout and any number of matches at once. Clients do not need to install the mod.

[How to play every minigame](docs/HOW_TO_PLAY.md) — a quick player guide.

## Requirements

- Minecraft 26.2
- Either Fabric Loader 0.19.3 or newer with Fabric API, or NeoForge 26.2.0.88 or newer
  - Install the Fabric API only with the Fabric release.
- Java 25 or newer
- The `pvp` game rule must be `true`; vanilla blocks player hits before the mod sees them otherwise.

Install exactly one loader JAR and never both. A root `./gradlew build` emits both loader-specific artifacts.

## Games

| Game | Id | Rules |
| --- | --- | --- |
| UHC | `uhc` | Survival in a fresh region of the dedicated UHC dimension. Starter kit, 10-minute grace, no natural regeneration, items drop on elimination, and an always-noon sky. The default Hypixel-style border goes from 1000 to 100 blocks wide between 20:00 and 35:00; optional Badlion-style instant shrinks teleport outsiders onto the surface. At 40:00 survivors enter a generated circular deathmatch arena; at 50:00 remaining teams draw. Border style, deathmatch and always-day are configurable. Nether portals lead to the match's own nether until 20:00. |
| Speed UHC | `speed_uhc` | Hypixel's Speed UHC, about 10-15 minutes: survival in a 300-wide region of the UHC dimension, 2-minute grace, ores drop smelted and meat cooked, a broken log fells its tree, gravel and chickens also drop arrows and sugar cane drops a book and a sugar, brewing is instant. Each player's chosen kit (17), perks (17) and one Mastery (9). The border closes to 50 wide from 5:00 to 10:00; deathmatch at 11:00, draw at 16:00. Solo or teams of two. See [Speed UHC](#speed-uhc). |
| MiniUHC | `mini_uhc` | Badlion's MiniUHC: UHC on a 600-wide region with an 8-minute grace and instant shrinks to 400, 300, 200 and 100 wide at 15:00, 20:00, 25:00 and 30:00; no arena deathmatch, the fight ends inside the last border, draw at 45:00. UHC's kits, professions and crafts. See [MiniUHC](#miniuhc). |
| BuildUHC | `build_uhc` | Survival-mode kit fight with the BuildUHC kit (gear, lava, water, blocks); no natural regeneration. |
| Classic | `classic` | Iron gear, bow and rod. |
| No Debuff | `no_debuff` | Diamond gear, healing splash potions, speed potions and ender pearls. |
| Gapple | `gapple` | Protection IV diamond gear, 64 golden apples, strength and speed potions. |
| Boxing | `boxing` | Nobody takes damage; each landed hit scores a point and the first team to 100 hits wins. |
| Combo | `combo` | Players can be hit every 2 ticks (ten hits a second) instead of every 10, and attacks have no cooldown, so every swing is at full strength and combos land. |
| Bow | `bow` | Melee damage is blocked; projectiles and environmental damage remain enabled. |
| Sumo | `sumo` | Hypixel's and Minemen's Sumo: empty-handed on a small raised platform where hits only knock back. Dropping a block below the platform puts you out until the round ends; the last team on the platform wins the round, and the first to 3 rounds wins. 5-minute limit. See [Sumo](#sumo). |
| SkyWars | `skywars` | Hypixel's Insane SkyWars: glass cages above separate floating islands open after the countdown with each player's chosen kit (48 kits) and their perks on (31 perks); loot strong island and mid chests, knock each other into the void, last team standing wins. Chests refill at 3:00 and 5:00. See [SkyWars](#skywars). |
| Mini SkyWars | `skywars_mini` | Hypixel's Mini SkyWars: four players on small four-island maps, each with one of 11 Mini kits that carry their own perk, plus perks chosen into slots; Juggernaut for everyone. See [SkyWars modes](#skywars-modes). |
| Mega SkyWars | `skywars_mega` | Hypixel's Mega SkyWars Doubles: teams of two on a large map of twelve two-player islands, 16 maxed Mega kits, and perks chosen from 12 into seven perk slots (six while perks are not maxed). 15-minute limit. See [SkyWars modes](#skywars-modes). |
| Lucky Block SkyWars | `skywars_lucky` | Insane SkyWars with lucky blocks on every island and around the mid: breaking one gives a random item or event (22 outcomes). See [SkyWars modes](#skywars-modes). |
| Meetup | `meetup` | The end of a UHC: a random late-game kit (see [Meetup](#meetup)), PvP from the start, no natural regeneration, items drop on elimination, and a border around a patch of generated terrain that starts at 100 blocks across and closes in by 25 blocks every minute from 2:00 until it is 10 across. Last team standing; 15-minute limit. |
| FinalUHC | `final_uhc` | Minemen's Final UHC duel: identical late-game gear, building, lava and water allowed, no natural regeneration, on generated terrain inside a 100-block border. 15-minute limit. |
| Spleef | `spleef` | Dig out the snow floors under your opponents with an instant-breaking shovel; every block dug gives snowballs that break the floor where they land and knock players back. Nobody takes damage; falling below the lowest floor eliminates. See [Spleef](#spleef). |
| Bow Spleef | `bow_spleef` | Shoot the TNT floor out from under your opponents with a flame bow; arrows remove the TNT they hit without exploding it and never hurt players. Double jumps, triple shots and repulsors; falling into the void eliminates. |
| Bridge | `bridge` | Hypixel's The Bridge: jump into the other team's goal to score; every goal rebuilds the map and puts everyone back in their cages. Deaths only send you back to base. First to 5 goals; 15-minute limit. See [Bridge](#bridge). |
| Battle Rush | `battle_rush` | Minemen's Battle Rush: Bridge with only wool and shears and nothing linking the islands, so you rush across with wool and knock opponents off with your fists. First to 3 goals; 10-minute limit. |
| Capture the Wool | `capture_the_wool` | Hypixel's Capture the Wool: steal the other team's wools from the wool rooms at the back of its base and place them on your monument; the first team to fill its monument wins. Killed players respawn after 5 seconds, a killed carrier drops the wool, and only placed blocks break. 20-minute limit, decided by wool placed, then wool carried. See [Capture the Wool](#capture-the-wool). |
| Bed Wars | `bedwars` | Hypixel's Bed Wars: protect your bed, break the others' and be the last team standing. Solo (8 teams of 1), Doubles (8 of 2), 3v3v3v3, 4v4v4v4 and 4v4; island generators split between teammates, diamond and emerald generators that gain tiers, the Item Shop with its Quick Buy, Rotating Items, Tracker and Hotbar Manager, team upgrades and traps, bed destruction and Sudden Death dragons. See [Bed Wars](#bed-wars). |
| Bed Wars Castle | `bedwars_castle` | 40v40 Castle: two teams, three beds each, launch pads, the Banker and streak powers. See [Castle](#castle-bedwars_castle). |
| Bed Wars Dream modes | `bedwars_rush`, `bedwars_ultimate`, `bedwars_armed`, `bedwars_lucky`, `bedwars_voidless`, `bedwars_swappage`, `bedwars_one_block` | Bed Wars with Rush, Ultimate, Armed, Lucky Blocks, Voidless, Swappage or One Block rules. See [Dream modes](#dream-modes). |
| Quake | `quake` | Hypixel's Quakecraft: a railgun kills with one instant beam that walls stop, a feather dashes; killed players respawn at once away from their opponents. First to 25 kills (100 for teams); no melee or fall damage. |
| Pearl Fight | `pearl_fight` | Knockback stick, ender pearls, wool and shears on floating platforms; nobody takes health damage. Knocking an opponent into the void scores a point. Non-winning points reset the round in two-team matches; with more teams, only the fallen player respawns. First to 3 points. |
| Parkour | `parkour` | Hypixel's Parkour Duels: everyone runs the same course from one start line through every checkpoint in order; the first to finish wins. Falls send you back to your last checkpoint, nobody can hurt or push anyone, and a boost feather throws you forward on a cooldown. See [Parkour](#parkour). |
| Ice Boat Racing | `ice_boat_racing` | Every racer drives their own boat around an ice track through every checkpoint gate in order; the first to finish 3 laps wins. Leaving your boat gets you a new one at your last checkpoint. |
| Grinch Simulator | `grinch_simulator` | Hypixel's seasonal Arcade game as revised in 2020: everyone is a Grinch in a snowy village, stealing the presents hidden in, on and around its houses. Right-clicking a present steals it for a point; the most presents after 4 minutes wins. Presents are put at random spots every match, a village map shows where they are, and nobody can hurt or hinder anyone. See [Grinch Simulator](#grinch-simulator). |

The layout grammar accepts **any number of teams, each of any size**, e.g. `1v2` or `2v3v4`: these are examples, not a fixed list. Write sizes separated by `v` (2–100 teams, 1–100 players per team), or `ffa` for free-for-all. Map games enforce their available team/start slots, and `/duel` has its separate invitation limit.

Every game supports every team layout, except that Capture the Wool needs as many teams as one of its maps has (two on the bundled maps) and no free-for-all, and Bed Wars needs a map with a base for every team (eight on the bundled maps; two in Castle). Duels run in separate barrier-walled arenas in the void `brainage_minigames:minigames` dimension. UHC, Meetup and FinalUHC can also run concurrently, including multiple matches of each type. Each match owns its border and a separate generated region; dimension-global borders remain untouched. UHC uses `brainage_minigames:uhc` and `uhc_nether`, Meetup uses `meetup` and `meetup_nether`, and FinalUHC uses `final_uhc` and `final_uhc_nether`, all in the `brainage_minigames` namespace. Each pair has its own dimension types and clock, so UHC daylight does not change Meetup or FinalUHC daylight. The six natural dimensions are scheduled for regeneration when a match opens and regenerated after the server stops or before it starts, never while any of their regions are in use. A stopping server does not write their chunks, since it deletes them right after. UHC tries up to 16 unoccupied random regions and takes the one with the most land inside its starting border (ocean and river count as water), favouring a land biome at the centre; a region at least 85% land is taken at once. Choosing reads only the biome map, so it generates nothing. Reservations account for both surface and Nether widths, including a custom Nether divisor, so large regions cannot overlap. The lobby, original team starts and nether-close returns use the nearest solid, dry ground within 48 blocks, falling back to the water surface only if none exists. Instant Badlion teleports keep the exact five-block horizontal inset. Lobby players are brought back if they wander more than 16 blocks away.

UHC, Meetup and FinalUHC team starts are prepared with temporary chunk-loading tickets, at most four new chunk neighbourhoods per tick and eight still loading at a time; so is the lobby, the nearest dry ground to the region's centre, while the match is open, and the UHC deathmatch arena is pasted two loaded chunks per tick. Players stay in the lobby while the sidebar says **Preparing: spawn terrain**; the configured countdown and grace clock do not advance until every spawn and its immediate neighbouring chunks are fully loaded. UHC starts form an inward-facing ring with a 48-block dry-ground search and a water-surface fallback, with candidates kept inside the starting border. Terrain reads never synchronously generate the spread's chunks when a match opens, in the start command or in the countdown tick, so large lobbies do not stall one tick generating all their starts. Preparation retains its own tickets independently of the asynchronous load, including with replacement chunk systems such as Moonrise; completing a load cannot unload earlier teams' spawn neighbourhoods while other teams are still searching. Tickets are released after placement or if the match is stopped.

Every teleported player forgets the block they last stood on and finds their footing where they land. Vanilla keeps that block across a teleport and reads it again on the player's next tick in their new level, so moving players between dimensions (into a match's lobby, back to the hub, through a nether portal or with `/tp`) would make the server load, or generate, the chunk at the same coordinates in the new dimension on the server thread, stalling every player for seconds.

UHC, Meetup and FinalUHC expose optional **`region_seed`** through the usual per-game settings:

```mcfunction
/minigames settings uhc region_seed 942026
/minigames open uhc ffa
/minigames settings uhc region_seed reset
```

It is **unset by default**, retaining random regions and starts; explicit `0` is a valid seed.
Any signed 32-bit integer seeds the entire candidate search (including UHC's up-to-16
best-land search), independent spawn-ring rotation and unrequested team assignment. Joining
the same participants in the same order therefore replays their individual starts as well.
Settings are captured when each match opens, so changing/resetting the setting does not
change an already-open match.
With the same world seed, generation settings, starting border, team layout and unchanged
terrain, sequential same-seed matches choose the same centre and starting positions.
Close the previous match before replaying it: all three game types skip occupied regions.
This is terrain/start reproducibility, not deterministic bot or mob AI, kit rolls or gameplay.
`/minigames settings` marks absent overrides as `(unset)` so explicit zero can be distinguished
from the random default. Replace `uhc` with `meetup` or `final_uhc` to seed those arenas.

### UHC

#### Border modes, deathmatch and daylight

All sizes are **whole widths**, not distances from the centre. These world-persisted gamerules use vanilla-client-compatible boolean/integer types and affect only UHC gameplay:

```mcfunction
/gamerule brainage_minigames:uhc_border_style 0
/gamerule brainage_minigames:uhc_deathmatch true
/gamerule brainage_minigames:uhc_always_day true
/gamerule brainage_minigames:uhc_double_health true
```

- **Border style `0` (default, Hypixel):** 1000 wide until 20:00, then a continuous shrink to 100 at 35:00. That is **1 block/second across the whole width, 0.5 per side**. Changing the first/final times or starting/final widths changes the rate accordingly.
- **Border style `1` (Badlion):** instant widths of 750 at 20:00, 500 at 25:00, 250 at 30:00 and 100 at 35:00. Only outsiders move: each coordinate is clamped to the nearest point five blocks inside the new square, then placed on its surface. For a border centred on 0,0, `(450,450)` becomes `(370,370)` at the 750-wide shrink; players already inside, including underground players, stay put. The nether border defaults to 1/8 the surface width, configurable with `uhc_nether_border_scale`; an outside nether player returns at the equivalent surface point.
- **Deathmatch (default on):** all survivors keep their health and inventory and teleport into an original circular stone-and-grass arena, with 24 **open rim spawn pads** connected to the combat floor; there are no iron-bar rooms or sheltered template interiors. Teams are spread around the pads; with more than 24 teams, pads are shared evenly. Spectators come along. Movement, damage, building and item/container interaction are frozen for a 10-second action-bar countdown. Eight middle chests roll `brainage_minigames:uhc/deathmatch`: enchanted Sharpness/Protection/Power books, arrows, golden apples, flint, feathers, sticks, and rare diamond swords/armour. An anvil, crafting table and enchanting table sit in the middle. By default, at 45:00 the 113-wide arena border shrinks to 56 blocks wide in 60 seconds, and at 50:00 surviving teams draw **regardless of kill counts**. Border-width, scheduling and timeout overrides are listed below. Either the `uhc_deathmatch` gamerule or `deathmatch_enabled 0` disables the arena transition; the ordinary configured match time limit then applies.
- **Always-day (default on):** both UHC dimensions share the dedicated `brainage_minigames:uhc` clock and dedicated dimension types/timelines. That clock pauses at 6000 (noon). Meetup and FinalUHC each have an independent clock held at noon; vanilla Overworld, Nether and End clocks remain untouched. With the rule **off**, UHC holds **0 (vanilla dawn/wake-up time)** while all of its survival matches are in the lobby or countdown. Starting the first survival match releases the clock; opening another lobby does not reset or pause a running match's daylight. Concurrent UHCs share dimension time, not separate match times. At 20 ticks/second, a full Minecraft day is 24,000 ticks / 20 minutes; the default 10-minute grace of the first match advances 12,000 ticks from dawn to **12000 (sunset), still bright and before night at 13000**. Changing always-day during an active match takes effect immediately: enabling locks noon, disabling resumes from noon without reconnecting to vanilla time. Later concurrent starts and longer custom grace periods are not guaranteed daylight.
- **Double-health (default on):** participants start with 40 maximum health (20 hearts), filled at match start, as in Hypixel UHC. Its transient match-only bonus is removed on leave, disconnect or end before the saved player snapshot is restored; it is never written into a snapshot. The rule is independent of border style and captured at start, so changing it mid-match does not resize existing participants. Golden apples and golden-head healing amounts are not doubled. Client health scores report actual health up to 40, rounded up, rather than capping it at 20. Disable `uhc_double_health` for vanilla/Badlion's 20-health behaviour; selecting Badlion borders does not implicitly toggle health.
- Border style and deathmatch gamerule choices are captured when a match opens; the daylight toggle applies immediately. Optional behaviour is never forced on a match which disables it.

The deathmatch template is generated by `python3 tools/maps/uhc_deathmatch.py` into `structure/maps/uhc_deathmatch/colosseum.nbt`. It occupies a slot in the **`brainage_minigames:minigames`** void dimension, independently of other maps and duels. Like the other maps, it is pasted two chunks per tick while the match is open and cleared two chunks per tick after the match closes; its slot is reused only once it is clear. This dimension has fixed midday, clear weather and **no mob spawning**; existing mobs in the slot are removed at the transition. Its match-local border shrinks independently without changing the dimension's global border or any other slot. Survivors, including those still in the UHC Nether, and spectators transfer together; saved snapshots restore everyone's original dimension, position and state when they leave or the match closes. The starting 10-minute Fire Resistance hides its particles while keeping its HUD icon; other effects retain their normal particle behaviour.

The combat floor is grass with packed-mud paths along two rings and from each pad to the dais, and patches of moss and coarse dirt; each pad has a polished floor and a coloured banner on the wall behind it, and lantern pillars stand between the pads in front of the crenellated rim wall. Cover stands between the pads' paths in three rings: short broken walls two blocks high near the dais, eight lantern-topped pillars and four low mounds (one block up, then a second step) in the middle ring, and longer broken walls further out. Every piece stands alone with open ground around it, so nothing encloses a player. Blocks can be placed only up to five blocks above the floor, so nobody can tower onto the rim wall and the barrier ring above it. A player `UhcArena.moveToSurface` puts back inside the deathmatch border lands on the lowest spot at their position with ground under them and room to stand, on top of any cover there, and only falls back to the floor height if the column has no such spot.

`/gamerule brainage_minigames:pre_pvp_following true` is a shared, world-persisted boolean bot policy, **off by default**. With it off, SparringBots must not follow or stalk opponents before that match's PvP begins (including the UHC lobby/countdown and grace); enabling opts into that behaviour. Brainage Minigames registers the exact id `brainage_minigames:pre_pvp_following`; the bot mod reads it by name and implements the movement policy. This is a gamerule rather than a per-match setting so both mods use the same toggle.

All match sidebars use the player-facing mode label **FFA** (for example **UHC FFA**); command syntax remains `ffa`, and numeric layouts remain `1v1`, `2v3v4`, etc. Their bottom line is a compact, grey **`MM/dd/yy HH:mm:ss`** server-local wall-clock timestamp, using a 24-hour time (for example `10/04/26 14:05:09`). The timestamp reserves one of the client's 15 sidebar lines, refreshes on the next regular sidebar update after a second changes, and is not the Minecraft world clock.

Entering the arena also advances the regular-UHC progression timeline: carried Apprentice Swords become Sharpness III and Apprentice Bows become Power III immediately, without replacing the survivor's inventory or resetting health. See the [progression catalog](docs/UHC_PROGRESSION.md) for their earlier upgrades.

| Setting | Default | Meaning |
| --- | --- | --- |
| `border_start_size` | 1000 | Starting whole width |
| `first_shrink_minutes` / `first_shrink_size` | 20 / 750 | Continuous shrink starts; first Badlion width |
| `second_shrink_minutes` / `second_shrink_size` | 25 / 500 | Second Badlion step |
| `third_shrink_minutes` / `third_shrink_size` | 30 / 250 | Third Badlion step |
| `final_shrink_minutes` / `final_shrink_size` | 35 / 100 | Continuous shrink finishes; final Badlion step |
| `nether_close_minutes` | 20 | Close portals and return nether players; 0 disables the nether |
| `deathmatch_enabled` | 1 | Per-match toggle, additionally gated by the gamerule |
| `deathmatch_minutes` / `deathmatch_duration_minutes` | 40 / 10 | Arena teleport time / duration including countdown |
| `deathmatch_shrink_minutes` / `deathmatch_shrink_seconds` | 5 / 60 | Offset into deathmatch / shrink duration |
| `time_limit_minutes` | 50 | Ordinary match limit; 0 disables that limit, not the deathmatch deadline |

The obsolete `shrink_duration_minutes` setting is removed: continuous duration is exactly the gap between first and final shrink times. Badlion widths/times must be strictly decreasing/increasing, and the deathmatch shrink must finish before its deadline. An enabled deathmatch's own deadline takes priority over the ordinary match time limit.

**Research and adaptations:** [Badlion's official UHC 3.0 patch notes](https://www.badlion.net/forum/thread/47369) describe edge teleports on larger shrinks, random scattering on the 500/100 shrinks, and closing the nether at 500. [Its UHC 6.0 announcement](https://www.badlion.net/forum/thread/187558) documents clock-driven scheduling and a changed first-shrink time. This mod deliberately uses a shorter schedule, five-block nearest-edge teleports at **every** instant shrink instead of the historical final random scatter, and a configurable nether close defaulting to the first shrink. Exact historical warning intervals could not be established from those primary notes or the reviewed [2016 Danteh Badlion footage](https://www.youtube.com/watch?v=z9IJF-AKVlk); the mod provides advance warnings at 5/1 minutes, 30/10 seconds and each of the final five seconds rather than presenting an unverified cadence as historical fact.

[Hypixel's official 2016 update](https://hypixel.net/threads/uhc-solo-mode-and-balancing-update.741385/) documents multiple deathmatch arenas and barrier anti-exploit boundaries. The [UHC wiki](https://hypixel.fandom.com/wiki/UHC_Champions) describes individual starting areas and a rush to central resource chests; its 15-minute deathmatch and kill-count tiebreak are available through the Hypixel preset, while the default remains a 10-minute draw. [Scotteh's “THE PERFECT HYPIXEL UHC” (2020)](https://www.youtube.com/watch?v=YYy9HxmW_C8&t=705s), especially 11:50–12:00, shows the frozen arrival/grace period, open rim entrance and stone/grass arena with a central enchanting area. The generated arena is an original interpretation, not a copied Hypixel map.

#### UHC rule overrides and presets

All rule names below use the `brainage_minigames:` namespace.

| Gamerule | Default | Behaviour |
| --- | --- | --- |
| `uhc_max_all_perks` | `true` | Treat every profession, profession prestige and Extra recipe as owned, independently of max-kits; `false` uses purchases |
| `uhc_max_all_kits` | `true` | Treat every selectable kit as tier III and prestiged, independently of max-perks; `false` uses purchases |
| `uhc_choose_prestige_bonus` | `false` | Use the saved per-UUID kit prestige choice instead of a weighted roll |
| `uhc_coin_multiplier` | `100` | Integer percentage applied to every coin award; round down per award |
| `uhc_uncapped_coin_awards` | `true` | No per-match cap on profession-craft, ore-mining or hostile-mob coin awards; `false` caps their base coins at 50, 60 and 30 per player respectively |
| `uhc_deathmatch_border_start` | `113` | Starting deathmatch whole width |
| `uhc_deathmatch_border_final` | `56` | Whole width after the deathmatch shrink; must be below the starting width |
| `uhc_nether_border_scale` | `8` | Divide the survival border width by this integer in the Nether; `1` gives equal widths. Portal coordinates and the border centre still use vanilla 1:8 scaling |
| `uhc_deathmatch_after_grace_minutes` | `0` | `0` uses the per-match `deathmatch_minutes`; a positive value counts from grace ending |
| `uhc_deathmatch_skip_players` | `0` | `0` disables the player-count skip; otherwise, reaching this many remaining players after grace shortens a longer countdown |
| `uhc_deathmatch_skip_minutes` | `10` | Remaining minutes after the player-count skip; never lengthens a shorter countdown |
| `uhc_deathmatch_duration_minutes` | `0` | `0` uses the per-match duration; positive values override it |
| `uhc_timeout_most_kills` | `false` | On: the surviving team/player with most opponent kills wins at timeout; tied leaders draw. Off: all surviving teams draw |
| `uhc_combat_logger` | `true` | Keep a disconnected UHC/Meetup/FinalUHC participant alive as an attackable zombie |

Deathmatch scheduling and timeout rules are captured at match start. Border widths and Nether divisor are captured when the match opens. An enabled deathmatch runs to its own deadline rather than being cut short by the ordinary match time limit. The default deathmatch runs from 40:00 through 50:00. The final width is 56 whole blocks.

Operators can apply `/minigames uhc preset hypixel` or `/minigames uhc preset badlion`. This writes the rule bundle in one command; any individual `/gamerule` can still be changed afterwards, and other UHC rules/settings are left unchanged. These are supported-mode adaptations, not exact recreations of historical servers.

| Rule | Hypixel preset | Badlion preset | Source / adaptation |
| --- | --- | --- | --- |
| `uhc_border_style` | `0` continuous | `1` instant | [Hypixel UHC wiki](https://hypixel.fandom.com/wiki/UHC_Champions) describes a closing border; [Badlion UHC 3.0](https://www.badlion.net/forum/thread/47369) describes instant shrinks. The existing 20–35-minute widths are this mod's shorter schedule, not a historical timing claim |
| `uhc_double_health` | `true` | `false` | Hypixel wiki: two health rows. Badlion preset uses vanilla maximum health as a local adaptation |
| `uhc_deathmatch` | `true` | `true` | Hypixel wiki; [Badlion UHC 6.0 announcement](https://www.badlion.net/forum/thread/187558) confirms a later arena deathmatch |
| `uhc_deathmatch_after_grace_minutes` | `35` | `0` (per-match 40:00 default) | Hypixel wiki: 35-minute countdown after grace. Badlion timing is the existing local schedule; 6.0 instead describes ten minutes after the 25×25 shrink |
| `uhc_deathmatch_skip_players` | `15` | `0` | Hypixel wiki: skip once 15 remain. No Badlion threshold is claimed; skip disabled locally |
| `uhc_deathmatch_skip_minutes` | `10` | `10` (unused while skip disabled) | Hypixel wiki: remaining countdown becomes ten minutes |
| `uhc_deathmatch_duration_minutes` | `15` | `0` (per-match ten-minute default) | Hypixel wiki: fifteen-minute arena. Badlion uses the existing local duration; 6.0's random damage after fifteen minutes is not emulated |
| `uhc_timeout_most_kills` | `true` | `false` | Hypixel wiki: most kills wins at timeout. Draw is the local Badlion adaptation, not a historical-server assertion |
| `uhc_combat_logger` | `true` | `false` | Hypixel wiki describes reconnectable zombies. Badlion preset keeps immediate disconnect elimination as local policy; its historical logger details are not confirmed here |

The Nether-width divisor stays at its current value under either preset: Hypixel's exact Nether scaling is not confirmed by the cited wiki.

#### UHC scenarios

Optional scenarios borrowed from Badlion and Hoplite apply to **every UHC-style game**: UHC and its variants, Meetup and FinalUHC (any match whose arena is a UHC region or Meetup/FinalUHC terrain, so they follow players into the Nether and the deathmatch arena). Each is a world-persisted gamerule in the `brainage_minigames:` namespace, **off by default**, read when it takes effect (a kill, a death, a command), so changing one mid-match affects what happens next.

| Rule | Default | Effect |
| --- | --- | --- |
| `uhc_cutclean` | `false` | Ores drop their smelted form: iron, gold and copper ingots, and netherite scrap from ancient debris; the drop percentages then multiply the smelted items. Silk Touch ore keeps its block. Gravel always drops flint. Raw meat and fish from mobs come cooked, and a cow, mooshroom, pig or chicken always drops at least 3 meat, a chicken a feather and a cow or mooshroom a leather |
| `uhc_timber` | `false` | Breaking a natural log also breaks every log of the same kind connected to it, diagonals included, up to 256 logs: each log costs the tool's durability, and a tool that breaks stops the fall. Logs placed by players never fall and never start a fall. Every log drops at the broken one |
| `uhc_vein_miner` | `false` | Mining an ore with a tool that harvests it also mines the connected ore of the same resource (normal and deepslate together, diagonals included), up to 64 blocks: each block costs durability and rolls its own loot with Fortune and the drop percentages, and a tool that breaks stops the vein. Placed ore is never mined with a vein. Drops and experience land at the mined block |
| `uhc_hastey_boys` | `false` | Every mining tool (pickaxe, axe, shovel, hoe, shears) a participant has, crafted, from a kit, looted or picked up, gets Efficiency III and Unbreaking III; higher levels stay. Tools with a fixed number of uses (Philosopher's Pickaxe, Lumberjack's Axe) keep their uses |
| `uhc_blood_diamonds` | `false` | Mining a diamond ore with a tool that harvests it costs half a heart of true damage: armour, Protection, Resistance and damage immunity do not reduce it, absorption goes first, and it can kill. With Vein Miner every diamond ore in the vein costs half a heart |
| `uhc_diamondless` | `false` | Diamond ore drops nothing, with Silk Touch or explosions too; its experience still drops. An eliminated participant drops a diamond |
| `uhc_goldless` | `false` | Gold ore, nether gold included, drops nothing. An eliminated participant drops 8 gold ingots and a golden head |
| `uhc_time_bomb_seconds` | `0` (off) | A dead player's items go into a double chest at the death spot with a golden head; it explodes this many seconds later (Badlion used `30`) |
| `uhc_no_clean_seconds` | `0` (off) | After a kill, players cannot hurt the killer for this many seconds (Hoplite uses `30`); attacking a player ends it early |
| `uhc_safeloot_seconds` | `0` (off) | Only the killer's team can pick up a kill's drops or open its Time Bomb chest for this many seconds |
| `uhc_team_backpack` | `false` | `/backpack` (`/bp`) opens a 27-slot inventory shared by the team; it drops where the team's last member dies. Solo players have none |
| `uhc_second_chance` | `false` | A player who dies before PvP is enabled comes back once, at a random spot inside the border, keeping their items |
| `meetup_adaptive_border` | `false` | Meetup's border shrinks at a player count or a time, whichever comes first, then survivors take random damage (see [Meetup](#meetup)) |

- **Where they apply:** the block and mob rules (CutClean, Timber, Vein Miner, Blood Diamonds and the Diamondless/Goldless ores) work in the UHC-style dimensions, like the [resource gamerules](#uhc-resource-gamerules), including other games played there. Hastey Boys and the kill rewards follow the participants of UHC-style matches, the deathmatch arena included.
- **Kill rewards** are added to the eliminated player's own items, so they drop with the rest of the inventory, or go into the anti-janitor (or Time Bomb) chest. FinalUHC keeps inventories, so there they drop on the ground. Diamondless and Goldless together give all three.
- **CutClean and profession crafts:** with CutClean on, Quick Pick and the Philosopher's Pickaxe also take iron and gold ingots in place of ore. Iron Economy and Gold Pack keep needing ore or raw ore (from Silk Touch, chests or raw-ore blocks), and Food Economy raw beef: CutClean already does what they do, and taking ingots or cooked beef would let them multiply ingots and steak without end. Apples are left to `uhc_apple_drop_percent`, whose default of 200 already gives Badlion CutClean's 1% chance.
- **Timber and Vein Miner** break their blocks through the normal player break, so match rules, protected chests, statistics, coins, Blood Diamonds and other scenarios apply to each block. The search for connected blocks is breadth-first and iterative, reads only chunks that are already loaded (a vein or tree never loads one) and stops at its limit, so a break never stalls the tick: in a 256-log block it breaks exactly 256.
- **Diamondless and Goldless** empty the ores' drops rather than generating no ore, so the map, its experience, Blood Diamonds and the generation percentages stay independent of them.
- **Time Bomb:** the chest is placed like the [anti-janitor](#anti-janitor-protection) death chest (the death's block, its second half to the east, searching upward past other containers) and holds the victim's inventory, armour and offhand, then a golden head (Bloodcraft's, edible in any UHC-style match), the UHC player head and Hunter gold nuggets, and the team backpack if they were its last member; anything beyond 54 stacks drops beside it. It applies in FinalUHC too, whose eliminated players otherwise leave nothing. A floating countdown above the chest and a chat line with its coordinates tell everyone in the match. At zero, whatever is left inside is destroyed, the chest is removed and it explodes as strongly as TNT: players and mobs nearby, the killer included, are hurt and knocked back. The blast breaks only blocks players may break at that spot: natural terrain and placed blocks inside the match border, and in the deathmatch arena only its build area. Nothing beyond the border and none of the arena's rim, walls or barriers is touched, since a UHC's natural terrain is the only ground players may dig. An unclaimed chest can be broken early, spilling its items, and still explodes; bombs that have not gone off when the match ends are removed with what is in them. When an anti-janitor fight claims the loot, the Time Bomb chest replaces the anti-janitor chest and keeps its claim.
- **No Clean:** starts when a player is credited with killing an opponent. While it lasts, damage from players (melee or projectiles) to the killer is refused; mobs, falls, lava and the border still hurt them. Any attack the killer makes on an opponent, even a blocked one, ends it at once. An action-bar countdown shows the seconds left, and the killer is told when it ends.
- **Safeloot:** a kill (not a death to mobs or the environment) claims everything dropped at the death spot in that tick, including the victim's items, head, Hunter nuggets and a dropped backpack, and the Time Bomb or anti-janitor chest. Until the claim runs out only the killer's team can pick the items up or open the chest; hoppers cannot collect the items, and a claimed chest cannot be broken, blown up or emptied by a hopper. With anti-janitor also claiming the chest, the later deadline applies and the killer's team holds it.
- **Team backpacks:** only alive players of a team that started with at least two members can open it, during the active match. Teammates see the same 27 slots at once. When the team's last member dies or forfeits, its contents drop at their feet (or go into their Time Bomb chest); a team with members still alive keeps it.
- **Second Chance:** as in Hoplite, which brings back anyone who dies before its mining phase, a participant who dies (in any way, in the overworld or the Nether) while the match's grace period still runs comes back once per match: at a random dry spot inside the surface border, preferring loaded chunks so nothing generates, with full health and food and their inventory, effects and experience kept, since they cannot have been killed by a player yet. The next death is final. Meetup and FinalUHC start with PvP on, so it never applies there; a combat logger killed while its player is away is not brought back.

**Sources:** Badlion's forum guide [All UHC Game Modes Explained](https://www.badlion.net/forum/thread/88227/post/448354) (2016, player-written) defines CutClean (pre-smelted ores, pre-cooked food, flint from every gravel, 3 food from cows, chickens and pigs, a feather from every chicken and a leather from every cow), Diamondless (diamond ore gives only experience; a dead player drops 1 diamond), Goldless (a dead player drops a golden head and 8 gold ingots) and Blood Diamonds (half a heart per mined diamond). Hoplite's [May 22, 2026 patch notes](https://www.hoplite.gg/news/patch-notes-may-22nd-2026) define Hastey Boys (all tools get Efficiency III and Unbreaking III), Timber (breaking a log breaks all attached logs) and Vein Miner (mining an ore mines its whole vein). Local choices: netherite scrap from debris, Silk Touch keeping blocks, mooshrooms counting as cows, the 256-log and 64-ore limits, same-kind logs, drops at the broken block, and tools with fixed uses left alone.

[Badlion's scenario guide](https://www.badlion.net/forum/thread/88227/post/448354) describes Time Bomb's 30-second double chest with a golden head and shared backpacks; [Hoplite's May 2026 patch notes](https://www.hoplite.gg/news/patch-notes-may-22nd-2026) define No Clean (30 seconds, ended by attacking), Safeloot (no published duration, so this mod has none by default) and 27-slot `/backpack` inventories, and [its November 2025 notes](https://www.hoplite.gg/news/patch-notes-nov-29th-2025) Second Chance. The blast strength, Safeloot's scope and Second Chance's respawn spot and kept inventory are this mod's choices.

### Optional 1.8-style combat

```mcfunction
/gamerule brainage_minigames:combat_1_8 true
```

This world-persisted boolean defaults to **false**. It applies combat balance to **alive, active minigame participants**: lobbies, spectators, finished matches and ordinary survival players keep modern combat. Damage from participants also uses legacy armour and enchantment reduction on their targets. The enchanted-golden-apple crafting recipe is world-rule-gated, including automated recipe consumers. Registry definitions are not replaced; all changes are implemented in code.

When enabled:

- Every melee swing has full attack strength, including several attack packets in the same tick and immediately after changing weapons. This removes attack-charge damage scaling and the server's item minimum-charge check, **not** the victim's hit immunity.
- Normal full hits still have the authentic **10-tick** delay: immunity counts down from 20 and full hits resume at 10. A stronger hit inside that window applies only the excess over the previous hit, without restarting the window or repeating base knockback. Minecraft 26.2 already implements this 1.8 behavior; the rule deliberately does not reset immunity on every click.
- Snowballs and eggs hit players for **zero damage** but cause knockback and start the same immunity window. Rod impacts likewise knock back and hook a player only if the zero-damage hit is accepted. Direction is away from the thrower, as in 1.8, not the projectile's flight direction.
- Retracting a rod from a player **pulls them**, including with `combat_1_8` enabled, as unmodified 1.8 did. The server pull and the vanilla client pull event both remain active. Fishing, hooked items and other entities otherwise remain vanilla.
- Base knockback halves existing motion and adds 0.4 horizontal/upward impulse, capped at 0.4 upward, **also while airborne**. Knockback resistance is the legacy probability of resisting a base hit. Sprint/enchantment knockback adds to that impulse rather than halving it again; sprint-hit slowdown/reset remains, allowing W-tapping.
- Sword sweeping is disabled, including Sweeping Edge. Falling critical hits may happen while sprinting; the usual water, ladder, blindness, riding and grounded exclusions remain.
- **Sword blocking:** right-click a main-hand sword for immediate, all-direction blocking. Eligible damage becomes **`(damage + 1) / 2` before armour**, after the raw hit-immunity comparison. Armour-bypassing damage is not blockable. Blocking neither damages the sword nor triggers shield reactions or axe disable. No substitute shield is issued and the offhand is not locked: food, pearls and ordinary inventory transfers remain available; modern shields cannot be used by legacy participants.

Game-specific rules always take priority: UHC grace, Bridge cages, teammate/PvP protection and noncombat games cannot be bypassed by a snowball or rod. **Combo** retains its configurable `hit_delay_ticks` (2 by default) and always-full-strength attacks even with the gamerule off. **Boxing** still prevents health damage and scores only accepted opposing melee hits, not snowballs, eggs or rods.

#### Balance values

Damage is in health points, including the player's base point, before armour:

| Weapon | Wood / gold | Stone | Iron | Diamond |
|---|---:|---:|---:|---:|
| Sword | 5 | 6 | 7 | 8 |
| Axe | 4 | 5 | 6 | 7 |
| Shovel | 2 | 3 | 4 | 5 |

Custom kit attribute modifiers remain intact; the old tiers receive their verified offset rather than a replacement final damage value. Modern-only weapon tiers retain their native contribution.

- Sharpness adds **1.25 per level**; Smite/Bane retain **2.5 per level** against their eligible targets. Bane applies Slowness IV for **`20 + nextInt(10 × level)` ticks**. Critical hits multiply attack-attribute damage by **1.5**, then add enchantment damage.
- Armour reduces damage by **4% per point, capped at 80%**, regardless of toughness or hit size. Protection EPF is `floor((6 + level²) × modifier / 3)`, with modifiers **0.75 / 1.25 / 1.5 / 1.5 / 2.5** for general/fire/blast/projectile/fall protection. All applicable pieces share one roll: cap the sum at 25, roll `ceil(sum/2) + nextInt(floor(sum/2)+1)`, cap at 20, then reduce damage by 4% per rolled point. Resistance and protection apply after armour.
- Fire and Blast Protection side effects use the **highest equipped level**, not a sum: subtract `floor(duration/impulse × level × 0.15)`. Unrelated attribute modifiers remain effective. Armour wear retains `max(1, floor(incoming damage/4))`; held/worn items break only when damage **exceeds** maximum durability.
- Strength multiplies attack-attribute damage by **`1 + 1.3 × level`**; Weakness subtracts **0.5 per level before Strength**. This also takes precedence over UHC's modern-mode Strength adjustment.
- Bow draw retains the vanilla/legacy 20-tick curve and `3 × charge` speed. Bow-arrow spread is independent Gaussian noise **0.0075 × inaccuracy per axis**, without inherited shooter velocity. Arrow damage is `ceil(speed × coefficient)` with base coefficient 2 and Power's **`0.5 × (level+1)`** addition; full draw retains critical-arrow randomness. Punch adds **0.6 per level horizontally and 0.1 vertically**, without resistance scaling. Fire Aspect retains four seconds per level plus its one-second tentative pre-hit ignition, undone if the attack fails.
- Ordinary golden apples retain Regeneration II **100 ticks** and Absorption I **2400 ticks**. Enchanted apples give Regeneration V **600 ticks**, Absorption I **2400 ticks**, and Resistance/Fire Resistance I **6000 ticks**; an apple surrounded by eight gold blocks crafts one while the rule is on. Existing stacks choose effects when consumed. Golden heads retain the project's **Regeneration II 200 ticks, Absorption I 2400 ticks, food 4, saturation 9.6**, under either rule.
- Instant Health/Harming retain **4/6 × 2^(level−1)** health points and undead reversal. Regeneration potions retain I **900 ticks**, II **450 ticks**, and use extended I **2400 ticks** (the 1.8.1–1.8.9 value); healing intervals remain **50/25/3 ticks** for I/II/V. Splashes use rounded-up **75%** non-instant duration, entity-position distance falloff `1 − distance/4`, and full splash strength for a direct hit; instant effects do not receive the 75% penalty.
- Exhaustion is **0.2 jump, 0.8 sprint-jump, 0.3 successful attack, 0.3 ordinary damage, 0.01/metre walking or sneaking, 0.015/metre swimming, 0.1/metre sprinting, 0.025 block break**. Damage sources with no exhaustion remain unchanged. Natural regeneration heals **one point per 80 ticks at food ≥18**, adding **3 exhaustion**; fast saturation healing is disabled. Match and world natural-regeneration restrictions still apply.
- Pearls apply **no new cooldown**. Lava/fire retain raw **4/1** damage and burning's **one point every 20 ticks**; lava ignites for **300 ticks**. Burning attempts also occur while in lava, subject to ordinary immunity and Fire Resistance.

#### Switching boundary

Damage, enchantments, Strength/Weakness lookups, projectile impacts and exhaustion read the rule at the next event. Bow spread and shooter-motion inheritance are decided at **launch**: toggling does not rewrite an already-flying arrow's trajectory. Existing active effects retain their remaining duration and amplifier; future potion/food application uses the current rule, while active Strength/Weakness calculations change immediately. Existing cooldowns expire normally; future pearl uses omit cooldown only while enabled. Attack and hurt-resistance timers are not reset. Sword-use components and open crafting previews synchronize within one player/match tick; server damage/input checks and stale-output guards apply immediately, including shift-click.

No client mod or datapack is required. The modern client renders its native blocking pose for a sword carrying the synchronized use component; attack indicators, click packet rate, camera bobbing and other visuals remain client-version behavior.

Shared GameTests on both loaders assert the verified numeric values and disabled-rule controls through native attacks, enchantment/effect application, crafting, projectiles, exhaustion, consumables and durability. Reference mechanics: [1.8.9 player attacks](https://github.com/Marcelektro/MCP-919/blob/main/src/minecraft/net/minecraft/entity/player/EntityPlayer.java), [living damage](https://github.com/Marcelektro/MCP-919/blob/main/src/minecraft/net/minecraft/entity/EntityLivingBase.java), and [enchantment protection](https://github.com/Marcelektro/MCP-919/blob/main/src/minecraft/net/minecraft/enchantment/EnchantmentProtection.java).

#### Optional integration API

`io.github.brainage04.brainage_minigames.game.CombatRules` exposes public static, reflection-readable accessors for optional integrations:

- `legacyBalance(ServerLevel)` reads the rule; `classic(Entity)` additionally checks participant/spectator scope.
- `weaponDamage(ServerLevel, ItemStack, double vanillaUnbuffedTotal)` returns the rule-selected unbuffed total, preserving custom modifiers.
- `armorReduction(float)`, `protectionPoints(int, double)`, `strengthMultiplier(int)`, `instantHealing(int)`, `instantHarming(int)` and `regenerationInterval(int)` expose the legacy formulas above (fractions, health points and ticks respectively).

For an actual legacy participant, `getAttributeValue(Attributes.ATTACK_DAMAGE)` already includes the weapon adjustment and Strength/Weakness; integrations must not apply them twice. The weapon accessor's input is an **unbuffed** total including the player's base damage, not that already-adjusted attribute lookup.


### The UHC nether

Nether portals lit in the UHC dimension lead to `brainage_minigames:uhc_nether`, a vanilla-generated nether, and portals there lead back; exits are found or built as vanilla does, at an eighth of the coordinates (and eight times them on the way back), constrained by the traveller's match-local border. Meetup and FinalUHC have their own linked Nether dimensions, but match participants cannot enter them. Portals outside these three dimension pairs behave as in vanilla. Each Nether is regenerated with its paired natural dimension.

Players in a UHC's nether are still in the match: they stay alive and keep their health in the tab list, dying there eliminates them, and leaving, the match ending or being stopped returns them to wherever they joined from. The match's border width is divided by `uhc_nether_border_scale` (default **8**), about the centre scaled by vanilla 1:8 coordinates. By default a 1000-block surface border is 125 blocks across in the nether; divisor `1` makes the widths equal without changing portal coordinates or the centre. Each match's nether border shrinks with its surface border and hurts outsiders independently. Closing or ending one match never changes another match's portals or borders.

At `nether_close_minutes` (default 20:00; `0` disables the nether) portals stop leading into the nether and everyone still there returns to the surface at matching coordinates, pulled inside the border and its target size. Entering deathmatch also closes the nether and moves anyone still there directly into the arena.

UHC chat announces the selected border schedule, PvP grace, nether close and deathmatch duration at the start. "PvP is now enabled!" marks the moment PvP comes on: at the end of the grace period, or at the start of a UHC with no grace period and of every Meetup and FinalUHC match. Before each relevant border event and the deathmatch teleport it gives advance warnings; instant-shrink warnings name the new width and explain the surface teleport. The nether gives a one-minute warning and a closure announcement. Deathmatch has its ten-second frozen countdown and a one-minute warning before its shrink to the configured final width. The sidebar shows the current border and next event, nether status and survivors; during deathmatch it shows its own remaining duration and countdown/shrink timer.

Spectators follow players into the nether: `/minigames watch <match>` works for a client that is already spectating and while the match runs, `/spectate <player>` (and the spectator menu's teleport) reach a player in either dimension, and a spectator watching a player who goes through a portal, or is brought back when the nether closes, is taken along and keeps watching them once that player has reached their client (after at most five seconds).

#### UHC disconnects and End portals

UHC, Meetup and FinalUHC use **`uhc_combat_logger`** by default. A disconnected alive participant becomes a stationary, attackable zombie with their player head, held item, current health, armour and effects. Their team remains in the match. Match PvP grace, teammate protection, deathmatch freeze and anti-janitor rules also apply to the zombie. Rejoining while it is alive removes it and restores the player at its current position and health with their match inventory. If killed, chat says **"<player> was killed while disconnected!"** and the inventory drops, or goes into the protected death chest when anti-janitor applies. With the rule off, disconnecting immediately eliminates as before.

There is **no logger timeout**: it stays until rejoined, killed or the match ends. This timeout policy is local; the [Hypixel wiki](https://hypixel.fandom.com/wiki/UHC_Champions) confirms zombies/rejoining but does not specify a timeout. Match end/stop removes remaining loggers and preserves pre-match snapshot restoration on next connection. Loggers also move with the Nether closure and deathmatch transition. These tagged proxies represent participants and are exempt from the arena's ordinary-mob rejection and transition cleanup.

Integration: logger entities carry `brainage_minigames:combat_logger` and `brainage_minigames:participant=<UUID>` tags. Public `io.github.brainage04.brainage_minigames.game.UhcCombatLogger.participant(Entity)` returns the UUID or `null`; `match(Entity)` returns its Match or `null`; `zombie(UUID)` returns the live proxy or `null`. Bot/camera integrations must treat them as match opponents, not ordinary hostile mobs. `UhcGame.deathmatchStartTicks(Match)` and `deathmatchDurationTicks(Match)` expose actual schedules, including a shortened countdown.

`UhcCombatLogger.canAttack(ServerPlayer attacker, Entity logger)` checks whether that player may currently attack the genuine live proxy, including active match membership, enemy teams, PvP/deathmatch-freeze and anti-janitor protection. Permission probes do not record attack or kill credit.

During a match, End portals refuse participants, spectators and combat loggers, including those in the minigames deathmatch arena; players receive an explanation in chat. A portal cannot carry them into the server's ordinary End outside their match. Nonparticipants retain vanilla portals.


### UHC coins, kits and profession trees

Regular UHC awards the documented Hypixel base coins: **10 every five minutes alive, 50 for an opponent kill (also to alive teammates within 200 blocks), 15 on first Nether entry, and 150 for a win**. Killing a disconnected participant's combat logger is a full opponent kill when kill credit applies, including the nearby teammate award; it is not a second, separate 50-coin award. Balances, purchases and kit selections persist per UUID in the world. This progression does not run in duels, Meetup or FinalUHC.

Additional **local** rewards are enabled in regular UHC:

| Action | Base coins |
| --- | ---: |
| Assist: dealt accepted damage in the preceding 10 seconds to an opponent killed by somebody else | 20 |
| First opponent kill of the match | 25 bonus |
| Elimination with at most 10 / 5 / 3 players still alive, counting the eliminated player | 25 / 50 / 75, cumulative |
| Reach deathmatch alive, including disconnected combat loggers | 50 |
| Complete a profession recipe | 5 |
| Mine diamond / gold ore | 3 / 1 per block |
| Kill a hostile mob | 1 |
| Consume a golden head | 5 |
| Survive a border shrink stage, including the deathmatch shrink | 10 per stage |
| Win the current anti-janitor duel with a credited kill while protection is enabled | 10 bonus |

Every award goes through **`uhc_coin_multiplier`**, an integer percentage defaulting to **100**, and announces the actual earned amount and action in chat. `150` awards 1.5 times base coins, `0` awards none. Fractions round down separately for each award. With **`uhc_uncapped_coin_awards true`** (the default), profession crafts, ore mining and hostile mob kills have no cap. With it `false`, their base-coin totals are capped at **50 / 60 / 30 per player per match**, before multiplication; diamond and gold share the mining cap. Counters retain previously earned base coins if the rule changes mid-match. Other actions are not capped.

```text
/minigames uhc coins
/minigames uhc trees
/minigames uhc trees cooking
/minigames uhc unlock cooking recipe1
/minigames uhc unlock extras cornucopia
/minigames uhc kit ecologist
/minigames uhc kit_upgrade ecologist level1
/minigames uhc kit default
/minigames uhc prestige_bonus stone iron_pickaxe
/minigames uhc craft light_apple
/gamerule brainage_minigames:uhc_max_all_perks false
/gamerule brainage_minigames:uhc_max_all_kits false
```

The shop includes all **13 profession trees (52 recipes), 30 Extra recipes and 10 selectable kits**. Crafted recipe previews, taking results and shift-crafting enforce ownership in active UHC matches only. Recipes include level-I paper/flint books, eight-gold Golden Heads and four-gold Light Apples, plus enchanted weapons/tools, Forge, Backpack and Fusion Armor. Grid cells that take iron or gold ore (Iron Economy, Gold Pack, Quick Pick, Philosopher's Pickaxe) accept the ore block, its deepslate variant or the raw iron/raw gold that mining it drops, so 8 raw iron and a coal make 10 iron ingots without smelting.

This mod's default is that every UHC player owns everything from the first game: `brainage_minigames:uhc_max_all_perks` defaults **true** and treats every profession, profession prestige and Extra recipe as unlocked, and kits have their own independent rule, **`uhc_max_all_kits`**, also **true** by default, which gives every selectable kit tier III and prestige. Neither changes saved purchases, and neither max rule enables the other. Switch a rule to `false` to make that part of the progression purchase-based: ownership then comes from the coins shop, and players keep whatever they have bought.

A player with no personal kit selection gets **Stone Gear** (the four stone tools, upgraded and prestiged while max-kits is on). `/minigames uhc kit default` selects Stone Gear. Explicit match kit overrides still take precedence. With **`uhc_choose_prestige_bonus`** enabled (default **false**), `/minigames uhc prestige_bonus <kit>` lists clickable bonus choices, and `/minigames uhc prestige_bonus stone iron_pickaxe` saves that kit's choice per UUID. The player must have prestiged the kit, either through purchases or the max-kits rule. When selection is off, the original weighted random roll applies. When selection is on but no choice has been saved, the first listed bonus is used.

`uhc_unlimited_crafts` and `uhc_no_duplicate_crafts` both default **true**. With unlimited on, craft-count limits are disabled, including for Extra Ultimates. With it off, each normal profession recipe allows three crafts and each profession ultimate allows one, with profession prestige adding one to each limit; each Extra Ultimate allows one craft. No-duplicates controls the existing random-result pools, not recipe ownership. Turn it off for independent random results.

When an active regular-UHC player has the ingredients for an unlocked craft with remaining uses, chat names the craft and offers **[Craft]**. Clicking runs `/minigames uhc craft <recipe>` and opens a server-side crafting-table menu with exactly one craft's ingredients moved from the inventory into the grid. One output click crafts normally; closing returns unused grid and cursor items. No client mod is required. A prompt is not repeated while that recipe's ingredients remain unchanged; unrelated items do not reset it. The command rechecks ingredients, unlocks and craft limits, so an old message cannot bypass them.

The [complete source-cited catalog](docs/UHC_PROGRESSION.md) lists every tree node, passive level, recipe, kit level, coin reward and historical shop price. Official 2015/2017/2019/2020 announcements override older player-authored forum guides. Unpublished current prices, lost image ingredients and approximation parameters are explicitly marked **not Hypixel-confirmed**; the official wiki material found was SkyBlock, not a UHC Champions catalog.

### UHC resource gamerules

These world-persisted `/gamerule` settings apply **only** in the UHC-style dimensions, `brainage_minigames:uhc`, `meetup` and `final_uhc` and their nethers (`uhc_nether`, `meetup_nether`, `final_uhc_nether`), including other games played in them. The Overworld, vanilla Nether and every other dimension remain vanilla. Every resource rule defaults to **200 percent (2.0×)**; `100` restores vanilla rates, `150` means 1.5×, `50` means 0.5×, and `0` disables the corresponding drops or placed-feature attempts. Values are nonnegative integer percentages. [Mob spawn rules](#uhc-mob-spawn-gamerules) and [mob drop rules](#uhc-mob-drop-gamerules) work the same way with their own defaults.

Minecraft 26.2's built-in gamerule types and visitors support only booleans and integers. Fabric offers its own double extension, while NeoForge requires a different enum/visitor/client integration; there is no clean shared floating-point type compatible with this server-only mod's vanilla clients. Percentages therefore provide fractional multipliers consistently on both loaders without a new dependency or custom client requirement.

All names below have the `brainage_minigames:` namespace:

| Resource | Generation rule | Drop rule |
| --- | --- | --- |
| Apples from oak/dark oak leaves | — | `uhc_apple_drop_percent` |
| Sugar cane | `uhc_sugar_cane_generation_percent` | `uhc_sugar_cane_drop_percent` |
| Coal | `uhc_coal_generation_percent` | `uhc_coal_drop_percent` |
| Copper | `uhc_copper_generation_percent` | `uhc_copper_drop_percent` |
| Iron | `uhc_iron_generation_percent` | `uhc_iron_drop_percent` |
| Gold, including nether gold | `uhc_gold_generation_percent` | `uhc_gold_drop_percent` |
| Redstone | `uhc_redstone_generation_percent` | `uhc_redstone_drop_percent` |
| Lapis | `uhc_lapis_generation_percent` | `uhc_lapis_drop_percent` |
| Diamond | `uhc_diamond_generation_percent` | `uhc_diamond_drop_percent` |
| Emerald | `uhc_emerald_generation_percent` | `uhc_emerald_drop_percent` |
| Nether quartz | `uhc_nether_quartz_generation_percent` | `uhc_nether_quartz_drop_percent` |
| Ancient debris | `uhc_ancient_debris_generation_percent` | `uhc_ancient_debris_drop_percent` |

For example:

```mcfunction
/gamerule brainage_minigames:uhc_apple_drop_percent 150
/gamerule brainage_minigames:uhc_iron_generation_percent 200
/gamerule brainage_minigames:uhc_iron_drop_percent 200
```

- **Drops:** vanilla first determines the loot, including Fortune and explosion survival. Each eligible item that would drop gives `floor(multiplier)` copies plus one extra with probability equal to the fractional part: at 150%, each raw iron or apple gives one guaranteed item and a 50% chance of a second. Counts exceeding a stack are split without loss. The apple rule multiplies only apples from broken or decayed natural oak/dark oak leaves: it does not increase the initial vanilla apple chance or change saplings, sticks or leaf-block drops. The sugar cane rule multiplies the cane that natural cane drops, whether cut, washed away or broken with the block below it. Ore rules share normal/deepslate variants; XP is unchanged.
- **No replanting duplication:** resources placed by anyone, including non-participants, keep vanilla drops when mined or decayed, and so does sugar cane that grows during play, from natural or replanted stalks. Their positions are recorded separately in each UHC dimension, survive saves/restarts, and are cleared when that dimension is regenerated. Drops of the broken block's own item (such as Silk Touch ore blocks or sheared leaves) are never multiplied, except sugar cane, whose only loot is itself. The one normal-loot exception is a first break of natural ancient debris **without Silk Touch**: it still receives the debris multiplier, but placing and mining the resulting items cannot multiply them again.
- **Placed logs** are recorded the same way, so [Timber](#uhc-scenarios) never fells them; their drops are unchanged.
- **Generation:** each ore and sugar cane placed-feature pipeline runs `floor(multiplier)` times, with one additional run chosen by the fractional probability per feature per chunk. This scales attempts/vein and patch counts, including rare veins and cane patches using rarity filters, without resizing veins or patches or changing their height/biome/water restrictions. Block totals are statistical, not exactly proportional: attempts can overlap or find no suitable stone or shore. Noise-based large copper/iron veins retain vanilla behavior.
- **New chunks only:** changing generation rules never edits already generated chunks. Set them before opening a match/loading its region; subsequent fresh chunks use the current values. The gamerules persist when the UHC dimensions are regenerated.

### UHC mob spawn gamerules

Natural mob spawning in the same dimensions has a percentage rule for each group of mobs and for each mob, all in the `brainage_minigames:` namespace. A mob's percentage is its group's rule times its own, divided by 100: with the defaults, cows, horses, donkeys, chickens, rabbits, spiders and skeletons spawn at **200%** (for leather, transport, feathers, string, bones and arrows) and every other mob at **100%** (vanilla). Mules have no rule: they never spawn naturally, only from breeding.

| Group | Group rule (default) | Mobs, each with `uhc_<mob>_spawn_percent` (default 100; cow, horse, donkey, chicken, rabbit, spider and skeleton 200) |
| --- | --- | --- |
| Passive | `uhc_passive_spawn_percent` (100) | armadillo, axolotl, bat, camel, cat, chicken, cod, cow, donkey, fox, frog, glow_squid, horse, mooshroom, ocelot, parrot, pig, rabbit, salmon, sheep, squid, strider, tropical_fish, turtle |
| Neutral | `uhc_neutral_spawn_percent` (100) | dolphin, enderman, goat, llama, nautilus, panda, piglin, polar_bear, pufferfish, wolf, zombified_piglin |
| Hostile | `uhc_hostile_spawn_percent` (100) | blaze, bogged, cave_spider, creeper, drowned, ghast, guardian, hoglin, husk, magma_cube, parched, pillager, skeleton, slime, spider, stray, sulfur_cube, witch, wither_skeleton, zombie, zombie_horse, zombie_villager |

For example, three times the cows and horses, vanilla chickens, twice the sheep and no creepers:

```mcfunction
/gamerule brainage_minigames:uhc_cow_spawn_percent 300
/gamerule brainage_minigames:uhc_horse_spawn_percent 300
/gamerule brainage_minigames:uhc_chicken_spawn_percent 100
/gamerule brainage_minigames:uhc_sheep_spawn_percent 200
/gamerule brainage_minigames:uhc_creeper_spawn_percent 0
```

These are every mob that the overworld and nether biomes and structures (witch huts, fortresses, monuments, outposts) spawn; the group follows the mob's behaviour towards players, not its vanilla spawn category. The rules apply to both kinds of natural spawning:

- **With new chunks:** the animals vanilla places once when a chunk generates (where nearly all passive land animals in a UHC come from) are scaled like ore attempts: each one is placed `floor(multiplier)` times at its spot, plus once more at the fractional chance, and not at all at 0%. Like generation rules, this applies to chunks generated afterwards.
- **While chunks tick:** each spawn of the mob makes `floor(multiplier)` of it on average (below 100%, only that share of spawn attempts go ahead, none at 0%), and each one counts `100 / multiplier` towards its category's mob caps (rounded per mob, so at 200% half of them count and half do not). Vanilla spawning stops when the caps are reached, which they quickly are for hostile mobs at night or in caves; scaling only the spawn attempts would leave the number of mobs nearly unchanged, while scaling the caps alone would change every mob of a category together. With both, at 200% about twice as many of that mob are around however the caps limit them, and the other mobs of its category are unaffected. Changes take effect at once.

Mobs from spawners, spawn eggs, breeding, raids and patrols, structures that place them as they generate (villagers, iron golems, mansion illagers), and other mods are unaffected. Very high percentages multiply entity counts and server load accordingly.

Measured in an 8-bot UHC on a dedicated server pinned to two cores, from four to eight minutes in (two runs each): the defaults had 580–800 passive mobs loaded in the UHC dimension against 550–620 with every spawn rule at 100, 420–590 cows, sheep, pigs, chickens and rabbits against 350–410, and 365–490 hostile mobs against 340–390 (160–220 skeletons against 105–125). Average tick times, 17–22 ms with the defaults against 19–22 ms, were within the spread between runs.

### UHC mob drop gamerules

In the same dimensions, every mob in the table above also has `uhc_<mob>_drop_percent` (default **100**, vanilla), which scales the loot of mobs of that kind that **spawned naturally**, with new chunks or while chunks tick, as the ore rules scale natural ores: each item of the mob's death loot gives `floor(multiplier)` copies plus one more at the fractional chance, and none at 0%, split into stacks without loss. Only the mob's own loot table counts: the experience it drops and the equipment it picked up or spawned with are unchanged. Mobs from spawn eggs, spawners, breeding, commands, raids and patrols keep vanilla loot, so farms built during a match do not multiply resources; a mob that converts (a zombie that drowns) keeps whether it spawned naturally. The mark is an entity tag, `brainage_minigames.natural_spawn`, saved with the mob.

`uhc_all_meat_is_beef` (default **false**) makes every mob that drops meat drop the same amount of beef instead, and cooked beef where it would drop cooked meat (a mob that died burning): pigs, hoglins, sheep, chickens and rabbits; cows and mooshrooms already drop beef. Fish (cod, salmon) are not meat and stay as they are. The rule changes any mob's loot in the UHC dimensions, wherever it came from, before the drop rule scales it.

```mcfunction
/gamerule brainage_minigames:uhc_all_meat_is_beef true
/gamerule brainage_minigames:uhc_cow_drop_percent 200
/gamerule brainage_minigames:uhc_zombie_drop_percent 0
```

### Anti-janitor protection

`/gamerule brainage_minigames:anti_janitor true` enables exclusive fights in **public UHC, Meetup, FinalUHC and SkyWars matches**, in free-for-all layouts or matches where at least three teams start with players. It is **on by default**. Private `/duel` matches, two-team matches, kit duels and respawn/non-PvP games are unaffected: the protection is intended for survival/elimination matches with several competing opponents, not to change ordinary team or duel combat.

Fights lock **teams**, not just the two players (in a free-for-all every player is their own team). The first accepted player hit that actually removes health **or absorption** locks the attacker's team and the victim's team together. While locked, members of those two teams can damage only each other, so teammates can join their team's fight, and no third team can damage any of them. Every damaging hit between the two teams resets their shared countdown; invulnerability-rejected hits, fully blocked hits and zero-damage eggs, snowballs or fishing rods do not start or refresh it. Permission checks alone never create a lock. The first damaging pair of teams wins a three-way exchange; later incompatible hits are refused. A remaining-seconds countdown appears in the action bar of every locked player.

The countdown defaults to **30 seconds**, configured separately for each qualifying game:

```mcfunction
/minigames settings uhc anti_janitor_seconds 30
/minigames settings meetup anti_janitor_seconds 30
/minigames settings final_uhc anti_janitor_seconds 30
/minigames settings skywars anti_janitor_seconds 30
/gamerule brainage_minigames:anti_janitor false
```

The gamerule is a server-wide on/off switch; the setting accepts 1–3600 seconds and is captured when a match opens, like other game settings. Turning the gamerule off immediately releases combat and chest restrictions.

- **Deaths and disconnects:** dying, leaving or disconnecting during a lock puts the victim's inventory, armour and offhand in a physical double chest that only the duel partner can open. This also applies to deaths from mobs or the environment: ownership follows the current duel, not a possibly different killer. The survivor stays protected for the remaining countdown; the death or disconnect itself does not refresh it. A lethal partner hit does refresh it, just like any damaging hit.
- **Legacy-combat offhand:** sword blocking leaves the offhand untouched. Offhand food and ordinary shields remain normal death/leave/disconnect loot, including in the private partner chest.
- **Loot safety:** neither duellist nor anyone else can mine, explode, replace or extract items with a hopper/hopper minecart from the chest during protection. At exactly zero, it becomes an ordinary public chest and the survivor can fight another player. Closing/stopping a match also releases its locks before arena cleanup.
- **Location:** the first chest block is at the death's block position, with its second half immediately east and clear space above for opening. Air deaths leave a floating chest; water deaths preserve waterlogging; lava at the chest positions is replaced. Deaths below the playable/world floor are clamped to the first safe height above the arena's void threshold and world minimum, so void loot is not lost. Existing block entities are preserved: if the exact position is occupied, the chest searches upward, then east, and its coordinates are sent to the partner.
- **Non-player damage:** mob, fall, fire, lava, drowning, border and other environmental damage remain enabled and never refresh a duel. Vanilla-unattributed hazards, including lava placed by another player, remain environmental damage; this rule does not grant general invulnerability.

This follows the combat-timer/death-loot concept described in Hypixel's Duels forum discussion, [“Improvements to UHC Deathmatch”](https://hypixel.net/threads/improvements-to-uhc-deathmatch.2080082/). That historical player discussion requests an **8-second separate loot timer** and mentions an existing combat timer; it does **not** establish an exact 30-second Hypixel rule or confirm that Solo UHC Champions enables it. The shared 30-second timer and default-on policy here are local rules, not a claimed exact copy of Hypixel.

### Container protection

`/gamerule brainage_minigames:container_protection` is a persistent boolean, **true by default**, shared by all matches. Containers placed by a participant belong to that UUID for the match's lifetime, whether the rule is on or off. With it on, other players (including teammates) cannot open, break or take items from them. Each half of a double chest has its own owner; opening either half must be allowed for both. Hoppers may extract protected contents only if the hopper and every protected source half have the same owner; unowned hoppers and hopper minecarts cannot extract protected contents. Existing map/natural containers and unrelated worlds remain vanilla. Enabling the rule also blocks extraction from already-open foreign menus. Match end/map reset removes ownership, and a replacement container never inherits an old block entity's owner. Anti-janitor death-chest protection remains independent.

With the rule **false**, opening, breaking and extraction remain vanilla. Successful foreign opens/breaks are recorded for the container's owner so server-side bots can react without a compile dependency, through `ContainerProtection.owner` and `lastAccess` (see [Integration contract](#integration-contract)).

### BuildUHC

`build_uhc` is a survival-mode kit fight with the BuildUHC kit: gear, lava, water and building blocks. Natural regeneration is disabled. Any team layout works; matches use independent barrier-walled arena slots.

### Classic

`classic` is a kit fight with iron gear, a bow and a fishing rod. Any team layout works in an independent arena slot.

### No Debuff

`no_debuff` is a kit fight with diamond gear, healing splash potions, speed potions and ender pearls. Any team layout works in an independent arena slot.

### Gapple

`gapple` is a kit fight with Protection IV diamond gear, 64 golden apples, strength potions and speed potions. Any team layout works in an independent arena slot.

### Boxing

`boxing` deals no damage. Each landed hit scores one point; the first team to 100 hits wins. Any team layout works in an independent arena slot.

### Combo

`combo` allows a hit every two ticks rather than every ten, with no attack cooldown: every swing is at full strength. Any team layout works in an independent arena slot.

### Bow

`bow` blocks melee damage but permits projectile and environmental damage. Any team layout works in an independent arena slot.

### Meetup

Meetup plays on generated terrain in `brainage_minigames:meetup`; each match gets a far-away region with its own border instead of the dimension's world border. That border is sent only to the match's members, who see it and are stopped by it as usual; anyone more than a block outside it is hurt by 0.5 per further block (at least 1) as by a vanilla border. Placing and breaking blocks is allowed only inside it. Any number of Meetups can run alongside UHC and FinalUHC matches. The region is the first of up to eight unoccupied random ones whose centre and four points around it have land biomes (otherwise the one with the most), and the lobby and every spawn are on the nearest solid, dry ground inside the border, 20 blocks in from it. Its independent clock stays at noon. The dimension and its paired `meetup_nether` are regenerated with the other natural dimension pairs after the server stops or before it starts.

Meetup follows the usual UHC Meetup plugins: every player's kit (`kits/meetup`) is rolled separately. It picks one of three tiers from `brainage_minigames:meetup/`, each trading armour for damage and healing, with random slots for its diamond pieces:

| Tier | Armour | Sword | Bow | Golden apples | Golden heads |
| --- | --- | --- | --- | --- | --- |
| `diamond` | 3 diamond + 1 iron piece, Protection I | Diamond, Sharpness I | Power I | 4 | 2 |
| `mixed` | 2 diamond + 2 iron pieces, Protection II | Diamond, Sharpness I | Power II | 5 | 2 |
| `iron` | 1 diamond + 3 iron pieces, Protection II | Diamond, Sharpness II | Power III | 6 | 3 |

Everyone also gets a fishing rod, 32 arrows, a diamond axe and pickaxe, 64 steak, 64 cobblestone or oak planks, two water buckets, two lava buckets and flint and steel. A golden head is a golden apple named Golden Head that gives Regeneration II for 10 seconds (twice an apple's healing) and Absorption for two minutes. The plugins' enchanting table, anvil and experience bottles are left out, as matches are too short to use them.

As in Badlion's Meetup, each player's kit is rolled when the countdown starts and listed in their chat with a clickable **[Reroll]**; `/minigames reroll` replaces it with a new roll during the countdown, as many times per match as the `kit_rerolls` setting allows (default **1**; `0` hides the kit until the match starts). Players get exactly the kit they last saw. Rerolls exist only while the match's kit is a random (loot table) kit, so a Meetup opened with a fixed kit has none.

With `/gamerule brainage_minigames:meetup_adaptive_border true` (off by default; captured when the match starts), the border follows [Badlion's Meetup 2.0](https://www.badlion.net/forum/thread/150631/post/849228) instead of the steady schedule above: it shrinks at a number of players left or a time, whichever comes first, and after a set time everyone left takes random damage until one team remains. Each shrink takes `shrink_duration_seconds` and never grows the border. The random damage is one heart of magic damage, ignoring armour, every 5 to 10 seconds at random for each survivor; Badlion did not publish its amount. Since that ends every match, `time_limit_minutes` does not apply to an adaptive match. The thresholds are Meetup settings:

| Setting | Default | Meaning |
| --- | --- | --- |
| `adaptive_first_size` / `adaptive_first_players` / `adaptive_first_seconds` | 50 / 8 / 600 | First shrink: to 50 across at 8 players left or 10:00 |
| `adaptive_second_size` / `adaptive_second_players` / `adaptive_second_seconds` | 25 / 4 / 900 | Second shrink: to 25 across at 4 players left or 15:00 |
| `adaptive_damage_seconds` | 1500 | Random damage from 25:00 |
| `kit_rerolls` | 1 | Kit rerolls per player per match during the countdown |

### FinalUHC

FinalUHC uses the [same generated-terrain placement and independent border lifecycle as Meetup](#meetup), in its own `brainage_minigames:final_uhc` dimension with an independent noon clock and paired `final_uhc_nether`. It can run alongside any number of UHCs, Meetups and other FinalUHCs, and uses a fixed kit rather than a random one.

FinalUHC is Minemen's Final UHC, compared against its public match inventories (for example [this match](http://minemen.club/match/d7f6ddd6-29f5-3d9c-aa3a-4583e4d1be0c) and several of Rwcist's): every player finishes with full diamond armour, a diamond sword, axe and pickaxe, 16 golden apples, 64 steak, two stacks each of planks and cobblestone, six buckets between water, lava and empty, flint and steel and a fishing rod. The kit (`kits/final_uhc`) is exactly that, with 3 water and 3 lava buckets. The match pages do not show enchantments, so the levels are assumed: Protection II armour, Sharpness III sword, Efficiency III axe and pickaxe. Compared with BuildUHC, FinalUHC has no bow, arrows, or random enchantment levels, twice the blocks, three of each bucket instead of one, flint and steel, and 16 golden apples every time; and it is fought on hills, trees and caves inside a border rather than on BuildUHC's flat grass floor in a barrier box.

### Speed UHC

`speed_uhc` is Hypixel's Speed UHC: the same survival game as UHC, compressed. It is a UHC variant of the same game class, plays in a fresh region of the shared `brainage_minigames:uhc`/`uhc_nether` pair and keeps UHC's lobby auto-start, vote, bot fill, combat loggers, anti-janitor fights, sidebar, coins and the arena deathmatch. Its border always closes continuously (the `uhc_border_style` gamerule does not apply), UHC profession crafts, perks and golden heads are off (Speed UHC uses vanilla recipes), and the UHC schedule gamerules (`uhc_deathmatch_after_grace_minutes`, `uhc_deathmatch_duration_minutes`, `uhc_deathmatch_skip_players`) leave its own schedule alone. Lobbies fill to 12; the layouts offered are solo and teams of two.

| Setting | Default |
| --- | --- |
| `grace_period_minutes` | 2 (Fire Resistance for the same 2 minutes) |
| `border_start_size` | 300 |
| `first_shrink_minutes` / `final_shrink_minutes` / `final_shrink_size` | 5 / 10 / 50 |
| `nether_close_minutes` | 5 |
| `deathmatch_minutes` / `deathmatch_duration_minutes` / `deathmatch_shrink_minutes` | 11 / 5 / 2 |
| `time_limit_minutes` / `lobby_size` | 20 / 12 |

Hypixel advertises "average 10-minute games" and a 2016 player guide describes the border and deathmatch starting after 5 minutes; the exact current numbers are not published, so these are this mod's choice. Rules for every participant:

- **CutClean and Timber are always on**, whatever `uhc_cutclean` and `uhc_timber` say: ores drop smelted, killed animals drop cooked meat, and breaking a log fells the logs connected to it.
- **Gravel** also drops 2 arrows and a killed **chicken** 4 arrows; **sugar cane** the player breaks drops a book and a sugar instead of itself (cane that falls with it drops as usual). Arrow and book counts are this mod's choice.
- **Brewing is instant**: a brewing stand inside the match's border finishes a brew on its next tick.
- Ore, apple and cane amounts follow the [UHC resource gamerules](#uhc-resource-gamerules) as in UHC.

**Kits.** Every player starts with one kit. Default (always owned) is six oak planks and an iron chestplate; the other 16 are Hypixel's Speed UHC "Kits" menu as exported on 2026-10-06, with its contents and rarities: Archaeologist, Archer, Cowboy, Enchanter, Farmer, Fisherman, Healer, Knight, Logger, Miner, Nether Walker, Oink, Pyro, Scout, Summoner and Tamer.

**Perks.** Every owned perk is on unless the player turns it off. Hypixel's "Perks" menu lists tier I; tiered perks here have five tiers that each add the tier-I number (a 2020 player guide gives Arrow Recovery's top tier as 75%, five times 15%; the other top tiers follow the same rule and are this mod's choice). Top tiers: Arrow Recovery 75% of arrow hits return an arrow; Bow Flex +1 Power to the bow every 2 bow kills; Cold Blood halves fire and lava damage for the first 5 s of a burn; Ender Generosity 25% extra ender pearl from Endermen; Expert Miner +25% experience from ores and mobs; Low Gravity -25% fall damage; Marksmob 25% chance of a Power I bow from Skeletons and Spiders; Master Brewer +25% beneficial potion duration; Medicine -50% Poison duration; Monster Tamer no direct mob damage below 5 hearts; No Mercy 10% chance of a second kill coin award; Nourishment fills hunger and saturation on a kill; Portal Protection 20 s of Absorption I on entering the Nether; Swimming Champion Speed I in water; Telekinesis ore drops go straight to the inventory; Tenacity 75 s of Resistance I at the start; Vitamins 15 s of Speed II after a golden apple.

**Masteries.** Exactly one is active; Wild Specialist is every player's first. Wild Specialist halves environmental damage; Sniper adds 2% bow damage per block for shots from over 20 blocks; Berserk gives Strength I below 3 hearts; Fortune gives ores a 25% chance of one more drop; Master Baker makes a golden apple heal 2 more health (+50%); Invigorate adds 1 maximum health per kill, up to 4; Huntsman gives 30 s of Speed II after a kill; Vampirism heals 1 health on a kill; Guardian takes 5% less damage from players.

| Gamerule | Default | Effect |
| --- | --- | --- |
| `speed_uhc_max_all_kits` | `true` | Every Speed UHC kit counts as owned; `false`: Default plus operator grants |
| `speed_uhc_max_all_perks` | `true` | Every perk counts as owned at its top tier; `false`: operator grants, at tier I |
| `speed_uhc_max_all_masteries` | `true` | Every Mastery counts as owned; `false`: Wild Specialist plus operator grants |

Players choose in the **Speed UHC Shop** (the emerald in a Speed UHC lobby's hotbar, the Match Setup's emerald button, or `/minigames speed_uhc`): kits, perk toggles and the Mastery row. Commands: `/minigames speed_uhc kit [kit]`, `perk [perk] [true|false]`, `mastery [mastery]`; operators use `/minigames speed_uhc grant|revoke <players> kit|perk|mastery <id>`. Choices are saved per UUID and apply from the next match's start. Bots without a saved choice take Default, Archaeologist, Knight, Miner or Tamer and Fortune, Master Baker, Huntsman or Guardian. Hypixel's Drop Manager, cosmetics, Tears/Salt and Insane mode are not included.

### MiniUHC

`mini_uhc` is Badlion's MiniUHC: a smaller, shorter full-survival UHC, a UHC variant of the same game class in the shared `brainage_minigames:uhc`/`uhc_nether` pair, with UHC's kits, profession crafts, golden heads, lobby, bots and sidebar. Its border always uses instant (Badlion) shrinks, whatever `uhc_border_style` says, and the UHC schedule gamerules leave its schedule alone. Players report MiniUHC ending in a 100x100 border after roughly 30 minutes of shrinks; the rest of the schedule is this mod's choice:

| Setting | Default |
| --- | --- |
| `grace_period_minutes` | 8 (Fire Resistance for the same 8 minutes) |
| `border_start_size` | 600 |
| shrinks | 400 at 15:00, 300 at 20:00, 200 at 25:00, 100 at 30:00 |
| `nether_close_minutes` | 15 |
| `deathmatch_enabled` | 0: no arena; the last border is the meetup |
| `time_limit_minutes` | 45 |

Solo and teams of two to four are offered; any layout works.


## Playing

```text
/minigames list
/minigames join [match] [team]
/minigames watch <match>
/minigames leave
/minigames status <match>
/minigames elo [player]
/shout <message>
```

`join` without a match number joins the only open lobby. A team number requests a team; everyone else is assigned randomly. Joining saves your position, dimension, inventory, game mode, effects, health, hunger, experience and scoreboard team, and all of it is restored when you leave or the match ends; while the server has a [hub](#hub), you are restored at the hub instead of where you joined from. Rewards from the `brainage_minigames:rewards/default` loot table (empty by default; override it with a datapack) are added after restoration, and every win increments the `brainage_games_won` scoreboard objective.

Leaving during a match forfeits. During active UHC, Meetup and FinalUHC matches, disconnecting while alive leaves an attackable zombie when `uhc_combat_logger` is enabled (the default); reconnecting while it survives resumes the match. In other games, or with that rule disabled, disconnecting eliminates you and your saved state is restored when you reconnect. Deaths eliminate players into spectator mode in elimination games, but Bridge, Battle Rush, Quake, Pearl Fight, Parkour and Ice Boat Racing respawn them, and Sumo has knocked-off players watch in spectator mode only until the round ends. Leaving or the match ending restores your saved game mode and state.

While you are in or watching a match you see its own sidebar, sent only to you: the game and layout, the match number, the lobby size, countdown, elapsed time and time limit or result, your team, and, in team matches, each team with its score and how many of it are alive. Players are not listed there (the tab list does that), except in free-for-all games that score each player (kills, points, race progress), where the sidebar shows those standings. Boxing adds each team's hits and the target, Combo the hit delay, and UHC the time until PvP, the border size, the next shrink, deathmatch and nether-close countdowns, and the players alive; Meetup the border size, the time until the next shrink (or the size it is shrinking to) and the players alive, and FinalUHC the border size. Your tab list shows every participant's actual health as a number (normally 20 is full, or 40 in default double-health UHC, rounded up), cleared once they are eliminated. Both refresh twice a second, never change the server scoreboard, and when you leave the server's own sidebar and tab list objectives (such as `brainage_games_won`, if displayed) come back.

Chat, tab-list and sidebar names use only the nine bright team colours (red, blue, green, yellow, aqua, light purple, gold, white and gray), never black or the dark variants. The palette repeats for larger matches; numbered team names and each FFA player's own name remain distinct, including 50-player free-for-alls. Bridge's unfilled score dots also use readable gray.

In a lobby the sidebar also shows reserved bot slots, the time until an auto-starting lobby starts and the votes to start. Bots that a [bot provider](#lobbies-votes-and-bots) spawned are listed as `[BOT] <name>` on the sidebar and in the tab list.

### Team matches

Every layout with teams, in every game, plays the same way. UHC, Meetup and FinalUHC offer **Solo** (free-for-all) and **Teams of 2, 3 and 4** in the menu and in `/minigames open` suggestions, sized to fill the game's `lobby_size` (8 by default: `2v2v2v2`, `3v3v3` and `4v4`); any other layout, such as `1v2v3`, can be typed or built in the custom layout editor.

- **Starts:** each team spawns together on one spot, and the teams are spread apart (UHC's ring of starts, Meetup's and FinalUHC's spread), with the same seeded starts as solo matches.
- **Chat:** while a team match is counting down or running, plain chat from a player whose team has other members goes to **that team only**, shown as `[Red] <name> message`, like vanilla's `/teammsg`. Start the message with `!` (for example `!gg`) or use `/shout <message>` to talk to everyone. Solo players, lobbies, spectators who are not on a team and ended matches chat to everyone as usual.
- **Friendly fire:** teammates cannot damage each other: melee, arrows, tridents, thrown items, harming splash and lingering potions, and TNT and fireworks they set off all pass harmlessly (other potion effects, such as poison, still apply, as in vanilla teams). Lava a teammate poured and fire a teammate lit with flint and steel or a fire charge do not hurt or keep burning you either, including lava within 8 blocks of the source they poured; lava and fire that spread further, or that an opponent or the world placed, hurt as usual.
- **Sidebar:** under **Team:** you see each teammate's health (struck through once eliminated, `offline` while their combat logger stands in); UHC, Meetup and FinalUHC add the players alive and, in team layouts, the teams still standing (`Alive: 5 (3 teams)`).
- **Winning:** a team is out once all its members are; the match ends when one team is left, and every member of that team wins, eliminated or not.
- **Bots** from a [bot provider](#lobbies-votes-and-bots) join teams exactly like players.


### Elo ratings

Every human player starts at **2000 Elo**. Ratings follow the player's UUID, are saved with the world's scoreboard (`brainage_elo_uuid` is the internal UUID ledger), and survive reconnects, name changes and server restarts. `/minigames elo` shows your rating; `/minigames elo <player>` shows an online player's rating without requiring game-master permission. The public dummy objective **`brainage_elo`** publishes ratings under player names so other mods and commands can read them without a compile dependency.

`/gamerule brainage_minigames:elo_k_factor 32` controls updates globally and is world-persisted; **32** is the default and **0** disables changes. It is a gamerule rather than a per-game setting because the same rating is shared across games. Given ratings `R` and `O`, the expected score is `1 / (1 + 10^((O - R) / 400))`. The change is `K * (score - expected)`, where a win scores `1`, a loss `0`, and a draw `0.5`. Changes are rounded to integer Elo for scoreboard publication. Each result uses pre-result ratings for both sides, and human wins and losses count against both humans and bots. A bot's published Elo stays fixed.

- **Duels/team layouts:** the match result updates each participant once; forfeiting or disconnecting still counts. With multiple opposing players, each participant uses the mean expectation against the other teams, so one match applies one K-scaled update rather than multiplying K by team size. Teammates never rate against one another.
- **Free-for-all policy:** each credited kill immediately counts as the killer's win over the victim and the victim's loss. Environmental deaths without a credited player, forfeits without a kill, and the final last-player-standing result add no separate update. At a drawn match end, **every pair of remaining players counts as a draw**, including remaining bots as fixed-rated opponents; all pair expectations are taken before applying the end-of-match changes. Eliminated players are excluded from that final draw. This is the FFA policy for UHC and the other games when opened with `ffa`.
- Stopping/cancelling a match without a result does not rate it. Spectators are never participants in rating updates.

Sparring Bots tags its players `sparringbot` and publishes their fixed rating in `brainage_elo`; this is the scoreboard/tag integration contract, not a linked dependency. Its `sparringbots:bot_elo_offset` gamerule makes a bot fighting a rated human use the human's Elo plus an offset while retaining its own fixed rating.


## Menus

Players on vanilla clients can do everything above through server-side chest menus as well as commands. `/minigames` (or `/minigames menu`) opens the main menu for players; the console still gets the match list. Players entering the hub get a **Game Menu** compass that opens the same menu; it cannot be dropped or thrown, and right-clicking another player with it (both outside matches) opens the duel builder against them.

- **Play a Game**: a category row (All, Duels, UHC, Arena, Goals, Wool Games, Bed Wars, Races, Arcade) above the games, then a layout (the game's presets, which are **Solo** and **Teams of 2, 3 and 4** for UHC, Meetup and FinalUHC and the duel layouts for other games, or a custom layout of up to 21 teams of any size), a kit (the game's own first, then every other kit) and, for games with maps, a map (random by default; maps without room for the layout's teams are refused). **Match Setup** summarises these choices, shows the game's settings (operators can change the server-wide values there: left-click +1, right-click −1, shift-left +10, shift-right resets) and lists every slot: click an open slot to reserve a bot, click a bot to free it, shift-click an open slot to play on that team, and toggle whether you join at all. **Open Match** opens the match as yours through the same ownership rules and limits as `/minigames open`, joins you and reserves the bots.
- **UHC Scenarios**: the settings page of UHC, Meetup and FinalUHC (and the UHC variants) has a **UHC Scenarios** button listing every [scenario gamerule](#uhc-scenarios) with its current value; its icon lists the scenarios that are not at their default. Operators toggle them there (shift-right resets), with the same effect as `/gamerule`.
- **Open Matches**: every match with its status, map, owner and players. A match's page joins any team with room, watches, or leaves; its owner and operators can start it now, add or clear bot slots and stop it (shift-click, so a stray click cannot).
- **Duel Builder** (also bare `/duel`): game, layout, kit and map, then a lineup in team order with you first. Left-click a slot to choose an online player (busy players are shown but refused), right-click to put a bot there, click a filled slot to empty it; free-for-all adds and removes participants. **Send Challenge** sends the usual `/duel` request, and a lineup of bots only starts at once.
- **Lobby hotbar**: players waiting in a lobby get **Vote to Start**, the Game Menu and **Leave**; SkyWars lobbies add **Kits & Perks** (a bow) with the lobby's mode's kit menu and perk menu (toggles in Insane and Lucky Block, perk slots in Mini and Mega), which Match Setup also offers for SkyWars. `/minigames skywars` opens Kits & Perks for every mode.
- **Bed Wars**: in a match the Item Shop and Team Upgrades villagers open their shops, and the compass the Tracker Shop (see [Bed Wars](#bed-wars)); `/minigames bedwars quickbuy` opens the Quick Buy editor, `/minigames bedwars hotbar` the Hotbar Manager and `/minigames bedwars ultimate` the Ultimate picker.
- **Settings**: toggles the hourly feedback reminder, and **Send Feedback** posts a chat link that fills in `/feedback `.

Bot options appear only when a bot provider is installed and the game supports bots. Menus never hand out or accept items: plain and shift clicks run buttons, and drags, number keys, the offhand key, throws, double-click collecting and creative cloning do nothing, so nothing moves into or out of a menu.

## Duels

Any player can challenge others without game-master permission:

```text
/duel <game> <layout> <player> [<player> ...]
/duel accept <challenger>
/duel deny <challenger>
/duel cancel
/duel <game> <layout> bots <counts> [<player> ...]
```

List up to 15 other players. The participants are you followed by the listed players, and teams are filled in that order: `/duel classic 2v2 Bob Carol Dave` puts you and Bob against Carol and Dave. A fixed layout needs exactly as many players as it has slots; `ffa` needs at least one other player. Nobody may be listed twice, already be in a match, or be part of another pending duel, and each player can have only one pending request.

Each invited player gets one chat message with clickable `[Accept]` and `[Deny]` buttons; hovering `[Accept]` lists every player. Once everyone has accepted, the match opens with the game's own kit, everyone joins their team, and it starts. Duel matches are private: they are not announced, and nobody else can join them, though anyone can watch. A request is cancelled when anyone denies it, the challenger runs `/duel cancel`, a participant leaves the server, or 60 seconds pass without everyone accepting. If a player is no longer available when the last invitee accepts, the duel is cancelled and everyone is told why. The challenger owns the duel match, so it counts toward their [open-match limit](#running-matches) and they can stop it.

With a bot provider installed (see [Lobbies, votes and bots](#lobbies-votes-and-bots)), `bots <counts>` puts bots in some slots: one count per team, written like the layout, or a single count in a free-for-all. Each team's slots are filled with the listed players first, in order, then its bots; you take the first slot of team 1, so team 1 needs at least one slot without a bot. `/duel classic 1v2 bots 0v2` is you against two bots and starts at once, since nobody needs to accept; `/duel bridge 2v2 bots 1v1 Bob` puts you and a bot against Bob and a bot once Bob accepts.

## Lobbies, votes and bots

```text
/minigames vote
/minigames bots <match> add <count> [team]
/minigames bots <match> fill
/minigames bots <match> clear
/minigames bots <match> difficulty <easy|normal|hard|mixed>
```

`/minigames vote` votes to start the lobby you are waiting in now. Once most of its waiting players have voted (3 of 5, 2 of 3, 1 of 1), it starts with them; a player who leaves takes their vote along.

**UHC, Meetup and FinalUHC** lobbies also start on their own: `lobby_seconds` (default 30) after the first player started waiting, counting again from the start whenever the lobby empties. The sidebar and `/minigames list` show the time left, and every player who joins is told it. A full fixed layout starts at once, as in every game.

**Early starts fill with bots.** When a public lobby of any game starts before it is full, by a vote, `/minigames start`, the menu's **Start Now** or a lobby timer, its empty slots are filled with bots from the bot provider while the gamerule `brainage_minigames:fill_bots_on_early_start` is on (the default):

- a fixed layout fills every empty slot: one player voting in a Classic 1v1 plays a bot;
- a free-for-all fills up to the game's `lobby_size` (UHC, Meetup and FinalUHC, default 8, never past the map's spawns), or else every spawn of its map: SkyWars on the four-island `mesa` fills to 4, on `archipelago` to 8, Quake on `foundry` to 12, Grinch Simulator to 6; a game played in a generated box, with no fixed spawns, fills to **8**;
- Parkour and Ice Boat Racing, which bots cannot play, and private `/duel` matches (which start once everyone listed accepts) never fill; slots reserved with `/minigames bots` or `/duel ... bots` are filled either way.

Five of eight players waiting in a Meetup, three of them voting, start it with three bots. With `/gamerule brainage_minigames:fill_bots_on_early_start false` no game fills its empty slots, UHC, Meetup and FinalUHC included: their `lobby_seconds` timer still starts them with the players present, and `lobby_size` only matters while the rule is on. Without a bot provider nothing is filled either way: the match starts with the players who are there and needs at least two of them, so a player waiting alone keeps waiting after the timer, and the match starts as soon as a second player joins.

In any public lobby, players waiting in it, its owner and game masters can reserve slots for bots: on a team with `add <count> <team>`, on any team with `add <count>` (always the case in a free-for-all), every free slot of a fixed layout with `fill`, and none with `clear`. Reserved slots count towards the layout, so a lobby whose every slot has a player or a reserved bot starts at once; `/minigames open classic 1v2`, joining team 1 and `/minigames bots <match> add 2 2` is a 1v2 against two bots, and in a 2v3v4 any mix of players and bots on each team works. The bots are spawned when the match starts and join it exactly like players: teams, saved and restored state, kit. If the provider has fewer bots than reserved, the match starts with the ones it gave, as long as two participants on two teams play. A vote, `/minigames start` or a lobby timer fills reserved slots first, then the other empty slots as above. `difficulty` sets how the provider plays this match's bots; the default is `mixed`.

Bots are listed as `[BOT] <name>` in the tab list and on the sidebar, and leave once they are eliminated or the match ends. Parkour and Ice Boat Racing cannot be played by bots. Bot commands and `/duel ... bots` exist only while a bot provider such as SparringBots is installed; see the [integration contract](#integration-contract).

## Running matches

Any player can open a match, and then owns it:

```text
/minigames open <game> <layout> [kit] [nojoin]
/minigames start <match>
/minigames stop <match>
```

The owner can start and stop their match; game masters (permission level 2) can start and stop every match, and so can the server console and command blocks. A player who opens a match is put in its lobby, like the menu's **Join the Match** (on by default); a trailing `nojoin` only opens it, for hosts who run matches without playing, and a player already in a match must use `nojoin`. The console and command blocks only open matches. The announcement has a `[Join]` button and `/minigames list` names each match's owner. A player owns at most `brainage_minigames:max_open_matches_per_player` matches at once (a gamerule, default **1**, counting duels they challenged others to; `0` lets only game masters open matches); game masters have no limit. A stopped or finished match frees its slot. Spawning bots outside matches stays with Sparring Bots' own operator commands.

UHC, Meetup and FinalUHC pick their region from the biome map without generating anything, then find the lobby's dry ground and paste the UHC deathmatch arena over the next ticks while the chunks generate off the server thread. Players who join before the lobby is ready (usually a second or two) wait where they are and are moved there as soon as it is; the match cannot start before then.

A layout is `ffa` (everyone for themselves) or **any number of teams, each of any size**, written as sizes separated by `v`, e.g. `1v1`, `2v2`, `1v2`, `2v3v4`, `1v1v1v1`. These are examples, not a fixed list: the parser accepts 2–100 teams of 1–100 players each. A game's map may limit the number of teams or total players; `/duel` also accepts at most 15 invitees. Fixed layouts start by themselves when every slot is filled. `/minigames start` starts a lobby now with the players waiting, filling bot slots as a [vote](#lobbies-votes-and-bots) does; it needs two participants on two teams, which are spread so that no team is left empty. The optional kit replaces the game's kit, for example `/minigames open classic 2v2 brainage_minigames:kits/instant_crossbow`. `/minigames help` explains the layout syntax and main commands.

## Hub

The hub is the protected spawn area players return to between matches. On its first start, a world without one gets a hub at the world spawn: a lit stone platform 25 blocks across with a low wall and a sign listing the commands, and the world spawn moves onto it. A data pack (or the world's `generated` folder, e.g. from a structure block) can provide the structure template `brainage_minigames:hub` to be built instead, centred on the spawn at ground height.

Inside the hub's radius (32 blocks by default), players who are not in a match:

- play in adventure mode; survival players switch to it on entering and back to survival on leaving;
- cannot break or place blocks, unless they are game masters in creative mode;
- take no damage (except from `/kill` and the void) and stay fed and healed.

```text
/hub              (or /spawn) go to the hub; in a match, this leaves it first
/hub info         game masters: show the hub's position, radius and state
/hub set [radius] game masters: make where you stand the hub spawn, optionally with a new radius
/hub radius <n>   game masters: change the protected radius (1–1024)
/hub build        game masters: build the default hub (or the template) where you stand and move the hub there
/hub on | off     game masters: turn the hub's rules, returns and /hub on or off
```

While the hub is on, everyone leaving a match, including at server shutdown, is restored at the hub spawn with their saved inventory and state. The hub's settings are stored in the world's command storage (`brainage_minigames:hub`); turning it off keeps them, and moving the spawn with `/hub set` or `/hub build` also moves the world spawn.

## Feedback

Every player can send feedback with `/feedback <message>`. Each message is appended as one JSON object per line to `<world>/brainage_minigames/feedback.jsonl`, with the time (UTC, ISO-8601), the player's UUID and name, dimension and position, the match they were in (`id`, `game`, `layout`, `phase`) if any, and the message. It is also written to the server log at INFO with the prefix `[Feedback]`, so `grep '\[Feedback\]' logs/latest.log` lists it.

Players are told about it in three places:

- A welcome message on their first join.
- A reminder every 60 minutes of play, skipped while they are playing in a match, with clickable `[Send feedback]` and `[Turn off reminders]` buttons. `/feedback reminders off` turns them off and `/feedback reminders on` back on; the choice is stored per UUID in the world. `/gamerule brainage_minigames:feedback_reminder_minutes <minutes>` changes the interval for everyone; `0` turns the reminders off.
- The server list MOTD, which operators set in `server.properties`, for example:

```properties
motd=\u00A76Brainage Minigames playtest\u00A7r \u00A77- \u00A7eideas and bugs: /feedback <message>
```

## Settings

```text
/minigames settings <game>
/minigames settings <game> <setting> <value>
/minigames settings <game> <setting> reset
```

Settings are stored per world and apply to matches opened afterwards. Every game has `countdown_seconds`, `time_limit_minutes` (`0` disables it) and `natural_regeneration` (`1` or `0`, applying only to participants). At timeout, score-based games and races award the win to the highest-scoring surviving team, with a draw between tied leaders; elimination games draw between surviving teams unless UHC's kill tiebreak is enabled. UHC's border and deathmatch settings and defaults are listed [above](#uhc-border-modes-deathmatch-and-daylight); `grace_period_minutes` defaults to 10. Boxing adds `hits_to_win`; Combo adds `hit_delay_ticks` (1–10 ticks between hits; vanilla is 10). Meetup adds `border_start_size` (100), `first_shrink_seconds` (120), `shrink_interval_seconds` (60), `shrink_step` (25 blocks off the whole width), `final_size` (10) and `shrink_duration_seconds` (10; `0` is instant); FinalUHC adds `border_size` (100).

UHC, Meetup and FinalUHC add `lobby_seconds` (30; how long after the first player started waiting a lobby that is not full starts, `0` leaves it to a full lobby, a vote or `/minigames start`) and `lobby_size` (8; how many participants a free-for-all lobby is filled up to with bots when it starts early, while the `brainage_minigames:fill_bots_on_early_start` gamerule is on); see [Lobbies, votes and bots](#lobbies-votes-and-bots).

Meetup adds `kit_rerolls` (1; kit rerolls per player during the countdown) and the `adaptive_*` thresholds of `meetup_adaptive_border`; see [Meetup](#meetup).

Bed Wars and its modes add `respawn_seconds` (5; how long a killed player whose bed stands watches before respawning), `event_seconds` (360; the time between generator upgrades and on to bed destruction) and `sudden_death_seconds` (600; from bed destruction to Sudden Death); `time_limit_minutes` is 50, when the match ends in a draw. Swappage adds `swap_min_seconds` (60) and `swap_max_seconds` (120); see [Bed Wars](#bed-wars).

Capture the Wool adds `respawn_seconds` (5; how long a killed player watches before respawning, `0` respawns at once) and `wool_return_seconds` (10; how long a dropped wool lies before it returns to its wool room); see [Capture the Wool](#capture-the-wool).

Grinch Simulator adds `presents` (150; how many presents a match hides at random among its map's present spots, at most one per spot) and plays 4 minutes by default (`time_limit_minutes`); see [Grinch Simulator](#grinch-simulator).

## Kits

Kits are loot tables. The bundled ones are `brainage_minigames:kits/` followed by `barebones`, `battle_rush`, `bedwars`, `bow`, `bow_spleef`, `boxing`, `bridge`, `build_uhc`, `capture_the_wool`, `classic`, `combo`, `final_uhc`, `gapple`, `instant_crossbow`, `instant_firework_crossbow`, `meetup`, `no_debuff`, `parkour`, `pearl_fight`, `quake`, `skywars`, `skywars_mega`, `skywars_mini`, `spleef` and `uhc_starter`, plus `brainage_minigames:empty`. Armour in a kit is worn automatically. Menus and `/minigames kit list` show each bundled kit by name (UHC Starter, BuildUHC, FinalUHC, No Debuff, ...) with a one-line description of what it gives; other kits read like the last part of their id, described as edited on the server or added by a data pack.

Kits can be edited in game with game-master permission:

```text
/minigames kit edit <kit>
/minigames kit give <kit> [players]
/minigames kit delete <kit>
/minigames kit list         every kit offered for any game: name, id and contents
```

`edit` opens a six-row container; put the items in it and close it to save. Editing a bundled kit creates a world-specific override, and deleting the override restores the bundled kit.

## SkyWars

SkyWars is played as Hypixel's **Insane** mode (`skywars`), with its kits, perks and chest loot; Mini, Mega and Lucky Block are their own games, described under [SkyWars modes](#skywars-modes). Every team starts in a glass cage above its own floating island (a cage per player where an island holds a team of two); the cages are built when the map is pasted and disappear when the countdown ends, dropping everyone three blocks onto their island. When the cages open each player gets their chosen **kit** (the mode's default when they chose none) and their **perks** take effect; everyone plays in survival and can break and place blocks anywhere inside the map's bounds, but not outside them. Hunger stays full, ender pearls do no damage, mined ores drop smelted and mined drops go straight into the inventory, as on Hypixel. Falling below the map's `void` marker or dying eliminates; the last team standing wins, and the sidebar shows each team's kills, the mode, the next refill and the map.

The maps mark chests `brainage_minigames:skywars/island` (three per island) or `brainage_minigames:skywars/mid` (the mid island). At the start every chest is emptied and rolls the mode's table, `brainage_minigames:skywars/insane/island` or `skywars/insane/mid`; each refill (`first_refill_seconds`, default 180, and `second_refill_seconds`, default 300, 0 disables either) rolls the same table into every chest that still exists. Island chests always hold 32 planks or stone and an iron or diamond sword, plus usually diamond or iron armour, a Power I or III bow with arrows or throwables, a potion and a tool or bucket; mid chests hold Protection IV and Projectile Protection diamond armour, a Sharpness I Fire Aspect II diamond sword or a Power V bow, splash potions, three or five ender pearls, golden apples, TNT and enchanted tools. The item lists follow Hypixel's patch notes and players' chest lists; the weights are this mod's ([SkyWars catalog](docs/SKYWARS.md)).

**Kits.** All 48 Insane kits from Hypixel's kit menu, with the contents it lists: Default (iron pickaxe, axe, shovel, sword and chestplate), Armorer, Armorsmith, Ecologist, Healer, Knight, Pro, Scout, Batguy, Disco, Energix, Cactus, Frog, Grenade, Farmer, Baseball Player, Enchanter, Hunter, Pharaoh, Snowman, Speleologist, Warlock, Engineer, Pig Rider, Sloth, Magician, Enderchest, Fisherman, Princess, Cannoneer, Enderman, Guardian, Archeologist, Fallen Angel, Salmon, Slime, Jester, Zookeeper, Pyro, Troll, Golem and the mythical End Lord, Monster Trainer, Nether Lord, Fishmonger, Thundermeister, Chronobreaker and Cryomancer. Kit items with an ability (Corrupted and Time Warp Pearls, Echo, Ice Bridge, Capture, Throwable Spawn and Mystery Eggs, Fishmonger Silverfish, Charged Creeper Egg, Thundermeister Axe, Fire Nugget, My Precious) carry `brainage_minigames:skywars_item` in their custom data. Choose a kit in the **Kits & Perks** menu (the bow in a SkyWars lobby's hotbar or in **Match Setup**, which show the lobby's mode, or `/minigames skywars`, which shows every mode), or with `/minigames skywars kit <kit>`; the choice is saved per UUID and mode. A match opened with an explicit kit gives that kit instead. Bots that never chose a kit pick a random one of Armorer, Knight, Pro, Scout, Baseball Player, Speleologist, Pig Rider, Farmer, Salmon, Ecologist, Fallen Angel or Golem each match.

**Perks.** All 31 Insane perks, all active at once, as Hypixel's "Toggle Insane Perks": Bridger, Bulldozer, Juggernaut, Knowledge, Lucky Charm, Mining Expertise, Resistance Boost, Savior, Annoy-o-mite, Arrow Recovery, Blazing Arrows, Environmental Expert, Fat, Speed Boost, Barbarian, Black Magic, Diamondpiercer, Frost, Marksmanship, Necromancer, Robbery, Apothecary, Diamond In The Rough, Double-Edged Sword, Dragon's Pledge, Ender End Game, Fortune Teller, Fruit Finder, Hide and Seek, Librarian and Tenacity. Each is on unless the player turns it off (in the menu, or `/minigames skywars perk <perk> <true|false>`), except Double-Edged Sword and Dragon's Pledge, whose drawbacks keep them off until turned on. Toggles are saved per UUID and mode and are read when the cages open.

| Gamerule | Default | Effect |
| --- | --- | --- |
| `skywars_max_all_kits` | `true` | Every kit of every mode is unlocked; `false` leaves each player the mode's default kit (Default; Champion in Mini) plus the kits an operator granted |
| `skywars_max_all_perks` | `true` | Every perk is unlocked with every upgrade level (Big Brain, Luckier Charm, Meticulous Miner, Steel Quiver, Adrenaline, Sorcerer's Spell, Chilled Quiver, Diamond In The Rough tiers) and Mini's and Mega's seventh perk slot is open; `false` leaves only granted perks, at their base numbers, and six slots |

There are no SkyWars coins: with a rule off, operators unlock kits and perks with `/minigames skywars grant <players> kit <kit>` or `perk <perk>` and take them away with `revoke`; grants are saved per UUID and kept while the rules are on. `/minigames skywars kit` and `/minigames skywars perk` list what a player owns and has on. The [SkyWars catalog](docs/SKYWARS.md) gives every kit's and perk's numbers, their sources, and what is this mod's choice where Hypixel publishes nothing.

Free-for-all and any team layout up to the map's island count work (each island is one team); a layout with more teams than any map has islands is refused when the match opens, and a free-for-all lobby is full once every island is taken. A random map with enough islands is picked:

| Map | Islands | Notes |
| --- | --- | --- |
| `frostbite` | 2 | Snowy spruce islands 24 blocks either side of a mid, each with an ice spike; two small ice-spike islets on the flanks; for duels. |
| `mesa` | 4 | Red sand and terracotta islands 26 blocks from mid in the four directions, each with a banded terracotta hoodoo; four hoodoo islets on the diagonals. |
| `archipelago` | 8 | Grassy oak islands in a ring 34 blocks from a mid with six chests, each with a mossy ruin; four ruin islets inside the ring; team numbers alternate around the ring so fewer teams are spread out. |

Every island has a low broken wall in front of its front chest, facing the mid, and the mid has a broken wall on each side of its altar between lantern-topped rim pillars. The islets carry no chests.

The maps are generated by `python3 tools/maps/skywars.py` into `data/brainage_minigames/structure/maps/skywars/` (Insane and Lucky Block), `skywars_mini/` and `skywars_mega/`. They are larger than the 48-block limit of an in-game structure block, so edit them through the script (or in parts).

### SkyWars modes

Each mode is its own game: open it in **Play a Game** (the Arena category) or with `/minigames open skywars_mini|skywars_mega|skywars_lucky <layout>`. Kits, perks and choices belong to the mode, and the kits item and Match Setup open the lobby's mode; `/minigames skywars mini ...` and `/minigames skywars mega ...` are the Mini and Mega versions of the kit, perk, grant and revoke commands. The [SkyWars catalog](docs/SKYWARS.md) lists every kit, perk, chest table and lucky block outcome with its source.

**Mini** (`skywars_mini`) is Hypixel's Mini SkyWars, the former Ranked SkyWars: four-player games on small maps. Its 11 kits are Armorer, Athlete, Blacksmith, Bowman, Champion (the default), Healer, Hound, Magician, Paladin, Pyromancer and Scout, each with its own perk built in (for example Champion's kills add a Sharpness level to its sword, Hound starts with a tamed wolf and its kills spawn more). On top of the kit's perk, players choose perks into seven perk slots as in Mega (six usable, plus the seventh while `skywars_max_all_perks` is on): the slots start empty and offer the 12 Mega slot perks. Hypixel's Mini refuses every slot, so this is this mod's choice. Everyone has the global perks Juggernaut and Telekinesis. Island chests roll `skywars/mini/island`, with 64 blocks instead of Insane's 32, and mid chests `skywars/mini/mid`. Layouts offered first: 1v1v1v1, 1v1, 1v1v1, 2v2 and free-for-all. `/minigames skywars mini kit <kit>` chooses a kit and `/minigames skywars mini perk <slot> <perk|clear>` sets a perk slot.

**Mega** (`skywars_mega`) is Hypixel's Mega SkyWars Doubles: teams of two (the first layouts offered are twelve and eight teams of two) on a large map whose islands each hold a team of two, with a cage per player. Its 16 kits are maxed as on Hypixel: Default, Armorer, Armorsmith, Baseball Player, Cannoneer, Enderman, Fisherman, Healer, Hellhound, Hunter, Knight, Paladin, Pyro, Scout, Skeletor and Witch. Perks are chosen into seven perk slots: six usable, plus the seventh while `skywars_max_all_perks` is on; Hypixel's six defaults (Bridger, Lucky Charm, Rusher, Arrow Recovery, Blazing Arrows, Tank) fill a new player's slots, and Mining Expertise, Environmental Expert, Notoriety, Marksmanship, Necromancer and Black Magic can replace them. Juggernaut and Telekinesis are global. In **Select Mega Perks** left-click a slot to choose its perk and right-click to empty it; `/minigames skywars mega perk` lists the slots and `/minigames skywars mega perk <slot> <perk|clear>` sets one (a perk already in another slot moves). Chests roll `skywars/mega/island` and `skywars/mega/mid` (Insane's mid with one enchantment level less). 15-minute limit. The map pastes over several seconds of server ticks, two chunks a tick, like every map.

**Lucky Block** (`skywars_lucky`) is Insane SkyWars, with the Insane kits, perks, choices, chest tables and maps, plus lucky blocks: yellow glazed terracotta blocks, two on every team's island near its spawn and four around the mid. Breaking one never drops the block; it rolls one of 22 outcomes (55% good items such as diamond armour, golden apples, pearls or a totem; 30% bad events such as lightning, three zombies, lit TNT, blindness, levitation or cobwebs; 15% fun ones such as fireworks, a wolf pack, chickens or a falling anvil) and tells the player which. Every refill puts back the broken lucky blocks whose spot is empty.

| Map | Mode | Islands | Notes |
| --- | --- | --- | --- |
| `blossom` | Mini | 4 | Cherry islands 18 blocks from a small mid with four chests, each with a mossy ruin. |
| `oasis` | Mini | 4 | Sand and sandstone islands with jungle trees and terracotta hoodoos, 19 blocks from the mid; two hoodoo islets. |
| `highlands` | Mega | 12 (two players each) | Podzol and dark oak islands in a ring 72 blocks from a large mid with six chests; four sub-mid islands 36 blocks out with two mid chests each and eight ruin islets between them. |

## Spleef

Spleef follows Hypixel's Spleef Duels. Everyone gets the `brainage_minigames:kits/spleef` kit, an unbreakable Efficiency V diamond shovel that breaks snow instantly, and plays in survival. Only shovel-mineable blocks (snow, clay) inside a map's `floor_<n>` regions can be broken, and only with a shovel in hand; the walls and everything else stay. Dug blocks drop nothing: the digger gets `snowballs_per_block` snowballs (default 2) instead, up to `snowball_cap` held (default 16, Hypixel's cap). A snowball thrown by a player breaks the floor block it lands on and knocks back players it hits; nothing else does damage and nobody can place blocks. Hunger stays full. Falling below the map's `void` marker eliminates, and the last team standing wins. `camp_seconds` (default 0, off) breaks the floor block under a player who stands on it that long.

Spleef works with free-for-all and any team layout up to the map's eight spawns; a random map with room for the layout is picked.

| Map | Floors | Notes |
| --- | --- | --- |
| `glacier` | 3 | Round packed-ice tower, 25-block snow floors six blocks apart (the middle one ringed with clay), sea lanterns and spruce pillars. |
| `lantern_pit` | 2 | Square deepslate pit with 21-block snow floors seven blocks apart, patterned with clay (rings on the top floor, a star on the lower one) and lit by froglights; buttressed walls, a lantern tower at each corner and spectator stands on two sides. |

Maps are generated by `python3 tools/maps/spleef.py` into `data/brainage_minigames/structure/maps/spleef/`. A floor region may cover the walls around its floor, since only snow-like blocks in it can be removed.

## Bow Spleef

Bow Spleef follows Hypixel's Bow Spleef Duels (itself from the TNT Games mode): an arena of TNT floors, a round one about 41 blocks across with walls seven blocks high, the void eight blocks below and an invisible ceiling fourteen above. Players are in adventure mode with the `brainage_minigames:kits/bow_spleef` kit (an unbreakable Infinity and Flame bow and one arrow). An arrow landing on TNT inside a `floor_<n>` region removes that block without lighting it and is used up; arrows never hurt players and nothing else does damage. Each player also gets the TNT Games perks, as hotbar items whose stack size is the uses left:

| Perk | Item | Use | Setting (default) |
| --- | --- | --- | --- |
| Double jump | Feather | Double-tap jump in mid-air, or use the feather: flings you the way you look, upwards when you look up. Re-armed half a second after each use. | `double_jumps` (5) |
| Triple shot | Blaze rod | Use it: three flaming arrows side by side. Hypixel fires it with a left click, which the server cannot see, so it has its own item here. | `triple_shots` (5) |
| Repulsor | Magma cream | Sneak, or use the item: flings opponents within 4.5 blocks away, downwards if they are below you. | `repulsors` (5) |

Bow Spleef works with free-for-all and any team layout up to the map's eight spawns; a random map with room for the layout is picked. Falling below the map's `void` marker eliminates, and the last team standing wins.

| Map | Floors | Notes |
| --- | --- | --- |
| `ember_court` | 1 | Round 41-block TNT floor inside blackstone and nether brick walls with shroomlight. |
| `twin_decks` | 2 | A 27-block TNT deck eight blocks above a 39-block one, ringed by prismarine and sea lanterns. |

Maps are generated by `python3 tools/maps/bow_spleef.py` into `data/brainage_minigames/structure/maps/bow_spleef/`. A floor region may cover the walls around its floor, since only TNT in it can be removed. The maps are larger than the 48-block limit of an in-game structure block, so edit them through the script (or in parts).

## Bridge

Bridge follows Hypixel's The Bridge (and Minemen's Bridge, whose kit is the same). Each team has an island with a goal, a hole five blocks across with a crying-obsidian floor, and a glass cage above its spawn. Jumping into another team's goal scores for your team: everyone sees who scored and the score, the map is rebuilt as it was (placed blocks, arrows and dropped items disappear), every player is reset with a full kit into their team's cage, and the cages open after `cage_seconds` (default 5) with a countdown on the action bar. The cages are rebuilt at once and the rest of the map two chunks per tick, so a goal never holds up the server; the action bar says "Rebuilding the map..." until it is whole, and only then does the countdown run. Players cannot move, fight, build or be hurt while caged. The countdown before the first round is the match's `countdown_seconds`, also in the cages. Jumping into your own goal scores nothing and sends you back to your spawn.

Dying, including falling below the map's void height or being knocked off, never eliminates: you respawn at once at your team's spawn with your kit refilled, and whoever last hit you gets the kill. You can only break blocks placed during the current round, and place blocks only inside the map's `build` regions (a few blocks above and below the bridge, stopping short of the goals) and never in a goal. Hunger does not drop. The first team to `goals_to_win` goals wins (default 5); when the time limit runs out the team with the most goals wins, or the leaders draw.

The `brainage_minigames:kits/bridge` kit is Hypixel's: iron sword, bow, one arrow, Efficiency II diamond pickaxe, two stacks of clay, eight golden apples and a leather chestplate, leggings and boots. The clay (and any terracotta, wool, stained glass or concrete in whatever kit the match uses) is turned into the team's colour and leather armour is dyed to match. The arrow comes back 3.5 seconds after you run out, and a golden apple heals you fully on top of its absorption hearts.

The sidebar shows each team's goals as dots out of the target and its kills, the goals needed, the map and the round. Bridge works with any team layout up to the map's goals; a map with exactly as many goals as the layout has teams is preferred, and free-for-all uses the map with the most goals.

| Game | Map | Teams | Notes |
| --- | --- | --- | --- |
| Bridge | `grove` | 2 | Grassy oak islands 30 blocks apart, joined by a one-block andesite bridge with a small lantern-lit resting platform in the middle. Mossy stone-brick bases with oak pillars. |
| Bridge | `basalt` | 2 | Crimson nylium and blackstone islands 34 blocks apart with shroomlight basalt pillars; the blackstone-brick bridge crosses two basalt stepping pillars. Blackstone-brick bases with basalt pillars. |
| Bridge | `compass` | 4 | Four birch islands around a stone-brick plaza, each 16 blocks out along its own bridge; four teams, each of any size accepted by the layout parser. Stone-brick bases with birch pillars. |

Every team's island has a base: the bridge lands under a gate arch, low crenellated walls run along both flanks, the goal has a raised terrace on either side, and behind it a back wall with the team's colour stands between two corner towers. The path from the gate to the goal stays open, and a GameTest checks on every Bridge map that a player dropping from each opened cage can run and jump over the map's own blocks into every other team's goal.

Maps mark `spawn <team>` inside the cage, `region cage_<team>` around the cage (cleared when a round starts), `region goal_<team>` over the goal hole, `region build` where blocks may be placed and `void`. They are generated by `python3 tools/maps/bridge.py` into `data/brainage_minigames/structure/maps/bridge/`; the maps are larger than the 48-block limit of an in-game structure block, so edit them through the script (or in parts).

## Battle Rush

Battle Rush follows Minemen's Battle Rush, whose match pages show only 64 wool and shears in every player's inventory: the `brainage_minigames:kits/battle_rush` kit is exactly that, so fights are fist knockback duels. Its islands are smaller and closer and nothing links them, so each round starts with both players rushing across with wool. Everything else works as in Bridge; the first team to 3 goals wins, with a 10-minute limit.

Battle Rush uses [Bridge's scoring, cages, respawns, building rules and sidebar](#bridge), with its own kit, map and goal target. Any team layout up to the map's goals works.

| Game | Map | Teams | Notes |
| --- | --- | --- | --- |
| Battle Rush | `lilypond` | 2 | Mossy water-garden islands 20 blocks apart: lily ponds either side of the cage, a broken shrine wall with lantern pillars behind each three-block goal, and azalea bushes and a flowering azalea for cover. |
| Battle Rush | `driftwood` | 2 | Sandy beach islands 24 blocks apart: a railed boardwalk wraps the back and sides of each goal, a beached boat with a sail stands beside the spawn, and a palm leans over a tide pool. |

Maps use the [Bridge marker grammar](#bridge) and are generated by `python3 tools/maps/battle_rush.py` into `data/brainage_minigames/structure/maps/battle_rush/`.


## Capture the Wool

Capture the Wool follows Hypixel's Wool Games mode of that name. Two teams each have a base at one end of the map: from the front, a gate wall with an opening for every lane, the team's monument, its spawn and, at the back, a courtyard between two wool rooms, each with one wool on a pedestal. A team defends the wools in its own rooms and steals the other team's: breaking an enemy wool on its pedestal takes it (nothing drops; the pedestal stays empty while the wool is away). Carry it back and place it on its slot on your monument, a hole in the monument with glass of the wool's colour behind, beside and above it; the slot takes only that wool, and wool can be placed nowhere else. A placed wool stays for good and nobody can break it. The first team to fill every slot of its monument wins (two wools on the bundled maps).

- **Wool rooms** are closed to the team that keeps them: a player who steps into one of their own team's wool rooms is put back where they last stood outside it. Nobody can build inside any wool room, on a wool's pedestal or in a monument slot.
- **Dropped wool:** a killed carrier drops the wool where they died, glowing. A teammate who picks it up carries it on; a player of any other team who touches it returns it to its room at once. Otherwise it returns after `wool_return_seconds` (default 10), and at once if it falls into the void or its carrier leaves the match. A carrier who throws the wool down drops it the same way; wool put anywhere else, such as in a chest, returns to its room at once.
- **Respawning:** a killed player watches from above the map in spectator mode for `respawn_seconds` (default 5; 0 respawns at once), then respawns at their team's spawn with the kit refilled. Kills, thefts, drops, pickups, returns and captures are all announced in chat; a capture also shows a title to everyone.
- **Building:** the kit's planks can be placed anywhere in the map's `build` region outside the wool rooms, and only blocks placed during the match can be broken; the map itself cannot. Hunger does not drop, so health comes back through natural regeneration.
- **Time limit:** after `time_limit_minutes` (default 20) the team with the most wool placed wins; between teams level on that, the one carrying the most enemy wool wins, and teams level on both draw.

The `brainage_minigames:kits/capture_the_wool` kit is the one Hypixel's Capture the Wool layout editor shows: an unbreakable stone sword, iron pickaxe, bow and iron axe, three stacks of oak planks, a golden apple and 8 arrows, in that hotbar order. Players also wear unbreakable leather armour dyed in their team's colour (Hypixel players wear armour too, but its kind is not in the capture).

The sidebar shows each team's monument after its name, one square per slot in the wool's colour: filled once the wool is placed, ▣ while a teammate carries it, empty otherwise. Below, a line for every wool away from both its room and its slot says who carries it, or how many seconds until a dropped one returns. Capture the Wool is listed under Wool Games in the menus, offers 1v1 to 6v6, and plays with `/duel` and bot fill like the other team games; bots from a [bot provider](#lobbies-votes-and-bots) are told where every wool is through the integration contract below.

| Game | Map | Teams | Notes |
| --- | --- | --- | --- |
| Capture the Wool | `bastions` | 2 | Two stone-brick bases on floating islands, 68 blocks apart. A mid bridge runs from gate to gate through a ruined watchtower on a middle island; two side bridges along the flanks pass small mossy platforms with broken walls halfway, joined to the middle island by narrow cross bridges. Red keeps orange and yellow, blue keeps lime and cyan. |
| Capture the Wool | `timberline` | 2 | Two timber forts on grassy islands with no straight way between them: each fort's two gates open onto flank bridges that land on a wide orchard in the middle, crossed by hedges and trees around a roofed well, so every crossing passes through the orchard. Red keeps pink and light blue, blue keeps lime and purple. |

The maps are point-symmetric, so neither side has an advantage. They mark `spawn <team>`, `point wool_<team>_<colour>` (where the game puts a wool team `<team>` keeps, on a pedestal inside one of its wool rooms), `point monument_<team>_<colour>` (the slot on team `<team>`'s monument for that colour), `region woolroom_<team>_<n>` (the inside of a wool room), `point guard_<team>` (where bots that defend stand; without one, the team's first spawn), `region build`, `void` and `lobby`; colours are dye names such as `light_blue`. A map is refused when a wool has no slot or a slot no wool, a team would place its own wool, a wool lies outside its team's wool rooms, or a team has no monument or no wool to defend, and a match only picks a map for exactly as many teams as its layout has. They are generated by `python3 tools/maps/capture_the_wool.py` into `data/brainage_minigames/structure/maps/capture_the_wool/`; the script refuses a map where a wool or its slot cannot be walked to from the other team's spawn. The maps are larger than the 48-block limit of an in-game structure block, so edit them through the script (or in parts).

## Bed Wars

Bed Wars (`bedwars`) follows Hypixel's. Every team starts on its own island with a bed, an island generator and two shopkeepers. While a team's bed stands, a killed player watches for `respawn_seconds` (5) and respawns at the team's spawn; once it is broken their next death is a **FINAL KILL** and they are out. A killer takes the iron, gold, diamonds and emeralds the victim carried. The last team with a player left wins; at `time_limit_minutes` (50) the remaining teams draw.

- **Layouts**: Solo (8 teams of 1), Doubles (8 of 2), 3v3v3v3, 4v4v4v4 and 4v4, as Hypixel's queues; any other layout can still be typed. A match takes the map with the fewest bases that holds its teams: eight-team maps for Solo and Doubles, four-team maps for 3v3v3v3 and 4v4v4v4, two-team maps for 4v4. Teams of up to two pay Hypixel's Solo/Doubles prices, bigger teams its 3v3v3v3/4v4v4v4 prices. A team nobody joined has no bed. Obsidian is not sold with two teams, as in Hypixel's 4v4.
- **Beds**: only another team's players can break a bed (a player is told "You can't destroy your own bed!"); explosions never do. Breaking one takes both halves, drops nothing and is announced ("BED DESTRUCTION > Blue Bed was destroyed by ..."); its team sees "BED DESTROYED! You will no longer respawn!". Right-clicking a bed places the held block against it instead of sleeping.
- **Building**: blocks can be placed anywhere inside the map's build region except on the generators and shopkeepers; only blocks placed during the match can be broken. TNT lights as it is placed. TNT and fireballs blow up placed blocks only, never blast-proof glass (stained glass), beds or the map. A team's chest opens only for that team while any of it is in the game.
- **Generators**: each island generator drops iron and gold into one pile on it. With Solo/Doubles prices it drops an ingot of iron every 1.5 seconds, inside the 0.6 to 0.8 a second [players measured](https://hypixel.net/threads/tool-guide-generator-speeds.3296403/) on most Solo/Doubles maps; with bigger teams one a second, gold every eight seconds, and at most 48 iron and 16 gold lying on it are this mod's choice. Diamond generators drop a diamond every 30, 23 and 12 seconds at Tiers I, II and III (30 is the [wiki's](https://hypixel.fandom.com/wiki/Bed_Wars); 23 and 12 are [players' timings](https://hypixel.net/threads/emerald-and-diamond-generators-speeds-depending-on-their-tier.4316840/)), emerald generators an emerald every 65, 50 and 35 seconds (65 is the wiki's; 50 and 35 are this mod's choice); at most 4 diamonds and 2 emeralds lie on one with Solo/Doubles prices, 8 and 5 otherwise, as the wiki gives them. Floating text over each says its tier and when it spawns next.
- **Splitting**: every player of the team standing on its island generator picks up the whole pile of iron and gold, each their own, as Hypixel's [generator splitting](https://hypixel.net/threads/guide-how-to-split-the-generator-in-bedwars.3565332/) works; an enemy standing there alone takes it. Emeralds from an Emerald Forge, and anything dropped onto a generator, are picked up as usual.
- **Events**, every `event_seconds` (360, Hypixel's 6 minutes): Diamond II, Emerald II, Diamond III, Emerald III, then **Bed Destruction** breaks every bed; `sudden_death_seconds` (600) later **Sudden Death** sends an ender dragon for every remaining team (two with Dragon Buff) that never hurts its own team. With two teams (4v4) the border closes in instead, as Hypixel's 4v4: over five minutes from the map's edge to 10 blocks from its middle (this mod's choice), a heart a second outside it. The sidebar shows the next event and how long until it, and after each team's name ✔ while its bed stands, ✘ once it is out, else how many of it are left.
- **Equipment**: everyone spawns with a wooden sword, unbreakable leather armour in their team's colour and a compass (see Tracker below). Armour bought replaces the leggings and boots for good. Pickaxes and axes are tiered: each purchase buys the next tier, a death loses one tier, and nobody drops below the first. A bought sword replaces the wooden one and is lost on death; shears are kept.

### Item Shop

Right-click the **ITEM SHOP** villager (any team's). The menu is Hypixel's: category tabs along the top (Quick Buy, Blocks, Melee, Armor, Tools, Ranged, Potions, Utility, Rotating Items), a separator row with a lime pane under the open tab, and the items below; Quick Buy's bottom row also holds the **Tracker Shop** (left) and **Hotbar Manager** (right) buttons, as in the [wiki's shop menus](https://hypixel.fandom.com/wiki/Bed_Wars#In-game_shops). Each item shows its price, coloured by currency, and says whether the player can afford it; clicking buys it. Prices are the Hypixel wiki's shop menus, with the forum's later balance changes:

| Item | Solo/Doubles | 3v3v3v3/4v4v4v4/4v4 | Notes |
| --- | --- | --- | --- |
| Wool ×16 | 4 iron | 4 iron | Team colour |
| Hardened Clay ×16 | 12 iron | 12 iron | Team colour |
| Blast-Proof Glass ×4 | 12 iron | 12 iron | Team colour; immune to explosions |
| End Stone ×12 | 24 iron | 24 iron | |
| Ladder ×8 | 4 iron | 4 iron | |
| Wood ×16 | 4 gold | 4 gold | Oak planks |
| Obsidian ×4 | 4 emeralds | 4 emeralds | Not with two teams |
| Packed Ice ×8 | 8 iron | 8 iron | A rotating item since 2023, permanent since September 2025 ([rotation log](https://hypixel.net/threads/bedwars-item-rotation-log.6013756/)) |
| Stone / Iron / Diamond Sword | 10 iron / 7 gold / 4 emeralds | 10 iron / 7 gold / 3 emeralds | |
| Stick (Knockback I) | 5 gold | 5 gold | |
| Permanent Chainmail / Iron / Diamond Armor | 30 iron / 12 gold / 6 emeralds | the same | Leggings and boots |
| Pickaxe: Wooden (Eff I), Iron (Eff II), Golden (Eff III, Sharpness II), Diamond (Eff III) | 10 iron, 10 iron, 3 gold, 6 gold | the same | Tiered |
| Axe: Wooden (Eff I), Stone (Eff I), Iron (Eff II), Diamond (Eff III) | 10 iron, 10 iron, 3 gold, 6 gold | the same | Tiered |
| Permanent Shears | 20 iron | 20 iron | |
| Arrow ×6 / Bow / Bow (Power I) / Bow (Power I, Punch I) | 2 gold / 12 gold / 20 gold / 6 emeralds | the same | |
| Speed II (0:45) / Jump V (0:45) / Invisibility (0:30) potions | 1 / 1 / 2 emeralds | the same | Complete Invisibility: armour and held items are hidden from everyone else too |
| Golden Apple | 3 gold | 3 gold | |
| Bedbug | 30 iron | 30 iron | A snowball that lands as three silverfish of your team for 15 seconds |
| Dream Defender | 120 iron | 120 iron | An iron golem of your team for 4 minutes |
| Fireball | 40 iron | 40 iron | Right-click launches it; it blows up where it hits |
| TNT | 4 gold | 8 gold | |
| Ender Pearl | 4 emeralds | 4 emeralds | |
| Water Bucket | 3 gold | 6 gold | |
| Bridge Egg | 1 emerald | 1 emerald | Leaves a three-wide trail of your wool under its flight |
| Magic Milk | 4 gold | 4 gold | No traps go off for you for 30 seconds |
| Sponge ×4 | 3 gold | 6 gold | |
| Compact Pop-up Tower | 24 iron | 24 iron | A wool tower with a ladder where you aim |

**Quick Buy** is the first tab. Every player starts with the layout of the captured "Edit Quick Buy" menu (21 slots, the third empty): Wool, Stone Sword, —, Wooden Axe, Bow, Bridge Egg, TNT / Wood, Iron Sword, Permanent Iron Armor, Wooden Pickaxe, Arrow, Jump V Potion, Golden Apple / End Stone, Diamond Sword, Ender Pearl, Permanent Shears, Bow (Power I, Punch I), Invisibility Potion, Fireball. Sneak-click any item in a tab to put it into a Quick Buy slot ("Adding to Quick Buy..."), and sneak-click one in Quick Buy to take it out. `/minigames bedwars quickbuy` opens the same "Edit Quick Buy" editor outside a match: click a slot to choose its item from every shop item over two pages, in Hypixel's "Adding to Quick Buy..." order, or right-click it to empty it. Layouts are saved per player in the world.

**Rotating Items**, the last tab, sells two items a week beside "What are Rotating Items?", after Hypixel's weekly rotation ([announcement](https://hypixel.net/threads/bed-wars-update-item-rotation-and-bug-fixes.5180486/); items, prices and limits from the [community rotation log](https://hypixel.net/threads/bedwars-item-rotation-log.6013756/)). This mod rotates through the eight it has, two at a time in a fixed order, changing every Friday (UTC); a match keeps the items of the week it started in. In order:

| Item | Solo/Doubles | 3v3v3v3/4v4v4v4/4v4 | Notes |
| --- | --- | --- | --- |
| Lucky Chest | 5 gold | 5 gold | At most 10 a game; used, it bursts into 8 to 24 iron, 2 to 8 gold, 1 or 2 diamonds or an emerald (this mod's choice) |
| Sugar Cookie | 1 emerald | 1 emerald | Speed III and Jump Boost IV for 15 seconds |
| Cobweb ×4 | 3 gold | 3 gold | At most 4 buys a game |
| Mega TNT | 1 emerald | 2 emeralds | Goes off five seconds after it is placed, in a bigger blast than TNT's (this mod's choice of size) that breaks blast-proof glass too |
| Hay Bale ×5 | 4 gold | 4 gold | Landing on one takes no fall damage |
| Block Zapper | 5 gold | 5 gold | Right-click breaks one placed block (never a bed) |
| Bridge Zapper | 3 gold | 3 gold | Right-click on placed wool breaks it and up to 15 wool blocks joined to it |
| Throwable TNT | 6 gold | 10 gold | Right-click throws it about five blocks; it goes off three seconds later |

Rotating items are not in the Quick Buy editor's list, and count as Utility in the Hotbar Manager.

**Tracker**: everyone spawns with a compass in their Hotbar Manager's Compass slot (the last hotbar slot by default). Right-clicking it, or Quick Buy's **Tracker Shop** button, opens "Purchase Enemy Tracker" with a **Track Team** button for each enemy team still in the game. Once every enemy bed is gone, two emeralds buy tracking of one team until the buyer dies: the compass points at that team's nearest player and the action bar says how far away they are, as the wiki's shop menus have it.

**Hotbar Manager** (Quick Buy's bottom-right button, or `/minigames bedwars hotbar` outside a match, as in Hypixel's Bed Wars Settings): the categories Blocks, Melee, Tools, Ranged, Potions, Utility and Compass above the nine hotbar slots. Click a category to pick it up, then a hotbar slot to make that slot prefer it (menus here never move items, so Hypixel's drag is two clicks); click a filled slot to clear it, or **Reset to Default**. A bought item goes into a free slot its category prefers, or onto a stack of its own kind there, moving another category's item out of the way; at every spawn the sword, tools and compass go to their slots; with no Compass slot there is no compass, and Ultimate's ability item keeps the last slot. Preferences are saved per player in the world; the default is Hypixel's: the Compass in the last slot and nothing else.

### Upgrades & Traps

Right-click the **TEAM UPGRADES** villager. Upgrades are for the whole team, paid in diamonds; prices are the wiki's menus, Cushioned Boots' (a rotating upgrade that became permanent in January 2025) the rotation log's:

| Upgrade | Solo/Doubles | 3v3v3v3/4v4v4v4/4v4 | Castle |
| --- | --- | --- | --- |
| Sharpened Swords (Sharpness I on swords and axes) | 4 | 8 | 40 |
| Reinforced Armor I-IV (Protection on all armour) | 2, 4, 8, 16 | 5, 10, 20, 30 | 25, 50, 100, 150 |
| Maniac Miner I-II (Haste) | 2, 4 | 4, 6 | 20, 30 |
| Iron Forge (+50%), Golden Forge (+100%), Emerald Forge (emeralds), Molten Forge (+200%) | 2, 4, 6, 8 | 4, 8, 12, 16 | 20, 40, 60, 80 |
| Heal Pool (Regeneration in the base) | 1 | 3 | 15 |
| Dragon Buff (two dragons at Sudden Death) | 5 | 5 | 25 |
| Cushioned Boots I-II (Feather Falling on boots) | 1, 2 | 2, 4 | 10, 20 (this mod's choice) |
| Deadshot I-IV, Armed only (a quarter more gun damage a tier) | 2, 4, 8, 16 | 5, 10, 20, 30 | — |

Traps wait in a queue of three, costing 1, 2 then 4 diamonds (5 each in Castle), and only while the team's bed stands. The first enemy to walk into the base (its `base` region) without Magic Milk sets off the first trap, at most one every ten seconds (this mod's choice; no source gives Hypixel's): **It's a trap!** (Blindness and Slowness for 8 seconds), **Counter-Offensive Trap** (Speed II and Jump Boost II for 15 seconds to the team in its base), **Alarm Trap** (ends the intruder's invisibility and names them and their team) or **Miner Fatigue Trap** (Mining Fatigue for 10 seconds), as the traps' descriptions in the wiki's menus give them. The team sees "TRAP TRIGGERED!".

### Castle (`bedwars_castle`)

40v40 Castle follows Hypixel's Castle announcements ([original](https://hypixel.net/threads/bed-wars-castle-limited-time-40v40-mode.1676798/) and [v2](https://hypixel.net/threads/bed-wars-dream-rotation-castle-v2.1772914/)): two teams of up to 40 (`40v40`; `20v20` and `8v8` are offered too) on one map split by a wall with three tunnels under it. Each team has a castle and two towers, each holding one of its three beds (Keep, North Tower, South Tower, named in announcements and traps) under a beacon; when a bed breaks its beacon goes out and the team-coloured wool on its building turns grey. The team respawns while any of its beds stands. Launch pads beside the castle and behind the towers throw the team's own players between its buildings. Each of the team's four island generators (two in the castle, one in each tower) has an Item Shop, Team Upgrades, **Banker** and **Streak Powers** villager. Three diamond generators stand on each side and three emerald generators on islands off both ends of the wall and over its middle.

- **Banker**: deposit iron, gold, diamonds and emeralds with its buttons (4 to 64 at a time). Shops spend from the bank whatever a player is short of, and a full island generator's resources go to the bank.
- **Streak points** come from kills (2), final kills (5), breaking a bed (10), diamonds (1) and emeralds (2) picked up and deposits (a point per 32 iron, 8 gold, diamond or emerald). The announcement names these sources and says most "increase progressively" but gives no numbers, so the amounts are this mod's choice. Powers, with the announcements' tiers, costs and lengths: **Golden Knight** (30 points, 5 minutes: the Sword of Justice, which heals a heart on every hit and, right-clicked, calls a horse in golden armour), **Lone Wolf** (30, 5 minutes: three wolves that fight for you), **Hot Floor** (30, 3 minutes: a trail of flames that sets enemies alight), **Wither Rider** (60, 3 minutes: ride a wither of 10 health that flies where you look and spits fireballs that blow up placed blocks without hurting anyone) and **Block Wizard** (60, 6 minutes: a wand that throws a block for five hearts and a knockback); at most two of a team may have a Tier 2 power at once (this mod's choice). A power ends with its owner's death.
- Each team starts with three Alarm Traps; every trap lasts five triggers and names the building it guards. Nobody may place TNT within 8 blocks of their own standing bed. Upgrades use Castle's prices, items the 3v3v3v3 ones.

### Dream modes

Each Dream mode is Bed Wars with its rules on top, played on the core maps unless it says otherwise, in Doubles and 4v4v4v4 (Rush and One Block also Solo):

- **Rush** (`bedwars_rush`, Rush v2): every generator starts at Tier III and island generators run three times as fast; each bed starts covered in wood, then wool, then blast-proof glass; everyone has Speed I and Haste I ([Rush](https://hypixel.net/threads/bed-wars-v1-5-rush-dream-mode-new-cosmetic-and-more.1644376/) and [Rush v2](https://hypixel.net/threads/bed-wars-new-achievements-dream-rotation-rush-v2.1721113/) announcements); wool placed builds five more blocks in the direction the player faces, and a left-click with wool turns that on and off; potions cost double and ender pearls half (this mod's choice of how much cheaper; the wiki says only that pearls are cheaper); no obsidian; two dragons per team at Sudden Death.
- **Ultimate** (`bedwars_ultimate`, [Ultimate v2](https://hypixel.net/threads/bed-wars-ultimate-v2.1740675/)): every player plays the ultimate they picked with `/minigames bedwars ultimate [ultimate]` (no argument opens the picker; Kangaroo until they pick), used from the ability item in the last hotbar slot. **Kangaroo**: double-jump (a vanilla client's double tap of jump), keeps its resources on half its deaths, Magic Milk for every bed it breaks. **Swordsman**: right-click a sword to dash, hurting players in the way, and again within five seconds to return; a kill resets the cooldown. **Healer**: heals teammates within 6 blocks; right-click a sword to heal itself. **Frozo**: slows enemies within 6 blocks; a snowball for every kill, up to 16. **Builder**: a bridge of its wool ahead (a wall while sneaking), a wool every five seconds, and once a life a wool cover over its bed. **Demolition**: burns placed wool joined to the wool it aims at; leaves a lit TNT where it dies; a **Creeper Egg** for every bed it breaks, which, right-clicked at a block, hatches a creeper of its team that hunts the nearest enemy for 30 seconds and blows up like a smaller TNT, placed blocks only. **Gatherer**: one time in three gets a second diamond or emerald from a generator it stands on; a portable ender chest; its team gets the dearest upgrade it can still buy, free, when its bed falls. The announcements say what each ultimate does but give no numbers: ranges, strengths, chances and cooldowns are this mod's choice.
- **Armed** (`bedwars_armed`): guns instead of bows. Everyone spawns with a **Pistol**; the Ranged tab sells the **Magnum** (6 gold), **Rifle** (8 gold), **SMG** (50 iron), **Flamethrower** (12 gold) and **Shotgun** (1 emerald), and there is no obsidian. Right-click fires a shot that hits the first enemy in line within the gun's range (a line of particles shows it), left-click reloads, and an empty gun reloads by itself; a hit above the shoulders is a headshot for 1.5 times the damage. Damage, fire rate, clip, reload and range are a community guide's measurements of Hypixel's guns (Pistol 4 damage, 0.4 s, 12 rounds, 1.5 s reload, 30 blocks; Magnum 6, 0.6 s, 6, 3 s, 40; Rifle 4, 0.2 s, 25, 3 s, 40; SMG 2, 0.2 s, 45, 2 s, 30; Flamethrower 2, 0.1 s, 50, 3 s, 20, setting players alight and ending invisibility; Shotgun 2 per pellet, 1 s, 4, 4 s, 10, six pellets of this mod's choosing). Vanilla clients have no gun models: a gun is a hoe (the Flamethrower a flint and steel) whose durability bar shows the rounds left, with cooldown overlays for the fire rate and reloads. The team upgrades also sell **Deadshot** I to IV, each tier a quarter more damage from the team's guns; [players' guides](https://hypixel.net/threads/a-comprehensive-guide-to-armed-bedwars-as-written-by-a-solo-queuer.4816507/) mention tiers up to at least III, and the strength, tier count and prices are this mod's choice.
- **Lucky Blocks** (`bedwars_lucky`, [Lucky Blocks v2](https://hypixel.net/threads/bed-wars-lucky-blocks-v2.3096896/)): generators also drop lucky blocks, each a glazed terracotta: a **Lucky Block** (yellow) with one iron in 20, a **Promising** one (orange) with one gold in 6, a **Fortunate** (light blue) or **Offensive** (red) one with one diamond in 3, a **Miracle** one (lime) with one emerald in 3 (these odds are this mod's choice). Placed and broken, a lucky block opens one effect of its own table, named in chat. The tables are the effects the announcement's list gives each block, with its rarities, as far as this mod has them; how often each rarity comes up (Common 50, Rare 30, Epic 15, Legendary 5) and the numbers the list leaves open are this mod's choice:

  | Block | Common | Rare | Epic | Legendary |
  | --- | --- | --- | --- | --- |
  | Lucky Block | Bridge Eggs (2), Rainbow Wool (32 in eight random colours), Flint and Steel, Wither Skeleton (a hostile one), Trap > Slowness, Trap > damage | Instant Tool Upgrade (pickaxe and axe up a tier), Trap > Arrow Rain, Bed Compass (points at the nearest enemy bed) | Random Team Upgrade (a free tier) | |
  | Promising | Sugar Cookie Rotation Effect, Instant Barrier (a ring of team wool three high around you), Cute Pants (Protection II leggings) | Squid boots (Depth Strider III), Spiky Suit (Thorns II chestplate) | | |
  | Fortunate | Protective Bed Cover (team wool over your bed), heat-resistant boots (Fire Protection IV) | 4x Obsidian, Sharp Spoon (Sharpness III wooden shovel), Endstone Drop (32), Trap > freeze | | |
  | Offensive | 5x Cobweb, Rush Pearl (an ender pearl), Trap > Poison, Transform block > Fire | Battle Axe (Sharpness II iron axe), Instant Trap Queue (fills the trap queue), Instant Weapon Upgrade (your best sword up a tier), Sword Of Justice (heals a heart on every hit) | Baby Zombie Helper (fights for your team for a minute), Knockback Slimeball (Knockback II) | |
  | Miracle | Jerry, Spicy Sword (Fire Aspect II stone sword), OP chainmail helmet (Protection IV) | OP iron helmet (Protection IV) | OP diamond helmet (Protection IV), Placeable Wither, Obsidian Drop (8) | Super Star (Resistance IV, Speed II and Regeneration II for 10 seconds) |

  A **lucky trap** (Slowness III for 5 seconds, 3 hearts of damage, a rain of eight arrows, freezing for 3 seconds, or Poison II for 5 seconds) lies as a carpet of its opener's team colour and goes off on the first enemy to step on it; an arrow that hits it clears it. **Jerry**, a villager, stays two minutes and trades a Miracle Lucky Block for 3 emeralds. The **Placeable Wither**, right-clicked at a block, brings an old wither of its team (10 hearts) that floats after its owner for a minute and every 1.5 seconds strikes the nearest enemy within 12 blocks for two hearts and a moment of Wither. Not yet in the tables: the list's Blitz kits (Vampire, Gremlin, Invoker, Imprison, Roulette, Shotgun, Jedi Force, Wither Warrior, Apocalypse, Vault Hunter, Nuke), Explosive Chicken, Hot Head, Ice Bridge Rotation Effect, Splitter Slime, Water Balloon, Boombox, Lava Rune, Sleepinator, Fling Bow, Resource Trader, Fist Upgrade, Slime Boots, the Gold, Emerald, Lava and Bedrock transforms, Chicken Hat, Hot Potato, Chicken Bomb, Disco Armor, Ghast, Blaze Rider, Exodus, Scythe, Time Warp Pearl, James Bond Armor, Companion Picker, Frog Helmet, Mystery Meat, Devil Chicken Bow, Gravity Gun, Little Big Problem, Beam me up scotty!, Grappling hook, Wildcard, Snowman Rotation Effect, Telebow, Dreadlord Skull, Axe Of Perun, Golden Knight (from castle), Magic Toy Stick, Diamond Sheep, Voidmaker and Placeable bed.
- **Voidless** (`bedwars_voidless`): its own maps, the core layouts raised over solid ground instead of the void; every bed starts covered in wood, wool and stained glass.
- **Swappage** (`bedwars_swappage`): every `swap_min_seconds` to `swap_max_seconds` (60 to 120; Hypixel's intervals are random) teams swap places in pairs, player for player; teams that still have a bed swap only with each other, the others among themselves.
- **One Block** (`bedwars_one_block`): its own map, eight tiny islands of two wool blocks and a bed in a ring, with no shops or generators; every player still in the game gets a random item or block every three seconds, every four after ten minutes.

### Bed Wars maps

| Game | Map | Teams | Description |
| --- | --- | --- | --- |
| Bed Wars | `lighthouse` | 8 | Eight grassy base islands, two on each side of a round middle island with a red-and-white striped tower and four emerald generators; a stone diamond island off each corner. |
| Bed Wars | `quarry` | 4 | Four coarse-dirt bases, one on each side, each with its own cobblestone diamond island to its left; two emerald generators on a gravel middle island around a stone-brick tower. |
| Bed Wars | `outpost` | 2 | Two podzol bases facing each other across a mossy middle island with a dark watchtower and two emerald generators; a diamond island off each base. |
| Bed Wars Voidless | `lighthouse`, `quarry` | 8, 4 | The core layouts on a solid ground fourteen blocks below the islands. |
| Bed Wars One Block | `drift` | 8 | Eight two-block islands of team wool with a bed, in a ring. |
| Bed Wars Castle | `castle` | 2 | Two halves of one meadow split by a deepslate wall with three tunnels under it; each team's stone-brick keep and two cobblestone towers with beds on top, beacons and launch pads; three diamond generators a side and three emerald islands. |

The maps are original layouts written by `tools/maps/bedwars.py`, following the island counts the Hypixel wiki gives for each mode. They mark `spawn <team> <yaw>`, `point bed_<team>[_<name>] <yaw>` (a bed's foot; its head lies the way the yaw faces; Castle names each team's three), `point shop_<team>[_<n>] <yaw>` and `point upgrades_<team>[_<n>] <yaw>` (the shopkeepers), `point forge_<team>[_<n>]` (island generators), `point diamond_<n>` and `point emerald_<n>`, `region base_<team>[_<name>]` (the base, where traps and the Heal Pool work), `region build`, `void` and `lobby`; Castle also marks `point banker_<team>_<n>`, `point streak_<team>_<n>`, `point pad_<team>_<n> <yaw>` (launch pads, thrown the way the yaw faces) and `point beacon_<team>_<bed>`. A map is refused when a team lacks a bed, a base or (but on One Block maps) its shopkeepers.

Bed Wars and its modes are listed under Bed Wars in the menus, play with `/duel` and bot fill like the other team games, and bots from a [bot provider](#lobbies-votes-and-bots) shop and find the beds through the integration contract below.

## Quake

Quake follows Hypixel's Quakecraft. Everyone plays in adventure mode with the `brainage_minigames:kits/quake` kit: a Railgun (a wooden hoe; any hoe works) and a Dash feather, and has Speed II (`speed_level`) and full hunger. Using the railgun fires an instant beam up to 100 blocks that stops at the first solid block but passes through players, so one shot can kill several (announced as a DOUBLE or TRIPLE KILL); every opponent it touches dies at once and the shooter's team scores a kill. The railgun then reloads for `reload_ticks` (default 24, 1.2 seconds), shown as the item cooldown. Using the feather dashes you forward and slightly up, recharging after `dash_cooldown_ticks` (default 40; Hypixel dashes with a left click on the railgun, which the server cannot see, so it has its own item here; `dash` 0 turns it off). Nothing else hurts: no melee, no fall damage. Killed players respawn at once at a random `respawn_<n>` point from the half of the map's points farthest from their opponents. Kill streaks of 5, 10, 15, … and shut-downs are announced as on Hypixel. A player (or team) wins on reaching `kills_to_win` kills (default 25, Hypixel Solo) when every team is one player, or `team_kills_to_win` (default 100, Hypixel Teams) otherwise; at the time limit (10 minutes) the most kills wins. Free-for-all and every team layout up to the map's spawns work; teammates' beams pass through each other.

The sidebar shows each team's score against the target.

| Game | Map | Players | Notes |
| --- | --- | --- | --- |
| Quake | `foundry` | 12 | A 65-block walled hall: a two-storey brick tower with a drop hole and a ladder to a lookout, bridges east and west to high balconies, copper corner balconies up wall stairs, and pillars, low walls and crates on the ground; 32 respawn points on four levels. |
| Quake | `cloister` | 8 | A 37-block courtyard for duels: a garden with a fountain and hedges inside a pillared cloister whose roof is a walkway reached by two stairways; 24 respawn points. |

Quake maps mark `spawn <n>` (one per player of a free-for-all; teams start at the first ones) and `point respawn_<n>`. They are generated by `python3 tools/maps/quake.py` into `data/brainage_minigames/structure/maps/quake/`; the maps are larger than the 48-block limit of an in-game structure block, so edit them through the script (or in parts).

## Pearl Fight

Pearl Fight follows Minemen Club's Pearl Fight. Minemen publishes no rules for it, so they were reconstructed from its match pages, which show both final inventories: a stick, ender pearls (8 was the most left over), 16 wool and shears in every one. The `brainage_minigames:kits/pearl_fight` kit is a Knockback I stick, 8 ender pearls, 16 wool (turned into the team's colour) and shears; players are in survival over floating platforms. Hits only knock back (Resistance V; pearls and falls do no damage either), wool may be placed only inside the map's `build` region and only placed blocks can be broken. Falling below the map's void scores a point for whoever last hit you within 10 seconds, or for the other team when nobody did. In a two-team match each point starts a new round: the map is rebuilt, everyone returns to their spawn with a fresh kit and is frozen for `round_freeze_ticks` (default 60). With more teams only the fallen player respawns, and a fall nobody caused scores nothing. The first team to `points_to_win` (default 3) wins; the round-based format, the target and the Knockback level are assumptions, as the match pages show none of them.

The sidebar shows each team's score against the target. Free-for-all and any team layout up to the map's spawns work.

| Game | Map | Players | Notes |
| --- | --- | --- | --- |
| Pearl Fight | `skyreach` | 4 | Four quartz shrine terraces 17 blocks around a mossy island with a stepped amethyst spire and broken walls; on each diagonal a stepping rock, alternately low and high, and amethyst perches between each home and the middle. |
| Pearl Fight | `twin_peaks` | 2 | Two home terraces 30 blocks apart, each under its own snow-capped peak with steps to a lookout. A stepping stone leads to a low middle rock under a broken arch, and each flank has a high crag and a low ledge; the map is point-symmetric. |

Pearl Fight maps mark `spawn <team>` and `region build`. They are generated by `python3 tools/maps/pearl_fight.py` into `data/brainage_minigames/structure/maps/pearl_fight/`.

## Sumo

Sumo follows Hypixel's Sumo Duels and Minemen Club's Sumo: everyone starts empty-handed (`brainage_minigames:empty`) in adventure mode on a small raised platform. Hits only knock back (Resistance V), nobody gets hungry, and nothing can be built, broken or used. A player whose feet drop a whole block below the platform's surface (into the water or void under it, or onto the ground around it) is out: the chat says who knocked them off (whoever last hit them within 10 seconds) or that they fell, and they watch the rest of the round in spectator mode from the map's gallery, free to fly anywhere. When only one team has a player left on the platform it wins the round; if the last players of every team fall together, the round is a draw and nobody scores. Every round after the first puts everyone back on their spawn, frozen through `round_countdown_seconds` (default 3) with a countdown on the action bar, while the first round starts after the usual match countdown.

The first team to `rounds_to_win` rounds wins (default 3, so a match is best of five). Hypixel's casual Sumo is a single round; set `rounds_to_win` to 1 to play it that way. When the 5-minute time limit runs out the team with the most rounds wins, or the leaders draw. The sidebar shows the rounds needed, the map and the round, and each team's rounds as dots out of the target, followed in team layouts by how many of the team are still on the platform. Any team layout up to the map's spawns works, including free-for-all; teammates start together on their team's spawn, facing the middle. Bots from a [bot provider](#lobbies-votes-and-bots) play Sumo like players.

Nothing on a Sumo map can change during a match, so rounds never rebuild it and a new round costs only putting the players back.

| Game | Map | Teams | Notes |
| --- | --- | --- | --- |
| Sumo | `dohyo` | 2 | A round clay ring 15 blocks across, edged with flush straw bales, raised two blocks on a square earthen mound in a sandstone hall: stepping off onto the mound is out. A shrine roof with a coloured tassel at each corner hangs high above, and benches on two sides look on. |
| Sumo | `lotus` | 4 | An octagonal cherry-wood deck 17 blocks across on stilts over a koi pond, with a lotus inlaid in the middle; falls end in the water. Cherry trees grow on islets in the pond's corners, and a pavilion on the north bank is the gallery. |
| Sumo | `crucible` | 4 | A deepslate foundry floor 19 blocks across with cut corners, hanging over the void, its edge striped yellow and black and copper rails crossing to a copper hearth. Four soul-lit pylons stand off its corners and a copper catwalk on the north side is the gallery. |

Sumo maps mark `spawn <team>` on the platform, `void` at the height of the platform's top layer, so a player is out once their feet are a block below where they stand, and `lobby` on the gallery, where players wait before the match and knocked-off players are sent. Nothing outside the platform from a block below its top layer to three blocks above it may be within 6 blocks of the platform, so that nobody knocked off can land somewhere and stay in; the GameTest `sumo_every_map_keeps_its_platform_clear` checks every Sumo map for this, and `python3 tools/maps/sumo.py`, which generates them into `data/brainage_minigames/structure/maps/sumo/`, refuses a map that breaks it.


## Parkour

Parkour is a race on a map: everyone starts together, must pass the map's `region checkpoint_1`, `checkpoint_2`, … in that order and then its `region finish`; a gate passed out of order counts for nothing, so skipping part of the course never helps. The first player to finish wins for their team. Nobody is eliminated and nobody takes damage (no PvP, no fall damage), and racers pass through each other (the match teams' collision rule is `never`). A racer who falls five blocks below their last checkpoint, touches a `region fail` or drops into the void is put back at their last checkpoint, or at the start before the first one. The action bar shows each racer's checkpoint and time; the sidebar shows the checkpoint count and each team's furthest racer. When `time_limit_minutes` (default 10) runs out, the team that got furthest wins, and teams level on gates draw. Free-for-all and every team layout work up to the map's eight start slots.

Parkour follows Hypixel's Parkour Duels (an eight-player race through checkpointed sections, with a boost feather). Runners play in adventure mode with the `brainage_minigames:kits/parkour` kit: a Boost feather, which throws them forwards and upwards and then waits `boost_cooldown_seconds` (default 15; the cooldown shows on the item), and a Back to checkpoint pressure plate that returns them at once. Parkour has one lap.

| Game | Map | Notes |
| --- | --- | --- |
| Parkour | `canopy` | Six sections through floating jungle trees, climbing 27 blocks over about 160: branch hops and a log beam, temple ruins with a trunk ladder and a hanging ladder, fence posts around a slab, zig-zag bamboo steps, leaf leaps ending in a three-block gap one block up, and wall posts and a tall ladder to a gold finish; 5 checkpoints, turning at four of them. |
| Parkour | `spire` | A square spiral climbing one and a half times around a quartz tower: quartz, fence, top-slab and purpur jumps with three ladders, a checkpoint at every corner and the finish above the start; 6 checkpoints. |

Checkpoint gates may carry a `point` of the same name (`point checkpoint_<n> <yaw>`, `point finish <yaw>`) setting where and facing which way racers are put back; without one they stand at the bottom centre of the gate facing the next gate. Parkour courses never descend more than a block below the previous checkpoint, since a five-block drop counts as a fall, and no block before a checkpoint is within a jump of one after it, so no gate can be bypassed by cutting a corner. Maps are generated by `python3 tools/maps/parkour.py` into `data/brainage_minigames/structure/maps/parkour/`; the script refuses a course that breaks either rule or needs more than a sprint jump (a four-block gap on the level, three blocks one block up, nothing higher than 1.25 blocks), and keeps scenery out of the air runners jump through and off anything they could land on. The GameTest `race_every_parkour_course_runs_in_order_with_legal_jumps` checks every bundled course the same way after pasting it: each start slot and checkpoint reaches the next gate with those jumps and ladders, without counting as a fall, and never reaches the gate after it without passing it. The maps are larger than the 48-block limit of an in-game structure block, so edit them through the script (or in parts).

## Ice Boat Racing

Ice Boat Racing uses [Parkour's ordered checkpoints, finish gate, no-damage rules, team scoring and race displays](#parkour). It also accepts free-for-all and any team layout up to the map's eight start slots. A `region fail` or the void returns a racer to their last checkpoint; simply driving downhill does not.

Ice Boat Racing puts every racer in an oak boat on the map's `point boat_<n>` grid slot (in team order) when the race starts. Boats cannot be broken; a racer who is out of their boat for any reason gets a new one at their last checkpoint (or the finish line, after a lap), and the old one is removed. `laps` sets the laps (default 3). Boats are removed when their racer leaves and when the race ends, and racers are taken out of them before being restored.

| Game | Map | Notes |
| --- | --- | --- |
| Ice Boat Racing | `frostbite_oval` | A 266-block oval, 9 wide: two 70-block straights (blue ice down the back one) joined by half circles of radius 20; 3 checkpoints. |
| Ice Boat Racing | `glacier_circuit` | A technical 305-block circuit, 7 wide: a blue-ice main straight, a fast right-hand sweeper, a hairpin, a kink, four S bends and a long left-hander home; 5 checkpoints. |

Checkpoint gates use the [same optional checkpoint/finish points as Parkour](#parkour). Tracks are packed ice with blue ice only on long straights, kerbs a block high under a glass rail (a boat cannot climb them and drivers can see over them) and gates spanning the whole track four blocks high; the eight-slot grid of `point boat_<n>` (with a `spawn <n>` under each) sits behind the finish line. Maps are generated by `python3 tools/maps/ice_boat_racing.py` into `data/brainage_minigames/structure/maps/ice_boat_racing/`; they are larger than the 48-block limit of an in-game structure block, so edit them through the script (or in parts).

## Grinch Simulator

Grinch Simulator follows Hypixel's seasonal Arcade game as it has played since its 2020 revision ([launch](https://hypixel.net/threads/huge-holiday-update-new-games-boxes-lobby-more.927157/), [revision](https://hypixel.net/threads/happy-holidays-from-hypixel.3588435/), [tournament rules](https://hypixel.net/threads/hypixel-tournaments-grinch-simulator-2nd-iteration.5553435/)). Everyone is a Grinch, alone (a free-for-all), let loose in a snowy village with presents hidden in, on and around its houses. Right-clicking a present steals it: it disappears for everyone and counts one point at once; there is nothing to carry back. When `time_limit_minutes` (default **4**) runs out the player with the most presents wins and leaders draw, and the match ends early once every present is stolen.

- **Presents** are player heads with present skins. Each match puts `presents` (default 150) of them at random among the map's present spots, so no route can be learnt by heart; presents lie on floors and shelves, round the small Christmas trees inside the houses, outside the doors, on the roofs and round the plaza tree. Nothing else in the village can be used.
- **The map:** everyone gets a **Village Map** in the first hotbar slot: a filled map of the whole village that marks every present still there and every player, and drops each present's mark as soon as it is stolen. A map thrown away comes back within a second.
- **Houses:** open cottages have a doorway; locked houses have an iron door that never opens, so you get in through the open window upstairs at the back, up the leaf steps along the back wall, or down the chimney from the roof. Climbing trees (green terracotta steps inside a Christmas tree) lead onto the roofs.
- **Nobody can hinder anyone:** no damage of any kind, no pushing (players pass through each other), no hunger, and nothing can be built or broken. The original game's snowballs and punches were taken out in the 2020 revision and are not played.
- Players play in adventure mode with an otherwise empty inventory, start round the plaza tree, and the sidebar lists everyone's presents and how many are left; the action bar shows your own.

Up to six players (each map has six spawns, so bot fill fills to six), as Hypixel plays it; team layouts also work, and a team scores its members' presents together. The present count, the six-player cap and the ending once every present is stolen are this mod's choices where Hypixel publishes no number. Grinch Simulator is listed under Arcade in the menus, and bots from a [bot provider](#lobbies-votes-and-bots) are told where the presents are through the integration contract below.

| Game | Map | Players | Notes |
| --- | --- | --- | --- |
| Grinch Simulator | `hollyvale` | 6 | A 104-block snowy village in a fenced square: cobbled roads split it into nine plots, the plaza in the middle with a decorated Christmas tree, four locked two-storey houses in the corners and two cottages on each side plot, facing the roads; pines and snowmen in the gardens and lamps at the crossings. 278 present spots. |
| Grinch Simulator | `frostmere` | 6 | The same village plan turned round: the four locked houses face the plaza from the side plots and the cottages pair up in the corners, round a frozen pond under the plaza tree. 278 present spots. |

Maps mark `spawn <n>` (one per player), `lobby` and `point present` at every spot a present may be put (an empty block with a floor under it); a map without present spots is refused. They are generated by `python3 tools/maps/grinch_simulator.py` into `data/brainage_minigames/structure/maps/grinch_simulator/`, which builds each house from a seeded template and refuses a map where a present spot cannot be reached on foot from the spawns and left again (walking, jumping a block up and dropping at most three, with every other spot taken); the maps are larger than the 48-block limit of an in-game structure block, so edit them through the script (or in parts).


## Maps

Games other than UHC, Meetup, FinalUHC and the kit duels play on maps: vanilla structure templates under `data/brainage_minigames/structure/maps/<game>/<map>.nbt` (in the mod's resources, a datapack, or saved in the world by a structure block). A match picks one at random among those that have room for its teams, and `/minigames status` and the sidebar name it. Each open map is pasted into its own slot of the `brainage_minigames:minigames` void dimension with its minimum corner at Y 64, with its chunks kept loaded while the match runs. Every map is read from its file when the server starts. A match pastes its map two chunks per tick once they have loaded, so opening one never holds up the server: players who join meanwhile see "Preparing the arena..." and are moved onto it once it is pasted, and the match cannot start before then (usually a fraction of a second, a few seconds for the largest maps). Games that rebuild the map between rounds paste it again, which removes placed blocks and every non-player entity. When the match ends, the map's entities, chests and other block entities go at once and its blocks are cleared two chunks per tick; its slot is reused once it is clear.

Markers are structure blocks in DATA mode whose metadata (the "Custom Data Tag Name" field) the mod reads when it pastes the map; they are then replaced by air. Words are separated by spaces and lowercase:

| Marker | Meaning |
| --- | --- |
| `lobby [yaw]` | Where players wait before the match; without one, the first spawn of team 1. |
| `spawn <team> [yaw]` | A spawn of team `<team>`, from 1; a team may have several, used in turn when its players start or respawn. Every team from 1 to the highest must have one, and the highest is how many teams the map holds. |
| `point <name> [yaw]` | A named position for the game, such as `point chest_mid` or `point boat_1 -90`. |
| `region <name> <dx> <dy> <dz>` | The whole blocks from the marker to the marker plus (`dx`, `dy`, `dz`), which may be negative. `build` limits block placing to the build regions when a map has any (otherwise anywhere inside the map); games also use `goal_<team>`, `checkpoint_<n>`, `finish`, `floor_<n>` and names of their own. |
| `void` | Players below this marker's Y die at once; without one, 10 blocks below the map. |

Yaws are in degrees: 0 faces south (+Z), 90 west, 180 north and -90 east; a marker without one faces the middle of the map. A map with an unknown or malformed marker, no spawns, or a gap in its team numbers is refused with a message naming the marker and its position in the template. Chests can carry a loot table (`LootTable` in their block-entity data), filled when a player first opens them.

The bundled maps are written by deterministic Python scripts, one per game: `python3 tools/maps/<game>.py`, run from the repository root, rewrites that game's `.nbt` files, using the `Structure` helper in `tools/maps/structure.py` (`set`, `fill`, `marker`, `save`). `tools/maps/test_map.py` builds the maps the GameTests use. Every block of a map must be able to stay where it is pasted: sand needs a block under it, and plants, snow layers, hanging vines and lanterns need their support, or they fall or break (while the map is pasted, or at the next update) and leave items or falling blocks in the map. The GameTest `map_every_bundled_map_block_survives_where_it_is_pasted` pastes every bundled map and checks both that each block of the template is in place and that it can stay there. Clearing or rebuilding an arena never drops items either: blocks are removed without updating their neighbours, so nothing loses its support and breaks, and containers are emptied without spilling or rolling their loot tables; `map_clearing_an_arena_drops_nothing` checks this for every bundled map and for a box arena with a filled chest.

To edit a map in game, load it with a structure block in LOAD mode (structure name `brainage_minigames:maps/<game>/<map>`), change it, and save it under the same name with a SAVE-mode structure block; the saved copy in the world's `generated` folder then takes the place of the bundled one on that server. Place markers as DATA-mode structure blocks and include them in the saved area. An in-game structure block saves at most 48 × 48 × 48 blocks, so larger maps are edited in parts or through their script.

To look at maps without joining a server, render previews with the Fabric client GameTest; it does nothing unless the `brainage_minigames.map_previews` system property lists maps (`<game>/<map>` separated by commas, or `all` for every bundled map):

```shell
JAVA_TOOL_OPTIONS=-Dbrainage_minigames.map_previews=parkour/canopy,bridge/grove ./gradlew :fabric:runProductionClientGameTest
```

Each map is pasted into the minigames dimension and photographed from two raised corners, from straight above, from its first spawn and, for races, from every checkpoint and the finish. The screenshots are written to `fabric/build/run/clientGameTest/screenshots/`, named `<index>_<game>-<map>-<view>.png`.

## Integration contract

Server-side mods such as SparringBots use Brainage Minigames without a compile dependency: they call public static methods by reflection, read gamerules by id, parse chat and sidebar text, and recognise dimensions and entity tags. Everything below is that contract; its names, signatures and wording are kept stable. Classes are in `io.github.brainage04.brainage_minigames`.

- **Combat loggers** — `game.UhcCombatLogger`:
  - `public static boolean canAttack(ServerPlayer attacker, Entity logger)`: whether `attacker` may hit this offline participant's zombie under match, team, PvP, grace, deathmatch-freeze and anti-janitor rules. It changes no state and records no attack.
  - `public static @Nullable UUID participant(Entity)`, `public static @Nullable Match match(Entity)` and `public static @Nullable Zombie zombie(UUID participant)`.
  - Logger zombies carry the entity tags `brainage_minigames:combat_logger` and `brainage_minigames:participant=<uuid>`.
- **Container ownership** — `game.ContainerProtection`:
  - `public static @Nullable UUID owner(Level level, BlockPos pos)`: the placer for this exact dimension, position and current block entity, or `null` for unowned, replaced or cleared containers.
  - `public static @Nullable ContainerProtection.Access lastAccess(ServerPlayer owner)`: the most recent successful foreign opening or break while protection was off. The record `Access` exposes `actor(): UUID`, `dimension(): ResourceKey<Level>`, `position(): BlockPos`, `action(): String` (`"open"` or `"break"`) and `tick(): int` (the server's tick counter). It remains after a break removes ownership and is cleared at match end or reset. Compare the tick with the current server tick and remember the last handled record; a rejected action or merely looking at a container produces no record.
- **Death loot** — `game.DeathLoot`, the anti-janitor and Safeloot claims on death chests and dropped items:
  - `public static boolean canOpen(Level level, BlockPos pos, Player player)` and `public static boolean protectedChest(Level level, BlockPos pos)`: whether the player may open a death chest half, and whether it is claimed (and so cannot be broken).
  - `public static boolean canPickUp(ItemEntity item, Player player)` and `public static boolean claimed(ItemEntity item)`: whether the player may pick up a dropped item, and whether it is claimed.
- **UHC scenarios** — `game.uhc.UhcScenarios`:
  - `public static Map<BlockPos, Integer> timeBombs(ServerLevel level)`: armed Time Bomb chests in that level, by the chest's first (west) half, with the ticks until each explodes with `public static final float TIME_BOMB_POWER` (TNT's 4). Their countdown text displays carry the entity tag `brainage_minigames:time_bomb`.
  - `public static boolean noCleanProtected(ServerPlayer player)`: whether players cannot hurt this player now because of No Clean.
- **UHC recipes** — `game.uhc.UhcCrafting`:
  - `public static List<UhcCrafting.Recipe> recipes()` and `public static ItemStack preview(ServerPlayer player, UhcCrafting.Recipe recipe)` (empty unless the player may craft it now, by unlocks and remaining uses).
  - `Recipe` exposes `id(): String`, `output(): Item`, `grid(): Item[]` and `matches(CraftingInput): boolean` (whether a crafting grid fits this recipe, ownership aside). A grid cell holding `IRON_ORE` or `GOLD_ORE` also accepts the deepslate ore and `RAW_IRON` or `RAW_GOLD` respectively.
  - `public static String kind(ItemInstance stack)` returns a crafted UHC item's recipe id (for example `forge`), stored in the item's custom data under `brainage_uhc_item`, or an empty string.
- **UHC kits** — run as the player before a match: `/minigames uhc kit <id>` or `/minigames uhc kit default` (Stone Gear), and `/minigames uhc prestige_bonus <kit> <bonus>` while `brainage_minigames:uhc_choose_prestige_bonus` is on.
- **SkyWars kits and perks** — run as the player: `/minigames skywars kit <kit>` and `/minigames skywars perk <perk> <true|false>` (Insane, also played by `skywars_lucky`), `/minigames skywars mini|mega kit <kit>` and `/minigames skywars mini|mega perk <slot> <perk|clear>`. Kit items with an ability carry their id (for example `time_warp_pearl`, `ice_bridge_egg`, `thundermeister_axe`) in their custom data under `brainage_minigames:skywars_item`; `game.skywars.SkyWarsItems.ability(ItemStack)` returns it or an empty string. `game.skywars.SkyWarsKits.botKits(SkyWarsMode)` lists the kits bots pick from in a mode; a match's game is a `game.skywars.SkyWarsGame` whose `mode()` names it. In a lucky block game `SkyWarsGame.luckyBlocks(Arena)` lists where the unbroken lucky blocks stand; breaking one through the normal block-breaking path rolls its outcome.
- **Match borders** — `game.uhc.UhcArena`:
  - `public WorldBorder border()`: the active match border, never a dimension-global one.
  - `public @Nullable WorldBorder border(ServerLevel current)`: this match's surface, Nether or deathmatch border, or `null` outside its current levels.
  - `game.uhc.NaturalArena`: `public WorldBorder border()` is Meetup's and FinalUHC's match border.
  - Use these instead of `level().getWorldBorder()` in every phase. `UhcArena.level()` changes to `brainage_minigames:minigames` at deathmatch, where the survival surface and Nether borders stop applying.
- **Legacy combat** — `game.CombatRules`, while `brainage_minigames:combat_1_8` is on:
  - `public static boolean legacyBalance(ServerLevel)` reads the rule; `public static boolean classic(@Nullable Entity)` also requires an active, non-spectating participant.
  - `public static double weaponDamage(ServerLevel, ItemStack weapon, double vanillaDamage)` adds only the 1.8 weapon offset to a total that includes the bare-hand base and kit modifiers but not Strength or Weakness.
  - `public static float armorReduction(float armor)` (fraction 0–0.8), `public static int protectionPoints(int level, double modifier)` (per-piece EPF before aggregation), `public static double strengthMultiplier(int level)`, `public static int instantHealing(int level)`, `public static int instantHarming(int level)` and `public static int regenerationInterval(int amplifier)` (ticks).
  - `LivingEntity.getAttributeValue(ATTACK_DAMAGE)` already includes the legacy weapon, Strength and Weakness values for `classic` entities. Sword blocking is main-hand sword use (`isUsingItem()` with a sword in the main hand).
- **Gamerules**, read by id: `brainage_minigames:pre_pvp_following`, `brainage_minigames:container_protection`, `brainage_minigames:uhc_no_duplicate_crafts`, `brainage_minigames:combat_1_8`, `brainage_minigames:uhc_max_all_perks`, `brainage_minigames:uhc_max_all_kits`, `brainage_minigames:skywars_max_all_kits` and `brainage_minigames:skywars_max_all_perks` (all four default `true`), `brainage_minigames:uhc_choose_prestige_bonus`, and the [UHC scenarios](#uhc-scenarios) `uhc_time_bomb_seconds`, `uhc_no_clean_seconds`, `uhc_safeloot_seconds`, `uhc_team_backpack`, `uhc_second_chance` and `meetup_adaptive_border`.
- **Resource scenario gamerules**, read by id, all default `false`: `brainage_minigames:uhc_cutclean`, `uhc_timber`, `uhc_vein_miner`, `uhc_hastey_boys`, `uhc_blood_diamonds`, `uhc_diamondless` and `uhc_goldless` (see [UHC scenarios](#uhc-scenarios)).
- **Chat lines** to match participants (`N minutes` is `1 minute` for one):
  - `PvP is enabled in N minutes.` at the start of a UHC with a grace period, and `PvP is now enabled!` when PvP starts (also at the start without one).
  - `The border starts shrinking in N minutes; it reaches W blocks across at MM:00.` (Hypixel-style border) or `The border shrinks instantly to W blocks across at MM:00.` per shrink (Badlion-style).
  - `The nether closes in N minutes.`, or `The nether is disabled in this match.`; one minute before closing `The nether closes in 1 minute. Anyone still in it will be moved to the surface.`, then `The nether has closed; everyone still in it was moved to the surface.`
  - `Your opponent's loot chest is at X, Y, Z.` to every member of the team that holds a death chest's claim (an anti-janitor fight or Safeloot).
  - `<name>'s loot is a Time Bomb at X, Y, Z: it explodes in N seconds.` when a Time Bomb is armed, and `<name>'s Time Bomb exploded.` when it goes off.
  - Duel invitations contain a click event running `/duel accept <challenger>` whose hover text is `Players: ` followed by the invited players' names, comma-separated.
- **Sidebar lines**: `Shrink in: M:SS` counts down to the next border shrink.
- **Bot providers** — `api.MatchBots`, for mods that spawn player bots into match slots:
  - `public static void register(String providerId, Function<Map<String, Object>, List<ServerPlayer>> spawner, Consumer<ServerPlayer> remover)`, called at server start; registering an id again replaces it, and the most recently registered provider spawns every bot. `public static boolean available()` tells whether one is registered; bot commands and options exist only then.
  - The spawner gets a map with every key present: `"server"` (`MinecraftServer`), `"game"` (the game id, e.g. `uhc`, `meetup`, `final_uhc`, `bridge`, `classic`), `"match"` (the match number as a `String`), `"count"` (`Integer`), `"names"` (`List<String>`; empty, so the provider chooses names, never an online player's) and `"difficulty"` (`easy`, `normal`, `hard` or `mixed`; `mixed` unless the lobby chose another). It returns the spawned, connected players, possibly fewer than asked; extra ones are handed straight back. One request is made per start.
  - The match moves the bots to its lobby, saves their state and puts them on their teams with the kit exactly like players, and treats them as players for the rest of the match. It calls the remover once it no longer needs a bot: the tick after the bot is eliminated, and when the match ends or is stopped, always after the bot has left the match and its state was restored. A bot the provider removes itself is eliminated like a player who disconnects, without a combat logger, and the remover is still called for it. Bots are requested for reserved slots, and for every empty slot of a public lobby of any game that starts early while `brainage_minigames:fill_bots_on_early_start` is on. Parkour and Ice Boat Racing never request bots.
- **Dimension ids**: `brainage_minigames:uhc` and `uhc_nether` (UHC), `meetup` and `meetup_nether` (Meetup), `final_uhc` and `final_uhc_nether` (FinalUHC); `brainage_minigames:minigames` holds duel arenas, maps and every UHC deathmatch arena.
- **Capture the Wool** — `game.ctw.CaptureTheWoolGame`: `public static @Nullable Map<String, Object> botView(ServerPlayer player)` describes the active Capture the Wool match the player plays in, or returns `null`. Keys: `"team"` (`Integer`), `"slot"` (`Integer`, the player's place in its team from 0), `"teamSize"` (`Integer`), `"guard"` (`Vec3`, where the team's defenders stand), `"woolRooms"` (`List<AABB>`, the team's own wool rooms, which it may not enter) and `"wools"`, a `List` of maps with `"colour"` (`String`, a dye name), `"owner"` and `"capturer"` (`Integer` team numbers: the team keeping it and the team placing it), `"source"` and `"slot"` (`BlockPos`: its pedestal top and its monument slot), `"state"` (`home`, `carried`, `dropped` or `placed`), plus `"carrier"` (`UUID`) while carried and `"dropped"` (`Vec3`) while on the ground. A wool is taken by breaking it at `source` and placed by using it on the top face of the block under `slot`.
- **Bed Wars** — `game.bedwars.BedWarsGame` (every Bed Wars mode's game is one; `mode()` names the mode):
  - `public static @Nullable Map<String, Object> botView(ServerPlayer player)` describes the active Bed Wars match the player plays in, or returns `null`. Keys: `"team"`, `"slot"` (the player's place in its team from 0) and `"teamSize"` (`Integer`), `"spawn"` (`Vec3`), `"beds"` (a `List` of maps with `"team"` (`Integer`), `"foot"` and `"head"` (`BlockPos`) and `"standing"` (`Boolean`)), `"shop"` and `"upgrades"` (`Vec3`, the team's nearest Item Shop and Team Upgrades villagers; missing on One Block maps), `"forge"` (`Vec3`, the team's island generator), `"diamonds"` and `"emeralds"` (`List<Vec3>`), `"base"` (`AABB`), `"respawning"` (`Boolean`) and `"resources"` (a map from `iron`, `gold`, `diamond` and `emerald` to the `Integer` the player carries).
  - `public static boolean botBuy(ServerPlayer player, String item)` buys a shop item (ids: `wool`, `hardened_clay`, `blast_proof_glass`, `end_stone`, `ladder`, `wood`, `obsidian`, `packed_ice`, `stone_sword`, `iron_sword`, `diamond_sword`, `knockback_stick`, `chainmail_armor`, `iron_armor`, `diamond_armor`, `pickaxe`, `axe`, `shears`, `arrow`, `bow`, `bow_power`, `bow_power_punch`, `speed_potion`, `jump_potion`, `invisibility_potion`, `golden_apple`, `bedbug`, `dream_defender`, `fireball`, `tnt`, `ender_pearl`, `water_bucket`, `bridge_egg`, `magic_milk`, `sponge`, `popup_tower`; in Armed `magnum`, `rifle`, `smg`, `flamethrower`, `shotgun`; and in the weeks they rotate in `lucky_chest`, `sugar_cookie`, `cobweb`, `mega_tnt`, `hay_bale`, `block_zapper`, `bridge_zapper`, `throwable_tnt`) through the same purchase as the Item Shop's button, for a player within reach of one of the match's Item Shop villagers; it returns whether the item was bought. `public static boolean botUpgrade(ServerPlayer player, String upgrade)` does the same for a team upgrade (`sharpened_swords`, `reinforced_armor`, `maniac_miner`, `forge`, `heal_pool`, `dragon_buff`, `cushioned_boots`, and in Armed `deadshot`) at a Team Upgrades villager. Bought items go into the player's Hotbar Manager slots, which are empty by default.
  - Every player carries a compass (custom data `brainage_minigames:bedwars_item` = `tracker`) in the last hotbar slot by default. An island generator's iron and gold lie in one item stack carrying the entity tag `brainage_minigames:bedwars_forge_item`, which vanilla never picks up: the game hands it each tick to every player of the generator's team within pickup reach of it, or to an enemy alone there.
  - Shopkeepers, generator holograms and the game's mobs carry the entity tag `brainage_minigames:bedwars`; a mob or dragon fighting for a team also carries `brainage_minigames:bedwars_team=<team>`.
- **Grinch Simulator** — `game.grinch.GrinchSimulatorGame`: `public static @Nullable Map<String, Object> botView(ServerPlayer player)` describes the active Grinch Simulator match the player plays in, or returns `null`. Keys: `"presents"` (`List<BlockPos>`, every present still to steal) and `"stolen"` (`Integer`, the player's own presents). A present is stolen by using (right-clicking) its block, from within reach, as a client would.

## Building and verification

```shell
flock /tmp/brainage-minigames-gametest.lock ./gradlew --no-daemon build runAllGameTests
```

With FabricModdingConventions 2.4.22, `runAllGameTests` runs all five GameTest tasks
sequentially: `:fabric:runGameTest` and `:neoforge:runGameTest` in development, then
`:fabric:runProductionServerGameTest`, `:fabric:runProductionClientGameTest`, and
`:neoforge:runProductionServerGameTest` against the release JARs. Run any of these
tasks directly for a single environment; development clients use `:fabric:runClient`
and `:neoforge:runClient`. Record the Fabric showcase with `:fabric:recordClientGameTest`.

Every server GameTest body lives in `common/src/gametest/java`, and both loaders run all of
them. `BrainageMinigamesGameTests.functions()` maps each test id to its body; Fabric registers
the map from its GameTest mod's `main` entrypoint and NeoForge through `RegisterEvent`. Each
test's structure, environment and time limit are in
`common/src/gametest/resources/data/brainage_minigames/test_instance/<id>.json`, so the same
tests run in development and production. Each loader adds the natural game dimensions, which the
flat GameTest world lacks, before every test. Run a subset on Fabric with
`JAVA_TOOL_OPTIONS=-Dfabric-api.gametest.filter=brainage_minigames:<id-glob>`.
A stopping GameTest server writes no chunks, entities or points of interest, including writes
still queued, because every launch starts from a new world; a running one saves as usual.
A running one also writes its region files without `O_DSYNC`, whatever `sync-chunk-writes`
says: vanilla's GameTest server always writes synchronously and a dedicated server follows
server.properties, which defaults to `true`, so every chunk save would wait for the disk.
Flushing and closing a region file do not force it to disk either. `GameTestMixinPlugin`
adds these GameTest-only mixins on both loaders, in development and production, only when
the GameTest source set is on the classpath.
NeoForge requires only Minecraft and NeoForge; any additional required mod dependency in
`neoforge.mods.toml` must also be declared in `neoforge/build.gradle` as
`productionRuntimeMods` for the installed production server. See
[release instructions](docs/RELEASE.md) and [Modrinth publishing](docs/MODRINTH.md)
for distribution.

The shared UHC mode GameTests cover the sunrise lock through lobby/countdown, immediate
clock release at grace start, brightness at every tick of the default ten-minute grace,
FFA/game-label casing and the date/time footer within the 15-line client limit, plus
the exact bot-policy gamerule id, fresh-world default and boolean command toggle.

Asynchronous UHC fixture preparation does not consume the accelerated GameTest tick budget.
The behaviour-test timeout resumes when terrain is ready, with a separate three-minute
real-time guard for a genuine generation hang. Completion listeners close fixtures and restore
settings on both success and failure; sunrise tests clear and restore the real weather flags
and timers as well as the visible rain/thunder levels.
Deathmatch tests sample the positional sun-angle attribute at the survivor's location on both loaders.

The concurrent UHC tests cover independent surface/Nether shrink and damage, simultaneous
UHC/Meetup/FinalUHC matches, portal trips and per-match Nether closure, combat loggers and coins,
independent deathmatch slots, region release/replay, and regeneration of all six natural dimensions.

## License

Brainage Minigames is available under the [MIT License](LICENSE).
