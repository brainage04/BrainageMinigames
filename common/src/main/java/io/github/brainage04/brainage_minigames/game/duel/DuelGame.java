package io.github.brainage04.brainage_minigames.game.duel;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchSidebar;
import io.github.brainage04.brainage_minigames.game.MatchTeam;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.BoxArena;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;

/**
 * A kit fight in a {@link BoxArena}: every team gets the same kit and the last team standing wins.
 * The mechanic adds whatever makes a particular duel mode different.
 */
public final class DuelGame implements Minigame {
    public static final GameSetting HITS_TO_WIN =
            new GameSetting("hits_to_win", 100, 1, 10_000, "Hits a team must land to win");
    public static final GameSetting HIT_DELAY =
            new GameSetting(
                    "hit_delay_ticks",
                    2,
                    1,
                    10,
                    "Ticks between hits a player can take (vanilla is 10)");

    /** Vanilla sets a player's invulnerableTime to this on every hit that lands in full. */
    private static final int HIT_IMMUNITY = 20;

    /**
     * Damage immunity lasts while invulnerableTime is above this value, so vanilla allows one hit
     * every {@code HIT_IMMUNITY - IMMUNITY_THRESHOLD} ticks.
     */
    private static final int IMMUNITY_THRESHOLD = 10;

    /**
     * Makes the attack cooldown recharge within half a tick, so every Combo attack is at full
     * strength.
     */
    private static final AttributeModifier NO_ATTACK_COOLDOWN =
            new AttributeModifier(
                    BrainageMinigames.id("no_attack_cooldown"),
                    1_000.0,
                    AttributeModifier.Operation.ADD_VALUE);

    public enum Mechanic {
        /** Players cannot place or break blocks. */
        STANDARD,
        /**
         * Players play in survival mode, so they can build; the bedrock floor and barrier walls
         * keep them inside.
         */
        BUILD,
        /** Only projectiles do damage. */
        BOW,
        /** Nobody takes damage; the first team to land {@link #HITS_TO_WIN} hits wins. */
        BOXING,
        /**
         * Hits land every {@link #HIT_DELAY} ticks instead of every 10 and attacks have no
         * cooldown, so combos are possible.
         */
        COMBO
    }

    private final String id;
    private final String displayName;
    private final Identifier kit;
    private final int arenaSize;
    private final Block surface;
    private final Mechanic mechanic;
    private final List<GameSetting> settings;

    public DuelGame(
            String id,
            String displayName,
            Identifier kit,
            int arenaSize,
            Block surface,
            boolean naturalRegeneration,
            Mechanic mechanic) {
        this.id = id;
        this.displayName = displayName;
        this.kit = kit;
        this.arenaSize = arenaSize;
        this.surface = surface;
        this.mechanic = mechanic;
        List<GameSetting> all = new ArrayList<>(GameSetting.common(5, 10, naturalRegeneration));
        switch (mechanic) {
            case BOXING -> all.add(HITS_TO_WIN);
            case COMBO -> all.add(HIT_DELAY);
            default -> {}
        }
        this.settings = List.copyOf(all);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public List<GameSetting> settings() {
        return settings;
    }

    @Override
    public Identifier defaultKit() {
        return kit;
    }

    public Mechanic mechanic() {
        return mechanic;
    }

    @Override
    public GameType playerGameMode() {
        return mechanic == Mechanic.BUILD ? GameType.SURVIVAL : GameType.ADVENTURE;
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings values) throws MatchException {
        ServerLevel level = server.getLevel(ModDimensions.MINIGAMES);
        if (level == null) {
            throw new MatchException("The minigames dimension is unavailable.");
        }
        return BoxArena.open(level, arenaSize, surface.defaultBlockState());
    }

    @Override
    public void onStart(Match match) {
        if (mechanic == Mechanic.BOXING) {
            for (ServerPlayer player : match.alivePlayers()) {
                player.addEffect(
                        new MobEffectInstance(
                                MobEffects.SPEED,
                                MobEffectInstance.INFINITE_DURATION,
                                1,
                                false,
                                false));
                // Resistance V cancels all damage but keeps knockback, so hits still land.
                player.addEffect(
                        new MobEffectInstance(
                                MobEffects.RESISTANCE,
                                MobEffectInstance.INFINITE_DURATION,
                                4,
                                false,
                                false));
            }
            showScores(match);
        } else if (mechanic == Mechanic.COMBO) {
            for (ServerPlayer player : match.alivePlayers()) {
                AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
                if (attackSpeed != null) {
                    attackSpeed.addOrUpdateTransientModifier(NO_ATTACK_COOLDOWN);
                }
            }
        }
    }

    @Override
    public void onRelease(Match match, ServerPlayer player) {
        if (mechanic == Mechanic.COMBO) {
            AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
            if (attackSpeed != null) {
                attackSpeed.removeModifier(NO_ATTACK_COOLDOWN.id());
            }
        }
    }

    @Override
    public void addSidebarLines(Match match, List<Component> lines) {
        switch (mechanic) {
            case BOXING ->
                    lines.add(
                            MatchSidebar.label(
                                    "First to: ", match.settings().get(HITS_TO_WIN) + " hits"));
            case COMBO ->
                    lines.add(
                            MatchSidebar.label(
                                    "Hit delay: ", match.settings().get(HIT_DELAY) + " ticks"));
            default -> {}
        }
    }

    @Override
    public Component sidebarTeamSuffix(Match match, MatchTeam team) {
        return mechanic == Mechanic.BOXING
                ? Component.literal(" " + team.score()).withStyle(ChatFormatting.YELLOW)
                : Component.empty();
    }

    @Override
    public void tick(Match match) {
        if (mechanic == Mechanic.BOXING) {
            int target = match.settings().get(HITS_TO_WIN);
            match.teams().stream()
                    .filter(team -> team.score() >= target)
                    .findFirst()
                    .ifPresent(team -> match.finish(List.of(team)));
        }
    }

    @Override
    public boolean allowDamage(Match match, ServerPlayer victim, DamageSource source) {
        return switch (mechanic) {
            case BOW -> !(source.getDirectEntity() instanceof Player);
            case BOXING -> {
                // Only hits that get past damage immunity count, as they are the only ones that
                // knock back.
                if (source.getDirectEntity() instanceof ServerPlayer attacker
                        && victim.invulnerableTime <= IMMUNITY_THRESHOLD) {
                    Optional<MatchTeam> team = match.teamOf(attacker.getUUID());
                    if (team.isPresent()
                            && match.teamOf(victim.getUUID()).orElse(null) != team.get()) {
                        team.get().addScore(1);
                        showScores(match);
                    }
                }
                yield true;
            }
            case COMBO -> {
                // invulnerableTime counts down once per tick from the last full hit, so this is the
                // ticks since that hit wherever in the tick either hit landed. Ending the immunity
                // here, as the next hit arrives, keeps the delay exact.
                int sinceLastHit = HIT_IMMUNITY - victim.invulnerableTime;
                if (victim.invulnerableTime > IMMUNITY_THRESHOLD
                        && sinceLastHit >= match.settings().get(HIT_DELAY)) {
                    victim.invulnerableTime = 0;
                }
                yield true;
            }
            default -> true;
        };
    }

    private static void showScores(Match match) {
        MutableComponent scores = Component.empty();
        for (MatchTeam team : match.teams()) {
            if (!scores.getSiblings().isEmpty()) {
                scores.append(Component.literal("  |  ").withStyle(ChatFormatting.GRAY));
            }
            scores.append(team.displayName()).append(" " + team.score());
        }
        match.broadcastActionBar(scores);
    }
}
