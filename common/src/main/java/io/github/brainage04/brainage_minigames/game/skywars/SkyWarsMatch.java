package io.github.brainage04.brainage_minigames.game.skywars;

import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchTeam;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Everything one SkyWars match tracks: its chests, kits, active perks and ability items in use. */
public final class SkyWarsMatch {
    public enum ChestKind { ISLAND, MID }

    final SkyWarsMode mode;
    /** Set once the cages open; until then the match is a lobby or a countdown. */
    @Nullable Match match;

    /** Glass placed for the countdown cages. */
    final List<BlockPos> cage = new ArrayList<>();
    /** Every SkyWars chest of the map and whether it is an island or a mid chest. */
    final Map<BlockPos, ChestKind> chests = new LinkedHashMap<>();
    /** Kills by team number. */
    final Map<Integer, Integer> kills = new HashMap<>();

    /** The kit each participant got when the cages opened. */
    final Map<UUID, SkyWarsKit> kits = new HashMap<>();
    /** The perks each participant had active when the cages opened. */
    final Map<UUID, Set<String>> perks = new HashMap<>();
    /** Whether perks have their upgrades, decided when the cages opened. */
    boolean upgradedPerks;

    /** Kills per participant: total, with an axe, a bow and a sword. */
    final Map<UUID, int[]> playerKills = new HashMap<>();
    /** Max-health changes per participant, in health points, removed when they are released. */
    final Map<UUID, Double> maxHealth = new HashMap<>();

    /** Mobs fighting for a team: spawned by a perk or an ability item. */
    final Map<Mob, Integer> friendlyMobs = new HashMap<>();
    /** Thrown ability items in flight and what they do when they land. */
    final Map<Projectile, Thrown> thrown = new HashMap<>();
    /** Time warps waiting to return their player. */
    final List<Warp> warps = new ArrayList<>();
    /** Each participant's positions over the last ten seconds, one per second, for Echo. */
    final Map<UUID, ArrayDeque<Vec3>> history = new HashMap<>();
    /** Server tick each participant's ability is ready again, by ability id. */
    final Map<String, Map<UUID, Integer>> cooldowns = new HashMap<>();

    /** Players who have had their first mid chest (Fruit Finder). */
    final Set<UUID> openedMid = new HashSet<>();
    /** Chests each player has opened since the last refill (Ender End Game). */
    final Map<UUID, Set<BlockPos>> openedSinceRefill = new HashMap<>();
    /** Refills so far. */
    int refills;
    /** Tracking compasses handed out for the next refill (Hide and Seek). */
    final Set<UUID> compasses = new HashSet<>();

    SkyWarsMatch(SkyWarsMode mode) {
        this.mode = mode;
    }

    public SkyWarsMode mode() {
        return mode;
    }

    /** What a thrown ability item does when it lands. */
    record Thrown(String ability, UUID owner, int team, Vec3 origin) {}

    record Warp(ServerPlayer player, Vec3 origin, int returnTick) {}

    boolean hasPerk(ServerPlayer player, String perk) {
        Set<String> active = perks.get(player.getUUID());
        return active != null && active.contains(perk);
    }

    /** The perk's number for this match; {@code 0} when the player does not have it active. */
    int perkValue(ServerPlayer player, String perk) {
        return hasPerk(player, perk) ? SkyWarsPerk.find(mode, perk).value(upgradedPerks) : 0;
    }

    int team(ServerPlayer player) {
        return match == null ? 0 : match.teamOf(player.getUUID()).map(MatchTeam::number).orElse(0);
    }

    /** Whether the ability is ready, starting its cooldown if so. */
    boolean ready(ServerPlayer player, String ability, int cooldownTicks) {
        int now = player.level().getServer().getTickCount();
        Map<UUID, Integer> ready = cooldowns.computeIfAbsent(ability, ignored -> new HashMap<>());
        if (ready.getOrDefault(player.getUUID(), 0) > now) {
            return false;
        }
        ready.put(player.getUUID(), now + cooldownTicks);
        return true;
    }

    int[] killsOf(UUID player) {
        return playerKills.computeIfAbsent(player, ignored -> new int[4]);
    }
}
