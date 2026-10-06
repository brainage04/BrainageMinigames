package io.github.brainage04.brainage_minigames.game;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.GameTestLifecycle;
import io.github.brainage04.brainage_minigames.TestPlayers;
import io.github.brainage04.brainage_minigames.api.MatchBots;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.BoxArena;
import io.github.brainage04.brainage_minigames.game.uhc.NaturalArena;
import io.github.brainage04.brainage_minigames.storage.PlayerSnapshotStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/** Bot providers filling match slots: lobby auto-start, start votes and bot slots in any layout. */
public final class MatchBotsGameTestFunctions {
    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final Identifier BAREBONES = BrainageMinigames.id("kits/barebones");

    private MatchBotsGameTestFunctions() {}

    /**
     * UHC, Meetup and FinalUHC lobbies start on their own 30 seconds after someone waits, filling
     * up to 8. In a Meetup lobby with 5 of 8 players, 3 votes start it with 3 bots: 8 participants.
     */
    public static void meetupVoteFillsEmptySlots(GameTestHelper context) throws MatchException {
        for (Minigame game : List.of(Minigames.UHC, Minigames.MEETUP, Minigames.FINAL_UHC)) {
            context.assertTrue(
                    game.setting(GameSetting.LOBBY_SECONDS).map(GameSetting::defaultValue).orElse(0) == 30
                            && game.setting(GameSetting.LOBBY_SIZE).map(GameSetting::defaultValue).orElse(0) == 8,
                    game.id() + " lobbies must start 30 seconds after someone waits and fill to 8");
        }
        Fixture fixture = new Fixture(context, true);
        BlockPos center = context.absolutePos(new BlockPos(2, 2, 2));
        Match match = fixture.open(
                Minigames.MEETUP,
                TeamLayout.FREE_FOR_ALL,
                (server, values) -> NaturalArena.at(context.getLevel(), center.getX(), center.getZ(), 200));
        List<ServerPlayer> humans = fixture.join(match, 5, 0);
        match.voteStart(humans.get(0));
        match.voteStart(humans.get(1));
        context.assertTrue(match.phase() == MatchPhase.LOBBY && match.startVotes() == 2 && match.votesNeeded() == 3,
                "Two of five votes must not start the lobby");
        match.voteStart(humans.get(2));
        context.assertTrue(match.phase() == MatchPhase.COUNTDOWN, "Three of five votes must start the lobby, not " + match.phase());
        context.assertTrue(match.aliveCount() == 8, "Bots must fill the lobby to 8 participants, found " + match.aliveCount());
        List<ServerPlayer> bots = fixture.provider.spawned;
        context.assertTrue(bots.size() == 3 && fixture.provider.requests.size() == 1,
                "One request must spawn the 3 missing participants, spawned " + bots.size());
        Map<String, Object> request = fixture.provider.requests.getFirst();
        context.assertTrue(request.get("server") == context.getLevel().getServer()
                        && "meetup".equals(request.get("game"))
                        && String.valueOf(match.id()).equals(request.get("match"))
                        && Integer.valueOf(3).equals(request.get("count"))
                        && List.of().equals(request.get("names"))
                        && MatchBots.MIXED.equals(request.get("difficulty")),
                "Unexpected spawn request " + request);
        for (ServerPlayer bot : bots) {
            context.assertTrue(match.isBot(bot.getUUID()) && match.isAlive(bot.getUUID()) && match.teamOf(bot.getUUID()).isPresent(),
                    bot.getScoreboardName() + " must play like a joined participant");
            context.assertTrue(PlayerSnapshotStorage.hasSnapshot(context.getLevel().getServer(), bot.getUUID()),
                    bot.getScoreboardName() + "'s state must be saved like a player's");
            Component tab = bot.getTabListDisplayName();
            context.assertTrue(tab != null && tab.getString().equals("[BOT] " + bot.getScoreboardName()),
                    "Bots must be listed as [BOT] in the tab list, found " + tab);
            String teamName = match.teamOf(bot.getUUID()).orElseThrow().displayName().getString();
            context.assertTrue(teamName.contains("[BOT] "), "A free-for-all bot's team must read [BOT], found " + teamName);
        }
        for (ServerPlayer human : humans) {
            context.assertTrue(!match.isBot(human.getUUID()) && human.getTabListDisplayName() == null,
                    "Players must not be marked as bots");
        }
        MatchManager.stop(match);
        for (ServerPlayer bot : bots) {
            context.assertTrue(fixture.provider.removed.contains(bot.getUUID()), "Closing the match must hand every bot back");
            context.assertTrue(!PlayerSnapshotStorage.hasSnapshot(context.getLevel().getServer(), bot.getUUID()),
                    "A bot must be restored before it is handed back");
        }
        context.succeed();
    }

    /** The lobby timer runs only while someone waits; 30 seconds later the empty slots fill with bots. */
    public static void lobbyTimerStartsThirtySecondsAfterFirstWait(GameTestHelper context) throws MatchException {
        Fixture fixture = new Fixture(context, true);
        Match match = fixture.open(fixture.game(30, 4, false), TeamLayout.FREE_FOR_ALL, null);
        context.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    context.assertTrue(match.phase() == MatchPhase.LOBBY && match.autoStartSecondsLeft().isEmpty(),
                            "An empty lobby must not count down");
                    fixture.join(match, 1, 0);
                    context.assertTrue(match.autoStartSecondsLeft().orElse(0) == 30, "The countdown must start at 30 seconds");
                })
                .thenIdle(590)
                .thenExecute(() -> context.assertTrue(match.phase() == MatchPhase.LOBBY && fixture.provider.spawned.isEmpty(),
                        "The lobby must wait the full 30 seconds"))
                .thenIdle(15)
                .thenExecute(() -> {
                    context.assertTrue(match.phase() == MatchPhase.ACTIVE || match.phase() == MatchPhase.COUNTDOWN,
                            "The lobby must start 30 seconds after the first player waited, not " + match.phase());
                    context.assertTrue(match.aliveCount() == 4 && fixture.provider.spawned.size() == 3,
                            "The free-for-all must fill to lobby_size 4 with 3 bots");
                })
                .thenSucceed();
    }

    /** Without a provider the timer and votes start with the players present, at least 2. */
    public static void withoutProviderStartWithHumans(GameTestHelper context) throws MatchException {
        context.assertTrue(!MatchBots.available(), "No bot provider may be registered for this test");
        Fixture fixture = new Fixture(context, false);
        Match timed = fixture.open(fixture.game(1, 4, false), TeamLayout.FREE_FOR_ALL, null);
        Match voted = fixture.open(fixture.game(0, 4, false), TeamLayout.FREE_FOR_ALL, null);
        try {
            voted.addBots(0, 1);
            throw context.assertionException("Bot slots need a provider");
        } catch (MatchException expected) {
            context.assertTrue(expected.getMessage().contains("No bot provider"), expected.getMessage());
        }
        ServerPlayer first = fixture.join(timed, 1, 0).getFirst();
        List<ServerPlayer> voters = fixture.join(voted, 3, 0);
        voted.voteStart(voters.get(0));
        context.assertTrue(voted.phase() == MatchPhase.LOBBY, "One of three votes must not start the lobby");
        voted.voteStart(voters.get(1));
        context.assertTrue(voted.phase() == MatchPhase.COUNTDOWN && voted.aliveCount() == 3,
                "Two of three votes must start with the 3 players and no bots");
        Match teams = fixture.open(fixture.game(0, 4, false), TeamLayout.parse("2v2").orElseThrow(), null);
        List<ServerPlayer> pair = fixture.join(teams, 2, 0);
        teams.voteStart(pair.get(0));
        teams.voteStart(pair.get(1));
        context.assertTrue(teams.phase() == MatchPhase.COUNTDOWN && teams.standingTeams().size() == 2,
                "Two players starting a 2v2 early must be spread over both teams");
        context.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    context.assertTrue(timed.phase() == MatchPhase.LOBBY && timed.isWaiting(first.getUUID()),
                            "One player alone must keep waiting after the timer");
                    fixture.join(timed, 1, 0);
                })
                .thenIdle(3)
                .thenExecute(() -> context.assertTrue(
                        timed.phase() != MatchPhase.LOBBY && timed.aliveCount() == 2
                                && timed.alivePlayers().stream().noneMatch(player -> timed.isBot(player.getUUID())),
                        "A second player after the timer must start the match with the 2 players"))
                .thenSucceed();
    }

    /**
     * Players choose bot slots on any team of any layout: 2v3v4 with one player on each of the first
     * two teams and bots everywhere else, a 1v2 duel against two bots, a chosen kit, and a provider
     * that spawns fewer bots than asked.
     */
    public static void chosenSlotsInAnyLayout(GameTestHelper context) throws Exception {
        Fixture fixture = new Fixture(context, true);
        MinecraftServer server = context.getLevel().getServer();
        Match match = fixture.open(Minigames.CLASSIC, TeamLayout.parse("2v3v4").orElseThrow(), null);
        ServerPlayer first = fixture.join(match, 1, 1).getFirst();
        ServerPlayer second = fixture.join(match, 1, 2).getFirst();
        command(context, second, "minigames bots " + match.id() + " add 1 1");
        match.addBots(2, 2);
        try {
            match.addBots(3, 5);
            throw context.assertionException("Team 3 has only 4 slots");
        } catch (MatchException expected) {
            context.assertTrue(expected.getMessage().equals("Team 3 has room for 4 more."), expected.getMessage());
        }
        context.assertTrue(match.phase() == MatchPhase.LOBBY && match.freeSlots() == 4 && match.reservedBots() == 3,
                "Reserved bots must take slots without starting a lobby that is not full");
        match.addBots(3, 4);
        context.assertTrue(match.phase() == MatchPhase.COUNTDOWN, "Filling the last slot with bots must start the match");
        assertTeam(context, match, 1, List.of(first), 1);
        assertTeam(context, match, 2, List.of(second), 2);
        assertTeam(context, match, 3, List.of(), 4);

        ServerPlayer duelist = fixture.player();
        command(context, duelist, "duel classic 1v2 bots 0v2");
        Match duel = MatchManager.matchOf(duelist.getUUID()).orElseThrow(() -> context.assertionException("No duel opened"));
        fixture.matches.add(duel);
        context.assertTrue(duel.isPrivate() && duel.phase() != MatchPhase.LOBBY, "The bot duel must start at once");
        assertTeam(context, duel, 1, List.of(duelist), 0);
        assertTeam(context, duel, 2, List.of(), 2);

        ServerPlayer kitted = fixture.player();
        DuelRequests.challenge(kitted, Minigames.CLASSIC, TeamLayout.parse("1v1").orElseThrow(),
                List.of(Optional.empty()), BAREBONES,
                (ignored, values) -> BoxArena.open(context.getLevel(), 21, Blocks.SMOOTH_STONE.defaultBlockState()));
        Match kitDuel = MatchManager.matchOf(kitted.getUUID()).orElseThrow(() -> context.assertionException("No kit duel opened"));
        fixture.matches.add(kitDuel);
        context.assertTrue(kitDuel.kit().equals(BAREBONES), "A duel must use the chosen kit, found " + kitDuel.kit());

        fixture.provider.limit = 1;
        Match short1v1v1 = fixture.open(Minigames.CLASSIC, TeamLayout.parse("1v1v1").orElseThrow(), null);
        ServerPlayer alone = fixture.join(short1v1v1, 1, 0).getFirst();
        short1v1v1.addBots(0, 2);
        context.assertTrue(short1v1v1.phase() == MatchPhase.COUNTDOWN && short1v1v1.aliveCount() == 2
                        && short1v1v1.standingTeams().size() == 2 && short1v1v1.isAlive(alone.getUUID()),
                "A provider that spawns fewer bots must still start with the ones it gave, on separate teams");
        context.assertTrue(server.getPlayerList().getPlayers().containsAll(fixture.provider.spawned), "Bots must stay online while playing");
        context.succeed();
    }

    /**
     * An eliminated bot leaves the match restored and goes back to its provider; a bot its provider
     * removes mid-match is eliminated without leaving a combat logger.
     */
    public static void eliminatedAndRemovedBotsLeave(GameTestHelper context) throws MatchException {
        Fixture fixture = new Fixture(context, true);
        MinecraftServer server = context.getLevel().getServer();
        Match match = fixture.open(fixture.game(-1, 0, true), TeamLayout.parse("1v1v1").orElseThrow(), null);
        ServerPlayer human = fixture.join(match, 1, 1).getFirst();
        match.addBots(0, 2);
        ServerPlayer killed = fixture.provider.spawned.get(0);
        ServerPlayer removed = fixture.provider.spawned.get(1);
        context.startSequence()
                .thenWaitUntil(() -> context.assertTrue(match.phase() == MatchPhase.ACTIVE, "Waiting for the match to begin"))
                .thenExecute(() -> {
                    killed.hurtServer(killed.level(), killed.damageSources().genericKill(), Float.MAX_VALUE);
                    context.assertTrue(!match.isAlive(killed.getUUID()), "The killed bot must be eliminated");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    context.assertTrue(fixture.provider.removed.contains(killed.getUUID()) && !match.involves(killed.getUUID()),
                            "An eliminated bot must be handed back to its provider");
                    context.assertTrue(!PlayerSnapshotStorage.hasSnapshot(server, killed.getUUID()),
                            "An eliminated bot must be restored before it leaves");
                    server.getPlayerList().remove(removed);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    context.assertTrue(!match.isAlive(removed.getUUID()) && fixture.provider.removed.contains(removed.getUUID()),
                            "A bot removed by its provider must be eliminated, not kept alive by a combat logger");
                    context.assertTrue(match.phase() == MatchPhase.ENDED && match.winners().size() == 1
                                    && match.winners().getFirst().members().contains(human.getUUID()),
                            "The last player standing must win, not " + match.phase() + " " + match.winners());
                })
                .thenSucceed();
    }

    private static void assertTeam(GameTestHelper context, Match match, int number, List<ServerPlayer> humans, int bots) {
        MatchTeam team = match.teamNumbered(number).orElseThrow(() -> context.assertionException("No team " + number));
        long botCount = team.members().stream().filter(match::isBot).count();
        context.assertTrue(team.members().containsAll(humans.stream().map(ServerPlayer::getUUID).toList())
                        && botCount == bots && team.members().size() == humans.size() + bots,
                "Team %d must hold %s and %d bots, holds %s".formatted(number, humans, bots, team.members()));
    }

    /** Runs a player's command and fails the test with its output unless it succeeds. */
    private static void command(GameTestHelper context, ServerPlayer player, String command) {
        var server = player.level().getServer();
        var results = new int[1];
        List<String> output = new ArrayList<>();
        var source = player.createCommandSourceStack()
                .withSource(new net.minecraft.commands.CommandSource() {
                    public void sendSystemMessage(Component message) { output.add(message.getString()); }
                    public boolean acceptsSuccess() { return true; }
                    public boolean acceptsFailure() { return true; }
                    public boolean shouldInformAdmins() { return false; }
                })
                .withCallback((success, result) -> results[0] = success ? result : 0);
        server.getCommands().performPrefixedCommand(source, command);
        context.assertTrue(results[0] > 0, "/" + command + " failed: " + output);
    }

    /** Players, matches and the provider of one test, cleaned up when it ends. */
    private static final class Fixture {
        private final GameTestHelper context;
        private final MinecraftServer server;
        private final @Nullable TestBotProvider provider;
        private final List<ServerPlayer> players = new ArrayList<>();
        private final List<Match> matches = new ArrayList<>();

        Fixture(GameTestHelper context, boolean provider) {
            this.context = context;
            this.server = context.getLevel().getServer();
            this.provider = provider ? TestBotProvider.register(server) : null;
            GameTestLifecycle.afterTest(context, this::close);
        }

        /** A box-arena game with no countdown; {@code lobbySeconds} below 0 leaves out the lobby settings. */
        Minigame game(int lobbySeconds, int lobbySize, boolean combatLoggers) {
            List<GameSetting> settings = new ArrayList<>(GameSetting.common(0, 0, false));
            if (lobbySeconds >= 0) settings.addAll(GameSetting.lobby(lobbySeconds, lobbySize));
            return new Minigame() {
                public String id() { return "bots_test"; }
                public String displayName() { return "Bots test"; }
                public List<GameSetting> settings() { return settings; }
                public Identifier defaultKit() { return BAREBONES; }
                public GameType playerGameMode() { return GameType.SURVIVAL; }
                public boolean combatLoggers() { return combatLoggers; }
                public Arena openArena(MinecraftServer ignored, GameSettings values) {
                    return BoxArena.open(context.getLevel(), 21, Blocks.SMOOTH_STONE.defaultBlockState());
                }
            };
        }

        Match open(Minigame game, TeamLayout layout, MatchManager.@Nullable ArenaFactory arena) throws MatchException {
            Match match = MatchManager.open(server, game, layout, null, arena != null ? arena
                    : (ignored, values) -> BoxArena.open(context.getLevel(), 21, Blocks.SMOOTH_STONE.defaultBlockState()));
            matches.add(match);
            return match;
        }

        ServerPlayer player() {
            ServerPlayer player = TestPlayers.connect(context, "Fill" + NAMES.incrementAndGet());
            players.add(player);
            return player;
        }

        List<ServerPlayer> join(Match match, int count, int team) {
            List<ServerPlayer> joined = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                ServerPlayer player = player();
                try {
                    MatchManager.join(player, match, team);
                } catch (MatchException exception) {
                    throw context.assertionException(exception.getMessage());
                }
                joined.add(player);
            }
            return joined;
        }

        void close() {
            matches.forEach(MatchManager::stop);
            players.forEach(player -> server.getPlayerList().remove(player));
            if (provider != null) provider.close();
        }
    }
}
