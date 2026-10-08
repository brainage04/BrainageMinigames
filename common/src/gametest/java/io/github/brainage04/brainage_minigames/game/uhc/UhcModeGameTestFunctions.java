package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.TestPlayers;
import com.mojang.authlib.GameProfile;
import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.MatchSidebar;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/** Real match transitions, terrain teleports, chest loot and isolated dimension clock regressions. */
public final class UhcModeGameTestFunctions {
    private UhcModeGameTestFunctions() {}

    public static void hypixelBorder(GameTestHelper context) {
        withFixture(context, new Fixture(context, false, false), fixture -> {
            Match match = fixture.match;
            var border = ((UhcArena) match.arena()).border();
            advance(match, 20 * 60 * 20 - 1);
            near(1000, border.getSize(), "border before 20:00");
            advance(match, 20 * 60 * 20);
            // Tick the real vanilla border interpolator: width falls by one block each second.
            for (int i = 0; i < 20; i++) { border.tick(); }
            near(999, border.getSize(), "width after one second");
            for (int i = 20; i < 15 * 60 * 20; i++) { border.tick(); }
            near(100, border.getSize(), "width at 35:00");
            near(0, border.getLerpTime(), "completed shrink");
            context.succeed();
        });
    }

    public static void badlionBorder(GameTestHelper context) {
        withFixture(context, new Fixture(context, true, false), fixture -> {
            Match match = fixture.match;
            ServerLevel level = match.arena().level();
            var border = ((UhcArena) match.arena()).border();
            double cx = border.getCenterX(), cz = border.getCenterZ();
            ServerPlayer outside = fixture.players.getFirst();
            ServerPlayer inside = fixture.players.getLast();
            level.setBlock(BlockPos.containing(cx + 370, 80, cz + 370), Blocks.STONE.defaultBlockState(), 3);
            PlayerUtils.teleport(outside, level, new Vec3(cx + 450, 40, cz + 450), 0);
            PlayerUtils.teleport(inside, level, new Vec3(cx + 10, 40, cz + 10), 0);
            Vec3 kept = inside.position();
            advance(match, 20 * 60 * 20);
            near(750, border.getSize(), "first instant width");
            near(cx + 370, outside.getX(), "nearest inset x");
            near(cz + 370, outside.getZ(), "nearest inset z");
            near(81, outside.getY(), "surface teleport");
            check(kept.equals(inside.position()), "An insider was teleported out of its cave");
            advance(match, 25 * 60 * 20);
            near(500, border.getSize(), "25:00 width");
            near(cx + 245, outside.getX(), "second inset x");
            advance(match, 30 * 60 * 20);
            near(250, border.getSize(), "30:00 width");
            advance(match, 35 * 60 * 20);
            near(100, border.getSize(), "35:00 width");
            Vec3 corner = UhcArena.nearestInside(cx - 450, cz + 450, cx, cz, 750, 5);
            near(cx - 370, corner.x(), "negative inset x");
            near(cz + 370, corner.z(), "positive inset z");
            context.succeed();
        });
    }

    public static void deathmatch(GameTestHelper context) {
        withFixture(context, new Fixture(context, false, true), fixture -> {
            Match match = fixture.match;
            UhcArena arena = (UhcArena) match.arena();
            ServerPlayer player = fixture.players.getFirst();
            var resistance = player.getEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE);
            check(resistance != null && !resistance.isVisible() && resistance.showIcon(),
                    "Starter Fire Resistance must hide particles but retain its HUD icon");
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
            player.setHealth(11);
            ServerPlayer spectator = fixture.connect(context);
            try { MatchManager.watch(spectator, match); }
            catch (MatchException exception) { throw new IllegalStateException(exception); }
            io.github.brainage04.brainage_minigames.util.PlayerUtils.teleport(player,
                    match.server().getLevel(ModDimensions.UHC_NETHER), new Vec3(0, 70, 0), 0);
            var globalBorder = match.server().getLevel(ModDimensions.MINIGAMES).getWorldBorder();
            double globalWidth = globalBorder.getSize();
            double globalCenter = globalBorder.getCenterX();
            var map = deathmatchMap(arena);
            var existing = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(map.level(),
                    net.minecraft.world.entity.EntitySpawnReason.LOAD);
            existing.snapTo(map.lobbyPosition());
            map.level().addLegacyChunkEntities(java.util.stream.Stream.of(existing));
            advance(match, 40 * 60 * 20);
            fixture.acknowledgeTeleports();
            check(arena.inDeathmatch() && arena.deathmatchFrozen(), "Deathmatch did not start frozen at 40:00");
            check(player.level().dimension() == ModDimensions.MINIGAMES
                            && arena.level() == player.level(),
                    "Deathmatch did not move the match into the minigames dimension");
            check(player.getInventory().getItem(0).getCount() == 7 && player.getHealth() == 11,
                    "Deathmatch reset the survivor's gear or health");
            check(spectator.level() == arena.level() && spectator.isSpectator(), "Deathmatch left a spectator in the old dimension");
            check(existing.isRemoved(), "A pre-existing arena hostile survived the transition");
            near(globalWidth, globalBorder.getSize(), "Other arena slots' global border width");
            near(globalCenter, globalBorder.getCenterX(), "Other arena slots' global border centre");
            check(arena.level().dimensionType().hasFixedTime(), "The arena's midday is not fixed");
            near(0, arena.level().environmentAttributes().getValue(
                            net.minecraft.world.attribute.EnvironmentAttributes.SUN_ANGLE, player.position()),
                    "Deathmatch midday sun angle");
            for (var type : List.of(net.minecraft.world.entity.EntityTypes.ZOMBIE, net.minecraft.world.entity.EntityTypes.CREEPER,
                    net.minecraft.world.entity.EntityTypes.COW)) {
                var mob = type.create(arena.level(), net.minecraft.world.entity.EntitySpawnReason.NATURAL);
                mob.snapTo(arena.lobbyPosition());
                check(!arena.level().addFreshEntity(mob), "The arena accepted a new mob: " + type);
            }
            assertClearWeather(arena.level());
            assertReachableSpawnPads(map);
            var border = arena.border();
            double width = border.getSize();
            Vec3 spawn = player.position();
            check(Math.hypot(spawn.x() - border.getCenterX(), spawn.z() - border.getCenterZ()) > 43,
                    "Player did not arrive in a rim spawn room");
            player.snapTo(spawn.x() + 3, spawn.y() + 1, spawn.z(), 0, 0);
            advance(match, 40 * 60 * 20 + 100);
            check(player.position().equals(spawn), "Jumping escaped the frozen countdown");
            check(!Minigames.UHC.allowDamage(match, player, player.damageSources().generic()),
                    "Countdown allowed damage");
            check(!Minigames.UHC.allowBreak(match, player, player.blockPosition().below(),
                    player.level().getBlockState(player.blockPosition().below())), "Countdown allowed block breaking");
            check(!MatchManager.allowUseOn(player), "Countdown allowed chest/item interactions");
            advance(match, 40 * 60 * 20 + 200);
            check(!arena.deathmatchFrozen(), "Countdown did not release after ten seconds");
            check(Minigames.UHC.allowDamage(match, player, player.damageSources().generic()),
                    "Deathmatch remained invulnerable after release");
            // Building stops five blocks above the floor, below the top of the rim wall, so
            // nobody can tower over the wall onto the barrier ring and out of the arena.
            int standing = map.bounds().minY() + 3;
            BlockPos middle = BlockPos.containing(border.getCenterX() + 10, standing, border.getCenterZ() + 10);
            check(arena.canBuild(middle) && arena.canBuild(middle.above(4)),
                    "Deathmatch refused building up to five blocks above the floor");
            check(!arena.canBuild(middle.above(5)),
                    "Deathmatch allowed building six blocks above the floor, high enough to climb the rim wall");
            // Nor is there a ledge inside the rim within a jump of the tallest pillar: a block one
            // or two above the build limit with room on top would be a step onto the wall.
            for (BlockPos pos : BlockPos.betweenClosed(
                    map.bounds().minX(), standing + 5, map.bounds().minZ(),
                    map.bounds().maxX(), standing + 7, map.bounds().maxZ())) {
                if (Math.hypot(pos.getX() - Math.floor(border.getCenterX()), pos.getZ() - Math.floor(border.getCenterZ())) >= 53
                        || arena.level().getBlockState(pos).getCollisionShape(arena.level(), pos).isEmpty()) {
                    continue;
                }
                BlockPos above = pos.above();
                check(!arena.level().getBlockState(above).getCollisionShape(arena.level(), above).isEmpty(),
                        "A ledge inside the deathmatch rim at " + pos + " lets players climb onto the wall");
            }
            player.snapTo(border.getCenterX() + border.getSize() / 2 + border.getSafeZone() + 4,
                    spawn.y(), spawn.z(), 0, 0);
            player.invulnerableTime = 0;
            arena.tickDeathmatchBorder(match);
            check(player.getHealth() < 11, "The match-local deathmatch border did not damage an outsider");
            PlayerUtils.teleport(player, arena.level(), spawn, 0);
            player.setHealth(11);
            player.invulnerableTime = 0;
            // A player pushed back inside the border lands on whatever stands at that spot, never
            // inside it: here a two-block pillar where the push-back puts them.
            double edgeX = border.getCenterX() + border.getSize() / 2 - 8;
            BlockPos pillar = BlockPos.containing(edgeX, map.bounds().minY() + 3, spawn.z());
            arena.level().setBlock(pillar, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
            arena.level().setBlock(pillar.above(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
            player.snapTo(border.getCenterX() + border.getSize() / 2 + 4, spawn.y(), spawn.z(), 0, 0);
            arena.moveToSurface(player);
            check(player.blockPosition().equals(pillar.above(2)),
                    "The push-back put the player at " + player.blockPosition() + ", not on top of the pillar at " + pillar.above(2));
            arena.level().setBlock(pillar, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            arena.level().setBlock(pillar.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            PlayerUtils.teleport(player, arena.level(), spawn, 0);
            BlockPos chestPos = BlockPos.containing(border.getCenterX() - 0.5,
                    67, border.getCenterZ() - 4.5);
            check(arena.level().getBlockEntity(chestPos) instanceof ChestBlockEntity,
                    "Central resource chest is missing");
            ChestBlockEntity chest = (ChestBlockEntity) arena.level().getBlockEntity(chestPos);
            chest.unpackLootTable(player);
            boolean resource = false;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                if (stack.is(Items.ARROW) || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.FLINT)
                        || stack.is(Items.FEATHER) || stack.is(Items.STICK) || stack.is(Items.ENCHANTED_BOOK)) {
                    resource = true;
                }
            }
            check(resource, "Middle chest did not supply a deathmatch resource");
            advance(match, 45 * 60 * 20);
            for (int i = 0; i < 60 * 20; i++) { border.tick(); }
            near(arena.deathmatchFinalWidth(), border.getSize(), "deathmatch border at 46:00");
            match.teams().getFirst().addScore(10);
            advance(match, 50 * 60 * 20);
            check(match.phase() == MatchPhase.ENDED && match.winners().size() == 2,
                    "Survivors did not draw at 50:00 regardless of kills");
            fixture.close();
            check(player.level() == context.getLevel() && spectator.level() == context.getLevel()
                            && !spectator.isSpectator(), "Snapshot restore left players/spectators in deathmatch");
            check(map.level().getBlockEntity(chestPos) == null, "Closed deathmatch left its resource chests behind");
            near(globalWidth, globalBorder.getSize(), "Closing deathmatch changed another slot's global border");
            context.succeed();
        });
    }

    public static void deathmatchLifecycle(GameTestHelper context) {
        withFixture(context, new Fixture(context, false, true, 3), fixture -> {
            Match match = fixture.match;
            UhcArena arena = (UhcArena) match.arena();
            ServerPlayer victim = fixture.players.getFirst(), killer = fixture.players.get(1);
            victim.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 4));
            advance(match, 40 * 60 * 20);
            fixture.acknowledgeTeleports();
            advance(match, 40 * 60 * 20 + 200);
            BlockPos death = victim.blockPosition();
            killer.snapTo(victim.position().add(1, 0, 0));
            long before = UhcProgression.coins(fixture.server, killer.getUUID());
            victim.hurtServer(arena.level(), victim.damageSources().playerAttack(killer), 1000);
            check(!match.isAlive(victim.getUUID()) && victim.isSpectator()
                            && victim.level() == arena.level(), "Deathmatch elimination used the old dimension");
            check(arena.level().getBlockEntity(death) instanceof ChestBlockEntity,
                    "Deathmatch elimination did not create a chest in the arena");
            var chest = (ChestBlockEntity) arena.level().getBlockEntity(death);
            check(java.util.stream.IntStream.range(0, chest.getContainerSize())
                            .anyMatch(slot -> chest.getItem(slot).is(Items.DIAMOND)
                                    && chest.getItem(slot).getCount() == 4),
                    "Deathmatch chest lost the eliminated survivor's inventory");
            check(io.github.brainage04.brainage_minigames.game.AntiJanitor.canOpen(arena.level(), death, killer),
                    "The deathmatch killer could not access their death chest");
            check(UhcProgression.coins(fixture.server, killer.getUUID()) > before,
                    "Deathmatch kills stopped awarding coins after the dimension transition");
            fixture.close();
            check(fixture.players.stream().allMatch(player -> player.level() == context.getLevel()),
                    "Closing deathmatch did not restore an eliminated participant's snapshot");
            context.succeed();
        });
    }
    private static io.github.brainage04.brainage_minigames.game.arena.MapArena deathmatchMap(UhcArena arena) {
        try {
            var field = UhcArena.class.getDeclaredField("deathmatchArena");
            field.setAccessible(true);
            return (io.github.brainage04.brainage_minigames.game.arena.MapArena) field.get(arena);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void assertClearWeather(ServerLevel level) {
        var weather = level.getWeatherData();
        boolean rain = weather.isRaining(), thunder = weather.isThundering();
        try {
            weather.setRaining(true);
            weather.setThundering(true);
            level.setRainLevel(1);
            level.setThunderLevel(1);
            var cycle = ServerLevel.class.getDeclaredMethod("advanceWeatherCycle");
            cycle.setAccessible(true);
            cycle.invoke(level);
            check(!level.isRaining() && !level.isThundering(), "Deathmatch inherited stormy weather");
            check(weather.isRaining() && weather.isThundering(), "Arena clear weather changed the survival world's weather");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        } finally {
            weather.setRaining(rain);
            weather.setThundering(thunder);
        }
    }

    private static void assertReachableSpawnPads(io.github.brainage04.brainage_minigames.game.arena.MapArena map) {
        BlockPos start = BlockPos.containing(map.lobbyPosition()).below();
        var reached = new java.util.HashSet<BlockPos>();
        var queue = new java.util.ArrayDeque<BlockPos>();
        reached.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(direction);
                if (!map.bounds().isInside(next) || reached.contains(next)
                        || !map.level().getBlockState(next.below()).isFaceSturdy(map.level(), next.below(), net.minecraft.core.Direction.UP)
                        || !map.level().getBlockState(next).getCollisionShape(map.level(), next).isEmpty()
                        || !map.level().getBlockState(next.above()).getCollisionShape(map.level(), next.above()).isEmpty()) continue;
                reached.add(next);
                queue.addLast(next);
            }
        }
        check(map.teamSlots() == 24, "The deathmatch must retain all 24 team spawn pads");
        for (int team = 1; team <= map.teamSlots(); team++) {
            BlockPos spawn = BlockPos.containing(map.spawnsOf(team).getFirst().position());
            check(reached.contains(spawn),
                    "A sheltered deathmatch spawn pad cannot be reached from the combat floor: " + team);
            for (int y = spawn.getY() + 2; y <= map.bounds().maxY(); y++) {
                BlockPos above = new BlockPos(spawn.getX(), y, spawn.getZ());
                check(map.level().getBlockState(above).getCollisionShape(map.level(), above).isEmpty(),
                        "Deathmatch spawn pad retains a sheltering roof: " + team);
            }
        }
    }


    public static void disabledDeathmatch(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        GameSetting limit = Minigames.UHC.setting(GameSetting.TIME_LIMIT_MINUTES).orElseThrow();
        int oldLimit = SettingsStorage.resolve(server, Minigames.UHC).get(limit.key());
        io.github.brainage04.brainage_minigames.GameTestLifecycle.afterTest(context, () -> SettingsStorage.set(server, Minigames.UHC, limit, oldLimit));
        SettingsStorage.set(server, Minigames.UHC, limit, 45);
        withFixture(context, new Fixture(context, false, false), fixture -> {
            Match match = fixture.match;
            advance(match, 40 * 60 * 20);
            check(!((UhcArena) match.arena()).inDeathmatch(), "Disabled deathmatch teleported the survivors");
            check(match.phase() == MatchPhase.ACTIVE, "Disabling deathmatch ended survival prematurely");
            context.succeed();
        });
    }

    public static void disabledDeathmatchSetting(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        int oldEnabled = SettingsStorage.resolve(server, Minigames.UHC).get(UhcGame.DEATHMATCH_ENABLED);
        io.github.brainage04.brainage_minigames.GameTestLifecycle.afterTest(context, () -> SettingsStorage.set(server, Minigames.UHC, UhcGame.DEATHMATCH_ENABLED, oldEnabled));
        SettingsStorage.set(server, Minigames.UHC, UhcGame.DEATHMATCH_ENABLED, 0);
        withFixture(context, new Fixture(context, false, true), fixture -> {
            advance(fixture.match, 40 * 60 * 20);
            check(!((UhcArena) fixture.match.arena()).inDeathmatch(),
                    "Per-match deathmatch disable was ignored");
            context.succeed();
        });
    }

    public static void doubleHealth(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        boolean oldHealth = server.getGameRules().get(UhcModeRules.DOUBLE_HEALTH);
        server.getGameRules().set(UhcModeRules.DOUBLE_HEALTH, true, server);
        Fixture fixture;
        try {
            fixture = new Fixture(context, false, false);
        } catch (RuntimeException exception) {
            server.getGameRules().set(UhcModeRules.DOUBLE_HEALTH, oldHealth, server);
            throw exception;
        }
        io.github.brainage04.brainage_minigames.GameTestLifecycle.afterTest(context, () -> server.getGameRules().set(UhcModeRules.DOUBLE_HEALTH, oldHealth, server));
        UhcSpawnGameTestFunctions.awaitReady(context, fixture.match, () -> {
            advance(fixture.match, 0);
            ServerPlayer player = fixture.players.getFirst();
            player.setHealth(37.25F);
            context.runAfterDelay(15, () -> {
                try {
                    near(40, player.getMaxHealth(), "Double-health maximum");
                    near(40, fixture.players.get(1).getHealth(), "Double-health starting health");
                    EmbeddedChannel channel = fixture.channels.getFirst();
                    channel.flushOutbound();
                    check(healthScore(channel.outboundMessages(), player.getScoreboardName()) == 38,
                            "The client health objective capped or misrounded double health");
                    server.getGameRules().set(UhcModeRules.DOUBLE_HEALTH, false, server);
                    near(40, player.getMaxHealth(), "Changing the rule mid-match changed participant health");
                    MatchManager.leave(player);
                    near(20, player.getMaxHealth(), "Maximum health after leaving");
                    near(20, player.getHealth(), "Snapshot health after leaving");
                    MatchManager.stop(fixture.match);
                    near(20, fixture.players.get(1).getMaxHealth(), "Maximum health after ending");
                    fixture.close();
                    withFixture(context, new Fixture(context, false, false), normal -> {
                        near(20, normal.players.getFirst().getMaxHealth(), "Disabled double-health maximum");
                        near(20, normal.players.getFirst().getHealth(), "Disabled double-health starting health");
                        server.getGameRules().set(UhcModeRules.DOUBLE_HEALTH, oldHealth, server);
                        context.succeed();
                    });
                } catch (MatchException exception) {
                    throw new GameTestAssertException(Component.literal(exception.getMessage()), 0);
                } finally {
                    fixture.close();
                }
            });
        });
    }

    private static int healthScore(Iterable<?> packets, String owner) {
        int result = -1;
        for (Object packet : packets) {
            if (packet instanceof BundlePacket<?> bundle) {
                int nested = healthScore(bundle.subPackets(), owner);
                if (nested >= 0) { result = nested; }
            } else if (packet instanceof ClientboundSetScorePacket score
                    && score.objectiveName().equals(MatchSidebar.HEALTH_OBJECTIVE_NAME)
                    && score.owner().equals(owner)) {
                result = score.score();
            }
        }
        return result;
    }

    public static void clocks(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        ServerLevel uhc = server.getLevel(ModDimensions.UHC);
        boolean oldDay = server.getGameRules().get(UhcModeRules.ALWAYS_DAY);
        boolean oldAdvance = server.getGameRules().get(GameRules.ADVANCE_TIME);
        var vanillaClock = server.overworld().dimensionType().defaultClock().orElseThrow();
        long oldTime = server.overworld().clockManager().getTotalTicks(vanillaClock);
        server.overworld().clockManager().setTotalTicks(vanillaClock, 18000);
        server.getGameRules().set(UhcModeRules.ALWAYS_DAY, true, server);
        server.getGameRules().set(GameRules.ADVANCE_TIME, true, server);
        UhcClock.tick(server);
        long vanilla = server.overworld().getDefaultClockTime();
        context.runAfterDelay(30, () -> {
            try {
                check(server.overworld().getDefaultClockTime() > vanilla, "Always-day paused the vanilla Overworld");
                near(6000, uhc.getDefaultClockTime(), "UHC noon");
                near(6000, server.getLevel(ModDimensions.UHC_NETHER).getDefaultClockTime(), "UHC Nether clock");
                check(server.getLevel(Level.NETHER).getOverworldClockTime() == server.overworld().getDefaultClockTime(),
                        "Vanilla Nether time was detached from vanilla");
                check(server.getLevel(Level.END).getOverworldClockTime() == server.overworld().getDefaultClockTime(),
                        "Vanilla End time was detached from vanilla");
                // Async preparation advances enough test ticks for storms to develop. Test the
                // day timeline in clear weather, independently of vanilla storm darkening.
                float rain = uhc.getRainLevel(1), thunder = uhc.getThunderLevel(1);
                try {
                    uhc.setRainLevel(0);
                    uhc.setThunderLevel(0);
                    uhc.environmentAttributes().invalidateTickCache();
                    uhc.updateSkyBrightness();
                    check(uhc.isBrightOutside(), "UHC day timeline still reads the vanilla clock");
                } finally {
                    uhc.setRainLevel(rain);
                    uhc.setThunderLevel(thunder);
                    uhc.environmentAttributes().invalidateTickCache();
                    uhc.updateSkyBrightness();
                }
                check(server.overworld().isDarkOutside(), "The vanilla Overworld did not remain at its own night");
                server.getGameRules().set(UhcModeRules.ALWAYS_DAY, false, server);
                UhcClock.tick(server);
            } catch (RuntimeException exception) {
                server.getGameRules().set(UhcModeRules.ALWAYS_DAY, oldDay, server);
                server.getGameRules().set(GameRules.ADVANCE_TIME, oldAdvance, server);
                server.overworld().clockManager().setTotalTicks(vanillaClock, oldTime);
                throw exception;
            }
        });
        context.runAfterDelay(60, () -> {
            try {
                check(uhc.getDefaultClockTime() > 6000, "Turning always-day off did not resume the UHC clock");
                context.succeed();
            } finally {
                server.getGameRules().set(UhcModeRules.ALWAYS_DAY, oldDay, server);
                server.getGameRules().set(GameRules.ADVANCE_TIME, oldAdvance, server);
                server.overworld().clockManager().setTotalTicks(vanillaClock, oldTime);
                UhcClock.tick(server);
            }
        });
    }

    public static void sunriseGrace(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        ServerLevel uhc = server.getLevel(ModDimensions.UHC);
        var manager = server.overworld().clockManager();
        var clock = uhc.dimensionType().defaultClock().orElseThrow();
        var vanillaClock = server.overworld().dimensionType().defaultClock().orElseThrow();
        long oldTime = manager.getTotalTicks(clock), oldVanilla = manager.getTotalTicks(vanillaClock);
        boolean oldDay = server.getGameRules().get(UhcModeRules.ALWAYS_DAY);
        boolean oldAdvance = server.getGameRules().get(GameRules.ADVANCE_TIME);
        int oldGrace = SettingsStorage.resolve(server, Minigames.UHC).get(UhcGame.GRACE_PERIOD);
        float oldRain = uhc.getRainLevel(1), oldThunder = uhc.getThunderLevel(1);
        var weather = uhc.getWeatherData();
        int oldClearTime = weather.getClearWeatherTime(), oldRainTime = weather.getRainTime(),
                oldThunderTime = weather.getThunderTime();
        boolean oldRaining = weather.isRaining(), oldThundering = weather.isThundering();
        Runnable cleanup = () -> {
            server.getGameRules().set(UhcModeRules.ALWAYS_DAY, oldDay, server);
            server.getGameRules().set(GameRules.ADVANCE_TIME, oldAdvance, server);
            SettingsStorage.set(server, Minigames.UHC, UhcGame.GRACE_PERIOD, oldGrace);
            manager.setTotalTicks(clock, oldTime);
            manager.setTotalTicks(vanillaClock, oldVanilla);
            weather.setClearWeatherTime(oldClearTime);
            weather.setRainTime(oldRainTime);
            weather.setThunderTime(oldThunderTime);
            weather.setRaining(oldRaining);
            weather.setThundering(oldThundering);
            uhc.setRainLevel(oldRain);
            uhc.setThunderLevel(oldThunder);
            UhcClock.tick(server);
        };
        io.github.brainage04.brainage_minigames.GameTestLifecycle.afterTest(context, cleanup);
        try {
            server.getGameRules().set(UhcModeRules.ALWAYS_DAY, false, server);
            server.getGameRules().set(GameRules.ADVANCE_TIME, true, server);
            SettingsStorage.set(server, Minigames.UHC, UhcGame.GRACE_PERIOD, 10);
            // Exercise weather inherited from another scenario, then establish real clear weather.
            weather.setRaining(true);
            weather.setThundering(true);
            weather.setClearWeatherTime(Integer.MAX_VALUE);
            weather.setRainTime(0);
            weather.setThunderTime(0);
            weather.setRaining(false);
            weather.setThundering(false);
            uhc.setRainLevel(0);
            uhc.setThunderLevel(0);
            check(!weather.isRaining() && !weather.isThundering(),
                    "Sunrise fixture did not clear inherited rain/thunder flags");
            check(weather.getClearWeatherTime() == Integer.MAX_VALUE && weather.getRainTime() == 0
                            && weather.getThunderTime() == 0,
                    "Sunrise fixture did not establish a clear-weather spell");
            manager.setTotalTicks(clock, 18000);
            Fixture fixture = new Fixture(context, false, false, 3, 2, false);
            Match match = fixture.match;
            check(match.phase() == MatchPhase.LOBBY, "The sunrise fixture skipped its lobby");
            near(0, uhc.getDefaultClockTime(), "Sunrise immediately after opening");
            for (int i = 0; i < 1000; i++) {
                manager.tick();
                UhcClock.tick(server);
                near(0, uhc.getDefaultClockTime(), "Sunrise while waiting in the lobby");
            }
            check(manager.getTotalTicks(vanillaClock) > oldVanilla,
                    "Holding the UHC lobby paused the vanilla clock");
            match.start();
            check(match.preparingSpawns(), "Unloaded spawn terrain did not delay countdown");
            UhcSpawnGameTestFunctions.awaitReady(context, match, () -> {
                try (fixture) {
                    advance(match, 39);
                    manager.tick();
                    UhcClock.tick(server);
                    check(match.phase() == MatchPhase.COUNTDOWN, "The countdown ended early");
                    near(0, uhc.getDefaultClockTime(), "Sunrise on the last countdown tick");
                    near(0, server.getLevel(ModDimensions.UHC_NETHER).getDefaultClockTime(), "Nether sunrise lock");
                    advance(match, 40);
                    check(match.phase() == MatchPhase.ACTIVE && match.activeTicks() == 0,
                            "Countdown zero did not begin grace");
                    near(0, uhc.getDefaultClockTime(), "Sunrise at grace start");
                    ServerPlayer player = fixture.players.getFirst(), enemy = fixture.players.getLast();
                    check(!MatchManager.allowDamage(player, player.damageSources().playerAttack(enemy)),
                            "PvP was not protected at grace start");
                    int grace = match.settings().minutesInTicks(UhcGame.GRACE_PERIOD);
                    check(grace == 12000, "Ten-minute grace is not 12000 ticks");
                    for (int elapsed = 1; elapsed <= grace; elapsed++) {
                        manager.tick();
                        UhcClock.tick(server);
                        uhc.environmentAttributes().invalidateTickCache();
                        uhc.updateSkyBrightness();
                        near(elapsed, uhc.getDefaultClockTime(), "Advancing grace clock");
                        check(uhc.isBrightOutside() && !uhc.isDarkOutside(),
                                "Grace became dark at elapsed tick " + elapsed);
                    }
                    advance(match, grace - 1);
                    check(!MatchManager.allowDamage(player, player.damageSources().playerAttack(enemy)),
                            "PvP was enabled before grace ended");
                    advance(match, grace);
                    check(MatchManager.allowDamage(player, player.damageSources().playerAttack(enemy)),
                            "PvP did not enable at ten minutes");
                    near(12000, uhc.getDefaultClockTime(), "End of ten-minute grace");
                    server.getGameRules().set(UhcModeRules.ALWAYS_DAY, true, server);
                    UhcClock.tick(server);
                    manager.tick();
                    near(6000, uhc.getDefaultClockTime(), "Always-day still locks noon during a match");
                    server.getGameRules().set(UhcModeRules.ALWAYS_DAY, false, server);
                    UhcClock.tick(server);
                    manager.tick();
                    near(6001, uhc.getDefaultClockTime(), "Active-match toggle resumes from noon");
                    fixture.close();
                    try (Fixture cancelled = new Fixture(context, false, false, 3, 2, false)) {
                        near(0, uhc.getDefaultClockTime(), "A new lobby resets the previous match's clock");
                        MatchManager.stop(cancelled.match);
                        manager.tick();
                        near(1, uhc.getDefaultClockTime(), "Cancelling a lobby releases its clock lock");
                    }
                    context.succeed();
                } finally {
                    cleanup.run();
                }
            });
        } catch (MatchException exception) {
            cleanup.run();
            throw new IllegalStateException(exception);
        }
    }

    public static void preparationClock(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        int serverStart = server.getTickCount();
        long testStart = context.getTick();
        io.github.brainage04.brainage_minigames.GameTestLifecycle.awaitPreparation(
                context, () -> server.getTickCount() - serverStart >= 10, () -> {
                    context.assertTrue(server.getTickCount() - serverStart >= 10,
                            "Preparation did not wait for independent server ticks");
                    context.assertTrue(context.getTick() <= testStart + 1,
                            "Preparation consumed the accelerated GameTest tick budget");
                    context.succeed();
                });
    }

    public static void followingRule(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        var rule = UhcModeRules.PRE_PVP_FOLLOWING;
        check(rule.getIdentifier().toString().equals("brainage_minigames:pre_pvp_following"),
                "The shared bot gamerule has the wrong registered id");
        check(!new GameRules(List.of(rule)).get(rule), "Pre-PvP following must default off in a fresh world");
        boolean oldFollowing = server.getGameRules().get(rule);
        try {
            for (boolean enabled : List.of(true, false)) {
                int result = server.getCommands().getDispatcher().execute(
                        "gamerule brainage_minigames:pre_pvp_following " + enabled,
                        server.createCommandSourceStack());
                check(result == (enabled ? 1 : 0), "The bot gamerule is not a vanilla-compatible boolean");
                check(server.getLevel(ModDimensions.UHC).getGameRules().get(rule) == enabled
                                && server.getLevel(ModDimensions.UHC_NETHER).getGameRules().get(rule) == enabled,
                        "The shared bot gamerule differs between the two UHC dimensions");
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            throw new IllegalStateException(exception);
        } finally {
            server.getGameRules().set(rule, oldFollowing, server);
        }
        context.succeed();
    }

    public static void sidebarText(GameTestHelper context) {
        try {
            var clockLine = MatchSidebar.class.getDeclaredMethod("dateTimeLine", long.class);
            clockLine.setAccessible(true);
            long second = 1_791_085_500L;
            Component first = (Component) clockLine.invoke(null, second);
            check(first == clockLine.invoke(null, second), "The unchanged footer must reuse its cached component");
            Component next = (Component) clockLine.invoke(null, second + 1);
            check(!first.equals(next), "The sidebar clock still refreshes only once per minute");
            String expected = DateTimeFormatter.ofPattern("MM/dd/yy HH:mm:ss", java.util.Locale.ROOT)
                    .format(LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(second + 1), java.time.ZoneId.systemDefault()));
            check(next.getString().equals(expected), "The next-second footer has the wrong date/time");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
        List<String> names = List.of("UHC", "Speed UHC", "MiniUHC", "BuildUHC", "Classic", "No Debuff", "Gapple", "Boxing",
                "Combo", "Bow", "Sumo", "SkyWars", "Meetup", "FinalUHC", "Spleef", "Bow Spleef", "Quake",
                "Pearl Fight", "Bridge", "Battle Rush", "Capture the Wool", "Parkour", "Ice Boat Racing");
        check(Minigames.ALL.size() == names.size(), "The mode-label casing test must cover every game");
        for (int i = 0; i < names.size(); i++) {
            check(Minigames.ALL.get(i).displayName().equals(names.get(i)),
                    "Incorrect game label casing for " + Minigames.ALL.get(i).id());
        }
        check(TeamLayout.FREE_FOR_ALL.displayName().equals("FFA"), "The sidebar mode label must be FFA");
        check(TeamLayout.FREE_FOR_ALL.toString().equals("ffa"), "The command spelling must remain ffa");
        for (String layout : List.of("1v1", "2v2", "2v3v4")) {
            check(TeamLayout.parse(layout).orElseThrow().displayName().equals(layout),
                    "Numeric mode labels changed spelling");
        }
        DateTimeFormatter format = DateTimeFormatter.ofPattern("MM/dd/yy HH:mm:ss", java.util.Locale.ROOT);
        String before = format.format(LocalDateTime.now());
        Fixture fixture = new Fixture(context, false, false, 3);
        UhcSpawnGameTestFunctions.awaitReady(context, fixture.match, () -> context.runAfterDelay(15, () -> {
            try {
                assertSidebar(fixture, "UHC FFA", false, before, format);
                check(fixture.match.title().getString().contains("UHC FFA"), "The match title still uses lowercase ffa");
            } finally {
                fixture.close();
            }
            String layout = String.join("v", java.util.Collections.nCopies(20, "1"));
            Fixture crowded = new Fixture(context, false, false, 20, 0, true,
                    TeamLayout.parse(layout).orElseThrow());
            UhcSpawnGameTestFunctions.awaitReady(context, crowded.match, () -> context.runAfterDelay(15, () -> {
                try {
                    assertSidebar(crowded, "UHC " + layout, true, before, format);
                    context.succeed();
                } finally {
                    crowded.close();
                }
            }));
        }));
    }

    private static void assertSidebar(Fixture fixture, String title, boolean crowded, String before,
            DateTimeFormatter format) {
        EmbeddedChannel channel = fixture.channels.getFirst();
        channel.flushOutbound();
        List<Object> packets = new ArrayList<>();
        flattenPackets(channel.outboundMessages(), packets);
        check(packets.stream().anyMatch(packet -> packet instanceof ClientboundSetObjectivePacket objective
                        && objective.getObjectiveName().equals(MatchSidebar.OBJECTIVE_NAME)
                        && objective.getDisplayName().getString().equals(title)),
                "The client did not receive the correctly cased sidebar title: " + title);
        Map<String, ClientboundSetScorePacket> lines = new HashMap<>();
        for (Object packet : packets) {
            if (packet instanceof ClientboundSetScorePacket score
                    && score.objectiveName().equals(MatchSidebar.OBJECTIVE_NAME)) {
                lines.put(score.owner(), score);
            }
        }
        check(!lines.isEmpty() && lines.size() <= MatchSidebar.MAX_LINES,
                "The sidebar and footer exceed the client line limit: " + lines.size());
        if (crowded) {
            check(lines.size() == MatchSidebar.MAX_LINES,
                    "The crowded team sidebar must use its available 15 lines: " + lines.size());
            check(lines.values().stream().anyMatch(line -> line.display().orElseThrow().getString().endsWith(" more")),
                    "Crowded team standings were not condensed to leave room for the footer");
        }
        var bottom = lines.values().stream().min(java.util.Comparator.comparingInt(ClientboundSetScorePacket::score))
                .orElseThrow();
        String timestamp = bottom.display().orElseThrow().getString();
        check(timestamp.matches("\\d{2}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}"),
                "The bottom line is not the documented compact date/time: " + timestamp);
        check(!LocalDateTime.parse(timestamp, format).isBefore(LocalDateTime.parse(before, format))
                        && !LocalDateTime.parse(timestamp, format).isAfter(LocalDateTime.now()),
                "The footer is not the current server-local date/time: " + timestamp);
        check(lines.values().stream().anyMatch(line -> line.display().orElseThrow().getString().startsWith("PvP in: ")),
                "The timestamp displaced the grace label");
    }

    private static void flattenPackets(Iterable<?> source, List<Object> target) {
        for (Object packet : source) {
            if (packet instanceof BundlePacket<?> bundle) {
                flattenPackets(bundle.subPackets(), target);
            } else {
                target.add(packet);
            }
        }
    }

    static void advance(Match match, int ticks) {
        try {
            Field field = Match.class.getDeclaredField("phaseTicks");
            field.setAccessible(true);
            field.setInt(match, ticks - 1);
            MatchManager.tick();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static void readableChat(GameTestHelper context) {
        withFixture(context, new Fixture(context, false, false, 50), fixture -> {
            java.util.Set<String> names = new java.util.HashSet<>();
            for (var team : fixture.match.teams()) {
                int rgb = team.color().orElseThrow().getValue();
                double luminance = 0.2126 * linear((rgb >> 16) & 255)
                        + 0.7152 * linear((rgb >> 8) & 255) + 0.0722 * linear(rgb & 255);
                check((luminance + 0.05) / (linear(17) + 0.05) >= 3,
                        "A team name has poor contrast on the dark chat background");
                check(names.add(team.displayName().getString()),
                        "Repeated palette colours made FFA names ambiguous");
            }
            check(names.size() == 50, "The fifty-player lobby did not start with fifty distinct names");
            context.succeed();
        });
    }

    public static void fiftyPlayerSpread(GameTestHelper context) {
        // The test starts the lobby itself; its own timer must not start it while terrain loads.
        MinecraftServer server = context.getLevel().getServer();
        GameSetting lobbySeconds = Minigames.UHC.setting(GameSetting.LOBBY_SECONDS).orElseThrow();
        int oldLobbySeconds = SettingsStorage.resolve(server, Minigames.UHC).get(lobbySeconds);
        io.github.brainage04.brainage_minigames.GameTestLifecycle.afterTest(context,
                () -> SettingsStorage.set(server, Minigames.UHC, lobbySeconds, oldLobbySeconds));
        SettingsStorage.set(server, Minigames.UHC, lobbySeconds, 0);
        Fixture fixture = new Fixture(context, false, false, 50, 0, false);
        // The players join as the region's lobby is still being found, and are moved there once it is.
        io.github.brainage04.brainage_minigames.GameTestLifecycle.awaitPreparation(context,
                () -> fixture.match.arena().lobbyReady()
                        && fixture.players.stream().allMatch(player -> player.level() == fixture.match.arena().level()),
                () -> spreadFifty(context, fixture));
    }

    private static void spreadFifty(GameTestHelper context, Fixture fixture) {
        Vec3 lobby = fixture.match.arena().lobbyPosition();
        try {
            fixture.match.start();
        } catch (MatchException exception) {
            fixture.close();
            throw new IllegalStateException(exception);
        }
        check(fixture.match.preparingSpawns(), "An unloaded fifty-player match skipped terrain preparation");
        check(fixture.match.phase() == MatchPhase.COUNTDOWN, "Start did not lock the teams");
        for (ServerPlayer player : fixture.players) {
            check(player.position().equals(lobby), "A player moved before its spawn chunks were ready");
        }
        withFixture(context, fixture, ready -> {
            var border = ((UhcArena) ready.match.arena()).border();
            java.util.Set<Vec3> positions = new java.util.HashSet<>();
            for (ServerPlayer player : ready.players) {
                Vec3 position = player.position();
                check(positions.add(position), "Fifty-player flat-terrain spread overlapped");
                check(NaturalTerrain.isDry(player.level(), position), "A player spawned on fluid/non-solid ground");
                check(Math.abs(position.x() - border.getCenterX()) < border.getSize() / 2
                                && Math.abs(position.z() - border.getCenterZ()) < border.getSize() / 2,
                        "A player spawned outside the border");
                double radius = Math.hypot(position.x() - border.getCenterX() - 0.5,
                        position.z() - border.getCenterZ() - 0.5);
                check(radius >= 399 && radius <= 401, "The fifty-player spread radius changed");
            }
            check(positions.size() == 50, "The match did not place all fifty players");
            context.succeed();
        });
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    static void withFixture(GameTestHelper context, Fixture fixture,
            java.util.function.Consumer<Fixture> action) {
        UhcSpawnGameTestFunctions.awaitReady(context, fixture.match, () -> {
            try (fixture) {
                advance(fixture.match, fixture.match.settings().get(GameSetting.COUNTDOWN_SECONDS) * 20);
                check(fixture.match.phase() == MatchPhase.ACTIVE, "Fixture did not start its match");
                fixture.acknowledgeTeleports();
                action.accept(fixture);
            }
        });
    }

    /** A started match of a UHC variant with connected test players; closing it restores every rule and setting it changed. */
    static final class Fixture implements AutoCloseable {
        private final MinecraftServer server;
        private final io.github.brainage04.brainage_minigames.game.Minigame game;
        private final int oldStyle;
        private final boolean oldDeathmatch;
        private final int oldCountdown;
        private final int oldNetherClose;
        private final boolean oldMaxPerks;
        private final boolean oldMaxKits;
        final List<ServerPlayer> players = new ArrayList<>();
        private final List<EmbeddedChannel> channels = new ArrayList<>();
        Match match;
        private boolean closed;

        private Fixture(GameTestHelper context, boolean badlion, boolean deathmatch) {
            this(context, badlion, deathmatch, 2);
        }

        private Fixture(GameTestHelper context, boolean badlion, boolean deathmatch, int count) {
            this(context, badlion, deathmatch, count, 0, true);
        }

        private Fixture(GameTestHelper context, boolean badlion, boolean deathmatch, int count,
                int countdownSeconds, boolean start) {
            this(context, badlion, deathmatch, count, countdownSeconds, start,
                    count == 2 ? TeamLayout.parse("1v1").orElseThrow() : TeamLayout.FREE_FOR_ALL);
        }

        private Fixture(GameTestHelper context, boolean badlion, boolean deathmatch, int count,
                int countdownSeconds, boolean start, TeamLayout layout) {
            this(context, Minigames.UHC, badlion, deathmatch, count, countdownSeconds, start, layout);
        }

        /** Two players of {@code game} on opposing teams, started, with {@code uhc_border_style} at Badlion when {@code badlionRule}. */
        Fixture(GameTestHelper context, io.github.brainage04.brainage_minigames.game.Minigame game, boolean badlionRule,
                boolean deathmatch) {
            this(context, game, badlionRule, deathmatch, 2, 0, true, TeamLayout.parse("1v1").orElseThrow());
        }

        private Fixture(GameTestHelper context, io.github.brainage04.brainage_minigames.game.Minigame game,
                boolean badlion, boolean deathmatch, int count, int countdownSeconds, boolean start, TeamLayout layout) {
            server = context.getLevel().getServer();
            this.game = game;
            oldStyle = server.getGameRules().get(UhcModeRules.BORDER_STYLE);
            oldDeathmatch = server.getGameRules().get(UhcModeRules.DEATHMATCH);
            oldCountdown = SettingsStorage.resolve(server, game).get(GameSetting.COUNTDOWN_SECONDS);
            oldNetherClose = SettingsStorage.resolve(server, game).get(UhcGame.NETHER_CLOSE_TIME);
            oldMaxPerks = server.getGameRules().get(UhcProgression.MAX_ALL);
            oldMaxKits = server.getGameRules().get(UhcProgression.MAX_ALL_KITS);
            server.getGameRules().set(UhcModeRules.BORDER_STYLE, badlion ? 1 : 0, server);
            server.getGameRules().set(UhcModeRules.DEATHMATCH, deathmatch, server);
            SettingsStorage.set(server, game, game.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), countdownSeconds);
            SettingsStorage.set(server, game, UhcGame.NETHER_CLOSE_TIME, 0);
            // Passive perks (Vitamins absorption, Survivalism) would absorb the border and health changes measured here.
            server.getGameRules().set(UhcProgression.MAX_ALL, false, server);
            server.getGameRules().set(UhcProgression.MAX_ALL_KITS, false, server);
            try {
                match = MatchManager.open(server, game, layout, null);
                for (int team = 1; team <= count; team++) {
                    ServerPlayer player = connect(context);
                    MatchManager.join(player, match, layout.isFreeForAll() ? 0 : team);
                }
                if (start) {
                    if (match.phase() == MatchPhase.LOBBY) { match.start(); }
                }
            } catch (MatchException | RuntimeException exception) {
                close();
                throw new IllegalStateException(exception);
            }
            io.github.brainage04.brainage_minigames.GameTestLifecycle.afterTest(context, this::close);
        }
        private ServerPlayer connect(GameTestHelper context) {
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                    new GameProfile(UUID.randomUUID(), "mode" + UUID.randomUUID().toString().substring(0, 8)), false);
            ServerPlayer player = new ServerPlayer(server, context.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            channels.add(TestPlayers.connect(player, cookie));
            players.add(player);
            return player;
        }

        /** An embedded client must acknowledge dimension changes just like a real client. */
        private void acknowledgeTeleports() {
            for (ServerPlayer player : players) {
                player.hasChangedDimension();
                player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            }
        }


        @Override public void close() {
            if (closed) return;
            closed = true;
            if (match != null) { MatchManager.stop(match); }
            for (ServerPlayer player : players) { server.getPlayerList().remove(player); }
            SettingsStorage.set(server, game, game.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), oldCountdown);
            SettingsStorage.set(server, game, UhcGame.NETHER_CLOSE_TIME, oldNetherClose);
            server.getGameRules().set(UhcModeRules.BORDER_STYLE, oldStyle, server);
            server.getGameRules().set(UhcModeRules.DEATHMATCH, oldDeathmatch, server);
            server.getGameRules().set(UhcProgression.MAX_ALL, oldMaxPerks, server);
            server.getGameRules().set(UhcProgression.MAX_ALL_KITS, oldMaxKits, server);
        }
    }

    private static void near(double expected, double actual, String label) {
        check(Math.abs(expected - actual) < 0.0001, label + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new GameTestAssertException(Component.literal(message), 0); }
    }
}
