package io.github.brainage04.brainage_minigames.game;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import org.jspecify.annotations.Nullable;

/** Match-lifetime ownership, shared with bot mods through the documented public queries. */
public final class ContainerProtection {
    public static final GameRule<Boolean> ENABLED = new GameRule<>(
            GameRuleCategory.PLAYER, GameRuleType.BOOL, BoolArgumentType.bool(),
            GameRuleTypeVisitor::visitBoolean, Codec.BOOL, value -> value ? 1 : 0,
            true, FeatureFlagSet.of());
    private static final Map<Level, Long2ObjectOpenHashMap<Ownership>> OWNERS = new HashMap<>();
    private static final Map<UUID, Access> ACCESSES = new HashMap<>();

    /** Last successful foreign use/break while protection is off; tick is the server tick count. */
    public record Access(UUID actor, ResourceKey<Level> dimension, BlockPos position, String action, int tick) {}
    private record Ownership(Match match, UUID owner, BlockEntity entity) {}

    private ContainerProtection() {}

    public static void register(BiConsumer<Identifier, GameRule<?>> registry) {
        registry.accept(BrainageMinigames.id("container_protection"), ENABLED);
    }

    static void placed(Match match, ServerPlayer player, BlockPos pos) {
        BlockEntity entity = player.level().getBlockEntity(pos);
        if (entity instanceof Container || entity instanceof net.minecraft.world.level.block.entity.EnderChestBlockEntity) {
            OWNERS.computeIfAbsent(player.level(), ignored -> new Long2ObjectOpenHashMap<>())
                    .put(pos.asLong(), new Ownership(match, player.getUUID(), entity));
        }
    }

    private static @Nullable Ownership ownership(Level level, BlockPos pos) {
        var positions = OWNERS.get(level);
        Ownership ownership = positions == null ? null : positions.get(pos.asLong());
        // Replacing a block outside a player's break path must not transfer its old ownership.
        if (ownership != null && level.getBlockEntity(pos) != ownership.entity) {
            positions.remove(pos.asLong());
            return null;
        }
        return ownership;
    }

    /** Null for map/natural/unowned containers; each half of a double chest has its own owner. */
    public static @Nullable UUID owner(Level level, BlockPos pos) {
        Ownership ownership = ownership(level, pos);
        return ownership == null ? null : ownership.owner;
    }

    /** Null until another player successfully uses/breaks this participant's container. */
    public static @Nullable Access lastAccess(ServerPlayer owner) {
        return ACCESSES.get(owner.getUUID());
    }

    public static boolean canAccess(Level level, BlockPos pos, Player player) {
        if (level.isClientSide() || !level.getServer().getGameRules().get(ENABLED)) return true;
        UUID owner = owner(level, pos);
        return owner == null || owner.equals(player.getUUID());
    }

    /** Opening either half exposes both halves, so neither may belong to someone else. */
    public static boolean canOpen(Level level, BlockPos pos, Player player) {
        if (!canAccess(level, pos, player)) return false;
        BlockPos other = otherHalf(level, pos);
        return other == null || canAccess(level, other, player);
    }

    private static @Nullable BlockPos otherHalf(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                ? pos.relative(ChestBlock.getConnectedDirection(state)) : null;
    }

    /** UI extraction also checks ownership if the rule changes while a menu is already open. */
    public static boolean canTake(Container container, Player player) {
        if (container instanceof BlockEntity entity && entity.getLevel() != null) {
            return canAccess(entity.getLevel(), entity.getBlockPos(), player);
        }
        if (container instanceof CompoundContainer compound) {
            // CompoundContainer delegates stillValid to both halves, including our ownership hook.
            return compound.stillValid(player);
        }
        return true;
    }

    /** Cheap automation gate: ordinary worlds and disabled protection need no position work. */
    public static boolean protectsContainers(Level level) {
        var positions = OWNERS.get(level);
        return !level.isClientSide() && positions != null && !positions.isEmpty()
                && level.getServer().getGameRules().get(ENABLED);
    }

    /** A placed hopper may extract only its owner's items; unowned hoppers/minecarts cannot. */
    public static boolean canExtract(Level level, BlockPos source, @Nullable BlockPos hopper) {
        return !protectsContainers(level) || canExtractOwned(level, source, hopper);
    }

    public static boolean canExtract(Level level, long source, @Nullable BlockPos hopper) {
        return !protectsContainers(level) || canExtractOwned(level, BlockPos.of(source), hopper);
    }

    private static boolean canExtractOwned(Level level, BlockPos source, @Nullable BlockPos hopper) {
        UUID destinationOwner = hopper == null ? null : owner(level, hopper);
        UUID sourceOwner = owner(level, source);
        if (sourceOwner != null && !sourceOwner.equals(destinationOwner)) return false;
        BlockPos other = otherHalf(level, source);
        UUID otherOwner = other == null ? null : owner(level, other);
        return otherOwner == null || otherOwner.equals(destinationOwner);
    }

    public static void used(ServerPlayer actor, BlockPos pos) {
        accessed(actor, pos, "open");
        BlockPos other = otherHalf(actor.level(), pos);
        if (other != null) accessed(actor, other, "open");
    }

    private static void accessed(ServerPlayer actor, BlockPos pos, String action) {
        if (actor.level().getGameRules().get(ENABLED)) return;
        Ownership ownership = ownership(actor.level(), pos);
        if (ownership != null && !ownership.owner.equals(actor.getUUID())) {
            ACCESSES.put(ownership.owner, new Access(actor.getUUID(), actor.level().dimension(),
                    pos.immutable(), action, actor.level().getServer().getTickCount()));
        }
    }

    public static void broken(ServerPlayer actor, BlockPos pos) {
        // Vanilla removed the block entity already, so read the entry without the identity check.
        var positions = OWNERS.get(actor.level());
        Ownership ownership = positions == null ? null : positions.remove(pos.asLong());
        if (ownership != null && !actor.level().getGameRules().get(ENABLED)
                && !ownership.owner.equals(actor.getUUID())) {
            ACCESSES.put(ownership.owner, new Access(actor.getUUID(), actor.level().dimension(),
                    pos.immutable(), "break", actor.level().getServer().getTickCount()));
        }
    }

    static void clear(Match match) {
        OWNERS.values().forEach(positions -> positions.values().removeIf(ownership -> ownership.match == match));
        OWNERS.values().removeIf(Map::isEmpty);
        for (var team : match.teams()) for (UUID member : team.members()) ACCESSES.remove(member);
    }
}
