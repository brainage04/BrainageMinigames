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
| Meetup | `meetup` | The end of a UHC: a random late-game kit (see [Meetup and FinalUHC](#meetup-and-finaluhc)), PvP from the start, no natural regeneration, items drop on elimination, and a border around a patch of generated terrain that starts at 100 blocks across and closes in by 25 blocks every minute from 2:00 until it is 10 across. Last team standing; 15-minute limit. |
| FinalUHC | `final_uhc` | Minemen's Final UHC duel: identical late-game gear, building, lava and water allowed, no natural regeneration, on generated terrain inside a 100-block border. 15-minute limit. |
| Spleef | `spleef` | Dig out the snow floors under your opponents with an instant-breaking shovel; every block dug gives snowballs that break the floor where they land and knock players back. Nobody takes damage; falling below the lowest floor eliminates. See [Spleef and Bow Spleef](#spleef-and-bow-spleef). |
| Bow Spleef | `bow_spleef` | Shoot the TNT floor out from under your opponents with a flame bow; arrows remove the TNT they hit without exploding it and never hurt players. Double jumps, triple shots and repulsors; falling into the void eliminates. |
| Bridge | `bridge` | Hypixel's The Bridge: jump into the other team's goal to score; every goal rebuilds the map and puts everyone back in their cages. Deaths only send you back to base. First to 5 goals; 15-minute limit. See [Bridge and Battle Rush](#bridge-and-battle-rush). |
| Battle Rush | `battle_rush` | Minemen's Battle Rush: Bridge with only wool and shears and nothing linking the islands, so you rush across with wool and knock opponents off with your fists. First to 3 goals; 10-minute limit. |
| Quake | `quake` | Hypixel's Quakecraft: a railgun kills with one instant beam that walls stop, a feather dashes; killed players respawn at once away from their opponents. First to 25 kills (100 for teams); no melee or fall damage. |
| Pearl Fight | `pearl_fight` | Minemen's Pearl Fight: knockback stick, ender pearls, wool and shears on floating platforms; nobody takes damage, knocking an opponent into the void scores and starts a new round. First to 3 points. |
| Parkour | `parkour` | Hypixel's Parkour Duels: everyone runs the same course from one start line through every checkpoint in order; the first to the finish wins. Falls send you back to your last checkpoint, nobody can hurt or push anyone, and a boost feather throws you forward on a cooldown. See [Parkour and Ice Boat Racing](#parkour-and-ice-boat-racing). |
| Ice Boat Racing | `ice_boat_racing` | Every racer drives their own boat around an ice track through every checkpoint gate in order; the first to finish 3 laps wins. Leaving your boat gets you a new one at your last checkpoint. |

Every game supports every team layout. Duels run in their own barrier-walled arena in the void `brainage_minigames:minigames` dimension, so any number of duels can run at once. Only one UHC can run at a time, because its world border belongs to the whole UHC dimension; that dimension and its nether are regenerated the next time the server stops or starts. A UHC tries up to 16 random regions and takes the one with the most land inside its starting border (ocean and river count as water), favouring dry ground at the centre; a region at least 85% land is taken at once. The lobby, original team starts and nether-close returns use the nearest solid, dry ground within 48 blocks, falling back to the water surface only if none exists. Instant Badlion teleports instead keep the exact five-block horizontal inset. Lobby players are brought back if they wander more than 16 blocks away.

### UHC border modes, deathmatch and daylight

All sizes are **whole widths**, not distances from the centre. These world-persisted gamerules use vanilla-client-compatible boolean/integer types and affect only UHC gameplay:

```mcfunction
/gamerule brainage_minigames:uhc_border_style 0
/gamerule brainage_minigames:uhc_deathmatch true
/gamerule brainage_minigames:uhc_always_day true
/gamerule brainage_minigames:uhc_double_health true
```

- **Border style `0` (default, Hypixel):** 1000 wide until 20:00, then a continuous shrink to 100 at 35:00. That is **1 block/second across the whole width, 0.5 per side**. Changing the first/final times or starting/final widths changes the rate accordingly.
- **Border style `1` (Badlion):** instant widths of 750 at 20:00, 500 at 25:00, 250 at 30:00 and 100 at 35:00. Only outsiders move: each coordinate is clamped to the nearest point five blocks inside the new square, then placed on its surface. For a border centred on 0,0, `(450,450)` becomes `(370,370)` at the 750-wide shrink; players already inside, including underground players, stay put. The nether border stays scaled 1/8; an outside nether player returns at the equivalent surface point.
- **Deathmatch (default on):** all survivors keep their health and inventory and teleport into an original circular stone-and-grass arena, with 24 rim spawn rooms behind partially open iron-bar gates. Teams are spread around the rooms; with more than 24 teams, rooms are shared evenly. Spectators come along. Movement, damage and building are frozen for a 10-second action-bar countdown. Eight middle chests roll `brainage_minigames:uhc/deathmatch`: enchanted Sharpness/Protection/Power books, arrows, golden apples, flint, feathers, sticks, and rare diamond swords/armour. An anvil, crafting table and enchanting table sit in the middle. At 45:00 the 113-wide arena border shrinks to half width in 60 seconds. At 50:00 surviving teams draw **regardless of kill counts**. Either the `uhc_deathmatch` gamerule or `deathmatch_enabled 0` disables the arena transition; the ordinary configured match time limit still applies.
- **Always-day (default on):** both UHC dimensions use `brainage_minigames:uhc`, a dedicated world clock, and dedicated dimension types/timelines. Only that clock pauses at 6000 (noon). The vanilla Overworld clock and the vanilla Nether/End remain untouched. Turning the rule off resumes the independent UHC clock from noon; it does not reconnect it to vanilla time.
- **Double-health (default on):** participants start with 40 maximum health (20 hearts), filled at match start, as in Hypixel UHC. Its transient match-only bonus is removed on leave, disconnect or end before the saved player snapshot is restored; it is never written into a snapshot. The rule is independent of border style and captured at start, so changing it mid-match does not resize existing participants. Golden apples and golden-head healing amounts are not doubled. Client health scores report actual health up to 40, rounded up, rather than capping it at 20. Disable `uhc_double_health` for vanilla/Badlion's 20-health behaviour; selecting Badlion borders does not implicitly toggle health.
- Border style and deathmatch gamerule choices are captured when a match opens; the daylight toggle applies immediately. Optional behaviour is never forced on a match which disables it.

The deathmatch template is generated by `python3 tools/maps/uhc_deathmatch.py` into `structure/maps/uhc_deathmatch/colosseum.nbt`. It is pasted for the match in the UHC dimension and cleared when the match closes; the normal UHC regeneration lifecycle is unchanged. The starting 10-minute Fire Resistance hides its particles while keeping its HUD icon; other effects retain their normal particle behaviour.

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

The obsolete `shrink_duration_minutes` setting is removed: continuous duration is exactly the gap between first and final shrink times. Badlion widths/times must be strictly decreasing/increasing; the deathmatch shrink must finish before its deadline, and a nonzero ordinary limit must allow the complete enabled deathmatch.

**Research and adaptations:** [Badlion's official UHC 3.0 patch notes](https://www.badlion.net/forum/thread/47369) describe edge teleports on larger shrinks, random scattering on the 500/100 shrinks, and closing the nether at 500. [Its UHC 6.0 announcement](https://www.badlion.net/forum/thread/187558) documents clock-driven scheduling and a changed first-shrink time. This mod deliberately uses the owner's shorter schedule, five-block nearest-edge teleports at **every** instant shrink instead of the historical final random scatter, and a configurable nether close defaulting to the first shrink. Exact historical warning intervals could not be established from those primary notes or the reviewed [2016 Danteh Badlion footage](https://www.youtube.com/watch?v=z9IJF-AKVlk); the mod provides advance warnings at 5/1 minutes, 30/10 seconds and each of the final five seconds rather than presenting an unverified cadence as historical fact.

[Hypixel's official 2016 update](https://hypixel.net/threads/uhc-solo-mode-and-balancing-update.741385/) documents multiple deathmatch arenas and barrier anti-exploit boundaries. The [UHC wiki](https://hypixel.fandom.com/wiki/UHC_Champions) describes individual starting areas and a rush to central resource chests, but its 15-minute deathmatch and kill-count tiebreak differ from this owner's requested 10-minute draw. [Scotteh's “THE PERFECT HYPIXEL UHC” (2020)](https://www.youtube.com/watch?v=YYy9HxmW_C8&t=705s), especially 11:50–12:00, shows the frozen arrival/grace period, open rim entrance and stone/grass arena with a central enchanting area. The generated arena is an original interpretation, not a copied Hypixel map.

### The UHC nether

Nether portals lit in the UHC dimension lead to `brainage_minigames:uhc_nether`, a vanilla-generated nether, and portals there lead back; exits are found or built as vanilla does, at an eighth of the coordinates (and eight times them on the way back). Portals in every other dimension behave as in vanilla. The UHC nether is regenerated with the UHC dimension.

Players in a UHC's nether are still in the match: they stay alive and keep their health in the tab list, dying there eliminates them, and leaving, the match ending or being stopped returns them to wherever they joined from. The match's border applies there scaled by 1/8 about the centre divided by 8 (a 1000-block border is 125 blocks across in the nether), shrinks with it and hurts players outside it the same way. A player in another match on the UHC dimension (Meetup, FinalUHC) cannot use portals.

At `nether_close_minutes` (default 20:00; `0` disables the nether) portals stop leading into the nether and everyone still there returns to the surface at matching coordinates, pulled inside the border and its target size. Entering deathmatch also closes the nether and moves anyone still there directly into the arena.

UHC chat announces the selected border schedule, PvP grace, nether close and deathmatch duration at the start. Before each relevant border event and the deathmatch teleport it gives advance warnings; instant-shrink warnings name the new width and explain the surface teleport. The nether gives a one-minute warning and a closure announcement. Deathmatch has its ten-second frozen countdown and a one-minute warning before its half-width shrink. The sidebar shows the current border and next event, nether status and survivors; during deathmatch it shows its own remaining duration and countdown/shrink timer.

Spectators follow players into the nether: `/minigames watch <match>` works for a client that is already spectating and while the match runs, `/spectate <player>` (and the spectator menu's teleport) reach a player in either dimension, and a spectator watching a player who goes through a portal, or is brought back when the nether closes, is taken along and keeps watching them once that player has reached their client (after at most five seconds).

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
- **New chunks only:** changing generation rules never edits already generated chunks. Set them before opening a match/loading its region; subsequent fresh chunks use the current values. The existing UHC dimension/nether regeneration lifecycle described above is unchanged, and the gamerules persist when those dimensions are regenerated.

### Meetup and FinalUHC

Both play on generated terrain in the UHC dimension, like UHC, but each match gets its own far-away region with a border of its own instead of the dimension's world border. That border is sent only to the match's members, who see it and are stopped by it as usual; anyone more than a block outside it is hurt by 0.5 per further block (at least 1) as by a vanilla border. Placing and breaking blocks is allowed only inside it. So any number of Meetup and FinalUHC matches can run at once, but not at the same time as a UHC, whose world border covers the whole dimension (each refuses to open while the other kind is running). The region is the first of up to eight random ones whose centre and four points around it are dry land (otherwise the driest), and the lobby and every spawn are on the nearest solid, dry ground inside the border, 20 blocks in from it. The dimension is regenerated at the next server start, as after a UHC.

FinalUHC is Minemen's Final UHC, compared against its public match inventories (for example [this match](http://minemen.club/match/d7f6ddd6-29f5-3d9c-aa3a-4583e4d1be0c) and several of Rwcist's): every player finishes with full diamond armour, a diamond sword, axe and pickaxe, 16 golden apples, 64 steak, two stacks each of planks and cobblestone, six buckets between water, lava and empty, flint and steel and a fishing rod. The kit (`kits/final_uhc`) is exactly that, with 3 water and 3 lava buckets. The match pages do not show enchantments, so the levels are assumed: Protection II armour, Sharpness III sword, Efficiency III axe and pickaxe. Compared with BuildUHC, FinalUHC has no bow, arrows, or random enchantment levels, twice the blocks, three of each bucket instead of one, flint and steel, and 16 golden apples every time; and it is fought on hills, trees and caves inside a border rather than on BuildUHC's flat grass floor in a barrier box.

Meetup follows the usual UHC Meetup plugins: every player's kit (`kits/meetup`) is rolled separately. It picks one of three tiers from `brainage_minigames:meetup/`, each trading armour for damage and healing, with random slots for its diamond pieces:

| Tier | Armour | Sword | Bow | Golden apples | Golden heads |
| --- | --- | --- | --- | --- | --- |
| `diamond` | 3 diamond + 1 iron piece, Protection I | Diamond, Sharpness I | Power I | 4 | 2 |
| `mixed` | 2 diamond + 2 iron pieces, Protection II | Diamond, Sharpness I | Power II | 5 | 2 |
| `iron` | 1 diamond + 3 iron pieces, Protection II | Diamond, Sharpness II | Power III | 6 | 3 |

Everyone also gets a fishing rod, 32 arrows, a diamond axe and pickaxe, 64 steak, 64 cobblestone or oak planks, two water buckets, two lava buckets and flint and steel. A golden head is a golden apple named Golden Head that gives Regeneration II for 10 seconds (twice an apple's healing) and Absorption for two minutes. The plugins' enchanting table, anvil and experience bottles are left out, as matches are too short to use them.

## Playing

```text
/minigames list
/minigames join [match] [team]
/minigames watch <match>
/minigames leave
/minigames status <match>
```

`join` without a match number joins the only open lobby. A team number requests a team; everyone else is assigned randomly. Joining saves your position, dimension, inventory, game mode, effects, health, hunger, experience and scoreboard team, and all of it is restored when you leave or the match ends. Rewards from the `brainage_minigames:rewards/default` loot table (empty by default; override it with a datapack) are added after restoration, and every win increments the `brainage_games_won` scoreboard objective.

Leaving during a match forfeits. Disconnecting during a match eliminates you, and your state is restored when you reconnect. Dying never kills you: you become a spectator until the match ends, and are then switched back to your saved game mode.

While you are in or watching a match you see its own sidebar, sent only to you: the game and layout, the match number, the lobby size, countdown, elapsed time and time limit or result, your team, and, in team matches, each team with its score and how many of it are alive. Players are not listed there (the tab list does that), except in free-for-all games that score each player (kills, points, race progress), where the sidebar shows those standings. Boxing adds each team's hits and the target, Combo the hit delay, and UHC the time until PvP, the border size, the next shrink, deathmatch and nether-close countdowns, and the players alive; Meetup the border size, the time until the next shrink (or the size it is shrinking to) and the players alive, and FinalUHC the border size. Your tab list shows every participant's actual health as a number (normally 20 is full, or 40 in default double-health UHC, rounded up), cleared once they are eliminated. Both refresh twice a second, never change the server scoreboard, and when you leave the server's own sidebar and tab list objectives (such as `brainage_games_won`, if displayed) come back.

Chat, tab-list and sidebar names use only the nine bright team colours (red, blue, green, yellow, aqua, light purple, gold, white and gray), never black or the dark variants. The palette repeats for larger matches; numbered team names and each FFA player's own name remain distinct, including 50-player free-for-alls. Bridge's unfilled score dots also use readable gray.


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

A layout is `ffa` (everyone for themselves) or two or more team sizes separated by `v`: `1v1`, `2v2`, `1v2`, `2v3v4`, `1v1v1v1`. Fixed layouts start by themselves when every slot is filled; free-for-all matches start with `/minigames start` once at least two players have joined. The optional kit replaces the game's kit, for example `/minigames open classic 2v2 brainage_minigames:kits/instant_crossbow`.

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

## Spleef and Bow Spleef

Spleef follows Hypixel's Spleef Duels. Everyone gets the `brainage_minigames:kits/spleef` kit, an unbreakable Efficiency V diamond shovel that breaks snow instantly, and plays in survival. Only shovel-mineable blocks (snow, clay) inside a map's `floor_<n>` regions can be broken, and only with a shovel in hand; the walls and everything else stay. Dug blocks drop nothing: the digger gets `snowballs_per_block` snowballs (default 2) instead, up to `snowball_cap` held (default 16, Hypixel's cap). A snowball thrown by a player breaks the floor block it lands on and knocks back players it hits; nothing else does damage and nobody can place blocks. Hunger stays full. Falling below the map's `void` marker eliminates, and the last team standing wins. `camp_seconds` (default 0, off) breaks the floor block under a player who stands on it that long.

Bow Spleef follows Hypixel's Bow Spleef Duels (itself from the TNT Games mode): an arena of TNT floors, a round one about 41 blocks across with walls seven blocks high, the void eight blocks below and an invisible ceiling fourteen above. Players are in adventure mode with the `brainage_minigames:kits/bow_spleef` kit (an unbreakable Infinity and Flame bow and one arrow). An arrow landing on TNT inside a `floor_<n>` region removes that block without lighting it and is used up; arrows never hurt players and nothing else does damage. Each player also gets the TNT Games perks, as hotbar items whose stack size is the uses left:

| Perk | Item | Use | Setting (default) |
| --- | --- | --- | --- |
| Double jump | Feather | Double-tap jump in mid-air, or use the feather: flings you the way you look, upwards when you look up. Re-armed half a second after each use. | `double_jumps` (5) |
| Triple shot | Blaze rod | Use it: three flaming arrows side by side. Hypixel fires it with a left click, which the server cannot see, so it has its own item here. | `triple_shots` (5) |
| Repulsor | Magma cream | Sneak, or use the item: flings opponents within 4.5 blocks away, downwards if they are below you. | `repulsors` (5) |

Both games work with free-for-all and every team layout up to the map's spawns (eight on every map); a random map with room for the layout is picked.

| Game | Map | Floors | Notes |
| --- | --- | --- | --- |
| Spleef | `glacier` | 3 | Round packed-ice tower, 25-block snow floors six blocks apart (the middle one ringed with clay), sea lanterns and spruce pillars. |
| Spleef | `lantern_pit` | 2 | Square deepslate pit with 21-block snow floors seven blocks apart, lit by froglights. |
| Bow Spleef | `ember_court` | 1 | Round 41-block TNT floor inside blackstone and nether brick walls with shroomlight. |
| Bow Spleef | `twin_decks` | 2 | A 27-block TNT deck eight blocks above a 39-block one, ringed by prismarine and sea lanterns. |

The maps are generated by `python3 tools/maps/spleef.py` and `python3 tools/maps/bow_spleef.py` into `data/brainage_minigames/structure/maps/<game>/`. A floor region may cover the walls around its floor, since only snow-like blocks (Spleef) or TNT (Bow Spleef) in it can be removed. The Bow Spleef maps are larger than the 48-block limit of an in-game structure block, so edit them through the script (or in parts).

## Bridge and Battle Rush

Bridge follows Hypixel's The Bridge (and Minemen's Bridge, whose kit is the same). Each team has an island with a goal, a hole five blocks across with a crying-obsidian floor, and a glass cage above its spawn. Jumping into another team's goal scores for your team: everyone sees who scored and the score, the map is rebuilt as it was (placed blocks, arrows and dropped items disappear), every player is reset with a full kit into their team's cage, and the cages open after `cage_seconds` (default 5) with a countdown on the action bar. Players cannot move, fight, build or be hurt while caged. The countdown before the first round is the match's `countdown_seconds`, also in the cages. Jumping into your own goal scores nothing and sends you back to your spawn.

Dying, including falling below the map's void height or being knocked off, never eliminates: you respawn at once at your team's spawn with your kit refilled, and whoever last hit you gets the kill. You can only break blocks placed during the current round, and place blocks only inside the map's `build` regions (a few blocks above and below the bridge, stopping short of the goals) and never in a goal. Hunger does not drop. The first team to `goals_to_win` goals wins (default 5); when the time limit runs out the team with the most goals wins, or the leaders draw.

The `brainage_minigames:kits/bridge` kit is Hypixel's: iron sword, bow, one arrow, Efficiency II diamond pickaxe, two stacks of clay, eight golden apples and a leather chestplate, leggings and boots. The clay (and any terracotta, wool, stained glass or concrete in whatever kit the match uses) is turned into the team's colour and leather armour is dyed to match. The arrow comes back 3.5 seconds after you run out, and a golden apple heals you fully on top of its absorption hearts.

Battle Rush follows Minemen's Battle Rush, whose match pages show only 64 wool and shears in every player's inventory: the `brainage_minigames:kits/battle_rush` kit is exactly that, so fights are fist knockback duels. Its islands are smaller and closer and nothing links them, so each round starts with both players rushing across with wool. Everything else works as in Bridge; the first team to 3 goals wins, with a 10-minute limit.

The sidebar shows each team's goals as dots out of the target and its kills, the goals needed, the map and the round. Both games work with every team layout up to the map's goals; a map with exactly as many goals as the layout has teams is preferred, and free-for-all uses the map with the most goals.

| Game | Map | Teams | Notes |
| --- | --- | --- | --- |
| Bridge | `grove` | 2 | Grassy oak islands 30 blocks apart, joined by a one-block andesite bridge with a small lantern-lit resting platform in the middle. |
| Bridge | `basalt` | 2 | Crimson nylium and blackstone islands 34 blocks apart with shroomlight basalt pillars; the blackstone-brick bridge crosses two basalt stepping pillars. |
| Bridge | `compass` | 4 | Four birch islands around a stone-brick plaza, each 16 blocks out along its own bridge; for 1v1v1v1 up to 4v4v4v4. |
| Battle Rush | `lilypond` | 2 | Mossy islands 20 blocks apart, each with a lily-pad pond; three-block goals. |
| Battle Rush | `driftwood` | 2 | Sandy islands 24 blocks apart with a lone driftwood post below the gap. |

Maps mark `spawn <team>` inside the cage, `region cage_<team>` around the cage (cleared when a round starts), `region goal_<team>` over the goal hole, `region build` where blocks may be placed and `void`. They are generated by `python3 tools/maps/bridge.py` and `python3 tools/maps/battle_rush.py` into `data/brainage_minigames/structure/maps/<game>/`; the Bridge maps are larger than the 48-block limit of an in-game structure block, so edit them through the scripts (or in parts).

## Quake and Pearl Fight

Quake follows Hypixel's Quakecraft. Everyone plays in adventure mode with the `brainage_minigames:kits/quake` kit: a Railgun (a wooden hoe; any hoe works) and a Dash feather, and has Speed II (`speed_level`) and full hunger. Using the railgun fires an instant beam up to 100 blocks that stops at the first solid block but passes through players, so one shot can kill several (announced as a DOUBLE or TRIPLE KILL); every opponent it touches dies at once and the shooter's team scores a kill. The railgun then reloads for `reload_ticks` (default 24, 1.2 seconds), shown as the item cooldown. Using the feather dashes you forward and slightly up, recharging after `dash_cooldown_ticks` (default 40; Hypixel dashes with a left click on the railgun, which the server cannot see, so it has its own item here; `dash` 0 turns it off). Nothing else hurts: no melee, no fall damage. Killed players respawn at once at a random `respawn_<n>` point from the half of the map's points farthest from their opponents. Kill streaks of 5, 10, 15, … and shut-downs are announced as on Hypixel. A player (or team) wins on reaching `kills_to_win` kills (default 25, Hypixel Solo) when every team is one player, or `team_kills_to_win` (default 100, Hypixel Teams) otherwise; at the time limit (10 minutes) the most kills wins. Free-for-all and every team layout up to the map's spawns work; teammates' beams pass through each other.

Pearl Fight follows Minemen Club's Pearl Fight. Minemen publishes no rules for it, so they were reconstructed from its match pages, which show both final inventories: a stick, ender pearls (8 was the most left over), 16 wool and shears in every one. The `brainage_minigames:kits/pearl_fight` kit is a Knockback I stick, 8 ender pearls, 16 wool (turned into the team's colour) and shears; players are in survival over floating platforms. Hits only knock back (Resistance V; pearls and falls do no damage either), wool may be placed only inside the map's `build` region and only placed blocks can be broken. Falling below the map's void scores a point for whoever last hit you within 10 seconds, or for the other team when nobody did. In a two-team match each point starts a new round: the map is rebuilt, everyone returns to their spawn with a fresh kit and is frozen for `round_freeze_ticks` (default 60). With more teams only the fallen player respawns, and a fall nobody caused scores nothing. The first team to `points_to_win` (default 3) wins; the round-based format, the target and the Knockback level are assumptions, as the match pages show none of them.

Both sidebars show each team's score against the target.

| Game | Map | Players | Notes |
| --- | --- | --- | --- |
| Quake | `foundry` | 12 | A 65-block walled hall: a two-storey brick tower with a drop hole and a ladder to a lookout, bridges east and west to high balconies, copper corner balconies up wall stairs, and pillars, low walls and crates on the ground; 32 respawn points on four levels. |
| Quake | `cloister` | 8 | A 37-block courtyard for duels: a garden with a fountain and hedges inside a pillared cloister whose roof is a walkway reached by two stairways; 24 respawn points. |
| Pearl Fight | `skyreach` | 4 | Four quartz home platforms 17 blocks around a grassy central island, with end-stone stepping stones and amethyst perches between them. |
| Pearl Fight | `twin_peaks` | 2 | Two long andesite platforms 28 blocks apart joined by a broken slab bridge, with a higher island to either side. |

Quake maps mark `spawn <n>` (one per player of a free-for-all; teams start at the first ones) and `point respawn_<n>`; Pearl Fight maps mark `spawn <team>` and `region build`. They are generated by `python3 tools/maps/quake.py` and `python3 tools/maps/pearl_fight.py` into `data/brainage_minigames/structure/maps/<game>/`; the Quake maps are larger than the 48-block limit of an in-game structure block, so edit them through the script (or in parts).

## Parkour and Ice Boat Racing

Both are races on a map: everyone starts together, must pass the map's `region checkpoint_1`, `checkpoint_2`, … in that order and then its `region finish`; a gate passed out of order counts for nothing, so skipping part of the course never helps. The first player to finish the last lap wins for their team. Nobody is eliminated and nobody takes damage (no PvP, no fall damage), and racers pass through each other (the match teams' collision rule is `never`). A racer who falls five blocks below their last checkpoint (Parkour only), touches a `region fail` or drops into the void is put back at their last checkpoint, or at the start before the first one. The action bar shows each racer's lap, checkpoint and time; the sidebar shows the checkpoint count, the laps, and each team's furthest racer. When `time_limit_minutes` (default 10) runs out, the team that got furthest wins, and teams level on gates draw. Both work with free-for-all and every layout up to the map's eight start slots.

Parkour follows Hypixel's Parkour Duels (an eight-player race through checkpointed sections, with a boost feather). Runners play in adventure mode with the `brainage_minigames:kits/parkour` kit: a Boost feather, which throws them forwards and upwards and then waits `boost_cooldown_seconds` (default 15; the cooldown shows on the item), and a Back to checkpoint pressure plate that returns them at once. Parkour has one lap.

Ice Boat Racing puts every racer in an oak boat on the map's `point boat_<n>` grid slot (in team order) when the race starts. Boats cannot be broken; a racer who is out of their boat for any reason gets a new one at their last checkpoint (or the finish line, after a lap), and the old one is removed. `laps` sets the laps (default 3). Boats are removed when their racer leaves and when the race ends, and racers are taken out of them before being restored.

| Game | Map | Notes |
| --- | --- | --- |
| Parkour | `canopy` | A jungle course heading east over a pool: a log warm-up, a ladder wall and a jump onto a hanging ladder, fence posts, a leaf staircase heading north and a tall ladder to a gold finish; 4 checkpoints. |
| Parkour | `spire` | A square spiral climbing one and a half times around a quartz tower: quartz, fence, top-slab and purpur jumps with three ladders, a checkpoint at every corner and the finish above the start; 6 checkpoints. |
| Ice Boat Racing | `frostbite_oval` | A 266-block oval, 9 wide: two 70-block straights (blue ice down the back one) joined by half circles of radius 20; 3 checkpoints. |
| Ice Boat Racing | `glacier_circuit` | A technical 305-block circuit, 7 wide: a blue-ice main straight, a fast right-hand sweeper, a hairpin, a kink, four S bends and a long left-hander home; 5 checkpoints. |

Checkpoint gates may carry a `point` of the same name (`point checkpoint_<n> <yaw>`, `point finish <yaw>`) setting where and facing which way racers are put back; without one they stand at the bottom centre of the gate facing the next gate. Parkour courses never descend more than a block below the previous checkpoint, since a five-block drop counts as a fall. Tracks are packed ice with blue ice only on long straights, kerbs a block high under a glass rail (a boat cannot climb them and drivers can see over them) and gates spanning the whole track four blocks high; the eight-slot grid of `point boat_<n>` (with a `spawn <n>` under each) sits behind the finish line. The maps are generated by `python3 tools/maps/parkour.py` and `python3 tools/maps/ice_boat_racing.py` into `data/brainage_minigames/structure/maps/<game>/`; they are larger than the 48-block limit of an in-game structure block, so edit them through the scripts (or in parts).

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

## Building and verification

```shell
flock /tmp/brainage-minigames-gametest.lock ./gradlew --no-daemon build runAllGameTests
```

`runAllGameTests` runs the Fabric production server and client GameTests and the NeoForge
server GameTests. For a single loader, use `:fabric:runProductionServerGameTest`,
`:fabric:runProductionClientGameTest`, or `:neoforge:runGameTest`; development clients
use `:fabric:runClient` and `:neoforge:runClient`. Record the Fabric showcase with
`:fabric:recordClientGameTest`.

The loader-neutral NeoForge test bodies live in `common/src/gametest/java`; NeoForge
registers them and supplies its dimension setup. The resource drop and generation tests
are shared by both loaders. See [release instructions](docs/RELEASE.md) and
[Modrinth publishing](docs/MODRINTH.md) for distribution.

## License

Brainage Minigames is available under the [MIT License](LICENSE).
