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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

/**
 * Survival UHC in a fresh region of the UHC dimension: players gather their own gear during a grace
 * period, then the world border closes in until one team is left. Nether portals lead to the UHC
 * nether until it closes. The defaults are a one-hour game.
 */
public final class UhcGame implements Minigame {
    public static final GameSetting GRACE_PERIOD =
            new GameSetting(
                    "grace_period_minutes",
                    10,
                    0,
                    600,
                    "Minutes before players can damage each other");
    public static final GameSetting BORDER_START_SIZE =
            new GameSetting(
                    "border_start_size",
                    1_000,
                    32,
                    20_000,
                    "Side length of the world border when the match starts");
    public static final GameSetting FIRST_SHRINK_TIME =
            new GameSetting(
                    "first_shrink_minutes",
                    30,
                    1,
                    600,
                    "Minutes before the border starts its first shrink");
    public static final GameSetting FIRST_SHRINK_SIZE =
            new GameSetting(
                    "first_shrink_size", 100, 8, 20_000, "Side length the first shrink ends at");
    public static final GameSetting FINAL_SHRINK_TIME =
            new GameSetting(
                    "final_shrink_minutes",
                    40,
                    1,
                    600,
                    "Minutes before everyone is moved to the surface and the final shrink starts");
    public static final GameSetting FINAL_SHRINK_SIZE =
            new GameSetting(
                    "final_shrink_size", 20, 2, 20_000, "Side length the final shrink ends at");
    public static final GameSetting SHRINK_DURATION =
            new GameSetting("shrink_duration_minutes", 10, 1, 120, "Minutes each shrink takes");
    public static final GameSetting NETHER_CLOSE_TIME =
            new GameSetting(
                    "nether_close_minutes",
                    FIRST_SHRINK_TIME.defaultValue(),
                    0,
                    600,
                    "Minutes before portals stop leading to the nether and everyone in it is moved to the surface (0 disables the nether)");

    private static final Identifier STARTER_KIT = BrainageMinigames.id("kits/uhc_starter");
    private static final int FIRE_RESISTANCE_TICKS = 10 * 60 * 20;
    private static final int LAST_MINUTES_WARNING = 10;

    /** Minutes before a shrink or the nether closing that players are warned. */
    private static final int[] WARNING_MINUTES = {5, 1};

    private final List<GameSetting> settings;

    public UhcGame() {
        List<GameSetting> all = new ArrayList<>(GameSetting.common(10, 60, false));
        all.addAll(
                List.of(
                        GRACE_PERIOD,
                        BORDER_START_SIZE,
                        FIRST_SHRINK_TIME,
                        FIRST_SHRINK_SIZE,
                        FINAL_SHRINK_TIME,
                        FINAL_SHRINK_SIZE,
                        SHRINK_DURATION,
                        NETHER_CLOSE_TIME));
        this.settings = List.copyOf(all);
    }

    @Override
    public String id() {
        return "uhc";
    }

    @Override
    public String displayName() {
        return "UHC";
    }

    @Override
    public List<GameSetting> settings() {
        return settings;
    }

    @Override
    public Optional<String> validate(GameSettings values) {
        int timeLimit = values.get(GameSetting.TIME_LIMIT_MINUTES);
        if (timeLimit > 0 && values.get(FINAL_SHRINK_TIME) >= timeLimit) {
            return Optional.of("final_shrink_minutes must be before time_limit_minutes");
        }
        if (values.get(FIRST_SHRINK_TIME) >= values.get(FINAL_SHRINK_TIME)) {
            return Optional.of("first_shrink_minutes must be before final_shrink_minutes");
        }
        if (values.get(FIRST_SHRINK_SIZE) >= values.get(BORDER_START_SIZE)) {
            return Optional.of("first_shrink_size must be smaller than border_start_size");
        }
        if (values.get(FINAL_SHRINK_SIZE) >= values.get(FIRST_SHRINK_SIZE)) {
            return Optional.of("final_shrink_size must be smaller than first_shrink_size");
        }
        if (values.get(NETHER_CLOSE_TIME) > values.get(FINAL_SHRINK_TIME)) {
            return Optional.of("nether_close_minutes must not be after final_shrink_minutes");
        }
        return Optional.empty();
    }

    @Override
    public Identifier defaultKit() {
        return STARTER_KIT;
    }

    @Override
    public GameType playerGameMode() {
        return GameType.SURVIVAL;
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings values) throws MatchException {
        return UhcArena.open(
                server, values.get(BORDER_START_SIZE), values.get(NETHER_CLOSE_TIME) > 0);
    }

    @Override
    public boolean dropsInventoryOnElimination() {
        return true;
    }

    @Override
    public void onStart(Match match) {
        for (ServerPlayer player : match.alivePlayers()) {
            player.addEffect(
                    new MobEffectInstance(
                            MobEffects.FIRE_RESISTANCE, FIRE_RESISTANCE_TICKS, 0, false, true));
        }
        GameSettings values = match.settings();
        int grace = values.get(GRACE_PERIOD);
        if (grace > 0) {
            match.broadcast(
                    Component.literal("PvP is enabled in %d minutes.".formatted(grace))
                            .withStyle(ChatFormatting.YELLOW));
        }
        match.broadcast(
                Component.literal(
                                "The border starts shrinking in %s."
                                        .formatted(minutes(values.get(FIRST_SHRINK_TIME))))
                        .withStyle(ChatFormatting.YELLOW));
        int netherClose = values.get(NETHER_CLOSE_TIME);
        if (netherClose == 0) {
            match.broadcast(
                    Component.literal("The nether is disabled in this match.")
                            .withStyle(ChatFormatting.YELLOW));
        } else if (((UhcArena) match.arena()).openNether().isPresent()) {
            match.broadcast(
                    Component.literal("The nether closes in %s.".formatted(minutes(netherClose)))
                            .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static String minutes(int minutes) {
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }

    @Override
    public void tick(Match match) {
        GameSettings values = match.settings();
        UhcArena arena = (UhcArena) match.arena();
        int ticks = match.activeTicks();
        long shrinkTicks = values.minutesInTicks(SHRINK_DURATION);

        if (values.get(GRACE_PERIOD) > 0 && ticks == values.minutesInTicks(GRACE_PERIOD)) {
            match.broadcast(
                    Component.literal("PvP is now enabled!")
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        }
        for (int minutes : WARNING_MINUTES) {
            if (ticks == values.minutesInTicks(FIRST_SHRINK_TIME) - minutes * 60 * 20) {
                match.broadcast(
                        Component.literal(
                                        "The border starts shrinking in %s."
                                                .formatted(minutes(minutes)))
                                .withStyle(ChatFormatting.YELLOW));
            }
            if (ticks == values.minutesInTicks(FINAL_SHRINK_TIME) - minutes * 60 * 20) {
                match.broadcast(
                        Component.literal(
                                        "The final shrink starts in %s; everyone will be moved to the surface."
                                                .formatted(minutes(minutes)))
                                .withStyle(ChatFormatting.GOLD));
            }
        }
        tickNether(match, arena, ticks);
        if (ticks == values.minutesInTicks(FIRST_SHRINK_TIME)) {
            arena.shrinkBorder(values.get(FIRST_SHRINK_SIZE), shrinkTicks);
            UhcRules.announceShrink(match, values.get(FIRST_SHRINK_SIZE));
        }
        if (ticks == values.minutesInTicks(FINAL_SHRINK_TIME)) {
            match.alivePlayers().forEach(arena::moveToSurface);
            arena.shrinkBorder(values.get(FINAL_SHRINK_SIZE), shrinkTicks);
            match.broadcast(
                    Component.literal(
                                    "Final phase: everyone is on the surface and the border is shrinking to %d blocks across!"
                                            .formatted(values.get(FINAL_SHRINK_SIZE)))
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        int timeLimit = values.get(GameSetting.TIME_LIMIT_MINUTES);
        int lastMinutes = timeLimit - LAST_MINUTES_WARNING;
        if (lastMinutes > values.get(FINAL_SHRINK_TIME) && ticks == lastMinutes * 60 * 20) {
            match.alivePlayers().forEach(arena::moveToSurface);
            match.broadcast(
                    Component.literal(
                                    "%d minutes remain; everyone has been moved to the surface."
                                            .formatted(LAST_MINUTES_WARNING))
                            .withStyle(ChatFormatting.GOLD));
        }
    }

    /** Warns a minute before the nether closes, then closes it and brings everyone in it back. */
    private static void tickNether(Match match, UhcArena arena, int ticks) {
        int closeTicks = match.settings().minutesInTicks(NETHER_CLOSE_TIME);
        if (closeTicks == 0 || arena.openNether().isEmpty()) {
            return;
        }
        if (ticks == closeTicks - 60 * 20) {
            match.broadcast(
                    Component.literal(
                                    "The nether closes in 1 minute. Anyone still in it will be moved to the surface.")
                            .withStyle(ChatFormatting.GOLD));
        }
        if (ticks == closeTicks) {
            arena.closeNether(match.alivePlayers());
            match.broadcast(
                    Component.literal(
                                    "The nether has closed; everyone still in it was moved to the surface.")
                            .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public void addSidebarLines(Match match, List<Component> lines) {
        if (match.phase() != MatchPhase.ACTIVE) {
            return;
        }
        GameSettings values = match.settings();
        int ticks = match.activeTicks();
        int grace = values.minutesInTicks(GRACE_PERIOD);
        lines.add(
                ticks < grace
                        ? MatchSidebar.label("PvP in: ", MatchSidebar.countdown(grace - ticks))
                        : MatchSidebar.label("PvP: ", "enabled"));
        lines.add(UhcRules.borderLine(match.arena().level().getWorldBorder().getSize()));
        int firstShrink = values.minutesInTicks(FIRST_SHRINK_TIME);
        int finalShrink = values.minutesInTicks(FINAL_SHRINK_TIME);
        if (ticks < firstShrink) {
            lines.add(
                    MatchSidebar.label("Shrink in: ", MatchSidebar.countdown(firstShrink - ticks)));
        } else if (ticks < finalShrink) {
            lines.add(
                    MatchSidebar.label(
                            "Final shrink in: ", MatchSidebar.countdown(finalShrink - ticks)));
        } else {
            lines.add(MatchSidebar.label("Shrink: ", "final"));
        }
        int netherClose = values.minutesInTicks(NETHER_CLOSE_TIME);
        if (netherClose > 0 && ((UhcArena) match.arena()).hasNether()) {
            lines.add(
                    ticks < netherClose
                            ? MatchSidebar.label(
                                    "Nether closes in: ",
                                    MatchSidebar.countdown(netherClose - ticks))
                            : MatchSidebar.label("Nether: ", "closed"));
        }
        lines.add(UhcRules.aliveLine(match));
    }

    @Override
    public boolean allowDamage(Match match, ServerPlayer victim, DamageSource source) {
        return !(source.getEntity() instanceof ServerPlayer)
                || match.activeTicks() >= match.settings().minutesInTicks(GRACE_PERIOD);
    }
}
