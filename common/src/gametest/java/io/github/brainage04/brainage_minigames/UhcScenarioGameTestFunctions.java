package io.github.brainage04.brainage_minigames;

import static io.github.brainage04.brainage_minigames.UhcProgressionGameTestFunctions.check;
import static io.github.brainage04.brainage_minigames.UhcProgressionGameTestFunctions.setTicks;
import static io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions.chatCount;
import static io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions.command;
import static io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions.hit;

import io.github.brainage04.brainage_minigames.UhcSettingsGameTestFunctions.Fixture;
import io.github.brainage04.brainage_minigames.game.AntiJanitor;
import io.github.brainage04.brainage_minigames.game.DeathLoot;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.uhc.MeetupGame;
import io.github.brainage04.brainage_minigames.game.uhc.NaturalArena;
import io.github.brainage04.brainage_minigames.game.uhc.UhcArena;
import io.github.brainage04.brainage_minigames.game.uhc.UhcCrafting;
import io.github.brainage04.brainage_minigames.game.uhc.UhcGame;
import io.github.brainage04.brainage_minigames.game.uhc.UhcScenarioRules;
import io.github.brainage04.brainage_minigames.game.uhc.UhcScenarios;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The UHC scenario gamerules (Time Bomb, No Clean, Safeloot, backpacks, Second Chance) and Meetup's adaptive border and kit rerolls. */
public final class UhcScenarioGameTestFunctions {
    private UhcScenarioGameTestFunctions() {}

    @FunctionalInterface
    private interface Action {
        void run(Fixture fixture) throws Exception;
    }

    /**
     * Opens a started match with {@code configure}'s rules and runs {@code action} a second later, once the chunks
     * around the players, kept loaded until the test ends, track entities; the fixture closes when the test ends.
     */
    private static void withScenario(GameTestHelper context, Minigame game, TeamLayout layout, int players,
            Consumer<MinecraftServer> configure, Action action) {
        Fixture f = new Fixture(context, game, false, layout, players, false, true, server -> {
            server.getGameRules().set(AntiJanitor.ENABLED, false, server);
            configure.accept(server);
        });
        io.github.brainage04.brainage_minigames.game.uhc.UhcSpawnGameTestFunctions.awaitReady(context, f.match, () -> {
            if (f.match.phase() == MatchPhase.COUNTDOWN && f.match.settings().get(GameSetting.COUNTDOWN_SECONDS) == 0) {
                MatchManager.tick();
            }
            for (ServerPlayer player : f.players) {
                player.hasChangedDimension();
                player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
                keepLoaded(f, player.level(), player.blockPosition());
            }
            context.runAfterDelay(20, () -> run(() -> action.run(f)));
        });
    }

    /** Keeps the chunks around {@code pos} loaded with their entities until the test ends. */
    private static void keepLoaded(Fixture f, ServerLevel level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int x = (pos.getX() >> 4) + dx, z = (pos.getZ() >> 4) + dz;
            if (level.setChunkForced(x, z, true)) f.cleanup.add(() -> level.setChunkForced(x, z, false));
        }
    }

    @FunctionalInterface
    private interface Step {
        void run() throws Exception;
    }

    private static void run(Step step) {
        try {
            step.run();
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static <T> void rule(MinecraftServer server, net.minecraft.world.level.gamerules.GameRule<T> rule, T value) {
        server.getGameRules().set(rule, value, server);
    }

    /** Clears a 7x4x7 room on a stone floor at the surface near {@code x, z} and returns its floor-level centre. */
    private static BlockPos room(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (BlockPos pos : BlockPos.betweenClosed(x - 4, y - 1, z - 4, x + 4, y - 1, z + 4)) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(x - 4, y, z - 4, x + 4, y + 4, z + 4)) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        return new BlockPos(x, y, z);
    }

    private static void place(ServerPlayer player, BlockPos pos) {
        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static List<ItemEntity> drops(ServerPlayer victim, Item item) {
        return victim.level().getEntitiesOfClass(ItemEntity.class, victim.getBoundingBox().inflate(4),
                entity -> entity.getItem().is(item));
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().countItem(item);
    }

    /**
     * Time Bomb: the loot goes into a double chest at the death spot with a golden head under a
     * countdown, which explodes when the fuse runs out, hurting players nearby and breaking blocks
     * inside the border but not outside it. A player the blast kills leaves a Time Bomb of their own.
     */
    public static void timeBomb(GameTestHelper context) {
        withScenario(context, Minigames.MEETUP, TeamLayout.FREE_FOR_ALL, 3,
                server -> rule(server, UhcScenarioRules.TIME_BOMB_SECONDS, 2), f -> {
            ServerPlayer killer = f.players.get(0), victim = f.players.get(1);
            ServerLevel level = victim.level();
            var border = ((NaturalArena) f.match.arena()).border();
            int edge = (int) Math.floor(border.getMaxX()) - 1;
            BlockPos death = room(level, edge - 2, (int) border.getCenterZ());
            keepLoaded(f, level, death);
            place(victim, death);
            place(killer, death.south(4));
            place(f.players.get(2), death.north(2));
            BlockPos inside = death.west(2), outside = death.east(3);
            check(border.isWithinBounds(inside) && !border.isWithinBounds(outside), "test blocks are not either side of the border");
            level.setBlockAndUpdate(inside, Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(outside, Blocks.DIRT.defaultBlockState());
            victim.getInventory().clearContent();
            victim.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
            // The players' new chunks track entities once their view has loaded there.
            context.runAfterDelay(40, () -> run(() -> explode(context, f, killer, victim, f.players.get(2), death, inside, outside)));
        });
    }

    private static void explode(GameTestHelper context, Fixture f, ServerPlayer killer, ServerPlayer victim,
            ServerPlayer bystander, BlockPos death, BlockPos inside, BlockPos outside) {
        ServerLevel level = victim.level();
        place(victim, death);
        place(killer, death.south(4));
        place(bystander, death.north(2));
        hit(victim, killer, Float.MAX_VALUE);
        check(victim.isSpectator() && !f.match.isAlive(victim.getUUID()), "the victim was not eliminated");
        Container chest = ChestBlock.getContainer((ChestBlock) Blocks.CHEST, level.getBlockState(death), level, death, true);
        check(chest != null && chest.getContainerSize() == 54, "no double chest at the death spot " + death);
        check(chest.countItem(Items.DIAMOND) == 5, "the chest lost the victim's items");
        boolean head = false;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            head |= UhcCrafting.kind(chest.getItem(slot)).equals("golden_head");
        }
        check(head, "the chest holds no golden head");
        check(drops(victim, Items.DIAMOND).isEmpty(), "items were also dropped on the ground");
        List<Display.TextDisplay> countdown = level.getEntitiesOfClass(Display.TextDisplay.class,
                new AABB(death).inflate(3), display -> display.entityTags().contains(UhcScenarios.TIME_BOMB_TAG));
        check(countdown.size() == 1 && ((io.github.brainage04.brainage_minigames.mixin.TextDisplayAccess) countdown.getFirst())
                        .brainage_minigames$getText().getString().contains("2s"),
                "no countdown above the chest");
        check(UhcScenarios.timeBombs(level).containsKey(death), "the bomb is not listed for bots");
        check(chatCount(f.channels.get(2), "Time Bomb at") == 1, "the bomb was not announced");
        float health = killer.getHealth();
        bystander.setHealth(1.0F);
        context.runAfterDelay(41, () -> {
            check(!level.getBlockState(death).is(Blocks.CHEST) && !level.getBlockState(death.east()).is(Blocks.CHEST),
                    "the chest did not explode");
            check(countdown.getFirst().isRemoved(), "the countdown stayed after the explosion");
            check(!UhcScenarios.timeBombs(level).containsKey(death), "the exploded bomb is still listed");
            check(bystander.isSpectator() && UhcScenarios.timeBombs(level).size() == 1,
                    "the player the blast killed left no Time Bomb");
            check(drops(victim, Items.DIAMOND).isEmpty(), "the explosion spilled the chest's items");
            check(killer.getHealth() < health, "the explosion did not hurt a player beside it");
            check(level.getBlockState(inside).isAir(), "the explosion did not break a block inside the border");
            check(level.getBlockState(outside).is(Blocks.DIRT), "the explosion broke a block outside the border");
            context.succeed();
        });
    }

    /** No Clean: after a kill, players cannot hurt the killer until the protection runs out or the killer attacks. */
    public static void noClean(GameTestHelper context) {
        withScenario(context, Minigames.MEETUP, TeamLayout.FREE_FOR_ALL, 4,
                server -> rule(server, UhcScenarioRules.NO_CLEAN_SECONDS, 30), f -> {
            ServerPlayer killer = f.players.get(0), victim = f.players.get(1), third = f.players.get(2), fourth = f.players.get(3);
            hit(victim, killer, Float.MAX_VALUE);
            check(UhcScenarios.noCleanProtected(killer), "the killer is not protected");
            float health = killer.getHealth();
            killer.invulnerableTime = 0;
            killer.hurtServer(killer.level(), killer.damageSources().playerAttack(third), 2);
            check(killer.getHealth() == health, "a player hurt the protected killer");
            killer.invulnerableTime = 0;
            killer.hurtServer(killer.level(), killer.damageSources().fall(), 2);
            check(killer.getHealth() < health, "No Clean blocked fall damage");
            hit(third, killer, 2);
            check(!UhcScenarios.noCleanProtected(killer), "attacking a player did not end the protection");
            check(chatCount(f.channels.getFirst(), "No Clean ended") == 1, "the killer was not told the protection ended");
            health = killer.getHealth();
            hit(killer, third, 2);
            check(killer.getHealth() < health, "the protection lasted after the killer attacked");
            rule(f.server, UhcScenarioRules.NO_CLEAN_SECONDS, 1);
            hit(third, killer, Float.MAX_VALUE);
            float protectedHealth = killer.getHealth();
            killer.invulnerableTime = 0;
            killer.hurtServer(killer.level(), killer.damageSources().playerAttack(fourth), 2);
            check(killer.getHealth() == protectedHealth, "the second kill gave no protection");
            context.runAfterDelay(21, () -> {
                check(!UhcScenarios.noCleanProtected(killer), "the protection did not run out");
                hit(killer, fourth, 2);
                check(killer.getHealth() < protectedHealth, "the expired protection still blocked damage");
                context.succeed();
            });
        });
    }

    /** Safeloot: a kill's drops and Time Bomb chest belong to the killer's team until the claim runs out. */
    public static void safeloot(GameTestHelper context) {
        withScenario(context, Minigames.MEETUP, TeamLayout.FREE_FOR_ALL, 4,
                server -> rule(server, UhcScenarioRules.SAFELOOT_SECONDS, 1), f -> {
            ServerPlayer killer = f.players.get(0), victim = f.players.get(1), third = f.players.get(2), last = f.players.get(3);
            victim.getInventory().clearContent();
            victim.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
            victim.getInventory().setItem(1, new ItemStack(Items.EMERALD, 3));
            int thirdEmeralds = count(third, Items.EMERALD), killerDiamonds = count(killer, Items.DIAMOND);
            hit(victim, killer, Float.MAX_VALUE);
            List<ItemEntity> diamonds = drops(victim, Items.DIAMOND), emeralds = drops(victim, Items.EMERALD);
            check(diamonds.size() == 1 && emeralds.size() == 1, "the victim's items were not dropped");
            diamonds.getFirst().setNoPickUpDelay();
            emeralds.getFirst().setNoPickUpDelay();
            emeralds.getFirst().playerTouch(third);
            check(!emeralds.getFirst().isRemoved() && count(third, Items.EMERALD) == thirdEmeralds,
                    "another player picked up the killer's loot");
            check(!net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(
                    new net.minecraft.world.SimpleContainer(5), emeralds.getFirst()), "a hopper collected the killer's loot");
            diamonds.getFirst().playerTouch(killer);
            check(count(killer, Items.DIAMOND) == killerDiamonds + 3, "the killer could not pick up their loot");
            rule(f.server, UhcScenarioRules.SAFELOOT_SECONDS, 30);
            rule(f.server, UhcScenarioRules.TIME_BOMB_SECONDS, 30);
            BlockPos death = last.blockPosition();
            hit(last, killer, Float.MAX_VALUE);
            var half = (ChestBlockEntity) last.level().getBlockEntity(death);
            check(half != null && half.canOpen(killer) && !half.canOpen(third), "the Time Bomb chest was not the killer's");
            check(DeathLoot.protectedChest(last.level(), death), "the claimed chest could be broken");
            context.runAfterDelay(21, () -> {
                emeralds.getFirst().playerTouch(third);
                check(count(third, Items.EMERALD) == thirdEmeralds + 3, "the claim did not run out");
                context.succeed();
            });
        });
    }

    /** Team backpacks: teammates share one, solo players have none, and the last member's death drops it. */
    public static void backpack(GameTestHelper context) {
        withScenario(context, Minigames.MEETUP, TeamLayout.parse("2v1").orElseThrow(), 3,
                server -> rule(server, UhcScenarioRules.TEAM_BACKPACK, true), f -> {
            ServerPlayer first = f.players.get(0), second = f.players.get(1), solo = f.players.get(2);
            check(f.match.teamOf(first.getUUID()).equals(f.match.teamOf(second.getUUID())), "fixture teams differ");
            check(command(first, "backpack") == 1 && first.containerMenu instanceof ChestMenu menu
                    && menu.getRowCount() == 3, "/backpack did not open a 27-slot chest");
            first.containerMenu.getSlot(0).set(new ItemStack(Items.NETHER_STAR));
            first.closeContainer();
            check(command(second, "bp") == 1 && second.containerMenu.getSlot(0).getItem().is(Items.NETHER_STAR),
                    "the teammate does not share the backpack");
            second.closeContainer();
            check(command(solo, "backpack") == 0 && solo.containerMenu == solo.inventoryMenu, "a solo player opened a backpack");
            hit(first, solo, Float.MAX_VALUE);
            check(drops(first, Items.NETHER_STAR).isEmpty(), "the backpack dropped while a teammate lived");
            hit(second, solo, Float.MAX_VALUE);
            check(drops(second, Items.NETHER_STAR).size() == 1, "the last member's death did not drop the backpack: first alive "
                    + f.match.isAlive(first.getUUID()) + ", second alive " + f.match.isAlive(second.getUUID())
                    + ", stars within 16 blocks " + second.level().getEntitiesOfClass(ItemEntity.class,
                            second.getBoundingBox().inflate(16), entity -> entity.getItem().is(Items.NETHER_STAR)).size());
            context.succeed();
        });
    }

    /** Second Chance: a death before PvP brings the player back once with their items; later deaths are final. */
    public static void secondChance(GameTestHelper context) {
        withScenario(context, Minigames.UHC, TeamLayout.FREE_FOR_ALL, 3, server -> {
            rule(server, UhcScenarioRules.SECOND_CHANCE, true);
            SettingsStorage.set(server, Minigames.UHC, UhcGame.GRACE_PERIOD, 10);
        }, f -> {
            ServerPlayer first = f.players.get(0), second = f.players.get(1);
            UhcArena arena = (UhcArena) f.match.arena();
            first.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
            first.hurtServer(first.level(), first.damageSources().genericKill(), Float.MAX_VALUE);
            check(f.match.isAlive(first.getUUID()) && !first.isSpectator(), "a death before PvP was final");
            check(count(first, Items.DIAMOND) == 7, "the player lost their items");
            check(first.getHealth() == first.getMaxHealth() && first.level() == arena.level()
                    && arena.border().isWithinBounds(first.blockPosition()), "the player did not come back inside the border");
            first.invulnerableTime = 0;
            first.hurtServer(first.level(), first.damageSources().genericKill(), Float.MAX_VALUE);
            check(!f.match.isAlive(first.getUUID()) && first.isSpectator(), "the second chance was given twice");
            setTicks(f.match, f.match.settings().minutesInTicks(UhcGame.GRACE_PERIOD));
            second.hurtServer(second.level(), second.damageSources().genericKill(), Float.MAX_VALUE);
            check(!f.match.isAlive(second.getUUID()), "a death after PvP was enabled came back");
            context.succeed();
        });
    }

    /** Meetup's adaptive border shrinks at a player count or a time, then hurts survivors, past the time limit. */
    public static void adaptiveBorder(GameTestHelper context) {
        withScenario(context, Minigames.MEETUP, TeamLayout.FREE_FOR_ALL, 4, server -> {
            rule(server, UhcScenarioRules.MEETUP_ADAPTIVE_BORDER, true);
            SettingsStorage.set(server, Minigames.MEETUP, MeetupGame.ADAPTIVE_FIRST_PLAYERS, 3);
            SettingsStorage.set(server, Minigames.MEETUP, MeetupGame.ADAPTIVE_SECOND_PLAYERS, 1);
        }, f -> {
            NaturalArena arena = (NaturalArena) f.match.arena();
            int start = arena.targetSize();
            check(start > 50 && arena.size() == start, "the border did not start above the first stage, unshrinking");
            hit(f.players.get(3), f.players.get(0), Float.MAX_VALUE);
            context.runAfterDelay(2, () -> {
                check(arena.targetSize() == 50, "3 players left did not shrink the border to 50: " + arena.targetSize());
                try {
                    setTicks(f.match, 900 * 20 - 1);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                context.runAfterDelay(2, () -> {
                    check(arena.targetSize() == 25, "15 minutes did not shrink the border to 25: " + arena.targetSize());
                    check(f.match.phase() == MatchPhase.ACTIVE, "the 15-minute time limit ended the adaptive match");
                    Map<UUID, Float> health = new HashMap<>();
                    for (ServerPlayer player : f.match.alivePlayers()) {
                        player.setHealth(player.getMaxHealth());
                        health.put(player.getUUID(), player.getHealth());
                    }
                    try {
                        setTicks(f.match, 1500 * 20 - 1);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                    context.runAfterDelay(205, () -> {
                        for (ServerPlayer player : f.match.alivePlayers()) {
                            check(player.getHealth() < health.get(player.getUUID()), "a survivor took no random damage");
                        }
                        context.succeed();
                    });
                });
            });
        });
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> rolledKit(Match match, ServerPlayer player) throws Exception {
        Field field = Match.class.getDeclaredField("rolledKits");
        field.setAccessible(true);
        return List.copyOf(((Map<UUID, List<ItemStack>>) field.get(match)).get(player.getUUID()));
    }

    /** Meetup's random kit is shown during the countdown, rerolled as often as kit_rerolls allows, and given as shown. */
    public static void kitReroll(GameTestHelper context) {
        withScenario(context, Minigames.MEETUP, TeamLayout.FREE_FOR_ALL, 2, server -> SettingsStorage.set(
                server, Minigames.MEETUP, Minigames.MEETUP.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), 3), f -> {
            ServerPlayer player = f.players.getFirst();
            check(f.match.phase() == MatchPhase.COUNTDOWN, "the match is not counting down");
            check(chatCount(f.channels.getFirst(), "Your kit: ") == 1 && f.match.rerollsLeft(player.getUUID()) == 1,
                    "the countdown did not show the kit with a reroll");
            check(command(player, "minigames reroll") == 1, "the reroll was refused");
            check(chatCount(f.channels.getFirst(), "Your kit: ") == 2, "the rerolled kit was not shown");
            check(command(player, "minigames reroll") == 0, "a reroll beyond kit_rerolls was allowed");
            List<ItemStack> kit = rolledKit(f.match, player);
            context.runAfterDelay(70, () -> {
                check(f.match.phase() == MatchPhase.ACTIVE, "the match did not start");
                Map<Item, Integer> expected = new HashMap<>(), actual = new HashMap<>();
                kit.forEach(stack -> expected.merge(stack.getItem(), stack.getCount(), Integer::sum));
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (!stack.isEmpty()) actual.merge(stack.getItem(), stack.getCount(), Integer::sum);
                }
                check(expected.equals(actual), "the player did not get the kit they rerolled: " + actual + " vs " + expected);
                try {
                    check(command(player, "minigames reroll") == 0, "a reroll after the start was allowed");
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                context.succeed();
            });
        });
    }
}
