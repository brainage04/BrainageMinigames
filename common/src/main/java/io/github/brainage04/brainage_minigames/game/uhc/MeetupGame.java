package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.AntiJanitor;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.MatchSidebar;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * UHC Meetup: the last minutes of a UHC. Everyone starts with a random late-game kit on a small
 * patch of generated terrain, PvP is on from the start, there is no natural regeneration, and the
 * border closes in step by step until one team is left.
 */
public final class MeetupGame implements Minigame {
    public static final GameSetting BORDER_START_SIZE =
            new GameSetting(
                    "border_start_size",
                    100,
                    16,
                    1_000,
                    "Side length of the border when the match starts");
    public static final GameSetting FIRST_SHRINK_TIME =
            new GameSetting(
                    "first_shrink_seconds",
                    120,
                    1,
                    3_600,
                    "Seconds before the border starts its first shrink");
    public static final GameSetting SHRINK_INTERVAL =
            new GameSetting(
                    "shrink_interval_seconds",
                    60,
                    1,
                    3_600,
                    "Seconds between the starts of consecutive shrinks");
    public static final GameSetting SHRINK_STEP =
            new GameSetting(
                    "shrink_step", 25, 1, 1_000, "Blocks each shrink takes off the side length");
    public static final GameSetting FINAL_SIZE =
            new GameSetting("final_size", 10, 2, 1_000, "Side length the last shrink ends at");
    public static final GameSetting SHRINK_DURATION =
            new GameSetting(
                    "shrink_duration_seconds",
                    10,
                    0,
                    600,
                    "Seconds each shrink takes (0 moves the border at once)");
    public static final GameSetting KIT_REROLLS =
            new GameSetting("kit_rerolls", 1, 0, 10,
                    "Times each player may reroll their random kit during the countdown (/minigames reroll)");
    public static final GameSetting ADAPTIVE_FIRST_SIZE =
            new GameSetting("adaptive_first_size", 50, 2, 1_000,
                    "meetup_adaptive_border: side length of the first shrink");
    public static final GameSetting ADAPTIVE_FIRST_PLAYERS =
            new GameSetting("adaptive_first_players", 8, 1, 1_000,
                    "meetup_adaptive_border: players left that start the first shrink");
    public static final GameSetting ADAPTIVE_FIRST_SECONDS =
            new GameSetting("adaptive_first_seconds", 600, 1, 7_200,
                    "meetup_adaptive_border: seconds that start the first shrink if the players have not");
    public static final GameSetting ADAPTIVE_SECOND_SIZE =
            new GameSetting("adaptive_second_size", 25, 2, 1_000,
                    "meetup_adaptive_border: side length of the second shrink");
    public static final GameSetting ADAPTIVE_SECOND_PLAYERS =
            new GameSetting("adaptive_second_players", 4, 1, 1_000,
                    "meetup_adaptive_border: players left that start the second shrink");
    public static final GameSetting ADAPTIVE_SECOND_SECONDS =
            new GameSetting("adaptive_second_seconds", 900, 1, 7_200,
                    "meetup_adaptive_border: seconds that start the second shrink if the players have not");
    public static final GameSetting ADAPTIVE_DAMAGE_SECONDS =
            new GameSetting("adaptive_damage_seconds", 1_500, 1, 7_200,
                    "meetup_adaptive_border: seconds after which survivors take random damage");
    /** Random damage: one heart, every 5 to 10 seconds. */
    private static final float RANDOM_DAMAGE = 2.0F;
    private static final int RANDOM_DAMAGE_MIN_TICKS = 5 * 20;
    private static final int RANDOM_DAMAGE_MAX_TICKS = 10 * 20;

    private static final Identifier KIT = BrainageMinigames.id("kits/meetup");

    private final List<GameSetting> settings;
    /** Matches started with meetup_adaptive_border on, and how far their adaptive border got. */
    private final Map<Match, Adaptive> adaptive = new HashMap<>();

    private static final class Adaptive {
        /** Shrinks started so far: 0, 1 or 2. */
        int stage;
        /** The active tick each survivor next takes random damage. */
        final Map<UUID, Integer> nextDamage = new HashMap<>();
    }

    public MeetupGame() {
        List<GameSetting> all = new ArrayList<>(GameSetting.common(10, 15, false));
        all.addAll(GameSetting.lobby(30, 8));
        all.add(AntiJanitor.SECONDS);
        all.add(UhcGame.REGION_SEED);
        all.addAll(
                List.of(
                        BORDER_START_SIZE,
                        FIRST_SHRINK_TIME,
                        SHRINK_INTERVAL,
                        SHRINK_STEP,
                        FINAL_SIZE,
                        SHRINK_DURATION,
                        KIT_REROLLS,
                        ADAPTIVE_FIRST_SIZE,
                        ADAPTIVE_FIRST_PLAYERS,
                        ADAPTIVE_FIRST_SECONDS,
                        ADAPTIVE_SECOND_SIZE,
                        ADAPTIVE_SECOND_PLAYERS,
                        ADAPTIVE_SECOND_SECONDS,
                        ADAPTIVE_DAMAGE_SECONDS));
        this.settings = List.copyOf(all);
    }

    @Override
    public String id() {
        return "meetup";
    }

    @Override
    public boolean combatLoggers() {
        return true;
    }

    @Override
    public boolean antiJanitor() {
        return true;
    }

    @Override
    public String displayName() {
        return "Meetup";
    }

    @Override
    public List<GameSetting> settings() {
        return settings;
    }

    @Override
    public Optional<String> validate(GameSettings values) {
        if (values.get(FINAL_SIZE) >= values.get(BORDER_START_SIZE)) {
            return Optional.of("final_size must be smaller than border_start_size");
        }
        if (values.get(SHRINK_DURATION) > values.get(SHRINK_INTERVAL)) {
            return Optional.of("shrink_duration_seconds must not exceed shrink_interval_seconds");
        }
        if (values.get(ADAPTIVE_SECOND_SIZE) >= values.get(ADAPTIVE_FIRST_SIZE)) {
            return Optional.of("adaptive_second_size must be smaller than adaptive_first_size");
        }
        if (values.get(ADAPTIVE_SECOND_PLAYERS) >= values.get(ADAPTIVE_FIRST_PLAYERS)) {
            return Optional.of("adaptive_second_players must be fewer than adaptive_first_players");
        }
        if (values.get(ADAPTIVE_SECOND_SECONDS) <= values.get(ADAPTIVE_FIRST_SECONDS)) {
            return Optional.of("adaptive_second_seconds must be after adaptive_first_seconds");
        }
        return Optional.empty();
    }

    @Override
    public int kitRerolls(GameSettings values) {
        return values.get(KIT_REROLLS);
    }

    /** The adaptive border replaces the time limit: its random damage ends the match instead. */
    @Override
    public boolean controlsTimeout(Match match) {
        return adaptive.containsKey(match);
    }

    @Override
    public Identifier defaultKit() {
        return KIT;
    }

    @Override
    public GameType playerGameMode() {
        return GameType.SURVIVAL;
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings values) throws MatchException {
        return NaturalArena.open(server, io.github.brainage04.brainage_minigames.dimension.ModDimensions.MEETUP,
                values.get(BORDER_START_SIZE), values);
    }

    @Override
    public boolean dropsInventoryOnElimination() {
        return true;
    }

    @Override
    public void onStart(Match match) {
        UhcRules.announcePvpEnabled(match);
        if (!match.server().getGameRules().get(UhcScenarioRules.MEETUP_ADAPTIVE_BORDER)) return;
        adaptive.put(match, new Adaptive());
        GameSettings values = match.settings();
        announce(match, "Adaptive border: it shrinks to %d blocks at %d players left or at %s, then to %d at %d left or at %s; from %s everyone takes random damage."
                .formatted(values.get(ADAPTIVE_FIRST_SIZE), values.get(ADAPTIVE_FIRST_PLAYERS),
                        clock(values.get(ADAPTIVE_FIRST_SECONDS)), values.get(ADAPTIVE_SECOND_SIZE),
                        values.get(ADAPTIVE_SECOND_PLAYERS), clock(values.get(ADAPTIVE_SECOND_SECONDS)),
                        clock(values.get(ADAPTIVE_DAMAGE_SECONDS))));
    }

    @Override
    public void onClose(Match match) {
        adaptive.remove(match);
    }

    @Override
    public void tick(Match match) {
        NaturalArena arena = (NaturalArena) match.arena();
        Adaptive state = adaptive.get(match);
        if (state != null) {
            tickAdaptive(match, arena, state);
        } else {
            tickSteps(match, arena);
        }
        arena.tick(match.onlineMembers(), match.alivePlayers());
    }

    private void tickSteps(Match match, NaturalArena arena) {
        GameSettings values = match.settings();
        int ticks = match.activeTicks();
        int first = values.get(FIRST_SHRINK_TIME) * 20;
        int interval = values.get(SHRINK_INTERVAL) * 20;
        if (ticks >= first && (ticks - first) % interval == 0) {
            int shrink = (ticks - first) / interval;
            if (shrink < shrinkCount(values)) {
                int size = sizeAfter(values, shrink);
                arena.shrink(size, values.get(SHRINK_DURATION) * 20L, match.onlineMembers());
                UhcRules.announceShrink(match, size);
            }
        }
    }

    /**
     * Badlion's Meetup border: each shrink starts at a number of players left or a time,
     * whichever comes first; after the last stage survivors take a heart of damage at random
     * moments until one team is left.
     */
    private static void tickAdaptive(Match match, NaturalArena arena, Adaptive state) {
        GameSettings values = match.settings();
        int ticks = match.activeTicks();
        GameSetting[][] stages = {
                {ADAPTIVE_FIRST_SIZE, ADAPTIVE_FIRST_PLAYERS, ADAPTIVE_FIRST_SECONDS},
                {ADAPTIVE_SECOND_SIZE, ADAPTIVE_SECOND_PLAYERS, ADAPTIVE_SECOND_SECONDS}};
        while (state.stage < stages.length) {
            GameSetting[] stage = stages[state.stage];
            boolean players = match.aliveCount() <= values.get(stage[1]);
            if (!players && ticks < values.get(stage[2]) * 20) break;
            state.stage++;
            int size = Math.min(values.get(stage[0]), arena.targetSize());
            arena.shrink(size, values.get(SHRINK_DURATION) * 20L, match.onlineMembers());
            announce(match, "The border is shrinking to %d blocks across: %s.".formatted(size,
                    players ? match.aliveCount() + " players are left" : clock(values.get(stage[2])) + " has passed"));
        }
        int damage = values.get(ADAPTIVE_DAMAGE_SECONDS) * 20;
        if (ticks == damage) announce(match, "Everyone left now takes random damage until one team remains!");
        if (ticks < damage) return;
        for (ServerPlayer player : match.alivePlayers()) {
            int next = state.nextDamage.computeIfAbsent(player.getUUID(), ignored -> ticks + delay(player));
            if (ticks >= next && player.hurtServer(player.level(), player.damageSources().magic(), RANDOM_DAMAGE)) {
                state.nextDamage.put(player.getUUID(), ticks + delay(player));
            }
        }
    }

    private static int delay(ServerPlayer player) {
        return RANDOM_DAMAGE_MIN_TICKS + player.getRandom().nextInt(RANDOM_DAMAGE_MAX_TICKS - RANDOM_DAMAGE_MIN_TICKS + 1);
    }

    private static void announce(Match match, String message) {
        match.broadcast(Component.literal(message).withStyle(ChatFormatting.YELLOW));
    }

    private static String clock(int seconds) {
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }

    @Override
    public void onRelease(Match match, ServerPlayer player) {
        NaturalArena.restoreBorder(player);
    }

    @Override
    public boolean allowBreak(Match match, ServerPlayer player, BlockPos pos, BlockState state) {
        return match.arena().canBuild(pos);
    }

    @Override
    public void addSidebarLines(Match match, List<Component> lines) {
        NaturalArena arena = (NaturalArena) match.arena();
        lines.add(UhcRules.borderLine(arena.size()));
        if (match.phase() != MatchPhase.ACTIVE) {
            return;
        }
        GameSettings values = match.settings();
        int ticks = match.activeTicks();
        Adaptive state = adaptive.get(match);
        if (arena.targetSize() != arena.size()) {
            lines.add(MatchSidebar.label("Shrinking to: ", arena.targetSize() + " blocks"));
        } else if (state != null) {
            lines.add(adaptiveLine(values, state, ticks));
        } else {
            int next = nextShrink(values, ticks);
            lines.add(
                    next < shrinkCount(values)
                            ? MatchSidebar.label(
                                    "Shrink in: ",
                                    MatchSidebar.countdown(shrinkTick(values, next) - ticks))
                            : MatchSidebar.label("Shrink: ", "final"));
        }
        lines.add(UhcRules.aliveLine(match));
    }

    /** The adaptive border's next shrink (at a player count or a time), or its random damage. */
    private static Component adaptiveLine(GameSettings values, Adaptive state, int ticks) {
        if (state.stage < 2) {
            boolean first = state.stage == 0;
            int size = values.get(first ? ADAPTIVE_FIRST_SIZE : ADAPTIVE_SECOND_SIZE);
            int players = values.get(first ? ADAPTIVE_FIRST_PLAYERS : ADAPTIVE_SECOND_PLAYERS);
            int at = values.get(first ? ADAPTIVE_FIRST_SECONDS : ADAPTIVE_SECOND_SECONDS) * 20;
            return MatchSidebar.label("To " + size + ": ",
                    players + " left or " + MatchSidebar.countdown(Math.max(0, at - ticks)));
        }
        int damage = values.get(ADAPTIVE_DAMAGE_SECONDS) * 20;
        return ticks < damage ? MatchSidebar.label("Damage in: ", MatchSidebar.countdown(damage - ticks))
                : MatchSidebar.label("Damage: ", "random");
    }

    /** Shrinks it takes to get from the start size to the final size. */
    private static int shrinkCount(GameSettings values) {
        return Math.ceilDiv(
                values.get(BORDER_START_SIZE) - values.get(FINAL_SIZE), values.get(SHRINK_STEP));
    }

    /** The active tick the given shrink (from 0) starts on. */
    private static long shrinkTick(GameSettings values, int shrink) {
        return (values.get(FIRST_SHRINK_TIME) + (long) shrink * values.get(SHRINK_INTERVAL)) * 20;
    }

    /** The first shrink that has not started by the given active tick. */
    private static int nextShrink(GameSettings values, int ticks) {
        int first = values.get(FIRST_SHRINK_TIME) * 20;
        return ticks < first ? 0 : (ticks - first) / (values.get(SHRINK_INTERVAL) * 20) + 1;
    }

    /** The side length the given shrink (from 0) ends at. */
    private static int sizeAfter(GameSettings values, int shrink) {
        return Math.max(
                values.get(FINAL_SIZE),
                values.get(BORDER_START_SIZE) - (shrink + 1) * values.get(SHRINK_STEP));
    }
}
