package io.github.brainage04.brainage_minigames.game.spleef;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchSidebar;
import io.github.brainage04.brainage_minigames.game.MatchTeam;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bow Spleef as in Hypixel's Bow Spleef Duels: players stand on TNT floors with a flame bow, every
 * arrow that lands on the floor removes that block of TNT without exploding it, and whoever falls
 * into the void is out. Arrows never hurt players. Each player also has a limited number of the TNT
 * Games perks: double jumps, triple shots and repulsors.
 */
public final class BowSpleefGame implements Minigame {
    public static final String ID = "bow_spleef";
    public static final GameSetting DOUBLE_JUMPS =
            new GameSetting(
                    "double_jumps",
                    5,
                    0,
                    64,
                    "Double jumps per player (double-tap jump or use the feather)");
    public static final GameSetting TRIPLE_SHOTS =
            new GameSetting(
                    "triple_shots", 5, 0, 64, "Triple shots per player (use the blaze rod)");
    public static final GameSetting REPULSORS =
            new GameSetting(
                    "repulsors",
                    5,
                    0,
                    64,
                    "Repulsors per player (sneak or use the magma cream), which fling nearby opponents away");

    /** Perk items; their stack sizes are the uses left. */
    static final Item DOUBLE_JUMP_ITEM = Items.FEATHER;

    static final Item TRIPLE_SHOT_ITEM = Items.BLAZE_ROD;
    static final Item REPULSOR_ITEM = Items.MAGMA_CREAM;

    private static final double REPULSOR_RANGE = 4.5;
    private static final int DOUBLE_JUMP_COOLDOWN_TICKS = 10;
    private static final float TRIPLE_SHOT_SPREAD = 12.0F;

    private static final List<GameSetting> SETTINGS;

    static {
        List<GameSetting> all = new ArrayList<>(GameSetting.common(5, 10, false));
        all.add(DOUBLE_JUMPS);
        all.add(TRIPLE_SHOTS);
        all.add(REPULSORS);
        SETTINGS = List.copyOf(all);
    }

    private final Map<Match, State> states = new HashMap<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Bow Spleef";
    }

    @Override
    public List<GameSetting> settings() {
        return SETTINGS;
    }

    @Override
    public Identifier defaultKit() {
        return BrainageMinigames.id("kits/bow_spleef");
    }

    @Override
    public GameType playerGameMode() {
        return GameType.ADVENTURE;
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
    public void onStart(Match match) {
        GameSettings settings = match.settings();
        for (ServerPlayer player : match.alivePlayers()) {
            givePerk(player, DOUBLE_JUMP_ITEM, settings.get(DOUBLE_JUMPS), "Double Jump");
            givePerk(player, TRIPLE_SHOT_ITEM, settings.get(TRIPLE_SHOTS), "Triple Shot");
            givePerk(player, REPULSOR_ITEM, settings.get(REPULSORS), "Repulsor");
        }
    }

    @Override
    public void onRelease(Match match, ServerPlayer player) {
        if (!player.isCreative() && !player.isSpectator()) {
            setMayFly(player, false);
        }
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
        State state = state(match);
        for (ServerPlayer player : match.alivePlayers()) {
            player.clearFire();
            UUID id = player.getUUID();
            Abilities abilities = player.getAbilities();
            // Double-tapping jump makes the client start flying, which is how the double jump is
            // triggered; the player never actually flies.
            if (abilities.flying) {
                setMayFly(player, false);
                if (consume(player, DOUBLE_JUMP_ITEM)) {
                    doubleJump(player);
                }
                state.jumpReady.put(id, match.activeTicks() + DOUBLE_JUMP_COOLDOWN_TICKS);
            } else {
                boolean armed =
                        match.activeTicks() >= state.jumpReady.getOrDefault(id, 0)
                                && player.getInventory().countItem(DOUBLE_JUMP_ITEM) > 0;
                if (abilities.mayfly != armed) {
                    setMayFly(player, armed);
                }
            }
            boolean sneaking = player.isShiftKeyDown();
            if (sneaking && state.sneaking.add(id)) {
                if (consume(player, REPULSOR_ITEM)) {
                    repulse(match, player);
                }
            } else if (!sneaking) {
                state.sneaking.remove(id);
            }
        }
    }

    @Override
    public void addSidebarLines(Match match, List<Component> lines) {
        lines.add(MatchSidebar.label("Floors: ", String.valueOf(SpleefFloors.count(match))));
        lines.add(MatchSidebar.label("TNT shot: ", String.valueOf(state(match).destroyed)));
    }

    /** Arrows only break the floor; nothing hurts. */
    @Override
    public boolean allowDamage(Match match, ServerPlayer victim, DamageSource source) {
        return false;
    }

    @Override
    public boolean allowBreak(Match match, ServerPlayer player, BlockPos pos, BlockState state) {
        return false;
    }

    @Override
    public boolean allowPlace(Match match, ServerPlayer player, BlockPos pos, BlockState state) {
        return false;
    }

    @Override
    public InteractionResult onUseItem(
            Match match, ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (stack.is(DOUBLE_JUMP_ITEM)) {
            stack.shrink(1);
            doubleJump(player);
        } else if (stack.is(TRIPLE_SHOT_ITEM)) {
            stack.shrink(1);
            tripleShot(player);
        } else if (stack.is(REPULSOR_ITEM)) {
            stack.shrink(1);
            repulse(match, player);
        } else {
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    /** An arrow landing on a floor's TNT removes that block instead of lighting it. */
    @Override
    public void onProjectileHitBlock(Match match, Projectile projectile, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        if (projectile instanceof AbstractArrow
                && breakable(match, pos, projectile.level().getBlockState(pos))) {
            if (match.arena().level().destroyBlock(pos, false, projectile.getOwner())) {
                state(match).destroyed++;
            }
            projectile.discard();
        }
    }

    static boolean breakable(Match match, BlockPos pos, BlockState state) {
        return state.is(Blocks.TNT) && SpleefFloors.contains(match, pos);
    }

    private static void givePerk(ServerPlayer player, Item item, int count, String name) {
        if (count <= 0) {
            return;
        }
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.ITEM_NAME, Component.literal(name).withStyle(ChatFormatting.GOLD));
        player.getInventory().add(stack);
    }

    private static boolean consume(ServerPlayer player, Item item) {
        return player.getInventory()
                        .clearOrCountMatchingItems(
                                stack -> stack.is(item), 1, player.inventoryMenu.getCraftSlots())
                > 0;
    }

    private static void setMayFly(ServerPlayer player, boolean mayFly) {
        Abilities abilities = player.getAbilities();
        abilities.mayfly = mayFly;
        if (!mayFly) {
            abilities.flying = false;
        }
        player.onUpdateAbilities();
    }

    /** Flings the player the way they look, upwards when they look up. */
    private static void doubleJump(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        push(player, new Vec3(look.x * 1.2, 0.8 + Math.max(0.0, look.y) * 0.6, look.z * 1.2));
        effects(player, SoundEvents.BAT_TAKEOFF);
    }

    /** Three flaming arrows side by side, in the direction the player looks. */
    private static void tripleShot(ServerPlayer player) {
        ServerLevel level = player.level();
        for (float offset : new float[] {-TRIPLE_SHOT_SPREAD, 0.0F, TRIPLE_SHOT_SPREAD}) {
            Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
            arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            arrow.shootFromRotation(
                    player, player.getXRot(), player.getYRot() + offset, 0.0F, 2.5F, 1.0F);
            arrow.igniteForSeconds(100.0F);
            level.addFreshEntity(arrow);
        }
        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.ARROW_SHOOT,
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
    }

    /** Flings every nearby opponent away from the player, downwards if they are below. */
    private static void repulse(Match match, ServerPlayer player) {
        Optional<MatchTeam> team = match.teamOf(player.getUUID());
        for (ServerPlayer target : match.alivePlayers()) {
            if (target == player
                    || match.teamOf(target.getUUID()).equals(team)
                    || target.distanceTo(player) > REPULSOR_RANGE) {
                continue;
            }
            Vec3 away = target.position().subtract(player.position());
            Vec3 direction = away.lengthSqr() < 1.0E-4 ? player.getLookAngle() : away.normalize();
            double vertical = direction.y < -0.3 ? direction.y * 1.5 : 0.6;
            push(target, new Vec3(direction.x * 1.8, vertical, direction.z * 1.8));
        }
        effects(player, SoundEvents.FIRECHARGE_USE);
    }

    private static void push(ServerPlayer player, Vec3 velocity) {
        player.setDeltaMovement(velocity);
        player.resetFallDistance();
        player.hurtMarked = true;
    }

    private static void effects(ServerPlayer player, SoundEvent sound) {
        ServerLevel level = player.level();
        level.sendParticles(
                ParticleTypes.CLOUD,
                player.getX(),
                player.getY(),
                player.getZ(),
                12,
                0.3,
                0.1,
                0.3,
                0.05);
        level.playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private State state(Match match) {
        return states.computeIfAbsent(match, ignored -> new State());
    }

    private static final class State {
        /** Active tick from which a player's double jump is armed again. */
        private final Map<UUID, Integer> jumpReady = new HashMap<>();

        private final Set<UUID> sneaking = new HashSet<>();
        private int destroyed;
    }
}
