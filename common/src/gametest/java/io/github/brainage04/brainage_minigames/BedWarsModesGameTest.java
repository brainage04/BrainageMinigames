package io.github.brainage04.brainage_minigames;

import static io.github.brainage04.brainage_minigames.BedWarsGameTest.assertEquals;
import static io.github.brainage04.brainage_minigames.BedWarsGameTest.assertTrue;
import static io.github.brainage04.brainage_minigames.BedWarsGameTest.clickNamed;
import static io.github.brainage04.brainage_minigames.BedWarsGameTest.configure;
import static io.github.brainage04.brainage_minigames.BedWarsGameTest.contains;
import static io.github.brainage04.brainage_minigames.BedWarsGameTest.failure;
import static io.github.brainage04.brainage_minigames.BedWarsGameTest.hasNamed;
import static io.github.brainage04.brainage_minigames.BedWarsGameTest.held;
import static io.github.brainage04.brainage_minigames.BedWarsGameTest.kill;
import static io.github.brainage04.brainage_minigames.BedWarsGameTest.open;
import static io.github.brainage04.brainage_minigames.BedWarsGameTest.place;
import static io.github.brainage04.brainage_minigames.BedWarsGameTest.players;
import static io.github.brainage04.brainage_minigames.BedWarsGameTest.resetSettings;
import static io.github.brainage04.brainage_minigames.BedWarsGameTest.run;
import static io.github.brainage04.brainage_minigames.BedWarsGameTest.shopkeeper;
import static io.github.brainage04.brainage_minigames.BedWarsGameTest.teleport;
import static io.github.brainage04.brainage_minigames.BedWarsGameTest.title;

import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsGame;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsLayout;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsLucky;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Currency;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsUltimates;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsUltimates.Ultimate;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsUpgrades;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Bed Wars Castle and the Dream modes on their bundled maps. */
public final class BedWarsModesGameTest {
    /**
     * The Castle map holds two teams, each with three named beds (keep and two towers) under beacons,
     * four island generators with an Item Shop, Team Upgrades, Banker and Streak Powers each, base
     * regions named after the beds and launch pads; six diamond and three emerald generators. A team
     * respawns while any of its beds stands; once all three are broken the next death is final.
     */
    public void castleTeamsRespawnUntilAllThreeBedsAreBroken(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        BedWarsGame castle = Minigames.BED_WARS_CASTLE;
        List<Identifier> maps = MapArena.maps(server, castle.mapDirectory());
        assertTrue(!maps.isEmpty(), "Expected a Castle map.");
        for (Identifier map : maps) {
            MapArena arena = MapArena.open(context.getLevel(), map);
            try {
                assertEquals(2, arena.teamSlots(), map + " teams");
                BedWarsLayout layout = BedWarsLayout.read(arena, 2, true);
                assertEquals(6, layout.diamonds().size(), map + " diamond generators");
                assertEquals(3, layout.emeralds().size(), map + " emerald generators");
                for (int team = 1; team <= 2; team++) {
                    assertEquals(List.of("keep", "north_tower", "south_tower"),
                            layout.beds().get(team).stream().map(BedWarsLayout.Bed::name).sorted().toList(), map + " beds of team " + team);
                    assertEquals(4, layout.forges().get(team).size(), map + " island generators of team " + team);
                    assertEquals(4, arena.points("banker_" + team + "_").size(), map + " bankers of team " + team);
                    assertEquals(4, arena.points("streak_" + team + "_").size(), map + " streak NPCs of team " + team);
                    assertEquals(3, arena.points("beacon_" + team + "_").size(), map + " beacons of team " + team);
                    assertTrue(arena.points("pad_" + team + "_").size() >= 2, map + ": team " + team + " has no launch pads.");
                    for (BedWarsLayout.Bed bed : layout.beds().get(team)) {
                        assertTrue(layout.inBase(team, Vec3.atCenterOf(bed.foot())), map + ": bed " + bed.name() + " is outside a base region.");
                        assertTrue(!arena.level().getBlockState(bed.foot().below()).isAir(), map + ": bed " + bed.name() + " floats.");
                    }
                }
            } finally {
                arena.close();
            }
        }
        configure(server, castle, 1, 360, 600);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, castle, "castle", "1v1");
        resetSettings(server, castle);
        TestPlayers.ChatPlayer red = players.get(0);
        TestPlayers.ChatPlayer blue = players.get(1);
        MatchManager.join(red, match, 1);
        MatchManager.join(blue, match, 2);
        ServerLevel level = match.arena().level();
        context.startSequence()
                .thenExecuteAfter(30, () -> run(match, players, () -> {
                    assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                    assertEquals(3, castle.traps(match, red).size(), "free Alarm Traps");
                    List<BedWarsLayout.Bed> blueBeds = castle.layout(match).orElseThrow().beds().get(2);
                    for (BedWarsLayout.Bed bed : blueBeds.subList(0, 2)) {
                        teleport(red, Vec3.atBottomCenterOf(bed.foot()).add(1, 0, 0));
                        red.gameMode.destroyBlock(bed.foot());
                    }
                    assertTrue(castle.bedStanding(match, 2), "Expected blue's last bed to keep blue respawning.");
                    assertTrue(contains(blue.messages, "Blue " + capital(blueBeds.getFirst().name()) + " Bed was destroyed"),
                            "Expected the named bed announced: " + blue.messages);
                    AABB fallen = building(castle, match, 2, blueBeds.getFirst().name());
                    AABB standing = building(castle, match, 2, blueBeds.get(2).name());
                    assertEquals(0, BedWarsGameTest.countBlocks(level, fallen, Blocks.WOOL.blue()), "blue wool left on the fallen bed's building");
                    assertTrue(BedWarsGameTest.countBlocks(level, fallen, Blocks.WOOL.gray()) > 0, "Expected the fallen bed's building's wool grey.");
                    assertTrue(BedWarsGameTest.countBlocks(level, standing, Blocks.WOOL.blue()) > 0, "Expected blue wool on the standing bed's building.");
                    kill(blue, red);
                }))
                .thenExecuteAfter(2, () -> run(match, players, () -> {
                    assertTrue(castle.isRespawning(match, blue.getUUID()), "Expected blue to respawn while a bed stands.");
                    BedWarsLayout.Bed last = castle.layout(match).orElseThrow().beds().get(2).get(2);
                    teleport(red, Vec3.atBottomCenterOf(last.foot()).add(1, 0, 0));
                    red.gameMode.destroyBlock(last.foot());
                    assertTrue(!castle.bedStanding(match, 2), "Expected all of blue's beds gone.");
                }))
                .thenExecuteAfter(25, () -> run(match, players, () -> {
                    assertEquals(GameType.SURVIVAL, blue.gameMode.getGameModeForPlayer(), "blue respawned");
                    kill(blue, red);
                }))
                .thenExecuteAfter(2, () -> run(match, players, () -> {
                    assertEquals(MatchPhase.ENDED, match.phase(), "phase after blue's final kill");
                    context.succeed();
                }, true));
    }

    private static String capital(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1).replace('_', ' ');
    }

    /** The base region of {@code team}'s building that holds its bed {@code bed}. */
    private static AABB building(BedWarsGame castle, Match match, int team, String bed) {
        return castle.layout(match).orElseThrow().bases().get(team).stream().filter(region -> region.name().equals("base_" + team + "_" + bed))
                .findFirst().orElseThrow(() -> failure("No base region for bed " + bed + ".")).box();
    }

    /**
     * Castle's Banker takes deposits from its buttons and pays for what a player can't afford; banking
     * earns streak points, spent at the Streak Powers NPC (Lone Wolf brings wolves). A team can't put
     * TNT next to its own standing bed, and a launch pad throws its team's players.
     */
    public void castleBankerStreakPowersAndLaunchPads(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        BedWarsGame castle = Minigames.BED_WARS_CASTLE;
        configure(server, castle, 1, 360, 600);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, castle, "castle", "1v1");
        resetSettings(server, castle);
        TestPlayers.ChatPlayer red = players.get(0);
        MatchManager.join(red, match, 1);
        MatchManager.join(players.get(1), match, 2);
        ServerLevel level = match.arena().level();
        context.startSequence()
                .thenExecuteAfter(30, () -> run(match, players, () -> {
                    red.getInventory().clearContent();
                    red.getInventory().add(new ItemStack(Items.IRON_INGOT, 64));
                    red.getInventory().add(new ItemStack(Items.DIAMOND, 32));
                    Villager banker = shopkeeper(match, red, "BANKER");
                    teleport(red, banker.position().add(0.5, 0, 0.5));
                    red.interactOn(banker, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    assertEquals("Banker", title(red), "menu");
                    clickNamed(red, "Add 64x Iron to bank", ContainerInput.PICKUP);
                    clickNamed(red, "Add 32x Diamond to bank", ContainerInput.PICKUP);
                    assertEquals(64, castle.banked(match, 1, Currency.IRON), "banked iron");
                    assertEquals(0, red.getInventory().countItem(Items.IRON_INGOT), "iron left on red");
                    assertEquals(34, castle.streakPoints(match, red.getUUID()), "streak points for 64 iron and 32 diamonds");
                    red.closeContainer();
                    Villager shop = shopkeeper(match, red, "ITEM SHOP");
                    red.interactOn(shop, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    clickNamed(red, "Wool", ContainerInput.PICKUP);
                    assertEquals(16, red.getInventory().countItem(Items.WOOL.red()), "wool bought from the bank");
                    assertEquals(60, castle.banked(match, 1, Currency.IRON), "banked iron after the wool");
                    red.closeContainer();
                    Villager streaks = shopkeeper(match, red, "STREAK POWERS");
                    red.interactOn(streaks, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    clickNamed(red, "Lone Wolf", ContainerInput.PICKUP);
                    assertEquals(3, level.getEntitiesOfClass(Wolf.class, red.getBoundingBox().inflate(4)).size(), "Lone Wolf's wolves");
                    assertEquals(4, castle.streakPoints(match, red.getUUID()), "streak points left");
                    BedWarsLayout.Bed keep = castle.layout(match).orElseThrow().beds().get(1).stream()
                            .filter(bed -> bed.name().equals("keep")).findFirst().orElseThrow();
                    assertTrue(!MatchManager.allowPlace(red, keep.foot().relative(keep.facing().getClockWise(), 2), Blocks.TNT.defaultBlockState()),
                            "Expected no TNT next to red's own bed.");
                    MapArena.Point pad = ((MapArena) match.arena()).points("pad_1_").getFirst();
                    teleport(red, pad.position());
                    red.setDeltaMovement(Vec3.ZERO);
                    red.setOnGround(true);
                }))
                .thenExecuteAfter(1, () -> run(match, players, () -> {
                    // A test player has no client to move it, so the throw shows as the velocity the server sent.
                    Vec3 motion = red.getDeltaMovement();
                    assertTrue(motion.horizontalDistance() > 1.5 && motion.y > 0.8, "Expected the launch pad to throw red, moving " + motion + ".");
                    context.succeed();
                }, true));
    }

    /**
     * Rush: generators start at Tier III, every bed is covered in wood, wool and blast-proof glass,
     * players have Speed, potions cost double and obsidian is not sold; wool placed facing the void
     * builds five more, until a left-click with wool turns that off.
     */
    public void rushDefendsBedsAndBuildsBridges(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        BedWarsGame rush = Minigames.BED_WARS_RUSH;
        configure(server, rush, 1, 360, 600);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, rush, "outpost", "1v1");
        resetSettings(server, rush);
        TestPlayers.ChatPlayer red = players.get(0);
        MatchManager.join(red, match, 1);
        MatchManager.join(players.get(1), match, 2);
        ServerLevel level = match.arena().level();
        context.startSequence()
                .thenExecuteAfter(5, () -> run(match, players, () -> {
                    assertEquals(3, rush.generatorTier(match, Currency.DIAMOND), "diamond tier");
                    assertEquals(3, rush.generatorTier(match, Currency.EMERALD), "emerald tier");
                    BedWarsLayout.Bed bed = rush.layout(match).orElseThrow().beds().get(1).getFirst();
                    assertTrue(level.getBlockState(bed.foot().above()).is(Blocks.OAK_PLANKS), "Expected wood over the bed.");
                    assertTrue(level.getBlockState(bed.foot().above(2)).is(Blocks.WOOL.red()), "Expected wool over the wood.");
                    assertTrue(level.getBlockState(bed.foot().above(3)).is(Blocks.STAINED_GLASS.red()), "Expected glass over the wool.");
                    assertTrue(red.hasEffect(MobEffects.SPEED) && red.hasEffect(MobEffects.HASTE), "Expected Speed and Haste.");
                    BedWarsShop.Entry speed = BedWarsShop.find("speed_potion").orElseThrow();
                    assertEquals(2, rush.price(match, red, speed).amount(), "Speed potion price");
                    assertTrue(!rush.sells(match, BedWarsShop.find("obsidian").orElseThrow()), "Expected no obsidian.");
                    // The edge of the island behind the forge, facing out over the void.
                    Arena.Spawn spawn = ((MapArena) match.arena()).spawnsOf(1).getFirst();
                    Direction out = Direction.fromYRot(spawn.yaw()).getOpposite();
                    BlockPos edge = BlockPos.containing(spawn.position()).below();
                    while (!level.getBlockState(edge.relative(out)).isAir()) edge = edge.relative(out);
                    BlockPos first = edge.relative(out);
                    teleport(red, Vec3.atBottomCenterOf(edge.above()));
                    red.snapTo(red.getX(), red.getY(), red.getZ(), out.toYRot(), 60.0F);
                    red.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOOL.red(), 16));
                    red.gameMode.useItemOn(red, level, red.getMainHandItem(), InteractionHand.MAIN_HAND,
                            new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(edge).relative(out, 0.5), out, edge, false));
                    assertTrue(level.getBlockState(first).is(Blocks.WOOL.red()), "Expected the wool placed.");
                    for (int step = 1; step <= 5; step++) {
                        assertTrue(level.getBlockState(first.relative(out, step)).is(Blocks.WOOL.red()),
                                "Expected the bridge built out " + step + " blocks.");
                    }
                    MatchManager.swing(red);
                    BlockPos end = first.relative(out, 5);
                    red.gameMode.useItemOn(red, level, red.getMainHandItem(), InteractionHand.MAIN_HAND,
                            new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(end).relative(out, 0.5), out, end, false));
                    assertTrue(level.getBlockState(end.relative(out)).is(Blocks.WOOL.red()), "Expected the next wool placed.");
                    assertTrue(level.getBlockState(end.relative(out, 2)).isAir(), "Expected no bridge with bridging turned off.");
                    context.succeed();
                }, true));
    }

    /** Voidless: solid ground under the islands, nobody dies to the void, and beds start defended. */
    public void voidlessMapsStandOnGroundWithDefendedBeds(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        BedWarsGame voidless = Minigames.BED_WARS_VOIDLESS;
        List<Identifier> maps = MapArena.maps(server, voidless.mapDirectory());
        assertTrue(maps.size() >= 2, "Expected Voidless maps for Doubles and 4v4v4v4, found " + maps + ".");
        for (Identifier map : maps) {
            MapArena arena = MapArena.open(context.getLevel(), map);
            try {
                Vec3 spawn = arena.spawnsOf(1).getFirst().position();
                Vec3 middle = BedWarsLayout.read(arena, arena.teamSlots(), true).emeralds().getFirst();
                Vec3 half = spawn.add(middle).scale(0.5);
                boolean ground = false;
                for (int y = (int) spawn.y; y > arena.voidY() && !ground; y--) {
                    ground = !arena.level().getBlockState(BlockPos.containing(half.x, y, half.z)).isAir();
                }
                assertTrue(ground, map + ": no ground between the islands at " + half + ".");
            } finally {
                arena.close();
            }
        }
        configure(server, voidless, 1, 360, 600);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, voidless, "quarry", "1v1");
        resetSettings(server, voidless);
        MatchManager.join(players.get(0), match, 1);
        MatchManager.join(players.get(1), match, 2);
        context.startSequence()
                .thenExecuteAfter(5, () -> run(match, players, () -> {
                    BedWarsLayout.Bed bed = voidless.layout(match).orElseThrow().beds().get(1).getFirst();
                    assertTrue(match.arena().level().getBlockState(bed.foot().above()).is(Blocks.OAK_PLANKS), "Expected a defended bed.");
                    context.succeed();
                }, true));
    }

    /** Swappage: when the swap comes, each player takes the place of a player of the other team. */
    public void swappageSwapsTeamsPlayerForPlayer(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        BedWarsGame swappage = Minigames.BED_WARS_SWAPPAGE;
        configure(server, swappage, 1, 360, 600);
        SettingsStorage.set(server, swappage, BedWarsGame.SWAP_MIN_SECONDS, 1);
        SettingsStorage.set(server, swappage, BedWarsGame.SWAP_MAX_SECONDS, 1);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, swappage, "outpost", "1v1");
        resetSettings(server, swappage);
        TestPlayers.ChatPlayer red = players.get(0);
        TestPlayers.ChatPlayer blue = players.get(1);
        MatchManager.join(red, match, 1);
        MatchManager.join(blue, match, 2);
        Vec3[] before = new Vec3[2];
        context.startSequence()
                .thenExecuteAfter(4, () -> run(match, players, () -> {
                    before[0] = red.position();
                    before[1] = blue.position();
                }))
                .thenExecuteAfter(24, () -> run(match, players, () -> {
                    assertTrue(red.position().distanceTo(before[1]) < 1.5 && blue.position().distanceTo(before[0]) < 1.5,
                            "Expected red and blue swapped: red at " + red.position() + ", blue at " + blue.position() + ".");
                    context.succeed();
                }, true));
    }

    /** One Block: no shopkeepers on the tiny islands, and a random item for every player every three seconds. */
    public void oneBlockGivesEveryPlayerARandomItem(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        BedWarsGame oneBlock = Minigames.BED_WARS_ONE_BLOCK;
        configure(server, oneBlock, 1, 360, 600);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, oneBlock, "drift", "1v1");
        resetSettings(server, oneBlock);
        TestPlayers.ChatPlayer red = players.get(0);
        MatchManager.join(red, match, 1);
        MatchManager.join(players.get(1), match, 2);
        int[] items = new int[1];
        context.startSequence()
                .thenExecuteAfter(4, () -> run(match, players, () -> {
                    assertTrue(match.arena().level().getEntitiesOfClass(Villager.class, AABB.of(((MapArena) match.arena()).bounds())).isEmpty(),
                            "Expected no shopkeepers in One Block.");
                    items[0] = red.getInventory().getNonEquipmentItems().stream().mapToInt(ItemStack::getCount).sum();
                }))
                .thenExecuteAfter(64, () -> run(match, players, () -> {
                    int now = red.getInventory().getNonEquipmentItems().stream().mapToInt(ItemStack::getCount).sum();
                    assertTrue(now > items[0], "Expected a random item within three seconds: " + items[0] + " then " + now + ".");
                    context.succeed();
                }, true));
    }

    /** Lucky Blocks: a placed lucky block, broken, is opened: gone, with an item, a strike or zombies for it. */
    public void luckyBlocksOpenWhenBroken(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        BedWarsGame lucky = Minigames.BED_WARS_LUCKY;
        configure(server, lucky, 1, 360, 600);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, lucky, "outpost", "1v1");
        resetSettings(server, lucky);
        TestPlayers.ChatPlayer red = players.get(0);
        MatchManager.join(red, match, 1);
        MatchManager.join(players.get(1), match, 2);
        ServerLevel level = match.arena().level();
        context.startSequence()
                .thenExecuteAfter(4, () -> run(match, players, () -> {
                    Arena.Spawn spawn = ((MapArena) match.arena()).spawnsOf(1).getFirst();
                    BlockPos first = BlockPos.containing(spawn.position()).relative(Direction.fromYRot(spawn.yaw()).getOpposite(), 2);
                    for (int tries = 0; tries < 6; tries++) {
                        // Each try on its own spot: a Miracle Lucky Block may turn into bedrock or call Jerry.
                        BlockPos at = first.relative(Direction.fromYRot(spawn.yaw()).getOpposite(), tries / 3)
                                .relative(Direction.fromYRot(spawn.yaw()).getClockWise(), tries % 3 - 1);
                        red.messages.clear();
                        place(red, at, new ItemStack(Blocks.GLAZED_TERRACOTTA.pick(DyeColor.LIME)));
                        assertTrue(level.getBlockState(at).is(Blocks.GLAZED_TERRACOTTA.pick(DyeColor.LIME)), "Expected the lucky block placed at " + at + ".");
                        red.setHealth(red.getMaxHealth());
                        assertTrue(!red.gameMode.destroyBlock(at), "Opening a lucky block must not break it as a block.");
                        assertTrue(!level.getBlockState(at).is(Blocks.GLAZED_TERRACOTTA.pick(DyeColor.LIME)), "Expected the lucky block opened.");
                        assertTrue(contains(red.messages, "Miracle Lucky Block: "), "Expected what it gave named: " + red.messages);
                        List<net.minecraft.world.entity.Entity> given = level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(at).inflate(2),
                                entity -> entity instanceof ItemEntity || entity instanceof net.minecraft.world.entity.Mob);
                        assertTrue(!given.isEmpty() || red.hasEffect(MobEffects.RESISTANCE) || level.getBlockState(at).is(Blocks.BEDROCK),
                                "Expected the Miracle Lucky Block to give something: " + red.messages);
                        given.forEach(net.minecraft.world.entity.Entity::discard);
                    }
                    context.succeed();
                }, true));
    }

    /** Every effect of the Lucky Blocks tables opens without trouble, one after another, on a live island. */
    public void luckyEveryEffectOpens(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        BedWarsGame lucky = Minigames.BED_WARS_LUCKY;
        configure(server, lucky, 1, 360, 600);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, lucky, "outpost", "1v1");
        resetSettings(server, lucky);
        TestPlayers.ChatPlayer red = players.get(0);
        MatchManager.join(red, match, 1);
        MatchManager.join(players.get(1), match, 2);
        ServerLevel level = match.arena().level();
        context.startSequence()
                .thenExecuteAfter(4, () -> run(match, players, () -> {
                    Arena.Spawn spawn = ((MapArena) match.arena()).spawnsOf(1).getFirst();
                    Direction back = Direction.fromYRot(spawn.yaw()).getOpposite();
                    BlockPos feet = BlockPos.containing(spawn.position());
                    List<BlockPos> spots = new java.util.ArrayList<>();
                    for (int ahead = 2; ahead <= 4; ahead++) {
                        for (int across = -2; across <= 2; across += 2) {
                            BlockPos spot = feet.relative(back, ahead).relative(back.getClockWise(), across);
                            if (level.getBlockState(spot).isAir()) spots.add(spot);
                        }
                    }
                    assertTrue(spots.size() >= 4, "Expected room on red's island, found " + spots + ".");
                    List<String> effects = BedWarsLucky.effectNames();
                    assertTrue(effects.size() >= 80, "Expected the announcement's effects, found " + effects.size() + ".");
                    for (int index = 0; index < effects.size(); index++) {
                        assertTrue(BedWarsLucky.open(red, spots.get(index % spots.size()), effects.get(index)), "Expected " + effects.get(index) + " opened.");
                    }
                }))
                .thenExecuteAfter(170, () -> run(match, players, () -> {
                    assertEquals(MatchPhase.ACTIVE, match.phase(), "phase after every effect");
                    context.succeed();
                }, true));
    }

    /**
     * Lucky items at work: a Transform block of gold breaks into gold, the Resource Trader trades iron for
     * gold, a Placeable Bed gives a team whose bed fell a bed again, Vampire Blitz heals on every hit, a
     * Telebow arrow takes its shooter where it lands, and an Ice Bridge melts five seconds after it is
     * built.
     */
    public void luckyItemsTradeBuildAndTeleport(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        BedWarsGame lucky = Minigames.BED_WARS_LUCKY;
        configure(server, lucky, 1, 360, 600);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, lucky, "outpost", "1v1");
        resetSettings(server, lucky);
        TestPlayers.ChatPlayer red = players.get(0);
        TestPlayers.ChatPlayer blue = players.get(1);
        MatchManager.join(red, match, 1);
        MatchManager.join(blue, match, 2);
        ServerLevel level = match.arena().level();
        BlockPos[] bridge = new BlockPos[1];
        context.startSequence()
                .thenExecuteAfter(4, () -> run(match, players, () -> {
                    Arena.Spawn spawn = ((MapArena) match.arena()).spawnsOf(1).getFirst();
                    Direction back = Direction.fromYRot(spawn.yaw()).getOpposite();
                    Direction side = back.getClockWise();
                    BlockPos feet = BlockPos.containing(spawn.position());
                    teleport(red, spawn.position());
                    // A gold Transform block breaks into gold ingots.
                    BlockPos gold = feet.relative(back, 2);
                    assertTrue(BedWarsLucky.open(red, gold, "Transform block > Gold"), "Expected the gold transform opened.");
                    assertTrue(level.getBlockState(gold).is(Blocks.GOLD_BLOCK), "Expected a block of gold.");
                    red.gameMode.destroyBlock(gold);
                    assertTrue(level.getBlockState(gold).isAir() && level.getEntitiesOfClass(ItemEntity.class, new AABB(gold).inflate(1),
                            item -> item.getItem().is(Items.GOLD_INGOT)).stream().mapToInt(item -> item.getItem().getCount()).sum() == 3,
                            "Expected the block of gold to break into 3 gold.");
                    level.getEntitiesOfClass(ItemEntity.class, new AABB(gold).inflate(2)).forEach(net.minecraft.world.entity.Entity::discard);
                    // The Resource Trader.
                    BlockPos traderAt = feet.relative(side, 2);
                    assertTrue(BedWarsLucky.open(red, traderAt, "Resource Trader"), "Expected the Resource Trader opened.");
                    Villager trader = level.getEntitiesOfClass(Villager.class, new AABB(traderAt).inflate(2),
                                    villager -> villager.getCustomName() != null && villager.getCustomName().getString().equals("Resource Trader"))
                            .stream().findFirst().orElseThrow(() -> failure("Expected the Resource Trader."));
                    red.getInventory().clearContent();
                    red.getInventory().add(new ItemStack(Items.IRON_INGOT, 32));
                    red.interactOn(trader, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    assertEquals("Resource Trader", title(red), "menu");
                    clickNamed(red, "Gold Ingot", ContainerInput.PICKUP);
                    assertEquals(4, red.getInventory().countItem(Items.GOLD_INGOT), "gold from 32 iron");
                    assertEquals(0, red.getInventory().countItem(Items.IRON_INGOT), "iron after the trade");
                    red.closeContainer();
                    trader.discard();
                    // Blue breaks red's bed; a Placeable Bed gives red one again.
                    BedWarsLayout.Bed redBed = lucky.layout(match).orElseThrow().beds().get(1).getFirst();
                    teleport(blue, Vec3.atBottomCenterOf(redBed.foot()).add(1, 0, 0));
                    blue.gameMode.destroyBlock(redBed.head());
                    assertTrue(!lucky.bedStanding(match, 1), "Expected red's bed broken.");
                    BlockPos bedAt = feet.relative(side.getOpposite(), 2);
                    assertTrue(BedWarsLucky.open(red, bedAt, "Placeable bed"), "Expected the Placeable bed opened.");
                    ItemEntity bedItem = level.getEntitiesOfClass(ItemEntity.class, new AABB(bedAt).inflate(1),
                                    item -> BedWarsShop.ability(item.getItem()).equals("placeable_bed"))
                            .stream().findFirst().orElseThrow(() -> failure("Expected a Placeable Bed."));
                    red.setItemInHand(InteractionHand.MAIN_HAND, bedItem.getItem().copy());
                    bedItem.discard();
                    red.snapTo(spawn.position().x, spawn.position().y, spawn.position().z, back.toYRot(), 60.0F);
                    red.gameMode.useItem(red, level, red.getMainHandItem(), InteractionHand.MAIN_HAND);
                    assertTrue(lucky.bedStanding(match, 1), "Expected red's team to have a bed again.");
                    // Vampire Blitz: a hit heals red a heart.
                    teleport(blue, spawn.position().add(1, 0, 0));
                    assertTrue(BedWarsLucky.open(red, feet.relative(back, 3), "Vampire Blitz"), "Expected Vampire Blitz opened.");
                    red.setHealth(10.0F);
                    red.attack(blue);
                    assertEquals(12.0F, red.getHealth(), "red's health after a hit under Vampire Blitz");
                    teleport(blue, ((MapArena) match.arena()).spawnsOf(2).getFirst().position());
                    // A Telebow arrow takes red where it lands.
                    BlockPos landing = feet.relative(side, 3).below();
                    ItemStack telebow = new ItemStack(Items.BOW);
                    net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                    tag.putString(BedWarsShop.ITEM_KEY, "telebow");
                    telebow.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
                    net.minecraft.world.entity.projectile.arrow.Arrow arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(level, red,
                            new ItemStack(Items.ARROW), telebow);
                    arrow.setPos(landing.getX() + 0.5, landing.getY() + 3.0, landing.getZ() + 0.5);
                    arrow.setDeltaMovement(0, -1.0, 0);
                    level.addFreshEntity(arrow);
                    // An Ice Bridge built out over the void from the island's edge.
                    BlockPos edge = feet.below();
                    Direction out = Direction.fromYRot(spawn.yaw()).getOpposite();
                    while (!level.getBlockState(edge.relative(out)).isAir()) edge = edge.relative(out);
                    bridge[0] = edge.relative(out, 3);
                }))
                .thenExecuteAfter(10, () -> run(match, players, () -> {
                    Arena.Spawn spawn = ((MapArena) match.arena()).spawnsOf(1).getFirst();
                    BlockPos landing = BlockPos.containing(spawn.position()).relative(Direction.fromYRot(spawn.yaw()).getOpposite().getClockWise(), 3);
                    assertTrue(red.blockPosition().distManhattan(landing) <= 1,
                            "Expected the Telebow to take red to " + landing + ", found at " + red.blockPosition() + ".");
                    Direction out = Direction.fromYRot(spawn.yaw()).getOpposite();
                    BlockPos edge = bridge[0].relative(out.getOpposite(), 3);
                    teleport(red, Vec3.atBottomCenterOf(edge.above()));
                    red.snapTo(red.getX(), red.getY(), red.getZ(), out.toYRot(), 0.0F);
                    assertTrue(BedWarsLucky.open(red, edge.above().relative(out.getClockWise(), 2), "Ice Bridge Rotation Effect"),
                            "Expected the Ice Bridge opened.");
                    ItemEntity ice = level.getEntitiesOfClass(ItemEntity.class, red.getBoundingBox().inflate(4),
                                    item -> BedWarsShop.ability(item.getItem()).equals("ice_bridge"))
                            .stream().findFirst().orElseThrow(() -> failure("Expected an Ice Bridge."));
                    red.setItemInHand(InteractionHand.MAIN_HAND, ice.getItem().copy());
                    ice.discard();
                    red.gameMode.useItem(red, level, red.getMainHandItem(), InteractionHand.MAIN_HAND);
                    assertTrue(level.getBlockState(bridge[0]).is(Blocks.ICE), "Expected an ice bridge out over the void at " + bridge[0] + ".");
                }))
                .thenExecuteAfter(110, () -> run(match, players, () -> {
                    assertTrue(level.getBlockState(bridge[0]).isAir(), "Expected the ice bridge melted.");
                    context.succeed();
                }, true));
    }

    /**
     * Lucky Blocks' named effects: a lucky trap lies as a carpet of its team's colour and goes off on
     * the first enemy to step on it, unless an arrow clears it first; Jerry trades a Miracle Lucky
     * Block for three emeralds; the Placeable Wither brings a wither of its team that never hurts it.
     */
    public void luckyTrapsJerryAndTheWitherAlly(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        BedWarsGame lucky = Minigames.BED_WARS_LUCKY;
        configure(server, lucky, 1, 360, 600);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, lucky, "outpost", "1v1");
        resetSettings(server, lucky);
        TestPlayers.ChatPlayer red = players.get(0);
        TestPlayers.ChatPlayer blue = players.get(1);
        MatchManager.join(red, match, 1);
        MatchManager.join(blue, match, 2);
        ServerLevel level = match.arena().level();
        BlockPos[] spots = new BlockPos[2];
        context.startSequence()
                .thenExecuteAfter(4, () -> run(match, players, () -> {
                    Arena.Spawn spawn = ((MapArena) match.arena()).spawnsOf(1).getFirst();
                    Direction back = Direction.fromYRot(spawn.yaw()).getOpposite();
                    Direction side = back.getClockWise();
                    BlockPos feet = BlockPos.containing(spawn.position());
                    spots[0] = feet.relative(back, 2);
                    spots[1] = feet.relative(side, 2);
                    assertTrue(BedWarsLucky.open(red, spots[0], "Trap > Slowness"), "Expected the Slowness trap opened.");
                    assertTrue(level.getBlockState(spots[0]).is(Blocks.CARPET.red()), "Expected the trap laid as red carpet.");
                    assertTrue(BedWarsLucky.open(red, spots[1], "Trap > Poison"), "Expected the Poison trap opened.");
                    net.minecraft.world.entity.projectile.arrow.Arrow arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(level,
                            spots[1].getX() + 0.5, spots[1].getY() + 2.5, spots[1].getZ() + 0.5, new ItemStack(Items.ARROW), null);
                    arrow.setDeltaMovement(0, -1.0, 0);
                    arrow.setOwner(blue);
                    level.addFreshEntity(arrow);
                    teleport(red, spawn.position());
                    teleport(blue, Vec3.atBottomCenterOf(spots[0]));
                }))
                .thenExecuteAfter(8, () -> run(match, players, () -> {
                    assertTrue(blue.hasEffect(MobEffects.SLOWNESS), "Expected blue slowed by red's lucky trap.");
                    assertTrue(level.getBlockState(spots[0]).isAir(), "Expected the sprung trap gone.");
                    assertTrue(level.getBlockState(spots[1]).isAir(), "Expected the arrow to clear the Poison trap.");
                    teleport(blue, Vec3.atBottomCenterOf(spots[1]));
                }))
                .thenExecuteAfter(8, () -> run(match, players, () -> {
                    assertTrue(!blue.hasEffect(MobEffects.POISON), "Expected the cleared trap not to go off.");
                    teleport(blue, ((MapArena) match.arena()).spawnsOf(2).getFirst().position());
                    BlockPos jerryAt = spots[1];
                    assertTrue(BedWarsLucky.open(red, jerryAt, "Jerry"), "Expected Jerry opened.");
                    Villager jerry = level.getEntitiesOfClass(Villager.class, new AABB(jerryAt).inflate(2),
                                    villager -> villager.getCustomName() != null && villager.getCustomName().getString().equals("Jerry"))
                            .stream().findFirst().orElseThrow(() -> failure("Expected Jerry by the lucky block."));
                    red.getInventory().add(new ItemStack(Items.EMERALD, 3));
                    red.interactOn(jerry, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    assertEquals("Jerry", title(red), "menu");
                    clickNamed(red, "Miracle Lucky Block", ContainerInput.PICKUP);
                    assertEquals(1, red.getInventory().countItem(Blocks.GLAZED_TERRACOTTA.pick(DyeColor.LIME).asItem()), "Miracle Lucky Blocks from Jerry");
                    assertEquals(0, red.getInventory().countItem(Items.EMERALD), "emeralds after Jerry's trade");
                    red.closeContainer();
                    assertTrue(BedWarsLucky.open(red, spots[0], "Placeable Wither"), "Expected the Placeable Wither opened.");
                    ItemEntity skull = level.getEntitiesOfClass(ItemEntity.class, new AABB(spots[0]).inflate(1),
                                    item -> BedWarsShop.ability(item.getItem()).equals("placeable_wither"))
                            .stream().findFirst().orElseThrow(() -> failure("Expected a Placeable Wither dropped."));
                    Arena.Spawn spawn = ((MapArena) match.arena()).spawnsOf(1).getFirst();
                    teleport(red, spawn.position());
                    red.snapTo(spawn.position().x, spawn.position().y, spawn.position().z, spawn.yaw(), 70.0F);
                    red.setItemInHand(InteractionHand.MAIN_HAND, skull.getItem().copy());
                    skull.discard();
                    red.gameMode.useItem(red, level, red.getMainHandItem(), InteractionHand.MAIN_HAND);
                    List<net.minecraft.world.entity.boss.wither.WitherBoss> withers = level.getEntitiesOfClass(
                            net.minecraft.world.entity.boss.wither.WitherBoss.class, red.getBoundingBox().inflate(8));
                    assertEquals(1, withers.size(), "withers placed");
                    assertTrue(!MatchManager.allowDamage(red, red.damageSources().mobAttack(withers.getFirst())),
                            "Expected red's wither unable to hurt red.");
                    assertTrue(MatchManager.allowDamage(blue, blue.damageSources().mobAttack(withers.getFirst())),
                            "Expected red's wither able to hurt blue.");
                    context.succeed();
                }, true));
    }

    /**
     * Ultimate: a Kangaroo's second jump in the air (a vanilla client asking to fly) becomes a leap; a
     * Builder's ability builds a bridge of its wool, and its ability item sits in the last hotbar slot.
     */
    public void ultimatesLeapAndBuild(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        BedWarsGame ultimate = Minigames.BED_WARS_ULTIMATE;
        configure(server, ultimate, 1, 360, 600);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        TestPlayers.ChatPlayer red = players.get(0);
        TestPlayers.ChatPlayer blue = players.get(1);
        BedWarsUltimates.select(server, red.getUUID(), Ultimate.KANGAROO);
        BedWarsUltimates.select(server, blue.getUUID(), Ultimate.BUILDER);
        Match match = open(context, ultimate, "outpost", "1v1");
        resetSettings(server, ultimate);
        MatchManager.join(red, match, 1);
        MatchManager.join(blue, match, 2);
        ServerLevel level = match.arena().level();
        context.startSequence()
                .thenExecuteAfter(5, () -> run(match, players, () -> red.setOnGround(true)))
                .thenExecuteAfter(1, () -> run(match, players, () -> {
                    // Test players have no client to report landing, so it is set by hand.
                    assertTrue(red.getAbilities().mayfly, "Expected a Kangaroo on the ground able to double-jump.");
                    assertTrue(BedWarsShop.ability(blue.getInventory().getItem(8)).equals("ultimate"), "Expected the ability item in slot 9.");
                    red.teleportTo(red.getX(), red.getY() + 1.5, red.getZ());
                    red.setOnGround(false);
                    red.getAbilities().flying = true;
                    Arena.Spawn spawn = ((MapArena) match.arena()).spawnsOf(2).getFirst();
                    Direction out = Direction.fromYRot(spawn.yaw()).getOpposite();
                    BlockPos edge = BlockPos.containing(spawn.position()).below();
                    while (!level.getBlockState(edge.relative(out)).isAir()) edge = edge.relative(out);
                    teleport(blue, Vec3.atBottomCenterOf(edge.above()));
                    blue.snapTo(blue.getX(), blue.getY(), blue.getZ(), out.toYRot(), 30.0F);
                    blue.getInventory().setSelectedSlot(8);
                    blue.gameMode.useItem(blue, level, blue.getInventory().getItem(8), InteractionHand.MAIN_HAND);
                    int built = 0;
                    for (int step = 1; step <= 6; step++) if (level.getBlockState(edge.relative(out, step)).is(Blocks.WOOL.blue())) built++;
                    assertEquals(6, built, "Builder's bridge length");
                }))
                .thenExecuteAfter(2, () -> run(match, players, () -> {
                    assertTrue(!red.getAbilities().flying, "Expected the flight request turned into a leap.");
                    assertTrue(red.getDeltaMovement().y > 0.5, "Expected the Kangaroo to leap, moving " + red.getDeltaMovement() + ".");
                    context.succeed();
                }, true));
    }

    /**
     * Armed: everyone spawns with a Pistol whose bar shows its clip; a shot at an enemy in sight hurts
     * them and uses a round; a left-click reloads; the shop sells guns and no bows.
     */
    public void armedGunsShootAndReload(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        BedWarsGame armed = Minigames.BED_WARS_ARMED;
        configure(server, armed, 1, 360, 600);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, armed, "outpost", "1v1");
        resetSettings(server, armed);
        TestPlayers.ChatPlayer red = players.get(0);
        TestPlayers.ChatPlayer blue = players.get(1);
        MatchManager.join(red, match, 1);
        MatchManager.join(blue, match, 2);
        ServerLevel level = match.arena().level();
        context.startSequence()
                .thenExecuteAfter(5, () -> run(match, players, () -> {
                    ItemStack pistol = held(red, Items.WOODEN_HOE);
                    assertEquals("pistol", BedWarsShop.ability(pistol), "starting gun");
                    Arena.Spawn spawn = ((MapArena) match.arena()).spawnsOf(1).getFirst();
                    Direction back = Direction.fromYRot(spawn.yaw()).getOpposite();
                    teleport(red, spawn.position());
                    teleport(blue, Vec3.atBottomCenterOf(BlockPos.containing(spawn.position()).relative(back, 4)));
                    red.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, blue.position().add(0, 1.0, 0));
                    red.getInventory().setSelectedSlot(red.getInventory().findSlotMatchingItem(pistol));
                    float health = blue.getHealth();
                    red.gameMode.useItem(red, level, red.getMainHandItem(), InteractionHand.MAIN_HAND);
                    assertTrue(blue.getHealth() < health, "Expected the shot to hurt blue (" + health + " then " + blue.getHealth() + ").");
                    assertEquals(1, red.getMainHandItem().getOrDefault(DataComponents.DAMAGE, 0), "rounds fired, on the bar");
                    MatchManager.swing(red);
                }))
                .thenExecuteAfter(35, () -> run(match, players, () -> {
                    assertEquals(0, red.getMainHandItem().getOrDefault(DataComponents.DAMAGE, -1), "rounds fired after reloading");
                    Villager shop = shopkeeper(match, red, "ITEM SHOP");
                    red.interactOn(shop, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    BedWarsGameTest.click(red, 5, 0, ContainerInput.PICKUP);
                    assertEquals("Ranged", title(red), "menu");
                    assertTrue(hasNamed(red, "Rifle") && hasNamed(red, "Magnum") && !hasNamed(red, "Bow"), "Expected guns and no bows.");
                    context.succeed();
                }, true));
    }

    /**
     * Armed's Deadshot team upgrade, sold only in Armed, makes every gun shot hurt a quarter more per
     * tier: a Pistol body shot takes 4 health from an unarmoured player, 5 after Deadshot I.
     */
    public void armedDeadshotAddsGunDamage(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        BedWarsGame armed = Minigames.BED_WARS_ARMED;
        assertTrue(!Minigames.BED_WARS.offers(BedWarsUpgrades.Upgrade.DEADSHOT), "Expected no Deadshot outside Armed.");
        configure(server, armed, 1, 360, 600);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, armed, "outpost", "1v1");
        resetSettings(server, armed);
        TestPlayers.ChatPlayer red = players.get(0);
        TestPlayers.ChatPlayer blue = players.get(1);
        MatchManager.join(red, match, 1);
        MatchManager.join(blue, match, 2);
        ServerLevel level = match.arena().level();
        float[] drops = new float[2];
        Runnable shoot = () -> {
            for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {
                    net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                blue.setItemSlot(slot, ItemStack.EMPTY);
            }
            blue.setHealth(blue.getMaxHealth());
            Arena.Spawn spawn = ((MapArena) match.arena()).spawnsOf(1).getFirst();
            Direction back = Direction.fromYRot(spawn.yaw()).getOpposite();
            teleport(red, spawn.position());
            teleport(blue, Vec3.atBottomCenterOf(BlockPos.containing(spawn.position()).relative(back, 4)));
            red.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, blue.position().add(0, 0.8, 0));
            ItemStack pistol = held(red, Items.WOODEN_HOE);
            red.getInventory().setSelectedSlot(red.getInventory().findSlotMatchingItem(pistol));
            // Test players are not ticked, so the fire-rate cooldown of the last shot is cleared by hand.
            red.getCooldowns().removeCooldown(red.getCooldowns().getCooldownGroup(pistol));
            float health = blue.getHealth();
            red.gameMode.useItem(red, level, red.getMainHandItem(), InteractionHand.MAIN_HAND);
            drops[drops[0] == 0 ? 0 : 1] = health - blue.getHealth();
        };
        context.startSequence()
                .thenExecuteAfter(5, () -> run(match, players, () -> {
                    shoot.run();
                    assertEquals(4.0F, drops[0], "health a Pistol body shot takes");
                    red.getInventory().add(new ItemStack(Items.DIAMOND, 2));
                    try {
                        armed.buyUpgrade(match, red, BedWarsUpgrades.Upgrade.DEADSHOT);
                    } catch (MatchException exception) {
                        throw failure(exception.getMessage());
                    }
                    assertEquals(1, armed.upgradeLevel(match, red, BedWarsUpgrades.Upgrade.DEADSHOT), "Deadshot tier");
                }))
                .thenExecuteAfter(12, () -> run(match, players, () -> {
                    shoot.run();
                    assertEquals(5.0F, drops[1], "health a Pistol body shot takes with Deadshot I");
                    context.succeed();
                }, true));
    }

    /**
     * Ultimate's Demolition gets a Creeper Egg for every bed it breaks; the creeper hatched from it
     * fights for its team, never hurting it.
     */
    public void demolitionGetsACreeperEggForABed(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        BedWarsGame ultimate = Minigames.BED_WARS_ULTIMATE;
        configure(server, ultimate, 1, 360, 600);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        TestPlayers.ChatPlayer red = players.get(0);
        TestPlayers.ChatPlayer blue = players.get(1);
        BedWarsUltimates.select(server, red.getUUID(), Ultimate.DEMOLITION);
        Match match = open(context, ultimate, "outpost", "1v1");
        resetSettings(server, ultimate);
        MatchManager.join(red, match, 1);
        MatchManager.join(blue, match, 2);
        ServerLevel level = match.arena().level();
        context.startSequence()
                .thenExecuteAfter(5, () -> run(match, players, () -> {
                    BedWarsLayout.Bed bed = ultimate.layout(match).orElseThrow().beds().get(2).getFirst();
                    teleport(red, Vec3.atBottomCenterOf(bed.foot()).add(1, 0, 0));
                    red.gameMode.destroyBlock(bed.head());
                    assertTrue(!ultimate.bedStanding(match, 2), "Expected blue's bed broken.");
                    ItemStack egg = held(red, Items.CREEPER_SPAWN_EGG);
                    assertEquals("creeper_egg", BedWarsShop.ability(egg), "what Demolition got for the bed");
                    Vec3 spawn = ((MapArena) match.arena()).spawnsOf(1).getFirst().position();
                    teleport(red, spawn);
                    red.snapTo(spawn.x, spawn.y, spawn.z, 0.0F, 70.0F);
                    red.getInventory().setSelectedSlot(red.getInventory().findSlotMatchingItem(egg));
                    red.gameMode.useItem(red, level, red.getMainHandItem(), InteractionHand.MAIN_HAND);
                    List<net.minecraft.world.entity.monster.Creeper> creepers = level.getEntitiesOfClass(
                            net.minecraft.world.entity.monster.Creeper.class, red.getBoundingBox().inflate(6));
                    assertEquals(1, creepers.size(), "creepers hatched");
                    assertTrue(!MatchManager.allowDamage(red, red.damageSources().explosion(creepers.getFirst(), null)),
                            "Expected red's creeper unable to hurt red.");
                    assertTrue(MatchManager.allowDamage(blue, blue.damageSources().explosion(creepers.getFirst(), null)),
                            "Expected red's creeper able to hurt blue.");
                    context.succeed();
                }, true));
    }
}
