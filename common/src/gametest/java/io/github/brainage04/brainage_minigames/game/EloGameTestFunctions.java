package io.github.brainage04.brainage_minigames.game;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.brainage04.brainage_minigames.game.arena.BoxArena;
import io.github.brainage04.brainage_minigames.scoreboard.EloRatings;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.scores.ScoreboardSaveData;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.block.Blocks;

public final class EloGameTestFunctions {
    private EloGameTestFunctions() {}

    public static void updates(GameTestHelper context) {
        var server = context.getLevel().getServer();
        var a = new EloRatings.Player(UUID.randomUUID(), "EloHumanA", false, 2000);
        var b = new EloRatings.Player(UUID.randomUUID(), "EloHumanB", false, 2000);
        var bot = new EloRatings.Player(UUID.randomUUID(), "EloFixed", true, 2000);
        int originalK = server.getGameRules().get(EloRatings.K_FACTOR);
        try {
            server.getGameRules().set(EloRatings.K_FACTOR, 32, server);
            context.assertTrue(EloRatings.rating(server, a) == 2000, "A new UUID starts at 2000");
            EloRatings.result(server, a, b, 1);
            context.assertTrue(EloRatings.rating(server, a) == 2016 && EloRatings.rating(server, b) == 1984,
                    "Equal-rating win/loss must update both humans symmetrically");
            EloRatings.result(server, a, bot, 0);
            context.assertTrue(EloRatings.rating(server, a) == 1999 && EloRatings.rating(server, bot) == 2000,
                    "A loss against a bot changes only the human");
            var fresh = new EloRatings.Player(UUID.randomUUID(), "EloFresh", false, 2000);
            var stronger = new EloRatings.Player(UUID.randomUUID(), "EloStrong", true, 2400);
            EloRatings.result(server, fresh, stronger, 0.5);
            context.assertTrue(EloRatings.rating(server, fresh) == 2013 && EloRatings.rating(server, stronger) == 2400,
                    "An underdog draw gains rounded K*(0.5-expected), and bot stays fixed");
            var drawA = new EloRatings.Player(UUID.randomUUID(), "EloDrawA", false, 2000);
            var drawB = new EloRatings.Player(UUID.randomUUID(), "EloDrawB", false, 2000);
            EloRatings.draws(server, List.of(drawA, drawB, stronger));
            context.assertTrue(EloRatings.rating(server, drawA) == 2013 && EloRatings.rating(server, drawB) == 2013
                            && EloRatings.rating(server, stronger) == 2400,
                    "FFA draws count each surviving pair, not a diluted average or a change to the bot");
            EloRatings.result(server, a, b, 0.5);
            context.assertTrue(EloRatings.rating(server, a) == 1998 && EloRatings.rating(server, b) == 1985,
                    "A human-human draw must adjust both pre-draw ratings");
            var saved = new ScoreboardSaveData(ScoreboardSaveData.Packed.EMPTY);
            server.getScoreboard().storeToSaveDataIfDirty(saved);
            var encoded = ScoreboardSaveData.Packed.CODEC.encodeStart(NbtOps.INSTANCE, saved.getData()).getOrThrow();
            var reloaded = new ServerScoreboard(server);
            reloaded.load(ScoreboardSaveData.Packed.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow());
            var stored = reloaded.getPlayerScoreInfo(ScoreHolder.forNameOnly(a.id().toString()),
                    reloaded.getObjective("brainage_elo_uuid"));
            context.assertTrue(stored != null && stored.value() == 1998, "The UUID rating must survive world scoreboard serialization");
            var renamed = new EloRatings.Player(a.id(), "EloRenamed", false, 2000);
            context.assertTrue(EloRatings.rating(server, renamed) == 1998, "Rating must follow UUID, not name");
            server.getGameRules().set(EloRatings.K_FACTOR, 0, server);
            EloRatings.result(server, a, b, 1);
            context.assertTrue(EloRatings.rating(server, a) == 1998, "K=0 disables rating changes");
        } finally {
            server.getGameRules().set(EloRatings.K_FACTOR, originalK, server);
        }
        context.succeed();
    }

    public static void lifecycle(GameTestHelper context) {
        ServerPlayer a = connect(context, "EloMatchA", false);
        ServerPlayer b = connect(context, "EloMatchB", true);
        ServerPlayer c = connect(context, "EloMatchC", false);
        var server = context.getLevel().getServer();
        int originalK = server.getGameRules().get(EloRatings.K_FACTOR);
        Match duel = null;
        Match ffa = null;
        try {
            server.getGameRules().set(EloRatings.K_FACTOR, 32, server);
            duel = match(context, -801, "1v1");
            duel.join(a, 1);
            duel.join(b, 2);
            duel.tick();
            duel.finish(List.of(duel.teamOf(a.getUUID()).orElseThrow()));
            duel.finish(List.of(duel.teamOf(a.getUUID()).orElseThrow()));
            context.assertTrue(EloRatings.rating(server, a.getUUID()) == 2016,
                    "Duel match result updates once, including against a bot");
            context.assertTrue(EloRatings.publish(b) == 2000, "The duel must not update bot Elo");
            context.assertTrue(server.getCommands().getDispatcher().execute("minigames elo EloMatchA",
                    server.createCommandSourceStack()) == 2016, "The live Elo command must expose the updated rating");
            duel.stop();
            ffa = match(context, -802, "ffa");
            ffa.join(a, 0);
            ffa.join(b, 0);
            ffa.join(c, 0);
            ffa.start();
            ffa.tick();
            ffa.allowDamage(b, b.damageSources().playerAttack(a));
            ffa.handleDeath(b);
            context.assertTrue(EloRatings.rating(server, a.getUUID()) == 2031,
                    "FFA kill scores a win against the victim immediately");
            ffa.finish(ffa.standingTeams());
            context.assertTrue(EloRatings.rating(server, a.getUUID()) == 2030
                            && EloRatings.rating(server, c.getUUID()) == 2001,
                    "FFA terminal draw updates only surviving players using pre-draw ratings");
            context.assertTrue(EloRatings.publish(b) == 2000, "Eliminated bot remains fixed after FFA draw");
        } catch (MatchException | CommandSyntaxException exception) {
            throw new RuntimeException(exception);
        } finally {
            if (duel != null) duel.stop();
            if (ffa != null) ffa.stop();
            server.getGameRules().set(EloRatings.K_FACTOR, originalK, server);
            server.getPlayerList().remove(a);
            server.getPlayerList().remove(b);
            server.getPlayerList().remove(c);
        }
        context.succeed();
    }

    private static Match match(GameTestHelper context, int id, String layout) {
        var game = Minigames.CLASSIC;
        return new Match(id, context.getLevel().getServer(), game, TeamLayout.parse(layout).orElseThrow(),
                new GameSettings(game, Map.of(GameSetting.COUNTDOWN_SECONDS, 0)), game.defaultKit(),
                BoxArena.open(context.getLevel(), 21, Blocks.SMOOTH_STONE.defaultBlockState()), Set.of());
    }

    private static ServerPlayer connect(GameTestHelper context, String name, boolean bot) {
        var server = context.getLevel().getServer();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        var player = new ServerPlayer(server, context.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        if (bot) player.addTag("sparringbot");
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        return player;
    }
}
