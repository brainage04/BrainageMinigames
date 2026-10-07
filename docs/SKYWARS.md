# SkyWars catalog

`skywars` plays Hypixel's **Insane** SkyWars. This page lists every kit, kit ability, perk and chest table with where its numbers come from. **Local** marks a choice this mod makes where no source gives the detail.

## Sources

- **Menu exports**: Hypixel's *Insane Kits* (two pages), *Toggle Insane Perks* (two pages) and *Kits & Perks* menus, exported item by item (name and full tooltip) on a 26.2 client on 2026-10-06. These are the reference for every kit's contents and every perk's description; they win over older sources.
- **Hypixel announcements**: [Angel's Rebirth update](https://hypixel.net/threads/skywars-angels-rebirth-update.5745688/) (2025: Insane perks all active at once, kit and chest-loot changes, perk upgrade changes), [Balancing, QoL, a Grim Reaper and more](https://hypixel.net/threads/skywars-update-balancing-qol-a-grim-reaper-and-more.5003770/) (2022: Chronobreaker, Cryomancer, perk numbers, Insane kit changes), [New Prestiges, QoL changes, Balancing and Bug Fixes, March 2023](https://hypixel.net/threads/skywars-update-new-prestiges-qol-changes-balancing-and-bug-fixes-march-2023.5303364/) (guaranteed island blocks and sword, Time Warp and Echo restrictions), [Twin Pearl balance patch](https://hypixel.net/threads/february-3-twin-pearl-balance-patch.5835829/) (Time Warp Weakness, Fishmonger chance), [Bug fixes, QoL & Balancing](https://hypixel.net/threads/skywars-bug-fixes-qol-balancing-update.5077132/) (Bulldozer in teams).
- **Player guides** (used only where the above say nothing): [Normal and Insane kits guide](https://hypixel.net/threads/guide-skywars-normal-and-insane-kits.2572556/) (mythical abilities, potion effects), [Angel's Descent listed](https://hypixel.net/threads/guide-angels-descent-listed-from-beginning-to-end.3461193/) (upgrade tier counts, Archeologist items), [Insane & Normal chest items list](https://hypixel.net/threads/updated-11-21-tool-insane-normal-ranked-skywars-chest-items-list-wip.919141/) (chest item lists).

## Modes

Kits, perks, saved choices and chest tables belong to a mode (`game.skywars.SkyWarsMode`); `skywars` plays `insane`. Each kit (`SkyWarsKits`) and perk (`SkyWarsPerk`) is data with its mode, so Mini, Mega or Lucky Block add a mode with their own lists and loot tables (`skywars/<mode>/island` and `skywars/<mode>/mid`). Hypixel's Mini and Mega perks are *selected* rather than toggled; that selection is not built yet.

## Progression

There are no SkyWars coins, Soul Well, opals or kit prestige. `brainage_minigames:skywars_max_all_kits` and `skywars_max_all_perks` (both default `true`) unlock everything, perks with every upgrade level. With a rule `false` a player owns Default and what an operator granted (`/minigames skywars grant <players> kit <kit>` / `perk <perk>`, `revoke` to remove), and perks have their base numbers. Choices and grants are saved per UUID and mode in the world's command storage (`brainage_minigames:skywars_progression`). Kits are given and perks read when the cages open; changes take effect from the next match.

## Kits

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

## Perks

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

Hunger stays full and ender pearls do no damage (both default for all players since the Grim Reaper and Angel's Rebirth updates), mined iron, gold and copper drop as ingots, and mined drops go straight into the inventory (Hypixel's former Instant Smelting and Telekinesis perks, now defaults). Any of a stack that does not fit drops as usual.

## Chest loot

Maps mark chests `skywars/island` or `skywars/mid`; the game fills them from `skywars/insane/island` and `skywars/insane/mid` at the start and at each refill. Items come from the sources below; every weight and roll count is **local**.

- **Island** (each pool rolls once): 32 oak planks or stone, always (March 2023: blocks and a sword in every island chest); an iron sword with Sharpness I (March 2023 replaced the Sharpness I stone sword), a diamond sword or a Sharpness I diamond sword, always; one or two diamond or iron armour pieces; a Power I or Power III bow with 6 arrows (Angel's Rebirth), 16 eggs or 16 snowballs; a splash Regeneration, Regeneration II, Poison or Swiftness II, or a Fire Resistance potion; a fishing rod, 32 or 64 experience bottles, a diamond axe or pickaxe, a water or lava bucket, or an enchanting table (chest items list).
- **Mid**: one or two of a Protection IV diamond helmet, a Projectile Protection III or Protection IV diamond chestplate, Projectile Protection IV diamond leggings (Twin Pearl patch) or Protection II Fire Protection V diamond boots; a Sharpness I Fire Aspect II diamond sword, a Power V bow with 8 arrows or 16 arrows (Angel's Rebirth arrow counts); a splash Regeneration II, Swiftness II or Poison; 3 or 5 ender pearls; 5 or 8 golden apples; 64 snowballs, 64 experience bottles, an Efficiency III diamond axe or pickaxe, a Knockback III fishing rod, 10 TNT, flint and steel or 64 oak planks (chest items list).

Food is left out: there is no hunger.
