package io.github.brainage04.brainage_minigames.game;

import io.github.brainage04.brainage_minigames.game.uhc.UhcScenarios;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;

/**
 * Where an eliminated participant's belongings go, and who may take them for a while: the
 * anti-janitor death chest, the UHC Time Bomb chest and Safeloot's claim on a kill's drops all keep
 * physical loot in the world and only let one team take it until their claim runs out.
 */
public final class DeathLoot {
    /** Loot in a chest or on the ground that only {@code owners} may take until {@code until}. */
    public record Claim(Match match, MatchTeam owners, long until, boolean antiJanitor) {
        boolean active(MinecraftServer server) {
            // An anti-janitor claim lapses as soon as anti_janitor is turned off.
            return until > server.getTickCount() && (!antiJanitor || server.getGameRules().get(AntiJanitor.ENABLED));
        }

        /** Both claims on the same loot: the later deadline, held by the second claim's team. */
        static @Nullable Claim merge(@Nullable Claim first, @Nullable Claim second) {
            if (first == null) return second;
            if (second == null) return first;
            return new Claim(second.match, second.owners, Math.max(first.until, second.until),
                    first.antiJanitor && second.antiJanitor);
        }
    }

    /** Dimension identity matters: equal coordinates in another match/world must remain ordinary. */
    private static final Map<ServerLevel, Long2ObjectOpenHashMap<Claim>> CHESTS = new HashMap<>();
    /** Claimed dropped items, by item entity. */
    private static final Map<UUID, Claim> ITEMS = new HashMap<>();

    private DeathLoot() {}

    /**
     * Puts the belongings of {@code victim}, who is leaving play, where the rules want them: a UHC
     * Time Bomb chest, else the anti-janitor chest of the fight they were in ({@code duel}), else on
     * the ground when they {@code died} in a game that drops inventories. A UHC kill's drops are
     * then claimed for the killer's team under Safeloot.
     */
    static void eliminated(Match match, ServerPlayer victim, @Nullable ServerPlayer killer,
            @Nullable Claim duel, boolean died) {
        UhcScenarios.Loot scenario = UhcScenarios.loot(match, victim, killer);
        Claim claim = Claim.merge(duel, scenario.safeloot());
        if (scenario.fuseTicks() > 0) {
            List<ItemStack> items = takeInventory(victim);
            items.addAll(scenario.extra());
            BlockPos chest = chest(match, victim, items, claim);
            UhcScenarios.arm(match, victim, chest, scenario.fuseTicks());
        } else {
            if (duel != null) {
                chest(match, victim, takeInventory(victim), claim);
            } else if (died && match.game().dropsInventoryOnElimination()) {
                victim.getInventory().dropAll();
            }
            for (ItemStack stack : scenario.extra()) victim.drop(stack, true, false);
        }
        if (scenario.safeloot() != null) claimDrops(victim, scenario.safeloot());
    }

    private static List<ItemStack> takeInventory(ServerPlayer victim) {
        Inventory inventory = victim.getInventory();
        List<ItemStack> items = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.removeItemNoUpdate(slot);
            if (!stack.isEmpty()) items.add(stack);
        }
        inventory.setChanged();
        return items;
    }

    /**
     * Places a double chest at the victim's feet, or above whatever containers are already there,
     * holding {@code items} in order; anything that does not fit is dropped beside it. A claim, if
     * any, covers both halves and its owners are told where the chest is.
     */
    private static BlockPos chest(Match match, ServerPlayer victim, List<ItemStack> items, @Nullable Claim claim) {
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
        var container = new CompoundContainer(
                (ChestBlockEntity) level.getBlockEntity(first), (ChestBlockEntity) level.getBlockEntity(second));
        for (int index = 0; index < items.size(); index++) {
            if (index < container.getContainerSize()) {
                container.setItem(index, items.get(index));
            } else {
                victim.drop(items.get(index), true, false);
            }
        }
        container.setChanged();
        if (claim != null) {
            var byPosition = CHESTS.computeIfAbsent(level, ignored -> new Long2ObjectOpenHashMap<>());
            byPosition.put(first.asLong(), claim);
            byPosition.put(second.asLong(), claim);
            Component where = Component.literal("Your opponent's loot chest is at " + first.toShortString() + ".");
            for (UUID owner : claim.owners().members()) {
                ServerPlayer player = match.server().getPlayerList().getPlayer(owner);
                if (player != null) player.sendSystemMessage(where);
            }
        }
        return first;
    }

    private static boolean spaceForLoot(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) == null && level.getBlockEntity(pos.east()) == null
                && level.getBlockEntity(pos.above()) == null
                && level.getBlockEntity(pos.east().above()) == null;
    }

    /** Claims every item dropped at the victim's death this tick: their inventory, head and rewards. */
    private static void claimDrops(ServerPlayer victim, Claim claim) {
        for (ItemEntity item : victim.level().getEntitiesOfClass(ItemEntity.class,
                victim.getBoundingBox().inflate(2.0), item -> item.tickCount == 0)) {
            ITEMS.put(item.getUUID(), claim);
        }
    }

    /** Frees both halves of a chest at {@code first}, so it can be emptied and removed. */
    public static void release(ServerLevel level, BlockPos first) {
        var positions = CHESTS.get(level);
        if (positions == null) return;
        positions.remove(first.asLong());
        positions.remove(first.east().asLong());
    }

    /** Reclaims expired claims of the match once a second. */
    static void tick(Match match) {
        MinecraftServer server = match.server();
        if (server.getTickCount() % 20 != 0) return;
        CHESTS.values().forEach(chests -> chests.values().removeIf(claim -> claim.match() == match && !claim.active(server)));
        CHESTS.values().removeIf(Map::isEmpty);
        ITEMS.values().removeIf(claim -> claim.match() == match && !claim.active(server));
    }

    static void clear(Match match) {
        CHESTS.values().forEach(chests -> chests.values().removeIf(claim -> claim.match() == match));
        CHESTS.values().removeIf(Map::isEmpty);
        ITEMS.values().removeIf(claim -> claim.match() == match);
    }

    /** Clears the match's anti-janitor claims once anti-janitor no longer applies to it. */
    static void clearAntiJanitor(Match match) {
        CHESTS.values().forEach(chests -> chests.values().removeIf(claim -> claim.match() == match && claim.antiJanitor()));
        CHESTS.values().removeIf(Map::isEmpty);
    }

    private static @Nullable Claim chestClaim(Level level, long pos) {
        var positions = CHESTS.get(level);
        Claim claim = positions == null ? null : positions.get(pos);
        return claim != null && claim.active(level.getServer()) ? claim : null;
    }

    public static boolean protectedChest(Level level, BlockPos pos) {
        return protectedChest(level, pos.asLong());
    }

    public static boolean protectedChest(Level level, long pos) {
        return chestClaim(level, pos) != null;
    }

    /** Claimed death loot opens only for the claim's team until the claim runs out. */
    public static boolean canOpen(Level level, BlockPos pos, Player player) {
        Claim claim = chestClaim(level, pos.asLong());
        return claim == null || claim.owners().members().contains(player.getUUID());
    }

    /** A claimed dropped item can only be picked up by the claim's team until the claim runs out. */
    public static boolean canPickUp(ItemEntity item, Player player) {
        Claim claim = itemClaim(item);
        return claim == null || claim.owners().members().contains(player.getUUID());
    }

    /** Whether a dropped item is still claimed, so nothing but its team may collect it. */
    public static boolean claimed(ItemEntity item) {
        return itemClaim(item) != null;
    }

    private static @Nullable Claim itemClaim(ItemEntity item) {
        if (ITEMS.isEmpty()) return null;
        Claim claim = ITEMS.get(item.getUUID());
        return claim != null && claim.active(item.level().getServer()) ? claim : null;
    }
}
