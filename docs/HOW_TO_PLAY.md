# How to play

## The hub

- You arrive in the hub, the protected spawn area. Between matches you are in adventure mode there: no breaking, placing or damage.
- `/hub` or `/spawn` takes you back to it from anywhere; in a match, it leaves the match first. After every match you return to the hub with the inventory you had before it.
- Ideas or bugs? `/feedback <message>` sends them to the server team. An hourly reminder has a **[Turn off reminders]** button; `/feedback reminders on` turns it back on.

## Menus

Everything below also works from chest menus; commands keep working.

- **Game Menu** compass (given in the hub; right-click it) or `/minigames`: **Play a Game**, **Open Matches**, **Duel Builder** and **Settings**.
- **Play a Game**: pick a category and game, a layout (presets, such as **Solo** and **Teams of 2, 3 or 4** for UHC, Meetup and FinalUHC, or **Custom Layout** with any team sizes), a kit (each shows its name and what it gives) and, for map games, a map. In **Match Setup**, click open slots to add bots (when the server has a bot provider), shift-click a slot to change team, then **Open Match**.
- **Open Matches**: click a match to join a team, watch, or leave. Its owner and operators can also start it, add or clear bots, or shift-click **Stop Match**.
- **Duel Builder** (or `/duel`): choose game, layout and kit; left-click slots for players, right-click for bots, then **Send Challenge**. Bot-only duels start at once. Right-click a player with the Game Menu to challenge them.
- In a lobby, right-click **Vote to Start** (lime dye) or **Leave** (red bed). **Settings** toggles hourly feedback reminders.

## Joining a game

- `/minigames list` shows matches and their numbers. Use `/minigames join <match> [team]`, or just `/minigames join` if only one lobby is open.
- `/minigames watch <match>` joins as a spectator. `/minigames leave` restores your saved inventory and state; leaving an active game forfeits.
- Invite friends with `/duel <game> <layout> <player> [player ...]`, e.g. `/duel classic 1v1 Alex`. Click **[Accept]** or **[Deny]** in chat; everyone must accept. Invites expire after 60 seconds; `/duel cancel` cancels yours.

## Starting a game

- Anyone can open a match with `/minigames open <game> <layout> [kit]`, e.g. `/minigames open bridge 2v2`, and then owns it and plays in it: `/minigames start <match>` and `/minigames stop <match>` work on it, and operators can start or stop any match. Add `nojoin` at the end (e.g. `/minigames open bridge 2v2 nojoin`) to only open it for others. You can own one open match at a time (servers can change this), counting duels you started. An optional kit replaces the usual equipment; `/minigames kit list` (operators) names every kit.
- A new match's map or UHC, Meetup or FinalUHC region takes a moment to prepare: if you join straight away, you are moved to its lobby as soon as it is ready.
- `ffa` is everyone for themselves; `1v1` is two solo players; `2v3v4` is teams of two, three and four. Other team counts and sizes work within map capacity.
- **Playing in a team:** teammates start together and cannot hurt each other (weapons, arrows, potions, or the lava and fire they place). While the match runs your chat goes to your team only; start a message with `!` (e.g. `!gg`) or use `/shout <message>` to talk to everyone. The sidebar shows your teammates' health. The last team with anyone alive wins.
- Fixed layouts start when full. `/minigames start <match>` (by its owner or an operator) starts a lobby now with the players waiting, at least two on two teams. Operators: see [README.md](../README.md#settings) for settings.
- **Vote to start:** in a lobby, `/minigames vote` (or the **[Vote to start]** button in chat). Once most players waiting have voted, the match starts.
- **UHC, Meetup and FinalUHC** lobbies start on their own **30 seconds** after the first player starts waiting, or as soon as most players vote. Empty slots are then filled with bots (a free-for-all fills up to 8 players). Without a bot mod the match starts with the players present; it needs at least two.
- **Bots in any game** (needs a bot mod such as SparringBots): while waiting in a lobby, `/minigames bots <match> add <count> [team]` reserves slots for bots on a team or anywhere, `fill` gives every free slot to a bot (which starts the match), `clear` removes them, and `difficulty <easy|normal|hard|mixed>` picks how they play (`mixed` by default). Bots join when the match starts, show as `[BOT] name` in the tab list and sidebar, and leave once eliminated or when the match ends. SparringBots' bots gather and craft in UHC, Meetup and FinalUHC, and only fight in other games. Parkour and Ice Boat Racing have no bots.
- **Duel bots:** `/duel <game> <layout> bots <counts> [player ...]` gives each team that many bots, written like the layout: `/duel classic 1v2 bots 0v2` is you against two bots and starts at once; `/duel bridge 2v2 bots 1v1 Alex` is you and a bot against Alex and a bot.

These are the shipped defaults; servers can change kits and rules. Eliminated players spectate. At a score/race time limit, the highest score or checkpoint progress wins; tied leaders draw. Other elimination games draw between surviving teams at timeout.

Public FFA and three-or-more-team **UHC, Meetup, FinalUHC and SkyWars** protect fights between two teams (or two players in FFA) for 30 seconds after the last damaging hit: teammates can join in, other teams cannot hit anyone in the fight, and death loot is reserved for the other team in a chest until the timer expires. Private `/duel` matches do not use this protection. In **UHC, Meetup and FinalUHC**, disconnecting leaves an attackable zombie; reconnect while it survives to resume.

## UHC (`uhc`)

- Gather resources, craft gear and be the last team alive. Start with **20 hearts**; default Stone Gear is four enchanted tools, one randomly upgraded to iron, with no armour or food. `/minigames uhc kit <kit>` selects your next kit.
- **10-minute PvP grace** and 10 minutes of Fire Resistance; other hazards still matter. No natural regeneration: heal with items. Death eliminates you and leaves loot.
- Always-noon sky; border **1000 blocks across**, shrinking from **20:00–35:00** to 100 across. Stay inside it. The Nether closes at **20:00**, returning players to the surface.
- At **40:00**, survivors keep health and gear and enter deathmatch, frozen for 10 seconds. Contest central loot; its border starts shrinking at **45:00**. Surviving teams draw at **50:00**.
- All profession perks/recipes and Extras are unlocked by default; all kits are tier III and prestiged. Special crafts include Light Apples, Golden Heads and enchanted gear. Click **[Craft]** when you have ingredients, then take the output from the prepared crafting grid.

## BuildUHC (`build_uhc`)

- Eliminate every opposing team; no respawns.
- Start with enchanted diamond gear, bow, rod, shield, golden apples, food, blocks, water, lava and enchanting supplies. Enchantments and apple counts vary between players.
- Build and break inside the arena; **no natural regeneration**. Heal with apples. **10-minute limit**.

## Classic (`classic`)

- Eliminate the other teams.
- Start with iron armour, sword and axe, bow and 32 arrows, rod, shield, golden apples and food.
- No building or digging; natural regeneration is on. **10-minute limit**.

## No Debuff (`no_debuff`)

- Eliminate opponents; manage healing and chase with pearls.
- Start with enchanted diamond gear, **30 Instant Health II splash potions**, 16 pearls, Speed II potions, Fire Resistance and food.
- No building or digging; natural regeneration is on. **10-minute limit**.

## Gapple (`gapple`)

- Outlast and eliminate the other teams.
- Start with Protection IV diamond armour, Sharpness III diamond sword, **64 golden apples**, Strength II and Speed II potions.
- No building or digging; natural regeneration is on. **10-minute limit**.

## Boxing (`boxing`)

- First team to **100 accepted melee hits** wins. Hits knock back but do not damage health.
- Start with a Sharpness I diamond sword and permanent Speed II.
- No building or digging. **10-minute limit**; hits, not kills, are the score.

## Combo (`combo`)

- Eliminate opponents with rapid combos.
- Start with enchanted diamond gear, eight golden apples and food.
- Full-strength swings, with hits every **2 ticks**. No building or digging; natural regeneration is on. **10-minute limit**.

## Bow (`bow`)

- Eliminate opponents with ranged attacks; melee cannot hurt players.
- Start with leather armour, Infinity bow and one arrow, golden apples and food.
- No building or digging; natural regeneration is on. **10-minute limit**.

## Meetup (`meetup`)

- A ready-equipped UHC finish: last team alive wins; deaths eliminate and leave loot.
- Each player rolls enchanted iron/diamond armour, sword, bow, golden apples and Golden Heads. Everyone also gets rod, tools, arrows, blocks, food, water and lava.
- Immediate PvP, **no natural regeneration**, building within the border. It starts **100 blocks across**, loses 25 each minute from **2:00**, and stops at 10; each shrink takes 10 seconds. **15-minute limit**.

## FinalUHC (`final_uhc`)

- Eliminate opposing teams on natural terrain; no respawns.
- Identical Protection II diamond armour, Sharpness III sword, rod, tools, **16 golden apples**, food, two stacks each of planks/cobblestone, three water and three lava buckets, and flint and steel. No bow.
- Immediate PvP, **no natural regeneration**. Build and use buckets within the fixed **100-block-wide border**. **15-minute limit**.

## SkyWars (`skywars`)

- Last team alive wins; death or the void eliminates you.
- Start caged above an island with stone pickaxe, axe and shovel. When cages open, loot island chests for gear and blocks; the middle has stronger loot.
- Build bridges, break blocks and fight inside the map. Chests refill at **3:00 and 5:00**; natural regeneration is on. **9-minute limit**.

## Spleef (`spleef`)

- Remove floors beneath opponents; falling below the lowest floor eliminates. Last team standing wins.
- Start with an unbreakable diamond shovel that digs floors instantly. Each block gives **two snowballs**, up to 16 held.
- Snowballs break floors and knock players back without hurting them. No placing blocks, breaking walls or ordinary combat; hunger stays full. **10-minute limit**.

## Bow Spleef (`bow_spleef`)

- Shoot holes in TNT floors; last team standing wins. The void eliminates you.
- Start with unbreakable Flame/Infinity bow and an arrow. Arrows remove TNT without explosions or player damage; no building or digging.
- **Five uses each**: double-tap jump or use feather for double jump; use blaze rod for triple shot; sneak or use magma cream to repel opponents. **10-minute limit**.

## Bridge (`bridge`)

- Jump into enemy goals; first team to **five goals** wins. Your own goal just returns you to base.
- Start with iron sword, bow, regenerating arrow, diamond pickaxe, two stacks of coloured terracotta, eight golden apples and leather armour. Arrow returns after 3.5 seconds without one; apples heal fully.
- Deaths respawn you with fresh gear. Goals rebuild the map and reset everyone into five-second cages.
- Build only in allowed areas, never goals; break only blocks placed this round. **15-minute limit**, decided by goals.

## Battle Rush (`battle_rush`)

- Bridge across the gap and enter enemy goals; first to **three goals** wins.
- Start with **64 coloured wool and shears**, no weapons or armour. Knock opponents off with fists; islands have no connecting bridge.
- Deaths respawn you with fresh gear; goals reset map and kits into five-second cages. Build only in allowed areas, not goals; break only placed blocks. **10-minute limit**, decided by goals.

## Quake (`quake`)

- First to **25 kills** wins with all-solo teams; otherwise **100 team kills**.
- Start with **Railgun** (wooden hoe), Dash feather and Speed II. Right-click railgun for an instant one-hit-kill beam: walls stop it, players do not. **1.2-second reload**; right-click feather to dash every two seconds.
- Deaths respawn you immediately away from opponents. No melee/fall damage, building or digging. **10-minute limit**, decided by kills.

## Pearl Fight (`pearl_fight`)

- Knock opponents into the void; first team to **three points** wins.
- Start with Knockback I stick, **eight pearls**, 16 coloured wool and shears. Hits only knock back; falls/pearls do not hurt. Place wool only in allowed areas; break only placed blocks.
- Two teams: even accidental falls award opponents a point; non-winning points reset map/kits with a three-second freeze. More teams: only the fallen player respawns; uncredited falls score nothing. **10-minute limit**, decided by points.

## Parkour (`parkour`)

- Pass every checkpoint in order, then finish first to win for your team.
- Right-click your Boost feather for a forward/upward burst (**15-second cooldown**), or the pressure plate to return to your checkpoint.
- Falls return you to your checkpoint; no elimination, damage or pushing between runners. **10-minute limit**, decided by checkpoint progress.

## Ice Boat Racing (`ice_boat_racing`)

- Drive through checkpoint gates in order for **three laps**; first to finish wins for their team.
- Start in your own boat with an empty inventory. Leaving/losing it, entering a reset area or falling into the void returns you in a replacement at your last checkpoint.
- No health damage; boats can still bump. **10-minute limit**, decided by lap/checkpoint progress.
