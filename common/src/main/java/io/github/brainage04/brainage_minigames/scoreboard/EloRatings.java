package io.github.brainage04.brainage_minigames.scoreboard;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** The world scoreboard persists the UUID ledger; the named objective is the public integration API. */
public final class EloRatings {
    public static final int INITIAL = 2000;
    public static final String OBJECTIVE = "brainage_elo";
    private static final String UUID_OBJECTIVE = "brainage_elo_uuid";
    public static final GameRule<Integer> K_FACTOR = new GameRule<>(
            GameRuleCategory.MISC, GameRuleType.INT, IntegerArgumentType.integer(0),
            GameRuleTypeVisitor::visitInteger, Codec.intRange(0, Integer.MAX_VALUE),
            Integer::intValue, 32, FeatureFlagSet.of());

    public record Player(UUID id, String name, boolean bot, int fixedRating) {}

    private EloRatings() {}

    public static void register(BiConsumer<Identifier, GameRule<?>> registry) {
        registry.accept(BrainageMinigames.id("elo_k_factor"), K_FACTOR);
    }

    private static Objective objective(ServerScoreboard board, String name) {
        Objective existing = board.getObjective(name);
        return existing != null ? existing : board.addObjective(name, ObjectiveCriteria.DUMMY,
                Component.literal("Elo"), ObjectiveCriteria.RenderType.INTEGER, false, null);
    }

    public static Player player(ServerPlayer player) {
        boolean bot = player.entityTags().contains("sparringbot");
        var board = player.level().getServer().getScoreboard();
        var score = board.getPlayerScoreInfo(player, objective(board, OBJECTIVE));
        return new Player(player.getUUID(), player.getScoreboardName(), bot,
                bot && score != null ? score.value() : INITIAL);
    }

    public static int rating(MinecraftServer server, UUID id) {
        var board = server.getScoreboard();
        var objective = objective(board, UUID_OBJECTIVE);
        var holder = ScoreHolder.forNameOnly(id.toString());
        var existing = board.getPlayerScoreInfo(holder, objective);
        if (existing != null) return existing.value();
        board.getOrCreatePlayerScore(holder, objective).set(INITIAL);
        return INITIAL;
    }

    public static int rating(MinecraftServer server, Player player) {
        return player.bot() ? player.fixedRating() : rating(server, player.id());
    }

    public static int publish(ServerPlayer player) {
        Player identity = player(player);
        int value = rating(player.level().getServer(), identity);
        player.level().getServer().getScoreboard()
                .getOrCreatePlayerScore(player, objective(player.level().getServer().getScoreboard(), OBJECTIVE))
                .set(value);
        return value;
    }

    private static void set(MinecraftServer server, Player player, int value) {
        if (player.bot()) return;
        var board = server.getScoreboard();
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(player.id().toString()),
                objective(board, UUID_OBJECTIVE)).set(value);
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(player.name()), objective(board, OBJECTIVE)).set(value);
    }

    public static double expected(int rating, int opponent) {
        return 1.0 / (1.0 + Math.pow(10.0, ((double) opponent - rating) / 400.0));
    }

    /** Both changes use the pre-result ratings, including a fixed bot's rating. */
    public static void result(MinecraftServer server, Player first, Player second, double score) {
        if (first.id().equals(second.id())) return;
        int a = rating(server, first);
        int b = rating(server, second);
        int k = server.overworld().getGameRules().get(K_FACTOR);
        long change = Math.round(k * (score - expected(a, b)));
        set(server, first, bounded((long) a + change));
        set(server, second, bounded((long) b - change));
    }

    /** Each unordered pair of surviving FFA players draws, with all expectations snapshotted before the result. */
    public static void draws(MinecraftServer server, List<Player> players) {
        int[] ratings = players.stream().mapToInt(player -> rating(server, player)).toArray();
        int k = server.overworld().getGameRules().get(K_FACTOR);
        for (int i = 0; i < players.size(); i++) {
            double change = 0.0;
            for (int j = 0; j < players.size(); j++) {
                if (i != j) change += 0.5 - expected(ratings[i], ratings[j]);
            }
            set(server, players.get(i), bounded((long) ratings[i] + Math.round(k * change)));
        }
    }

    /** One K-scaled result per player, against the mean expectation over their opposition. */
    public static void results(MinecraftServer server, List<Player> players, int[] teams, List<Integer> winners) {
        int[] ratings = players.stream().mapToInt(player -> rating(server, player)).toArray();
        int k = server.overworld().getGameRules().get(K_FACTOR);
        for (int i = 0; i < players.size(); i++) {
            double change = 0.0;
            int opponents = 0;
            for (int j = 0; j < players.size(); j++) {
                if (teams[i] == teams[j]) continue;
                boolean won = winners.contains(teams[i]);
                boolean otherWon = winners.contains(teams[j]);
                double score = won == otherWon ? 0.5 : won ? 1.0 : 0.0;
                change += score - expected(ratings[i], ratings[j]);
                opponents++;
            }
            if (opponents > 0) set(server, players.get(i),
                    bounded((long) ratings[i] + Math.round(k * change / opponents)));
        }
    }

    private static int bounded(long rating) {
        return (int) Math.clamp(rating, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
}
