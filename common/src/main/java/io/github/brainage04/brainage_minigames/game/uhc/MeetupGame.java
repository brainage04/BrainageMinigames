package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.MatchSidebar;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    private static final Identifier KIT = BrainageMinigames.id("kits/meetup");

    private final List<GameSetting> settings;

    public MeetupGame() {
        List<GameSetting> all = new ArrayList<>(GameSetting.common(10, 15, false));
        all.addAll(
                List.of(
                        BORDER_START_SIZE,
                        FIRST_SHRINK_TIME,
                        SHRINK_INTERVAL,
                        SHRINK_STEP,
                        FINAL_SIZE,
                        SHRINK_DURATION));
        this.settings = List.copyOf(all);
    }

    @Override
    public String id() {
        return "meetup";
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
        return Optional.empty();
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
        return NaturalArena.open(server, values.get(BORDER_START_SIZE));
    }

    @Override
    public boolean dropsInventoryOnElimination() {
        return true;
    }

    @Override
    public void tick(Match match) {
        GameSettings values = match.settings();
        NaturalArena arena = (NaturalArena) match.arena();
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
        arena.tick(match.onlineMembers(), match.alivePlayers());
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
        if (arena.targetSize() != arena.size()) {
            lines.add(MatchSidebar.label("Shrinking to: ", arena.targetSize() + " blocks"));
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
