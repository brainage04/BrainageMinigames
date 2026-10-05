package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.TestPlayers;
import com.mojang.authlib.GameProfile;
import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.GameTestLifecycle;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.phys.Vec3;

/** Replays actual arena searches and ticketed spawn preparation, not just PRNG samples. */
public final class UhcRegionGameTestFunctions {
    private UhcRegionGameTestFunctions() {}

    public static void regionSeed(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        var storage = BrainageMinigames.id("settings");
        var saved = server.getCommandStorage().get(storage).copy();
        Match[] current = {null};
        List<ServerPlayer> players = new ArrayList<>();
        List<EmbeddedChannel> channels = new ArrayList<>();
        GameTestLifecycle.afterTest(context, () -> {
            if (current[0] != null) MatchManager.stop(current[0]);
            for (ServerPlayer player : players) server.getPlayerList().remove(player);
            for (EmbeddedChannel channel : channels) channel.finishAndReleaseAll();
            server.getCommandStorage().set(storage, saved);
        });
        try {
            for (Minigame game : List.of(Minigames.UHC, Minigames.MEETUP, Minigames.FINAL_UHC)) {
                for (GameSetting setting : game.settings()) SettingsStorage.reset(server, game, setting);
                var level = NaturalTerrain.level(server, game == Minigames.UHC ? ModDimensions.UHC
                        : game == Minigames.MEETUP ? ModDimensions.MEETUP : ModDimensions.FINAL_UHC);
                var unset = SettingsStorage.resolve(server, game);
                context.assertFalse(unset.isOverridden(UhcGame.REGION_SEED), "Region seed default was set");
                context.assertTrue(NaturalTerrain.regionRandom(level, unset) == level.getRandom(),
                        "Unset region seed replaced the live world RNG");
                context.assertTrue(NaturalTerrain.spawnRandom(level, unset) == level.getRandom(),
                        "Unset region seed replaced the live spawn RNG");
                SettingsStorage.set(server, game, UhcGame.REGION_SEED, 0);
                current[0] = open(server, game);
                Match first = current[0];
                BlockPos firstCenter = center(first.arena());
                var firstRandom = NaturalTerrain.regionRandom(level, first.settings());
                SettingsStorage.set(server, game, UhcGame.REGION_SEED, 1);
                context.assertValueEqual(0, first.settings().get(UhcGame.REGION_SEED), "Match seed snapshot");
                context.assertTrue(first.settings().isOverridden(UhcGame.REGION_SEED), "Explicit zero was unset");
                MatchManager.stop(first);
                SettingsStorage.set(server, game, UhcGame.REGION_SEED, 0);
                // Unrelated world randomness must not change any of the sixteen candidates.
                for (int i = 0; i < 32; i++) level.getRandom().nextLong();
                current[0] = open(server, game);
                context.assertTrue(firstCenter.equals(center(current[0].arena())), game.id() + " did not replay its centre");
                var secondRandom = NaturalTerrain.regionRandom(level, current[0].settings());
                for (int i = 0; i < 16; i++) {
                    context.assertTrue(Arrays.equals(NaturalTerrain.randomRegionCenter(firstRandom),
                            NaturalTerrain.randomRegionCenter(secondRandom)), "Candidate sequence changed at " + i);
                }
                MatchManager.stop(current[0]);
                SettingsStorage.set(server, game, UhcGame.REGION_SEED, 1);
                current[0] = open(server, game);
                context.assertFalse(firstCenter.equals(center(current[0].arena())), game.id() + " ignored a different seed");
                MatchManager.stop(current[0]);
                current[0] = null;
                SettingsStorage.reset(server, game, UhcGame.REGION_SEED);
                context.assertTrue(NaturalTerrain.regionRandom(level, SettingsStorage.resolve(server, game)) == level.getRandom(),
                        "Reset did not restore random selection");
            }
            SettingsStorage.set(server, Minigames.UHC, UhcGame.REGION_SEED, 0);
            for (int i = 0; i < 8; i++) {
                var cookie = CommonListenerCookie.createInitial(
                        new GameProfile(UUID.randomUUID(), "seed" + UUID.randomUUID().toString().substring(0, 8)), false);
                var player = new ServerPlayer(server, context.getLevel(), cookie.gameProfile(), cookie.clientInformation());
                channels.add(TestPlayers.connect(player, cookie));
                players.add(player);
            }
            startPlayers(context, current, players);
            awaitSpawns(context, current, players, null, null);
        } catch (MatchException exception) {
            throw context.assertionException(exception.getMessage());
        }
    }

    private static Match open(MinecraftServer server, Minigame game) throws MatchException {
        return MatchManager.open(server, game, TeamLayout.parse("ffa").orElseThrow(), null);
    }

    private static BlockPos center(Arena arena) {
        if (arena instanceof UhcArena uhc) {
            return BlockPos.containing(uhc.border().getCenterX(), 0, uhc.border().getCenterZ());
        }
        NaturalArena natural = (NaturalArena) arena;
        return new BlockPos(natural.centerX(), 0, natural.centerZ());
    }

    private static void startPlayers(GameTestHelper context, Match[] current, List<ServerPlayer> players)
            throws MatchException {
        current[0] = open(context.getLevel().getServer(), Minigames.UHC);
        for (ServerPlayer player : players) {
            MatchManager.join(player, current[0], 0);
            player.hasChangedDimension();
            player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        }
        current[0].start();
    }

    private static void awaitSpawns(GameTestHelper context, Match[] current, List<ServerPlayer> players,
            List<Arena.Spawn> expectedSpawns, List<Vec3> expectedPositions) {
        GameTestLifecycle.awaitPreparation(context, () -> !current[0].preparingSpawns(), () -> {
            List<Arena.Spawn> spawns = current[0].arena().spawns(8);
            List<Vec3> positions = players.stream().map(ServerPlayer::position).toList();
            MatchManager.stop(current[0]);
            current[0] = null;
            if (expectedSpawns != null) {
                context.assertTrue(expectedSpawns.equals(spawns), "Seeded UHC did not replay all eight starting positions/yaws");
                context.assertTrue(expectedPositions.equals(positions), "Seeded UHC reassigned named participants to different starts");
                context.succeed();
                return;
            }
            try {
                startPlayers(context, current, players);
                awaitSpawns(context, current, players, spawns, positions);
            } catch (MatchException exception) {
                throw context.assertionException(exception.getMessage());
            }
        });
    }
}
