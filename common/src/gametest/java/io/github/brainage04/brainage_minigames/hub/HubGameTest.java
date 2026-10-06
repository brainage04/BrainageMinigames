package io.github.brainage04.brainage_minigames.hub;

import io.github.brainage04.brainage_minigames.GameTestLifecycle;
import io.github.brainage04.brainage_minigames.event.ModServerEvents;
import io.github.brainage04.brainage_minigames.TestPlayers;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

/**
 * The hub: its rules for players outside matches, {@code /hub} and {@code /spawn}, returning
 * players after a match, and building one in a world without. Each test has its own environment,
 * so no other test runs while the server-wide hub is on.
 */
public final class HubGameTest {
    private static final int RADIUS = 6;

    /**
     * A player outside every match is put in adventure mode inside the hub, cannot change its
     * blocks, takes no damage and gets survival back on leaving; operators in creative may build.
     */
    public void protectsPlayersOutsideMatches(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        Vec3 spawn = hubAt(context);
        BlockPos inside = BlockPos.containing(spawn).below();
        BlockPos outside = inside.east(RADIUS + 4);
        ServerPlayer player = TestPlayers.connect(context, "hub_hana");
        ServerPlayer builder = TestPlayers.connect(context, "hub_op");
        TestPlayers.setOperator(builder, true);
        AtomicInteger entered = new AtomicInteger();
        UUID playerId = player.getUUID();
        Hub.onEnter(entering -> {
            if (entering.getUUID().equals(playerId)) entered.incrementAndGet();
        });
        player.setGameMode(GameType.SURVIVAL);
        player.snapTo(spawn.x(), spawn.y(), spawn.z(), 0.0F, 0.0F);
        builder.setGameMode(GameType.CREATIVE);
        builder.snapTo(spawn.x(), spawn.y(), spawn.z(), 0.0F, 0.0F);
        player.getFoodData().setFoodLevel(5);
        GameTestLifecycle.afterTest(context, () -> {
            TestPlayers.setOperator(builder, false);
            TestPlayers.disconnect(player, builder);
            Hub.remove(server);
        });
        context.runAfterDelay(6, () -> {
            check(player.gameMode() == GameType.ADVENTURE, "Expected adventure mode in the hub, found " + player.gameMode());
            check(player.entityTags().contains(Hub.ADVENTURE_TAG), "Expected the hub to mark the mode it changed.");
            check(entered.get() == 1, "Expected one hub entry, found " + entered.get());
            check(player.getFoodData().getFoodLevel() == 20, "Expected the hub to keep players fed.");
            check(builder.gameMode() == GameType.CREATIVE, "Expected creative players to keep their mode.");

            var stone = Blocks.STONE.defaultBlockState();
            check(!MatchManager.allowBreak(player, inside, stone) && !MatchManager.allowPlace(player, inside.above(), stone),
                    "Expected the hub to refuse breaking and placing.");
            player.setGameMode(GameType.CREATIVE);
            check(!MatchManager.allowBreak(player, inside, stone), "Expected the hub to refuse non-operators in creative.");
            player.setGameMode(GameType.ADVENTURE);
            check(MatchManager.allowBreak(builder, inside, stone) && MatchManager.allowPlace(builder, inside.above(), stone),
                    "Expected operators in creative mode to build in the hub.");
            check(MatchManager.allowBreak(player, outside, stone), "Expected blocks outside the hub to stay unprotected.");

            var generic = player.damageSources().generic();
            check(!ModServerEvents.allowDamage(player, generic), "Expected no damage in the hub.");
            check(ModServerEvents.allowDamage(player, player.damageSources().genericKill()), "Expected /kill to work in the hub.");

            player.snapTo(outside.getX() + 0.5, outside.getY() + 1, outside.getZ() + 0.5, 0.0F, 0.0F);
            context.runAfterDelay(6, () -> {
                check(player.gameMode() == GameType.SURVIVAL, "Expected survival back outside the hub, found " + player.gameMode());
                check(!player.entityTags().contains(Hub.ADVENTURE_TAG), "Expected the hub mark to be removed.");
                check(ModServerEvents.allowDamage(player, player.damageSources().generic()), "Expected damage outside the hub.");
                context.succeed();
            });
        });
    }

    /**
     * Players leaving a match return to the hub with their saved inventory; {@code /hub} leaves
     * the current match and {@code /spawn} leads to the hub from anywhere.
     */
    public void returnsPlayersAfterMatchesAndByCommand(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        Vec3 spawn = hubAt(context);
        Vec3 home = spawn.add(RADIUS + 20, 0, 0);
        ServerPlayer player = TestPlayers.connect(context, "hub_ivan");
        Match[] matches = new Match[2];
        GameTestLifecycle.afterTest(context, () -> {
            for (Match match : matches) if (match != null) MatchManager.stop(match);
            TestPlayers.disconnect(player);
            Hub.remove(server);
        });
        player.setGameMode(GameType.SURVIVAL);
        player.snapTo(home.x(), home.y(), home.z(), 0.0F, 0.0F);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 4));

        matches[0] = MatchManager.open(server, Minigames.GAPPLE, TeamLayout.FREE_FOR_ALL, null);
        MatchManager.join(player, matches[0], 0);
        check(MatchManager.matchOf(player.getUUID()).isPresent(), "Expected the player in the match.");
        MatchManager.stop(matches[0]);
        check(player.position().distanceTo(spawn) < 0.01 && player.level() == context.getLevel(),
                "Expected the match to return the player to the hub at " + spawn + ", found " + player.position());
        check(player.getInventory().countItem(Items.DIAMOND) == 4, "Expected the saved inventory back.");

        matches[1] = MatchManager.open(server, Minigames.GAPPLE, TeamLayout.FREE_FOR_ALL, null);
        MatchManager.join(player, matches[1], 0);
        run(player, "hub");
        check(MatchManager.matchOf(player.getUUID()).isEmpty(), "Expected /hub to leave the match.");
        check(player.position().distanceTo(spawn) < 0.01, "Expected /hub to lead to the hub, found " + player.position());

        player.snapTo(home.x(), home.y(), home.z(), 0.0F, 0.0F);
        run(player, "spawn");
        check(player.position().distanceTo(spawn) < 0.01, "Expected /spawn to lead to the hub, found " + player.position());
        context.runAfterDelay(6, () -> {
            check(player.gameMode() == GameType.ADVENTURE, "Expected adventure mode after returning to the hub.");
            context.succeed();
        });
    }

    /**
     * A world without a hub gets one at its spawn: a solid platform under the spawn with headroom,
     * lamps and the command sign; the world spawn moves onto it and the hub turns on.
     */
    public void buildsHubAtWorldSpawn(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        LevelData.RespawnData previousSpawn = server.getRespawnData();
        GameTestLifecycle.afterTest(context, () -> {
            Hub.remove(server);
            server.setRespawnData(previousSpawn);
        });
        check(Hub.config() == null, "Expected the GameTest world to start without a hub.");
        Hub.buildAtWorldSpawn(server);
        Hub.Config config = Hub.config();
        check(config != null && config.enabled() && config.radius() == Hub.DEFAULT_RADIUS, "Expected an enabled hub, found " + config);
        ServerLevel level = server.getLevel(config.dimension());
        BlockPos stand = BlockPos.containing(config.spawn());
        check(Math.abs(stand.getX() - previousSpawn.pos().getX()) <= 1 && Math.abs(stand.getZ() - previousSpawn.pos().getZ()) <= 1,
                "Expected the hub at the world spawn " + previousSpawn.pos() + ", found " + stand);
        check(level.getBlockState(stand.below()).is(Blocks.SMOOTH_STONE), "Expected the hub floor under the spawn, found " + level.getBlockState(stand.below()));
        check(level.getBlockState(stand).isAir() && level.getBlockState(stand.above()).isAir(), "Expected headroom at the spawn.");
        check(level.getBlockState(stand.below().offset(4, 0, 4)).is(Blocks.SEA_LANTERN), "Expected lamps in the floor.");
        check(level.getBlockState(stand.offset(HubBuilder.RADIUS, 0, 2)).is(Blocks.STONE_BRICK_WALL),
                "Expected a wall around the rim.");
        check(level.getBlockEntity(stand.north(3)) instanceof SignBlockEntity sign
                        && sign.getFrontText().getMessage(2, false).getString().equals("/feedback <msg>"),
                "Expected the command sign facing the spawn.");
        check(server.getRespawnData().pos().equals(stand), "Expected the world spawn on the hub, found " + server.getRespawnData().pos());
        check(Hub.contains(level, stand.offset(Hub.DEFAULT_RADIUS - 1, 0, 0)) && !Hub.contains(level, stand.offset(Hub.DEFAULT_RADIUS + 1, 0, 0)),
                "Expected the protected radius around the spawn.");
        context.succeed();
    }

    /** Turns on a hub centred on the test with radius {@value #RADIUS}; returns its spawn. */
    private static Vec3 hubAt(GameTestHelper context) {
        Vec3 spawn = Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(1, 2, 1)));
        Hub.set(context.getLevel().getServer(),
                new Hub.Config(context.getLevel().dimension(), spawn, 0.0F, RADIUS, true));
        return spawn;
    }

    private static void run(ServerPlayer player, String command) {
        player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(Component.literal(message), 0);
    }
}
