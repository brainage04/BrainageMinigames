package io.github.brainage04.brainage_minigames.game;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.uhc.UhcProgression;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.flag.FeatureFlagSet;
import org.jspecify.annotations.Nullable;

/**
 * Exclusive team-against-team fights and physical, temporarily private death loot (see {@link
 * DeathLoot}). A team is locked with the opposing team of its last damaging exchange: members of
 * either team may keep fighting each other, but no third team can hit them or be hit by them until
 * the lock expires. In a free-for-all every player is their own team.
 */
public final class AntiJanitor {
    public static final GameRule<Boolean> ENABLED = new GameRule<>(
            GameRuleCategory.PLAYER, GameRuleType.BOOL, BoolArgumentType.bool(),
            GameRuleTypeVisitor::visitBoolean, Codec.BOOL, value -> value ? 1 : 0,
            true, FeatureFlagSet.of());
    public static final GameSetting SECONDS = new GameSetting(
            "anti_janitor_seconds", 30, 1, 3600,
            "Seconds of exclusive combat and death-chest protection after the last damaging hit");

    private final Match match;
    private final Map<MatchTeam, Duel> duels = new HashMap<>();

    AntiJanitor(Match match) {
        this.match = match;
    }

    public static void register(BiConsumer<Identifier, GameRule<?>> registry) {
        registry.accept(BrainageMinigames.id("anti_janitor"), ENABLED);
    }

    private boolean enabled() {
        return match.game().antiJanitor() && !match.isPrivate()
                && match.contestingTeams() > 2
                && match.server().getGameRules().get(ENABLED);
    }

    boolean allows(ServerPlayer first, ServerPlayer second) {
        if (!enabled() || first == second) return true;
        MatchTeam firstTeam = match.teamOf(first.getUUID()).orElse(null);
        MatchTeam secondTeam = match.teamOf(second.getUUID()).orElse(null);
        if (firstTeam == secondTeam) return false;
        Duel a = active(firstTeam);
        Duel b = active(secondTeam);
        return (a == null && b == null) || (a != null && a == b);
    }

    /** Called only after health or absorption was actually lost, before lethal damage eliminates. */
    void damaged(ServerPlayer victim, ServerPlayer attacker) {
        if (!enabled() || victim == attacker || !match.isActiveParticipant(victim.getUUID())
                || !match.isActiveParticipant(attacker.getUUID()) || !allows(victim, attacker)) return;
        MatchTeam victimTeam = match.teamOf(victim.getUUID()).orElseThrow();
        MatchTeam attackerTeam = match.teamOf(attacker.getUUID()).orElseThrow();
        Duel duel = active(victimTeam);
        if (duel == null) {
            duel = new Duel(victimTeam, attackerTeam);
            duels.put(victimTeam, duel);
            duels.put(attackerTeam, duel);
        }
        duel.until = match.server().getTickCount() + match.settings().get(SECONDS) * 20L;
    }

    private @Nullable Duel active(@Nullable MatchTeam team) {
        Duel duel = team == null ? null : duels.get(team);
        return duel != null && duel.until > match.server().getTickCount() ? duel : null;
    }

    void killed(ServerPlayer victim, @Nullable ServerPlayer killer) {
        MatchTeam victimTeam = match.teamOf(victim.getUUID()).orElse(null);
        Duel duel = enabled() ? active(victimTeam) : null;
        if (duel != null && killer != null
                && match.teamOf(killer.getUUID()).orElse(null) == duel.other(victimTeam)) {
            UhcProgression.duelWon(match, killer);
        }
    }

    /**
     * The opposing team of the victim's current fight, which gets exclusive access to their loot
     * until the lock expires: environmental deaths and forfeits included, without kill credit.
     */
    DeathLoot.@Nullable Claim claim(ServerPlayer victim) {
        MatchTeam victimTeam = match.teamOf(victim.getUUID()).orElse(null);
        Duel duel = enabled() ? active(victimTeam) : null;
        return duel == null ? null : new DeathLoot.Claim(match, duel.other(victimTeam), duel.until, true);
    }

    void tick() {
        if (!enabled()) {
            clear();
            return;
        }
        long now = match.server().getTickCount();
        // Permission checks use the exact deadline; reclaim expired entries only once a second.
        if (now % 20 != 0) return;
        duels.values().removeIf(duel -> duel.until <= now);
        for (ServerPlayer player : match.alivePlayers()) {
            Duel duel = active(match.teamOf(player.getUUID()).orElse(null));
            if (duel != null) player.sendSystemMessage(Component.literal(
                    "Anti-janitor: " + ((duel.until - now + 19) / 20) + "s"), true);
        }
    }

    void clear() {
        duels.clear();
        DeathLoot.clearAntiJanitor(match);
    }

    private static final class Duel {
        private final MatchTeam first;
        private final MatchTeam second;
        private long until;

        Duel(MatchTeam first, MatchTeam second) {
            this.first = first;
            this.second = second;
        }

        MatchTeam other(MatchTeam team) {
            return first == team ? second : first;
        }
    }
}
