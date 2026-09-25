package io.github.brainage04.brainage_minigames.game;

import io.github.brainage04.brainage_minigames.game.arena.Arena;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
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
     * Called on the server when a projectile owned by an alive participant of the active match hits
     * a block, before vanilla handles the hit.
     */
    default void onProjectileHitBlock(Match match, Projectile projectile, BlockHitResult hit) {}

    default Optional<GameSetting> setting(String key) {
        return settings().stream().filter(setting -> setting.key().equals(key)).findFirst();
    }
}
