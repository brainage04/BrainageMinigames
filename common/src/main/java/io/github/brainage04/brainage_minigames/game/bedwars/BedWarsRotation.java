package io.github.brainage04.brainage_minigames.game.bedwars;

import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsGame.State;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Currency;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Hypixel's Rotating Items: items the Item Shop's "Rotating Items" tab sells for a week, two at a
 * time, after the community log of Hypixel's weekly rotations. This mod rotates through the ones it
 * has, in a fixed order, changing every Friday (UTC) as Hypixel's do; a match keeps the items of the
 * week it started in.
 */
final class BedWarsRotation {
    private BedWarsRotation() {}

    /** The rotating items this mod has, in rotation order (two a week). */
    static final List<String> POOL = List.of(
            "lucky_chest", "sugar_cookie", "cobweb", "mega_tnt", "hay_bale", "block_zapper", "bridge_zapper", "throwable_tnt");
    /** A Friday, 00:00 UTC: weeks are counted from here. */
    private static final Instant FIRST_WEEK = Instant.parse("2022-12-09T00:00:00Z");
    static final int PER_WEEK = 2;

    /** The items the Rotating Items tab sells in the week of {@code now}. */
    static List<String> current(Instant now) {
        long week = Math.floorDiv(Duration.between(FIRST_WEEK, now).toDays(), 7);
        int first = (int) Math.floorMod(week * PER_WEEK, POOL.size());
        return List.of(POOL.get(first), POOL.get((first + 1) % POOL.size()));
    }

    /** Mega TNT's fuse: five seconds. */
    static final int MEGA_TNT_FUSE = 5 * 20;
    /** Mega TNT's explosion, larger than TNT's (this mod's choice of size). */
    static final float MEGA_TNT_POWER = 6.0F;
    /** Throwable TNT's fuse: three seconds. */
    static final int THROWN_TNT_FUSE = 3 * 20;
    /** A Bridge Zapper breaks this many joined wool blocks. */
    static final int BRIDGE_ZAPPER_BLOCKS = 16;

    /** Sugar Cookie: Speed III and Jump Boost IV for 15 seconds. */
    static void sugarCookie(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 15 * 20, 2));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 15 * 20, 3));
        player.playSound(SoundEvents.GENERIC_EAT.value(), 1.0F, 1.0F);
    }

    /** Block Zapper: breaks the one placed block at {@code pos}, never a bed; whether it did. */
    static boolean blockZapper(Match match, State state, BlockPos pos) {
        ServerLevel level = match.arena().level();
        if (!match.isPlacedBlock(pos) || level.getBlockState(pos).isAir() || BedWarsGame.isBed(state, pos)) return false;
        zap(level, pos);
        level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6F, 1.6F);
        return true;
    }

    /** Bridge Zapper: breaks the placed wool at {@code pos} and up to 15 placed wool blocks joined to it; whether it did. */
    static boolean bridgeZapper(Match match, BlockPos pos) {
        ServerLevel level = match.arena().level();
        if (!level.getBlockState(pos).is(BlockTags.WOOL) || !match.isPlacedBlock(pos)) return false;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(pos));
        Set<BlockPos> seen = new HashSet<>(List.of(pos));
        int broken = 0;
        while (!queue.isEmpty() && broken < BRIDGE_ZAPPER_BLOCKS) {
            BlockPos next = queue.poll();
            zap(level, next);
            broken++;
            for (Direction direction : Direction.values()) {
                BlockPos joined = next.relative(direction);
                if (seen.add(joined) && level.getBlockState(joined).is(BlockTags.WOOL) && match.isPlacedBlock(joined)) queue.add(joined);
            }
        }
        level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6F, 1.6F);
        return true;
    }

    private static void zap(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.0);
    }

    /**
     * Lucky Chest: a burst of random resources at the player's feet (this mod's choice of table: 8
     * to 24 iron, 2 to 8 gold, 1 or 2 diamonds or an emerald).
     */
    static void luckyChest(ServerPlayer player) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        ItemStack drop = switch (random.nextInt(10)) {
            case 0, 1, 2, 3, 4 -> new ItemStack(Currency.IRON.item, random.nextInt(8, 25));
            case 5, 6, 7 -> new ItemStack(Currency.GOLD.item, random.nextInt(2, 9));
            case 8 -> new ItemStack(Currency.DIAMOND.item, random.nextInt(1, 3));
            default -> new ItemStack(Currency.EMERALD.item, 1);
        };
        ServerLevel level = player.level();
        ItemEntity item = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), drop);
        item.setPickUpDelay(10);
        level.addFreshEntity(item);
        level.playSound(null, player.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    /** Throwable TNT: thrown the way the player looks (about five blocks), it goes off three seconds later. */
    static PrimedTnt throwTnt(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 look = player.getLookAngle();
        PrimedTnt tnt = new PrimedTnt(level, player.getX() + look.x, player.getEyeY() - 0.3, player.getZ() + look.z, player);
        tnt.setDeltaMovement(look.scale(0.9).add(0, 0.2, 0));
        tnt.setFuse(THROWN_TNT_FUSE + 20);
        tnt.addTag(BedWarsGame.ENTITY_TAG);
        level.addFreshEntity(tnt);
        level.playSound(null, player.blockPosition(), SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 1.0F, 1.0F);
        return tnt;
    }
}
