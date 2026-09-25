package io.github.brainage04.brainage_minigames.game.spleef;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchSidebar;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Spleef as in Hypixel's Spleef Duels: everyone digs out the snow floors under their opponents with
 * a shovel, every block they break gives them snowballs that break floor blocks where they land,
 * and whoever falls below the lowest floor is out. Nobody can hurt anyone; snowballs only knock
 * back.
 */
public final class SpleefGame implements Minigame {
    public static final String ID = "spleef";
    public static final GameSetting SNOWBALLS_PER_BLOCK =
            new GameSetting(
                    "snowballs_per_block",
                    2,
                    0,
                    16,
                    "Snowballs a player gets for each floor block they break with their shovel");
    public static final GameSetting SNOWBALL_CAP =
            new GameSetting(
                    "snowball_cap", 16, 0, 64, "Most snowballs a player can hold from breaking");
    public static final GameSetting CAMP_SECONDS =
            new GameSetting(
                    "camp_seconds",
                    0,
                    0,
                    60,
                    "Seconds a player may stand on one floor block before it breaks under them (0 disables)");

    private static final List<GameSetting> SETTINGS;

    static {
        List<GameSetting> all = new ArrayList<>(GameSetting.common(5, 10, false));
        all.add(SNOWBALLS_PER_BLOCK);
        all.add(SNOWBALL_CAP);
        all.add(CAMP_SECONDS);
        SETTINGS = List.copyOf(all);
    }

    private final Map<Match, State> states = new HashMap<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Spleef";
    }

    @Override
    public List<GameSetting> settings() {
        return SETTINGS;
    }

    @Override
    public Identifier defaultKit() {
        return BrainageMinigames.id("kits/spleef");
    }

    /** Survival, so players can dig; {@link #allowBreak} limits what they dig. */
    @Override
    public GameType playerGameMode() {
        return GameType.SURVIVAL;
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings settings) throws MatchException {
        return openArena(server, settings, TeamLayout.FREE_FOR_ALL);
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings settings, TeamLayout layout)
            throws MatchException {
        return SpleefFloors.open(server, ID, layout);
    }

    @Override
    public void onClose(Match match) {
        states.remove(match);
    }

    @Override
    public void tick(Match match) {
        if (match.activeTicks() % 20 == 0) {
            SpleefFloors.feed(match.alivePlayers());
        }
        int campTicks = match.settings().get(CAMP_SECONDS) * 20;
        if (campTicks <= 0) {
            return;
        }
        State state = state(match);
        for (ServerPlayer player : match.alivePlayers()) {
            if (!player.onGround()) {
                state.standing.remove(player.getUUID());
                continue;
            }
            BlockPos below = player.getOnPos();
            Standing standing = state.standing.get(player.getUUID());
            if (standing == null || !standing.pos.equals(below)) {
                state.standing.put(player.getUUID(), new Standing(below, 1));
            } else if (standing.ticks + 1 >= campTicks) {
                state.standing.remove(player.getUUID());
                if (breakable(match, below, player.level().getBlockState(below))) {
                    dig(match, below, null);
                }
            } else {
                state.standing.put(player.getUUID(), new Standing(below, standing.ticks + 1));
            }
        }
    }

    @Override
    public void addSidebarLines(Match match, List<Component> lines) {
        lines.add(MatchSidebar.label("Floors: ", String.valueOf(SpleefFloors.count(match))));
        lines.add(MatchSidebar.label("Blocks broken: ", String.valueOf(state(match).broken)));
    }

    /** Only snowballs land, and they only knock back. */
    @Override
    public boolean allowDamage(Match match, ServerPlayer victim, DamageSource source) {
        return source.getDirectEntity() instanceof Snowball;
    }

    /**
     * Shovels break floor blocks instantly and without drops, handing out snowballs instead; the
     * block is removed here and vanilla's break is cancelled.
     */
    @Override
    public boolean allowBreak(Match match, ServerPlayer player, BlockPos pos, BlockState state) {
        if (player.getMainHandItem().is(ItemTags.SHOVELS) && breakable(match, pos, state)) {
            dig(match, pos, player);
            giveSnowballs(match, player);
        }
        return false;
    }

    @Override
    public boolean allowPlace(Match match, ServerPlayer player, BlockPos pos, BlockState state) {
        return false;
    }

    @Override
    public void onProjectileHitBlock(Match match, Projectile projectile, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        if (projectile instanceof Snowball
                && breakable(match, pos, projectile.level().getBlockState(pos))) {
            dig(match, pos, projectile.getOwner());
        }
    }

    /** Shovel-mineable blocks inside a floor layer; walls are never shovel-mineable. */
    static boolean breakable(Match match, BlockPos pos, BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_SHOVEL) && SpleefFloors.contains(match, pos);
    }

    private void dig(Match match, BlockPos pos, Entity breaker) {
        if (match.arena().level().destroyBlock(pos, false, breaker)) {
            state(match).broken++;
        }
    }

    private static void giveSnowballs(Match match, ServerPlayer player) {
        int held = player.getInventory().countItem(Items.SNOWBALL);
        int count =
                Math.min(
                        match.settings().get(SNOWBALLS_PER_BLOCK),
                        match.settings().get(SNOWBALL_CAP) - held);
        if (count > 0) {
            player.getInventory().add(new ItemStack(Items.SNOWBALL, count));
        }
    }

    private State state(Match match) {
        return states.computeIfAbsent(match, ignored -> new State());
    }

    private static final class State {
        private final Map<UUID, Standing> standing = new HashMap<>();
        private int broken;
    }

    private record Standing(BlockPos pos, int ticks) {}
}
