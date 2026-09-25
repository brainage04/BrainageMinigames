package io.github.brainage04.brainage_minigames.game;

import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.storage.KitStorage;
import io.github.brainage04.brainage_minigames.storage.PlayerSnapshotStorage;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.scores.PlayerTeam;

/** Every open match on the server. Any number of matches can run at once, each in its own arena. */
public final class MatchManager {
    private static final Map<Integer, Match> MATCHES = new TreeMap<>();
    private static int nextId = 1;

    private MatchManager() {}

    public static Match open(
            MinecraftServer server, Minigame game, TeamLayout layout, Identifier kitOverride)
            throws MatchException {
        return open(
                server,
                game,
                layout,
                kitOverride,
                (openServer, settings) -> game.openArena(openServer, settings, layout));
    }

    /**
     * Opens a match in an arena from {@code arenaFactory} instead of the game's own. GameTests use
     * this: the GameTest server creates only the Overworld, so the mod's dimensions do not exist
     * there.
     */
    public static Match open(
            MinecraftServer server,
            Minigame game,
            TeamLayout layout,
            Identifier kitOverride,
            ArenaFactory arenaFactory)
            throws MatchException {
        Match match = create(server, game, layout, kitOverride, arenaFactory, Set.of());
        String joinCommand = "/minigames join " + match.id();
        server.getPlayerList()
                .broadcastSystemMessage(
                        Component.empty()
                                .append(match.title())
                                .append(" is open. ")
                                .append(
                                        Component.literal("[Join]")
                                                .withStyle(
                                                        style ->
                                                                style.withColor(
                                                                                ChatFormatting
                                                                                        .GREEN)
                                                                        .withClickEvent(
                                                                                new ClickEvent
                                                                                        .RunCommand(
                                                                                        joinCommand))))
                                .withStyle(ChatFormatting.GOLD),
                        false);
        return match;
    }

    /** Opens a match only {@code participants} may join, without announcing it; duels use this. */
    public static Match openPrivate(
            MinecraftServer server,
            Minigame game,
            TeamLayout layout,
            ArenaFactory arenaFactory,
            Collection<UUID> participants)
            throws MatchException {
        return create(server, game, layout, null, arenaFactory, Set.copyOf(participants));
    }

    private static Match create(
            MinecraftServer server,
            Minigame game,
            TeamLayout layout,
            Identifier kitOverride,
            ArenaFactory arenaFactory,
            Set<UUID> invited)
            throws MatchException {
        GameSettings settings = SettingsStorage.resolve(server, game);
        Optional<String> invalid = game.validate(settings);
        if (invalid.isPresent()) {
            throw new MatchException(
                    "The %s settings are invalid: %s".formatted(game.displayName(), invalid.get()));
        }
        Identifier kit = kitOverride == null ? game.defaultKit() : kitOverride;
        if (!KitStorage.exists(server, kit)) {
            throw new MatchException("Unknown kit " + kit + ".");
        }
        Arena arena = arenaFactory.open(server, settings);
        if (!layout.isFreeForAll() && layout.teamSizes().size() > arena.maxTeams()) {
            arena.close();
            throw new MatchException(
                    "%s %s needs %d teams, but the map has room for %d."
                            .formatted(
                                    game.displayName(),
                                    layout,
                                    layout.teamSizes().size(),
                                    arena.maxTeams()));
        }
        Match match = new Match(nextId++, server, game, layout, settings, kit, arena, invited);
        MATCHES.put(match.id(), match);
        return match;
    }

    @FunctionalInterface
    public interface ArenaFactory {
        Arena open(MinecraftServer server, GameSettings settings) throws MatchException;
    }

    public static Collection<Match> matches() {
        return List.copyOf(MATCHES.values());
    }

    public static Optional<Match> get(int id) {
        return Optional.ofNullable(MATCHES.get(id));
    }

    public static Optional<Match> matchOf(UUID playerId) {
        return MATCHES.values().stream().filter(match -> match.involves(playerId)).findFirst();
    }

    public static void join(ServerPlayer player, Match match, int teamNumber)
            throws MatchException {
        ensureAvailable(player);
        if (player.gameMode() == GameType.SPECTATOR) {
            throw new MatchException(
                    "Spectators cannot join as participants; use /minigames watch instead.");
        }
        match.join(player, teamNumber);
    }

    public static void watch(ServerPlayer player, Match match) throws MatchException {
        ensureAvailable(player);
        match.watch(player);
    }

    private static void ensureAvailable(ServerPlayer player) throws MatchException {
        Optional<String> reason = busyReason(player);
        if (reason.isPresent()) {
            throw new MatchException(player.getScoreboardName() + " " + reason.get() + ".");
        }
    }

    /**
     * Why the player cannot be made a participant of a match right now, phrased to follow their
     * name ("is dead"); empty when they can.
     */
    public static Optional<String> unavailableReason(ServerPlayer player) {
        Optional<String> reason = busyReason(player);
        if (reason.isEmpty() && player.gameMode() == GameType.SPECTATOR) {
            return Optional.of("is spectating");
        }
        return reason;
    }

    private static Optional<String> busyReason(ServerPlayer player) {
        if (!player.isAlive()) {
            return Optional.of("is dead");
        }
        Optional<Match> current = matchOf(player.getUUID());
        if (current.isPresent()) {
            return Optional.of("is already in match #" + current.get().id());
        }
        if (PlayerSnapshotStorage.hasSnapshot(player.level().getServer(), player.getUUID())) {
            return Optional.of(
                    "is still waiting for their state from an earlier match to be restored");
        }
        return Optional.empty();
    }

    public static Match leave(ServerPlayer player) throws MatchException {
        Match match =
                matchOf(player.getUUID())
                        .orElseThrow(() -> new MatchException("You are not in a match."));
        match.leave(player);
        return match;
    }

    public static void stop(Match match) {
        match.stop();
        MATCHES.remove(match.id());
    }

    public static void stopAll() {
        for (Match match : List.copyOf(MATCHES.values())) {
            stop(match);
        }
        nextId = 1;
    }

    public static void tick() {
        for (Match match : List.copyOf(MATCHES.values())) {
            match.tick();
            if (match.isClosed()) {
                MATCHES.remove(match.id());
            }
        }
    }

    public static boolean allowDamage(ServerPlayer victim, DamageSource source) {
        Optional<Match> match = matchOf(victim.getUUID());
        if (match.isPresent()) {
            return match.get().allowDamage(victim, source);
        }
        // Participants cannot reach players outside their match.
        return !(source.getEntity() instanceof ServerPlayer attacker
                && matchOf(attacker.getUUID()).isPresent());
    }

    /**
     * Whether the player is in a match whose {@code natural_regeneration} setting is off; their
     * food then no longer heals them, as if the game rule were off for them alone.
     */
    public static boolean naturalRegenerationDisabled(ServerPlayer player) {
        UUID playerId = player.getUUID();
        for (Match match : MATCHES.values()) {
            if (match.involves(playerId)) {
                return !match.settings().enabled(GameSetting.NATURAL_REGENERATION);
            }
        }
        return false;
    }

    /** Returns whether the player may die; members of a match never do. */
    public static boolean allowDeath(ServerPlayer player) {
        Optional<Match> match = matchOf(player.getUUID());
        match.ifPresent(value -> value.handleDeath(player));
        return match.isEmpty();
    }

    /**
     * Whether the player may break the block; members of a match may only while alive in its active
     * phase, and then as the game allows. A refused break leaves the block in place.
     */
    public static boolean allowBreak(ServerPlayer player, BlockPos pos, BlockState state) {
        Optional<Match> match = matchOf(player.getUUID());
        return match.isEmpty() || match.get().allowBreak(player, pos, state);
    }

    /** Forgets a placed block once any player broke it. */
    public static void blockBroken(ServerPlayer player, BlockPos pos) {
        matchOf(player.getUUID()).ifPresent(match -> match.blockBroken(pos));
    }

    /**
     * Whether the player may place {@code state} (a block, or a bucket's fluid) at {@code pos}; as
     * {@link #allowBreak}.
     */
    public static boolean allowPlace(ServerPlayer player, BlockPos pos, BlockState state) {
        Optional<Match> match = matchOf(player.getUUID());
        return match.isEmpty() || match.get().allowPlace(player, pos, state);
    }

    /** Records a block or fluid the player placed, for {@link Match#isPlacedBlock}. */
    public static void blockPlaced(ServerPlayer player, BlockPos pos) {
        matchOf(player.getUUID()).ifPresent(match -> match.blockPlaced(player, pos));
    }

    /**
     * The result of the player using an item, or {@link InteractionResult#PASS} to let vanilla
     * handle it; members who are not playing cannot use items.
     */
    public static InteractionResult useItem(
            ServerPlayer player, InteractionHand hand, ItemStack stack) {
        Optional<Match> match = matchOf(player.getUUID());
        return match.isEmpty() ? InteractionResult.PASS : match.get().useItem(player, hand, stack);
    }

    /** Whether the player may use an item on a block or interact with it. */
    public static boolean allowUseOn(ServerPlayer player) {
        Optional<Match> match = matchOf(player.getUUID());
        return match.isEmpty() || match.get().isActiveParticipant(player.getUUID());
    }

    /** Tells the game when a projectile of one of its alive participants hits a block. */
    public static void projectileHitBlock(Projectile projectile, BlockHitResult hit) {
        if (MATCHES.isEmpty() || !(projectile.getOwner() instanceof ServerPlayer owner)) {
            return;
        }
        matchOf(owner.getUUID())
                .filter(match -> match.isActiveParticipant(owner.getUUID()))
                .ifPresent(match -> match.projectileHitBlock(projectile, hit));
    }

    /** Restores a player whose match ended, or who was removed from it, while they were offline. */
    public static void handleConnect(ServerPlayer player) {
        if (matchOf(player.getUUID()).isEmpty()
                && PlayerSnapshotStorage.hasSnapshot(
                        player.level().getServer(), player.getUUID())) {
            PlayerSnapshotStorage.restore(player);
        }
    }

    public static void handleDisconnect(ServerPlayer player) {
        matchOf(player.getUUID()).ifPresent(match -> match.disconnect(player));
    }

    /**
     * Removes scoreboard teams left behind by matches that were running when the server crashed.
     */
    public static void removeLeftoverTeams(ServerScoreboard scoreboard) {
        for (PlayerTeam team : List.copyOf(scoreboard.getPlayerTeams())) {
            if (team.getName().startsWith(Match.TEAM_PREFIX)) {
                scoreboard.removePlayerTeam(team);
            }
        }
    }
}
