package io.github.brainage04.brainage_minigames.game;

import io.github.brainage04.brainage_minigames.game.arena.Arena;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A kind of game. {@link Match} owns the shared lifecycle (lobby, teams, countdown, elimination,
 * last team standing, time limit, restoring players); implementations only add what makes their
 * game different.
 */
public interface Minigame {
    /** Stable identifier used in commands and stored settings. */
    String id();

    String displayName();

    /**
     * The directory under {@code structure/maps/} whose maps the game plays on and the map picker
     * offers: its id, unless several games share one set of maps.
     */
    default String mapDirectory() {
        return id();
    }

    /** Every setting this game reads, including {@link GameSetting#common}. */
    List<GameSetting> settings();

    /** Rejects combinations of individually valid settings that cannot be played. */
    default Optional<String> validate(GameSettings settings) {
        return Optional.empty();
    }

    /**
     * Kit given to every player when the match begins, unless the match was opened with another
     * kit.
     */
    Identifier defaultKit();

    /** Game mode players are in while the match is active. */
    GameType playerGameMode();

    Arena openArena(MinecraftServer server, GameSettings settings) throws MatchException;

    /**
     * Opens the arena for a match with {@code layout}; games whose maps hold a limited number of
     * teams override this to pick a map that fits. {@link Arena#maxTeams} is enforced either way.
     */
    default Arena openArena(MinecraftServer server, GameSettings settings, TeamLayout layout)
            throws MatchException {
        return openArena(server, settings);
    }

    default void onStart(Match match) {}

    /**
     * Called once when the match closes, after every member was restored and before the arena is
     * closed; games drop their per-match state here.
     */
    default void onClose(Match match) {}

    /** Called every tick while the match is active, before the win and time-limit checks. */
    default void tick(Match match) {}

    /**
     * Undoes whatever {@link #onStart} applied to a player that their snapshot does not restore;
     * called before a player leaves, disconnects or is restored when the match closes.
     */
    default void onRelease(Match match, ServerPlayer player) {}

    /** Game-specific lines for the bottom of every member's sidebar, in any phase. */
    default void addSidebarLines(Match match, List<Component> lines) {}

    /** Shown after a team's name on the sidebar, such as its points. */
    default Component sidebarTeamSuffix(Match match, MatchTeam team) {
        return Component.empty();
    }

    /** Extra damage rules for alive participants during the active phase. */
    default boolean allowDamage(Match match, ServerPlayer victim, DamageSource source) {
        return true;
    }

    default boolean dropsInventoryOnElimination() {
        return false;
    }

    /**
     * How many times each player may reroll a random (loot table) kit during the countdown; 0
     * gives the kit when the match begins without showing it first.
     */
    default int kitRerolls(GameSettings settings) {
        return 0;
    }

    /** Whether the game ends the match itself, so {@code time_limit_minutes} does not apply. */
    default boolean controlsTimeout(Match match) {
        return false;
    }

    /** Whether a participant who disconnects leaves a combat logger, while {@code uhc_combat_logger} is on. */
    default boolean combatLoggers() {
        return false;
    }

    /**
     * Whether anti-janitor exclusive fights apply to public free-for-all or three-team matches, while
     * {@code anti_janitor} is on.
     */
    default boolean antiJanitor() {
        return false;
    }

    /** Whether matches of this game may fill slots with bots from a {@code MatchBots} provider. */
    default boolean supportsBots() {
        return true;
    }

    /** The layouts most games offer first: even duels, four teams and free-for-all. */
    List<String> COMMON_LAYOUTS = List.of("1v1", "2v2", "3v3", "4v4", "1v1v1v1", "2v2v2v2", "ffa");

    /** Team sizes offered by games that fill a lobby of {@code lobby_size} players. */
    List<Integer> LOBBY_TEAM_SIZES = List.of(2, 3, 4);

    /**
     * The layouts menus and command suggestions offer for this game. A game that fills its lobby to
     * {@code lobby_size} (UHC, Meetup, FinalUHC) offers solo (free-for-all) and teams of two, three
     * and four, with as many teams as fill about that many players (at least two); other games
     * offer {@link #COMMON_LAYOUTS}. Any other layout can still be typed or built.
     */
    default List<TeamLayout> layoutPresets(GameSettings settings) {
        if (setting(GameSetting.LOBBY_SIZE).isEmpty()) {
            return COMMON_LAYOUTS.stream().map(layout -> TeamLayout.parse(layout).orElseThrow()).toList();
        }
        int players = settings.get(GameSetting.LOBBY_SIZE);
        List<TeamLayout> presets = new java.util.ArrayList<>();
        presets.add(TeamLayout.FREE_FOR_ALL);
        for (int size : LOBBY_TEAM_SIZES) {
            presets.add(TeamLayout.teamsOf(size, Math.max(2, Math.round(players / (float) size))));
        }
        return presets;
    }

    /** What happens to an alive participant who dies during the active phase. */
    enum DeathResult {
        /** They become a spectator; the default. */
        ELIMINATE,
        /** They go back to their team's spawn with the kit, through {@link Match#respawn}. */
        RESPAWN
    }

    /**
     * Decides the fate of an alive participant who died in the active phase. {@code killer} is the
     * alive participant credited with the kill: the combat tracker's killer, or the last player who
     * hurt the victim (directly or with a projectile) within 10 seconds, which also covers knocking
     * them into the void.
     */
    default DeathResult onDeath(Match match, ServerPlayer victim, @Nullable ServerPlayer killer) {
        return DeathResult.ELIMINATE;
    }

    /**
     * The death message of an alive participant who dropped below the arena's {@link Arena#voidY
     * void height}; {@code killer} is credited as in {@link #onDeath}.
     */
    default Component voidDeathMessage(ServerPlayer player, @Nullable ServerPlayer killer) {
        MutableComponent message = Component.empty().append(player.getDisplayName());
        return killer == null
                ? message.append(" fell into the void")
                : message.append(" was knocked into the void by ").append(killer.getDisplayName());
    }

    /**
     * The death message of an alive participant as the match announces it, {@code eliminated} when
     * the death takes them out of the match rather than respawning them.
     */
    default Component deathMessage(Match match, ServerPlayer victim, Component message, boolean eliminated) {
        return message;
    }

    /** Called after {@link Match#respawn} placed and reset the player and gave them the kit. */
    default void onRespawn(Match match, ServerPlayer player) {}

    /**
     * Whether an alive participant of the active match may break the block. Everyone else in a
     * match never can.
     */
    default boolean allowBreak(Match match, ServerPlayer player, BlockPos pos, BlockState state) {
        return true;
    }

    /**
     * Whether an alive participant of the active match may place {@code state} at {@code pos}; also
     * asked for bucket fluids, with the fluid's block state. Everyone else in a match never can.
     */
    default boolean allowPlace(Match match, ServerPlayer player, BlockPos pos, BlockState state) {
        return match.arena().canBuild(pos);
    }

    /** Whether an active participant may use an item on or interact with a block. */
    default boolean allowUseOn(Match match, ServerPlayer player) {
        return true;
    }

    /**
     * Called when an alive participant of the active match uses an item. {@link
     * InteractionResult#PASS} lets vanilla continue; any other result cancels the vanilla use and
     * is returned in its place.
     */
    default InteractionResult onUseItem(
            Match match, ServerPlayer player, InteractionHand hand, ItemStack stack) {
        return InteractionResult.PASS;
    }

    /**
     * Called when an alive participant of the active match uses a block (right-clicks it, with
     * any item or none), before vanilla handles it. {@link InteractionResult#PASS} lets vanilla
     * continue; any other result cancels the vanilla use and is returned in its place.
     */
    default InteractionResult onUseBlock(
            Match match, ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    /**
     * Called on the server when a projectile owned by an alive participant of the active match hits
     * a block, before vanilla handles the hit.
     */
    default void onProjectileHitBlock(Match match, Projectile projectile, BlockHitResult hit) {}

    /**
     * Called when an alive participant of the active match right-clicks an entity, before vanilla
     * interacts with it; returning true consumes the click.
     */
    default boolean onInteractEntity(Match match, ServerPlayer player, Entity entity) {
        return false;
    }

    /** Called after {@code attacker} hurt {@code victim}, both alive participants of the active match. */
    default void onDamaged(Match match, ServerPlayer victim, ServerPlayer attacker) {}

    /** Called when an alive participant of the active match swings their arm: a left-click on anything or nothing. */
    default void onSwing(Match match, ServerPlayer player) {}

    /** Called after an alive participant of the active match placed a block or fluid at {@code pos}. */
    default void onBlockPlaced(Match match, ServerPlayer player, BlockPos pos) {}

    default Optional<GameSetting> setting(String key) {
        return settings().stream().filter(setting -> setting.key().equals(key)).findFirst();
    }
}
