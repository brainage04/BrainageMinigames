package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.MatchTeam;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsGame;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsLayout;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsMenus;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsQuickBuy;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Currency;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsUpgrades.Upgrade;
import io.github.brainage04.brainage_minigames.menu.MenuView;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Bed Wars on its bundled maps, pasted into the test level. */
public final class BedWarsGameTest {
    private static final BedWarsGame BED_WARS = Minigames.BED_WARS;
    private static final AtomicInteger NEXT_NAME = new AtomicInteger();

    /**
     * Every map holds, for each of its teams, a bed on solid ground with room for both halves, a
     * standable spawn, both shopkeepers and an island generator inside the team's base; eight-team maps
     * have four diamond and four emerald generators, four-team maps four and two, two-team maps two and
     * two. Islands are apart: between each spawn and the middle there is void to bridge.
     */
    public void everyMapHoldsBedsShopsAndGeneratorsForItsTeams(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        List<Identifier> maps = MapArena.maps(server, "bedwars");
        List<Integer> sizes = new ArrayList<>();
        for (Identifier map : maps) {
            MapArena arena = MapArena.open(context.getLevel(), map);
            try {
                ServerLevel level = arena.level();
                int teams = arena.teamSlots();
                sizes.add(teams);
                BedWarsLayout layout = BedWarsLayout.read(arena, teams, true);
                Map<Integer, int[]> generators = Map.of(8, new int[] {4, 4}, 4, new int[] {4, 2}, 2, new int[] {2, 2});
                assertTrue(generators.containsKey(teams), map + " is for " + teams + " teams.");
                assertEquals(generators.get(teams)[0], layout.diamonds().size(), map + " diamond generators");
                assertEquals(generators.get(teams)[1], layout.emeralds().size(), map + " emerald generators");
                for (Vec3 generator : concat(layout.diamonds(), layout.emeralds())) {
                    BlockPos pos = BlockPos.containing(generator);
                    assertTrue(level.getBlockState(pos).isAir() && solid(level, pos.below()), map + ": generator at " + pos + " is not on a block.");
                }
                Vec3 middle = layout.emeralds().getFirst();
                for (int team = 1; team <= teams; team++) {
                    BedWarsLayout.Bed bed = layout.beds().get(team).getFirst();
                    assertTrue(level.getBlockState(bed.foot()).isAir() && level.getBlockState(bed.head()).isAir()
                                    && solid(level, bed.foot().below()) && solid(level, bed.head().below()),
                            map + ": bed " + team + " has no room on solid ground.");
                    Arena.Spawn spawn = arena.spawnsOf(team).getFirst();
                    BlockPos feet = BlockPos.containing(spawn.position());
                    assertTrue(level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir() && solid(level, feet.below()),
                            map + ": spawn " + team + " cannot be stood on.");
                    for (Vec3 point : concat(List.of(spawn.position(), Vec3.atCenterOf(bed.foot()), layout.forges().get(team).getFirst()),
                            List.of(layout.shops().get(team).getFirst().position(), layout.upgrades().get(team).getFirst().position()))) {
                        assertTrue(layout.inBase(team, point), map + ": " + point + " of team " + team + " is outside its base.");
                    }
                    Vec3 half = spawn.position().add(middle).scale(0.5);
                    boolean gap = false;
                    for (double step = 0.3; step <= 0.7 && !gap; step += 0.05) {
                        Vec3 at = spawn.position().lerp(middle, step);
                        BlockPos column = BlockPos.containing(at.x, spawn.position().y - 1, at.z);
                        gap = level.getBlockState(column).isAir() && level.getBlockState(column.below(4)).isAir();
                    }
                    assertTrue(gap, map + ": team " + team + " can walk to the middle without bridging (" + half + ").");
                }
            } finally {
                arena.close();
            }
        }
        assertTrue(sizes.contains(8) && sizes.contains(4) && sizes.contains(2),
                "Expected an eight-, a four- and a two-team map, found " + sizes + ".");
        context.succeed();
    }

    /**
     * A team can't break its own bed; breaking the enemy's takes both halves and is announced. While its
     * bed stands a killed player watches, then respawns at their base, and their killer takes their iron;
     * once it is gone their next death is a final kill and ends the match.
     */
    public void aBrokenBedMakesTheNextDeathAFinalKill(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        configure(server, 1, 360, 600);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, "outpost", "1v1");
        resetSettings(server);
        TestPlayers.ChatPlayer red = players.get(0);
        TestPlayers.ChatPlayer blue = players.get(1);
        MatchManager.join(red, match, 1);
        MatchManager.join(blue, match, 2);
        ServerLevel level = match.arena().level();
        context.startSequence()
                .thenExecuteAfter(3, () -> run(match, players, () -> {
                    assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                    BedWarsLayout layout = BED_WARS.layout(match).orElseThrow();
                    BedWarsLayout.Bed redBed = layout.beds().get(1).getFirst();
                    BedWarsLayout.Bed blueBed = layout.beds().get(2).getFirst();
                    assertTrue(level.getBlockState(redBed.foot()).is(Blocks.BED.red()) && level.getBlockState(redBed.head()).is(Blocks.BED.red()),
                            "Expected red's bed in place.");
                    assertTrue(level.getBlockState(blueBed.head()).is(Blocks.BED.blue()), "Expected blue's bed in place.");
                    assertTrue(red.getInventory().contains(stack -> stack.is(Items.WOODEN_SWORD)), "Expected a wooden sword.");
                    assertTrue(red.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET), "Expected a leather helmet.");
                    teleport(red, Vec3.atBottomCenterOf(redBed.foot()).add(1, 0, 0));
                    assertTrue(!red.gameMode.destroyBlock(redBed.foot()), "A player must not break their own bed.");
                    assertTrue(BED_WARS.bedStanding(match, 1), "Expected red's bed to stand.");
                    teleport(red, Vec3.atBottomCenterOf(blueBed.foot()).add(1, 0, 0));
                    red.gameMode.destroyBlock(blueBed.head());
                    assertTrue(level.getBlockState(blueBed.foot()).isAir() && level.getBlockState(blueBed.head()).isAir(),
                            "Expected both halves of blue's bed gone.");
                    assertTrue(!BED_WARS.bedStanding(match, 2), "Expected blue's bed broken.");
                    assertTrue(contains(blue.messages, "Blue Bed was destroyed by " + red.getScoreboardName()),
                            "Expected the bed destruction announced: " + blue.messages);
                    assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(blueBed.foot()).inflate(3), item -> item.getItem().is(net.minecraft.tags.ItemTags.BEDS)).isEmpty(),
                            "A broken bed drops nothing.");
                    red.getInventory().add(new ItemStack(Items.IRON_INGOT, 5));
                    kill(red, blue);
                }))
                .thenExecuteAfter(2, () -> run(match, players, () -> {
                    assertTrue(BED_WARS.isRespawning(match, red.getUUID()), "Expected red waiting to respawn.");
                    assertEquals(GameType.SPECTATOR, red.gameMode.getGameModeForPlayer(), "red's game mode while waiting");
                    assertEquals(5, blue.getInventory().countItem(Items.IRON_INGOT), "iron blue took from red");
                }))
                .thenExecuteAfter(25, () -> run(match, players, () -> {
                    assertTrue(!BED_WARS.isRespawning(match, red.getUUID()), "Expected red respawned after a second.");
                    assertEquals(GameType.SURVIVAL, red.gameMode.getGameModeForPlayer(), "red's game mode after respawning");
                    assertTrue(red.position().distanceTo(((MapArena) match.arena()).spawnsOf(1).getFirst().position()) < 2.0,
                            "Expected red back at its spawn, found at " + red.position() + ".");
                    kill(blue, red);
                }))
                .thenExecuteAfter(2, () -> run(match, players, () -> {
                    assertEquals(MatchPhase.ENDED, match.phase(), "phase after blue's final kill");
                    assertEquals(List.of(team(match, red)), match.winners(), "winners");
                    assertTrue(contains(red.messages, "FINAL KILL!"), "Expected a final kill: " + red.messages);
                    assertEquals(1, BED_WARS.finalKills(match, red.getUUID()), "red's final kills");
                    context.succeed();
                }, true));
    }

    /**
     * The Item Shop opens from the shopkeeper on the captured Quick Buy, sells at Hypixel's Solo prices
     * through its buttons (wool in the team's colour, a stone sword in place of the wooden one,
     * permanent iron armour, tiered pickaxes), and sneak-clicking adds an item to Quick Buy. Team
     * upgrades put Sharpness and Protection on the team's gear and a trap goes off on the first enemy in
     * the base. After a death armour stays, the pickaxe loses a tier and the bought sword is gone.
     */
    public void theShopSellsAtHypixelPricesAndUpgradesTheTeam(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        configure(server, 1, 360, 600);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, "outpost", "1v1");
        resetSettings(server);
        TestPlayers.ChatPlayer red = players.get(0);
        TestPlayers.ChatPlayer blue = players.get(1);
        BedWarsQuickBuy.reset(server, red.getUUID());
        MatchManager.join(red, match, 1);
        MatchManager.join(blue, match, 2);
        context.startSequence()
                .thenExecuteAfter(3, () -> run(match, players, () -> {
                    assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                    red.getInventory().add(new ItemStack(Items.IRON_INGOT, 64));
                    red.getInventory().add(new ItemStack(Items.GOLD_INGOT, 30));
                    red.getInventory().add(new ItemStack(Items.DIAMOND, 10));
                    Villager shop = shopkeeper(match, red, "ITEM SHOP");
                    teleport(red, shop.position().add(1, 0, 0));
                    red.interactOn(shop, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    assertEquals("Quick Buy", title(red), "menu");
                    List<String> quick = new ArrayList<>();
                    for (int slot : io.github.brainage04.brainage_minigames.menu.Menu.inner(2, 4)) quick.add(nameAt(red, slot));
                    assertEquals(List.of("Wool", "Stone Sword", "Empty slot!", "Wooden Axe (Efficiency I)", "Bow", "Bridge Egg", "TNT",
                            "Wood", "Iron Sword", "Permanent Iron Armor", "Wooden Pickaxe (Efficiency I)", "Arrow", "Jump V Potion (45 seconds)",
                            "Golden Apple", "End Stone", "Diamond Sword", "Ender Pearl", "Permanent Shears", "Bow (Power I, Punch I)",
                            "Invisibility Potion (30 seconds)", "Fireball"), quick, "the captured Quick Buy");
                    clickNamed(red, "Wool", ContainerInput.PICKUP);
                    assertEquals(16, red.getInventory().countItem(Items.WOOL.red()), "red wool bought");
                    assertEquals(60, red.getInventory().countItem(Items.IRON_INGOT), "iron after wool (4)");
                    clickNamed(red, "Stone Sword", ContainerInput.PICKUP);
                    assertTrue(!red.getInventory().contains(stack -> stack.is(Items.WOODEN_SWORD)) && red.getInventory().contains(stack -> stack.is(Items.STONE_SWORD)),
                            "Expected the stone sword in place of the wooden one.");
                    assertEquals(50, red.getInventory().countItem(Items.IRON_INGOT), "iron after the stone sword (10)");
                    clickNamed(red, "Permanent Iron Armor", ContainerInput.PICKUP);
                    assertTrue(red.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS) && red.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS),
                            "Expected iron leggings and boots worn.");
                    assertEquals(18, red.getInventory().countItem(Items.GOLD_INGOT), "gold after iron armour (12)");
                    clickNamed(red, "Wooden Pickaxe (Efficiency I)", ContainerInput.PICKUP);
                    clickNamed(red, "Iron Pickaxe (Efficiency II)", ContainerInput.PICKUP);
                    assertTrue(red.getInventory().contains(stack -> stack.is(Items.IRON_PICKAXE)) && !red.getInventory().contains(stack -> stack.is(Items.WOODEN_PICKAXE)),
                            "Expected the pickaxe upgraded to iron.");
                    assertEquals(30, red.getInventory().countItem(Items.IRON_INGOT), "iron after two pickaxe tiers (10 + 10)");
                    // Blocks tab (no obsidian in a two-team match, as Hypixel's 4v4), then sneak-click packed ice into the
                    // empty Quick Buy slot.
                    click(red, 1, 0, ContainerInput.PICKUP);
                    assertEquals("Blocks", title(red), "menu after the Blocks tab");
                    assertTrue(!hasNamed(red, "Obsidian"), "Expected no obsidian for sale with two teams.");
                    clickNamed(red, "Packed Ice", ContainerInput.QUICK_MOVE);
                    assertEquals(BedWarsMenus.ADDING_TITLE, title(red), "menu after sneak-clicking packed ice");
                    click(red, io.github.brainage04.brainage_minigames.menu.Menu.inner(2, 4)[2], 0, ContainerInput.PICKUP);
                    assertEquals("packed_ice", BedWarsQuickBuy.get(server, red.getUUID()).get(2), "Quick Buy slot 3");
                    assertEquals("Blocks", title(red), "back in the Blocks tab");
                    red.closeContainer();
                    Villager upgrades = shopkeeper(match, red, "TEAM UPGRADES");
                    teleport(red, upgrades.position().add(1, 0, 0));
                    red.interactOn(upgrades, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    assertEquals(BedWarsMenus.UPGRADES_TITLE, title(red), "menu");
                    clickNamed(red, "Sharpened Swords", ContainerInput.PICKUP);
                    clickNamed(red, "Reinforced Armor I", ContainerInput.PICKUP);
                    clickNamed(red, "It's a trap!", ContainerInput.PICKUP);
                    assertEquals(3, red.getInventory().countItem(Items.DIAMOND), "diamonds after 4 + 2 + 1");
                    assertEquals(1, BED_WARS.upgradeLevel(match, red, Upgrade.REINFORCED_ARMOR), "Reinforced Armor");
                    assertEquals(1, BED_WARS.traps(match, red).size(), "queued traps");
                    var enchantments = red.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                    ItemStack sword = held(red, Items.STONE_SWORD);
                    assertEquals(1, EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.SHARPNESS), sword), "Sharpness");
                    assertEquals(1, EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.PROTECTION),
                            red.getItemBySlot(EquipmentSlot.LEGS)), "Protection");
                    red.closeContainer();
                    teleport(blue, ((MapArena) match.arena()).spawnsOf(1).getFirst().position());
                }))
                .thenExecuteAfter(12, () -> run(match, players, () -> {
                    assertTrue(blue.hasEffect(MobEffects.BLINDNESS) && blue.hasEffect(MobEffects.SLOWNESS), "Expected the trap on blue.");
                    assertTrue(BED_WARS.traps(match, red).isEmpty(), "Expected the trap used up.");
                    teleport(blue, ((MapArena) match.arena()).spawnsOf(2).getFirst().position());
                    kill(red, blue);
                }))
                .thenExecuteAfter(30, () -> run(match, players, () -> {
                    assertEquals(GameType.SURVIVAL, red.gameMode.getGameModeForPlayer(), "red respawned");
                    assertTrue(red.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS), "Expected the permanent iron armour kept.");
                    assertTrue(red.getInventory().contains(stack -> stack.is(Items.WOODEN_PICKAXE)), "Expected the pickaxe down a tier.");
                    assertTrue(!red.getInventory().contains(stack -> stack.is(Items.STONE_SWORD)) && red.getInventory().contains(stack -> stack.is(Items.WOODEN_SWORD)),
                            "Expected a wooden sword again.");
                    var enchantments = red.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                    ItemStack sword = held(red, Items.WOODEN_SWORD);
                    assertEquals(1, EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.SHARPNESS), sword),
                            "Sharpness on the new wooden sword");
                    BedWarsQuickBuy.reset(server, red.getUUID());
                    context.succeed();
                }, true));
    }

    /**
     * The island generator drops iron at once; the diamond and emerald generators gain tiers every
     * {@code event_seconds} in Hypixel's order, every bed breaks at bed destruction and Sudden Death
     * sends each team's dragon.
     */
    public void generatorsUpgradeOnScheduleAndSuddenDeathSendsDragons(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        configure(server, 1, 1, 1);
        List<TestPlayers.ChatPlayer> players = players(context, 4);
        Match match = open(context, "quarry", "1v1v1v1");
        resetSettings(server);
        for (int index = 0; index < 4; index++) MatchManager.join(players.get(index), match, index + 1);
        ServerLevel level = match.arena().level();
        context.startSequence()
                .thenExecuteAfter(15, () -> run(match, players, () -> {
                    assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                    Vec3 forge = BED_WARS.layout(match).orElseThrow().forges().get(1).getFirst();
                    assertTrue(!level.getEntitiesOfClass(ItemEntity.class, new AABB(forge, forge).inflate(2), item -> item.getItem().is(Items.IRON_INGOT)).isEmpty(),
                            "Expected iron from red's island generator.");
                    assertEquals(1, BED_WARS.generatorTier(match, Currency.DIAMOND), "diamond tier before a second");
                }))
                .thenExecuteAfter(20, () -> run(match, players, () -> {
                    assertEquals(2, BED_WARS.generatorTier(match, Currency.DIAMOND), "diamond tier after a second");
                    assertEquals(1, BED_WARS.generatorTier(match, Currency.EMERALD), "emerald tier after a second");
                    assertTrue(contains(players.getFirst().messages, "Diamond Generators have been upgraded to Tier II"),
                            "Expected the upgrade announced: " + players.getFirst().messages);
                }))
                .thenExecuteAfter(60, () -> run(match, players, () -> {
                    assertEquals(3, BED_WARS.generatorTier(match, Currency.DIAMOND), "diamond tier after four seconds");
                    assertEquals(3, BED_WARS.generatorTier(match, Currency.EMERALD), "emerald tier after four seconds");
                }))
                .thenExecuteAfter(20, () -> run(match, players, () -> {
                    for (int team = 1; team <= 4; team++) assertTrue(!BED_WARS.bedStanding(match, team), "Expected bed " + team + " gone.");
                    assertTrue(contains(players.getFirst().messages, "All beds have been destroyed!"), "Expected bed destruction announced.");
                }))
                .thenExecuteAfter(25, () -> run(match, players, () -> {
                    List<EnderDragon> dragons = level.getEntitiesOfClass(EnderDragon.class, AABB.of(((MapArena) match.arena()).bounds()).inflate(64));
                    assertEquals(4, dragons.size(), "dragons at Sudden Death, one per team");
                    context.succeed();
                }, true));
    }

    /**
     * Only placed blocks break; nobody builds on a generator. TNT lights as it is placed and blows up
     * placed blocks around it but not blast-proof glass, the map or a bed; a fireball blows up placed wool.
     */
    public void explosionsBreakOnlyPlacedBlocksAndSpareGlassAndBeds(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        configure(server, 1, 360, 600);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, "outpost", "1v1");
        resetSettings(server);
        TestPlayers.ChatPlayer red = players.get(0);
        TestPlayers.ChatPlayer blue = players.get(1);
        MatchManager.join(red, match, 1);
        MatchManager.join(blue, match, 2);
        ServerLevel level = match.arena().level();
        BlockPos[] spots = new BlockPos[4];
        context.startSequence()
                .thenExecuteAfter(3, () -> run(match, players, () -> {
                    BedWarsLayout layout = BED_WARS.layout(match).orElseThrow();
                    BedWarsLayout.Bed bed = layout.beds().get(1).getFirst();
                    BlockPos floor = BlockPos.containing(((MapArena) match.arena()).spawnsOf(1).getFirst().position()).below();
                    assertTrue(!red.gameMode.destroyBlock(floor), "Expected a map block to hold.");
                    BlockPos forge = BlockPos.containing(layout.forges().get(1).getFirst());
                    assertTrue(!MatchManager.allowPlace(red, forge, Blocks.WOOL.red().defaultBlockState()), "Expected no building on the generator.");
                    // Wool and glass around red's bed, then blue's TNT beside it.
                    Direction side = bed.facing().getClockWise();
                    spots[0] = bed.foot().relative(side);
                    spots[1] = bed.foot().relative(side.getOpposite());
                    spots[2] = bed.foot().relative(bed.facing().getOpposite(), 2);
                    place(red, spots[0], new ItemStack(Items.WOOL.red()));
                    place(red, spots[1], new ItemStack(Items.STAINED_GLASS.red()));
                    place(red, spots[2], new ItemStack(Items.WOOL.red()));
                    assertTrue(level.getBlockState(spots[0]).is(Blocks.WOOL.red()) && level.getBlockState(spots[1]).is(Blocks.STAINED_GLASS.red()),
                            "Expected red's blocks placed.");
                    assertTrue(red.gameMode.destroyBlock(spots[2]), "Expected a placed block to break.");
                    place(red, spots[2], new ItemStack(Items.WOOL.red()));
                    spots[3] = spots[0].relative(side);
                    teleport(blue, Vec3.atBottomCenterOf(spots[3].relative(side, 3)));
                    place(blue, spots[3], new ItemStack(Items.TNT));
                    assertTrue(level.getBlockState(spots[3]).isAir(), "Expected the TNT lit as it was placed.");
                }))
                .thenExecuteAfter(70, () -> run(match, players, () -> {
                    BedWarsLayout.Bed bed = BED_WARS.layout(match).orElseThrow().beds().get(1).getFirst();
                    assertTrue(level.getBlockState(spots[0]).isAir(), "Expected the wool beside the TNT blown up.");
                    assertTrue(level.getBlockState(spots[1]).is(Blocks.STAINED_GLASS.red()), "Expected blast-proof glass to hold.");
                    assertTrue(level.getBlockState(bed.foot()).is(Blocks.BED.red()) && BED_WARS.bedStanding(match, 1), "Expected the bed to hold.");
                    assertTrue(!level.getBlockState(bed.foot().below()).isAir(), "Expected the map under the bed to hold.");
                    // Wool two blocks behind blue's spawn, and a fireball at it.
                    Arena.Spawn spawn = ((MapArena) match.arena()).spawnsOf(2).getFirst();
                    Direction back = Direction.fromYRot(spawn.yaw()).getOpposite();
                    BlockPos wall = BlockPos.containing(spawn.position()).relative(back, 2);
                    teleport(blue, spawn.position());
                    place(blue, wall, new ItemStack(Items.WOOL.blue()));
                    assertTrue(level.getBlockState(wall).is(Blocks.WOOL.blue()), "Expected blue's wool placed.");
                    spots[0] = wall;
                    blue.snapTo(blue.getX(), blue.getY(), blue.getZ(), back.toYRot(), 20.0F);
                    ItemStack fireball = BedWarsShop.stack(BedWarsShop.find("fireball").orElseThrow(), net.minecraft.world.item.DyeColor.BLUE, blue.registryAccess());
                    blue.setItemInHand(InteractionHand.MAIN_HAND, fireball);
                    blue.gameMode.useItem(blue, level, blue.getMainHandItem(), InteractionHand.MAIN_HAND);
                    assertTrue(!level.getEntitiesOfClass(LargeFireball.class, blue.getBoundingBox().inflate(4)).isEmpty(), "Expected a fireball launched.");
                }))
                .thenExecuteAfter(20, () -> run(match, players, () -> {
                    assertTrue(level.getBlockState(spots[0]).isAir(), "Expected the fireball to blow up the wool it hit at " + spots[0]
                            + "; fireballs: " + level.getEntitiesOfClass(LargeFireball.class, AABB.of(((MapArena) match.arena()).bounds()).inflate(50))
                                    .stream().map(Entity::position).toList() + ", blue at " + blue.position());
                    context.succeed();
                }, true));
    }

    /**
     * The utility items: a bridge egg leaves a trail of the team's wool, a pop-up tower builds a wool
     * tower with a ladder, a Dream Defender golem never hurts its team, a bedbug lands as silverfish, and
     * Magic Milk keeps a trap from going off.
     */
    public void utilityItemsBuildSummonAndDodgeTraps(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        configure(server, 1, 360, 600);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, "outpost", "1v1");
        resetSettings(server);
        TestPlayers.ChatPlayer red = players.get(0);
        TestPlayers.ChatPlayer blue = players.get(1);
        MatchManager.join(red, match, 1);
        MatchManager.join(blue, match, 2);
        ServerLevel level = match.arena().level();
        int[] woolBefore = new int[1];
        context.startSequence()
                .thenExecuteAfter(3, () -> run(match, players, () -> {
                    Vec3 spawn = ((MapArena) match.arena()).spawnsOf(1).getFirst().position();
                    woolBefore[0] = countBlocks(level, AABB.of(((MapArena) match.arena()).bounds()), Blocks.WOOL.red());
                    teleport(red, spawn);
                    red.snapTo(spawn.x, spawn.y, spawn.z, 90.0F, -10.0F);
                    use(red, "bridge_egg");
                    // A bedbug thrown at the floor and Magic Milk for blue, then a trap for red's base.
                    teleport(blue, ((MapArena) match.arena()).spawnsOf(2).getFirst().position());
                    blue.snapTo(blue.getX(), blue.getY(), blue.getZ(), 0.0F, 80.0F);
                    use(blue, "bedbug");
                    use(blue, "magic_milk");
                    red.getInventory().add(new ItemStack(Items.DIAMOND, 1));
                    Villager upgrades = shopkeeper(match, red, "TEAM UPGRADES");
                    teleport(red, upgrades.position().add(1, 0, 0));
                    red.interactOn(upgrades, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    clickNamed(red, "Miner Fatigue Trap", ContainerInput.PICKUP);
                    red.closeContainer();
                }))
                .thenExecuteAfter(25, () -> run(match, players, () -> {
                    int wool = countBlocks(level, AABB.of(((MapArena) match.arena()).bounds()), Blocks.WOOL.red());
                    assertTrue(wool >= woolBefore[0] + 9, "Expected a bridge egg trail of red wool, found " + (wool - woolBefore[0]) + " new blocks.");
                    assertTrue(!level.getEntitiesOfClass(Silverfish.class, blue.getBoundingBox().inflate(6)).isEmpty(), "Expected bedbug silverfish.");
                    // Blue under Magic Milk walks into red's base: the trap stays queued.
                    teleport(blue, ((MapArena) match.arena()).spawnsOf(1).getFirst().position());
                }))
                .thenExecuteAfter(12, () -> run(match, players, () -> {
                    assertTrue(!blue.hasEffect(MobEffects.MINING_FATIGUE) && BED_WARS.traps(match, red).size() == 1,
                            "Expected Magic Milk to keep the trap from going off.");
                    teleport(blue, ((MapArena) match.arena()).spawnsOf(2).getFirst().position());
                    // A pop-up tower and a Dream Defender on red's island.
                    Vec3 spawn = ((MapArena) match.arena()).spawnsOf(1).getFirst().position();
                    teleport(red, spawn);
                    red.snapTo(spawn.x, spawn.y, spawn.z, -90.0F, 60.0F);
                    int before = countBlocks(level, red.getBoundingBox().inflate(6), Blocks.WOOL.red());
                    use(red, "popup_tower");
                    int after = countBlocks(level, red.getBoundingBox().inflate(6), Blocks.WOOL.red());
                    assertTrue(after - before >= 40, "Expected a pop-up tower of red wool, found " + (after - before) + " blocks.");
                    assertTrue(countBlocks(level, red.getBoundingBox().inflate(6), Blocks.LADDER) >= 4, "Expected the tower's ladder.");
                    red.snapTo(spawn.x, spawn.y, spawn.z, 90.0F, 60.0F);
                    use(red, "dream_defender");
                    List<IronGolem> golems = level.getEntitiesOfClass(IronGolem.class, red.getBoundingBox().inflate(8));
                    assertEquals(1, golems.size(), "Dream Defenders");
                    assertTrue(!MatchManager.allowDamage(red, red.damageSources().mobAttack(golems.getFirst())),
                            "Expected red's golem unable to hurt red.");
                    assertTrue(MatchManager.allowDamage(blue, blue.damageSources().mobAttack(golems.getFirst())),
                            "Expected red's golem able to hurt blue.");
                    context.succeed();
                }, true));
    }

    /**
     * Complete Invisibility: while a player is invisible the others are sent their equipment as empty, armour and
     * held item alike; once it wears off they are sent it again.
     */
    public void invisiblePlayersAreShownWithoutArmour(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        configure(server, 1, 360, 600);
        List<TestPlayers.ChatPlayer> players = new ArrayList<>(players(context, 1));
        var cookie = TestPlayers.cookie("bw" + NEXT_NAME.incrementAndGet());
        ServerPlayer watcher = new ServerPlayer(server, context.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        io.netty.channel.embedded.EmbeddedChannel channel = TestPlayers.connect(watcher, cookie);
        Match match = open(context, "outpost", "1v1");
        resetSettings(server);
        TestPlayers.ChatPlayer red = players.getFirst();
        MatchManager.join(red, match, 1);
        MatchManager.join(watcher, match, 2);
        List<ServerPlayer> everyone = List.of(red, watcher);
        context.startSequence()
                .thenExecuteAfter(3, () -> run(match, everyone, () -> {
                    drain(channel);
                    red.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.INVISIBILITY, 600));
                }))
                .thenExecuteAfter(4, () -> run(match, everyone, () -> {
                    var shown = equipmentSentFor(channel, red);
                    assertTrue(shown != null && shown.getSlots().stream().allMatch(slot -> slot.getSecond().isEmpty())
                                    && shown.getSlots().stream().anyMatch(slot -> slot.getFirst() == EquipmentSlot.HEAD),
                            "Expected red's armour sent as empty while invisible: " + (shown == null ? null : shown.getSlots()));
                    red.removeEffect(MobEffects.INVISIBILITY);
                }))
                .thenExecuteAfter(4, () -> run(match, everyone, () -> {
                    var shown = equipmentSentFor(channel, red);
                    assertTrue(shown != null && shown.getSlots().stream().anyMatch(slot -> slot.getFirst() == EquipmentSlot.HEAD
                                    && slot.getSecond().is(Items.LEATHER_HELMET)),
                            "Expected red's armour sent again once visible: " + (shown == null ? null : shown.getSlots()));
                    context.succeed();
                }, true));
    }

    static void drain(io.netty.channel.embedded.EmbeddedChannel channel) {
        while (channel.readOutbound() != null) {
            // Everything sent so far.
        }
    }

    /** The last equipment packet for {@code player} sent through {@code channel} since it was last read. */
    static net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket equipmentSentFor(
            io.netty.channel.embedded.EmbeddedChannel channel, ServerPlayer player) {
        net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket last = null;
        for (Object sent = channel.readOutbound(); sent != null; sent = channel.readOutbound()) {
            List<Object> packets = new ArrayList<>(List.of(sent));
            if (sent instanceof net.minecraft.network.protocol.game.ClientboundBundlePacket bundle) {
                packets.clear();
                bundle.subPackets().forEach(packets::add);
            }
            for (Object packet : packets) {
                if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket equipment
                        && equipment.getEntity() == player.getId()) {
                    last = equipment;
                }
            }
        }
        return last;
    }

    /**
     * The Quick Buy editor shows the captured layout; "Adding to Quick Buy..." lists every shop item on
     * two pages in Hypixel's order, choosing one fills the slot, a right-click empties it, and the
     * layout is saved per player.
     */
    public void theQuickBuyEditorSavesEachPlayersLayout(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        TestPlayers.ChatPlayer player = players(context, 1).getFirst();
        try {
            BedWarsQuickBuy.reset(server, player.getUUID());
            BedWarsMenus.openEditor(player);
            assertEquals(BedWarsMenus.EDIT_TITLE, title(player), "menu");
            int[] slots = io.github.brainage04.brainage_minigames.menu.Menu.inner(1, 3);
            assertEquals("Wool", nameAt(player, slots[0]), "first Quick Buy slot");
            assertEquals("Empty slot!", nameAt(player, slots[2]), "third Quick Buy slot");
            assertEquals("Fireball", nameAt(player, slots[20]), "last Quick Buy slot");
            click(player, slots[0], 1, ContainerInput.PICKUP);
            assertEquals("", BedWarsQuickBuy.get(server, player.getUUID()).getFirst(), "slot emptied by a right-click");
            click(player, slots[2], 0, ContainerInput.PICKUP);
            assertEquals(BedWarsMenus.ADDING_TITLE, title(player), "menu after choosing a slot");
            List<String> first = new ArrayList<>();
            for (int slot : slots) first.add(nameAt(player, slot));
            assertEquals(List.of("Wool", "Hardened Clay", "Blast-Proof Glass", "End Stone", "Ladder", "Wood", "Obsidian",
                    "Packed Ice", "Stone Sword", "Iron Sword", "Diamond Sword", "Stick (Knockback I)", "Permanent Chainmail Armor",
                    "Permanent Iron Armor", "Permanent Diamond Armor", "Wooden Pickaxe (Efficiency I)", "Wooden Axe (Efficiency I)",
                    "Permanent Shears", "Arrow", "Bow", "Bow (Power I)"), first, "the first page, as Hypixel's");
            click(player, view(player).menu().bottom() + 8, 0, ContainerInput.PICKUP);
            assertEquals("(2/2) " + BedWarsMenus.ADDING_TITLE, title(player), "second page");
            clickNamed(player, "Compact Pop-up Tower", ContainerInput.PICKUP);
            assertEquals(BedWarsMenus.EDIT_TITLE, title(player), "back in the editor");
            assertEquals("popup_tower", BedWarsQuickBuy.get(server, player.getUUID()).get(2), "slot filled");
            assertEquals("Compact Pop-up Tower", nameAt(player, slots[2]), "slot shown");
            BedWarsQuickBuy.reset(server, player.getUUID());
            context.succeed();
        } finally {
            server.getPlayerList().remove(player);
        }
    }

    // ---------------------------------------------------------------- helpers

    static Villager shopkeeper(Match match, ServerPlayer player, String name) {
        int team = match.teamOf(player.getUUID()).map(MatchTeam::number).orElseThrow();
        Vec3 spawn = ((MapArena) match.arena()).spawnsOf(team).getFirst().position();
        return match.arena().level().getEntitiesOfClass(Villager.class, new AABB(spawn, spawn).inflate(12),
                        villager -> villager.getCustomName() != null && villager.getCustomName().getString().equals(name))
                .stream().findFirst().orElseThrow(() -> failure("No " + name + " shopkeeper near team " + team + "'s spawn."));
    }

    /** Uses a Bed Wars item with an ability from the main hand, as a client right-clicking. */
    static void use(ServerPlayer player, String ability) {
        ItemStack stack = BedWarsShop.stack(BedWarsShop.find(ability).orElseThrow(), net.minecraft.world.item.DyeColor.WHITE, player.registryAccess());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.gameMode.useItem(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
    }

    /** Places {@code stack} at {@code pos} against the block below it, as a client would. */
    static void place(ServerPlayer player, BlockPos pos, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos below = pos.below();
        player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false));
    }

    static int countBlocks(ServerLevel level, AABB area, net.minecraft.world.level.block.Block block) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(area.minX, area.minY, area.minZ),
                BlockPos.containing(area.maxX, area.maxY, area.maxZ))) {
            if (level.getBlockState(pos).is(block)) count++;
        }
        return count;
    }

    static <T> List<T> concat(List<? extends T> first, List<? extends T> second) {
        List<T> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    static void click(ServerPlayer player, int slot, int button, ContainerInput input) {
        var menu = player.containerMenu;
        player.connection.handleContainerClick(new ServerboundContainerClickPacket(
                menu.containerId, menu.getStateId(), (short) slot, (byte) button, input,
                Int2ObjectMaps.emptyMap(), HashedStack.EMPTY));
    }

    static void clickNamed(ServerPlayer player, String name, ContainerInput input) {
        MenuView view = view(player);
        for (int slot = 0; slot < view.menu().size(); slot++) {
            if (name.equals(nameAt(player, slot))) {
                click(player, slot, 0, input);
                return;
            }
        }
        throw failure("No \"" + name + "\" in " + title(player) + ".");
    }

    static ItemStack held(ServerPlayer player, Item item) {
        for (ItemStack stack : player.getInventory()) if (stack.is(item)) return stack;
        throw failure("Expected " + player.getScoreboardName() + " to carry " + item + ".");
    }

    static boolean hasNamed(ServerPlayer player, String name) {
        MenuView view = view(player);
        for (int slot = 0; slot < view.menu().size(); slot++) {
            if (name.equals(nameAt(player, slot))) return true;
        }
        return false;
    }

    static String nameAt(ServerPlayer player, int slot) {
        ItemStack icon = view(player).menu().icon(slot);
        Component name = icon.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        return name == null ? "" : name.getString();
    }

    static MenuView view(ServerPlayer player) {
        if (player.containerMenu instanceof MenuView view) return view;
        throw failure("Expected a menu, found " + player.containerMenu + ".");
    }

    static String title(ServerPlayer player) {
        return view(player).menu().title().getString();
    }

    static boolean solid(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    static void kill(ServerPlayer victim, ServerPlayer killer) {
        victim.invulnerableTime = 0;
        victim.hurtServer(victim.level(), victim.damageSources().playerAttack(killer), 1000.0F);
    }

    static void run(Match match, List<? extends ServerPlayer> players, Runnable step) {
        run(match, players, step, false);
    }

    static void run(Match match, List<? extends ServerPlayer> players, Runnable step, boolean last) {
        boolean failed = true;
        try {
            step.run();
            failed = false;
        } finally {
            if (failed || last) finish(match, players);
        }
    }

    static Match open(GameTestHelper context, String map, String layout) throws MatchException {
        return open(context, BED_WARS, map, layout);
    }

    static Match open(GameTestHelper context, BedWarsGame game, String map, String layout) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        Identifier id = MapArena.maps(server, game.mapDirectory()).stream()
                .filter(candidate -> candidate.getPath().endsWith("/" + map))
                .findFirst()
                .orElseThrow(() -> failure("Missing " + game.displayName() + " map " + map + "."));
        return MatchManager.open(server, game, TeamLayout.parse(layout).orElseThrow(), null,
                (unused, settings) -> MapArena.open(context.getLevel(), id));
    }

    static List<TestPlayers.ChatPlayer> players(GameTestHelper context, int count) {
        List<TestPlayers.ChatPlayer> players = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            players.add(TestPlayers.chat(context, "bw" + NEXT_NAME.incrementAndGet()));
        }
        return players;
    }

    static void finish(Match match, List<? extends ServerPlayer> players) {
        MatchManager.stop(match);
        for (ServerPlayer player : players) {
            var playerList = player.level().getServer().getPlayerList();
            if (playerList.getPlayer(player.getUUID()) == player) playerList.remove(player);
        }
    }

    /** Settings are read when a match opens, so they can be reset straight afterwards. */
    static void configure(MinecraftServer server, int respawnSeconds, int eventSeconds, int suddenDeathSeconds) {
        configure(server, BED_WARS, respawnSeconds, eventSeconds, suddenDeathSeconds);
    }

    static void configure(MinecraftServer server, BedWarsGame game, int respawnSeconds, int eventSeconds, int suddenDeathSeconds) {
        SettingsStorage.set(server, game, game.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), 0);
        SettingsStorage.set(server, game, BedWarsGame.RESPAWN_SECONDS, respawnSeconds);
        SettingsStorage.set(server, game, BedWarsGame.EVENT_SECONDS, eventSeconds);
        SettingsStorage.set(server, game, BedWarsGame.SUDDEN_DEATH_SECONDS, suddenDeathSeconds);
    }

    static void resetSettings(MinecraftServer server) {
        resetSettings(server, BED_WARS);
    }

    static void resetSettings(MinecraftServer server, BedWarsGame game) {
        for (GameSetting setting : game.settings()) SettingsStorage.reset(server, game, setting);
    }

    static MatchTeam team(Match match, ServerPlayer player) {
        return match.teamOf(player.getUUID()).orElseThrow(() -> failure("Expected " + player.getScoreboardName() + " on a team."));
    }

    static boolean contains(List<Component> messages, String text) {
        return messages.stream().anyMatch(message -> message.getString().contains(text));
    }

    static void teleport(ServerPlayer player, Vec3 position) {
        player.teleportTo(position.x, position.y, position.z);
    }

    static void assertEquals(Object expected, Object actual, String description) {
        if (!expected.equals(actual)) throw failure("Expected " + description + " " + expected + ", found " + actual + ".");
    }

    static void assertTrue(boolean condition, String message) {
        if (!condition) throw failure(message);
    }

    static GameTestAssertException failure(String message) {
        return new GameTestAssertException(Component.literal(message), 0);
    }
}
