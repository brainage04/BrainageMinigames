# Brainage Minigames

A server-side mod for Minecraft 26.2 that runs minigames on an ordinary server: UHC and a set of kit duels, with any team layout and any number of matches at once. Clients do not need to install the mod.

## Requirements

- Minecraft 26.2
- Either Fabric Loader 0.19.3 or newer with Fabric API, or NeoForge 26.2.0.23-beta or newer
  - Install the Fabric API only with the Fabric release.
- Java 25 or newer
- The `pvp` game rule must be `true`; vanilla blocks player hits before the mod sees them otherwise.

Install exactly one loader JAR and never both. A root `./gradlew build` emits both loader-specific artifacts.

## Games

| Game | Id | Rules |
| --- | --- | --- |
| UHC | `uhc` | Survival in a fresh region of the dedicated UHC dimension. Starter kit, 10-minute grace period without player damage, no natural regeneration, items drop on elimination, and a world border that shrinks from 1000 to 100 blocks at 30 minutes and to 20 blocks at 40 minutes. Everyone is moved to the surface at the final shrink and again when 10 minutes remain. Nether portals lead to the match's own nether until the first shrink (see [The UHC nether](#the-uhc-nether)). One-hour limit. |
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

Every game supports every team layout. Duels run in their own barrier-walled arena in the void `brainage_minigames:minigames` dimension, so any number of duels can run at once. Only one UHC can run at a time, because the world border belongs to the whole UHC dimension; that dimension and the UHC nether are regenerated the next time the server stops or starts. UHC puts the lobby, every team's start position and everyone moved to the surface on the nearest solid, dry ground within 48 blocks, never on water or lava; regions whose centre is ocean are skipped (after eight tries the last one is used and anyone with no dry ground nearby lands on the water's surface). Lobby players are brought back if they wander more than 16 blocks away.

### The UHC nether

Nether portals lit in the UHC dimension lead to `brainage_minigames:uhc_nether`, a vanilla-generated nether, and portals there lead back; exits are found or built as vanilla does, at an eighth of the coordinates (and eight times them on the way back). Portals in every other dimension behave as in vanilla. The UHC nether is regenerated with the UHC dimension.

Players in a UHC's nether are still in the match: they stay alive and on the sidebar, dying there eliminates them, and leaving, the match ending or being stopped returns them to wherever they joined from. The match's border applies there scaled by 1/8 about the centre divided by 8 (a 1000-block border is 125 blocks across in the nether), shrinks with it and hurts players outside it the same way. A player in another match on the UHC dimension (Meetup, FinalUHC) cannot use portals.

At `nether_close_minutes` (by default the first shrink, 30 minutes; `0` disables the nether) portals stop leading into the nether and everyone still in it is moved to dry ground at the matching overworld position, pulled inside the border and the size it is shrinking to. The final shrink and the last-ten-minutes move bring back anyone still there too. UHC chat announces the schedule:

| When | Message |
| --- | --- |
| Start | `PvP is enabled in 10 minutes.`, `The border starts shrinking in 30 minutes.`, then `The nether closes in 30 minutes.` or `The nether is disabled in this match.` |
| 5 and 1 minutes before the first shrink | `The border starts shrinking in 5 minutes.` / `... in 1 minute.` |
| First shrink | `The border is shrinking to 100 blocks across.` |
| 1 minute before the nether closes | `The nether closes in 1 minute. Anyone still in it will be moved to the surface.` |
| Nether closes | `The nether has closed; everyone still in it was moved to the surface.` |
| 5 and 1 minutes before the final shrink | `The final shrink starts in 5 minutes; everyone will be moved to the surface.` / `... in 1 minute; ...` |
| Final shrink | `Final phase: everyone is on the surface and the border is shrinking to 20 blocks across!` |

Spectators follow players into the nether: `/minigames watch <match>` works for a client that is already spectating and while the match runs, `/spectate <player>` (and the spectator menu's teleport) reach a player in either dimension, and a spectator watching a player who goes through a portal, or is brought back when the nether closes, is taken along and keeps watching them once that player has reached their client (after at most five seconds).

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

While you are in or watching a match you see its own sidebar, sent only to you: the game and layout, the match number, the lobby size, countdown, elapsed time and time limit or result, your team, and every team's players with their health in hearts (or crossed out once eliminated, or marked offline). Boxing adds each team's hits and the target, Combo the hit delay, and UHC the time until PvP, the border size, the time until the next shrink, the time until the nether closes (or that it has) and the players alive; Meetup the border size, the time until the next shrink (or the size it is shrinking to) and the players alive, and FinalUHC the border size. It refreshes twice a second, never changes the server scoreboard, and when you leave the server's own sidebar (such as `brainage_games_won`, if displayed) comes back.

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

Settings are stored per world and apply to matches opened afterwards. Every game has `countdown_seconds`, `time_limit_minutes` (the remaining teams draw when it runs out, unless one has more points; `0` disables it) and `natural_regeneration` (`1` or `0`; with `0`, a full hunger bar no longer heals players in the match, as if the `natural_health_regeneration` game rule were off for them alone, while hunger still drains and starves as usual and food still restores it). UHC adds `grace_period_minutes`, `border_start_size`, `first_shrink_minutes`, `first_shrink_size`, `final_shrink_minutes`, `final_shrink_size`, `shrink_duration_minutes` and `nether_close_minutes` (no later than `final_shrink_minutes`; `0` disables the nether); Boxing adds `hits_to_win`; Combo adds `hit_delay_ticks` (ticks between hits a player can take, 1 to 10; vanilla is 10). Meetup adds `border_start_size` (100), `first_shrink_seconds` (120), `shrink_interval_seconds` (60), `shrink_step` (25 blocks off the side length per shrink), `final_size` (10) and `shrink_duration_seconds` (10; `0` moves the border at once); FinalUHC adds `border_size` (100).

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
./gradlew build
./gradlew :fabric:runProductionServerGameTest runNeoForgeGameTests
```

## License

Brainage Minigames is available under the [MIT License](LICENSE).
