package io.github.brainage04.brainage_minigames.game.uhc;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.BundlePacket;
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
        try (Fixture fixture = new Fixture(context, false, false)) {
            Match match = fixture.match;
            var border = match.arena().level().getWorldBorder();
            advance(match, 20 * 60 * 20 - 1);
            near(1000, border.getSize(), "border before 20:00");
            advance(match, 20 * 60 * 20);
            // Tick the real vanilla border interpolator: width falls by one block each second.
            for (int i = 0; i < 20; i++) { border.tick(); }
            near(999, border.getSize(), "width after one second");
            for (int i = 20; i < 15 * 60 * 20; i++) { border.tick(); }
            near(100, border.getSize(), "width at 35:00");
            near(0, border.getLerpTime(), "completed shrink");
        }
        context.succeed();
    }

    public static void badlionBorder(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, true, false)) {
            Match match = fixture.match;
            ServerLevel level = match.arena().level();
            var border = level.getWorldBorder();
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
        }
        context.succeed();
    }

    public static void deathmatch(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, false, true)) {
            Match match = fixture.match;
            UhcArena arena = (UhcArena) match.arena();
            ServerPlayer player = fixture.players.getFirst();
            var resistance = player.getEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE);
            check(resistance != null && !resistance.isVisible() && resistance.showIcon(),
                    "Starter Fire Resistance must hide particles but retain its HUD icon");
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
            player.setHealth(11);
            advance(match, 40 * 60 * 20);
            check(arena.inDeathmatch() && arena.deathmatchFrozen(), "Deathmatch did not start frozen at 40:00");
            check(player.getInventory().getItem(0).getCount() == 7 && player.getHealth() == 11,
                    "Deathmatch reset the survivor's gear or health");
            var border = arena.level().getWorldBorder();
            double width = border.getSize();
            Vec3 spawn = player.position();
            check(Math.hypot(spawn.x() - border.getCenterX(), spawn.z() - border.getCenterZ()) > 43,
                    "Player did not arrive in a rim spawn room");
            player.snapTo(spawn.x() + 3, spawn.y() + 1, spawn.z(), 0, 0);
            advance(match, 40 * 60 * 20 + 100);
            check(player.position().equals(spawn), "Jumping escaped the frozen countdown");
            check(!Minigames.UHC.allowDamage(match, player, player.damageSources().generic()),
                    "Countdown allowed damage");
            advance(match, 40 * 60 * 20 + 200);
            check(!arena.deathmatchFrozen(), "Countdown did not release after ten seconds");
            check(Minigames.UHC.allowDamage(match, player, player.damageSources().generic()),
                    "Deathmatch remained invulnerable after release");
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
            near(width / 2, border.getSize(), "deathmatch border at 46:00");
            match.teams().getFirst().addScore(10);
            advance(match, 50 * 60 * 20);
            check(match.phase() == MatchPhase.ENDED && match.winners().size() == 2,
                    "Survivors did not draw at 50:00 regardless of kills");
        }
        context.succeed();
    }

    public static void disabledDeathmatch(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        GameSetting limit = Minigames.UHC.setting(GameSetting.TIME_LIMIT_MINUTES).orElseThrow();
        SettingsStorage.set(server, Minigames.UHC, limit, 45);
        try (Fixture fixture = new Fixture(context, false, false)) {
            Match match = fixture.match;
            UhcArena arena = (UhcArena) match.arena();
            advance(match, 40 * 60 * 20);
            check(!arena.inDeathmatch(), "Disabled deathmatch teleported the survivors");
            check(match.phase() == MatchPhase.ACTIVE, "Disabling deathmatch ended survival prematurely");
        } finally {
            SettingsStorage.reset(server, Minigames.UHC, limit);
        }
        SettingsStorage.set(server, Minigames.UHC, UhcGame.DEATHMATCH_ENABLED, 0);
        try (Fixture fixture = new Fixture(context, false, true)) {
            advance(fixture.match, 40 * 60 * 20);
            check(!((UhcArena) fixture.match.arena()).inDeathmatch(),
                    "Per-match deathmatch disable was ignored");
        } finally {
            SettingsStorage.reset(server, Minigames.UHC, UhcGame.DEATHMATCH_ENABLED);
        }
        context.succeed();
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
                try (Fixture normal = new Fixture(context, false, false)) {
                    near(20, normal.players.getFirst().getMaxHealth(), "Disabled double-health maximum");
                    near(20, normal.players.getFirst().getHealth(), "Disabled double-health starting health");
                }
                context.succeed();
            } catch (MatchException exception) {
                throw new GameTestAssertException(Component.literal(exception.getMessage()), 0);
            } finally {
                fixture.close();
                server.getGameRules().set(UhcModeRules.DOUBLE_HEALTH, oldHealth, server);
            }
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
                check(uhc.isBrightOutside(), "UHC day timeline still reads the vanilla clock");
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

    private static void advance(Match match, int ticks) {
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
        try (Fixture fixture = new Fixture(context, false, false, 50)) {
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
        }
        context.succeed();
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static final class Fixture implements AutoCloseable {
        private final MinecraftServer server;
        private final int oldStyle;
        private final boolean oldDeathmatch;
        private final List<ServerPlayer> players = new ArrayList<>();
        private final List<EmbeddedChannel> channels = new ArrayList<>();
        private Match match;

        private Fixture(GameTestHelper context, boolean badlion, boolean deathmatch) {
            this(context, badlion, deathmatch, 2);
        }

        private Fixture(GameTestHelper context, boolean badlion, boolean deathmatch, int count) {
            server = context.getLevel().getServer();
            oldStyle = server.getGameRules().get(UhcModeRules.BORDER_STYLE);
            oldDeathmatch = server.getGameRules().get(UhcModeRules.DEATHMATCH);
            server.getGameRules().set(UhcModeRules.BORDER_STYLE, badlion ? 1 : 0, server);
            server.getGameRules().set(UhcModeRules.DEATHMATCH, deathmatch, server);
            SettingsStorage.set(server, Minigames.UHC, Minigames.UHC.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), 0);
            SettingsStorage.set(server, Minigames.UHC, UhcGame.NETHER_CLOSE_TIME, 0);
            try {
                match = MatchManager.open(server, Minigames.UHC,
                        count == 2 ? TeamLayout.parse("1v1").orElseThrow() : TeamLayout.FREE_FOR_ALL, null);
                for (int team = 1; team <= count; team++) {
                    CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                            new GameProfile(UUID.randomUUID(), "mode" + UUID.randomUUID().toString().substring(0, 8)), false);
                    ServerPlayer player = new ServerPlayer(server, context.getLevel(), cookie.gameProfile(), cookie.clientInformation());
                    Connection connection = new Connection(PacketFlow.SERVERBOUND);
                    channels.add(new EmbeddedChannel(connection));
                    server.getPlayerList().placeNewPlayer(connection, player, cookie);
                    player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
                    players.add(player);
                    MatchManager.join(player, match, count == 2 ? team : 0);
                }
                if (count != 2) { match.start(); }
                advance(match, 0);
                check(match.phase() == MatchPhase.ACTIVE, "Fixture did not start its match");
            } catch (MatchException | RuntimeException exception) {
                close();
                throw new IllegalStateException(exception);
            }
        }

        @Override public void close() {
            if (match != null) { MatchManager.stop(match); }
            for (ServerPlayer player : players) { server.getPlayerList().remove(player); }
            SettingsStorage.reset(server, Minigames.UHC, Minigames.UHC.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow());
            SettingsStorage.reset(server, Minigames.UHC, UhcGame.NETHER_CLOSE_TIME);
            server.getGameRules().set(UhcModeRules.BORDER_STYLE, oldStyle, server);
            server.getGameRules().set(UhcModeRules.DEATHMATCH, oldDeathmatch, server);
        }
    }

    private static void near(double expected, double actual, String label) {
        check(Math.abs(expected - actual) < 0.0001, label + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new GameTestAssertException(Component.literal(message), 0); }
    }
}
