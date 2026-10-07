package io.github.brainage04.brainage_minigames.game.sumo;

import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchSidebar;
import io.github.brainage04.brainage_minigames.game.MatchTeam;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.storage.KitStorage;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Sumo, as on Hypixel Duels and Minemen Club: empty-handed players on a small raised platform,
 * where hits only knock back. Whoever drops below the platform (into the water or void under it,
 * or onto the ground around it) is out until the round ends: they watch in spectator mode. The
 * round goes to the last team with a player still on the platform, and the first team to {@link
 * #ROUNDS_TO_WIN} rounds wins. Each new round puts everyone back on their spawn, frozen through a
 * short countdown.
 *
 * <p>Maps mark {@code spawn <team>} on the platform and {@code void} one block under its surface,
 * so a player is out as soon as their feet are a block below it; the {@code lobby} marker, above
 * the platform, is where players who are out watch from. Nothing in a Sumo map can change (no
 * items, adventure mode), so rounds never rebuild it.
 */
public final class SumoGame implements Minigame {
    public static final String ID = "sumo";

    public static final GameSetting ROUNDS_TO_WIN =
            new GameSetting("rounds_to_win", 3, 1, 100, "Rounds a team needs to win");
    public static final GameSetting ROUND_COUNTDOWN_SECONDS =
            new GameSetting(
                    "round_countdown_seconds",
                    3,
                    0,
                    30,
                    "Seconds everyone is frozen on their spawn before each round after the first");

    private static final List<GameSetting> SETTINGS;

    static {
        List<GameSetting> all = new ArrayList<>(GameSetting.common(3, 5, false));
        all.add(ROUNDS_TO_WIN);
        all.add(ROUND_COUNTDOWN_SECONDS);
        SETTINGS = List.copyOf(all);
    }

    private final Map<Match, State> states = new HashMap<>();

    /** Per-match state. */
    private static final class State {
        /** Players knocked off this round, watching until it ends. */
        private final Set<UUID> out = new HashSet<>();

        /** Ticks until the frozen players are released; 0 while a round is being played. */
        private int countdownTicks;

        private int round = 1;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Sumo";
    }

    @Override
    public List<GameSetting> settings() {
        return SETTINGS;
    }

    @Override
    public Identifier defaultKit() {
        return KitStorage.EMPTY_KIT;
    }

    @Override
    public GameType playerGameMode() {
        return GameType.ADVENTURE;
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings settings) throws MatchException {
        return MapArena.openRandom(server, ID, 2);
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings settings, TeamLayout layout)
            throws MatchException {
        return MapArena.openRandom(
                server, ID, layout.isFreeForAll() ? 2 : layout.teamSizes().size());
    }

    @Override
    public void onStart(Match match) {
        states.put(match, new State());
        for (ServerPlayer player : match.alivePlayers()) {
            equip(player);
        }
    }

    @Override
    public void onClose(Match match) {
        states.remove(match);
    }

    /** Resistance V cancels every hit's damage but keeps its knockback; nobody gets hungry. */
    private static void equip(ServerPlayer player) {
        player.addEffect(
                new MobEffectInstance(
                        MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4, false, false));
        player.addEffect(
                new MobEffectInstance(
                        MobEffects.SATURATION, MobEffectInstance.INFINITE_DURATION, 0, false, false));
    }

    @Override
    public void tick(Match match) {
        State state = states.get(match);
        if (state == null) {
            return;
        }
        if (state.countdownTicks > 0) {
            tickCountdown(match, state);
            return;
        }
        List<MatchTeam> onPlatform = teamsOnPlatform(match, state);
        if (onPlatform.size() <= 1 && match.standingTeams().size() > 1) {
            endRound(match, state, onPlatform.isEmpty() ? null : onPlatform.getFirst());
        }
    }

    /** Teams with a player still in this round: alive, online and not knocked off. */
    private static List<MatchTeam> teamsOnPlatform(Match match, State state) {
        Set<MatchTeam> teams = new HashSet<>();
        for (ServerPlayer player : match.alivePlayers()) {
            if (!state.out.contains(player.getUUID())) {
                match.teamOf(player.getUUID()).ifPresent(teams::add);
            }
        }
        return match.teams().stream().filter(teams::contains).toList();
    }

    /** Scores the round for {@code winner} (none when the last players fell together). */
    private void endRound(Match match, State state, @Nullable MatchTeam winner) {
        Component title;
        if (winner == null) {
            title = Component.literal("Draw!").withStyle(ChatFormatting.GRAY);
            match.broadcast(
                    Component.literal("Round " + state.round + " is a draw: everyone fell. ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(scoreLine(match)));
        } else {
            winner.addScore(1);
            title = Component.empty().append(winner.displayName()).append(" won the round!");
            match.broadcast(
                    Component.empty()
                            .append(winner.displayName())
                            .append(Component.literal(" won round " + state.round + "! ")
                                    .withStyle(ChatFormatting.GOLD))
                            .append(scoreLine(match)));
            if (winner.score() >= match.settings().get(ROUNDS_TO_WIN)) {
                match.finish(List.of(winner));
                return;
            }
        }
        state.round++;
        startRound(match, state, title);
    }

    /** Puts every player back on their spawn, frozen until the countdown runs out. */
    private static void startRound(Match match, State state, Component title) {
        state.out.clear();
        int ticks = match.settings().get(ROUND_COUNTDOWN_SECONDS) * 20;
        Component subtitle = scoreLine(match);
        for (ServerPlayer player : match.alivePlayers()) {
            match.respawn(player);
            match.freeze(player, ticks);
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);
        }
        state.countdownTicks = ticks;
    }

    private static void tickCountdown(Match match, State state) {
        state.countdownTicks--;
        if (state.countdownTicks == 0) {
            for (ServerPlayer player : match.alivePlayers()) {
                unfreeze(player);
            }
            match.broadcastActionBar(
                    Component.literal("Round " + state.round + ": fight!")
                            .withStyle(ChatFormatting.GREEN));
        } else if (state.countdownTicks % 20 == 0) {
            match.broadcastActionBar(
                    Component.literal(
                                    "Round " + state.round + " starts in "
                                            + state.countdownTicks / 20 + "...")
                            .withStyle(ChatFormatting.GOLD));
            for (ServerPlayer player : match.alivePlayers()) {
                player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
            }
        }
    }

    /** Undoes {@link Match#freeze}, whose effects outlast the countdown. */
    private static void unfreeze(ServerPlayer player) {
        player.removeEffect(MobEffects.SLOWNESS);
        player.removeEffect(MobEffects.MINING_FATIGUE);
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.SLOW_FALLING);
    }

    /** A knocked-off player is out until the round ends, then comes back with everyone else. */
    @Override
    public DeathResult onDeath(Match match, ServerPlayer victim, @Nullable ServerPlayer killer) {
        State state = states.get(match);
        if (state != null) {
            state.out.add(victim.getUUID());
        }
        return DeathResult.RESPAWN;
    }

    /** Back on the spawn with the kit, or, when out for the rest of the round, watching from above. */
    @Override
    public void onRespawn(Match match, ServerPlayer player) {
        equip(player);
        State state = states.get(match);
        if (state != null && state.out.contains(player.getUUID())) {
            player.setGameMode(GameType.SPECTATOR);
            PlayerUtils.teleport(
                    player, match.arena().level(), match.arena().lobbyPosition(), player.getYRot());
        }
    }

    @Override
    public Component voidDeathMessage(ServerPlayer player, @Nullable ServerPlayer killer) {
        MutableComponent message = Component.empty().append(player.getDisplayName());
        return killer == null
                ? message.append(" fell off the platform")
                : message.append(" was knocked off by ").append(killer.getDisplayName());
    }

    /** Whether the players are frozen on their spawns before a round. */
    public boolean isCountingDown(Match match) {
        State state = states.get(match);
        return state != null && state.countdownTicks > 0;
    }

    /** Whether {@code player} was knocked off and waits for the next round. */
    public boolean isOut(Match match, UUID player) {
        State state = states.get(match);
        return state != null && state.out.contains(player);
    }

    /** The round being played, from 1. */
    public int round(Match match) {
        State state = states.get(match);
        return state == null ? 1 : state.round;
    }

    /** Only other players' hits land, for their knockback; never during the countdown. */
    @Override
    public boolean allowDamage(Match match, ServerPlayer victim, DamageSource source) {
        return !isCountingDown(match) && source.getEntity() instanceof ServerPlayer;
    }

    @Override
    public boolean allowBreak(Match match, ServerPlayer player, BlockPos pos, BlockState state) {
        return false;
    }

    @Override
    public boolean allowPlace(Match match, ServerPlayer player, BlockPos pos, BlockState state) {
        return false;
    }

    @Override
    public boolean allowUseOn(Match match, ServerPlayer player) {
        return false;
    }

    @Override
    public void addSidebarLines(Match match, List<Component> lines) {
        lines.add(
                MatchSidebar.label(
                        "Rounds to win: ", String.valueOf(match.settings().get(ROUNDS_TO_WIN))));
        match.mapName().ifPresent(name -> lines.add(MatchSidebar.label("Map: ", name)));
        if (states.containsKey(match)) {
            lines.add(MatchSidebar.label("Round: ", String.valueOf(round(match))));
        }
    }

    /** Rounds won as dots out of the target, then how many of the team are still on the platform. */
    @Override
    public Component sidebarTeamSuffix(Match match, MatchTeam team) {
        MutableComponent suffix =
                Component.literal(" ")
                        .append(MatchSidebar.scoreDots(team, match.settings().get(ROUNDS_TO_WIN)));
        State state = states.get(match);
        if (state != null && team.members().size() > 1) {
            long in =
                    team.members().stream()
                            .filter(member -> match.isAlive(member) && !state.out.contains(member))
                            .count();
            suffix.append(
                    Component.literal(" " + in + "/" + team.members().size())
                            .withStyle(ChatFormatting.GRAY));
        }
        return suffix;
    }

    private static Component scoreLine(Match match) {
        MutableComponent line = Component.empty();
        for (MatchTeam team : match.teams()) {
            if (!line.getSiblings().isEmpty()) {
                line.append(Component.literal(" - ").withStyle(ChatFormatting.GRAY));
            }
            line.append(
                    Component.literal(String.valueOf(team.score()))
                            .withStyle(style -> team.color().map(style::withColor).orElse(style)));
        }
        return line;
    }
}
