package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.TestPlayers;
import com.mojang.authlib.GameProfile;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.GameTestLifecycle;
import io.github.brainage04.brainage_minigames.dimension.DiscardedWrites;
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
import io.github.brainage04.brainage_minigames.game.UhcCombatLogger;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/** Concurrent matches exercise the real allocator, portal mixin and match lifecycle. */
public final class UhcConcurrentGameTestFunctions {
    private UhcConcurrentGameTestFunctions() {}

    public static void borders(GameTestHelper context) {
        Fixture f = new Fixture(context);
        Match a = f.open(Minigames.UHC, false, 200);
        Match b = f.open(Minigames.UHC, false, 300);
        f.ready(() -> {
            UhcArena aa = (UhcArena) a.arena(), bb = (UhcArena) b.arena();
            context.assertTrue(aa.border() != bb.border(), "UHCs share a border");
            context.assertTrue(aa.border().getCenterX() != bb.border().getCenterX()
                    || aa.border().getCenterZ() != bb.border().getCenterZ(), "Same-seed live regions overlap");
            context.assertTrue(aa.border() != aa.level().getWorldBorder(), "UHC uses dimension-global border");
            double global = aa.level().getWorldBorder().getSize();
            aa.shrinkBorder(100, 20);
            for (int i = 0; i < 20; i++) {
                aa.border().tick();
                aa.border(f.server.getLevel(ModDimensions.UHC_NETHER)).tick();
            }
            near(context, 100, aa.border().getSize(), "First shrink");
            near(context, 300, bb.border().getSize(), "Second independent width");
            near(context, 12.5, aa.border(f.server.getLevel(ModDimensions.UHC_NETHER)).getSize(), "First Nether shrink");
            near(context, 37.5, bb.border(f.server.getLevel(ModDimensions.UHC_NETHER)).getSize(), "Second Nether width");
            near(context, global, aa.level().getWorldBorder().getSize(), "Global border after shrink");
            ServerPlayer outsider = a.alivePlayers().getFirst(), insider = b.alivePlayers().getFirst();
            PlayerUtils.teleport(outsider, aa.level(), new Vec3(aa.border().getCenterX() + 60, 80, aa.border().getCenterZ()), 0);
            PlayerUtils.teleport(insider, bb.level(), new Vec3(bb.border().getCenterX(), 80, bb.border().getCenterZ()), 0);
            f.acknowledge();
            float outsideHealth = outsider.getHealth(), insideHealth = insider.getHealth();
            outsider.invulnerableTime = 0;
            aa.tickBorder(a); bb.tickBorder(b);
            context.assertTrue(outsider.getHealth() < outsideHealth, "Local survival border did not hurt outsider");
            near(context, insideHealth, insider.getHealth(), "Other match's border hurt an insider");
            context.assertFalse(MatchManager.allowPlace(outsider, outsider.blockPosition(), Blocks.STONE.defaultBlockState()),
                    "Survival building ignored local border");
            context.assertTrue(MatchManager.allowPlace(insider, insider.blockPosition(), Blocks.STONE.defaultBlockState()),
                    "Other match's border blocked inside building");
            MatchManager.stop(a);
            near(context, 300, bb.border().getSize(), "Closing one UHC reset another border");
            near(context, global, bb.level().getWorldBorder().getSize(), "Closing UHC changed global border");
            context.succeed();
        });
    }

    public static void mixed(GameTestHelper context) {
        Fixture f = new Fixture(context);
        Match uhc = f.open(Minigames.UHC, false, 200);
        Match meetup = f.open(Minigames.MEETUP, false, 200);
        Match finalUhc = f.open(Minigames.FINAL_UHC, false, 200);
        f.ready(() -> {
            context.assertTrue(uhc.arena().level().dimension().equals(ModDimensions.UHC), "UHC dimension");
            context.assertTrue(meetup.arena().level().dimension().equals(ModDimensions.MEETUP), "Meetup shares UHC dimension");
            context.assertTrue(finalUhc.arena().level().dimension().equals(ModDimensions.FINAL_UHC), "FinalUHC shares UHC dimension");
            f.server.getGameRules().set(UhcModeRules.ALWAYS_DAY, false, f.server);
            UhcClock.tick(f.server);
            var clocks = f.server.overworld().clockManager();
            clocks.tick();
            UhcClock.tick(f.server);
            near(context, 6000, meetup.arena().level().getDefaultClockTime(), "Meetup noon");
            near(context, 6000, finalUhc.arena().level().getDefaultClockTime(), "FinalUHC noon");
            context.assertTrue(uhc.arena().level().getDefaultClockTime() != 6000, "UHC clock did not run independently");
            Match laterLobby = f.openLobby(Minigames.UHC, false, 200);
            long before = uhc.arena().level().getDefaultClockTime();
            clocks.tick(); UhcClock.tick(f.server);
            near(context, before + 1, uhc.arena().level().getDefaultClockTime(), "Concurrent lobby reset or paused active UHC time");
            MatchManager.stop(laterLobby);
            for (Match match : List.of(uhc, meetup, finalUhc)) {
                ServerPlayer player = match.alivePlayers().getFirst();
                context.assertTrue(MatchManager.naturalRegenerationDisabled(player), "Concurrent match regained natural healing");
                context.assertTrue(((EndPortalBlock) Blocks.END_PORTAL).getPortalDestination(player.level(), player,
                        player.blockPosition()) == null, "Concurrent participant escaped via End portal");
            }
            context.assertTrue(UhcNether.destination(meetup.arena().level(), meetup.alivePlayers().getFirst()) == null,
                    "Meetup participant entered its Nether");
            context.assertTrue(UhcNether.destination(finalUhc.arena().level(), finalUhc.alivePlayers().getFirst()) == null,
                    "FinalUHC participant entered its Nether");
            var loggerId = meetup.alivePlayers().getFirst().getUUID();
            f.disconnect(meetup.alivePlayers().getFirst());
            context.assertTrue(UhcCombatLogger.zombie(loggerId).level() == meetup.arena().level(), "Meetup logger in another game dimension");
            MatchManager.stop(uhc);
            context.assertTrue(meetup.phase() == MatchPhase.ACTIVE && finalUhc.phase() == MatchPhase.ACTIVE,
                    "Ending UHC ended another game");
            context.assertTrue(UhcCombatLogger.zombie(loggerId) != null, "Ending UHC removed Meetup logger");
            context.succeed();
        });
    }

    public static void nether(GameTestHelper context) {
        Fixture f = new Fixture(context);
        Match a = f.open(Minigames.UHC, false, 1000), b = f.open(Minigames.UHC, false, 1000);
        f.ready(() -> {
            UhcArena aa = (UhcArena) a.arena(), bb = (UhcArena) b.arena();
            ServerLevel nether = aa.openNether().orElseThrow();
            ServerPlayer ap = a.alivePlayers().getFirst(), bp = b.alivePlayers().getFirst();
            long ac = UhcProgression.coins(f.server, ap.getUUID()), bc = UhcProgression.coins(f.server, bp.getUUID());
            portal(context, ap, aa, nether);
            portal(context, bp, bb, nether);
            f.acknowledge();
            UhcProgression.tick(a); UhcProgression.tick(b);
            context.assertTrue(UhcProgression.coins(f.server, ap.getUUID()) == ac + 15
                    && UhcProgression.coins(f.server, bp.getUUID()) == bc + 15, "Nether coins were not match-scoped");
            near(context, aa.border().getCenterX() / 8, aa.border(nether).getCenterX(), "First Nether centre");
            near(context, bb.border().getCenterX() / 8, bb.border(nether).getCenterX(), "Second Nether centre");
            var offline = a.alivePlayers().getLast();
            PlayerUtils.teleport(offline, nether, ap.position(), 0); f.acknowledge();
            var offlineId = offline.getUUID(); f.disconnect(offline);
            aa.closeNether(a.alivePlayers());
            MatchManager.tick();
            context.assertTrue(ap.level() == aa.level() && aa.border().isWithinBounds(ap.blockPosition()), "First Nether return missed its border");
            context.assertTrue(UhcCombatLogger.zombie(offlineId).level() == aa.level(), "Closed Nether stranded combat logger");
            context.assertTrue(bp.level() == nether && UhcNether.destination(bb.level(), b.alivePlayers().getLast()) == nether,
                    "Closing first Nether closed second match's portals");
            context.assertTrue(UhcNether.destination(aa.level(), ap) == null, "Closed match can enter Nether");
            WorldBorder destination = UhcNether.destinationBorder(bb.level(), bp);
            context.assertTrue(destination == bb.border(), "Nether return used another match's border");
            PlayerUtils.teleport(bp, bb.level(), bb.surfaceReturnPosition(nether, bp.getX(), bp.getZ()), 0);
            context.assertTrue(bb.border().isWithinBounds(bp.blockPosition()), "Second Nether return missed its region");
            context.succeed();
        });
    }

    private static void portal(GameTestHelper context, ServerPlayer player, UhcArena arena, ServerLevel nether) {
        BlockPos entry = BlockPos.containing(arena.border().getCenterX(), 80, arena.border().getCenterZ());
        arena.level().setBlock(entry, Blocks.NETHER_PORTAL.defaultBlockState()
                .setValue(NetherPortalBlock.AXIS, Direction.Axis.X), 3);
        PlayerUtils.teleport(player, arena.level(), Vec3.atBottomCenterOf(entry), 0);
        context.assertTrue(UhcNether.destinationBorder(nether, player) == arena.border(nether), "Portal destination uses global border");
        var transition = ((NetherPortalBlock) Blocks.NETHER_PORTAL).getPortalDestination(arena.level(), player, entry);
        context.assertTrue(transition != null, "Open match portal has no destination");
        player.teleport(transition);
        context.assertTrue(player.level() == nether && arena.border(nether).isWithinBounds(player.blockPosition()),
                "Portal trip left the match's Nether region");
        context.assertTrue(MatchManager.allowPlace(player, player.blockPosition(), Blocks.STONE.defaultBlockState()),
                "Nether building used surface coordinates");
    }

    public static void deathmatch(GameTestHelper context) {
        Fixture f = new Fixture(context);
        Match a = f.open(Minigames.UHC, true, 200), b = f.open(Minigames.UHC, true, 200);
        f.ready(() -> {
            UhcArena aa = (UhcArena) a.arena(), bb = (UhcArena) b.arena();
            ServerPlayer offline = a.alivePlayers().getLast();
            UUID offlineId = offline.getUUID(); f.disconnect(offline);
            advance(a, a.settings().minutesInTicks(UhcGame.DEATHMATCH_TIME));
            context.assertTrue(aa.inDeathmatch() && !bb.inDeathmatch(), "Deathmatch transition was not match-local");
            context.assertTrue(UhcCombatLogger.zombie(offlineId).level() == aa.level(), "Deathmatch left a logger behind");
            near(context, 200, bb.border().getSize(), "Deathmatch changed live survival border");
            advance(b, b.settings().minutesInTicks(UhcGame.DEATHMATCH_TIME));
            context.assertTrue(aa.level() == bb.level() && aa.border() != bb.border(), "Deathmatches share a border");
            double distance = Math.max(Math.abs(aa.border().getCenterX() - bb.border().getCenterX()),
                    Math.abs(aa.border().getCenterZ() - bb.border().getCenterZ()));
            context.assertTrue(distance > aa.border().getSize(), "Deathmatch slots overlap");
            aa.shrinkBorder(56, 20);
            for (int i = 0; i < 20; i++) aa.border().tick();
            near(context, 113, bb.border().getSize(), "Other deathmatch shrank");
            BlockPos marker = BlockPos.containing(bb.border().getCenterX(), bb.lobbyPosition().y() + 1, bb.border().getCenterZ());
            bb.level().setBlock(marker, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
            MatchManager.stop(a);
            context.assertTrue(bb.level().getBlockState(marker).is(Blocks.DIAMOND_BLOCK), "Cleanup cleared another deathmatch slot");
            context.assertTrue(b.phase() == MatchPhase.ACTIVE, "Cleanup ended the other deathmatch");
            context.assertTrue(a.onlineMembers().isEmpty() && UhcCombatLogger.zombie(offlineId) == null, "Ended match kept members/logger");
            context.succeed();
        });
    }

    public static void cleanup(GameTestHelper context) {
        Fixture f = new Fixture(context);
        Match a = f.open(Minigames.UHC, false, 200), b = f.open(Minigames.UHC, false, 200);
        Match c = f.open(Minigames.MEETUP, false, 200), d = f.open(Minigames.FINAL_UHC, false, 200);
        f.ready(() -> {
            UhcArena aa = (UhcArena) a.arena();
            double x = aa.border().getCenterX(), z = aa.border().getCenterZ();
            Path pending = f.server.getWorldPath(LevelResource.ROOT).resolve(".brainage_minigames-uhc-reset");
            UhcWorldCleanup.deletePendingWorld(f.server);
            context.assertTrue(Files.exists(pending), "Live matches allowed dimension regeneration");
            MatchManager.stop(a);
            context.assertTrue(NaturalTerrain.inUse(f.server), "Closing first match released all regions");
            Match replay = f.openLobby(Minigames.UHC, false, 200);
            UhcArena reopened = (UhcArena) replay.arena();
            near(context, x, reopened.border().getCenterX(), "Released seeded region x");
            near(context, z, reopened.border().getCenterZ(), "Released seeded region z");
            for (Match match : List.of(b, c, d, replay)) MatchManager.stop(match);
            context.assertFalse(NaturalTerrain.inUse(f.server), "Cleanup leaked region reservations");
            for (ServerPlayer player : f.players) {
                context.assertTrue(player.level() == context.getLevel(), "Cleanup did not restore player dimension");
                context.assertFalse(MatchManager.naturalRegenerationDisabled(player), "Cleanup left regeneration disabled");
            }
            // A forced chunk stays loaded and accessible between the saves below.
            ServerLevel uhc = f.server.getLevel(ModDimensions.UHC);
            BlockPos probe = BlockPos.containing(x, 64, z);
            int chunkX = SectionPos.blockToSectionCoord(probe.getX()), chunkZ = SectionPos.blockToSectionCoord(probe.getZ());
            uhc.setChunkForced(chunkX, chunkZ, true);
            uhc.getChunk(chunkX, chunkZ);
            // Saves reach the chunk once its holder is visible and its load has settled.
            context.runAfterDelay(20, () -> {
                try {
                    var chunk = uhc.getChunk(chunkX, chunkZ);
                    chunk.markUnsaved();
                    DiscardedWrites.serverStopping(f.server, false, UhcWorldCleanup.resetPending(f.server));
                    try {
                        context.assertTrue(DiscardedWrites.chunks(uhc)
                                && !DiscardedWrites.chunks(context.getLevel()), "Stop discarded the wrong dimensions");
                        uhc.getChunkSource().save(false);
                        context.assertTrue(chunk.isUnsaved(), "Stopping server wrote a dimension it deletes afterwards");
                    } finally {
                        DiscardedWrites.clear();
                    }
                    uhc.getChunkSource().save(false);
                    context.assertFalse(chunk.isUnsaved(), "Running server did not save a marked dimension");
                } finally {
                    uhc.setChunkForced(chunkX, chunkZ, false);
                }
                Path root = null;
                try {
                    root = Files.createTempDirectory("brainage-concurrent-cleanup-");
                    Path regions = Files.createDirectories(root.resolve("regions"));
                    try (var storage = new RegionFileStorage(new RegionStorageInfo("test", ModDimensions.UHC, "chunk"), regions, false)) {
                        DiscardedWrites.serverStopping(f.server, false, UhcWorldCleanup.resetPending(f.server));
                        try {
                            storage.write(new ChunkPos(0, 0), new CompoundTag());
                        } finally {
                            DiscardedWrites.clear();
                        }
                        context.assertFalse(Files.exists(regions.resolve("r.0.0.mca")), "Stopping server wrote a region file it deletes afterwards");
                        storage.write(new ChunkPos(0, 0), new CompoundTag());
                        context.assertTrue(Files.exists(regions.resolve("r.0.0.mca")), "Running server dropped a region-file write");
                    }
                    try (var storage = new RegionFileStorage(new RegionStorageInfo("test", Level.OVERWORLD, "chunk"), regions.resolve("overworld"), false)) {
                        DiscardedWrites.serverStopping(f.server, true, false);
                        try {
                            context.assertTrue(DiscardedWrites.chunks(context.getLevel()) && DiscardedWrites.chunks(uhc),
                                    "Stopping GameTest server saved its disposable world");
                            storage.write(new ChunkPos(0, 0), new CompoundTag());
                        } finally {
                            DiscardedWrites.clear();
                        }
                        context.assertFalse(Files.exists(regions.resolve("overworld/r.0.0.mca")), "Stopping GameTest server wrote its disposable world");
                        context.assertFalse(DiscardedWrites.chunks(context.getLevel()), "Running server skipped saving its world");
                    }
                    Path dimensions = root.resolve("dimensions/brainage_minigames");
                    for (String name : List.of("uhc", "uhc_nether", "meetup", "meetup_nether", "final_uhc", "final_uhc_nether", "minigames")) {
                        Files.createDirectories(dimensions.resolve(name));
                        Files.writeString(dimensions.resolve(name).resolve("region-data"), "data");
                    }
                    Files.writeString(root.resolve(".brainage_minigames-uhc-reset"), "pending");
                    UhcWorldCleanup.deletePendingWorld(root);
                    for (String name : List.of("uhc", "uhc_nether", "meetup", "meetup_nether", "final_uhc", "final_uhc_nether")) {
                        context.assertFalse(Files.exists(dimensions.resolve(name)), "Regeneration kept " + name);
                    }
                    context.assertTrue(Files.exists(dimensions.resolve("minigames/region-data")), "Regeneration removed unrelated map dimension");
                    context.assertFalse(Files.exists(root.resolve(".brainage_minigames-uhc-reset")), "Regeneration kept completed marker");
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException(exception);
                } finally {
                    if (root != null) {
                        try (var paths = Files.walk(root)) {
                            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                        } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
                    }
                }
                context.succeed();
            });
        });
    }

    private static void advance(Match match, int tick) {
        try {
            var field = Match.class.getDeclaredField("phaseTicks");
            field.setAccessible(true); field.setInt(match, tick - 1);
            MatchManager.tick();
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static void near(GameTestHelper context, double expected, double actual, String message) {
        context.assertTrue(Math.abs(expected - actual) < 0.001, message + ": expected " + expected + ", got " + actual);
    }

    private static final class Fixture {
        final GameTestHelper context;
        final MinecraftServer server;
        final List<Match> matches = new ArrayList<>();
        final List<ServerPlayer> players = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>();
        final net.minecraft.nbt.CompoundTag settings;
        final net.minecraft.world.level.gamerules.GameRules rules;

        Fixture(GameTestHelper context) {
            this.context = context; server = context.getLevel().getServer();
            settings = server.getCommandStorage().get(BrainageMinigames.id("settings")).copy();
            rules = server.getGameRules().copy(context.getLevel().enabledFeatures());
            GameTestLifecycle.afterTest(context, this::close);
            server.getGameRules().set(UhcModeRules.BORDER_STYLE, 0, server);
            server.getGameRules().set(UhcModeRules.ALWAYS_DAY, true, server);
            server.getGameRules().set(UhcModeRules.DEATHMATCH_AFTER_GRACE, 0, server);
            server.getGameRules().set(UhcModeRules.DEATHMATCH_SKIP_PLAYERS, 0, server);
            server.getGameRules().set(UhcModeRules.NETHER_BORDER_SCALE, 8, server);
            server.getGameRules().set(UhcModeRules.COMBAT_LOGGER, true, server);
            server.getGameRules().set(UhcProgression.COIN_MULTIPLIER, 100, server);
            // Passive perks (Vitamins absorption, Survivalism) would absorb the border damage measured here.
            server.getGameRules().set(UhcProgression.MAX_ALL, false, server);
            server.getGameRules().set(UhcProgression.MAX_ALL_KITS, false, server);
        }

        Match openLobby(Minigame game, boolean deathmatch, int width) {
            for (GameSetting setting : game.settings()) SettingsStorage.reset(server, game, setting);
            SettingsStorage.set(server, game, game.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), 0);
            SettingsStorage.set(server, game, UhcGame.REGION_SEED, 942026);
            if (game == Minigames.UHC) {
                SettingsStorage.set(server, game, UhcGame.BORDER_START_SIZE, width);
                SettingsStorage.set(server, game, UhcGame.FIRST_SHRINK_SIZE, width * 3 / 4);
                SettingsStorage.set(server, game, UhcGame.SECOND_SHRINK_SIZE, width / 2);
                SettingsStorage.set(server, game, UhcGame.THIRD_SHRINK_SIZE, width / 4);
                SettingsStorage.set(server, game, UhcGame.FINAL_SHRINK_SIZE, width / 8);
                server.getGameRules().set(UhcModeRules.DEATHMATCH, deathmatch, server);
            }
            try {
                Match match = MatchManager.open(server, game, TeamLayout.parse("1v1").orElseThrow(), null);
                matches.add(match); return match;
            } catch (MatchException exception) { throw context.assertionException(exception.getMessage()); }
        }

        Match open(Minigame game, boolean deathmatch, int width) {
            Match match = openLobby(game, deathmatch, width);
            try {
                for (int team = 1; team <= 2; team++) {
                    var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),
                            "conc" + UUID.randomUUID().toString().substring(0, 8)), false);
                    ServerPlayer player = new ServerPlayer(server, context.getLevel(), cookie.gameProfile(), cookie.clientInformation());
                    channels.add(TestPlayers.connect(player, cookie));
                    players.add(player); MatchManager.join(player, match, team);
                }
                if (match.phase() == MatchPhase.LOBBY) match.start();
                return match;
            } catch (MatchException exception) { throw context.assertionException(exception.getMessage()); }
        }

        void ready(Runnable action) {
            GameTestLifecycle.awaitPreparation(context,
                    () -> matches.stream().allMatch(match -> match.phase() == MatchPhase.ACTIVE),
                    () -> { acknowledge(); action.run(); });
        }

        void acknowledge() {
            for (ServerPlayer player : players) {
                player.hasChangedDimension(); player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            }
        }

        void disconnect(ServerPlayer player) {
            MatchManager.handleDisconnect(player); server.getPlayerList().remove(player);
        }

        void close() {
            for (Match match : matches) MatchManager.stop(match);
            for (ServerPlayer player : players) if (server.getPlayerList().getPlayer(player.getUUID()) != null) server.getPlayerList().remove(player);
            for (EmbeddedChannel channel : channels) channel.finishAndReleaseAll();
            server.getCommandStorage().set(BrainageMinigames.id("settings"), settings);
            server.getGameRules().setAll(rules, server);
            UhcClock.tick(server);
        }
    }
}
