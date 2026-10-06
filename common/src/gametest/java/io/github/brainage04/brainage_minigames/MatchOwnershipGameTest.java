package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.game.DuelRequests;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.MatchService;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Who may open, start and stop matches, driven through the real commands where players use them. */
public final class MatchOwnershipGameTest {
    /**
     * A player without operator permission opens a match with {@code /minigames open}, owns it and
     * starts it; another player can neither start nor stop it; an operator can stop it.
     */
    public void anyPlayerOpensAndManagesOwnMatch(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        ServerPlayer owner = TestPlayers.connect(context, "owner_alice");
        ServerPlayer other = TestPlayers.connect(context, "other_bob");
        ServerPlayer operator = TestPlayers.connect(context, "op_carol");
        TestPlayers.setOperator(operator, true);
        try {
            assertTrue(!MatchService.isOperator(owner) && !MatchService.isOperator(other),
                    "Expected the test players to have no operator permission.");
            assertTrue(MatchService.isOperator(operator), "Expected the opped player to be an operator.");

            run(owner, "minigames open gapple ffa");
            List<Match> owned = MatchService.ownedBy(owner.getUUID());
            assertTrue(owned.size() == 1, "Expected /minigames open by a player to open one match they own, found " + owned.size() + ".");
            Match match = owned.getFirst();
            assertTrue(owner.getUUID().equals(match.owner()), "Expected the opener to own the match.");
            assertTrue(match.describe().getString().contains("opened by owner_alice"),
                    "Expected the match summary to name its owner: " + match.describe().getString());

            assertTrue(match.isWaiting(owner.getUUID()), "Expected the opener to be waiting in the match they opened.");
            MatchManager.join(other, match, 0);
            run(other, "minigames start " + match.id());
            assertTrue(match.phase() == MatchPhase.LOBBY, "Expected a non-owner's start to be refused.");
            assertRefused(() -> MatchService.start(other, match), "start by a non-owner");

            run(owner, "minigames start " + match.id());
            assertTrue(match.phase() == MatchPhase.COUNTDOWN, "Expected the owner's start to start the match, found " + match.phase() + ".");

            run(other, "minigames stop " + match.id());
            assertTrue(MatchManager.get(match.id()).isPresent(), "Expected a non-owner's stop to be refused.");
            assertRefused(() -> MatchService.stop(other, match), "stop by a non-owner");

            run(operator, "minigames stop " + match.id());
            assertTrue(MatchManager.get(match.id()).isEmpty(), "Expected an operator to stop any match.");
        } finally {
            stopOwned(owner, other, operator);
            TestPlayers.setOperator(operator, false);
            TestPlayers.disconnect(owner, other, operator);
        }
        context.succeed();
    }

    /**
     * A player who opens a match with {@code /minigames open} plays in it; with a trailing {@code
     * nojoin} they only own it; the console only opens one; a player who could not play is refused
     * before anything opens.
     */
    public void openJoinsTheOpenerUnlessNoJoin(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        TestPlayers.ChatPlayer opener = TestPlayers.chat(context, "opener_dana");
        TestPlayers.ChatPlayer host = TestPlayers.chat(context, "host_erin");
        Collection<Match> before = MatchManager.matches();
        try {
            run(opener, "minigames open gapple ffa");
            List<Match> opened = MatchService.ownedBy(opener.getUUID());
            assertTrue(opened.size() == 1, "Expected /minigames open to open one match, found " + opened.size() + ".");
            Match joined = opened.getFirst();
            assertTrue(joined.isWaiting(opener.getUUID()), "Expected /minigames open to put the opener in its lobby.");

            MatchManager.join(host, joined, 0);
            host.messages.clear();
            run(host, "minigames open classic 1v1");
            assertTrue(MatchService.ownedBy(host.getUUID()).isEmpty(),
                    "Expected a player who is already playing not to open a match to play in.");
            assertTrue(host.messages.stream().anyMatch(message -> message.getString().contains("nojoin")),
                    "Expected the refusal to suggest nojoin: " + host.messages);

            run(host, "minigames open classic 1v1 brainage_minigames:kits/no_debuff nojoin");
            List<Match> hosted = MatchService.ownedBy(host.getUUID());
            assertTrue(hosted.size() == 1, "Expected nojoin with a kit to open a match, found " + hosted.size() + ".");
            assertTrue(hosted.getFirst().kit().getPath().equals("kits/no_debuff"), "Expected the chosen kit.");
            assertTrue(!hosted.getFirst().involves(host.getUUID()), "Expected nojoin to leave the opener out.");
            assertTrue(joined.isWaiting(host.getUUID()), "Expected nojoin to keep the opener in the match they were in.");
            MatchManager.stop(hosted.getFirst());

            run(host, "minigames leave");
            run(host, "minigames open classic 1v1 nojoin");
            hosted = MatchService.ownedBy(host.getUUID());
            assertTrue(hosted.size() == 1 && !hosted.getFirst().involves(host.getUUID()),
                    "Expected nojoin without a kit to open a match without its opener.");
            MatchManager.stop(hosted.getFirst());

            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "minigames open gapple ffa");
            List<Match> byServer = MatchManager.matches().stream()
                    .filter(match -> !before.contains(match) && match.owner() == null).toList();
            assertTrue(byServer.size() == 1 && byServer.getFirst().onlineMembers().isEmpty(),
                    "Expected the console to open one match nobody is in, found " + byServer.size() + ".");
            MatchManager.stop(byServer.getFirst());
        } finally {
            stopOwned(opener, host);
            TestPlayers.disconnect(opener, host);
        }
        context.succeed();
    }

    /**
     * Players own at most {@code max_open_matches_per_player} matches, counting duels they
     * challenged others to; operators are never limited; stopping a match frees its slot.
     */
    public void openMatchLimitPerPlayer(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        ServerPlayer player = TestPlayers.connect(context, "limit_dave");
        ServerPlayer invitee = TestPlayers.connect(context, "limit_erin");
        ServerPlayer operator = TestPlayers.connect(context, "limit_op");
        TestPlayers.setOperator(operator, true);
        int limit = server.getGameRules().get(MatchService.MAX_OPEN_MATCHES);
        TeamLayout ffa = TeamLayout.FREE_FOR_ALL;
        try {
            assertTrue(limit == 1, "Expected one open match per player by default, found " + limit + ".");
            Match first = MatchService.open(player, Minigames.GAPPLE, ffa, null);
            assertRefused(() -> MatchService.open(player, Minigames.GAPPLE, ffa, null), "a second match over the limit");
            assertRefused(() -> DuelRequests.challenge(player, Minigames.CLASSIC, TeamLayout.parse("1v1").orElseThrow(),
                    List.of(Optional.of(invitee)), null,
                    (s, settings) -> Minigames.CLASSIC.openArena(s, settings, TeamLayout.parse("1v1").orElseThrow())),
                    "a duel challenge over the limit");

            server.getGameRules().set(MatchService.MAX_OPEN_MATCHES, 2, server);
            MatchService.open(player, Minigames.GAPPLE, ffa, null);
            assertRefused(() -> MatchService.open(player, Minigames.GAPPLE, ffa, null), "a third match over a limit of two");

            MatchService.stop(player, first);
            MatchService.open(player, Minigames.GAPPLE, ffa, null);
            assertTrue(MatchService.ownedBy(player.getUUID()).size() == 2, "Expected stopping a match to free its slot.");

            for (int i = 0; i < 3; i++) MatchService.open(operator, Minigames.GAPPLE, ffa, null);
            assertTrue(MatchService.ownedBy(operator.getUUID()).size() == 3, "Expected operators to open any number of matches.");
        } finally {
            server.getGameRules().set(MatchService.MAX_OPEN_MATCHES, limit, server);
            DuelRequests.clear();
            stopOwned(player, invitee, operator);
            TestPlayers.setOperator(operator, false);
            TestPlayers.disconnect(player, invitee, operator);
        }
        context.succeed();
    }

    @FunctionalInterface
    private interface Refusable {
        void run() throws MatchException;
    }

    private static void assertRefused(Refusable action, String description) {
        try {
            action.run();
        } catch (MatchException expected) {
            return;
        }
        throw failure("Expected " + description + " to be refused.");
    }

    private static void run(ServerPlayer player, String command) {
        player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }

    private static void stopOwned(ServerPlayer... players) {
        for (ServerPlayer player : players) {
            MatchService.ownedBy(player.getUUID()).forEach(MatchManager::stop);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw failure(message);
    }

    private static GameTestAssertException failure(String message) {
        return new GameTestAssertException(Component.literal(message), 0);
    }
}
