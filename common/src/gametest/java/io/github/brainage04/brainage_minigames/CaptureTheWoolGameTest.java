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
import io.github.brainage04.brainage_minigames.game.ctw.CaptureTheWoolGame;
import io.github.brainage04.brainage_minigames.game.ctw.CaptureTheWoolGame.WoolState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Capture the Wool on its bundled maps, pasted into the test level. */
public final class CaptureTheWoolGameTest {
    private static final CaptureTheWoolGame CTW = Minigames.CAPTURE_THE_WOOL;
    private static final AtomicInteger NEXT_NAME = new AtomicInteger();

    /** On bastions, red (team 1) steals these from blue's wool rooms, and blue these from red's. */
    private static final DyeColor LIME = DyeColor.LIME;
    private static final DyeColor CYAN = DyeColor.CYAN;
    private static final DyeColor ORANGE = DyeColor.ORANGE;

    /**
     * Every map is for two teams. Each wool lies in a wool room of the team that keeps it, and each
     * team's monument has a slot for every wool the other keeps. A slot is air on a pedestal under a
     * solid block, so nobody can stand in it and keep a carrier from placing, and open on at least
     * one side to place into. Every spawn can be stood on.
     */
    public void everyMapKeepsItsWoolsInRoomsAndItsSlotsOpen(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        List<Identifier> maps = MapArena.maps(server, CaptureTheWoolGame.ID);
        assertTrue(maps.size() >= 2, "Expected two Capture the Wool maps, found " + maps + ".");
        for (Identifier map : maps) {
            MapArena arena = MapArena.open(context.getLevel(), map);
            try {
                ServerLevel level = arena.level();
                assertEquals(2, arena.teamSlots(), map + " teams");
                Map<String, Integer> kept = new HashMap<>();
                for (MapArena.Point wool : arena.points("wool_")) {
                    String[] words = wool.name().split("_", 3);
                    int team = Integer.parseInt(words[1]);
                    kept.put(words[2], team);
                    assertTrue(arena.regions("woolroom_" + team + "_").stream().anyMatch(room -> room.contains(wool.position().add(0, 0.5, 0))),
                            map + ": " + wool.name() + " is not in a wool room of team " + team + ".");
                    assertTrue(solid(level, BlockPos.containing(wool.position()).below()),
                            map + ": " + wool.name() + " has no pedestal.");
                }
                assertEquals(4, kept.size(), map + " wools");
                for (int team = 1; team <= 2; team++) {
                    int owner = team;
                    assertEquals(2L, kept.values().stream().filter(value -> value == owner).count(),
                            map + " wools kept by team " + team);
                }
                List<MapArena.Point> slots = arena.points("monument_");
                assertEquals(4, slots.size(), map + " monument slots");
                for (MapArena.Point slot : slots) {
                    String[] words = slot.name().split("_", 3);
                    int team = Integer.parseInt(words[1]);
                    assertEquals(3 - team, kept.get(words[2]), map + ": the team keeping the wool of " + slot.name());
                    BlockPos pos = BlockPos.containing(slot.position());
                    assertTrue(level.getBlockState(pos).isAir(), map + ": " + slot.name() + " is not empty.");
                    assertTrue(solid(level, pos.below()), map + ": " + slot.name() + " has no pedestal.");
                    assertTrue(solid(level, pos.above()),
                            map + ": a player could stand in " + slot.name() + ".");
                    assertTrue(Direction.Plane.HORIZONTAL.stream().anyMatch(side -> level.getBlockState(pos.relative(side)).isAir()),
                            map + ": " + slot.name() + " is closed on every side.");
                }
                for (int team = 1; team <= 2; team++) {
                    for (Arena.Spawn spawn : arena.spawnsOf(team)) {
                        BlockPos feet = BlockPos.containing(spawn.position());
                        assertTrue(level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                                        && solid(level, feet.below()),
                                map + ": spawn " + team + " at " + feet + " cannot be stood on.");
                        assertTrue(arena.regions("woolroom_" + team + "_").stream().noneMatch(room -> room.contains(spawn.position())),
                                map + ": spawn " + team + " is inside its own wool room.");
                    }
                }
            } finally {
                arena.close();
            }
        }
        context.succeed();
    }

    private static boolean solid(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Breaking a wool in the enemy's room takes it (nothing drops); it can be placed only on its
     * own slot of the carrier's monument, and filling the monument wins. Owners are kept out of
     * their own wool rooms, nobody builds in one, and only placed blocks break.
     */
    public void stolenWoolsPlacedOnTheMonumentWin(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        configure(server, 5, 10);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, "bastions", "1v1");
        resetSettings(server);
        TestPlayers.ChatPlayer red = players.get(0);
        TestPlayers.ChatPlayer blue = players.get(1);
        MatchManager.join(red, match, 1);
        MatchManager.join(blue, match, 2);
        MapArena arena = (MapArena) match.arena();
        ServerLevel level = arena.level();
        Vec3[] blueInRoom = new Vec3[1];
        context.startSequence()
                .thenExecuteAfter(3, () -> run(match, players, () -> {
                    assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                    BlockPos lime = CTW.source(match, LIME).orElseThrow();
                    assertTrue(level.getBlockState(lime).is(Blocks.WOOL.lime()), "Expected the lime wool in its room.");
                    assertTrue(red.getInventory().contains(stack -> stack.is(Items.STONE_SWORD))
                                    && red.getInventory().contains(stack -> stack.is(Items.OAK_PLANKS)),
                            "Expected the kit.");
                    Map<String, Object> view = CaptureTheWoolGame.botView(red);
                    assertTrue(view != null && Integer.valueOf(1).equals(view.get("team"))
                                    && ((List<?>) view.get("wools")).size() == 4,
                            "Expected the bot view of the match: " + view);
                    // Blue may not walk into its own wool room.
                    blueInRoom[0] = Vec3.atBottomCenterOf(lime.north());
                    teleport(blue, blueInRoom[0]);
                    // A map block holds; nothing may be built in a wool room.
                    assertTrue(!red.gameMode.destroyBlock(lime.below()), "Expected the pedestal to hold.");
                    assertTrue(!MatchManager.allowPlace(red, lime.east(), Blocks.OAK_PLANKS.defaultBlockState()),
                            "Expected no building in a wool room.");
                    // Red takes the lime wool.
                    teleport(red, Vec3.atBottomCenterOf(lime.south()));
                    assertTrue(!red.gameMode.destroyBlock(lime), "Taking the wool must not break it as a block.");
                    assertTrue(level.getBlockState(lime).isAir(), "Expected the wool taken from its pedestal.");
                    assertTrue(red.getInventory().contains(stack -> stack.is(Items.WOOL.lime())), "Expected red to carry the lime wool.");
                    assertEquals(WoolState.CARRIED, CTW.woolState(match, LIME).orElseThrow(), "lime wool");
                    assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(lime).inflate(4)).isEmpty(), "Expected nothing dropped.");
                    BlockPos elsewhere = BlockPos.containing(arena.spawnsOf(1).getFirst().position()).east(3);
                    assertTrue(!MatchManager.allowPlace(red, elsewhere, Blocks.WOOL.lime().defaultBlockState()),
                            "Expected the wool to be placeable on its slot only.");
                    assertTrue(!MatchManager.allowPlace(red, CTW.slot(match, CYAN).orElseThrow(), Blocks.WOOL.lime().defaultBlockState()),
                            "Expected the lime wool to be refused on the cyan slot.");
                }))
                .thenExecuteAfter(2, () -> run(match, players, () -> {
                    assertTrue(blue.position().distanceTo(blueInRoom[0]) > 1.0,
                            "Expected blue put back out of its own wool room, found at " + blue.position() + ".");
                    assertTrue(contains(red.messages, "took the Lime wool"), "Expected the theft announced: " + red.messages);
                    BlockPos planks = BlockPos.containing(arena.spawnsOf(1).getFirst().position()).east(3);
                    assertTrue(MatchManager.allowPlace(red, planks, Blocks.OAK_PLANKS.defaultBlockState()), "Expected building in the open.");
                    level.setBlock(planks, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                    MatchManager.blockPlaced(red, planks);
                    assertTrue(red.gameMode.destroyBlock(planks), "Expected a placed block to break.");
                    List<Component> lines = new ArrayList<>();
                    CTW.addSidebarLines(match, lines);
                    assertTrue(lines.stream().anyMatch(line -> line.getString().equals("▣ Lime: " + red.getScoreboardName())),
                            "Expected the sidebar to name the lime wool's carrier: " + lines);
                    assertEquals(" ▣□", CTW.sidebarTeamSuffix(match, team(match, red)).getString(), "red's monument on the sidebar");
                    placeOnSlot(match, red, LIME);
                }))
                .thenExecuteAfter(2, () -> run(match, players, () -> {
                    BlockPos slot = CTW.slot(match, LIME).orElseThrow();
                    assertTrue(level.getBlockState(slot).is(Blocks.WOOL.lime()), "Expected the lime wool on its slot.");
                    assertEquals(WoolState.PLACED, CTW.woolState(match, LIME).orElseThrow(), "lime wool");
                    assertEquals(1, team(match, red).score(), "red's wool placed");
                    assertTrue(contains(blue.messages, "placed the Lime wool"), "Expected the capture announced: " + blue.messages);
                    assertTrue(!red.gameMode.destroyBlock(slot) && !blue.gameMode.destroyBlock(slot), "Expected a placed wool to stay.");
                    assertEquals(" ■□", CTW.sidebarTeamSuffix(match, team(match, red)).getString(), "red's monument on the sidebar");
                    List<Component> lines = new ArrayList<>();
                    CTW.addSidebarLines(match, lines);
                    assertTrue(lines.isEmpty(), "Expected no wool away from its room or slot on the sidebar: " + lines);
                    assertEquals(MatchPhase.ACTIVE, match.phase(), "phase with one wool of two");
                    BlockPos cyan = CTW.source(match, CYAN).orElseThrow();
                    teleport(red, Vec3.atBottomCenterOf(cyan.south()));
                    red.gameMode.destroyBlock(cyan);
                    placeOnSlot(match, red, CYAN);
                }))
                .thenExecuteAfter(2, () -> run(match, players, () -> {
                    assertEquals(MatchPhase.ENDED, match.phase(), "phase with the monument full");
                    assertEquals(List.of(team(match, red)), match.winners(), "winners");
                    context.succeed();
                }, true));
    }

    /**
     * A killed carrier drops the wool where they died; a teammate picks it up and carries on, and a
     * player of the team keeping it returns it to its room by touching it.
     */
    public void aDroppedWoolIsCarriedOnByATeammateOrReturnedByItsKeepers(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        configure(server, 5, 30);
        List<TestPlayers.ChatPlayer> players = players(context, 3);
        Match match = open(context, "bastions", "2v1");
        resetSettings(server);
        TestPlayers.ChatPlayer red = players.get(0);
        TestPlayers.ChatPlayer mate = players.get(1);
        TestPlayers.ChatPlayer blue = players.get(2);
        MatchManager.join(red, match, 1);
        MatchManager.join(mate, match, 1);
        MatchManager.join(blue, match, 2);
        ServerLevel level = match.arena().level();
        context.startSequence()
                .thenExecuteAfter(3, () -> run(match, players, () -> {
                    BlockPos lime = CTW.source(match, LIME).orElseThrow();
                    teleport(red, Vec3.atBottomCenterOf(lime.south(3)));
                    red.gameMode.destroyBlock(lime);
                    assertEquals(WoolState.CARRIED, CTW.woolState(match, LIME).orElseThrow(), "lime wool once taken");
                    kill(red, blue);
                    assertEquals(GameType.SPECTATOR, red.gameMode.getGameModeForPlayer(), "the killed carrier's game mode");
                    assertTrue(CTW.isRespawning(match, red.getUUID()), "Expected the killed carrier to wait to respawn.");
                    assertTrue(!red.getInventory().contains(stack -> stack.is(Items.WOOL.lime())), "The dead carrier kept the wool.");
                    assertEquals(WoolState.DROPPED, CTW.woolState(match, LIME).orElseThrow(), "lime wool once its carrier died");
                    assertTrue(contains(blue.messages, "dropped the Lime wool"), "Expected the drop announced: " + blue.messages);
                }))
                // The dropped wool cannot be picked up for half a second.
                .thenExecuteAfter(12, () -> run(match, players, () -> {
                    ItemEntity dropped = droppedWool(match, Items.WOOL.lime());
                    teleport(mate, dropped.position());
                    dropped.playerTouch(mate);
                }))
                .thenExecuteAfter(2, () -> run(match, players, () -> {
                    assertEquals(WoolState.CARRIED, CTW.woolState(match, LIME).orElseThrow(), "lime wool picked up by a teammate");
                    assertEquals(mate.getUUID(), CTW.carrier(match, LIME).orElseThrow(), "carrier");
                    assertTrue(contains(blue.messages, "picked up the Lime wool"), "Expected the pickup announced: " + blue.messages);
                    kill(mate, blue);
                    assertEquals(WoolState.DROPPED, CTW.woolState(match, LIME).orElseThrow(), "lime wool dropped again");
                }))
                .thenExecuteAfter(12, () -> run(match, players, () -> {
                    ItemEntity dropped = droppedWool(match, Items.WOOL.lime());
                    teleport(blue, dropped.position());
                    dropped.playerTouch(blue);
                }))
                .thenExecuteAfter(2, () -> run(match, players, () -> {
                    assertEquals(WoolState.HOME, CTW.woolState(match, LIME).orElseThrow(), "lime wool touched by its keepers");
                    assertTrue(level.getBlockState(CTW.source(match, LIME).orElseThrow()).is(Blocks.WOOL.lime()),
                            "Expected the lime wool back on its pedestal.");
                    assertTrue(!blue.getInventory().contains(stack -> stack.is(Items.WOOL.lime())), "A keeper kept the wool.");
                    assertTrue(contains(red.messages, "returned the Lime wool"), "Expected the return announced: " + red.messages);
                    context.succeed();
                }, true));
    }

    /**
     * A dropped wool nobody picks up returns to its room after the return delay, and a killed
     * player respawns at their base with the kit once the respawn timer runs out.
     */
    public void aDroppedWoolReturnsAndTheKilledRespawnAfterTheirTimers(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        configure(server, 1, 1);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, "bastions", "1v1");
        resetSettings(server);
        TestPlayers.ChatPlayer red = players.get(0);
        TestPlayers.ChatPlayer blue = players.get(1);
        MatchManager.join(red, match, 1);
        MatchManager.join(blue, match, 2);
        MapArena arena = (MapArena) match.arena();
        ServerLevel level = arena.level();
        context.startSequence()
                .thenExecuteAfter(3, () -> run(match, players, () -> {
                    BlockPos lime = CTW.source(match, LIME).orElseThrow();
                    teleport(red, Vec3.atBottomCenterOf(lime.south(3)));
                    red.gameMode.destroyBlock(lime);
                    kill(red, blue);
                    assertEquals(WoolState.DROPPED, CTW.woolState(match, LIME).orElseThrow(), "lime wool");
                    assertEquals(GameType.SPECTATOR, red.gameMode.getGameModeForPlayer(), "game mode while waiting");
                }))
                .thenExecuteAfter(10, () -> run(match, players, () -> {
                    assertEquals(WoolState.DROPPED, CTW.woolState(match, LIME).orElseThrow(), "lime wool after half a second");
                    assertTrue(CTW.isRespawning(match, red.getUUID()), "Expected the killed player still waiting.");
                }))
                .thenExecuteAfter(15, () -> run(match, players, () -> {
                    assertEquals(WoolState.HOME, CTW.woolState(match, LIME).orElseThrow(), "lime wool after its return delay");
                    assertTrue(level.getBlockState(CTW.source(match, LIME).orElseThrow()).is(Blocks.WOOL.lime()),
                            "Expected the lime wool back on its pedestal.");
                    assertTrue(level.getEntitiesOfClass(ItemEntity.class, AABB.of(arena.bounds()),
                            item -> item.getItem().is(Items.WOOL.lime())).isEmpty(), "Expected the dropped wool gone.");
                    assertTrue(contains(red.messages, "returned to"), "Expected the return announced: " + red.messages);
                    assertTrue(!CTW.isRespawning(match, red.getUUID()), "Expected the player respawned.");
                    assertEquals(GameType.SURVIVAL, red.gameMode.getGameModeForPlayer(), "game mode once respawned");
                    assertTrue(red.position().distanceTo(arena.spawnsOf(1).getFirst().position()) < 4,
                            "Expected a respawn at the base, found " + red.position() + ".");
                    assertTrue(red.getInventory().contains(stack -> stack.is(Items.IRON_PICKAXE)), "Expected the kit again.");
                    context.succeed();
                }, true));
    }

    /** At the time limit the team with more wool placed wins, then the one carrying more; else a draw. */
    public void theTimeLimitGoesToWoolPlacedThenCarried(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        configure(server, 5, 30);
        List<TestPlayers.ChatPlayer> players = players(context, 2);
        Match match = open(context, "bastions", "1v1");
        resetSettings(server);
        TestPlayers.ChatPlayer red = players.get(0);
        TestPlayers.ChatPlayer blue = players.get(1);
        MatchManager.join(red, match, 1);
        MatchManager.join(blue, match, 2);
        context.startSequence()
                .thenExecuteAfter(3, () -> run(match, players, () -> {
                    assertEquals(2, CTW.timeoutWinners(match).size(), "winners with nothing placed or carried");
                    BlockPos lime = CTW.source(match, LIME).orElseThrow();
                    teleport(red, Vec3.atBottomCenterOf(lime.south(3)));
                    red.gameMode.destroyBlock(lime);
                    assertEquals(List.of(team(match, red)), CTW.timeoutWinners(match), "winners while red carries a wool");
                    BlockPos orange = CTW.source(match, ORANGE).orElseThrow();
                    teleport(blue, Vec3.atBottomCenterOf(orange.north(3)));
                    blue.gameMode.destroyBlock(orange);
                    assertEquals(2, CTW.timeoutWinners(match).size(), "winners with a wool carried each");
                    placeOnSlot(match, blue, ORANGE);
                }))
                .thenExecuteAfter(2, () -> run(match, players, () -> {
                    assertEquals(WoolState.PLACED, CTW.woolState(match, ORANGE).orElseThrow(), "orange wool");
                    assertEquals(List.of(team(match, blue)), CTW.timeoutWinners(match),
                            "winners with a wool placed against one carried");
                    context.succeed();
                }, true));
    }

    /** Holds the carried wool and uses it on its slot's pedestal, as a client placing it would. */
    private static void placeOnSlot(Match match, ServerPlayer player, DyeColor colour) {
        BlockPos slot = CTW.slot(match, colour).orElseThrow();
        teleport(player, Vec3.atBottomCenterOf(slot.relative(front(match, slot), 2)));
        ItemStack wool = new ItemStack(Items.WOOL.pick(colour));
        for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
            if (player.getInventory().getItem(index).is(wool.getItem())) player.getInventory().setItem(index, ItemStack.EMPTY);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, wool);
        BlockPos pedestal = slot.below();
        player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pedestal).add(0, 0.5, 0), Direction.UP, pedestal, false));
    }

    /** The open side of a slot, which carriers place from. */
    private static Direction front(Match match, BlockPos slot) {
        ServerLevel level = match.arena().level();
        return Direction.Plane.HORIZONTAL.stream()
                .filter(side -> level.getBlockState(slot.relative(side)).isAir())
                .findFirst()
                .orElseThrow(() -> failure("The slot at " + slot + " is closed."));
    }

    private static ItemEntity droppedWool(Match match, net.minecraft.world.item.Item wool) {
        MapArena arena = (MapArena) match.arena();
        List<ItemEntity> items = arena.level().getEntitiesOfClass(ItemEntity.class, AABB.of(arena.bounds()),
                item -> item.getItem().is(wool));
        assertEquals(1, items.size(), "dropped wools on the ground");
        return items.getFirst();
    }

    private static void kill(ServerPlayer victim, ServerPlayer killer) {
        victim.invulnerableTime = 0;
        victim.hurtServer(victim.level(), victim.damageSources().playerAttack(killer), 1000.0F);
    }

    /** Runs a step; stops the match and removes the players after a failure or the last step. */
    private static void run(Match match, List<? extends ServerPlayer> players, Runnable step) {
        run(match, players, step, false);
    }

    private static void run(Match match, List<? extends ServerPlayer> players, Runnable step, boolean last) {
        boolean failed = true;
        try {
            step.run();
            failed = false;
        } finally {
            if (failed || last) finish(match, players);
        }
    }

    private static Match open(GameTestHelper context, String map, String layout) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        Identifier id = MapArena.maps(server, CaptureTheWoolGame.ID).stream()
                .filter(candidate -> candidate.getPath().endsWith("/" + map))
                .findFirst()
                .orElseThrow(() -> failure("Missing Capture the Wool map " + map + "."));
        return MatchManager.open(server, CTW, TeamLayout.parse(layout).orElseThrow(), null,
                (unused, settings) -> MapArena.open(context.getLevel(), id));
    }

    private static List<TestPlayers.ChatPlayer> players(GameTestHelper context, int count) {
        List<TestPlayers.ChatPlayer> players = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            players.add(TestPlayers.chat(context, "ctw" + NEXT_NAME.incrementAndGet()));
        }
        return players;
    }

    private static void finish(Match match, List<? extends ServerPlayer> players) {
        MatchManager.stop(match);
        for (ServerPlayer player : players) {
            var playerList = player.level().getServer().getPlayerList();
            if (playerList.getPlayer(player.getUUID()) == player) playerList.remove(player);
        }
    }

    /** Settings are read when a match opens, so they can be reset straight afterwards. */
    private static void configure(MinecraftServer server, int respawnSeconds, int returnSeconds) {
        SettingsStorage.set(server, CTW, CTW.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), 0);
        SettingsStorage.set(server, CTW, CaptureTheWoolGame.RESPAWN_SECONDS, respawnSeconds);
        SettingsStorage.set(server, CTW, CaptureTheWoolGame.WOOL_RETURN_SECONDS, returnSeconds);
    }

    private static void resetSettings(MinecraftServer server) {
        for (GameSetting setting : CTW.settings()) SettingsStorage.reset(server, CTW, setting);
    }

    private static MatchTeam team(Match match, ServerPlayer player) {
        return match.teamOf(player.getUUID())
                .orElseThrow(() -> failure("Expected " + player.getScoreboardName() + " on a team."));
    }

    private static boolean contains(List<Component> messages, String text) {
        return messages.stream().anyMatch(message -> message.getString().contains(text));
    }

    private static void teleport(ServerPlayer player, Vec3 position) {
        player.teleportTo(position.x, position.y, position.z);
    }

    private static void assertEquals(Object expected, Object actual, String description) {
        if (!expected.equals(actual)) {
            throw failure("Expected " + description + " " + expected + ", found " + actual + ".");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw failure(message);
    }

    private static GameTestAssertException failure(String message) {
        return new GameTestAssertException(Component.literal(message), 0);
    }
}
