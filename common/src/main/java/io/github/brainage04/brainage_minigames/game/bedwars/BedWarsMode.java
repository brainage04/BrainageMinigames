package io.github.brainage04.brainage_minigames.game.bedwars;

import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsGame.State;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsLayout.Bed;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Cost;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Currency;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Bed Wars variants: the core game, 40v40 Castle and the Dream modes. Each mode is a {@link
 * BedWarsGame} of its own id; the methods here are the points where a mode's rules differ from the
 * core game's.
 */
public enum BedWarsMode {
    /** Solo, Doubles, 3v3v3v3, 4v4v4v4 and 4v4. */
    CORE("bedwars", 1.0, 0),
    /** 40v40 Castle (v2): two teams of up to 40, three beds each, the Banker and streak powers. */
    CASTLE("bedwars_castle", 1.0, 0) {
        @Override
        void onStart(BedWarsGame game, Match match, State state) {
            BedWarsCastle.start(game, match, state);
        }

        @Override
        void tick(BedWarsGame game, Match match, State state, int now) {
            BedWarsCastle.tick(game, match, state, now);
        }

        @Override
        void onBedBroken(BedWarsGame game, Match match, State state, Bed bed, @Nullable ServerPlayer breaker) {
            BedWarsCastle.bedBroken(match, state, bed, breaker);
        }

        @Override
        boolean allowPlace(BedWarsGame game, Match match, State state, ServerPlayer player, BlockPos pos, BlockState block) {
            return BedWarsCastle.allowPlace(match, state, player, pos, block);
        }

        @Override
        InteractionResult onUseItem(BedWarsGame game, Match match, State state, ServerPlayer player, InteractionHand hand,
                ItemStack stack) {
            return BedWarsCastle.useItem(game, match, state, player, stack);
        }

        @Override
        void onKill(BedWarsGame game, Match match, State state, ServerPlayer killer, ServerPlayer victim, boolean finalKill) {
            BedWarsCastle.points(match, state, killer, finalKill ? 5 : 2, finalKill ? "Final kill" : "Kill");
        }

        @Override
        void onDeath(BedWarsGame game, Match match, State state, ServerPlayer victim) {
            BedWarsCastle.endPowers(match, state, victim);
        }
    },
    /**
     * Rush (v2): generators at their highest tier, beds defended from the start, wool that builds
     * itself out five blocks (left-click with wool to turn it off and on), Speed, doubled potion
     * prices, no obsidian and two dragons per team.
     */
    RUSH("bedwars", 3.0, 0) {
        @Override
        void onStart(BedWarsGame game, Match match, State state) {
            BedWarsDreams.rushStart(match, state);
        }

        @Override
        void onEquip(BedWarsGame game, Match match, State state, ServerPlayer player) {
            BedWarsDreams.rushSpeed(player);
        }

        @Override
        void tick(BedWarsGame game, Match match, State state, int now) {
            if (now % 20 == 0) {
                for (ServerPlayer player : match.alivePlayers()) if (!player.isSpectator()) BedWarsDreams.rushSpeed(player);
            }
        }

        @Override
        void onBlockPlaced(BedWarsGame game, Match match, State state, ServerPlayer player, BlockPos pos) {
            BedWarsDreams.rushBridge(match, state, player, pos);
        }

        @Override
        void onSwing(BedWarsGame game, Match match, State state, ServerPlayer player) {
            BedWarsDreams.rushToggle(state, player);
        }

        @Override
        boolean obsidian(Match match) {
            return false;
        }

        /** Potions cost double; ender pearls half (this mod's choice of how much cheaper). */
        @Override
        Cost price(Entry entry, Cost cost) {
            if (entry.category() == BedWarsShop.Category.POTIONS) return new Cost(cost.currency(), cost.amount() * 2);
            if (entry.id().equals("ender_pearl")) return new Cost(cost.currency(), Math.max(1, cost.amount() / 2));
            return cost;
        }
    },
    /** Voidless: the maps stand on solid ground instead of the void, and every bed starts defended. */
    VOIDLESS("bedwars_voidless", 1.0, 0) {
        @Override
        void onStart(BedWarsGame game, Match match, State state) {
            BedWarsDreams.defendAll(match, state);
        }
    },
    /** Swappage: every one to two minutes teams swap places, player for player. */
    SWAPPAGE("bedwars", 1.0, 0) {
        @Override
        void onStart(BedWarsGame game, Match match, State state) {
            state.modeTimer = BedWarsDreams.nextSwap(match);
        }

        @Override
        void tick(BedWarsGame game, Match match, State state, int now) {
            if (--state.modeTimer > 0) return;
            state.modeTimer = BedWarsDreams.nextSwap(match);
            BedWarsDreams.swap(match, state);
        }
    },
    /**
     * One Block: eight tiny islands of two blocks and a bed, no shops or generators; every player gets
     * a random item or block every three seconds, every four after ten minutes.
     */
    ONE_BLOCK("bedwars_one_block", 1.0, 0) {
        @Override
        boolean shops() {
            return false;
        }

        @Override
        void tick(BedWarsGame game, Match match, State state, int now) {
            if (now > 0 && now % BedWarsDreams.oneBlockInterval(now) == 0) BedWarsDreams.oneBlockItems(match, state);
        }
    },
    /**
     * Lucky Blocks (v2): generators also drop lucky blocks (Normal with iron, Promising with gold,
     * Fortunate and Offensive with diamonds, Miracle with emeralds); placed and broken, each gives a
     * random item or sets off an event.
     */
    LUCKY_BLOCKS("bedwars", 1.0, 0) {
        @Override
        void onDropped(BedWarsGame game, Match match, State state, Vec3 position, Currency currency) {
            BedWarsDreams.Lucky lucky = BedWarsDreams.luckyDrop(currency);
            if (lucky == null) return;
            ServerLevel level = match.arena().level();
            ItemEntity item = new ItemEntity(level, position.x, position.y + 0.1, position.z, lucky.item(), 0.0, 0.0, 0.0);
            level.addFreshEntity(item);
        }

        @Override
        @Nullable Boolean allowBreak(BedWarsGame game, Match match, State state, ServerPlayer player, BlockPos pos, BlockState block) {
            BedWarsDreams.Lucky lucky = BedWarsDreams.Lucky.of(block);
            if (lucky == null || !match.isPlacedBlock(pos)) return null;
            BedWarsDreams.open(match, state, player, pos, lucky);
            return false;
        }
    },
    /**
     * Ultimate (v2): every player picks one of seven ultimates (Kangaroo, Swordsman, Healer, Frozo,
     * Builder, Demolition, Gatherer), each with an ability and perks.
     */
    ULTIMATE("bedwars", 1.0, 0) {
        @Override
        void onEquip(BedWarsGame game, Match match, State state, ServerPlayer player) {
            BedWarsUltimates.equip(match, state, player);
        }

        @Override
        void tick(BedWarsGame game, Match match, State state, int now) {
            BedWarsUltimates.tick(game, match, state, now);
        }

        @Override
        InteractionResult onUseItem(BedWarsGame game, Match match, State state, ServerPlayer player, InteractionHand hand,
                ItemStack stack) {
            return BedWarsUltimates.use(game, match, state, player, stack);
        }

        @Override
        void onKill(BedWarsGame game, Match match, State state, ServerPlayer killer, ServerPlayer victim, boolean finalKill) {
            BedWarsUltimates.kill(match, state, killer);
        }

        @Override
        void onDeath(BedWarsGame game, Match match, State state, ServerPlayer victim) {
            BedWarsUltimates.death(game, match, state, victim);
        }

        @Override
        boolean keepsResources(Match match, State state, ServerPlayer victim) {
            return BedWarsUltimates.keepsResources(state, victim);
        }

        @Override
        void onBedBroken(BedWarsGame game, Match match, State state, Bed bed, @Nullable ServerPlayer breaker) {
            BedWarsUltimates.bedBroken(game, match, state, bed, breaker);
        }

        @Override
        void onDropped(BedWarsGame game, Match match, State state, Vec3 position, Currency currency) {
            BedWarsUltimates.dropped(match, state, position, currency);
        }
    },
    /**
     * Armed: guns from the start (a free Pistol) and in the shop (Magnum, Rifle, SMG, Flamethrower,
     * Shotgun) in place of bows; right-click fires, left-click reloads. No obsidian.
     */
    ARMED("bedwars", 1.0, 0) {
        @Override
        void onEquip(BedWarsGame game, Match match, State state, ServerPlayer player) {
            BedWarsGuns.equip(player);
        }

        @Override
        InteractionResult onUseItem(BedWarsGame game, Match match, State state, ServerPlayer player, InteractionHand hand,
                ItemStack stack) {
            return BedWarsGuns.fire(game, match, state, player, stack);
        }

        @Override
        void onSwing(BedWarsGame game, Match match, State state, ServerPlayer player) {
            BedWarsGuns.reload(match, state, player);
        }

        @Override
        void tick(BedWarsGame game, Match match, State state, int now) {
            BedWarsGuns.tick(match, state, now);
        }

        @Override
        boolean obsidian(Match match) {
            return false;
        }

        @Override
        boolean sells(Entry entry) {
            return entry.category() != BedWarsShop.Category.RANGED || BedWarsGuns.isGun(entry.id());
        }
    };

    /** The directory under {@code structure/maps/} whose maps the mode plays on. */
    public final String mapDirectory;
    /** How much faster the island generators run than the core game's. */
    final double forgeSpeed;
    /** Dragons every team gets at Sudden Death on top of its one and Dragon Buff's. */
    final int extraDragons;

    BedWarsMode(String mapDirectory, double forgeSpeed, int extraDragons) {
        this.mapDirectory = mapDirectory;
        this.forgeSpeed = forgeSpeed;
        this.extraDragons = extraDragons;
    }

    /** Whether the mode has shopkeepers and generators (One Block has neither). */
    boolean shops() {
        return true;
    }

    /** After the beds, shopkeepers and generators are in place and before players are equipped. */
    void onStart(BedWarsGame game, Match match, State state) {}

    /** Every tick of the active match, after the core game's own. */
    void tick(BedWarsGame game, Match match, State state, int now) {}

    void onBedBroken(BedWarsGame game, Match match, State state, Bed bed, @Nullable ServerPlayer breaker) {}

    /** After a player got their armour and tools at the start or a respawn. */
    void onEquip(BedWarsGame game, Match match, State state, ServerPlayer player) {}

    /** The mode's own placement rules, after the core game's. */
    boolean allowPlace(BedWarsGame game, Match match, State state, ServerPlayer player, BlockPos pos, BlockState block) {
        return true;
    }

    /** Whether the block breaks (true or false), or null to leave it to the core game's rules. */
    @Nullable Boolean allowBreak(BedWarsGame game, Match match, State state, ServerPlayer player, BlockPos pos, BlockState block) {
        return null;
    }

    void onBlockPlaced(BedWarsGame game, Match match, State state, ServerPlayer player, BlockPos pos) {}

    void onSwing(BedWarsGame game, Match match, State state, ServerPlayer player) {}

    /** After a generator dropped one of {@code currency} at {@code position}. */
    void onDropped(BedWarsGame game, Match match, State state, Vec3 position, Currency currency) {}

    void onKill(BedWarsGame game, Match match, State state, ServerPlayer killer, ServerPlayer victim, boolean finalKill) {}

    /** Before a killed player respawns or is out. */
    void onDeath(BedWarsGame game, Match match, State state, ServerPlayer victim) {}

    /** Whether a killed player keeps their iron, gold, diamonds and emeralds instead of the killer taking them. */
    boolean keepsResources(Match match, State state, ServerPlayer victim) {
        return false;
    }

    /** Whether the shop sells obsidian: not in 4v4, as Hypixel's. */
    boolean obsidian(Match match) {
        return match.teams().size() > 2;
    }

    /** Whether the shop sells {@code entry} in this mode at all (beyond obsidian). */
    boolean sells(Entry entry) {
        return !BedWarsGuns.isGun(entry.id());
    }

    /** The price of {@code entry} in this mode, given the core game's. */
    Cost price(Entry entry, Cost cost) {
        return cost;
    }

    /** Uses of items the mode handles itself; {@link InteractionResult#PASS} leaves them to the core game. */
    InteractionResult onUseItem(BedWarsGame game, Match match, State state, ServerPlayer player, InteractionHand hand,
            ItemStack stack) {
        return InteractionResult.PASS;
    }
}
