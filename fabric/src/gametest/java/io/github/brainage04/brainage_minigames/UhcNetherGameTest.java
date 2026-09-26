package io.github.brainage04.brainage_minigames;

import com.mojang.authlib.GameProfile;
import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.uhc.UhcGame;
import io.github.brainage04.brainage_minigames.game.uhc.UhcNether;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The UHC nether on dimensions {@link UhcTestDimensions} creates: portals between it and the UHC
 * dimension, and a UHC whose players go there. Each test has an environment of its own, so it runs
 * in a batch of its own: a running UHC owns the borders of both dimensions.
 */
public final class UhcNetherGameTest {
    private static final AtomicInteger NEXT_NAME = new AtomicInteger();

    /**
     * A portal lit in the UHC dimension takes a player to the UHC nether at an eighth of their
     * coordinates, a spectator watching them keeps watching them there, and the portal they arrive
     * in takes them back to the UHC dimension at eight times their nether coordinates.
     */
    @GameTest(environment = "brainage_minigames:uhc_nether_portals", maxTicks = 700)
    public void portalsLinkTheUhcDimensionAndItsNether(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        UhcTestDimensions.ensure(server);
        ServerLevel uhc = server.getLevel(ModDimensions.UHC);
        ServerLevel nether = server.getLevel(ModDimensions.UHC_NETHER);
        int x = 8 * (60_000 + uhc.getRandom().nextInt(10_000) * 4);
        int z = -8 * (60_000 + uhc.getRandom().nextInt(10_000) * 4);
        BlockPos inside = buildLitPortal(uhc, x, z);
        assertTrue(
                uhc.getBlockState(inside).is(Blocks.NETHER_PORTAL),
                "Expected fire to light a portal in the UHC dimension, found "
                        + uhc.getBlockState(inside)
                        + ".");

        ChatPlayer traveller = player(context);
        ChatPlayer watcher = player(context);
        traveller.setGameMode(GameType.SURVIVAL);
        watcher.setGameMode(GameType.SPECTATOR);
        PlayerUtils.teleport(traveller, uhc, new Vec3(x + 2.0, inside.getY(), z + 0.5), 0.0F);
        watcher.setCamera(traveller);
        Vec3 entry = traveller.position();
        Vec3[] arrival = {Vec3.ZERO};

        int[] stage = {0};
        context.onEachTick(
                () -> {
                    try {
                        // What the connection ticks for a real client.
                        traveller.doTick();
                        switch (stage[0]) {
                            case 0 -> {
                                if (traveller.level() == nether) {
                                    assertNear(entry.x() / 8.0, traveller.getX(), "nether x");
                                    assertNear(entry.z() / 8.0, traveller.getZ(), "nether z");
                                    assertTrue(
                                            nether.getBlockState(traveller.blockPosition())
                                                    .is(Blocks.NETHER_PORTAL),
                                            "Expected to arrive in a portal built in the nether.");
                                    arrival[0] = traveller.position();
                                    stage[0] = 1;
                                }
                            }
                            case 1 -> {
                                if (watcher.level() == nether && watcher.getCamera() == traveller) {
                                    // Step out of the portal and back in.
                                    traveller.portalProcess = null;
                                    traveller.setPortalCooldown(0);
                                    stage[0] = 2;
                                }
                            }
                            case 2 -> {
                                if (traveller.level() == uhc) {
                                    // Vanilla links to the nearest portal within 128 blocks of
                                    // eight times the nether position, or builds one within 16.
                                    Vec3 scaled = arrival[0].multiply(8.0, 1.0, 8.0);
                                    assertTrue(
                                            Math.abs(traveller.getX() - scaled.x()) <= 130.0
                                                    && Math.abs(traveller.getZ() - scaled.z())
                                                            <= 130.0,
                                            "Expected to come back near "
                                                    + scaled
                                                    + ", found "
                                                    + traveller.position()
                                                    + ".");
                                    stage[0] = 3;
                                }
                            }
                            case 3 -> {
                                if (watcher.level() == uhc && watcher.getCamera() == traveller) {
                                    stage[0] = 4;
                                    disconnect(traveller, watcher);
                                    context.succeed();
                                }
                            }
                            default -> {}
                        }
                    } catch (RuntimeException exception) {
                        disconnect(traveller, watcher);
                        throw exception;
                    }
                });
    }

    /**
     * A UHC's players in its nether stay in the match: its border is there, scaled; a death there
     * eliminates; watchers see them; when the nether closes they are brought back inside the
     * border, and stopping the match returns everyone, even from the nether.
     */
    @GameTest(environment = "brainage_minigames:uhc_nether_match", maxTicks = 1_500)
    public void uhcPlayersInTheNetherStayInTheMatchUntilItCloses(GameTestHelper context)
            throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        UhcTestDimensions.ensure(server);
        ServerLevel uhc = server.getLevel(ModDimensions.UHC);
        ServerLevel nether = server.getLevel(ModDimensions.UHC_NETHER);
        Minigame game = Minigames.UHC;
        GameSetting countdown = game.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow();
        List<GameSetting> changed =
                List.of(
                        countdown,
                        UhcGame.BORDER_START_SIZE,
                        UhcGame.FIRST_SHRINK_TIME,
                        UhcGame.FINAL_SHRINK_TIME,
                        UhcGame.NETHER_CLOSE_TIME);
        SettingsStorage.set(server, game, countdown, 0);
        SettingsStorage.set(server, game, UhcGame.BORDER_START_SIZE, 400);
        SettingsStorage.set(server, game, UhcGame.FIRST_SHRINK_TIME, 2);
        SettingsStorage.set(server, game, UhcGame.FINAL_SHRINK_TIME, 3);
        SettingsStorage.set(server, game, UhcGame.NETHER_CLOSE_TIME, 1);

        ChatPlayer explorer = player(context);
        ChatPlayer victim = player(context);
        ChatPlayer stayer = player(context);
        ChatPlayer watcher = player(context);
        Match match;
        try {
            match = MatchManager.open(server, game, TeamLayout.FREE_FOR_ALL, null);
        } catch (MatchException | RuntimeException exception) {
            changed.forEach(setting -> SettingsStorage.reset(server, game, setting));
            disconnect(explorer, victim, stayer, watcher);
            throw exception;
        }
        Runnable cleanup =
                () -> {
                    MatchManager.stop(match);
                    changed.forEach(setting -> SettingsStorage.reset(server, game, setting));
                    disconnect(explorer, victim, stayer, watcher);
                };
        try {
            MatchManager.join(explorer, match, 0);
            MatchManager.join(victim, match, 0);
            MatchManager.join(stayer, match, 0);
            match.start();
        } catch (MatchException | RuntimeException exception) {
            cleanup.run();
            throw exception;
        }

        WorldBorder border = uhc.getWorldBorder();
        context.runAfterDelay(
                5,
                () -> {
                    try {
                        assertEquals(MatchPhase.ACTIVE, match.phase(), "phase");
                        WorldBorder netherBorder = nether.getWorldBorder();
                        assertEquals(
                                border.getCenterX() / 8.0,
                                netherBorder.getCenterX(),
                                "nether border centre x");
                        assertEquals(
                                border.getCenterZ() / 8.0,
                                netherBorder.getCenterZ(),
                                "nether border centre z");
                        assertEquals(50.0, netherBorder.getSize(), "nether border size");
                        assertReceived(explorer, "The nether closes in 1 minute.");
                        assertReceived(explorer, "The border starts shrinking in 2 minutes.");
                        assertTrue(
                                UhcNether.destination(uhc, explorer) == nether,
                                "Expected portals to lead the match's players into its nether.");

                        Vec3 netherSpot =
                                new Vec3(
                                        netherBorder.getCenterX() + 5.5,
                                        64.0,
                                        netherBorder.getCenterZ() + 3.5);
                        PlayerUtils.teleport(explorer, nether, netherSpot, 0.0F);
                        PlayerUtils.teleport(victim, nether, netherSpot, 0.0F);
                        // What their clients confirm once they are in the new dimension; until
                        // then players cannot be hurt.
                        explorer.hasChangedDimension();
                        victim.hasChangedDimension();

                        // A client already spectating can watch the running match, then spectate
                        // a player in the nether.
                        watcher.setGameMode(GameType.SPECTATOR);
                        run(watcher, "minigames watch " + match.id());
                        assertTrue(
                                match.involves(watcher.getUUID()),
                                "Expected a spectating client to watch the running match.");
                        assertTrue(watcher.level() == uhc, "Expected the watcher at the arena.");
                        watcher.setCamera(explorer);
                        assertTrue(
                                watcher.level() == nether,
                                "Expected spectating to follow the explorer into the nether.");
                    } catch (RuntimeException exception) {
                        cleanup.run();
                        throw exception;
                    }
                });
        context.runAfterDelay(
                10,
                () -> {
                    try {
                        assertTrue(
                                match.isAlive(explorer.getUUID())
                                        && match.isAlive(victim.getUUID()),
                                "Expected players in the nether to stay alive in the match.");
                        victim.kill(nether);
                        assertTrue(
                                !match.isAlive(victim.getUUID()) && victim.isSpectator(),
                                "Expected a death in the nether to eliminate the victim.");
                        assertReceived(stayer, "(2 left)");
                        assertEquals(MatchPhase.ACTIVE, match.phase(), "phase after the death");
                    } catch (RuntimeException exception) {
                        cleanup.run();
                        throw exception;
                    }
                });
        // Active from tick 1 or 2; the nether closes one minute later.
        context.runAfterDelay(
                1_150,
                () -> {
                    try {
                        assertTrue(
                                explorer.level() == nether,
                                "Expected the explorer still in the nether before it closes.");
                    } catch (RuntimeException exception) {
                        cleanup.run();
                        throw exception;
                    }
                });
        context.runAfterDelay(
                1_220,
                () -> {
                    try {
                        assertTrue(
                                explorer.level() == uhc,
                                "Expected the explorer brought back when the nether closed.");
                        assertTrue(
                                border.isWithinBounds(explorer.blockPosition()),
                                "Expected the explorer inside the border, at "
                                        + explorer.position()
                                        + ".");
                        assertTrue(
                                match.isAlive(explorer.getUUID()),
                                "Expected the explorer still alive.");
                        assertReceived(explorer, "The nether has closed;");
                        assertTrue(
                                UhcNether.destination(uhc, explorer) == null,
                                "Expected portals to stop leading into the closed nether.");
                    } catch (RuntimeException exception) {
                        cleanup.run();
                        throw exception;
                    }
                });
        context.runAfterDelay(
                1_400,
                () -> {
                    try {
                        assertTrue(
                                watcher.level() == uhc && watcher.getCamera() == explorer,
                                "Expected the watcher to keep watching the explorer out of the nether.");
                        assertTrue(
                                victim.level() == nether, "Expected the victim spectating there.");
                        MatchManager.stop(match);
                        assertTrue(
                                victim.level() == context.getLevel()
                                        && explorer.level() == context.getLevel(),
                                "Expected stopping the match to return everyone, even from the nether.");
                    } finally {
                        cleanup.run();
                    }
                    context.succeed();
                });
    }

    /**
     * Builds an obsidian frame on the ground at x, z facing along X and lights it with fire;
     * returns the lower portal block beside the frame's left side.
     */
    private static BlockPos buildLitPortal(ServerLevel level, int x, int z) {
        level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        level.getChunk(SectionPos.blockToSectionCoord(x + 3), SectionPos.blockToSectionCoord(z));
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx <= 3; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                boolean frame = dx == 0 || dx == 3 || dy == 0 || dy == 4;
                level.setBlock(
                        pos.set(x + dx, y + dy, z),
                        frame
                                ? Blocks.OBSIDIAN.defaultBlockState()
                                : Blocks.AIR.defaultBlockState(),
                        3);
            }
        }
        BlockPos inside = new BlockPos(x + 1, y + 1, z);
        level.setBlock(inside, Blocks.FIRE.defaultBlockState(), 3);
        return inside;
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
                        new GameProfile(UUID.randomUUID(), "nether" + NEXT_NAME.incrementAndGet()),
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

    private static void assertReceived(ChatPlayer player, String text) {
        assertTrue(
                player.messages.stream().anyMatch(message -> message.getString().contains(text)),
                "Expected "
                        + player.getScoreboardName()
                        + " to be told '"
                        + text
                        + "', found "
                        + player.messages.stream().map(Component::getString).toList()
                        + ".");
    }

    /** Vanilla finds or builds the exit portal within 16 blocks of the scaled position. */
    private static void assertNear(double expected, double actual, String description) {
        assertTrue(
                Math.abs(expected - actual) <= 17.0,
                "Expected " + description + " near " + expected + ", found " + actual + ".");
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
