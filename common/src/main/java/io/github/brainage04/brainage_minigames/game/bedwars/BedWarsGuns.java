package io.github.brainage04.brainage_minigames.game.bedwars;

import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsGame.State;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Bed Wars Armed's guns. Vanilla clients have no gun models or reload animations, so a gun is a hoe
 * (the flamethrower a flint and steel) whose durability bar shows the rounds left in its clip;
 * right-click fires a hit-scan shot (a line of particles to where it lands), left-click reloads, and
 * firing an empty gun reloads it. Shots out of range do nothing; a hit above the shoulders is a
 * headshot for 1.5 times the damage. The numbers are the community guide's measurements of
 * Hypixel's guns; the shotgun's six pellets and their spread are this mod's choice.
 */
final class BedWarsGuns {
    private BedWarsGuns() {}

    enum Gun {
        PISTOL("pistol", "Pistol", Items.WOODEN_HOE, 4.0F, 8, 12, 30, 30, 1, 0.0),
        MAGNUM("magnum", "Magnum", Items.IRON_HOE, 6.0F, 12, 6, 60, 40, 1, 0.0),
        RIFLE("rifle", "Rifle", Items.DIAMOND_HOE, 4.0F, 4, 25, 60, 40, 1, 0.0),
        SMG("smg", "SMG", Items.STONE_HOE, 2.0F, 4, 45, 40, 30, 1, 0.0),
        FLAMETHROWER("flamethrower", "Flamethrower", Items.FLINT_AND_STEEL, 2.0F, 2, 50, 60, 20, 1, 0.0),
        SHOTGUN("shotgun", "Shotgun", Items.GOLDEN_HOE, 2.0F, 20, 4, 80, 10, 6, 0.12);

        final String id;
        final String displayName;
        final Item item;
        final float damage;
        /** Ticks between shots. */
        final int fireTicks;
        final int clip;
        final int reloadTicks;
        final double range;
        final int pellets;
        final double spread;

        Gun(String id, String displayName, Item item, float damage, int fireTicks, int clip, int reloadTicks, double range,
                int pellets, double spread) {
            this.id = id;
            this.displayName = displayName;
            this.item = item;
            this.damage = damage;
            this.fireTicks = fireTicks;
            this.clip = clip;
            this.reloadTicks = reloadTicks;
            this.range = range;
            this.pellets = pellets;
            this.spread = spread;
        }

        static Optional<Gun> of(String id) {
            for (Gun gun : values()) if (gun.id.equals(id)) return Optional.of(gun);
            return Optional.empty();
        }
    }

    /** Rounds left in each of a player's guns, and the gun they are reloading. */
    static final class Magazine {
        final Map<Gun, Integer> rounds = new HashMap<>();
        @Nullable Gun reloading;
        int reloadDone;
    }

    static boolean isGun(String id) {
        return Gun.of(id).isPresent();
    }

    /** A gun as the shop sells it, its durability bar a full clip. */
    static ItemStack stack(Gun gun) {
        ItemStack stack = BedWarsShop.tagged(gun.item, gun.id, gun.displayName);
        stack.remove(DataComponents.WEAPON);
        stack.remove(DataComponents.TOOL);
        stack.set(DataComponents.MAX_DAMAGE, gun.clip + 1);
        stack.set(DataComponents.DAMAGE, 0);
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        return stack;
    }

    /** Everyone spawns with a Pistol. */
    static void equip(ServerPlayer player) {
        PlayerUtils.giveOrDrop(player, stack(Gun.PISTOL));
    }

    private static Magazine magazine(State state, ServerPlayer player) {
        return state.magazines.computeIfAbsent(player.getUUID(), ignored -> new Magazine());
    }

    static InteractionResult fire(BedWarsGame game, Match match, State state, ServerPlayer player, ItemStack stack) {
        Optional<Gun> found = Gun.of(BedWarsShop.ability(stack));
        if (found.isEmpty()) return InteractionResult.PASS;
        Gun gun = found.get();
        Magazine magazine = magazine(state, player);
        if (magazine.reloading != null || player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
        int left = magazine.rounds.getOrDefault(gun, gun.clip);
        if (left <= 0) {
            startReload(match, state, player, gun, stack);
            return InteractionResult.FAIL;
        }
        magazine.rounds.put(gun, left - 1);
        show(player, stack, gun, left - 1);
        player.getCooldowns().addCooldown(stack, gun.fireTicks);
        ServerLevel level = player.level();
        BedWarsGame.TeamState team = state.teams.get(BedWarsGame.teamOf(match, player));
        float boost = deadshot(team == null ? 0 : team.level(BedWarsUpgrades.Upgrade.DEADSHOT));
        for (int pellet = 0; pellet < gun.pellets; pellet++) shoot(match, player, gun, level, boost);
        level.playSound(null, player.blockPosition(), gun == Gun.FLAMETHROWER ? SoundEvents.BLAZE_SHOOT : SoundEvents.CROSSBOW_SHOOT,
                SoundSource.PLAYERS, 0.6F, gun == Gun.MAGNUM ? 0.6F : 1.4F);
        if (left - 1 == 0) startReload(match, state, player, gun, stack);
        return InteractionResult.SUCCESS;
    }

    /** The team upgrade Deadshot's damage multiplier: a quarter more per tier (this mod's choice). */
    static float deadshot(int tier) {
        return 1.0F + 0.25F * tier;
    }

    private static void shoot(Match match, ServerPlayer player, Gun gun, ServerLevel level, float boost) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        if (gun.spread > 0) {
            var random = player.getRandom();
            look = look.add(random.triangle(0, gun.spread), random.triangle(0, gun.spread), random.triangle(0, gun.spread)).normalize();
        }
        Vec3 end = eye.add(look.scale(gun.range));
        BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() == HitResult.Type.BLOCK) end = wall.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end,
                new AABB(eye, end).inflate(1.0), entity -> target(match, player, entity), 0.0F);
        Vec3 landed = hit == null ? end : hit.getLocation();
        trail(level, gun == Gun.FLAMETHROWER ? ParticleTypes.FLAME : ParticleTypes.CRIT, eye.add(look.scale(0.8)), landed);
        if (hit == null || !(hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity victim)) return;
        boolean headshot = hit.getLocation().y >= victim.getEyeY() - 0.3;
        victim.invulnerableTime = 0;
        victim.hurtServer(level, player.damageSources().playerAttack(player), gun.damage * boost * (headshot ? 1.5F : 1.0F));
        if (gun == Gun.FLAMETHROWER) {
            victim.igniteForSeconds(3.0F);
            victim.removeEffect(MobEffects.INVISIBILITY);
        }
        if (gun == Gun.SHOTGUN) {
            victim.push(look.x * 0.4, 0.1, look.z * 0.4);
            victim.hurtMarked = true;
        }
        if (headshot) player.playSound(SoundEvents.ARROW_HIT_PLAYER, 0.5F, 1.2F);
    }

    /** Players of other teams and the game's mobs, never teammates or spectators. */
    private static boolean target(Match match, ServerPlayer shooter, Entity entity) {
        if (entity instanceof ServerPlayer other) {
            return !other.isSpectator() && match.isAlive(other.getUUID())
                    && BedWarsGame.teamOf(match, other) != BedWarsGame.teamOf(match, shooter);
        }
        return entity instanceof net.minecraft.world.entity.Mob && BedWarsGame.ownerTeam(entity) != BedWarsGame.teamOf(match, shooter);
    }

    private static void trail(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to) {
        Vec3 step = to.subtract(from);
        int points = Math.min(40, (int) (step.length() * 2));
        for (int index = 0; index <= points; index++) {
            Vec3 at = from.add(step.scale(points == 0 ? 0 : index / (double) points));
            level.sendParticles(particle, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** A left-click with a gun reloads it. */
    static void reload(Match match, State state, ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        Gun.of(BedWarsShop.ability(stack)).ifPresent(gun -> {
            if (magazine(state, player).rounds.getOrDefault(gun, gun.clip) < gun.clip) {
                startReload(match, state, player, gun, stack);
            }
        });
    }

    private static void startReload(Match match, State state, ServerPlayer player, Gun gun, ItemStack stack) {
        Magazine magazine = magazine(state, player);
        if (magazine.reloading != null) return;
        magazine.reloading = gun;
        magazine.reloadDone = match.activeTicks() + gun.reloadTicks;
        player.getCooldowns().addCooldown(stack, gun.reloadTicks);
        player.sendSystemMessage(Component.literal("Reloading...").withStyle(ChatFormatting.YELLOW), true);
        player.playSound(SoundEvents.CROSSBOW_LOADING_START.value(), 0.6F, 1.0F);
    }

    /** Finishes reloads, and keeps every gun's bar showing its rounds. */
    static void tick(Match match, State state, int now) {
        for (ServerPlayer player : match.alivePlayers()) {
            Magazine magazine = state.magazines.get(player.getUUID());
            if (magazine == null || magazine.reloading == null || now < magazine.reloadDone) continue;
            Gun gun = magazine.reloading;
            magazine.reloading = null;
            magazine.rounds.put(gun, gun.clip);
            for (ItemStack stack : player.getInventory()) {
                if (BedWarsShop.ability(stack).equals(gun.id)) show(player, stack, gun, gun.clip);
            }
            player.playSound(SoundEvents.CROSSBOW_LOADING_END.value(), 0.6F, 1.0F);
        }
    }

    private static void show(ServerPlayer player, ItemStack stack, Gun gun, int rounds) {
        stack.set(DataComponents.DAMAGE, gun.clip - rounds);
        player.sendSystemMessage(Component.literal(gun.displayName + " ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(rounds + "/" + gun.clip).withStyle(rounds == 0 ? ChatFormatting.RED : ChatFormatting.WHITE)), true);
    }

    /** Rounds left in the player's {@code gun}, for tests and bots. */
    static int rounds(State state, ServerPlayer player, Gun gun) {
        Magazine magazine = state.magazines.get(player.getUUID());
        return magazine == null ? gun.clip : magazine.rounds.getOrDefault(gun, gun.clip);
    }
}
