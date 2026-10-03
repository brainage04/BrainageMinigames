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
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;

/** Survival UHC, configurable continuous/instant border modes, and an optional arena finale. */
public final class UhcGame implements Minigame {
    private static final AttributeModifier DOUBLE_HEALTH =
            new AttributeModifier(BrainageMinigames.id("uhc_double_health"), 20,
                    AttributeModifier.Operation.ADD_VALUE);
    public static final GameSetting GRACE_PERIOD = minutes("grace_period_minutes", 10, 0,
            "Minutes before players can damage each other");
    public static final GameSetting BORDER_START_SIZE = size("border_start_size", 1000,
            "Starting border width");
    public static final GameSetting FIRST_SHRINK_TIME = minutes("first_shrink_minutes", 20, 1,
            "Hypixel shrink start / first Badlion instant shrink");
    public static final GameSetting FIRST_SHRINK_SIZE = size("first_shrink_size", 750,
            "First Badlion border width");
    public static final GameSetting SECOND_SHRINK_TIME = minutes("second_shrink_minutes", 25, 1,
            "Second Badlion instant shrink");
    public static final GameSetting SECOND_SHRINK_SIZE = size("second_shrink_size", 500,
            "Second Badlion border width");
    public static final GameSetting THIRD_SHRINK_TIME = minutes("third_shrink_minutes", 30, 1,
            "Third Badlion instant shrink");
    public static final GameSetting THIRD_SHRINK_SIZE = size("third_shrink_size", 250,
            "Third Badlion border width");
    public static final GameSetting FINAL_SHRINK_TIME = minutes("final_shrink_minutes", 35, 1,
            "Hypixel shrink completion / final Badlion instant shrink");
    public static final GameSetting FINAL_SHRINK_SIZE = size("final_shrink_size", 100,
            "Final survival border width for both styles");
    public static final GameSetting NETHER_CLOSE_TIME = minutes("nether_close_minutes", 20, 0,
            "Nether closes and players return to the surface (0 disables the nether)");
    public static final GameSetting DEATHMATCH_ENABLED = new GameSetting("deathmatch_enabled", 1, 0, 1,
            "Use deathmatch when the uhc_deathmatch gamerule is on");
    public static final GameSetting DEATHMATCH_TIME = minutes("deathmatch_minutes", 40, 1,
            "Elapsed match minutes before teleporting into deathmatch");
    public static final GameSetting DEATHMATCH_DURATION = minutes("deathmatch_duration_minutes", 10, 1,
            "Deathmatch duration, including its frozen countdown; survivors draw at expiry");
    public static final GameSetting DEATHMATCH_SHRINK_TIME = minutes("deathmatch_shrink_minutes", 5, 1,
            "Minutes into deathmatch before its border shrinks to half width");
    public static final GameSetting DEATHMATCH_SHRINK_SECONDS = new GameSetting(
            "deathmatch_shrink_seconds", 60, 1, 600, "Seconds for the deathmatch border shrink");
    public static final int DEATHMATCH_FREEZE_TICKS = 10 * 20;
    private static final GameSetting[] SHRINK_TIMES = {
            FIRST_SHRINK_TIME, SECOND_SHRINK_TIME, THIRD_SHRINK_TIME, FINAL_SHRINK_TIME};
    private static final GameSetting[] SHRINK_SIZES = {
            FIRST_SHRINK_SIZE, SECOND_SHRINK_SIZE, THIRD_SHRINK_SIZE, FINAL_SHRINK_SIZE};
    private static final int[] WARNING_SECONDS = {300, 60, 30, 10, 5, 4, 3, 2, 1};
    private static final Identifier STARTER_KIT = BrainageMinigames.id("kits/uhc_starter");
    private final List<GameSetting> settings;

    private static GameSetting minutes(String key, int value, int minimum, String description) {
        return new GameSetting(key, value, minimum, 600, description);
    }

    private static GameSetting size(String key, int value, String description) {
        return new GameSetting(key, value, 16, 20000, description);
    }

    public UhcGame() {
        List<GameSetting> all = new ArrayList<>(GameSetting.common(10, 50, false));
        all.add(AntiJanitor.SECONDS);
        all.addAll(List.of(GRACE_PERIOD, BORDER_START_SIZE, FIRST_SHRINK_TIME, FIRST_SHRINK_SIZE,
                SECOND_SHRINK_TIME, SECOND_SHRINK_SIZE, THIRD_SHRINK_TIME, THIRD_SHRINK_SIZE,
                FINAL_SHRINK_TIME, FINAL_SHRINK_SIZE, NETHER_CLOSE_TIME, DEATHMATCH_ENABLED,
                DEATHMATCH_TIME, DEATHMATCH_DURATION, DEATHMATCH_SHRINK_TIME, DEATHMATCH_SHRINK_SECONDS));
        settings = List.copyOf(all);
    }

    @Override public String id() { return "uhc"; }
    @Override public String displayName() { return "UHC"; }
    @Override public List<GameSetting> settings() { return settings; }
    @Override public Identifier defaultKit() { return STARTER_KIT; }
    @Override public GameType playerGameMode() { return GameType.SURVIVAL; }
    @Override public boolean dropsInventoryOnElimination() { return true; }

    @Override
    public Optional<String> validate(GameSettings values) {
        int previousTime = 0;
        int previousSize = values.get(BORDER_START_SIZE);
        for (int i = 0; i < SHRINK_TIMES.length; i++) {
            if (values.get(SHRINK_TIMES[i]) <= previousTime) {
                return Optional.of(SHRINK_TIMES[i].key() + " must be after the previous shrink");
            }
            if (values.get(SHRINK_SIZES[i]) >= previousSize) {
                return Optional.of(SHRINK_SIZES[i].key() + " must be smaller than the previous border");
            }
            previousTime = values.get(SHRINK_TIMES[i]);
            previousSize = values.get(SHRINK_SIZES[i]);
        }
        if (values.get(NETHER_CLOSE_TIME) > values.get(FINAL_SHRINK_TIME)) {
            return Optional.of("nether_close_minutes must not be after final_shrink_minutes");
        }
        if (values.get(DEATHMATCH_TIME) <= values.get(FINAL_SHRINK_TIME)) {
            return Optional.of("deathmatch_minutes must be after final_shrink_minutes");
        }
        if (values.get(DEATHMATCH_SHRINK_TIME) * 60 + values.get(DEATHMATCH_SHRINK_SECONDS)
                >= values.get(DEATHMATCH_DURATION) * 60) {
            return Optional.of("The deathmatch shrink must finish before deathmatch ends");
        }
        int limit = values.get(GameSetting.TIME_LIMIT_MINUTES);
        if (limit > 0 && limit <= values.get(FINAL_SHRINK_TIME)) {
            return Optional.of("time_limit_minutes must be after final_shrink_minutes");
        }
        return Optional.empty();
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings values) throws MatchException {
        boolean deathmatch = values.get(DEATHMATCH_ENABLED) != 0
                && server.getGameRules().get(UhcModeRules.DEATHMATCH);
        int limit = values.get(GameSetting.TIME_LIMIT_MINUTES);
        if (deathmatch && limit > 0
                && limit < values.get(DEATHMATCH_TIME) + values.get(DEATHMATCH_DURATION)) {
            throw new MatchException("time_limit_minutes must allow the complete deathmatch (or be 0)");
        }
        UhcArena arena = UhcArena.open(server, values.get(BORDER_START_SIZE), values.get(NETHER_CLOSE_TIME) > 0);
        if (deathmatch) {
            try {
                arena.prepareDeathmatch();
            } catch (MatchException | RuntimeException exception) {
                arena.close();
                throw exception;
            }
        }
        return arena;
    }

    @Override
    public void onStart(Match match) {
        boolean doubleHealth = match.server().getGameRules().get(UhcModeRules.DOUBLE_HEALTH);
        for (ServerPlayer player : match.alivePlayers()) {
            if (doubleHealth) {
                AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
                if (health != null) {
                    health.addOrUpdateTransientModifier(DOUBLE_HEALTH);
                    player.setHealth(player.getMaxHealth());
                }
            }
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 10 * 60 * 20, 0, false, false, true));
        }
        GameSettings values = match.settings();
        UhcArena arena = (UhcArena) match.arena();
        if (values.get(GRACE_PERIOD) > 0) {
            announce(match, "PvP is enabled in " + duration(values.get(GRACE_PERIOD) * 60) + ".");
        }
        if (arena.badlion()) {
            for (int i = 0; i < SHRINK_TIMES.length; i++) {
                announce(match, "The border shrinks instantly to " + values.get(SHRINK_SIZES[i])
                        + " blocks across at " + values.get(SHRINK_TIMES[i]) + ":00.");
            }
        } else {
            announce(match, "The border starts shrinking in " + duration(values.get(FIRST_SHRINK_TIME) * 60)
                    + "; it reaches " + values.get(FINAL_SHRINK_SIZE) + " blocks across at "
                    + values.get(FINAL_SHRINK_TIME) + ":00.");
        }
        announce(match, values.get(NETHER_CLOSE_TIME) == 0 ? "The nether is disabled in this match."
                : "The nether closes in " + duration(values.get(NETHER_CLOSE_TIME) * 60) + ".");
        announce(match, arena.deathmatchEnabled()
                ? "Deathmatch starts at " + values.get(DEATHMATCH_TIME) + ":00 and lasts "
                        + duration(values.get(DEATHMATCH_DURATION) * 60) + "."
                : "Deathmatch is disabled in this match.");
    }

    @Override
    public void onRelease(Match match, ServerPlayer player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.removeModifier(DOUBLE_HEALTH.id());
            player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
        }
    }

    private static void announce(Match match, String message) {
        match.broadcast(Component.literal(message).withStyle(ChatFormatting.YELLOW));
    }

    private static String duration(int seconds) {
        return seconds >= 60 ? (seconds / 60) + (seconds == 60 ? " minute" : " minutes")
                : seconds + (seconds == 1 ? " second" : " seconds");
    }

    @Override
    public void tick(Match match) {
        GameSettings values = match.settings();
        UhcArena arena = (UhcArena) match.arena();
        int ticks = match.activeTicks();
        if (values.get(GRACE_PERIOD) > 0 && ticks == values.minutesInTicks(GRACE_PERIOD)) {
            announce(match, "PvP is now enabled!");
        }
        if (arena.inDeathmatch()) {
            tickDeathmatch(match, arena, ticks - values.minutesInTicks(DEATHMATCH_TIME));
            return;
        }
        tickNether(match, arena, ticks);
        int stages = arena.badlion() ? SHRINK_TIMES.length : 1;
        for (int i = 0; i < stages; i++) {
            int at = values.minutesInTicks(SHRINK_TIMES[i]);
            int target = values.get(arena.badlion() ? SHRINK_SIZES[i] : FINAL_SHRINK_SIZE);
            for (int seconds : WARNING_SECONDS) {
                if (ticks == at - seconds * 20 && ticks > 0) {
                    announce(match, arena.badlion()
                            ? "The border shrinks instantly to " + target + " blocks across in " + duration(seconds)
                                    + "; players outside will be teleported to the surface inside it."
                            : "The border starts shrinking in " + duration(seconds) + ".");
                }
            }
            if (ticks == at) {
                if (arena.badlion()) {
                    arena.instantShrink(target, match.alivePlayers());
                    announce(match, "The border has shrunk to " + target + " blocks across; players outside were moved to the surface.");
                } else {
                    arena.shrinkBorder(target, values.minutesInTicks(FINAL_SHRINK_TIME) - at);
                    UhcRules.announceShrink(match, target);
                }
            }
        }
        if (arena.deathmatchEnabled()) {
            int at = values.minutesInTicks(DEATHMATCH_TIME);
            for (int seconds : WARNING_SECONDS) {
                if (ticks == at - seconds * 20 && ticks > 0) {
                    announce(match, "Deathmatch starts in " + duration(seconds) + ".");
                }
            }
            if (ticks == at) {
                arena.startDeathmatch(match, DEATHMATCH_FREEZE_TICKS);
                announce(match, "Deathmatch! Movement is frozen for 10 seconds; then fight for the middle chests.");
            }
        }
    }

    private static void tickDeathmatch(Match match, UhcArena arena, int elapsed) {
        GameSettings values = match.settings();
        if (elapsed < DEATHMATCH_FREEZE_TICKS) {
            arena.holdDeathmatchSpawns(match);
            if (elapsed % 20 == 0) {
                match.broadcastActionBar(Component.literal("Deathmatch begins in "
                        + (DEATHMATCH_FREEZE_TICKS - elapsed) / 20 + "...").withStyle(ChatFormatting.GOLD));
            }
        } else if (arena.deathmatchFrozen()) {
            arena.releaseDeathmatch(match);
            announce(match, "Go!");
        }
        int shrink = values.minutesInTicks(DEATHMATCH_SHRINK_TIME);
        if (elapsed == shrink - 60 * 20) {
            announce(match, "The deathmatch border shrinks to half width in 1 minute.");
        }
        if (elapsed == shrink) {
            arena.shrinkBorder(arena.level().getWorldBorder().getSize() / 2,
                    values.get(DEATHMATCH_SHRINK_SECONDS) * 20L);
            announce(match, "The deathmatch border is shrinking to half width!");
        }
        if (elapsed >= values.minutesInTicks(DEATHMATCH_DURATION)) {
            match.finish(match.standingTeams());
        }
    }

    private static void tickNether(Match match, UhcArena arena, int ticks) {
        int close = match.settings().minutesInTicks(NETHER_CLOSE_TIME);
        if (close == 0 || arena.openNether().isEmpty()) { return; }
        if (ticks == close - 60 * 20) {
            announce(match, "The nether closes in 1 minute. Anyone still in it will be moved to the surface.");
        }
        if (ticks == close) {
            arena.closeNether(match.alivePlayers());
            announce(match, "The nether has closed; everyone still in it was moved to the surface.");
        }
    }

    @Override
    public void addSidebarLines(Match match, List<Component> lines) {
        if (match.phase() != MatchPhase.ACTIVE) { return; }
        GameSettings values = match.settings();
        UhcArena arena = (UhcArena) match.arena();
        int ticks = match.activeTicks();
        int grace = values.minutesInTicks(GRACE_PERIOD);
        lines.add(ticks < grace ? MatchSidebar.label("PvP in: ", MatchSidebar.countdown(grace - ticks))
                : MatchSidebar.label("PvP: ", "enabled"));
        lines.add(UhcRules.borderLine(arena.level().getWorldBorder().getSize()));
        if (arena.inDeathmatch()) {
            int elapsed = ticks - values.minutesInTicks(DEATHMATCH_TIME);
            lines.add(MatchSidebar.label("Deathmatch ends in: ",
                    MatchSidebar.countdown(values.minutesInTicks(DEATHMATCH_DURATION) - elapsed)));
            if (elapsed < DEATHMATCH_FREEZE_TICKS) {
                lines.add(MatchSidebar.label("Fight in: ", MatchSidebar.countdown(DEATHMATCH_FREEZE_TICKS - elapsed)));
            } else if (elapsed < values.minutesInTicks(DEATHMATCH_SHRINK_TIME)) {
                lines.add(MatchSidebar.label("Shrink in: ",
                        MatchSidebar.countdown(values.minutesInTicks(DEATHMATCH_SHRINK_TIME) - elapsed)));
            }
        } else {
            int next = nextShrinkTicks(values, arena.badlion(), ticks);
            lines.add(next > ticks ? MatchSidebar.label("Shrink in: ", MatchSidebar.countdown(next - ticks))
                    : MatchSidebar.label("Border: ", ticks < values.minutesInTicks(FINAL_SHRINK_TIME) ? "shrinking" : "final"));
            if (arena.deathmatchEnabled()) {
                lines.add(MatchSidebar.label("Deathmatch in: ",
                        MatchSidebar.countdown(values.minutesInTicks(DEATHMATCH_TIME) - ticks)));
            }
            if (values.get(NETHER_CLOSE_TIME) > 0 && arena.hasNether()) {
                int close = values.minutesInTicks(NETHER_CLOSE_TIME);
                lines.add(ticks < close ? MatchSidebar.label("Nether closes in: ", MatchSidebar.countdown(close - ticks))
                        : MatchSidebar.label("Nether: ", "closed"));
            }
        }
        lines.add(UhcRules.aliveLine(match));
    }

    private static int nextShrinkTicks(GameSettings values, boolean badlion, int ticks) {
        for (int i = 0; i < (badlion ? SHRINK_TIMES.length : 1); i++) {
            int at = values.minutesInTicks(SHRINK_TIMES[i]);
            if (at > ticks) { return at; }
        }
        return 0;
    }

    @Override
    public boolean allowDamage(Match match, ServerPlayer victim, DamageSource source) {
        if (((UhcArena) match.arena()).deathmatchFrozen()) { return false; }
        return !(source.getEntity() instanceof ServerPlayer)
                || match.activeTicks() >= match.settings().minutesInTicks(GRACE_PERIOD);
    }

    @Override
    public boolean allowBreak(Match match, ServerPlayer player, BlockPos pos, BlockState state) {
        return !((UhcArena) match.arena()).deathmatchFrozen() && match.arena().canBuild(pos);
    }
}
