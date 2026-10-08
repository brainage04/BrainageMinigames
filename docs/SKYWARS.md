# SkyWars catalog

Four games play Hypixel's SkyWars modes: `skywars` (**Insane**), `skywars_mini` (**Mini**), `skywars_mega` (**Mega Doubles**) and `skywars_lucky` (**Lucky Block**: Insane with lucky blocks). This page lists every kit, kit ability, perk, chest table and lucky block outcome with where its numbers come from. **Local** marks a choice this mod makes where no source gives the detail.

## Sources

- **Menu exports**: Hypixel's *Insane Kits* (two pages), *Toggle Insane Perks* (two pages), *Mini Kits*, *Select Mini Perks*, *Mega Kits*, *Select Mega Perks* and *Kits & Perks* menus, exported item by item (name and full tooltip) on a 26.2 client on 2026-10-06. These are the reference for every kit's contents and every perk's description; they win over older sources.
- **Hypixel announcements**: [Angel's Rebirth update](https://hypixel.net/threads/skywars-angels-rebirth-update.5745688/) (2025: Insane perks all active at once, kit and chest-loot changes, perk upgrade changes), [Balancing, QoL, a Grim Reaper and more](https://hypixel.net/threads/skywars-update-balancing-qol-a-grim-reaper-and-more.5003770/) (2022: Chronobreaker, Cryomancer, perk numbers, Insane kit changes), [New Prestiges, QoL changes, Balancing and Bug Fixes, March 2023](https://hypixel.net/threads/skywars-update-new-prestiges-qol-changes-balancing-and-bug-fixes-march-2023.5303364/) (guaranteed island blocks and sword, Time Warp and Echo restrictions), [Twin Pearl balance patch](https://hypixel.net/threads/february-3-twin-pearl-balance-patch.5835829/) (Time Warp Weakness, Fishmonger chance), [Bug fixes, QoL & Balancing](https://hypixel.net/threads/skywars-bug-fixes-qol-balancing-update.5077132/) (Bulldozer in teams).
- **Player guides** (used only where the above say nothing): [Normal and Insane kits guide](https://hypixel.net/threads/guide-skywars-normal-and-insane-kits.2572556/) (mythical abilities, potion effects), [Angel's Descent listed](https://hypixel.net/threads/guide-angels-descent-listed-from-beginning-to-end.3461193/) (upgrade tier counts, Archeologist items), [Insane & Normal chest items list](https://hypixel.net/threads/updated-11-21-tool-insane-normal-ranked-skywars-chest-items-list-wip.919141/) (chest item lists).
- **Mini and Mega sources**: [Angel's Rebirth update](https://hypixel.net/threads/skywars-angels-rebirth-update.5745688/) (Mini SkyWars: four-player lobbies, a smaller map, Ranked kits with their perks built in, Juggernaut global, no hunger, pearls harmless, ores smelted, doubled island-chest blocks, Champion the default kit; Mega Doubles all week; Mega kits and perks maxed by default; six default Mega perks; Mega Marksmanship 2 kills and Blazing Arrows 15%), [Mega Doubles update](https://hypixel.net/threads/skywars-update-mega-doubles-new-kits-perks-more.1686124/) (teams of two, at most 40 players), the archived [Mega SkyWars wiki page](https://web.archive.org/web/20250323014539/https://hypixel.fandom.com/wiki/Mega_SkyWars) (Mega perk list and top-level numbers, Mega mid loot weaker than Insane), and the [Laboratory rotation notice](https://hypixel.net/threads/skywars-new-lab-mode-lab-rotation-and-more.1670000/) (Lucky Blocks was a Laboratory mode).

## Modes

Kits, perks, saved choices and chest tables belong to a mode (`game.skywars.SkyWarsMode`: `insane`, `mini`, `mega`); each game plays one (`game.skywars.SkyWarsGame`). Lucky Block SkyWars plays the Insane kits, perks, chest tables and maps, so its choices are Insane's. Each kit (`SkyWarsKits`) and perk (`SkyWarsPerk`) is data with its mode, and each mode has its loot tables `skywars/<mode>/island` and `skywars/<mode>/mid`.

How perks are chosen differs per mode, as on Hypixel:

- **Insane** toggles: every owned perk is on unless turned off (`/minigames skywars perk <perk> <true|false>`).
- **Mini** has seven perk slots, used like Mega's (six usable, the seventh while `skywars_max_all_perks` is on), on top of each kit's own perk; they start empty and offer the Mega slot perks. Everyone has the global perks Juggernaut and Telekinesis. On Hypixel the Mini menu refuses every slot ("This mode does not allow the use of perk slots!"), so the usable slots and their perk list are **local**. `/minigames skywars mini perk` lists the slots and `/minigames skywars mini perk <slot> <perk|clear>` changes one.
- **Mega** has seven perk slots: a player's perks are the ones in their slots plus the global Juggernaut and Telekinesis. Six slots are usable; the seventh (Hypixel: "Unlocked in Angel's Descent") opens while `skywars_max_all_perks` is on. A player who never changed a slot has Hypixel's six default perks in slots 1 to 6. Putting a perk into a slot moves it from any other slot. `/minigames skywars mega perk` lists the slots, `/minigames skywars mega perk <slot> <perk|clear>` changes one, and the Select Mega Perks menu does the same (left-click a slot to choose, right-click to empty it).

Kit commands are per mode: `/minigames skywars kit <kit>` (Insane and Lucky Block), `/minigames skywars mini kit <kit>`, `/minigames skywars mega kit <kit>`; grants likewise (`/minigames skywars mega grant <players> perk <perk>`).

## Progression

There are no SkyWars coins, Soul Well, opals or kit prestige. `brainage_minigames:skywars_max_all_kits` and `skywars_max_all_perks` (both default `true`) unlock everything in every mode, perks with every upgrade level. With a rule `false` a player owns each mode's default kit (Default; Champion in Mini) and what an operator granted (`/minigames skywars grant <players> kit <kit>` / `perk <perk>`, `revoke` to remove; `skywars mini ...` and `skywars mega ...` for those modes), and perks have their base numbers. Global perks are always owned. Choices and grants are saved per UUID and mode in the world's command storage (`brainage_minigames:skywars_progression`). Kits are given and perks read when the cages open; changes take effect from the next match.

## Insane kits

Contents are the export's, in the order given; armour is worn. Rarity is the export's (Common, Rare, Legendary, Mythical).

| Kit | Rarity | Contents | Local or sourced elsewhere |
| --- | --- | --- | --- |
| Default | Common | Iron pickaxe, axe, shovel, sword, chestplate | |
| Armorer | Common | Iron chestplate, iron leggings, diamond boots, splash Resistance | Resistance I for 10 s (kit guide; the export shows no duration) |
| Armorsmith | Common | Anvil, book (Protection III, Sharpness I, Power III, Feather Falling IV, Infinity I), 64 experience bottles, diamond helmet, book (Power I) | |
| Ecologist | Common | Iron axe (Efficiency II, Sharpness I), 24 oak logs, leather leggings (Protection III) | |
| Healer | Common | 3 splash Instant Health I, 2 splash Regeneration II (16 s), 2 golden apples | |
| Knight | Common | Iron sword (Sharpness II), gold helmet (Protection II, Unbreaking III) | |
| Pro | Common | Iron helmet, iron chestplate, iron sword (Sharpness I), 16 glass, golden apple | |
| Scout | Common | Diamond sword, 2 Speed II potions (60 s), diamond boots | |
| Batguy | Common | Leather helmet (Protection II), leather chestplate and leggings, iron boots (Protection II), 2 splash Blindness I (11 s), 10 bat eggs | |
| Disco | Common | Leather helmet (Projectile Protection IV), chestplate (Protection III, Thorns III), leggings (Protection IV), boots (Feather Falling IV), jukebox, 12 note blocks, a random music disc | Disc from the twelve 1.8 discs (local) |
| Energix | Common | Strength I potion (7 s), leather leggings (Protection II) | |
| Cactus | Common | 16 cactus, 32 sand, 16 sandstone, leather chestplate, leggings and helmet (Thorns V, Unbreaking V) | |
| Frog | Common | Frog's Hat (a player head), leather chestplate, leggings, boots, Frog's Potion, 16 lily pads | Potion: splash Speed II and Jump Boost IV for 40 s (kit guide). The hat has no effect; any Hypixel effect is unconfirmed |
| Grenade | Common | 3 Charged Creeper Eggs, leather chestplate, leggings, boots (Blast Protection IV) | |
| Farmer | Rare | Diamond leggings (Projectile Protection IV), 16 eggs, golden apple | |
| Baseball Player | Rare | Iron helmet (Protection IV), stone sword (Knockback I) | |
| Enchanter | Rare | Enchanting table, 64 experience bottles, 8 bookshelves, gold helmet, chestplate, leggings, boots | |
| Hunter | Rare | Bow (Power II), 16 arrows | |
| Pharaoh | Rare | Gold helmet and boots (Protection IV, Unbreaking V), white leather chestplate and leggings, beacon, 42 emerald blocks, 2 iron blocks, 2 gold blocks | |
| Snowman | Rare | 32 snowballs, 2 snow blocks, diamond shovel (Sharpness III), carved pumpkin, 16 coal blocks, leather helmet (Fire Protection IV), white leather chestplate, leggings, boots | Carved pumpkin instead of "Pumpkin": only it builds a snow golem today |
| Speleologist | Rare | Diamond pickaxe (Efficiency III, Sharpness II, Unbreaking III, Fortune II), 32 stone, diamond helmet | |
| Warlock | Rare | 2 splash Instant Damage I, 2 splash Weakness III (10 s), splash Poison I (10 s), 2 golden apples, leather chestplate (Protection IV), chainmail boots (Protection I) | |
| Engineer | Rare | 64 each of tripwire hooks, pistons, slime balls, redstone, levers, dispensers, gunpowder, sand, gravel, arrows; 8 cobwebs; 4 flint and steel; gold helmet (Protection II) | |
| Pig Rider | Rare | Saddle, pig egg, gold armour (Protection II), carrot on a stick, gold sword (Sharpness II), 16 hay | |
| Sloth | Rare | 5 Sloth Potions, leather armour (Protection I), 16 jungle logs, ghast tear (Sharpness V); permanent Slowness II | Sloth Potion: splash Slowness II for 10 s (local duration; the kit guide says only "Slowness 2") |
| Magician | Rare | Rabbit egg, leather helmet, 2 Magician Potions, The Wand (a stick, Sharpness VII) | Potion: splash Invisibility for 15 s (kit guide) |
| Enderchest | Rare | 3 golden apples; a fourth chest with island loot below the cage | The chest takes the cage floor's place under the player and is refilled with the island chests (local placement) |
| Fisherman | Rare | Fishing rod (Unbreaking X, Luck of the Sea X, Lure X), chainmail helmet (Protection II, Respiration III), chainmail leggings (Protection I) | |
| Princess | Rare | Gold helmet (Protection IV), bow (Power I), 5 arrows | |
| Cannoneer | Legendary | 24 TNT, 10 redstone blocks, diamond boots (Feather Falling IV, Blast Protection IV), water bucket, 4 pressure plates | Stone pressure plates (local) |
| Enderman | Legendary | Corrupted Pearl, iron helmet and boots (Protection II) | |
| Guardian | Legendary | 10 obsidian, 2 splash Resistance I (10 s), 2 skeleton eggs, 3 zombie eggs, chainmail leggings and boots (Protection I) | The spawned mobs are ordinary mobs |
| Archeologist | Legendary | Knight's Helmet (gold, Unbreaking X, Protection II), Legionnaire's Chestplate (iron, Protection I), Red Socks (red leather boots, Protection II), Simple Fishing Rod (Unbreaking X, Luck of the Sea III), Blazing Potion, Potion of Hearts | Materials, Blazing Potion (Fire Resistance and Speed, 30 s) and Potion of Hearts (Instant Health) from the Angel's Descent guide |
| Fallen Angel | Legendary | Diamond hoe (Sharpness IX) | |
| Salmon | Legendary | Raw salmon (Sharpness III, Knockback I), 16 sponges, leather leggings and boots (Protection II) | |
| Slime | Legendary | Diamond boots (Feather Falling X, Protection III), 64 slime blocks | |
| Jester | Legendary | Random sword, 2 splash Regeneration II (10 s), 2 golden apples | Sword: random wood to diamond, 1–3 random Sharpness, Smite, Bane, Knockback or Fire Aspect levels, an eighth of its durability left (local; the guide says only "very low durability and multiple random enchantments") |
| Zookeeper | Legendary | 5 Mystery Eggs | |
| Pyro | Legendary | Flint and steel, 5 lava buckets, diamond chestplate, Fire Resistance potion | The vanilla 3:00 potion |
| Troll | Legendary | 12 cobwebs, 5 fireworks, leather armour (Protection III, Fire Protection IV, Blast Protection IV, Thorns I, Unbreaking II), splash Poison II (5 s), 2 splash Slowness II (5 s) | |
| Golem | Legendary | Iron chestplate (Protection II), iron boots (Protection III), My Precious, Golem's Poppy (Sharpness I) | |
| End Lord | Mythical | Purple leather chestplate, leggings, boots (Protection III), 2 Time Warp Pearls | |
| Monster Trainer | Mythical | Green leather armour (Protection III), 5 Capture Eggs, a Throwable Spawn Egg | |
| Nether Lord | Mythical | Orange leather armour (Protection III), Nether Lord's Fire Nugget (Fire Aspect I, Sharpness II), Fire Resistance potion, 16 netherrack | Potion: Fire Resistance II for 2:00 (Twin Pearl patch) |
| Fishmonger | Mythical | Grey leather armour (Protection III), 3 Fishmonger Silverfish | |
| Thundermeister | Mythical | Blue leather armour (Protection III), Thundermeister Axe | A gold axe (local material) |
| Chronobreaker | Mythical | Blue leather armour (Protection III), 2 Echoes | A clock (local item) |
| Cryomancer | Mythical | Gray leather armour (Protection III), 16 snowballs, 2 Ice Bridge Eggs | |

Bots that never chose a kit pick one of Armorer, Knight, Pro, Scout, Baseball Player, Speleologist, Pig Rider, Farmer, Salmon, Ecologist, Fallen Angel or Golem at random each match (local: kits whose armour and weapon a combat bot uses at once).

### Kit abilities

| Item or ability | Effect | Source |
| --- | --- | --- |
| Corrupted Pearl | An ender pearl that cannot be thrown in the first 30 s | Export ("30s delay") |
| Time Warp Pearl | Teleports like a pearl, then returns you to where you threw it 3 s after landing, with Weakness II for 3 s | Kit guide (return after 3 s), Twin Pearl patch (Weakness II 3 s) |
| Echo | Teleports you to where you stood 10 s ago and heals 2 hearts; refused above the void | Grim Reaper update, March 2023 patch |
| Ice Bridge Egg | Lays a 3-wide packed-ice path under its flight for 2 s; cannot be thrown in the first 30 s | Export ("30s delay"), Cryomancer nerf thread (packed ice); path width and length local |
| Cryomancer ground freeze | Using a sword turns the solid ground in a 5×5 square below you into packed ice, every 10 s | Grim Reaper update (ability); size and cooldown local |
| Capture Egg | Thrown at a mob within 2.5 blocks of where it lands: removes it and gives you a Throwable Spawn Egg of it | Kit guide; range local |
| Throwable Spawn Egg | Thrown: spawns its captured mob, or else a zombie or skeleton in full leather, gold, iron or diamond armour, fighting for you | Kit guide |
| Mystery Egg | Thrown: spawns a random mob fighting for you (zombie, skeleton, spider, creeper, enderman, snow golem, wolf, blaze, silverfish, witch, chicken or pig; 1 in 10,000 a giant) | Kit guide (giant chance); mob list local |
| Fishmonger Silverfish | Thrown: spawns a silverfish fighting for you; melee hits by a Fishmonger also spawn one 10% of the time | Kit guide, Twin Pearl patch (10%) |
| Charged Creeper Egg | Thrown: spawns a charged creeper that goes for your enemies | Export |
| Thundermeister Axe | Melee hits strike visual lightning and take 1.5 hearts past armour, every 5 s | Kit guide (true damage), Angel's Rebirth (5 s cooldown); damage local; the guide's "4 uses" is not applied |
| Nether Lord fireball | Using the Fire Nugget or a sword throws a small fireball, every 20 s | Kit guide; using a bow draws it as usual |
| My Precious | Using it gives 2 permanent extra hearts for the match | Kit guide |

Mobs fighting for a team go for the nearest enemy participant within 16 blocks and cannot hurt their team. Thrown ability items fly like snowballs.

## Insane perks

All are on unless turned off, except **Double-Edged Sword** and **Dragon's Pledge**, which start off (local: their drawbacks). "Max" is the number with every upgrade level (`skywars_max_all_perks`).

| Perk | Base | Upgrade (levels) | Max | Notes |
| --- | --- | --- | --- | --- |
| Bridger | 50% chance a placed block is not used up | | 50% | |
| Bulldozer | Kills: Strength I 5 s in solo, 2 s in teams | | | "Solo" is a free-for-all or teams of one |
| Juggernaut | Kills: Regeneration I 10 s | | | |
| Knowledge | Kills: 3 levels | Big Brain +2 (1) | 5 levels | |
| Lucky Charm | 30% golden apple on kill | Luckier Charm +1% (3) | 33% | Tier count: Angel's Descent guide |
| Mining Expertise | 40% double ore drops | Meticulous Miner +2% (5) | 50% | Tier count: Angel's Descent guide |
| Resistance Boost | Resistance II 15 s at the start | | | |
| Savior | Kills: Absorption I 7 s | | | |
| Annoy-o-mite | Bow hits: 10% a silverfish beside the enemy | | | The silverfish fights for you |
| Arrow Recovery | Bow hits: 50% an arrow back | | | |
| Blazing Arrows | 15% arrows shot are on fire | Steel Quiver +2% (5) | 25% | Tier count local (Chilled Quiver, its sibling upgrade, has five) |
| Environmental Expert | Half damage from sources without an attacker | | | Not the void or `/kill` |
| Fat | Absorption I 20 s at the start | | | |
| Speed Boost | Haste II 300 s at the start | Adrenaline: Speed I 7 s (1) | | Adrenaline from Angel's Rebirth |
| Barbarian | +1 Sharpness on the held axe every 3 axe kills | | | |
| Black Magic | Void kills: 30% ender pearl | Sorcerer's Spell +1% (5) | 35% | |
| Diamondpiercer | 20% of melee hits on diamond armour wearers deal 20% more | | | |
| Frost | Critical arrows: 40% Slowness I 3 s | Chilled Quiver +2% (5) | 50% | |
| Marksmanship | +1 Power on the bow every 2 bow kills | | | |
| Necromancer | Kills: 16% a zombie fighting for you | | | |
| Robbery | Bare-fist hits: 20% you take the enemy's held item | | | |
| Apothecary | Positive potion effects last 30% longer | | | |
| Diamond In The Rough | 5% diamond on kill | +5% (4 more tiers) | 25% | Five tiers: Angel's Descent guide |
| Double-Edged Sword | First 3 sword kills: +1 Sharpness and −2 max hearts each | | | Starts off |
| Dragon's Pledge | Your nearest island chest holds only an ender pearl; 7 max hearts | | | Starts off |
| Ender End Game | 10% chance of an ender pearl in each chest you open after a refill | | | Per player and chest (local reading of "more likely") |
| Fortune Teller | The second enchanting offer for a sword or armour includes Sharpness I or Protection I | | | Replaces incompatible offers |
| Fruit Finder | The first mid chest you open gets a golden apple if it has none | | | |
| Hide and Seek | A Tracking Compass, pointing at the nearest enemy, 30 s before each refill | | | |
| Librarian | A Sharpness I, Protection I or Power I book every 3 kills | | | |
| Tenacity | Heal 1 heart on each kill | | | The export says 1 heart (older guides 0.5) |

Kill perks need a credited kill of an enemy (the last hit within 10 seconds counts, so knocking someone into the void does). A bow kill is one whose last hit was an arrow; otherwise the held weapon decides.

## Rules

In every mode hunger stays full and ender pearls do no damage (both default for all players since the Grim Reaper and Angel's Rebirth updates), mined iron, gold and copper drop as ingots, and mined drops go straight into the inventory (Hypixel's former Instant Smelting and Telekinesis perks, now defaults; Mini and Mega list Telekinesis as a global perk). Any of a stack that does not fit drops as usual.

## Insane chest loot

Maps mark chests `skywars/island` or `skywars/mid`; the game fills them from `skywars/insane/island` and `skywars/insane/mid` at the start and at each refill. Items come from the sources below; every weight and roll count is **local**.

- **Island** (each pool rolls once): 32 oak planks or stone, always (March 2023: blocks and a sword in every island chest); an iron sword with Sharpness I (March 2023 replaced the Sharpness I stone sword), a diamond sword or a Sharpness I diamond sword, always; one or two diamond or iron armour pieces; a Power I or Power III bow with 6 arrows (Angel's Rebirth), 16 eggs or 16 snowballs; a splash Regeneration, Regeneration II, Poison or Swiftness II, or a Fire Resistance potion; a fishing rod, 32 or 64 experience bottles, a diamond axe or pickaxe, a water or lava bucket, or an enchanting table (chest items list).
- **Mid**: one or two of a Protection IV diamond helmet, a Projectile Protection III or Protection IV diamond chestplate, Projectile Protection IV diamond leggings (Twin Pearl patch) or Protection II Fire Protection V diamond boots; a Sharpness I Fire Aspect II diamond sword, a Power V bow with 8 arrows or 16 arrows (Angel's Rebirth arrow counts); a splash Regeneration II, Swiftness II or Poison; 3 or 5 ender pearls; 5 or 8 golden apples; 64 snowballs, 64 experience bottles, an Efficiency III diamond axe or pickaxe, a Knockback III fishing rod, 10 TNT, flint and steel or 64 oak planks (chest items list).

Food is left out: there is no hunger.

## Mini

`skywars_mini` plays Hypixel's Mini SkyWars, the former Ranked SkyWars: four-player games on smaller maps (Angel's Rebirth). The layouts offered are 1v1v1v1, 1v1, 1v1v1, 2v2 and free-for-all; the Mini maps have four islands. Default time limit 9 minutes and refills at 3:00 and 5:00 (local, as Insane).

### Mini kits

The 11 kits of the export, A to Z; the menu shows no rarity. Each kit's perk is part of the kit (Hypixel removed Mini's perk slots and built each Ranked kit's perk into it). Champion is the default kit; there is no Default.

| Kit | Contents | Perk (export) | Local or sourced elsewhere |
| --- | --- | --- | --- |
| Armorer | Armorer's Diamond Chestplate (Protection I), Armorer's Diamond Boots, iron pickaxe, axe, helmet, leggings | Kills grant a Protection level on your armor | One random worn piece gets +1 Protection per kill (local reading of "your armor") |
| Athlete | Water bucket, diamond sword, fishing rod, Potion of Strength (0:40), 2 splash Speed III (0:04), 2 golden apples, iron pickaxe, axe, helmet, chestplate, leggings, iron boots (Feather Falling X) | Positive potion effects have a 50% longer duration | The export's "Strength +20%" is Strength I here |
| Blacksmith | Anvil, a random diamond armour piece, iron sword, diamond pickaxe, iron axe, iron armour, two enchanted books (Sharpness I, Protection I or Power I), bow, 16 arrows; starts with 15 levels | Kills grant 3 EXP levels and a random enchanted book (up to level 3) | Books are Sharpness, Protection or Power, level 1 to 3 at random (local choice of enchantments, as Insane's Librarian) |
| Bowman | Bowman's Bow (Power III), 32 arrows, iron sword, pickaxe, axe, Bowman's Diamond Helmet (Protection II), iron chestplate, leggings, boots | Kills grant a Power level on your bow and a Splash Potion of Instant Heal I | The first bow in the inventory gets the level |
| Champion | Champion's Diamond Sword (Sharpness II), iron pickaxe, axe, full iron armour, anvil, 3 Sharpness I books | Kills grant a Sharpness level on your sword | The held sword, else the first sword in the inventory |
| Healer | 2 splash Regeneration II (12 s), 2 splash Healing, diamond sword, iron pickaxe, axe, helmet, chestplate, leggings, diamond boots, 2 golden apples | Kills grant +2 max hearts, a golden apple, and heal 4 hearts | Extra hearts last the match |
| Hound | 16 steak, Hound's Diamond Boots (Protection II), diamond sword, iron pickaxe, axe, helmet, chestplate, leggings; starts with a tamed wolf (20 HP, Resistance II) | Kills spawn a Resistance II 10-heart wolf and 16 steaks; if no wolves are alive, two | Wolves are tamed to the player and fight for the team like other friendly mobs; steak is food, though hunger is off |
| Magician | Diamond sword, iron pickaxe, axe, helmet, diamond chestplate (Fire Protection II), iron leggings, boots, 2 milk buckets, 2 splash Weakness I and Poison I (8 s), 3 splash Harming | Kills grant a random positive potion effect | One of Speed II, Strength I, Regeneration II, Resistance I, Absorption II, Fire Resistance, Jump Boost II or Invisibility for 10 s (local list and duration) |
| Paladin | Diamond sword, iron pickaxe, axe, helmet, chestplate, Paladin's Diamond Leggings (Protection II), iron boots, 3 splash Resistance I (15 s) and Regeneration I (8 s) | First kill: Resistance III 3 s; second kill: Resistance II 4 s | |
| Pyromancer | Splash Fire Resistance I (90 s), diamond sword (Fire Aspect I), diamond boots (Protection III, Fire Protection IV), 2 lava buckets, iron pickaxe, axe, helmet, chestplate, leggings | Kills grant 24 s of Fire Resistance, 10 s of Speed II, light arrows you shoot on fire and leave a trail of fire as you walk | Burning arrows and the fire trail last 10 s after a kill, the Speed II's time (local reading) |
| Scout | Diamond axe (Sharpness I), diamond pickaxe (Efficiency II), 3 splash Speed II (0:20), iron helmet, chestplate, leggings, Scout's Diamond Boots (Feather Falling I) | Kills grant an ender pearl | |

The export names Magician's and Healer's potions "Harming (2❤)" and "Healing (2❤)"; they are vanilla Instant Damage I and Instant Health I. Bots that chose no kit pick one of Athlete, Blacksmith, Bowman, Champion, Healer, Hound, Magician, Paladin, Pyromancer or Scout (local: Armorer has no weapon).

### Mini perks

The Select Mini Perks menu shows seven perk slots. Hypixel's refuses all of them ("This mode does not allow the use of perk slots!"); here they work as Mega's (**local**): six usable, the seventh while `skywars_max_all_perks` is on, empty until the player chooses. They offer the 12 Mega slot perks with Mega's numbers (see [Mega perks](#mega-perks)), which add to the kit's own perk. Everyone has the global perks **Juggernaut** (kills grant 10 s of Regeneration I) and **Telekinesis** (mined ores go straight into the inventory), as the export shows.

### Mini chest loot

Maps mark chests as for Insane; Mini fills them from `skywars/mini/island` and `skywars/mini/mid`. Hypixel publishes no Mini loot list beyond "doubled the blocks found in starter island chests", so the tables are **local**:

- **Island**: 64 oak planks or 64 stone, always (Insane's 32 doubled); a diamond armour piece or a Sharpness I iron sword, or nothing; a bow with 8 arrows, 16 snowballs or 16 eggs, or nothing; a golden apple or a splash Healing or Swiftness potion, or nothing; a water or lava bucket, fishing rod or 16 experience bottles, or nothing.
- **Mid**: one or two Protection I-II diamond armour pieces; a Sharpness I diamond sword or a Power I bow with 12 arrows; 2 ender pearls, 3 golden apples or a splash Regeneration II or Healing II; 32 oak planks, 4 TNT, flint and steel or 32 experience bottles.

## Mega

`skywars_mega` plays Hypixel's Mega SkyWars Doubles: teams of two on a large map (Mega Doubles update; Angel's Rebirth keeps it open all week). The first layouts offered are twelve and eight teams of two, then 2v2v2v2, 2v2 and free-for-all. Hypixel fills up to 20 teams of two; the bundled map has twelve two-player islands (local). Default time limit 15 minutes (local) and refills at 3:00 and 5:00 (local, as Insane).

### Mega kits

The 16 kits of the export, maxed (Angel's Rebirth: "All Kits and Perks in Mega SkyWars are now maxed by default"); Default first, then A to Z; the menu shows no rarity.

| Kit | Contents | Local or sourced elsewhere |
| --- | --- | --- |
| Default | Iron pickaxe, axe, shovel, leather helmet, chestplate, leggings, boots, iron sword | |
| Armorer | Diamond chestplate and boots (Protection I), 2 splash Resistance (9 s) | |
| Armorsmith | 3 anvils, 24 experience bottles, book (Protection III, Sharpness I), diamond leggings, enchanting table | |
| Baseball Player | Diamond helmet (Protection IV), diamond sword (Knockback I), leather chestplate | |
| Cannoneer | 32 TNT, 4 redstone blocks, water bucket, diamond leggings (Blast Protection IV), leather chestplate, stone sword (Sharpness I), leather helmet | |
| Enderman | Leather chestplate and leggings, diamond boots (Feather Falling II, Protection I), 2 Corrupted Pearls (30 s delay) | |
| Fisherman | Fishing rod (Lure V, Luck of the Sea XL, Unbreaking X), diamond boots (Protection I), diamond helmet | |
| Healer | 3 splash Instant Health II, splash Regeneration II (16 s), diamond shovel, gold armour (Protection I) | |
| Hellhound | Diamond axe, diamond boots (Protection IV), 3 Wolf Eggs, leather helmet, chestplate, leggings | The eggs spawn ordinary wolves, as Insane's spawn eggs do; whether Hypixel's fight for you is unconfirmed |
| Hunter | Bow (Power III), 32 arrows, chainmail chestplate, leggings, boots | |
| Knight | Diamond sword (Sharpness II), gold armour, flint and steel | |
| Paladin | 2 golden apples, iron helmet and chestplate, white leather leggings and boots, iron sword (Smite X) | |
| Pyro | Diamond axe (Fire Aspect I), flint and steel, splash Fire Resistance II (1200 s), leather chestplate and leggings (Unbreaking X, Protection V), lava bucket | |
| Scout | Diamond sword, 4 splash Speed II (67 s), leather chestplate and leggings, 32 blue wool | |
| Skeletor | Chainmail armour (Protection II), 4 Skeleton Eggs, 16 arrows, bow | Ordinary skeleton eggs, as Hellhound's |
| Witch | 3 splash Poison (12 s) and Slowness (15 s), 3 splash Blindness I (10 s), 2 Strength I potions (6 s), leather armour (Protection I), wooden sword (Sharpness IV) | |

Bots that chose no kit pick one of Default, Baseball Player, Cannoneer, Hellhound, Knight, Paladin or Scout (local).

### Mega perks

Seven perk slots; a player's perks are those in the slots they may use (six, or seven while `skywars_max_all_perks` is on) plus the global Juggernaut and Telekinesis. Numbers are the maxed ones, since Hypixel maxes every Mega perk by default; Mega perks have no further upgrades here.

| Perk | Effect | Default slot | Source |
| --- | --- | --- | --- |
| Bridger | 50% chance a placed block is not used up | 1 | Export |
| Lucky Charm | 30% golden apple on kill | 2 | Export |
| Rusher | Speed I 15 s at the start | 3 | Export ("Rusher I") |
| Arrow Recovery | Bow hits: 50% an arrow back | 4 | Export |
| Blazing Arrows | 15% arrows shot are on fire | 5 | Export, Angel's Rebirth (10% to 15%) |
| Tank | Kills: Resistance I 10 s | 6 | Export |
| Mining Expertise | 50% double ore drops | | Wiki (level V); unconfirmed for the current Mega |
| Environmental Expert | 20% less damage from sources without an attacker | | Wiki (level III); unconfirmed for the current Mega |
| Notoriety | Sword kills: 10% chance of +1 Sharpness on the held sword | | Wiki (level V); unconfirmed for the current Mega |
| Marksmanship | +1 Power on the bow every 2 bow kills | | Angel's Rebirth (3 to 2 kills), effect as Insane |
| Necromancer | Kills: 7% a zombie fighting for you | | Wiki (level V); unconfirmed for the current Mega |
| Black Magic | Void kills: 15% ender pearl | | Wiki (level V); unconfirmed for the current Mega |
| Juggernaut (global) | Kills: Regeneration I 10 s | everyone | Export |
| Telekinesis (global) | Mined ores go straight into the inventory | everyone | Export |

The wiki's other Mega perks no longer apply: Ender Mastery (pearls do no damage), Nourishment (removed with hunger in Angel's Rebirth) and Instant Smelting (a rule of every mode).

### Mega chest loot

Mega fills chests from `skywars/mega/island` and `skywars/mega/mid`. The wiki describes Mega loot as Insane's with weaker enchantments in the middle, so the island table is Insane's and the mid table is Insane's with every enchantment above level I one level lower (Protection III instead of IV, Fire Aspect I, Power IV); weights are **local**. Mid chests stand on the mid island and on the four smaller islands around it.

## Lucky Block

`skywars_lucky` plays Insane SkyWars (its kits, perks, choices, chest tables and maps) with lucky blocks. Hypixel ran Lucky Blocks as a Laboratory mode and keeps Lucky Block SkyWars as a special mode, but publishes no outcome list, so the blocks and their table are this mod's (**local**).

When a map is pasted, each team island gets two lucky blocks (yellow glazed terracotta) on solid ground three blocks from its spawn, and four stand around the mid four blocks from its lobby point. Breaking one drops nothing of itself: it rolls one outcome from the table, tells the player what happened, and gives items by popping them out of the block. Each refill puts back every broken lucky block whose spot is empty.

| Outcome | Chance | Kind | What happens |
| --- | --- | --- | --- |
| Diamond armour | 8% | Good | A Protection I diamond helmet, chestplate, leggings or boots |
| Golden apples | 7% | Good | Three golden apples |
| Sharp sword | 6% | Good | A Sharpness II diamond sword |
| Ender pearls | 6% | Good | Two ender pearls |
| Building blocks | 6% | Good | 32 oak planks |
| Power bow | 5% | Good | A Power II bow and 16 arrows |
| Healing potions | 5% | Good | Two splash Instant Health II |
| Enchanting | 4% | Good | 16 experience bottles and a Sharpness, Protection or Power book of level 1 to 3 |
| TNT kit | 4% | Good | Four TNT and a flint and steel |
| Lucky Sword | 2% | Good | A golden sword with Sharpness V and Knockback II |
| Enchanted golden apple | 1% | Good | An enchanted golden apple |
| Totem | 1% | Good | A totem of undying |
| Lightning | 6% | Bad | A real lightning bolt strikes the player |
| Zombies | 6% | Bad | Three zombies, hostile to everyone, climb out of the block |
| Primed TNT | 5% | Bad | Lit TNT appears in the block's place with a 2-second fuse |
| Blindness | 5% | Bad | Blindness and Slowness II for 8 s |
| Levitation | 4% | Bad | Levitation II for 3 s |
| Cobwebs | 4% | Bad | Cobwebs fill the player's feet and head blocks |
| Fireworks | 4% | Fun | Three firework rockets go up and a cake pops out |
| Wolf pack | 4% | Fun | Two wolves tamed to the player join their team |
| Chickens | 4% | Fun | Six chickens and 16 eggs |
| Anvil | 3% | Fun | An anvil falls from six blocks above the player |

Good outcomes add up to 55%, bad ones to 30% and fun ones to 15%.
