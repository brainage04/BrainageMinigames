# UHC Champions progression: sources and implemented catalog

This is a sourced reconstruction, not a claim to reproduce Hypixel's unpublished current plugin. Official announcements override older screenshots. A guide being hosted on `hypixel.net` does **not** make its author Hypixel staff. Sources below distinguish those cases. Where an announcement does not publish a shop price, ingredient image or effect parameter, the implementation uses an explicitly identified local policy.

## Sources

- **A15 — official staff announcement:** [UHC Update, June 2015](https://hypixel.net/threads/uhc-update.338912/). Adds Hunter, Bloodcraft, profession prestige and Extra Ultimates; describes profession shop discounts.
- **A17 — official staff announcement and recipe screenshots:** [UHC Update — Balances, Professions, Recipes and more, 2017](https://hypixel.net/threads/uhc-update-balances-professions-recipes-and-more.1277330/). Adds Toolsmith and Apprentice, kit prestiges, Shoes of Vidar, Ambrosia, Potion of Vitality and Miner's Blessing. Halves kit upgrade prices and the first eight profession-node prices. Changes Artemis, Excalibur, King's Rod, Death's Scythe, Yggdrasil, Chest of Fate, Tablets and other crafts.
- **A19 — official staff announcement and screenshots:** [UHC Update — New Profession, Extra Ultimates, Gameplay Changes and more, 2019](https://hypixel.net/threads/uhc-update-new-profession-extra-ultimates-gameplay-changes-and-more.1948006/). Adds Invention (Gold Pack, Sugar Rush, Backpack, Fusion Armor), Convenience, Trapper, Bloodlust, Modular Bow, Expert Seal, Hermes' Boots and Barbarian Chestplate. Strength I/II is +30%/+60%, not modern vanilla's flat +3/+6.
- **A20 — official staff announcement and screenshots:** [UHC Update — Game Modifiers and New Profession, 2020](https://hypixel.net/threads/uhc-update-game-modifiers-and-new-profession.3319440/). Adds Strategist, Lucky Shears, The Deep, Swan Song, El Dorado, Fate's Call, The Mark and Warlock Pants; describes Strategist's second prestige and the flexible crafting system.
- **G15 — player-authored guide on the official forum:** [UHC Champions Full Guide Updated](https://hypixel.net/threads/uhc-champions-full-guide-updated.226242/). Provides the original nine trees, passive increments, coin actions, original kit contents, craft images and 17 Extra Ultimate prices/prerequisites.
- **G16 — player-authored guide on the official forum:** [Guide: UHC Craftings, Kits & Strategies](https://hypixel.net/threads/guide-uhc-craftings-kits-strategies.795911/). Provides historical kit levels, shop values and recipe/effect screenshots.
- **GP — explicitly unofficial guide on the official forum:** [All the professions in UHC Champions](https://hypixel.net/threads/unofficial-guide-all-the-professions-in-uhc-champions.471396/). Cross-checks original profession-node progression.
- **Official wiki checked:** [wiki.hypixel.net](https://wiki.hypixel.net/). The material found there was Hypixel **SkyBlock**, not a UHC Champions profession/coin catalog. No UHC tree values are attributed to that wiki. Current/live shop values and unpublished implementation details could not be confirmed there.

Images linked by official staff posts are primary evidence even when hosted on Imgur. Several 2019/2020 image links are no longer readable. The named features remain confirmed by the announcement, but unreadable ingredients/effects are not presented below as confirmed.

## Coin actions and persistence

G15 states these **base** rewards, before Hypixel network boosters, ranks or other multipliers:

| Action | Base coins | Implemented boundary |
| --- | ---: | --- |
| Survive each five minutes | 10 | Alive active participants only; once at each five-minute boundary |
| Kill an opponent | 50 | Killer and each alive teammate within **200 blocks**, inclusive, in the killer's dimension |
| Enter the Nether | 15 | First UHC Nether entry per player per match; portal cycling cannot repeat the reward |
| Win | 150 | Each participating member of the sole winning team; no draw/stop reward |

The once-per-match Nether interpretation is a local anti-repeat interpretation: G15 names entry but does not specify repeated crossings. Hypixel boosters and quests are not emulated. Coins are world-persisted per UUID, together with purchases and selected kit, in command storage `brainage_minigames:uhc_progression`; spending is checked before either the balance or purchase changes. Rebuying cannot charge twice. Match cleanup does not delete progression.

The following additional rewards are **local policy**, not attributed to Hypixel:

| Action | Base coins | Implemented boundary |
| --- | ---: | --- |
| Assist | 20 | Accepted player damage within the preceding 200 active-match ticks; somebody else receives opponent kill credit |
| First blood | 25 bonus | First credited opponent kill, once per match |
| Top 10 / 5 / 3 placement | 25 / 50 / 75 | Cumulative on elimination; count living participants immediately before removal, including the eliminated player |
| Reach deathmatch | 50 | Once per surviving participant, including a combat logger, after the arena transition succeeds |
| Profession craft | 5 | Take a profession result; previewing or opening the grid earns nothing |
| Diamond / gold mining | 3 / 1 | One award per successfully mined ore block, not per Fortune/drop-multiplied item; includes deepslate and Nether gold ores |
| Hostile mob kill | 1 | Successful vanilla death with player kill credit; excludes passive mobs and combat loggers |
| Golden head | 5 | Actual golden-head consumption, not right-clicking or interrupting use |
| Border stage | 10 | Once per announced shrink stage: four Badlion stages or one Hypixel continuous stage, plus the deathmatch shrink |
| Anti-janitor duel win | 10 bonus | Protection is enabled and the current lock holder receives kill credit for the opponent's elimination; no timeout/forfeit bonus |

A disconnected participant's zombie uses the normal **50-coin opponent kill**, including alive teammates within 200 blocks; there is no separate duplicate logger reward. First blood, assists, placement and the valid anti-janitor duel bonus use the same player-elimination path. Placement also applies to active-match forfeits and immediate disconnect elimination when combat loggers are disabled.

`brainage_minigames:uhc_uncapped_coin_awards` is a boolean defaulting to **true**. On: profession crafts, ore mining and hostile mob kills have no per-match coin cap. Off: their **base-coin** totals are capped separately at **50 / 60 / 30 per player per match**; diamond and gold share one mining counter, and a final ore award can be partial. These counters retain earlier earnings across rule changes. The caps are applied before the coin multiplier. Other actions are not capped.

`brainage_minigames:uhc_coin_multiplier` is a world-persisted integer percentage, default **100**, applied centrally to every award above. `150` gives 1.5×; `0` disables awards. Fractional coins round down separately for each award. Each positive award announces its actual amount and action to the recipient; a disconnected recipient's award is announced to the match instead. Purchases and balances are not rescaled when this rule changes. Coin progression runs only in regular UHC, never in duels, Meetup or FinalUHC.

## Commands and world gamerules

```text
/minigames uhc coins
/minigames uhc trees
/minigames uhc trees cooking
/minigames uhc unlock cooking recipe1
/minigames uhc unlock extras cornucopia
/minigames uhc kit
/minigames uhc kit ecologist
/minigames uhc kit_upgrade ecologist level1
/minigames uhc kit default
/minigames uhc prestige_bonus stone iron_pickaxe
/minigames uhc craft light_apple
/gamerule brainage_minigames:uhc_max_all_perks false
/gamerule brainage_minigames:uhc_max_all_kits false
/gamerule brainage_minigames:uhc_choose_prestige_bonus true
/gamerule brainage_minigames:uhc_coin_multiplier 150
/gamerule brainage_minigames:uhc_uncapped_coin_awards false
/gamerule brainage_minigames:uhc_unlimited_crafts false
/gamerule brainage_minigames:uhc_no_duplicate_crafts false
```

- `uhc_max_all_perks` defaults **true**. This is this mod's default, not a Hypixel claim: Hypixel's documented system requires coin purchases. On: every profession, profession prestige and Extra Ultimate counts as owned from the first game, without writing purchases. Off: ownership comes from actual purchases, which are kept while the rule is on. It does not change selectable kits.
- `uhc_max_all_kits` independently defaults **true**, also this mod's default. On: every selectable kit is tier III and prestiged, without changing purchases. Off: kit tiers and prestige come from purchases. It does not enable max-perks.
- `uhc_choose_prestige_bonus` defaults **false**. Off: kit prestige uses its original weighted roll. On: `/minigames uhc prestige_bonus <kit>` offers clickable choices; the selected bonus persists per UUID and kit. The kit must be prestiged through purchases or max-kits. With no saved choice, the first listed bonus is used.
- `uhc_unlimited_crafts` defaults **true**. This is **not** Hypixel's default. Off: normal profession recipes can be made three times, profession ultimates once; prestige adds one craft to each. Extra Ultimates stay at one, with no prestige bonus. Limits are per player, recipe and match.
- `uhc_no_duplicate_crafts` defaults **true**, not claimed as a Hypixel rule. Fusion draws helmet/chestplate/leggings/boots without replacement; Pandora excludes already drawn weighted outcomes; the local Dice pool also draws without replacement. Exhausting a pool resets it. Off restores independent random draws. Viewing a Fusion result cannot reroll or spend a result: only taking it commits the draw.

These gamerules are persistent world-wide overrides rather than per-match settings. Entitlements, crafting, passive perks and special-item use are restricted to active regular UHC participants, including their UHC Nether travel and deathmatch. They do not enable recipes in the lobby, after elimination/end, in duels, Meetup or FinalUHC. Kit selection is applied at the next UHC start; an explicit match kit override wins. With no selection, or after `kit default`, the player receives **Stone Gear**, the four stone tools (tier III with its prestige bonus while max-kits is on).

When an unlocked craft has remaining uses and the player has its ingredients, a chat prompt names it and offers **[Craft]**. Clicking runs `/minigames uhc craft <recipe>`, which rechecks availability and opens a server-side crafting-table menu with one craft's ingredients moved from inventory into the grid. Taking the output uses the ordinary crafting hooks and limits; closing returns unused grid and cursor items. Prompts do not repeat while that recipe's ingredients remain unchanged, and unrelated inventory changes do not reset them.

## Exact profession-node layout

The four craft slots in every tree below are `recipe1`, `recipe2`, `recipe3`, `recipe4` (ultimate). A **passive level is the number of purchased perk nodes**, not merely the depth of one branch.

| Node | Prerequisites | Passive contribution | Price in base units after A17 |
| --- | --- | ---: | ---: |
| `recipe1` | none | 0 | `floor(B/2)` |
| `perk1` | recipe1 | +1 | `floor(2B/2)` |
| `perk_a2`, `perk_b2` | perk1 | +1 each | `floor(4B/2)` each |
| `perk_a3`, `perk_b3` | matching branch level 2 | +1 each | `floor(10B/2)` each |
| `recipe2`, `recipe3` | a3, b3 respectively | 0 | `floor(20B/2)` each |
| `perk_a4`, `perk_b4` | matching branch recipe | +1 each | `40B` each |
| `perk_a5`, `perk_b5` | matching branch level 4 | +1 each | `60B` each |
| `perk6` | both a5 and b5 | +1 | `80B` |
| `recipe4` | perk6 | 0 | `100B` |
| `prestige` | recipe4 | extra craft, not an extra passive level | see below |

Both branches together yield ten passive levels. The first eight nodes including both branch recipes receive A17's 50% discount. Historical G16/GP bases: Weaponsmith, Armorsmith and Cooking `B=250`; Survivalism and Engineering `B=125`; Hunter and Bloodcraft `B=500`. A15's 25% Alchemy/Enchanting discount is applied to the `B=250` schedule, with G16's rounded root base of 190 (95 after A17); later nodes use `floor(250×multiplier×3/4)`. **Odd-price discount rounding is local and not confirmed by a current shop screenshot.** Prestige: 100,000 for Hunter/Bloodcraft; 50,000 for the B=250 trees; 37,500 for the discounted/125-base trees.

Later unpublished shop values are **local policy**: Invention uses `B=25`, Strategist `B=125`; Strategist second prestige costs 100,000. Toolsmith/Apprentice have four recipes and prestige, no invented passive: respectively 250/125 for recipe1, 5,000/2,500 for each branch recipe, 25,000/12,500 for recipe4 and 50,000 prestige. Their later shop values could not be confirmed. Their crafting content is independently confirmed by A17.

### Passive effects at every purchased level

| Tree / source | Levels 1 → 10, exactly |
| --- | --- |
| Weaponsmith — Berserk (G15) | Strength I after a kill for **0.5, 1, 1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5 seconds** |
| Armorsmith — Tenacity (G15) | Resistance I after a kill for **1, 2, 3, 4, 5, 6, 7, 8, 9, 10 seconds** |
| Alchemy — Brewing (G15) | Beneficial potion duration **+2, +4, +6, +8, +10, +12, +14, +16, +18, +20 percent** |
| Survivalism — Expert (G15) | Non-player/environment damage reduction **4, 8, 12, 16, 20, 24, 28, 32, 36, 40 percent**; no reduction of player damage |
| Engineering — Speed Mining (G15) | Haste I on mining an ore for **5, 10, 15, 20, 25, 30, 35, 40, 45, 50 seconds** |
| Cooking — Vitamins (G15) | Absorption II at match start for **60, 120, 180, 240, 300, 360, 420, 480, 540, 600 seconds** |
| Enchanting — Magnetism (G15) | Mob/ore XP **+5, +10, +15, +20, +25, +30, +35, +40, +45, +50 percent**; fractional XP is stochastically rounded |
| Hunter — Fortune (G15) | **1, 2, 3, 4, 5, 6, 7, 8, 9, 10 gold nuggets** per teammate's contribution on a team kill |
| Bloodcraft — Celerity (G15) | Speed II after consuming a head for **6, 7, 8, 9, 10, 11, 12, 13, 14, 15 seconds** |
| Invention — Convenience (A19) | Chance of extra sand/flint/obsidian **5, 10, 15, 20, 25, 30, 35, 40, 45, 50 percent**; eligible-block mining Efficiency bonus **+1 through +10**. Exact chance increments are unpublished; 5% per level is local policy. Gravel is faster to mine but is not itself duplicated. |
| Strategist (A20) | Below five hearts and after taking no damage: heal half a heart each **60, 57, 54, 51, 48, 45, 42, 39, 36, 33 seconds**. Second prestige subtracts another five seconds (max becomes 28). Taking damage resets the quiet interval. |
| Toolsmith / Apprentice (A17) | No separately published passive; none invented |

Convenience combines its purchased Efficiency levels with the held tool's Efficiency enchantment before vanilla mining-speed scaling, on both Fabric and NeoForge. It affects only sand, red sand, gravel and obsidian; airborne and Mining Fatigue penalties still apply to the resulting speed rather than being bypassed by the bonus.

### Every tree's four recipes

Effects shown are the implemented latest announcement overrides, not an unqualified claim about today's Hypixel server.

| Tree | Recipe 1 | Recipe 2 | Recipe 3 | Recipe 4 (ultimate) |
| --- | --- | --- | --- | --- |
| Weaponsmith | Vorpal Sword: iron, Smite II, Bane II, Looting I (A17) | Book of Sharpening: Sharpness **I** | Book of Power: Power **I** | Dragon Sword: diamond, +1 attack damage |
| Armorsmith | Leather Economy: 3 leather + 6 sticks → 8 leather | Protection **I** book | Book of Artemis: Projectile Protection **I** book | Dragon Armor: diamond chestplate, Protection IV |
| Alchemy | Dust of Light: 8 redstone + flint/steel → 8 glowstone dust | Brewing Artifact: 4 seeds + fermented spider eye → nether wart | Nectar: Regeneration II, 10 seconds (A17 adds two seconds) | Nether Artifact: logs/lava/firework → blaze rod |
| Survivalism | Food Economy: 8 raw beef + coal → 10 cooked beef | Toughness: Resistance I, 120 seconds | Spiked Armor: unbreakable leather chestplate, Protection V/Thorns I | Seven-League Boots: Protection III/Feather Falling III diamond boots |
| Engineering | Iron Economy: 8 iron ore (or raw iron) + coal → 10 iron | Obsidian: lava + water bucket, shapeless, returns buckets | Tarnhelm: diamond helmet, Protection I/Fire Protection I/Aqua Affinity III | Philosopher's Pickaxe: Fortune II diamond pick, three durability |
| Cooking | Eve's Temptation: apple + bone meal → 2 apples, shapeless (A20) | Healing Fruit: craftable melon slice, no extra healing invented | Holy Water: Absorption IV, 120 seconds | **Light Apple: 4 gold ingots + apple → ordinary golden apple**, not 8 gold |
| Enchanting | Enlightening Pack: 4 redstone blocks + bottle → 8 XP bottles | Light Anvil: 6 iron ingots + iron block → anvil | Light Enchanting Table: bookshelf, 5 obsidian, diamond, XP bottle | Book of Thoth: Sharpness II/Power I/Protection III (later A17 edit removes Fire Aspect/Punch) |
| Hunter | Arrow Economy: 3 flint, 3 sticks, 3 feathers → 20 arrows | Saddle | Velocity: Speed III, 12-second splash, full duration only to thrower (A17), plus Alchemy | Fenrir: tamed wolf, 200 maximum HP/2 initial HP, Speed II/Strength II/Resistance II (A19 Strength +60%) |
| Bloodcraft | **Golden Head**: player head surrounded by 8 gold; Regeneration II 10 seconds and Absorption I 120 seconds | Pandora's Box: player head surrounded by chests; weighted random reward | Panacea: Instant Health V (A17) | Cupid's Bow: Power II/Flame I (A17 changes Power to II) |
| Toolsmith (A17) | Forge: fuel-free instant smelting; destroyed after ten successful smelts | Quick Pick: Efficiency I/Unbreaking I iron pick | Lumberjack's Axe: ten whole-tree cuts, cannot enchant | Enhancement Book: anvil applies a vanilla level-30 roll |
| Apprentice (A17/A19) | Apprentice Helmet: Protection I/Fire Protection I/Blast Protection I/Projectile Protection I | Apprentice Sword: Sharpness I → II fifteen minutes after PvP → III at deathmatch; not anvilable | Apprentice Bow: Power I → II → III on the same timeline; not anvilable | Master's Compass: particles point toward nearest other surface player |
| Invention (A19/A20) | Gold Pack: 8 gold ore (or raw gold) + coal → 10 gold | Sugar Rush: any sapling + seeds/sugar → 4 sugar cane (A20 allows any sapling) | Backpack: persistent 27-slot item chest, no nested backpacks | Fusion Armor: any five diamond armor pieces → random Protection V piece; full Fusion set gives Resistance I |
| Strategist (A20) | Lucky Shears: five times ordinary apple chance, only while below five hearts with no living teammate | The Deep: random iron/gold/book/fish, gold cap sixteen per game, same low-health/no-teammate condition | Swan Song: book, +0.5% melee damage per missing health point per level; two level-I books combine to II | Treasure to El Dorado: map to care package on opposite side; same low-health/no-teammate condition |

The first nine rows use G15/G16 images, with A17/A19/A20 updates where published. There are **52 profession recipes**, plus **30 Extra recipes** in the next section (including Flask of Cleansing). No vanilla client recipe pack is required: the server's real crafting table preview/take/shift-craft paths enforce ownership and counts.

#### Crafting grids

Spaces are empty cells; rows are separated by `/`; horizontal mirror and translated placement are accepted.

- Light Apple: ` G /GAG/ G `, G=gold ingot, A=apple. This is Cooking's **recipe4**, unlocked after both perk branches, not an automatic level-1 craft.
- Golden Head: `GGG/GHG/GGG`, G=gold ingot, H=an actual UHC dropped player head. This is Bloodcraft's **recipe1**. An arbitrary player-head item is not accepted.
- Sharpness I book: `F  / PP/ PS`, F=flint, P=paper, S=iron sword.
- Power I book: `F  / PP/ PB`, F=flint, P=paper, B=bone.
- Protection I book: `   / PP/ PI`, P=paper, I=iron ingot.
- Projectile Protection I book: `   / PP/ PA`, P=paper, A=arrow.
- Dragon Sword: ` B / S /OBO`, B=blaze powder, S=diamond sword, O=obsidian.
- Dragon Armor: ` M / C /OAO`, M=magma cream, C=diamond chestplate, O=obsidian, A=anvil.
- Philosopher's Pickaxe: `OGO/LSL/ S `, O=iron ore, G=gold ore, L=lapis block, S=stick.
- Quick Pick: `OOO/CSC/ S `, O=iron ore, C=coal, S=stick.
- Lumberjack's Axe: `IIF/IS / S `, I=iron ingot, F=flint, S=stick.
- Enhancement Book: `PPP/KTA/SSS`, P=water bottle, K=gold pick, T=enchanting table, A=iron axe, S=bookshelf.

Logs, leaves, wool, discs and saplings accept their appropriate type/tag. Iron/gold ore cells accept the ore block, its deepslate variant, or raw iron/raw gold: in 26.2 mining those ores drops raw metal, so Iron Economy, Gold Pack, Quick Pick and Philosopher's Pickaxe take it directly and 8 raw iron + coal give 10 iron ingots without smelting. This applies to the crafting table, shift-crafting, the **[Craft]** prompt and `/minigames uhc craft`. Fenrir requires a Swiftness potion; Shoes of Vidar requires Water Breathing; Barbarian Chestplate requires Strength. Potion-input water bottles are not interchangeable with an empty glass bottle. Obsidian keeps both empty buckets.

## Extra Ultimate shop

The original seventeen rows' prices and prerequisites are G15's historical shop values, not new guesses. Required professions must have `recipe4` purchased (no prestige required). Later prices and prerequisite combinations are unpublished and marked **local**. A17 states one craft per game for its changed Extra Ultimates; the mod applies that rule to the Extra shop when the unlimited-crafts rule is off.

| Shop id | Coins | Required completed professions | Effect / evidence |
| --- | ---: | --- | --- |
| `artemis_bow` | 100,000 | Weaponsmith, Hunter | Power III; homing arrows; A17's revised grid is used |
| `flask_of_ichor` | 120,000 | Alchemy, Bloodcraft | Poison II splash for 12 seconds; historical effect parameter not confirmed in a staff announcement |
| `exodus` | 350,000 | Cooking, Armorsmith, Bloodcraft, Engineering | Diamond helmet, Protection III, regeneration on hits; Unbreaking III added A17 |
| `hide_of_leviathan` | 150,000 | Survivalism, Armorsmith | Protection IV diamond leggings, Respiration III after A17 |
| `tablets_of_destiny` | 250,000 | Engineering, Enchanting | Sharpness III/Power III/Protection IV/Fire Aspect I book; no Punch (A17) |
| `axe_of_perun` | 150,000 | Weaponsmith, Enchanting | Diamond axe, 4.5 total attack damage, Unbreaking I, four-second lightning cooldown (A17) |
| `excalibur` | 150,000 | Weaponsmith, Alchemy | Diamond sword; four true explosion damage, five-second cooldown (A17) |
| `anduril` | 150,000 | Weaponsmith, Engineering | Sharpness II iron sword; Speed I/Resistance I while held |
| `deaths_scythe` | 200,000 | Bloodcraft, Weaponsmith | Removes 20% victim current HP per hit, heals attacker for 25% removed (A17) |
| `chest_of_fate` | 75,000 | Bloodcraft, Survivalism, Alchemy | Absorption V 105 sec; Speed I 45 sec → II 30 sec → III 15 sec (A17); risky roll |
| `cornucopia` | 50,000 | Cooking, Engineering | Five golden carrots with extra regeneration |
| `essence_of_yggdrasil` | 75,000 | Enchanting, Alchemy | Right-click: solo 30 levels; team user 15, teammates 8; no ordinary XP bottles (A17) |
| `voidbox` | 50,000 | Bloodcraft, Hunter | Two ordinary ender chests; no fictitious portal output |
| `deus_ex_machina` | 100,000 | Bloodcraft, Alchemy, Cooking | Crafting halves current health (G15), Resistance V ten-second potion |
| `dice_of_god` | 100,000 | Bloodcraft, Engineering | One of six results (A17); unpublished result identities use local pool below |
| `kings_rod` | 50,000 | Engineering, Survivalism | Lure V/Luck X fishing rod, three extra nuggets per catch (A17) |
| `daredevil` | 150,000 | Hunter, Bloodcraft, Survivalism | Tamed saddled horse; 80% max vanilla horse speed; explosions turn it into bones (A17) |
| `shoes_of_vidar` | 100,000 **local** | Survivalism, Enchanting **local** | Depth Strider II/Unbreaking III/Projectile Protection II/Thorns I (A17 screenshot) |
| `ambrosia` | 100,000 **local** | Alchemy, Enchanting **local** | Brewing stand upgrades potion effects to III, caps duration at 60 seconds (A17 screenshot) |
| `potion_of_vitality` | 100,000 **local** | Alchemy, Cooking **local** | Thrower: Speed II 12 sec, Regeneration II 8 sec; other affected entities: Weakness II 12 sec, Wither II 6 sec (A17) |
| `miners_blessing` | 100,000 **local** | Engineering, Toolsmith **local** | Held Saturation III; every 100 durability spent grants Regeneration I five sec; every 250 adds Sharpness/Efficiency level (A17 screenshot) |
| `bloodlust` | 100,000 **local** | Bloodcraft, Weaponsmith **local** | Sharpness I diamond sword; II at 1 kill, III at 3, IV at 6, V at 10; no anvil (A19 screenshot) |
| `modular_bow` | 100,000 **local** | Hunter, Enchanting **local** | A19 name/grid confirmed; local selectable modes below |
| `expert_seal` | 100,000 **local** | Enchanting, Armorsmith **local** | Right-click raises every inventory/equipped item's existing enchants by one, including above vanilla caps (A19 screenshot) |
| `hermes_boots` | 100,000 **local** | Survivalism, Hunter **local** | Protection II/Feather Falling I/Unbreaking II, +10% walk speed (A19) |
| `barbarian_chestplate` | 100,000 **local** | Armorsmith, Weaponsmith **local** | Protection I diamond chestplate; Strength I/Resistance I while worn (A19) |
| `fates_call` | 100,000 **local** | Strategist, Engineering **local** | Chest of assorted items (A20); local grid/package below |
| `the_mark` | 100,000 **local** | Strategist, Hunter **local** | A20 name confirmed; local grid/tracking effect below |
| `warlock_pants` | 100,000 **local** | Strategist, Armorsmith **local** | A20 name confirmed; local grid/healing effect below |
| `flask_of_cleansing` | 100,000 **local** | Alchemy, Bloodcraft **local** | Removes effects and applies Weakness I five sec; A17's gravel/milk/bottle grid |

### Explicitly unconfirmed/local effect details

These are usable local approximations, **not Hypixel-confirmed mechanics**:

- Artemis homing: 25% of arrows fired from a marked Artemis Bow by an active regular-UHC participant, forward 60-degree search cone, nearest enemy within 40 blocks, 20% directional correction per tick. Ordinary arrows, including arrows without a recorded weapon, retain vanilla flight.
- Exodus: Regeneration I two seconds on a landed hit; Cornucopia: Regeneration II ten seconds. Those exact durations/base enchants could not be confirmed from a current staff source.
- Perun lightning deals four true damage. Excalibur's affected enemy radius is three blocks. The lightning amount/radius was not published in the retrieved staff text; Excalibur's four damage and five-second cooldown were.
- Chest of Fate: 50% successful potion roll, otherwise 20 true damage on right-click. Success effects/timing are confirmed by A17; risk probability/amount and instant activation are local.
- Dice results: Artemis, Anduril, Exodus, Perun, Hermes or Chest of Fate, equally likely before the no-duplicate exclusion. A17 says **six unique Dice-only results** but does not publish their identities; this local pool does not pretend otherwise.
- Modular Bow: sneak/right-click cycles Power III, Punch II and Flame I; ordinary right-click still shoots. Its source image grid is preserved, but these modes are local.
- Fate's Call: gold surrounding chest; right-click places an allowed adjacent chest with two golden apples, eight gold, one diamond and 32 arrows. The published name/assorted-chest effect is confirmed; this grid/package is local because its source image could not be read.
- The Mark: ender eye/redstone blocks/compass grid; consumes itself to show the nearest enemy with Glowing for 30 seconds. Warlock Pants: head/diamond leggings/blaze rod grid, Protection I, heals one heart per opponent kill. Their exact source grids/effects could not be confirmed.
- Daredevil max HP is locally 50; explosion remains eight bones; Horseman horse has 15 HP. A17's speed/explosion consequence and A19's Horseman changes are confirmed, these exact auxiliary values are local.
- Bloodlust counts credited opponent kills while the sword is held. An externally raised Sharpness level may be replaced by its next kill tier.
- Enhancement Book's anvil charge is locally three levels. Lumberjack connected-tree traversal is capped at 256 logs for server safety. El Dorado mirrors the player's position inside 80% of the border and lands a package of Efficiency II diamond pick, Sharpness II diamond sword, two golden apples and eight gold. The Deep uses a local 60% gold-attempt/20% iron/10% enchanted-book/10% fish distribution, not a claimed Hypixel loot table.
- Lucky Shears applies its 2.5% extra-apple chance to all leaf types and then the existing apple drop multiplier. All source gaps above are deliberate visible approximations, not hidden fallbacks.

### Pandora's Box (published A17 distribution)

The [staff screenshot](https://staticassets.hypixel.net/news/59c60f21f0305.bh6Fh5EOSGmTWVni9UAIyw.png) totals 100%. Probabilities: Protection II book 7%, Projectile Protection II book 7%, Feather Falling II book 6%, four golden apples 1%, Power II book 7%, Sharpness II book 7%, Instant Health II + Absorption II (90 sec) potion 7%, Slapfish (Regeneration III four sec when consumed) 6%, Golden Head 6%, Sharpness III diamond sword 3%, 32 gold 2%, Power III bow 3%, Fire Aspect I diamond sword 1%, Flame I bow 1%, Power III book 3%, Sharpness III book 3%, two golden apples 6%, three golden apples 3%, 12 gold 7%, 16 gold 6%, 24 gold 5%, Instant Health III potion 2%, Ender Dragon 1%. Owner no-duplicate mode preserves relative weights among remaining outcomes and resets after all outcomes; it does not preserve the unconditional original percentages when exclusion is enabled.

## Selectable kits: every level

The original seven kits' levels are G15/G16, overridden by A17; Farmer/Horseman are A17 screenshots, Trapper A19. Every level-0 kit is free. A17 halves the historical 5,000/15,000/30,000 upgrade prices to **2,500/7,500/15,000**. Later kit/prestige costs are local where no current staff price screenshot was available.

| Kit | Level 0 | Level 1 | Level 2 | Level 3 (latest sourced changes) |
| --- | --- | --- | --- | --- |
| Leather | Full plain leather armor | Full Protection I leather | Full Protection II leather | Full Protection III leather |
| Archer | 3 feathers, 3 string | 5 feathers, 4 string | 7 feathers, 5 string | 9 feathers, 6 string; stone shovel Efficiency III/Unbreaking I |
| Enchanting | 1 book, 7 XP bottles | 2 books, 10 XP bottles | 3 books, 13 XP bottles | 4 books, 15 XP bottles, 18 lapis; stone pick Efficiency III/Unbreaking I; old 3 obsidian removed by A17 |
| Stone | Stone sword/pick/axe/shovel | Same, Efficiency I | Same, Efficiency II | Same, Efficiency III/Unbreaking I |
| Lunch | 3 steak, 1 apple | 5 steak, 2 apples | 7 steak, 3 apples | 12 carrots, 2 melon slices, 2 gold, 3 apples; A17 replaces old golden carrots/steak |
| Looter | 1 bone, 1 slime ball | 2 bones, 2 slime balls, 1 gunpowder | 3 bones, 2 slime balls, 1 gunpowder, 1 spider eye | 3 bones, 3 slime balls, 2 gunpowder, 2 spider eyes; Looting I stone sword |
| Ecologist | 8 logs, 8 lily pads | 16 logs, 16 lily pads | 32 logs, 32 lily pads | 64 logs, 64 lily pads, 12 sugar cane, 21 vines; stone axe Efficiency III/Unbreaking I; old coal removed by A17 |
| Farmer | Stone hoe, 1 melon seed, 1 bone meal | Gold hoe, 1 melon slice, 1 carrot, 2 bone meal | Iron hoe, 2 melon slices, 2 carrots, 3 bone meal | Iron hoe, 3 melon slices, 3 carrots, 4 bone meal |
| Horseman | 3 leather, 3 wheat | 6 leather, 5 wheat | 9 leather, 7 wheat, 1 string | 12 leather, 1 hay bale, 4 string, horse egg, gold horse armor; A19 removes sugar and lowers egg horse HP |
| Trapper | 2 pistons, 2 sticky pistons, 10 redstone, 4 logs | 4/4 pistons, 15 redstone, 8 logs | 6/6 pistons, 20 redstone, 12 logs | 8/8 pistons, 25 redstone, 16 logs (A19 screenshot) |

A17 prestige screenshots add one random bonus. Implemented exact distributions:

| Kit | Prestige bonus distribution | Prestige price |
| --- | --- | ---: |
| Leather | 35% Protection II/Aqua Affinity I iron helmet; 35% Protection II/Feather Falling I iron boots; 20% Protection I iron leggings; 10% Protection I iron chest | 150,000 **local** |
| Archer | 25% 6 sugar cane; 25% 16 flint; 25% 32 arrows; 25% 1 bone | 250,000 **local** |
| Enchanting | 50% 9 sugar cane; 20% 4 obsidian; 15% Sharpness I/Power I book; 15% Protection I/Feather Falling I book | 250,000 **local** |
| Stone | 35% Efficiency II/Unbreaking I iron shovel; 30% same iron axe; 25% same iron pick; 10% Looting I iron sword | 250,000 **local** |
| Lunch | 25% 4 carrots; 25% 2 glistering melon slices; 25% 2 gold; 25% 2 cocoa | 200,000 **local** |
| Looter | 15% 1 magma cream; 35% 1 fermented spider eye; 25% 2 ink sacs; 25% 3 feathers | 250,000 **local** |
| Ecologist | 40% 6 cow eggs; 30% 5 coal blocks; 20% 2 tamed-wolf eggs; 10% 1 emerald | 250,000 **local** |
| Farmer | 40% 4 of each mushroom; 35% 4 apples; 15% melon block; 10% 2 bones | 200,000 **local** |
| Horseman | 40% 4 hay; 35% saddle; 15% 12 golden carrots; 10% diamond horse armor | 200,000 **local** |
| Trapper | **Local:** equal chances of 4 sticky pistons, 16 logs, TNT minecart or Efficiency III/Unbreaking I stone pick | 250,000 **local** |

Trapper prestige probabilities could not be confirmed from a readable primary screenshot. Kit armor/tool prestige replaces its corresponding leather/stone piece rather than granting duplicate starter gear. Kit selected/upgraded state is independent of profession purchases.

## Verification coverage

The shared Fabric/NeoForge GameTests exercise real command dispatch, crafting-table click/shift-click/ingredient consumption, level-I books, Golden Head consumption, bucket remainders, locks, prerequisite/coin deduction/duplicate rejection, kit selection at match start, max-all on/off without fabricated ownership, both craft-limit modes, exact coin boundary awards and finish transitions. The advanced scenario covers stable/no-repeat Fusion previews and pool resets, independent RNG mode, low-health Strategist boundaries, anvil consumption, Strength percentages, splash targeting, fuel-free Forge conservation, tree cuts, persistent Backpack, The Deep cap, quiet healing, Perun cooldown, Bloodlust tiers, Expert Seal, Vitality/Cleansing, Ambrosia brewing, Miner's Blessing and Dice pool resets.

The progression and advanced fixtures have separate environments so they never contend with another running UHC for its exclusive arena. Advanced coverage also verifies Convenience combined with enchanted tools, ineligible blocks, airborne/Mining Fatigue penalties, and ordinary weaponless-arrow flight inside regular UHC.

The settings scenarios check that a new world starts with both max rules on, so a player with no purchases starts a match with prestiged tier-III Stone Gear and may craft every profession and Extra recipe (Strategist's still need their own conditions), and that switching each rule off restores purchase-based ownership independently. They also craft Iron Economy from 8 raw iron and coal through the **[Craft]** prompt, `/minigames uhc craft` and shift-crafting, and Gold Pack, Quick Pick and Philosopher's Pickaxe from raw metal, mixed raw/ore and ore-block grids in the crafting table.

Coin-award scenarios cover real accepted player damage, first blood and assist expiry, cumulative placement at the 10/5/3 boundaries, real crafting/result clicks, diamond/gold mining, hostile/passive mob deaths, golden-head consumption, both cap modes and multiplier rounding. Border/deathmatch transitions and credited combat-logger kills include once-only and nearby-teammate assertions. Separate duel, Meetup and FinalUHC scenarios verify that those games receive no coin progression.

Run the progression scenario alone on Fabric with the command below; `brainage_minigames:uhc_advanced_crafts` selects the advanced one.

```sh
flock /tmp/brainage-minigames-gametest.lock env \
  'JAVA_TOOL_OPTIONS=-Dfabric-api.gametest.filter=brainage_minigames:uhc_progression' \
  ./gradlew --no-daemon :fabric:runGameTest
```
