package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.game.DeathLoot;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchTeam;
import io.github.brainage04.brainage_minigames.mixin.DisplayAccess;
import io.github.brainage04.brainage_minigames.mixin.TextDisplayAccess;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The combat and loot scenarios of {@link UhcScenarioRules} in UHC-style matches: Time Bomb loot
 * chests, No Clean protection after a kill, Safeloot claims, team backpacks and Second Chance.
 */
public final class UhcScenarios {
    /** A Time Bomb explodes as strongly as TNT. */
    public static final float TIME_BOMB_POWER = 4.0F;
    /** Tag of the countdown text above a Time Bomb chest. */
    public static final String TIME_BOMB_TAG = "brainage_minigames:time_bomb";
    private static final int BACKPACK_SLOTS = 27;
    private static final Map<Match, State> STATES = new HashMap<>();

    private static final class State {
        /** Server tick each killer's No Clean protection ends. */
        final Map<UUID, Long> noClean = new HashMap<>();
        final Set<UUID> secondChances = new HashSet<>();
        final Map<MatchTeam, SimpleContainer> backpacks = new HashMap<>();
        final List<Bomb> bombs = new ArrayList<>();
    }

    /** An armed Time Bomb chest; {@code chest} is its first (west) half. */
    private record Bomb(ServerLevel level, BlockPos chest, long explodesAt, Display.TextDisplay hologram, String owner) {}

    /**
     * What an elimination adds to the usual loot: a Time Bomb fuse (0 without one), items for the
     * loot chest or ground (a golden head, the team backpack) and a Safeloot claim.
     */
    public record Loot(int fuseTicks, List<ItemStack> extra, DeathLoot.@Nullable Claim safeloot) {}

    private static final Loot NO_LOOT = new Loot(0, List.of(), null);

    private UhcScenarios() {}

    private static State state(Match match) {
        return STATES.computeIfAbsent(match, ignored -> new State());
    }

    private static long now(Match match) {
        return match.server().getTickCount();
    }

    /** Whether the player is alive in the active phase of a UHC-style match. */
    public static boolean participant(ServerPlayer player) {
        return MatchManager.matchOf(player.getUUID())
                .filter(match -> UhcScenarioRules.family(match) && match.isActiveParticipant(player.getUUID()))
                .isPresent();
    }

    // Second Chance

    /**
     * Brings a player who died before PvP was enabled back once, under {@code uhc_second_chance}:
     * at a random dry spot inside the border, keeping their items, effects and experience, with the
     * health and food the death left them. Games without a grace period never use it.
     */
    public static boolean secondChance(Match match, ServerPlayer player) {
        if (!match.server().getGameRules().get(UhcScenarioRules.SECOND_CHANCE) || player.hasDisconnected()
                || !(match.arena() instanceof UhcArena arena)) {
            return false;
        }
        GameSetting grace = match.game().setting(UhcGame.GRACE_PERIOD.key()).orElse(null);
        if (grace == null || match.activeTicks() >= match.settings().minutesInTicks(grace)
                || !state(match).secondChances.add(player.getUUID())) {
            return false;
        }
        player.stopRiding();
        player.clearFire();
        player.setDeltaMovement(Vec3.ZERO);
        PlayerUtils.teleport(player, arena.level(), arena.randomSurface(player.getRandom()), player.getYRot());
        player.resetFallDistance();
        arena.sendBorder(player);
        player.sendSystemMessage(Component.literal(
                "Second Chance: you are back in the game with your items. Your next death is final.")
                .withStyle(ChatFormatting.GOLD));
        return true;
    }

    // No Clean

    /** Starts the killer's No Clean protection, under {@code uhc_no_clean_seconds}. */
    public static void eliminated(Match match, ServerPlayer victim, @Nullable ServerPlayer killer) {
        int seconds = match.server().getGameRules().get(UhcScenarioRules.NO_CLEAN_SECONDS);
        if (seconds == 0 || killer == null || killer == victim || !UhcScenarioRules.family(match)
                || !match.isActiveParticipant(killer.getUUID())
                || match.teamOf(killer.getUUID()).equals(match.teamOf(victim.getUUID()))) {
            return;
        }
        state(match).noClean.put(killer.getUUID(), now(match) + seconds * 20L);
        killer.sendSystemMessage(Component.literal(
                "No Clean: players cannot hurt you for %d seconds, or until you attack one.".formatted(seconds))
                .withStyle(ChatFormatting.AQUA));
    }

    /** Whether players cannot hurt {@code victim} because of their No Clean protection. */
    public static boolean noClean(Match match, ServerPlayer victim) {
        State state = STATES.get(match);
        Long until = state == null ? null : state.noClean.get(victim.getUUID());
        return until != null && until > now(match);
    }

    /** Whether players cannot hurt {@code player} now because of No Clean, in whatever match they play. */
    public static boolean noCleanProtected(ServerPlayer player) {
        return MatchManager.matchOf(player.getUUID()).map(match -> noClean(match, player)).orElse(false);
    }

    /** Ends the attacker's No Clean protection: they attacked a player. */
    public static void attacked(Match match, ServerPlayer attacker) {
        State state = STATES.get(match);
        Long until = state == null ? null : state.noClean.remove(attacker.getUUID());
        if (until != null && until > now(match)) {
            attacker.sendSystemMessage(Component.literal("No Clean ended: you attacked a player.")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    // Loot: Time Bomb, Safeloot and the backpack of a team's last member

    /** The scenario part of an elimination's loot; see {@link DeathLoot}. */
    public static Loot loot(Match match, ServerPlayer victim, @Nullable ServerPlayer killer) {
        if (!UhcScenarioRules.family(match)) return NO_LOOT;
        var rules = match.server().getGameRules();
        int fuse = rules.get(UhcScenarioRules.TIME_BOMB_SECONDS) * 20;
        List<ItemStack> extra = new ArrayList<>();
        if (fuse > 0) extra.add(UhcCrafting.goldenHead(victim));
        extra.addAll(lastBackpack(match, victim));
        DeathLoot.Claim claim = null;
        int safeloot = rules.get(UhcScenarioRules.SAFELOOT_SECONDS);
        MatchTeam killers = killer == null || killer == victim ? null : match.teamOf(killer.getUUID()).orElse(null);
        if (safeloot > 0 && killers != null && killers != match.teamOf(victim.getUUID()).orElse(null)) {
            claim = new DeathLoot.Claim(match, killers, now(match) + safeloot * 20L, false);
        }
        return fuse == 0 && extra.isEmpty() && claim == null ? NO_LOOT : new Loot(fuse, extra, claim);
    }

    /** The team backpack's contents when {@code victim} is the last of their team left alive. */
    private static List<ItemStack> lastBackpack(Match match, ServerPlayer victim) {
        State state = STATES.get(match);
        MatchTeam team = match.teamOf(victim.getUUID()).orElse(null);
        if (state == null || team == null || team.members().stream()
                .anyMatch(member -> !member.equals(victim.getUUID()) && match.isAlive(member))) {
            return List.of();
        }
        SimpleContainer backpack = state.backpacks.remove(team);
        return backpack == null ? List.of() : backpack.removeAllItems();
    }

    /** Arms a Time Bomb on the loot chest whose first half is at {@code chest}. */
    public static void arm(Match match, ServerPlayer victim, BlockPos chest, int fuseTicks) {
        ServerLevel level = victim.level();
        Display.TextDisplay hologram = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (hologram == null) return;
        hologram.snapTo(chest.getX() + 1.0, chest.getY() + 1.5, chest.getZ() + 0.5);
        ((DisplayAccess) hologram).brainage_minigames$setBillboardConstraints(Display.BillboardConstraints.CENTER);
        hologram.addTag(TIME_BOMB_TAG);
        Bomb bomb = new Bomb(level, chest.immutable(), now(match) + fuseTicks, hologram, victim.getScoreboardName());
        label(bomb, fuseTicks);
        level.addFreshEntity(hologram);
        state(match).bombs.add(bomb);
        match.broadcast(Component.literal("%s's loot is a Time Bomb at %s: it explodes in %d seconds."
                .formatted(bomb.owner(), chest.toShortString(), Math.ceilDiv(fuseTicks, 20)))
                .withStyle(ChatFormatting.RED));
    }

    private static void label(Bomb bomb, long ticksLeft) {
        long seconds = Math.ceilDiv(ticksLeft, 20);
        ((TextDisplayAccess) bomb.hologram()).brainage_minigames$setText(Component.literal(bomb.owner() + "'s Time Bomb\n")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(seconds + "s").withStyle(seconds <= 5 ? ChatFormatting.RED : ChatFormatting.YELLOW,
                        ChatFormatting.BOLD)));
    }

    /**
     * Empties and removes the chest, then explodes: entities nearby are hurt and pushed, and only
     * blocks players could break there are destroyed, never a map's protected blocks.
     */
    private static void detonate(Match match, Bomb bomb) {
        bomb.hologram().discard();
        ServerLevel level = bomb.level();
        remove(bomb);
        Vec3 center = new Vec3(bomb.chest().getX() + 1.0, bomb.chest().getY() + 0.5, bomb.chest().getZ() + 0.5);
        level.explode(null, null, new Blast(match), center, TIME_BOMB_POWER, false, Level.ExplosionInteraction.BLOCK);
        match.broadcast(Component.literal(bomb.owner() + "'s Time Bomb exploded.").withStyle(ChatFormatting.RED));
    }

    /** Destroys the bomb's chest and what is still in it, if it is still there. */
    private static void remove(Bomb bomb) {
        ServerLevel level = bomb.level();
        DeathLoot.release(level, bomb.chest());
        for (BlockPos half : List.of(bomb.chest(), bomb.chest().east())) {
            if (level.isLoaded(half) && level.getBlockEntity(half) instanceof ChestBlockEntity chest) {
                chest.clearContent();
                level.setBlock(half, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** A Time Bomb blast breaks only what players may break where it goes off. */
    private static final class Blast extends ExplosionDamageCalculator {
        private final Match match;

        Blast(Match match) {
            this.match = match;
        }

        @Override
        public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
            return match.arena() instanceof UhcArena arena ? arena.canBuild(explosion.level(), pos)
                    : match.arena().canBuild(pos);
        }
    }

    /** Armed Time Bomb chests in {@code level} and the ticks until each explodes, by the chest's first half. */
    public static Map<BlockPos, Integer> timeBombs(ServerLevel level) {
        Map<BlockPos, Integer> bombs = new HashMap<>();
        for (var entry : STATES.entrySet()) {
            long now = now(entry.getKey());
            for (Bomb bomb : entry.getValue().bombs) {
                if (bomb.level() == level) bombs.put(bomb.chest(), (int) Math.max(0, bomb.explodesAt() - now));
            }
        }
        return bombs;
    }

    // Team backpack

    /** Opens the 27-slot backpack the player's team shares, under {@code uhc_team_backpack}. */
    public static void openBackpack(ServerPlayer player) throws MatchException {
        Match match = MatchManager.matchOf(player.getUUID())
                .filter(UhcScenarioRules::family)
                .orElseThrow(() -> new MatchException("Backpacks are only in UHC-style matches."));
        if (!match.server().getGameRules().get(UhcScenarioRules.TEAM_BACKPACK)) {
            throw new MatchException("Team backpacks are off on this server.");
        }
        if (!match.isActiveParticipant(player.getUUID())) {
            throw new MatchException("Only players still in the game can open their team's backpack.");
        }
        MatchTeam team = match.teamOf(player.getUUID()).orElseThrow();
        if (team.members().size() < 2) {
            throw new MatchException("Solo players have no backpack.");
        }
        SimpleContainer backpack = state(match).backpacks.computeIfAbsent(team, ignored -> new SimpleContainer(BACKPACK_SLOTS));
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> ChestMenu.threeRows(id, inventory, backpack),
                Component.empty().append(team.displayName()).append(" Backpack")));
    }

    // Lifecycle

    public static void tick(Match match) {
        State state = STATES.get(match);
        if (state == null) return;
        long now = now(match);
        List<Bomb> due = new ArrayList<>();
        state.bombs.removeIf(bomb -> {
            long left = bomb.explodesAt() - now;
            if (left <= 0) return due.add(bomb);
            if (left % 20 == 0) label(bomb, left);
            return false;
        });
        // A blast can kill a player whose own loot becomes a new Time Bomb while these go off.
        for (Bomb bomb : due) detonate(match, bomb);
        if (now % 20 != 0) return;
        for (Iterator<Map.Entry<UUID, Long>> entries = state.noClean.entrySet().iterator(); entries.hasNext(); ) {
            Map.Entry<UUID, Long> entry = entries.next();
            ServerPlayer player = match.server().getPlayerList().getPlayer(entry.getKey());
            if (entry.getValue() <= now) {
                entries.remove();
                if (player != null) player.sendSystemMessage(Component.literal("No Clean protection has ended.")
                        .withStyle(ChatFormatting.AQUA));
            } else if (player != null) {
                player.sendSystemMessage(Component.literal("No Clean: " + Math.ceilDiv(entry.getValue() - now, 20) + "s")
                        .withStyle(ChatFormatting.AQUA), true);
            }
        }
    }

    /** Removes unexploded Time Bombs and their countdowns and forgets the match's backpacks. */
    public static void close(Match match) {
        State state = STATES.remove(match);
        if (state == null) return;
        for (Bomb bomb : state.bombs) {
            bomb.hologram().discard();
            remove(bomb);
        }
    }
}
