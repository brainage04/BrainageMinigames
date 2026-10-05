package io.github.brainage04.brainage_minigames.game;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.uhc.UhcProgression;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.flag.FeatureFlagSet;
import org.jspecify.annotations.Nullable;

/** Pairwise combat protection and physical, temporarily private death loot. */
public final class AntiJanitor {
    public static final GameRule<Boolean> ENABLED = new GameRule<>(
            GameRuleCategory.PLAYER, GameRuleType.BOOL, BoolArgumentType.bool(),
            GameRuleTypeVisitor::visitBoolean, Codec.BOOL, value -> value ? 1 : 0,
            true, FeatureFlagSet.of());
    public static final GameSetting SECONDS = new GameSetting(
            "anti_janitor_seconds", 30, 1, 3600,
            "Seconds of exclusive combat and death-chest protection after the last damaging hit");

    // Dimension identity matters: equal coordinates in another match/world must remain ordinary.
    private static final Map<ServerLevel, Long2ObjectOpenHashMap<Loot>> CHESTS = new HashMap<>();
    private final Match match;
    private final Map<UUID, Duel> duels = new HashMap<>();

    AntiJanitor(Match match) {
        this.match = match;
    }

    public static void register(BiConsumer<Identifier, GameRule<?>> registry) {
        registry.accept(BrainageMinigames.id("anti_janitor"), ENABLED);
    }

    private boolean enabled() {
        return match.game().antiJanitor() && !match.isPrivate()
                && (match.layout().isFreeForAll() || match.layout().teamSizes().size() > 2)
                && match.server().getGameRules().get(ENABLED);
    }

    boolean allows(ServerPlayer first, ServerPlayer second) {
        if (!enabled() || first == second) return true;
        if (match.teamOf(first.getUUID()).equals(match.teamOf(second.getUUID()))) return false;
        Duel a = active(first.getUUID());
        Duel b = active(second.getUUID());
        return (a == null && b == null) || (a != null && a == b);
    }

    /** Called only after health or absorption was actually lost, before lethal damage eliminates. */
    void damaged(ServerPlayer victim, ServerPlayer attacker) {
        if (!enabled() || victim == attacker || !match.isActiveParticipant(victim.getUUID())
                || !match.isActiveParticipant(attacker.getUUID()) || !allows(victim, attacker)) return;
        Duel duel = active(victim.getUUID());
        if (duel == null) {
            duel = new Duel(victim.getUUID(), attacker.getUUID());
            duels.put(victim.getUUID(), duel);
            duels.put(attacker.getUUID(), duel);
        }
        duel.until = match.server().getTickCount() + match.settings().get(SECONDS) * 20L;
    }

    private Duel active(UUID player) {
        Duel duel = duels.get(player);
        return duel != null && duel.until > match.server().getTickCount() ? duel : null;
    }

    void killed(ServerPlayer victim, @Nullable ServerPlayer killer) {
        Duel duel = enabled() ? active(victim.getUUID()) : null;
        if (duel != null && killer != null && duel.other(victim.getUUID()).equals(killer.getUUID())) {
            UhcProgression.duelWon(match, killer);
        }
    }

    /** Environmental deaths/forfeits still give the current partner exclusive loot, not kill credit. */
    boolean storeDrops(ServerPlayer victim) {
        Duel duel = enabled() ? active(victim.getUUID()) : null;
        if (duel == null) return false;
        ServerLevel level = victim.level();
        int minimum = Math.max(level.getMinY() + 1, (int) Math.ceil(match.arena().voidY() + 1));
        int y = Math.clamp(victim.blockPosition().getY(), minimum, level.getMaxY() - 2);
        BlockPos first = new BlockPos(victim.blockPosition().getX(), y, victim.blockPosition().getZ());
        // Preserve existing containers, including another death chest at the same death spot.
        while (!spaceForLoot(level, first)) {
            first = first.above();
            if (first.getY() >= level.getMaxY() - 1) {
                first = new BlockPos(first.getX() + 2, y, first.getZ());
            }
        }
        BlockPos second = first.east();
        boolean wetFirst = level.getFluidState(first).is(net.minecraft.tags.FluidTags.WATER);
        boolean wetSecond = level.getFluidState(second).is(net.minecraft.tags.FluidTags.WATER);
        level.setBlock(first.above(), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(second.above(), Blocks.AIR.defaultBlockState(), 2);
        var chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
        level.setBlock(first, chest.setValue(ChestBlock.TYPE, ChestType.LEFT)
                .setValue(ChestBlock.WATERLOGGED, wetFirst), 2);
        level.setBlock(second, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT)
                .setValue(ChestBlock.WATERLOGGED, wetSecond), 2);
        ChestBlockEntity left = (ChestBlockEntity) level.getBlockEntity(first);
        ChestBlockEntity right = (ChestBlockEntity) level.getBlockEntity(second);
        var container = new CompoundContainer(left, right);
        var inventory = victim.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            container.setItem(slot, inventory.removeItemNoUpdate(slot));
        }
        inventory.setChanged();
        container.setChanged();
        Loot loot = new Loot(match, duel.other(victim.getUUID()), duel.until);
        var byPosition = CHESTS.computeIfAbsent(level, ignored -> new Long2ObjectOpenHashMap<>());
        byPosition.put(first.asLong(), loot);
        byPosition.put(second.asLong(), loot);
        ServerPlayer owner = match.server().getPlayerList().getPlayer(loot.owner);
        if (owner != null) owner.sendSystemMessage(Component.literal(
                "Your opponent's loot chest is at " + first.toShortString() + "."));
        return true;
    }

    private static boolean spaceForLoot(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) == null && level.getBlockEntity(pos.east()) == null
                && level.getBlockEntity(pos.above()) == null
                && level.getBlockEntity(pos.east().above()) == null;
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
        CHESTS.values().forEach(chests -> chests.values().removeIf(loot -> loot.match == match && loot.until <= now));
        CHESTS.values().removeIf(Map::isEmpty);
        if (now % 20 == 0) {
            for (ServerPlayer player : match.alivePlayers()) {
                Duel duel = active(player.getUUID());
                if (duel != null) player.sendSystemMessage(Component.literal(
                        "Anti-janitor: " + ((duel.until - now + 19) / 20) + "s"), true);
            }
        }
    }

    void clear() {
        duels.clear();
        CHESTS.values().forEach(chests -> chests.values().removeIf(loot -> loot.match == match));
        CHESTS.values().removeIf(Map::isEmpty);
    }

    private static Loot protectedLoot(Level level, long pos) {
        var positions = CHESTS.get(level);
        Loot loot = positions == null ? null : positions.get(pos);
        return loot != null && level.getServer().getGameRules().get(ENABLED)
                && loot.until > level.getServer().getTickCount() ? loot : null;
    }

    public static boolean protectedChest(Level level, BlockPos pos) {
        return protectedChest(level, pos.asLong());
    }

    public static boolean protectedChest(Level level, long pos) {
        return protectedLoot(level, pos) != null;
    }

    public static boolean canOpen(Level level, BlockPos pos, Player player) {
        Loot loot = protectedLoot(level, pos.asLong());
        return loot == null || loot.owner.equals(player.getUUID());
    }

    private static final class Duel {
        private final UUID first;
        private final UUID second;
        private long until;

        Duel(UUID first, UUID second) {
            this.first = first;
            this.second = second;
        }

        UUID other(UUID player) {
            return first.equals(player) ? second : first;
        }
    }

    private record Loot(Match match, UUID owner, long until) {}
}
