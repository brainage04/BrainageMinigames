package io.github.brainage04.brainage_minigames.game;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import org.jspecify.annotations.Nullable;

/**
 * Who may open, start and stop matches. Any player may open a match and then owns it; the owner
 * and game masters (permission level 2) may start and stop it. Players other than game masters
 * own at most {@link #MAX_OPEN_MATCHES} matches at once, counting their private duels. Commands
 * and menus both go through these methods, so the rules are the same everywhere.
 */
public final class MatchService {
    /** {@code brainage_minigames:max_open_matches_per_player}; 0 stops players opening matches. */
    public static final GameRule<Integer> MAX_OPEN_MATCHES = new GameRule<>(
            GameRuleCategory.MISC, GameRuleType.INT, IntegerArgumentType.integer(0),
            GameRuleTypeVisitor::visitInteger, Codec.intRange(0, Integer.MAX_VALUE),
            Integer::intValue, 1, FeatureFlagSet.of());

    private MatchService() {}

    public static void register(BiConsumer<Identifier, GameRule<?>> registry) {
        registry.accept(BrainageMinigames.id("max_open_matches_per_player"), MAX_OPEN_MATCHES);
    }

    /** Whether the player is a game master, who may manage every match and is never limited. */
    public static boolean isOperator(ServerPlayer player) {
        return Commands.LEVEL_GAMEMASTERS.check(player.permissions());
    }

    /** Whether {@code actor} may start or stop {@code match}: its owner or a game master. */
    public static boolean canManage(ServerPlayer actor, Match match) {
        return match.isOwner(actor.getUUID()) || isOperator(actor);
    }

    /** Matches the player owns that have not closed yet, in id order. */
    public static List<Match> ownedBy(UUID playerId) {
        return MatchManager.matches().stream().filter(match -> match.isOwner(playerId)).toList();
    }

    /** Opens a public match owned by {@code actor}; the actor does not join it. */
    public static Match open(
            ServerPlayer actor, Minigame game, TeamLayout layout, @Nullable Identifier kit)
            throws MatchException {
        return open(actor, game, layout, kit, (server, settings) -> game.openArena(server, settings, layout));
    }

    /**
     * As {@link #open(ServerPlayer, Minigame, TeamLayout, Identifier)}, in an arena from {@code
     * arenaFactory}; GameTests use this.
     */
    public static Match open(
            ServerPlayer actor,
            Minigame game,
            TeamLayout layout,
            @Nullable Identifier kit,
            MatchManager.ArenaFactory arenaFactory)
            throws MatchException {
        ensureMayOwn(actor);
        return MatchManager.open(actor.level().getServer(), game, layout, kit, arenaFactory, actor);
    }

    public static void start(ServerPlayer actor, Match match) throws MatchException {
        ensureManages(actor, match, "start");
        match.start();
    }

    public static void stop(ServerPlayer actor, Match match) throws MatchException {
        ensureManages(actor, match, "stop");
        MatchManager.stop(match);
    }

    private static void ensureManages(ServerPlayer actor, Match match, String action)
            throws MatchException {
        if (!canManage(actor, match)) {
            String managers = match.ownerName().map(name -> name + " and operators").orElse("operators");
            throw new MatchException(
                    "Only %s can %s match #%d.".formatted(managers, action, match.id()));
        }
    }

    /**
     * Refuses a player who already owns as many matches as the {@link #MAX_OPEN_MATCHES} rule
     * allows; game masters are never refused.
     */
    static void ensureMayOwn(ServerPlayer player) throws MatchException {
        if (isOperator(player)) return;
        MinecraftServer server = player.level().getServer();
        int limit = server.getGameRules().get(MAX_OPEN_MATCHES);
        List<Match> owned = ownedBy(player.getUUID());
        if (owned.size() < limit) return;
        if (limit == 0) {
            throw new MatchException("Only operators can open matches on this server.");
        }
        throw new MatchException(
                "You already have %d open match%s (limit %d): %s. Stop one with /minigames stop <match> first."
                        .formatted(
                                owned.size(),
                                owned.size() == 1 ? "" : "es",
                                limit,
                                String.join(", ", owned.stream().map(match -> "#" + match.id()).toList())));
    }
}
