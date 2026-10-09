package io.github.brainage04.brainage_minigames.game.bedwars;

import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsGame.State;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsGame.TeamState;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsLayout.Bed;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Currency;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsUpgrades.Upgrade;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Bed Wars Ultimate (v2): every player plays one of seven ultimates, chosen with {@code /minigames
 * bedwars ultimate <ultimate>} (saved per player; Kangaroo until they choose). Abilities are used
 * from the ability item in the last hotbar slot (Swordsman and Healer also right-click a sword), and
 * their cooldowns show on that item. Hypixel's announcements give what each ultimate does; ranges,
 * strengths and cooldowns are this mod's choice.
 */
public final class BedWarsUltimates {
    public static final Identifier STORAGE = BrainageMinigames.id("bedwars_ultimate");

    private BedWarsUltimates() {}

    public enum Ultimate {
        KANGAROO("Kangaroo", Items.RABBIT_FOOT, 60,
                "Double-jump (press jump twice). Keeps half your resources on death half the time; Magic Milk for every bed you break."),
        SWORDSMAN("Swordsman", Items.IRON_SWORD, 200,
                "Right-click a sword to dash forward, hurting players in the way; again within five seconds to return. A kill resets the cooldown."),
        HEALER("Healer", Items.GLISTERING_MELON_SLICE, 300,
                "Heals teammates near you; right-click a sword to heal yourself."),
        FROZO("Frozo", Items.PACKED_ICE, 300, "Slows enemies near you; a snowball for every kill, up to 16."),
        BUILDER("Builder", Items.BRICKS, 160,
                "Builds a bridge of your wool ahead (a wall while sneaking); gets wool over time; once a life, covers your bed in wool."),
        DEMOLITION("Demolition", Items.FLINT_AND_STEEL, 200,
                "Burns placed wool joined to the wool you aim at; leaves a lit TNT where you die; a Creeper Egg for every bed you break."),
        GATHERER("Gatherer", Items.ENDER_CHEST, 20,
                "Sometimes doubles diamonds and emeralds you stand on as they spawn; a portable ender chest; your team gets its dearest upgrade free when your bed falls.");

        public final String displayName;
        public final Item icon;
        /** Ticks the ability then rests. */
        final int cooldown;
        public final String description;

        Ultimate(String displayName, Item icon, int cooldown, String description) {
            this.displayName = displayName;
            this.icon = icon;
            this.cooldown = cooldown;
            this.description = description;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Ultimate> of(String id) {
            for (Ultimate ultimate : values()) if (ultimate.id().equals(id)) return Optional.of(ultimate);
            return Optional.empty();
        }
    }

    /** A player's ultimate in a match, and its state. */
    static final class Chosen {
        final Ultimate ultimate;
        /** Swordsman: where the dash began, while it can still be returned to, and until when. */
        Vec3 dashFrom;
        int returnUntil;
        int dashing;
        boolean builtBedCover;
        int woolTimer;
        /** Gatherer's ender chest. */
        final SimpleContainer chest = new SimpleContainer(27);
        /** Kangaroo: the double jump is used until the next landing. */
        boolean jumped;

        Chosen(Ultimate ultimate) {
            this.ultimate = ultimate;
        }
    }

    /** The ultimate the player chose, or Kangaroo. */
    public static Ultimate selected(MinecraftServer server, UUID player) {
        return server.getCommandStorage().get(STORAGE).read(player.toString(), Codec.STRING)
                .flatMap(Ultimate::of).orElse(Ultimate.KANGAROO);
    }

    public static void select(MinecraftServer server, UUID player, Ultimate ultimate) {
        var root = server.getCommandStorage().get(STORAGE);
        root.store(player.toString(), Codec.STRING, ultimate.id());
        server.getCommandStorage().set(STORAGE, root);
    }

    private static Chosen chosen(Match match, State state, ServerPlayer player) {
        return state.ultimates.computeIfAbsent(player.getUUID(), ignored -> new Chosen(selected(match.server(), player.getUUID())));
    }

    /** The ability item in the last hotbar slot. */
    static void equip(Match match, State state, ServerPlayer player) {
        Chosen chosen = chosen(match, state, player);
        chosen.jumped = false;
        ItemStack item = BedWarsShop.tagged(chosen.ultimate.icon, "ultimate", chosen.ultimate.displayName + " Ability (Right Click)");
        item.remove(net.minecraft.core.component.DataComponents.WEAPON);
        player.getInventory().setItem(8, item);
    }

    static InteractionResult use(BedWarsGame game, Match match, State state, ServerPlayer player, ItemStack stack) {
        Chosen chosen = chosen(match, state, player);
        boolean ability = BedWarsShop.ability(stack).equals("ultimate");
        boolean sword = stack.is(ItemTags.SWORDS);
        if (!ability && !(sword && (chosen.ultimate == Ultimate.SWORDSMAN || chosen.ultimate == Ultimate.HEALER))) {
            return InteractionResult.PASS;
        }
        int now = match.activeTicks();
        ItemStack abilityItem = player.getInventory().getItem(8);
        if (chosen.ultimate == Ultimate.SWORDSMAN && chosen.dashFrom != null && now <= chosen.returnUntil) {
            PlayerUtils.teleport(player, player.level(), chosen.dashFrom, player.getYRot());
            chosen.dashFrom = null;
            player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.2F);
            return InteractionResult.SUCCESS;
        }
        if (player.getCooldowns().isOnCooldown(abilityItem)) return InteractionResult.FAIL;
        boolean used = switch (chosen.ultimate) {
            case KANGAROO -> false;
            case SWORDSMAN -> {
                chosen.dashFrom = player.position();
                chosen.returnUntil = now + 100;
                chosen.dashing = 10;
                Vec3 look = player.getLookAngle();
                player.setDeltaMovement(look.x * 1.8, 0.25, look.z * 1.8);
                player.hurtMarked = true;
                yield true;
            }
            case HEALER -> {
                if (sword) {
                    player.heal(4.0F);
                } else {
                    for (ServerPlayer mate : teammatesNear(match, player, 6.0)) {
                        mate.heal(6.0F);
                        mate.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1));
                    }
                }
                player.level().sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.5, player.getZ(), 8, 1.5, 0.5, 1.5, 0.0);
                yield true;
            }
            case FROZO -> {
                for (ServerPlayer enemy : enemiesNear(match, player, 6.0)) {
                    enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2));
                }
                player.level().sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.0, player.getZ(), 40, 3.0, 0.5, 3.0, 0.0);
                yield true;
            }
            case BUILDER -> build(match, player, chosen);
            case DEMOLITION -> demolish(match, player);
            case GATHERER -> {
                player.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> ChestMenu.threeRows(id, inventory, chosen.chest),
                        Component.literal("Ender Chest")));
                yield false;
            }
        };
        if (used) {
            player.getCooldowns().addCooldown(abilityItem, chosen.ultimate.cooldown);
            if (sword) player.getCooldowns().addCooldown(stack, chosen.ultimate.cooldown);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Builder: a three-wide bridge six blocks ahead at the feet, or while sneaking a wall five wide and
     * three high two blocks ahead; on the player's own bed (looking at it within five blocks), once a
     * life, a cover of wool over it.
     */
    private static boolean build(Match match, ServerPlayer player, Chosen chosen) {
        ServerLevel level = player.level();
        DyeColor dye = match.teamOf(player.getUUID()).map(BedWarsGame::dye).orElse(DyeColor.WHITE);
        BlockState wool = Blocks.WOOL.pick(dye).defaultBlockState();
        Direction facing = player.getDirection();
        Direction side = facing.getClockWise();
        BlockPos feet = player.blockPosition();
        BedWarsGame game = (BedWarsGame) match.game();
        State state = game.states.get(match);
        if (state != null && !chosen.builtBedCover) {
            for (Bed bed : state.teams.get(BedWarsGame.teamOf(match, player)).standing) {
                if (bed.foot().closerToCenterThan(player.position(), 5.0)) {
                    BedWarsDreams.defend(match, state, bed, dye, List.of(wool, wool));
                    chosen.builtBedCover = true;
                    return true;
                }
            }
        }
        boolean any = false;
        if (player.isShiftKeyDown()) {
            for (int across = -2; across <= 2; across++) {
                for (int up = 0; up < 3; up++) any |= put(match, level, feet.relative(facing, 2).relative(side, across).above(up), wool);
            }
        } else {
            for (int ahead = 1; ahead <= 6; ahead++) {
                for (int across = -1; across <= 1; across++) any |= put(match, level, feet.below().relative(facing, ahead).relative(side, across), wool);
            }
        }
        if (any) level.playSound(null, feet, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        return any;
    }

    private static boolean put(Match match, ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.getBlockState(pos).isAir() || !match.arena().canBuild(pos)) return false;
        level.setBlock(pos, state, Block.UPDATE_ALL);
        match.markPlaced(pos);
        return true;
    }

    /** Demolition: burns away up to 40 placed wool blocks joined to the wool the player looks at, within eight blocks. */
    private static boolean demolish(Match match, ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(player.getLookAngle().scale(8)),
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        BlockPos start = hit.getBlockPos();
        if (!level.getBlockState(start).is(BlockTags.WOOL) || !match.isPlacedBlock(start)) return false;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(start));
        Set<BlockPos> seen = new HashSet<>(List.of(start));
        int burnt = 0;
        while (!queue.isEmpty() && burnt < 40) {
            BlockPos pos = queue.poll();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.0);
            burnt++;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (seen.add(next) && level.getBlockState(next).is(BlockTags.WOOL) && match.isPlacedBlock(next)
                        && next.closerToCenterThan(Vec3.atCenterOf(start), 8.0)) {
                    queue.add(next);
                }
            }
        }
        level.playSound(null, start, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    static void tick(BedWarsGame game, Match match, State state, int now) {
        for (ServerPlayer player : match.alivePlayers()) {
            if (player.isSpectator()) continue;
            Chosen chosen = chosen(match, state, player);
            switch (chosen.ultimate) {
                case KANGAROO -> kangaroo(player, chosen);
                case SWORDSMAN -> {
                    if (chosen.dashing > 0) {
                        chosen.dashing--;
                        for (ServerPlayer enemy : enemiesNear(match, player, 1.8)) {
                            enemy.invulnerableTime = 0;
                            enemy.hurtServer(player.level(), player.damageSources().playerAttack(player), 4.0F);
                        }
                    }
                }
                case BUILDER -> {
                    if (++chosen.woolTimer >= 100) {
                        chosen.woolTimer = 0;
                        DyeColor dye = match.teamOf(player.getUUID()).map(BedWarsGame::dye).orElse(DyeColor.WHITE);
                        if (player.getInventory().countItem(Items.WOOL.pick(dye)) < 64) {
                            PlayerUtils.giveOrDrop(player, new ItemStack(Items.WOOL.pick(dye)));
                        }
                    }
                }
                default -> {}
            }
        }
    }

    /**
     * Kangaroo's double jump: on the ground the player may fly, so a vanilla client's double-tap of
     * jump asks to start flying; that request becomes a leap instead, once until the next landing.
     */
    private static void kangaroo(ServerPlayer player, Chosen chosen) {
        var abilities = player.getAbilities();
        if (player.onGround()) {
            chosen.jumped = false;
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                player.onUpdateAbilities();
            }
        } else if (abilities.flying) {
            abilities.flying = false;
            abilities.mayfly = false;
            player.onUpdateAbilities();
            if (!chosen.jumped) {
                chosen.jumped = true;
                Vec3 look = player.getLookAngle();
                player.setDeltaMovement(look.x * 0.9, 0.85, look.z * 0.9);
                player.hurtMarked = true;
                player.resetFallDistance();
                player.level().playSound(null, player.blockPosition(), SoundEvents.RABBIT_JUMP, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }

    /** Frozo gets a snowball for a kill (up to 16); a Swordsman's cooldown ends. */
    static void kill(Match match, State state, ServerPlayer killer) {
        Chosen chosen = state.ultimates.get(killer.getUUID());
        if (chosen == null) return;
        if (chosen.ultimate == Ultimate.FROZO && killer.getInventory().countItem(Items.SNOWBALL) < 16) {
            PlayerUtils.giveOrDrop(killer, new ItemStack(Items.SNOWBALL));
        }
        if (chosen.ultimate == Ultimate.SWORDSMAN) {
            var cooldowns = killer.getCooldowns();
            cooldowns.removeCooldown(cooldowns.getCooldownGroup(killer.getInventory().getItem(8)));
            for (ItemStack stack : killer.getInventory()) {
                if (stack.is(ItemTags.SWORDS)) cooldowns.removeCooldown(cooldowns.getCooldownGroup(stack));
            }
        }
    }

    /** Demolition leaves a lit TNT where they die. */
    static void death(BedWarsGame game, Match match, State state, ServerPlayer victim) {
        Chosen chosen = state.ultimates.get(victim.getUUID());
        if (chosen == null || chosen.ultimate != Ultimate.DEMOLITION) return;
        ServerLevel level = match.arena().level();
        PrimedTnt tnt = new PrimedTnt(level, victim.getX(), victim.getY(), victim.getZ(), victim);
        tnt.setFuse(60);
        tnt.addTag(BedWarsGame.ENTITY_TAG);
        level.addFreshEntity(tnt);
        state.tracked.add(new BedWarsGame.Tracked(tnt, "tnt", BedWarsGame.teamOf(match, victim), victim.getUUID(), match.activeTicks() + 40));
    }

    /** Kangaroo keeps its resources on half its deaths. */
    static boolean keepsResources(State state, ServerPlayer victim) {
        Chosen chosen = state.ultimates.get(victim.getUUID());
        return chosen != null && chosen.ultimate == Ultimate.KANGAROO && ThreadLocalRandom.current().nextBoolean();
    }

    /**
     * A Kangaroo who breaks a bed gets Magic Milk and a Demolition a Creeper Egg; when a team's bed
     * falls to another player, a Gatherer's team gets the dearest upgrade it can still buy, free.
     */
    static void bedBroken(BedWarsGame game, Match match, State state, Bed bed, ServerPlayer breaker) {
        if (breaker != null) {
            Chosen chosen = state.ultimates.get(breaker.getUUID());
            if (chosen != null && chosen.ultimate == Ultimate.KANGAROO) {
                PlayerUtils.giveOrDrop(breaker, BedWarsShop.stack(BedWarsShop.find("magic_milk").orElseThrow(), DyeColor.WHITE,
                        breaker.registryAccess()));
            }
            if (chosen != null && chosen.ultimate == Ultimate.DEMOLITION) PlayerUtils.giveOrDrop(breaker, creeperEgg());
        }
        TeamState team = state.teams.get(bed.team());
        boolean gatherer = BedWarsGame.members(match, bed.team()).stream()
                .anyMatch(player -> state.ultimates.containsKey(player.getUUID())
                        && state.ultimates.get(player.getUUID()).ultimate == Ultimate.GATHERER);
        if (breaker == null || team == null || !gatherer) return;
        Upgrade best = null;
        int bestCost = 0;
        for (Upgrade upgrade : Upgrade.values()) {
            int tier = team.level(upgrade) + 1;
            if (tier > upgrade.tiers() || !game.offers(upgrade)) continue;
            int cost = upgrade.cost(state.prices, tier);
            if (cost > bestCost) {
                bestCost = cost;
                best = upgrade;
            }
        }
        if (best == null) return;
        team.upgrades.put(best, team.level(best) + 1);
        for (ServerPlayer member : BedWarsGame.members(match, bed.team())) {
            game.applyUpgrades(match, state, member);
            member.sendSystemMessage(Component.literal("Gatherer: your team gets " + best.nameAt(team.level(best)) + " free!")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    /** Demolition's Creeper Egg: a creeper of its team that hunts the nearest enemy and blows up placed blocks. */
    static ItemStack creeperEgg() {
        return BedWarsShop.tagged(Items.CREEPER_SPAWN_EGG, "creeper_egg", "Creeper Egg (Right Click)");
    }

    /** A Gatherer standing on a diamond or emerald generator as it spawns gets a second one, one time in three. */
    static void dropped(Match match, State state, Vec3 position, Currency currency) {
        if (currency != Currency.DIAMOND && currency != Currency.EMERALD) return;
        for (ServerPlayer player : match.alivePlayers()) {
            Chosen chosen = state.ultimates.get(player.getUUID());
            if (chosen == null || chosen.ultimate != Ultimate.GATHERER || player.position().distanceTo(position) > 2.0) continue;
            if (ThreadLocalRandom.current().nextInt(3) == 0) {
                ItemEntity extra = new ItemEntity(player.level(), position.x, position.y + 0.1, position.z, new ItemStack(currency.item), 0, 0, 0);
                player.level().addFreshEntity(extra);
            }
            return;
        }
    }

    private static List<ServerPlayer> teammatesNear(Match match, ServerPlayer player, double range) {
        int team = BedWarsGame.teamOf(match, player);
        return match.alivePlayers().stream().filter(other -> !other.isSpectator() && BedWarsGame.teamOf(match, other) == team
                && other.distanceTo(player) <= range).toList();
    }

    private static List<ServerPlayer> enemiesNear(Match match, ServerPlayer player, double range) {
        int team = BedWarsGame.teamOf(match, player);
        AABB area = player.getBoundingBox().inflate(range);
        return match.alivePlayers().stream().filter(other -> !other.isSpectator() && BedWarsGame.teamOf(match, other) != team
                && area.intersects(other.getBoundingBox())).toList();
    }
}
