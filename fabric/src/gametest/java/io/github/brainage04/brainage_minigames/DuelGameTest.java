package io.github.brainage04.brainage_minigames;

import com.mojang.authlib.GameProfile;
import io.github.brainage04.brainage_minigames.game.DuelRequests;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.MatchTeam;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.BoxArena;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code /duel} driven through the real command dispatcher by players with distinct names (the
 * GameTest helper's mock players all share one name, which name arguments cannot tell apart).
 */
public final class DuelGameTest {
    private static final AtomicInteger NEXT_NAME = new AtomicInteger();

    @GameTest
    public void inviteCarriesAcceptCommandAndPlayerListAndDenyCancels(GameTestHelper context) {
        ChatPlayer alice = player(context);
        ChatPlayer bob = player(context);
        try {
            alice.messages.clear();
            bob.messages.clear();
            run(alice, "duel classic 1v1 " + bob.getScoreboardName());

            List<Component> invites =
                    bob.messages.stream().filter(message -> acceptButton(message) != null).toList();
            assertEquals(1, invites.size(), "invite messages");
            Component accept = acceptButton(invites.getFirst());
            assertEquals(
                    new ClickEvent.RunCommand("/duel accept " + alice.getScoreboardName()),
                    accept.getStyle().getClickEvent(),
                    "accept click event");
            if (!(accept.getStyle().getHoverEvent() instanceof HoverEvent.ShowText hover)) {
                throw failure("Expected the accept button to show text on hover.");
            }
            assertEquals(
                    "Players: " + alice.getScoreboardName() + ", " + bob.getScoreboardName(),
                    hover.value().getString(),
                    "accept hover text");
            assertEquals(
                    List.of(alice.getScoreboardName()),
                    DuelRequests.challengersOf(bob.getUUID()),
                    "challengers of the invitee");

            alice.messages.clear();
            run(bob, "duel deny " + alice.getScoreboardName());
            assertTrue(
                    DuelRequests.challengersOf(bob.getUUID()).isEmpty(),
                    "Expected denying to remove the request.");
            assertTrue(!alice.messages.isEmpty(), "Expected the challenger to be told.");
            assertTrue(
                    MatchManager.matchOf(alice.getUUID()).isEmpty(),
                    "Expected no match after a denial.");
        } finally {
            disconnect(alice, bob);
        }
        context.succeed();
    }

    @GameTest
    public void acceptingFillsTeamsInListedOrderAndStartsTheMatch(GameTestHelper context)
            throws MatchException {
        ChatPlayer alice = player(context);
        ChatPlayer bob = player(context);
        ChatPlayer carol = player(context);
        ChatPlayer dave = player(context);
        Match match = null;
        try {
            DuelRequests.challenge(
                    alice,
                    Minigames.CLASSIC,
                    TeamLayout.parse("2v2").orElseThrow(),
                    List.of(bob, carol, dave),
                    (server, settings) ->
                            BoxArena.open(
                                    context.getLevel(),
                                    21,
                                    Blocks.SMOOTH_STONE.defaultBlockState()));
            run(bob, "duel accept " + alice.getScoreboardName());
            run(carol, "duel accept " + alice.getScoreboardName());
            assertTrue(
                    MatchManager.matchOf(alice.getUUID()).isEmpty(),
                    "Expected no match until every invitee accepts.");
            run(dave, "duel accept " + alice.getScoreboardName());

            match =
                    MatchManager.matchOf(alice.getUUID())
                            .orElseThrow(
                                    () -> failure("Expected the last acceptance to open a match."));
            assertTrue(
                    match.phase() == MatchPhase.COUNTDOWN || match.phase() == MatchPhase.ACTIVE,
                    "Expected the match to start, found " + match.phase() + ".");
            MatchTeam first = team(match, alice);
            MatchTeam second = team(match, carol);
            assertTrue(first != second, "Expected the challenger's opponents on another team.");
            assertEquals(first, team(match, bob), "the second listed player's team");
            assertEquals(second, team(match, dave), "the fourth listed player's team");
            assertTrue(alice.getTeam() != null, "Expected a scoreboard team during the match.");
            assertEquals(alice.getTeam(), bob.getTeam(), "teammates' scoreboard team");
            assertTrue(
                    alice.getTeam() != carol.getTeam(),
                    "Expected opponents on another scoreboard team.");
            assertTrue(
                    DuelRequests.challengersOf(dave.getUUID()).isEmpty(),
                    "Expected the request to be consumed.");
        } finally {
            if (match != null) {
                MatchManager.stop(match);
            }
            disconnect(alice, bob, carol, dave);
        }
        context.succeed();
    }

    @GameTest
    public void invalidChallengesAreRefused(GameTestHelper context) {
        ChatPlayer alice = player(context);
        ChatPlayer bob = player(context);
        ChatPlayer carol = player(context);
        String bobName = bob.getScoreboardName();
        try {
            for (String command :
                    new String[] {
                        "duel classic 1v1 " + bobName + " " + carol.getScoreboardName(),
                        "duel classic 2v2 " + bobName,
                        "duel classic 1v1v1 " + bobName + " " + bobName,
                        "duel classic 1v1 " + alice.getScoreboardName(),
                        "duel classic 3 " + bobName,
                        "duel nonexistent 1v1 " + bobName
                    }) {
                alice.messages.clear();
                run(alice, command);
                assertTrue(
                        DuelRequests.challengersOf(bob.getUUID()).isEmpty(),
                        "Expected '" + command + "' to be refused.");
                assertTrue(
                        !alice.messages.isEmpty(),
                        "Expected '" + command + "' to tell the challenger why.");
            }

            run(alice, "duel classic 1v1 " + bobName);
            run(carol, "duel classic 1v1 " + bobName);
            assertEquals(
                    List.of(alice.getScoreboardName()),
                    DuelRequests.challengersOf(bob.getUUID()),
                    "challengers of a player already invited");

            run(alice, "duel cancel");
            assertTrue(
                    DuelRequests.challengersOf(bob.getUUID()).isEmpty(),
                    "Expected /duel cancel to withdraw the request.");

            run(alice, "duel classic 1v1 " + bobName);
            disconnect(bob);
            run(alice, "duel classic 1v1 " + carol.getScoreboardName());
            assertEquals(
                    List.of(alice.getScoreboardName()),
                    DuelRequests.challengersOf(carol.getUUID()),
                    "challengers after the previous invitee disconnected");
            run(alice, "duel cancel");
        } finally {
            disconnect(alice, bob, carol);
        }
        context.succeed();
    }

    /** A connected, loaded player with a unique name that records the chat it is sent. */
    private static final class ChatPlayer extends ServerPlayer {
        private final List<Component> messages = new ArrayList<>();

        private ChatPlayer(
                MinecraftServer server,
                ServerLevel level,
                GameProfile profile,
                ClientInformation information) {
            super(server, level, profile, information);
        }

        @Override
        public void sendSystemMessage(Component message, boolean overlay) {
            if (!overlay) {
                messages.add(message);
            }
            super.sendSystemMessage(message, overlay);
        }
    }

    private static ChatPlayer player(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(
                        new GameProfile(UUID.randomUUID(), "duelist" + NEXT_NAME.incrementAndGet()),
                        false);
        ChatPlayer player =
                new ChatPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        return player;
    }

    /** Removes the players still online, as their clients disconnecting would. */
    private static void disconnect(ServerPlayer... players) {
        for (ServerPlayer player : players) {
            var playerList = player.level().getServer().getPlayerList();
            if (playerList.getPlayer(player.getUUID()) == player) {
                playerList.remove(player);
            }
        }
    }

    private static void run(ServerPlayer player, String command) {
        player.level()
                .getServer()
                .getCommands()
                .performPrefixedCommand(player.createCommandSourceStack(), command);
    }

    /** The part of {@code message} that runs {@code /duel accept}, or null. */
    private static Component acceptButton(Component message) {
        for (Component part : message.toFlatList()) {
            if (part.getStyle().getClickEvent() instanceof ClickEvent.RunCommand(String command)
                    && command.startsWith("/duel accept ")) {
                return part;
            }
        }
        return null;
    }

    private static MatchTeam team(Match match, ServerPlayer player) {
        return match.teamOf(player.getUUID())
                .orElseThrow(
                        () -> failure("Expected " + player.getScoreboardName() + " on a team."));
    }

    private static void assertEquals(Object expected, Object actual, String description) {
        if (!expected.equals(actual)) {
            throw failure("Expected " + description + " " + expected + ", found " + actual + ".");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw failure(message);
        }
    }

    private static GameTestAssertException failure(String message) {
        return new GameTestAssertException(Component.literal(message), 0);
    }
}
