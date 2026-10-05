# Brainage Minigames

A server-side mod for Minecraft 26.2 that runs minigames on an ordinary server: UHC and a set of kit duels, with any team layout and any number of matches at once. Clients do not need to install the mod.

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
| BuildUHC | `build_uhc` | Survival-mode kit fight with the BuildUHC kit (gear, lava, water, blocks); no natural regeneration. |
| Classic | `classic` | Iron gear, bow and rod. |
| No Debuff | `no_debuff` | Diamond gear, healing splash potions, speed potions and ender pearls. |
| Gapple | `gapple` | Protection IV diamond gear, 64 golden apples, strength and speed potions. |
| Boxing | `boxing` | Nobody takes damage; each landed hit scores a point and the first team to 100 hits wins. |
| Combo | `combo` | Players can be hit every 2 ticks (ten hits a second) instead of every 10, and attacks have no cooldown, so every swing is at full strength and combos land. |
| Bow | `bow` | Only projectiles do damage. |
| SkyWars | `skywars` | Glass cages above separate floating islands open after the countdown; loot island and mid chests, knock each other into the void, last team standing wins. Chests refill at 3:00 and 5:00. |
| Meetup | `meetup` | The end of a UHC: a random late-game kit (see [Meetup](#meetup)), PvP from the start, no natural regeneration, items drop on elimination, and a border around a patch of generated terrain that starts at 100 blocks across and closes in by 25 blocks every minute from 2:00 until it is 10 across. Last team standing; 15-minute limit. |
| FinalUHC | `final_uhc` | Minemen's Final UHC duel: identical late-game gear, building, lava and water allowed, no natural regeneration, on generated terrain inside a 100-block border. 15-minute limit. |
| Spleef | `spleef` | Dig out the snow floors under your opponents with an instant-breaking shovel; every block dug gives snowballs that break the floor where they land and knock players back. Nobody takes damage; falling below the lowest floor eliminates. See [Spleef](#spleef). |
| Bow Spleef | `bow_spleef` | Shoot the TNT floor out from under your opponents with a flame bow; arrows remove the TNT they hit without exploding it and never hurt players. Double jumps, triple shots and repulsors; falling into the void eliminates. |
| Bridge | `bridge` | Hypixel's The Bridge: jump into the other team's goal to score; every goal rebuilds the map and puts everyone back in their cages. Deaths only send you back to base. First to 5 goals; 15-minute limit. See [Bridge](#bridge). |
| Battle Rush | `battle_rush` | Minemen's Battle Rush: Bridge with only wool and shears and nothing linking the islands, so you rush across with wool and knock opponents off with your fists. First to 3 goals; 10-minute limit. |
| Quake | `quake` | Hypixel's Quakecraft: a railgun kills with one instant beam that walls stop, a feather dashes; killed players respawn at once away from their opponents. First to 25 kills (100 for teams); no melee or fall damage. |
| Pearl Fight | `pearl_fight` | Minemen's Pearl Fight: knockback stick, ender pearls, wool and shears on floating platforms; nobody takes damage, knocking an opponent into the void scores and starts a new round. First to 3 points. |
| Parkour | `parkour` | Hypixel's Parkour Duels: everyone runs the same course from one start line through every checkpoint in order; the first to finish wins. Falls send you back to your last checkpoint, nobody can hurt or push anyone, and a boost feather throws you forward on a cooldown. See [Parkour](#parkour). |
| Ice Boat Racing | `ice_boat_racing` | Every racer drives their own boat around an ice track through every checkpoint gate in order; the first to finish 3 laps wins. Leaving your boat gets you a new one at your last checkpoint. |

The layout grammar accepts **any number of teams, each of any size**, e.g. `1v2` or `2v3v4`: these are examples, not a fixed list. Write sizes separated by `v` (2–100 teams, 1–100 players per team), or `ffa` for free-for-all. Map games enforce their available team/start slots, and `/duel` has its separate invitation limit.

Every game supports every team layout. Duels run in separate barrier-walled arenas in the void `brainage_minigames:minigames` dimension. UHC, Meetup and FinalUHC can also run concurrently, including multiple matches of each type. Each match owns its border and a separate generated region; dimension-global borders remain untouched. UHC uses `brainage_minigames:uhc` and `uhc_nether`, Meetup uses `meetup` and `meetup_nether`, and FinalUHC uses `final_uhc` and `final_uhc_nether`, all in the `brainage_minigames` namespace. Each pair has its own dimension types and clock, so UHC daylight does not change Meetup or FinalUHC daylight. The six natural dimensions are scheduled for regeneration when a match opens and regenerated after the server stops or before it starts, never while any of their regions are in use. A stopping server does not write their chunks, since it deletes them right after. UHC tries up to 16 unoccupied random regions and takes the one with the most land inside its starting border (ocean and river count as water), favouring dry ground at the centre; a region at least 85% land is taken at once. Reservations account for both surface and Nether widths, including a custom Nether divisor, so large regions cannot overlap. The lobby, original team starts and nether-close returns use the nearest solid, dry ground within 48 blocks, falling back to the water surface only if none exists. Instant Badlion teleports keep the exact five-block horizontal inset. Lobby players are brought back if they wander more than 16 blocks away.

UHC team starts are prepared with temporary chunk-loading tickets, at most four new chunk neighbourhoods per tick and eight still loading at a time. Players stay in the lobby while the sidebar says **Preparing: spawn terrain**; the configured countdown and grace clock do not advance until every spawn and its immediate neighbouring chunks are fully loaded. Starts form an inward-facing ring with a 48-block dry-ground search and a water-surface fallback, with candidates kept inside the starting border. Terrain reads never synchronously generate the spread's chunks in the start command or countdown tick, so large lobbies do not stall one tick generating all their starts. Preparation retains its own tickets independently of the asynchronous load, including with replacement chunk systems such as Moonrise; completing a load cannot unload earlier teams' spawn neighbourhoods while other teams are still searching. Tickets are released after placement or if the match is stopped.

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

The deathmatch template is generated by `python3 tools/maps/uhc_deathmatch.py` into `structure/maps/uhc_deathmatch/colosseum.nbt`. It occupies a slot in the **`brainage_minigames:minigames`** void dimension, independently of other maps and duels, and is cleared when the match closes. This dimension has fixed midday, clear weather and **no mob spawning**; existing mobs in the slot are removed at the transition. Its match-local border shrinks independently without changing the dimension's global border or any other slot. Survivors, including those still in the UHC Nether, and spectators transfer together; saved snapshots restore everyone's original dimension, position and state when they leave or the match closes. The starting 10-minute Fire Resistance hides its particles while keeping its HUD icon; other effects retain their normal particle behaviour.

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
| `uhc_max_all_kits` | `false` | Treat every selectable kit as tier III and prestiged, independently of max-perks |
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
/gamerule brainage_minigames:uhc_max_all_perks true
```

The shop includes all **13 profession trees (52 recipes), 30 Extra recipes and 10 selectable kits**. Crafted recipe previews, taking results and shift-crafting enforce ownership in active UHC matches only. Recipes include level-I paper/flint books, eight-gold Golden Heads and four-gold Light Apples, plus enchanted weapons/tools, Forge, Backpack and Fusion Armor.

`brainage_minigames:uhc_max_all_perks` defaults **false** and treats every profession, profession prestige and Extra recipe as unlocked without changing saved purchases. Kits have their own independent rule, **`uhc_max_all_kits`**, also **false** by default: it gives every selectable kit tier III and prestige without changing purchases. Neither max rule enables the other.

A player with no personal kit selection gets **Stone Gear** (the four stone tools). `/minigames uhc kit default` selects Stone Gear. Explicit match kit overrides still take precedence. With **`uhc_choose_prestige_bonus`** enabled (default **false**), `/minigames uhc prestige_bonus <kit>` lists clickable bonus choices, and `/minigames uhc prestige_bonus stone iron_pickaxe` saves that kit's choice per UUID. The player must have prestiged the kit, either through purchases or the max-kits rule. When selection is off, the original weighted random roll applies. When selection is on but no choice has been saved, the first listed bonus is used.

`uhc_unlimited_crafts` and `uhc_no_duplicate_crafts` both default **true**. Turn unlimited off for three normal crafts/one ultimate (profession prestige adds one); Extra Ultimates remain one craft. No-duplicates controls the existing random-result pools, not recipe ownership. Turn it off for independent random results.

When an active regular-UHC player has the ingredients for an unlocked craft with remaining uses, chat names the craft and offers **[Craft]**. Clicking runs `/minigames uhc craft <recipe>` and opens a server-side crafting-table menu with exactly one craft's ingredients moved from the inventory into the grid. One output click crafts normally; closing returns unused grid and cursor items. No client mod is required. A prompt is not repeated while that recipe's ingredients remain unchanged; unrelated items do not reset it. The command rechecks ingredients, unlocks and craft limits, so an old message cannot bypass them.

The [complete source-cited catalog](docs/UHC_PROGRESSION.md) lists every tree node, passive level, recipe, kit level, coin reward and historical shop price. Official 2015/2017/2019/2020 announcements override older player-authored forum guides. Unpublished current prices, lost image ingredients and approximation parameters are explicitly marked **not Hypixel-confirmed**; the official wiki material found was SkyBlock, not a UHC Champions catalog.

### UHC resource gamerules

These world-persisted `/gamerule` settings apply **only** in `brainage_minigames:uhc` and `brainage_minigames:uhc_nether`, including other games played in those dimensions. The Overworld, vanilla Nether and every other dimension remain vanilla. Every rule defaults to **200 percent (2.0×)**; `100` restores vanilla rates, `150` means 1.5×, `50` means 0.5×, and `0` disables the corresponding drops or placed-feature attempts. Values are nonnegative integer percentages.

Minecraft 26.2's built-in gamerule types and visitors support only booleans and integers. Fabric offers its own double extension, while NeoForge requires a different enum/visitor/client integration; there is no clean shared floating-point type compatible with this server-only mod's vanilla clients. Percentages therefore provide fractional multipliers consistently on both loaders without a new dependency or custom client requirement.

All names below have the `brainage_minigames:` namespace:

| Resource | Generation rule | Drop rule |
| --- | --- | --- |
| Apples from oak/dark oak leaves | — | `uhc_apple_drop_percent` |
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

- **Drops:** vanilla first determines the loot, including Fortune and explosion survival. Each eligible item that would drop gives `floor(multiplier)` copies plus one extra with probability equal to the fractional part: at 150%, each raw iron or apple gives one guaranteed item and a 50% chance of a second. Counts exceeding a stack are split without loss. The apple rule multiplies only apples from broken or decayed natural oak/dark oak leaves: it does not increase the initial vanilla apple chance or change saplings, sticks or leaf-block drops. Ore rules share normal/deepslate variants; XP is unchanged.
- **No replanting duplication:** resources placed by anyone, including non-participants, keep vanilla drops when mined or decayed. Their positions are recorded separately in each UHC dimension, survive saves/restarts, and are cleared when that dimension is regenerated. Drops of the broken block's own item (such as Silk Touch ore blocks or sheared leaves) are never multiplied. The one normal-loot exception is a first break of natural ancient debris **without Silk Touch**: it still receives the debris multiplier, but placing and mining the resulting items cannot multiply them again.
- **Generation:** each ore placed-feature pipeline runs `floor(multiplier)` times, with one additional run chosen by the fractional probability per feature per chunk. This scales attempts/vein counts, including rare veins using rarity filters, without resizing veins or changing their height/biome restrictions. Ore block totals are statistical, not exactly proportional: attempts can overlap or find no suitable stone. Noise-based large copper/iron veins retain vanilla behavior.
- **New chunks only:** changing generation rules never edits already generated chunks. Set them before opening a match/loading its region; subsequent fresh chunks use the current values. The gamerules persist when the UHC dimensions are regenerated.

### Anti-janitor protection

`/gamerule brainage_minigames:anti_janitor true` enables exclusive fights in **public UHC, Meetup, FinalUHC and SkyWars matches**, in free-for-all layouts or layouts with at least three teams. It is **on by default**. Private `/duel` matches, two-team matches, kit duels and respawn/non-PvP games are unaffected: the protection is intended for survival/elimination matches with several competing opponents, not to change ordinary team or duel combat. Teammates never start a duel with each other and cannot interfere with a teammate's locked opponent.

The first accepted player hit that actually removes health **or absorption** locks the two players together. While locked, they can damage only each other, and no third player can damage either of them. Every damaging hit in either direction resets their shared countdown; invulnerability-rejected hits, fully blocked hits and zero-damage eggs, snowballs or fishing rods do not start or refresh it. Permission checks alone never create a lock. The first damaging pair wins a three-way exchange; later incompatible hits are refused. A remaining-seconds countdown appears in the action bar.

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

`bow` permits only projectile damage. Any team layout works in an independent arena slot.

### Meetup

Meetup plays on generated terrain in `brainage_minigames:meetup`; each match gets a far-away region with its own border instead of the dimension's world border. That border is sent only to the match's members, who see it and are stopped by it as usual; anyone more than a block outside it is hurt by 0.5 per further block (at least 1) as by a vanilla border. Placing and breaking blocks is allowed only inside it. Any number of Meetups can run alongside UHC and FinalUHC matches. The region is the first of up to eight unoccupied random ones whose centre and four points around it are dry land (otherwise the driest), and the lobby and every spawn are on the nearest solid, dry ground inside the border, 20 blocks in from it. Its independent clock stays at noon. The dimension and its paired `meetup_nether` are regenerated with the other natural dimension pairs after the server stops or before it starts.

Meetup follows the usual UHC Meetup plugins: every player's kit (`kits/meetup`) is rolled separately. It picks one of three tiers from `brainage_minigames:meetup/`, each trading armour for damage and healing, with random slots for its diamond pieces:

| Tier | Armour | Sword | Bow | Golden apples | Golden heads |
| --- | --- | --- | --- | --- | --- |
| `diamond` | 3 diamond + 1 iron piece, Protection I | Diamond, Sharpness I | Power I | 4 | 2 |
| `mixed` | 2 diamond + 2 iron pieces, Protection II | Diamond, Sharpness I | Power II | 5 | 2 |
| `iron` | 1 diamond + 3 iron pieces, Protection II | Diamond, Sharpness II | Power III | 6 | 3 |

Everyone also gets a fishing rod, 32 arrows, a diamond axe and pickaxe, 64 steak, 64 cobblestone or oak planks, two water buckets, two lava buckets and flint and steel. A golden head is a golden apple named Golden Head that gives Regeneration II for 10 seconds (twice an apple's healing) and Absorption for two minutes. The plugins' enchanting table, anvil and experience bottles are left out, as matches are too short to use them.

### FinalUHC

FinalUHC uses the [same generated-terrain placement and independent border lifecycle as Meetup](#meetup), in its own `brainage_minigames:final_uhc` dimension with an independent noon clock and paired `final_uhc_nether`. It can run alongside any number of UHCs, Meetups and other FinalUHCs, and uses a fixed kit rather than a random one.

FinalUHC is Minemen's Final UHC, compared against its public match inventories (for example [this match](http://minemen.club/match/d7f6ddd6-29f5-3d9c-aa3a-4583e4d1be0c) and several of Rwcist's): every player finishes with full diamond armour, a diamond sword, axe and pickaxe, 16 golden apples, 64 steak, two stacks each of planks and cobblestone, six buckets between water, lava and empty, flint and steel and a fishing rod. The kit (`kits/final_uhc`) is exactly that, with 3 water and 3 lava buckets. The match pages do not show enchantments, so the levels are assumed: Protection II armour, Sharpness III sword, Efficiency III axe and pickaxe. Compared with BuildUHC, FinalUHC has no bow, arrows, or random enchantment levels, twice the blocks, three of each bucket instead of one, flint and steel, and 16 golden apples every time; and it is fought on hills, trees and caves inside a border rather than on BuildUHC's flat grass floor in a barrier box.


## Playing

```text
/minigames list
/minigames join [match] [team]
/minigames watch <match>
/minigames leave
/minigames status <match>
/minigames elo [player]
```

`join` without a match number joins the only open lobby. A team number requests a team; everyone else is assigned randomly. Joining saves your position, dimension, inventory, game mode, effects, health, hunger, experience and scoreboard team, and all of it is restored when you leave or the match ends. Rewards from the `brainage_minigames:rewards/default` loot table (empty by default; override it with a datapack) are added after restoration, and every win increments the `brainage_games_won` scoreboard objective.

Leaving during a match forfeits. Disconnecting during a match eliminates you, and your state is restored when you reconnect. Dying never kills you: you become a spectator until the match ends, and are then switched back to your saved game mode.

While you are in or watching a match you see its own sidebar, sent only to you: the game and layout, the match number, the lobby size, countdown, elapsed time and time limit or result, your team, and, in team matches, each team with its score and how many of it are alive. Players are not listed there (the tab list does that), except in free-for-all games that score each player (kills, points, race progress), where the sidebar shows those standings. Boxing adds each team's hits and the target, Combo the hit delay, and UHC the time until PvP, the border size, the next shrink, deathmatch and nether-close countdowns, and the players alive; Meetup the border size, the time until the next shrink (or the size it is shrinking to) and the players alive, and FinalUHC the border size. Your tab list shows every participant's actual health as a number (normally 20 is full, or 40 in default double-health UHC, rounded up), cleared once they are eliminated. Both refresh twice a second, never change the server scoreboard, and when you leave the server's own sidebar and tab list objectives (such as `brainage_games_won`, if displayed) come back.

Chat, tab-list and sidebar names use only the nine bright team colours (red, blue, green, yellow, aqua, light purple, gold, white and gray), never black or the dark variants. The palette repeats for larger matches; numbered team names and each FFA player's own name remain distinct, including 50-player free-for-alls. Bridge's unfilled score dots also use readable gray.


### Elo ratings

Every human player starts at **2000 Elo**. Ratings follow the player's UUID, are saved with the world's scoreboard (`brainage_elo_uuid` is the internal UUID ledger), and survive reconnects, name changes and server restarts. `/minigames elo` shows your rating; `/minigames elo <player>` shows an online player's rating without requiring game-master permission. The public dummy objective **`brainage_elo`** publishes ratings under player names so other mods and commands can read them without a compile dependency.

`/gamerule brainage_minigames:elo_k_factor 32` controls updates globally and is world-persisted; **32** is the default and **0** disables changes. It is a gamerule rather than a per-game setting because the same rating is shared across games. Given ratings `R` and `O`, the expected score is `1 / (1 + 10^((O - R) / 400))`. The change is `K * (score - expected)`, where a win scores `1`, a loss `0`, and a draw `0.5`. Changes are rounded to integer Elo for scoreboard publication. Each result uses pre-result ratings for both sides, and human wins and losses count against both humans and bots. A bot's published Elo stays fixed.

- **Duels/team layouts:** the match result updates each participant once; forfeiting or disconnecting still counts. With multiple opposing players, each participant uses the mean expectation against the other teams, so one match applies one K-scaled update rather than multiplying K by team size. Teammates never rate against one another.
- **Free-for-all policy:** each credited kill immediately counts as the killer's win over the victim and the victim's loss. Environmental deaths without a credited player, forfeits without a kill, and the final last-player-standing result add no separate update. At a drawn match end, **every pair of remaining players counts as a draw**, including remaining bots as fixed-rated opponents; all pair expectations are taken before applying the end-of-match changes. Eliminated players are excluded from that final draw. This is the FFA policy for UHC and the other games when opened with `ffa`.
- Stopping/cancelling a match without a result does not rate it. Spectators are never participants in rating updates.

Sparring Bots tags its players `sparringbot` and publishes their fixed rating in `brainage_elo`; this is the scoreboard/tag integration contract, not a linked dependency. Its `sparringbots:bot_elo_offset` gamerule makes a bot fighting a rated human use the human's Elo plus an offset while retaining its own fixed rating.


## Duels

Any player can challenge others without game-master permission:

```text
/duel <game> <layout> <player> [<player> ...]
/duel accept <challenger>
/duel deny <challenger>
/duel cancel
```

List up to 15 other players. The participants are you followed by the listed players, and teams are filled in that order: `/duel classic 2v2 Bob Carol Dave` puts you and Bob against Carol and Dave. A fixed layout needs exactly as many players as it has slots; `ffa` needs at least one other player. Nobody may be listed twice, already be in a match, or be part of another pending duel, and each player can have only one pending request.

Each invited player gets one chat message with clickable `[Accept]` and `[Deny]` buttons; hovering `[Accept]` lists every player. Once everyone has accepted, the match opens with the game's own kit, everyone joins their team, and it starts. Duel matches are private: they are not announced, and nobody else can join them, though anyone can watch. A request is cancelled when anyone denies it, the challenger runs `/duel cancel`, a participant leaves the server, or 60 seconds pass without everyone accepting. If a player is no longer available when the last invitee accepts, the duel is cancelled and everyone is told why.

## Running matches

These commands require game-master permission:

```text
/minigames open <game> <layout> [kit]
/minigames start <match>
/minigames stop <match>
```

A layout is `ffa` (everyone for themselves) or **any number of teams, each of any size**, written as sizes separated by `v`, e.g. `1v1`, `2v2`, `1v2`, `2v3v4`, `1v1v1v1`. These are examples, not a fixed list: the parser accepts 2–100 teams of 1–100 players each. A game's map may limit the number of teams or total players; `/duel` also accepts at most 15 invitees. Fixed layouts start by themselves when every slot is filled; free-for-all matches start with `/minigames start` once at least two players have joined. The optional kit replaces the game's kit, for example `/minigames open classic 2v2 brainage_minigames:kits/instant_crossbow`. `/minigames help` explains the layout syntax and main commands.

## Settings

```text
/minigames settings <game>
/minigames settings <game> <setting> <value>
/minigames settings <game> <setting> reset
```

Settings are stored per world and apply to matches opened afterwards. Every game has `countdown_seconds`, `time_limit_minutes` (remaining leaders draw when it runs out; `0` disables it) and `natural_regeneration` (`1` or `0`, applying only to participants). UHC's border and deathmatch settings and defaults are listed [above](#uhc-border-modes-deathmatch-and-daylight); `grace_period_minutes` defaults to 10. Boxing adds `hits_to_win`; Combo adds `hit_delay_ticks` (1–10 ticks between hits; vanilla is 10). Meetup adds `border_start_size` (100), `first_shrink_seconds` (120), `shrink_interval_seconds` (60), `shrink_step` (25 blocks off the whole width), `final_size` (10) and `shrink_duration_seconds` (10; `0` is instant); FinalUHC adds `border_size` (100).

## Kits

Kits are loot tables. The bundled ones are `brainage_minigames:kits/` followed by `barebones`, `battle_rush`, `bow`, `boxing`, `bridge`, `build_uhc`, `classic`, `combo`, `final_uhc`, `gapple`, `instant_crossbow`, `instant_firework_crossbow`, `meetup`, `no_debuff`, `parkour`, `pearl_fight`, `quake` and `uhc_starter`, plus `brainage_minigames:empty`. Armour in a kit is worn automatically.

Kits can be edited in game with game-master permission:

```text
/minigames kit edit <kit>
/minigames kit give <kit> [players]
/minigames kit delete <kit>
/minigames kit list
```

`edit` opens a six-row container; put the items in it and close it to save. Editing a bundled kit creates a world-specific override, and deleting the override restores the bundled kit.

## SkyWars

Modelled on Hypixel SkyWars (normal mode) and Minemen SkyWars duels. Every team starts in a glass cage above its own floating island; the cages are built when the map is pasted and disappear when the countdown ends, dropping everyone three blocks onto their island. Players start with the `brainage_minigames:kits/skywars` kit (a stone pickaxe, axe and shovel, like Hypixel's Default kit) and play in survival: blocks can be broken and placed anywhere inside the map's bounds, but not outside them. Each island has three `brainage_minigames:skywars/island` chests (blocks, usually one chain or iron armour piece, a stone or iron sword or axe, food, snowballs or eggs, sometimes an ender pearl, bucket, rod or pickaxe) and the mid island has four to six `brainage_minigames:skywars/mid` chests (diamond or Protection iron armour, diamond sword, Sharpness iron sword or bow with arrows, ender pearls, golden apples). Every chest is rolled fresh when the cages open and refilled (new loot into its empty slots) at `first_refill_seconds` (180) and `second_refill_seconds` (300; `0` disables either). Falling below the map's void level or dying eliminates you and drops your items; the last team standing wins, and when `time_limit_minutes` (9) runs out the remaining teams draw. The sidebar shows the players left, the next event (refill or game end) and its countdown, the map, and each team's kills. Defaults: 10-second countdown, natural regeneration on.

Free-for-all and any team layout up to the map's island count work (each island is one team); a layout with more teams than any map has islands is refused when the match opens, and a free-for-all lobby is full once every island is taken. A random map with enough islands is picked:

| Map | Islands | Notes |
| --- | --- | --- |
| `frostbite` | 2 | Snowy spruce islands 24 blocks either side of a mid; for duels. |
| `mesa` | 4 | Red sand and terracotta islands 26 blocks from mid in the four directions. |
| `archipelago` | 8 | Grassy oak islands in a ring 34 blocks from a mid with six chests; team numbers alternate around the ring so fewer teams are spread out. |

The maps are generated by `python3 tools/maps/skywars.py` into `data/brainage_minigames/structure/maps/skywars/`. They are larger than the 48-block limit of an in-game structure block, so edit them through the script (or in parts).

## Spleef

Spleef follows Hypixel's Spleef Duels. Everyone gets the `brainage_minigames:kits/spleef` kit, an unbreakable Efficiency V diamond shovel that breaks snow instantly, and plays in survival. Only shovel-mineable blocks (snow, clay) inside a map's `floor_<n>` regions can be broken, and only with a shovel in hand; the walls and everything else stay. Dug blocks drop nothing: the digger gets `snowballs_per_block` snowballs (default 2) instead, up to `snowball_cap` held (default 16, Hypixel's cap). A snowball thrown by a player breaks the floor block it lands on and knocks back players it hits; nothing else does damage and nobody can place blocks. Hunger stays full. Falling below the map's `void` marker eliminates, and the last team standing wins. `camp_seconds` (default 0, off) breaks the floor block under a player who stands on it that long.

Spleef works with free-for-all and any team layout up to the map's eight spawns; a random map with room for the layout is picked.

| Map | Floors | Notes |
| --- | --- | --- |
| `glacier` | 3 | Round packed-ice tower, 25-block snow floors six blocks apart (the middle one ringed with clay), sea lanterns and spruce pillars. |
| `lantern_pit` | 2 | Square deepslate pit with 21-block snow floors seven blocks apart, lit by froglights. |

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

Bridge follows Hypixel's The Bridge (and Minemen's Bridge, whose kit is the same). Each team has an island with a goal, a hole five blocks across with a crying-obsidian floor, and a glass cage above its spawn. Jumping into another team's goal scores for your team: everyone sees who scored and the score, the map is rebuilt as it was (placed blocks, arrows and dropped items disappear), every player is reset with a full kit into their team's cage, and the cages open after `cage_seconds` (default 5) with a countdown on the action bar. Players cannot move, fight, build or be hurt while caged. The countdown before the first round is the match's `countdown_seconds`, also in the cages. Jumping into your own goal scores nothing and sends you back to your spawn.

Dying, including falling below the map's void height or being knocked off, never eliminates: you respawn at once at your team's spawn with your kit refilled, and whoever last hit you gets the kill. You can only break blocks placed during the current round, and place blocks only inside the map's `build` regions (a few blocks above and below the bridge, stopping short of the goals) and never in a goal. Hunger does not drop. The first team to `goals_to_win` goals wins (default 5); when the time limit runs out the team with the most goals wins, or the leaders draw.

The `brainage_minigames:kits/bridge` kit is Hypixel's: iron sword, bow, one arrow, Efficiency II diamond pickaxe, two stacks of clay, eight golden apples and a leather chestplate, leggings and boots. The clay (and any terracotta, wool, stained glass or concrete in whatever kit the match uses) is turned into the team's colour and leather armour is dyed to match. The arrow comes back 3.5 seconds after you run out, and a golden apple heals you fully on top of its absorption hearts.

The sidebar shows each team's goals as dots out of the target and its kills, the goals needed, the map and the round. Bridge works with any team layout up to the map's goals; a map with exactly as many goals as the layout has teams is preferred, and free-for-all uses the map with the most goals.

| Game | Map | Teams | Notes |
| --- | --- | --- | --- |
| Bridge | `grove` | 2 | Grassy oak islands 30 blocks apart, joined by a one-block andesite bridge with a small lantern-lit resting platform in the middle. |
| Bridge | `basalt` | 2 | Crimson nylium and blackstone islands 34 blocks apart with shroomlight basalt pillars; the blackstone-brick bridge crosses two basalt stepping pillars. |
| Bridge | `compass` | 4 | Four birch islands around a stone-brick plaza, each 16 blocks out along its own bridge; four teams, each of any size accepted by the layout parser. |

Maps mark `spawn <team>` inside the cage, `region cage_<team>` around the cage (cleared when a round starts), `region goal_<team>` over the goal hole, `region build` where blocks may be placed and `void`. They are generated by `python3 tools/maps/bridge.py` into `data/brainage_minigames/structure/maps/bridge/`; the maps are larger than the 48-block limit of an in-game structure block, so edit them through the script (or in parts).

## Battle Rush

Battle Rush follows Minemen's Battle Rush, whose match pages show only 64 wool and shears in every player's inventory: the `brainage_minigames:kits/battle_rush` kit is exactly that, so fights are fist knockback duels. Its islands are smaller and closer and nothing links them, so each round starts with both players rushing across with wool. Everything else works as in Bridge; the first team to 3 goals wins, with a 10-minute limit.

Battle Rush uses [Bridge's scoring, cages, respawns, building rules and sidebar](#bridge), with its own kit, map and goal target. Any team layout up to the map's goals works.

| Game | Map | Teams | Notes |
| --- | --- | --- | --- |
| Battle Rush | `lilypond` | 2 | Mossy islands 20 blocks apart, each with a lily-pad pond; three-block goals. |
| Battle Rush | `driftwood` | 2 | Sandy islands 24 blocks apart with a lone driftwood post below the gap. |

Maps use the [Bridge marker grammar](#bridge) and are generated by `python3 tools/maps/battle_rush.py` into `data/brainage_minigames/structure/maps/battle_rush/`.


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
| Pearl Fight | `skyreach` | 4 | Four quartz home platforms 17 blocks around a grassy central island, with end-stone stepping stones and amethyst perches between them. |
| Pearl Fight | `twin_peaks` | 2 | Two long andesite platforms 28 blocks apart joined by a broken slab bridge, with a higher island to either side. |

Pearl Fight maps mark `spawn <team>` and `region build`. They are generated by `python3 tools/maps/pearl_fight.py` into `data/brainage_minigames/structure/maps/pearl_fight/`.


## Parkour

Parkour is a race on a map: everyone starts together, must pass the map's `region checkpoint_1`, `checkpoint_2`, … in that order and then its `region finish`; a gate passed out of order counts for nothing, so skipping part of the course never helps. The first player to finish wins for their team. Nobody is eliminated and nobody takes damage (no PvP, no fall damage), and racers pass through each other (the match teams' collision rule is `never`). A racer who falls five blocks below their last checkpoint, touches a `region fail` or drops into the void is put back at their last checkpoint, or at the start before the first one. The action bar shows each racer's checkpoint and time; the sidebar shows the checkpoint count and each team's furthest racer. When `time_limit_minutes` (default 10) runs out, the team that got furthest wins, and teams level on gates draw. Free-for-all and every team layout work up to the map's eight start slots.

Parkour follows Hypixel's Parkour Duels (an eight-player race through checkpointed sections, with a boost feather). Runners play in adventure mode with the `brainage_minigames:kits/parkour` kit: a Boost feather, which throws them forwards and upwards and then waits `boost_cooldown_seconds` (default 15; the cooldown shows on the item), and a Back to checkpoint pressure plate that returns them at once. Parkour has one lap.

| Game | Map | Notes |
| --- | --- | --- |
| Parkour | `canopy` | A jungle course heading east over a pool: a log warm-up, a ladder wall and a jump onto a hanging ladder, fence posts, a leaf staircase heading north and a tall ladder to a gold finish; 4 checkpoints. |
| Parkour | `spire` | A square spiral climbing one and a half times around a quartz tower: quartz, fence, top-slab and purpur jumps with three ladders, a checkpoint at every corner and the finish above the start; 6 checkpoints. |

Checkpoint gates may carry a `point` of the same name (`point checkpoint_<n> <yaw>`, `point finish <yaw>`) setting where and facing which way racers are put back; without one they stand at the bottom centre of the gate facing the next gate. Parkour courses never descend more than a block below the previous checkpoint, since a five-block drop counts as a fall. Maps are generated by `python3 tools/maps/parkour.py` into `data/brainage_minigames/structure/maps/parkour/`; they are larger than the 48-block limit of an in-game structure block, so edit them through the script (or in parts).

## Ice Boat Racing

Ice Boat Racing uses [Parkour's ordered checkpoints, finish gate, no-damage rules, team scoring and race displays](#parkour). It also accepts free-for-all and any team layout up to the map's eight start slots. A `region fail` or the void returns a racer to their last checkpoint; simply driving downhill does not.

Ice Boat Racing puts every racer in an oak boat on the map's `point boat_<n>` grid slot (in team order) when the race starts. Boats cannot be broken; a racer who is out of their boat for any reason gets a new one at their last checkpoint (or the finish line, after a lap), and the old one is removed. `laps` sets the laps (default 3). Boats are removed when their racer leaves and when the race ends, and racers are taken out of them before being restored.

| Game | Map | Notes |
| --- | --- | --- |
| Ice Boat Racing | `frostbite_oval` | A 266-block oval, 9 wide: two 70-block straights (blue ice down the back one) joined by half circles of radius 20; 3 checkpoints. |
| Ice Boat Racing | `glacier_circuit` | A technical 305-block circuit, 7 wide: a blue-ice main straight, a fast right-hand sweeper, a hairpin, a kink, four S bends and a long left-hander home; 5 checkpoints. |

Checkpoint gates use the [same optional checkpoint/finish points as Parkour](#parkour). Tracks are packed ice with blue ice only on long straights, kerbs a block high under a glass rail (a boat cannot climb them and drivers can see over them) and gates spanning the whole track four blocks high; the eight-slot grid of `point boat_<n>` (with a `spawn <n>` under each) sits behind the finish line. Maps are generated by `python3 tools/maps/ice_boat_racing.py` into `data/brainage_minigames/structure/maps/ice_boat_racing/`; they are larger than the 48-block limit of an in-game structure block, so edit them through the script (or in parts).


## Maps

Games other than UHC and the kit duels play on maps: vanilla structure templates under `data/brainage_minigames/structure/maps/<game>/<map>.nbt` (in the mod's resources, a datapack, or saved in the world by a structure block). A match picks one at random among those that have room for its teams, and `/minigames status` and the sidebar name it. Each open map is pasted into its own slot of the `brainage_minigames:minigames` void dimension with its minimum corner at Y 64, with its chunks kept loaded while the match runs; games that rebuild the map between rounds paste it again, which removes placed blocks and every non-player entity, and closing the match clears the area.

Markers are structure blocks in DATA mode whose metadata (the "Custom Data Tag Name" field) the mod reads when it pastes the map; they are then replaced by air. Words are separated by spaces and lowercase:

| Marker | Meaning |
| --- | --- |
| `lobby [yaw]` | Where players wait before the match; without one, the first spawn of team 1. |
| `spawn <team> [yaw]` | A spawn of team `<team>`, from 1; a team may have several, used in turn when its players start or respawn. Every team from 1 to the highest must have one, and the highest is how many teams the map holds. |
| `point <name> [yaw]` | A named position for the game, such as `point chest_mid` or `point boat_1 -90`. |
| `region <name> <dx> <dy> <dz>` | The whole blocks from the marker to the marker plus (`dx`, `dy`, `dz`), which may be negative. `build` limits block placing to the build regions when a map has any (otherwise anywhere inside the map); games also use `goal_<team>`, `checkpoint_<n>`, `finish`, `floor_<n>` and names of their own. |
| `void` | Players below this marker's Y die at once; without one, 10 blocks below the map. |

Yaws are in degrees: 0 faces south (+Z), 90 west, 180 north and -90 east; a marker without one faces the middle of the map. A map with an unknown or malformed marker, no spawns, or a gap in its team numbers is refused with a message naming the marker and its position in the template. Chests can carry a loot table (`LootTable` in their block-entity data), filled when a player first opens them.

The bundled maps are written by deterministic Python scripts, one per game: `python3 tools/maps/<game>.py`, run from the repository root, rewrites that game's `.nbt` files, using the `Structure` helper in `tools/maps/structure.py` (`set`, `fill`, `marker`, `save`). `tools/maps/test_map.py` builds the maps the GameTests use.

To edit a map in game, load it with a structure block in LOAD mode (structure name `brainage_minigames:maps/<game>/<map>`), change it, and save it under the same name with a SAVE-mode structure block; the saved copy in the world's `generated` folder then takes the place of the bundled one on that server. Place markers as DATA-mode structure blocks and include them in the saved area. An in-game structure block saves at most 48 × 48 × 48 blocks, so larger maps are edited in parts or through their script.

## Integration contract

Server-side mods such as SparringBots use Brainage Minigames without a compile dependency: they call public static methods by reflection, read gamerules by id, parse chat and sidebar text, and recognise dimensions and entity tags. Everything below is that contract; its names, signatures and wording are kept stable. Classes are in `io.github.brainage04.brainage_minigames`.

- **Combat loggers** — `game.UhcCombatLogger`:
  - `public static boolean canAttack(ServerPlayer attacker, Entity logger)`: whether `attacker` may hit this offline participant's zombie under match, team, PvP, grace, deathmatch-freeze and anti-janitor rules. It changes no state and records no attack.
  - `public static @Nullable UUID participant(Entity)`, `public static @Nullable Match match(Entity)` and `public static @Nullable Zombie zombie(UUID participant)`.
  - Logger zombies carry the entity tags `brainage_minigames:combat_logger` and `brainage_minigames:participant=<uuid>`.
- **Container ownership** — `game.ContainerProtection`:
  - `public static @Nullable UUID owner(Level level, BlockPos pos)`: the placer for this exact dimension, position and current block entity, or `null` for unowned, replaced or cleared containers.
  - `public static @Nullable ContainerProtection.Access lastAccess(ServerPlayer owner)`: the most recent successful foreign opening or break while protection was off. The record `Access` exposes `actor(): UUID`, `dimension(): ResourceKey<Level>`, `position(): BlockPos`, `action(): String` (`"open"` or `"break"`) and `tick(): int` (the server's tick counter). It remains after a break removes ownership and is cleared at match end or reset. Compare the tick with the current server tick and remember the last handled record; a rejected action or merely looking at a container produces no record.
- **UHC recipes** — `game.uhc.UhcCrafting`: `public static List<UhcCrafting.Recipe> recipes()` and `public static ItemStack preview(ServerPlayer player, UhcCrafting.Recipe recipe)` (empty unless the player may craft it now, by unlocks and remaining uses); `Recipe` exposes `id(): String`, `output(): Item` and `grid(): Item[]`.
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
- **Gamerules**, read by id: `brainage_minigames:pre_pvp_following`, `brainage_minigames:container_protection`, `brainage_minigames:uhc_no_duplicate_crafts` and `brainage_minigames:combat_1_8`.
- **Chat lines** to match participants (`N minutes` is `1 minute` for one):
  - `PvP is enabled in N minutes.` at the start of a UHC with a grace period, and `PvP is now enabled!` when PvP starts (also at the start without one).
  - `The border starts shrinking in N minutes; it reaches W blocks across at MM:00.` (Hypixel-style border) or `The border shrinks instantly to W blocks across at MM:00.` per shrink (Badlion-style).
  - `The nether closes in N minutes.`, or `The nether is disabled in this match.`; one minute before closing `The nether closes in 1 minute. Anyone still in it will be moved to the surface.`, then `The nether has closed; everyone still in it was moved to the surface.`
  - `Your opponent's loot chest is at X, Y, Z.` to the survivor of an anti-janitor duel.
  - Duel invitations contain a click event running `/duel accept <challenger>` whose hover text is `Players: ` followed by the invited players' names, comma-separated.
- **Sidebar lines**: `Shrink in: M:SS` counts down to the next border shrink.
- **Dimension ids**: `brainage_minigames:uhc` and `uhc_nether` (UHC), `meetup` and `meetup_nether` (Meetup), `final_uhc` and `final_uhc_nether` (FinalUHC); `brainage_minigames:minigames` holds duel arenas, maps and every UHC deathmatch arena.

## Building and verification

```shell
flock /tmp/brainage-minigames-gametest.lock ./gradlew --no-daemon build runAllGameTests
```

With FabricModdingConventions 2.4.20, `runAllGameTests` runs all five GameTest tasks
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
