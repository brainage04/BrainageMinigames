package io.github.brainage04.brainage_minigames;

import static io.github.brainage04.brainage_minigames.UhcProgressionGameTestFunctions.check;
import static io.github.brainage04.brainage_minigames.UhcProgressionGameTestFunctions.setTicks;

import com.mojang.authlib.GameProfile;
import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.*;
import io.github.brainage04.brainage_minigames.game.uhc.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/** Server menus, persisted profiles and live match transitions on both loaders. */
public final class UhcSettingsGameTestFunctions {
    private UhcSettingsGameTestFunctions() {}

    public static void kitsAndCoins(GameTestHelper context) {
        withMatch(context, Minigames.UHC, false, f -> {
            var rules = f.server.getGameRules();
            var player = f.players.getFirst();
            check(UhcProgression.selectedKit(f.server, player.getUUID()).equals("stone"), "unselected kit is not Stone Gear");
            check(count(player, Items.STONE_PICKAXE) == 1 && count(player, Items.STONE_SWORD) == 1, "default kit did not equip stone tools");
            check(!new GameRules(List.of(UhcProgression.MAX_ALL_KITS)).get(UhcProgression.MAX_ALL_KITS), "max kits default is not false");
            check(!new GameRules(List.of(UhcProgression.CHOOSE_PRESTIGE)).get(UhcProgression.CHOOSE_PRESTIGE), "prestige choice default is not false");
            rules.set(UhcProgression.MAX_ALL, true, f.server);
            check(UhcKits.level(player, UhcKits.Kit.STONE) == 0 && !UhcKits.prestiged(player, UhcKits.Kit.STONE), "max perks unexpectedly maxed kits");
            rules.set(UhcProgression.MAX_ALL, false, f.server);
            rules.set(UhcProgression.MAX_ALL_KITS, true, f.server);
            for (var kit : UhcKits.Kit.values()) check(UhcKits.level(player, kit) == 3 && UhcKits.prestiged(player, kit), "max kits missed " + kit.id);
            check(!UhcProgression.maxed(player), "max kits unexpectedly maxed perks");
            check(command(player, "minigames uhc prestige_bonus stone iron_pickaxe") == 0, "disabled prestige choice was accepted");
            rules.set(UhcProgression.CHOOSE_PRESTIGE, true, f.server);
            check(command(player, "minigames uhc prestige_bonus stone iron_pickaxe") == 1, "prestige command failed");
            check(UhcProgression.prestigeChoice(f.server, player.getUUID(), UhcKits.Kit.STONE) == 2, "prestige choice did not persist per UUID");
            for (int i = 0; i < 12; i++) {
                UhcKits.equip(player);
                check(count(player, Items.IRON_PICKAXE) == 1 && count(player, Items.STONE_PICKAXE) == 0, "selected prestige bonus rerolled");
            }
            rules.set(UhcProgression.CHOOSE_PRESTIGE, false, f.server);
            player.getRandom().setSeed(42);
            boolean other = false;
            for (int i = 0; i < 12; i++) { UhcKits.equip(player); other |= count(player, Items.IRON_PICKAXE) == 0; }
            check(other, "random prestige ignored disabled selection");
            rules.set(UhcProgression.MAX_ALL_KITS, false, f.server);
            check(UhcKits.level(player, UhcKits.Kit.STONE) == 0 && UhcProgression.purchases(f.server, player.getUUID()).isEmpty(), "max kits wrote fake purchases");
            check(new GameRules(List.of(UhcProgression.COIN_MULTIPLIER)).get(UhcProgression.COIN_MULTIPLIER) == 100, "coin default is not 100 percent");
            rules.set(UhcProgression.COIN_MULTIPLIER, 250, f.server);
            long expected = 0;
            for (var action : UhcProgression.CoinAction.values()) {
                UhcProgression.award(f.match, player.getUUID(), action);
                expected += action.coins * 250L / 100;
                check(coins(f, player) == expected, action + " did not use multiplier with integer rounding");
            }
            check(chatCount(f.channels.getFirst(), " UHC coins (") == UhcProgression.CoinAction.values().length,
                    "coin actions did not each announce their earned amount");
            rules.set(UhcProgression.COIN_MULTIPLIER, 0, f.server);
            UhcProgression.award(f.server, player.getUUID(), 100);
            check(coins(f, player) == expected, "zero multiplier awarded coins");
            context.succeed();
        });
    }

    public static void coinCombat(GameTestHelper context) {
        withMatch(context, Minigames.UHC, false, f -> {
            f.server.getGameRules().set(AntiJanitor.ENABLED, false, f.server);
            var killer = f.players.getFirst();
            var assistant = f.players.get(1);
            var victim = f.players.get(2);
            var second = f.players.get(3);
            hit(victim, assistant, 1);
            check(coins(f, assistant) == 0 && coins(f, killer) == 0, "damage awarded kill/assist coins before a death");
            hit(victim, killer, Float.MAX_VALUE);
            check(coins(f, killer) == 75 && coins(f, assistant) == 20, "kill, first blood or assist amount incorrect");
            check(coins(f, victim) == 75, "top-10/top-5 placement was not cumulative");
            hit(second, assistant, 1);
            setTicks(f.match, f.match.activeTicks() + 201);
            hit(second, killer, Float.MAX_VALUE);
            check(coins(f, killer) == 125, "second kill repeated first blood or awarded a self-assist");
            check(coins(f, assistant) == 20, "assist older than ten seconds was awarded");
            check(coins(f, second) == 150, "third-place elimination did not award all three placement tiers");
            check(chatCount(f.channels.getFirst(), "(first blood)") == 1, "first blood did not announce exactly once");
            context.succeed();
        });
    }

    public static void coinPlacement(GameTestHelper context) {
        withMatch(context, Minigames.UHC, false, TeamLayout.FREE_FOR_ALL, 12, false, f -> {
            f.server.getGameRules().set(AntiJanitor.ENABLED, false, f.server);
            for (int index = 11; index > 0; index--) {
                var victim = f.players.get(index);
                int place = f.match.aliveCount();
                check(coins(f, victim) == 0, "placement was awarded before elimination");
                hit(victim, f.players.getFirst(), Float.MAX_VALUE);
                long expected = (place <= 10 ? 25 : 0) + (place <= 5 ? 50 : 0) + (place <= 3 ? 75 : 0);
                check(coins(f, victim) == expected, "placement boundary failed at " + place);
                UhcProgression.eliminated(f.match, victim);
                check(coins(f, victim) == expected, "placement repeated after elimination");
            }
            context.succeed();
        });
    }

    public static void coinGathering(GameTestHelper context) {
        withMatch(context, Minigames.UHC, false, f -> {
            var player = f.players.getFirst();
            var rules = f.server.getGameRules();
            check(new GameRules(List.of(UhcProgression.UNCAPPED_COIN_AWARDS))
                    .get(UhcProgression.UNCAPPED_COIN_AWARDS), "uncapped coin awards default is not true");
            rules.set(UhcProgression.UNCAPPED_COIN_AWARDS, false, f.server);
            rules.set(UhcProgression.MAX_ALL, true, f.server);
            rules.set(UhcProgression.UNLIMITED_CRAFTS, true, f.server);
            var menu = UhcProgressionGameTestFunctions.grid(player);
            var recipe = UhcProgressionGameTestFunctions.recipe("arrow_economy");
            for (int i = 0; i < 12; i++) {
                UhcProgressionGameTestFunctions.fill(menu, recipe, player, 1);
                check(coins(f, player) == Math.min(i * 5, 50), "craft preview awarded coins");
                menu.clicked(0, 0, ContainerInput.PICKUP, player);
                check(!menu.getCarried().isEmpty() && coins(f, player) == Math.min((i + 1) * 5, 50),
                        "profession result click did not award exactly five coins up to the cap");
                menu.setCarried(ItemStack.EMPTY);
            }
            player.closeContainer();
            BlockPos ore = miningSpot(f, player);
            mine(player, ore, Blocks.GOLD_ORE);
            for (int i = 0; i < 20; i++) {
                mine(player, ore, i % 2 == 0 ? Blocks.DIAMOND_ORE : Blocks.DEEPSLATE_DIAMOND_ORE);
                check(coins(f, player) == 50 + Math.min(1 + (i + 1) * 3, 60), "shared ore cap or partial final award incorrect");
            }
            mine(player, ore, Blocks.GOLD_ORE);
            mine(player, ore, Blocks.IRON_ORE);
            check(coins(f, player) == 110, "capped gold or iron awarded extra coins");
            for (int i = 0; i < 32; i++) {
                Mob mob = mob(f, player, EntityTypes.ZOMBIE);
                mob.hurtServer(player.level(), mob.damageSources().playerAttack(player), Float.MAX_VALUE);
                check(coins(f, player) == 110 + Math.min(i + 1, 30), "hostile kill did not award exactly one coin up to the cap");
                mob.die(mob.damageSources().playerAttack(player));
                check(coins(f, player) == 110 + Math.min(i + 1, 30), "repeated hostile death awarded coins twice");
            }
            Mob passive = mob(f, player, EntityTypes.COW);
            passive.hurtServer(player.level(), passive.damageSources().playerAttack(player), Float.MAX_VALUE);
            check(coins(f, player) == 140, "passive mob received a hostile award");
            rules.set(UhcProgression.UNCAPPED_COIN_AWARDS, true, f.server);
            menu = UhcProgressionGameTestFunctions.grid(player);
            for (int i = 0; i < 2; i++) {
                UhcProgressionGameTestFunctions.fill(menu, recipe, player, 1);
                menu.clicked(0, 0, ContainerInput.PICKUP, player);
                menu.setCarried(ItemStack.EMPTY);
            }
            player.closeContainer();
            mine(player, ore, Blocks.DIAMOND_ORE);
            mine(player, ore, Blocks.DIAMOND_ORE);
            mine(player, ore, Blocks.NETHER_GOLD_ORE);
            for (int i = 0; i < 2; i++) {
                Mob mob = mob(f, player, EntityTypes.ZOMBIE);
                mob.hurtServer(player.level(), mob.damageSources().playerAttack(player), Float.MAX_VALUE);
            }
            check(coins(f, player) == 159, "uncapped crafts, ores or hostiles retained the old cap");
            rules.set(UhcProgression.UNCAPPED_COIN_AWARDS, false, f.server);
            UhcProgression.crafted(player, recipe.id());
            mine(player, ore, Blocks.DIAMOND_ORE);
            Mob capped = mob(f, player, EntityTypes.ZOMBIE);
            capped.hurtServer(player.level(), capped.damageSources().playerAttack(player), Float.MAX_VALUE);
            check(coins(f, player) == 159, "toggling caps forgot earlier earnings");
            ItemStack head = UhcCrafting.output(player, UhcProgressionGameTestFunctions.recipe("golden_head"));
            check(coins(f, player) == 159, "golden-head creation awarded consumption coins");
            head.finishUsingItem(player.level(), player);
            check(head.isEmpty() && coins(f, player) == 164, "golden-head consumption did not award exactly five coins");
            head.finishUsingItem(player.level(), player);
            new ItemStack(Items.PLAYER_HEAD).finishUsingItem(player.level(), player);
            check(coins(f, player) == 164, "empty or ordinary head awarded consumption coins");
            context.succeed();
        });
    }

    public static void coinHypixelBorder(GameTestHelper context) { coinBorder(context, false); }
    public static void coinBadlionBorder(GameTestHelper context) { coinBorder(context, true); }
    private static void coinBorder(GameTestHelper context, boolean badlion) {
        withMatch(context, Minigames.UHC, true, TeamLayout.FREE_FOR_ALL, 4, badlion, f -> {
            var player = f.players.getFirst();
            var channel = f.channels.getFirst();
            var game = (UhcGame) f.game;
            var stages = List.of(UhcGame.FIRST_SHRINK_TIME, UhcGame.SECOND_SHRINK_TIME,
                    UhcGame.THIRD_SHRINK_TIME, UhcGame.FINAL_SHRINK_TIME);
            for (int stage = 0; stage < (badlion ? 4 : 1); stage++) {
                int at = f.match.settings().minutesInTicks(stages.get(stage));
                setTicks(f.match, at - 1); game.tick(f.match);
                check(chatCount(channel, "(border shrink)") == stage, "border award fired before the shrink");
                long before = coins(f, player);
                setTicks(f.match, at); game.tick(f.match);
                check(coins(f, player) == before + 20, "border stage did not award ten coins alongside the five-minute survival award");
                check(chatCount(channel, "(border shrink)") == stage + 1, "border stage did not announce once");
                long after = coins(f, player);
                game.tick(f.match);
                check(coins(f, player) == after, "same shrink tick awarded twice");
            }
            if (!badlion) {
                setTicks(f.match, f.match.settings().minutesInTicks(UhcGame.FINAL_SHRINK_TIME));
                game.tick(f.match);
                check(chatCount(channel, "(border shrink)") == 1, "continuous shrink completion repeated its award");
            }
            context.succeed();
        });
    }

    public static void coinLoggerKill(GameTestHelper context) {
        withMatch(context, Minigames.UHC, false, TeamLayout.parse("3v1v1").orElseThrow(), 5, false, f -> {
            var killer = f.players.getFirst();
            var near = f.players.get(1);
            var far = f.players.get(2);
            var victim = f.players.get(3);
            check(f.match.teamOf(killer.getUUID()).equals(f.match.teamOf(near.getUUID()))
                    && !f.match.teamOf(killer.getUUID()).equals(f.match.teamOf(victim.getUUID())), "team fixture assignment incorrect");
            near.setPos(killer.position().add(1, 0, 0));
            far.setPos(killer.position().add(250, 0, 0));
            MatchManager.handleDisconnect(victim); f.server.getPlayerList().remove(victim);
            var logger = UhcCombatLogger.zombie(victim.getUUID());
            check(logger != null, "combat logger missing");
            BlockPos death = logger.blockPosition();
            for (BlockPos pos : List.of(death, death.east(), death.above(), death.east().above())) {
                var original = logger.level().getBlockState(pos);
                f.cleanup.add(() -> logger.level().setBlockAndUpdate(pos, original));
            }
            check(logger.hurtServer((ServerLevel) logger.level(), logger.damageSources().playerAttack(killer), 1), "logger duel hit failed");
            logger.invulnerableTime = 0;
            check(logger.hurtServer((ServerLevel) logger.level(), logger.damageSources().generic(), Float.MAX_VALUE), "credited logger elimination failed");
            check(coins(f, killer) == 85, "logger did not award one full kill, first blood and the valid duel bonus");
            check(coins(f, near) == 50 && coins(f, far) == 0, "logger kill did not use the 200-block teammate boundary");
            check(coins(f, victim) == 75, "disconnected participant lost placement");
            check(chatCount(f.channels.getFirst(), victim.getScoreboardName() + " earned ") == 2, "offline placement awards were not announced");
            logger.die(logger.damageSources().playerAttack(killer));
            check(coins(f, killer) == 85, "dead logger repeated kill or hostile-mob coins");
            expireDuels(f.match);
            var rival = f.players.get(4);
            hit(rival, killer, 1);
            expireDuels(f.match);
            rival.invulnerableTime = 0;
            check(rival.hurtServer(rival.level(), rival.damageSources().generic(), Float.MAX_VALUE), "expired-duel credited death failed");
            check(coins(f, killer) == 135 && coins(f, near) == 100, "expired lock awarded a duel bonus or lost normal kill credit");
            check(chatCount(f.channels.getFirst(), "(anti-janitor duel win)") == 1, "duel bonus repeated after expiry");
            MatchManager.leave(far);
            check(coins(f, far) == 150 && coins(f, killer) == 135, "forfeit placement failed or awarded a duel bonus");
            context.succeed();
        });
    }

    public static void coinScopeMeetup(GameTestHelper context) { coinScope(context, Minigames.MEETUP); }
    public static void coinScopeFinalUhc(GameTestHelper context) { coinScope(context, Minigames.FINAL_UHC); }
    public static void coinScopeDuel(GameTestHelper context) { coinScope(context, Minigames.CLASSIC); }
    private static void coinScope(GameTestHelper context, Minigame game) {
        withMatch(context, game, false, f -> {
            var player = f.players.getFirst();
            for (var action : UhcProgression.CoinAction.values()) UhcProgression.award(f.match, player.getUUID(), action);
            UhcProgression.crafted(player, "arrow_economy");
            UhcProgression.deathmatch(f.match); UhcProgression.borderShrink(f.match, 0);
            BlockPos ore = miningSpot(f, player); mine(player, ore, Blocks.DIAMOND_ORE);
            Mob mob = mob(f, player, EntityTypes.ZOMBIE);
            mob.hurtServer(player.level(), mob.damageSources().playerAttack(player), Float.MAX_VALUE);
            ItemStack head = UhcCrafting.output(player, UhcProgressionGameTestFunctions.recipe("golden_head"));
            head.finishUsingItem(player.level(), player);
            hit(f.players.get(1), player, Float.MAX_VALUE);
            for (ServerPlayer participant : f.players) check(coins(f, participant) == 0, game.id() + " awarded UHC coins");
            check(chatCount(f.channels.getFirst(), " UHC coins (") == 0, game.id() + " announced UHC coins");
            context.succeed();
        });
    }

    public static void craftPrompts(GameTestHelper context) {
        withMatch(context, Minigames.UHC, false, f -> {
            var player = f.players.getFirst();
            var channel = f.channels.getFirst();
            player.getInventory().clearContent();
            f.server.getGameRules().set(UhcProgression.MAX_ALL, true, f.server);
            f.server.getGameRules().set(UhcProgression.UNLIMITED_CRAFTS, false, f.server);
            player.getInventory().add(new ItemStack(Items.GOLD_INGOT, 8));
            player.getInventory().add(new ItemStack(Items.APPLE, 2));
            int before = chatCount(channel, "You can craft light apple");
            UhcCraftPrompts.tick(player);
            check(chatCount(channel, "You can craft light apple") == before + 1, "ingredients did not send craft prompt");
            Component message = chat(channel, "You can craft light apple");
            check(message.getStyle().getClickEvent() instanceof ClickEvent.RunCommand, "prompt was not clickable");
            UhcCraftPrompts.tick(player);
            player.getInventory().add(new ItemStack(Items.DIRT));
            UhcCraftPrompts.tick(player);
            check(chatCount(channel, "You can craft light apple") == before + 1, "unchanged recipe ingredients repeated prompt");
            var click = (ClickEvent.RunCommand) message.getStyle().getClickEvent();
            check(command(player, click.command().substring(1)) == 1, "click did not open crafting table");
            check(player.containerMenu instanceof CraftingMenu, "click did not open server-side vanilla crafting menu");
            CraftingMenu menu = (CraftingMenu) player.containerMenu;
            var recipe = UhcProgressionGameTestFunctions.recipe("light_apple");
            for (int cell = 0; cell < 9; cell++) {
                Item expected = recipe.grid()[cell];
                ItemStack actual = menu.getInputGridSlots().get(cell).getItem();
                check(expected == Items.AIR ? actual.isEmpty() : actual.is(expected) && actual.getCount() == 1, "autofill grid wrong at " + cell);
            }
            check(count(player, Items.GOLD_INGOT) == 4 && count(player, Items.APPLE) == 1, "autofill did not move exact ingredients out of inventory");
            check(menu.getResultSlot().getItem().is(Items.GOLDEN_APPLE), "autofill did not produce output");
            menu.clicked(0, 0, ContainerInput.PICKUP, player);
            check(menu.getCarried().is(Items.GOLDEN_APPLE), "one output click failed to craft");
            player.closeContainer();
            check(count(player, Items.GOLDEN_APPLE) == 1, "closing lost crafted carried output");
            check(UhcCraftPrompts.open(player, "light_apple"), "profession prestige did not permit its second ultimate");
            player.containerMenu.clicked(0, 0, ContainerInput.PICKUP, player);
            player.closeContainer();
            check(count(player, Items.GOLDEN_APPLE) == 2, "second ultimate output was lost");
            player.getInventory().add(new ItemStack(Items.GOLD_INGOT, 4));
            player.getInventory().add(new ItemStack(Items.APPLE));
            check(!UhcCraftPrompts.open(player, "light_apple"), "autofill bypassed prestiged ultimate craft limit");
            f.server.getGameRules().set(UhcProgression.UNLIMITED_CRAFTS, true, f.server);
            check(UhcCraftPrompts.open(player, "light_apple"), "unlimited crafts did not permit further autofill");
            player.closeContainer();
            check(count(player, Items.GOLD_INGOT) == 4 && count(player, Items.APPLE) == 1, "closing lost unused grid ingredients");
            f.server.getGameRules().set(UhcProgression.MAX_ALL, false, f.server);
            check(!UhcCraftPrompts.open(player, "light_apple"), "locked recipe opened");
            context.succeed();
        });
    }

    public static void presetsAndTiming(GameTestHelper context) {
        withMatch(context, Minigames.UHC, true, f -> {
            check(f.server.getCommands().getDispatcher().execute("minigames uhc preset hypixel", f.server.createCommandSourceStack()) == 1, "Hypixel preset command failed");
            var rules = f.server.getGameRules();
            check(rules.get(UhcModeRules.DOUBLE_HEALTH) && rules.get(UhcModeRules.BORDER_STYLE) == 0 && rules.get(UhcModeRules.DEATHMATCH), "Hypixel core preset values wrong");
            check(rules.get(UhcModeRules.DEATHMATCH_AFTER_GRACE) == 35 && rules.get(UhcModeRules.DEATHMATCH_SKIP_PLAYERS) == 15
                    && rules.get(UhcModeRules.DEATHMATCH_SKIP_MINUTES) == 10 && rules.get(UhcModeRules.DEATHMATCH_DURATION) == 15
                    && rules.get(UhcModeRules.TIMEOUT_MOST_KILLS) && rules.get(UhcModeRules.COMBAT_LOGGER), "Hypixel timing/logger preset values wrong");
            UhcGame game = (UhcGame) f.match.game();
            game.onStart(f.match);
            check(game.deathmatchStartTicks(f.match) == 35 * 60 * 20 && game.deathmatchDurationTicks(f.match) == 15 * 60 * 20, "after-grace/duration rules did not affect schedule");
            setTicks(f.match, 1);
            game.tick(f.match);
            check(game.deathmatchStartTicks(f.match) == 1 + 10 * 60 * 20, "player threshold did not skip remaining countdown to ten minutes");
            setTicks(f.match, game.deathmatchStartTicks(f.match));
            game.tick(f.match);
            UhcArena arena = (UhcArena) f.match.arena();
            check(arena.inDeathmatch(), "shortened countdown did not enter deathmatch");
            f.match.teams().getFirst().addScore(2);
            f.match.teams().get(1).addScore(1);
            setTicks(f.match, game.deathmatchStartTicks(f.match) + game.deathmatchDurationTicks(f.match));
            game.tick(f.match);
            check(f.match.phase() == MatchPhase.ENDED && f.match.winners().equals(List.of(f.match.teams().getFirst())), "deathmatch timeout did not award most kills");
            UhcPresets.apply(f.server, false);
            check(!rules.get(UhcModeRules.DOUBLE_HEALTH) && rules.get(UhcModeRules.BORDER_STYLE) == 1 && rules.get(UhcModeRules.DEATHMATCH)
                    && rules.get(UhcModeRules.DEATHMATCH_AFTER_GRACE) == 0 && rules.get(UhcModeRules.DEATHMATCH_SKIP_PLAYERS) == 0
                    && rules.get(UhcModeRules.DEATHMATCH_DURATION) == 0 && !rules.get(UhcModeRules.TIMEOUT_MOST_KILLS)
                    && !rules.get(UhcModeRules.COMBAT_LOGGER), "Badlion preset did not replace every differing rule");
            rules.set(UhcModeRules.DOUBLE_HEALTH, true, f.server);
            check(rules.get(UhcModeRules.DOUBLE_HEALTH), "individual rule could not override preset");
            context.succeed();
        });
    }

    public static void borders(GameTestHelper context) {
        withMatch(context, Minigames.UHC, true, f -> {
            var arena = (UhcArena) f.match.arena();
            check(new GameRules(List.of(UhcModeRules.DEATHMATCH_BORDER_START)).get(UhcModeRules.DEATHMATCH_BORDER_START) == 113
                    && new GameRules(List.of(UhcModeRules.DEATHMATCH_BORDER_FINAL)).get(UhcModeRules.DEATHMATCH_BORDER_FINAL) == 56
                    && new GameRules(List.of(UhcModeRules.NETHER_BORDER_SCALE)).get(UhcModeRules.NETHER_BORDER_SCALE) == 8, "border rule defaults changed");
            var game = (UhcGame) f.match.game();
            var loggedOut = f.players.get(1);
            MatchManager.handleDisconnect(loggedOut); f.server.getPlayerList().remove(loggedOut);
            var beforeMove = UhcCombatLogger.zombie(loggedOut.getUUID());
            check(coins(f, f.players.getFirst()) == 0 && coins(f, loggedOut) == 0, "deathmatch coins were awarded before entry");
            setTicks(f.match, game.deathmatchStartTicks(f.match)); game.tick(f.match);
            check(arena.border().getSize() == 100, "deathmatch starting width was ignored");
            var moved = UhcCombatLogger.zombie(loggedOut.getUUID());
            check(coins(f, f.players.getFirst()) == 60 && coins(f, loggedOut) == 50,
                    "deathmatch entry did not award fifty coins once to online and offline survivors");
            game.tick(f.match);
            check(coins(f, f.players.getFirst()) == 60 && coins(f, loggedOut) == 50, "deathmatch entry repeated its award");
            check(moved != null && moved != beforeMove && moved.level() == arena.level()
                    && moved.level().dimension().equals(ModDimensions.MINIGAMES), "deathmatch did not move disconnected participant");
            check(!UhcCombatLogger.canAttack(f.players.getFirst(), moved)
                    && !UhcCombatLogger.canAttack(f.players.getFirst(), beforeMove),
                    "logger target selection ignored deathmatch freeze or a retired proxy");
            check(!moved.hurtServer(arena.level(), moved.damageSources().playerAttack(f.players.getFirst()), 1),
                    "deathmatch freeze did not protect disconnected participant");
            var portal = (EndPortalBlock) Blocks.END_PORTAL;
            check(portal.getPortalDestination(arena.level(), moved, moved.blockPosition()) == null,
                    "End portal let a disconnected deathmatch participant escape");
            setTicks(f.match, game.deathmatchStartTicks(f.match) + f.match.settings().minutesInTicks(UhcGame.DEATHMATCH_SHRINK_TIME));
            game.tick(f.match);
            check(arena.border().getLerpTarget() == 40, "deathmatch final width was ignored");
            check(coins(f, f.players.getFirst()) == 80 && coins(f, loggedOut) == 60,
                    "deathmatch shrink did not award ten coins once to online and offline survivors");
            game.tick(f.match);
            check(coins(f, f.players.getFirst()) == 80 && coins(f, loggedOut) == 60, "deathmatch shrink repeated its award");
            check(UhcCombatLogger.canAttack(f.players.getFirst(), moved),
                    "released deathmatch logger remained ineligible for combat");
            context.succeed();
        });
    }

    public static void loggerUhc(GameTestHelper context) { logger(context, Minigames.UHC); }
    public static void loggerMeetup(GameTestHelper context) { logger(context, Minigames.MEETUP); }
    public static void loggerFinalUhc(GameTestHelper context) { logger(context, Minigames.FINAL_UHC); }
    private static void logger(GameTestHelper context, Minigame game) {
        withMatch(context, game, false, f -> {
            check(new GameRules(List.of(UhcModeRules.COMBAT_LOGGER)).get(UhcModeRules.COMBAT_LOGGER), "combat logger default is not true");
            var victim = f.players.get(1);
            var attacker = f.players.getFirst();
            victim.getInventory().clearContent();
            victim.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 3));
            victim.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_AXE));
            victim.setHealth(12);
            UUID id = victim.getUUID();
            MatchManager.handleDisconnect(victim);
            f.server.getPlayerList().remove(victim);
            var zombie = UhcCombatLogger.zombie(id);
            check(zombie != null && f.match.isAlive(id) && f.match.involves(id), "disconnect did not retain participant as zombie");
            check(zombie.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).has(DataComponents.PROFILE)
                    && zombie.getMainHandItem().is(Items.STONE_AXE) && zombie.entityTags().contains(UhcCombatLogger.TAG), "logger head, held item or marker missing");
            check(UhcCombatLogger.participant(zombie).equals(id) && UhcCombatLogger.match(zombie) == f.match, "bot/camera logger interface lost identity");
            check(UhcCombatLogger.canAttack(attacker, zombie)
                    && !UhcCombatLogger.canAttack(victim, zombie)
                    && !UhcCombatLogger.canAttack(attacker, attacker), "logger target eligibility lost match/team identity");
            var attackField = Match.class.getDeclaredField("lastAttacks");
            attackField.setAccessible(true);
            check(!((java.util.Map<?, ?>) attackField.get(f.match)).containsKey(id),
                    "logger permission probe recorded kill credit without a hit");
            check(zombie.hurtServer((net.minecraft.server.level.ServerLevel) zombie.level(), zombie.damageSources().generic(), 2), "logger was not attackable");
            zombie.invulnerableTime = 0;
            check(zombie.hurtServer((net.minecraft.server.level.ServerLevel) zombie.level(),
                    zombie.damageSources().playerAttack(attacker), 1), "logger PvP hit was refused");
            check(UhcCombatLogger.canAttack(attacker, zombie)
                    && !UhcCombatLogger.canAttack(f.players.get(2), zombie),
                    "logger target selection ignored anti-janitor exclusive combat");
            Vec3 position = zombie.position().add(2, 0, 0); zombie.setPos(position);
            float health = zombie.getHealth();
            var returned = f.connect(id, victim.getScoreboardName());
            MatchManager.handleConnect(returned);
            check(UhcCombatLogger.zombie(id) == null && zombie.isRemoved(), "rejoining left duplicate zombie");
            check(!UhcCombatLogger.canAttack(attacker, zombie), "rejoined logger remained an eligible target");
            check(returned.position().distanceTo(position) < 0.01 && Math.abs(returned.getHealth() - health) < 0.01, "rejoin did not restore current zombie position and health");
            check(count(returned, Items.DIAMOND) == 3, "rejoin lost match inventory");
            returned.getInventory().clearContent(); returned.getInventory().add(new ItemStack(Items.DIAMOND, 3));
            MatchManager.handleDisconnect(returned); f.server.getPlayerList().remove(returned);
            var doomed = UhcCombatLogger.zombie(id);
            BlockPos death = doomed.blockPosition();
            f.cleanup.add(doomed::discard);
            for (BlockPos pos : List.of(death, death.east(), death.above(), death.east().above())) {
                var state = doomed.level().getBlockState(pos);
                f.cleanup.add(() -> doomed.level().setBlockAndUpdate(pos, state));
            }
            f.cleanup.add(() -> doomed.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    doomed.getBoundingBox().inflate(3), entity -> UhcCrafting.kind(entity.getItem()).equals("player_head")
                            && entity.getItem().getOrDefault(DataComponents.CUSTOM_NAME, Component.empty()).getString()
                            .equals(victim.getScoreboardName() + "'s Head")).forEach(net.minecraft.world.entity.Entity::discard));
            int chatBefore = chatCount(f.channels.getFirst(), "was killed while disconnected!");
            check(doomed.hurtServer((net.minecraft.server.level.ServerLevel) doomed.level(), doomed.damageSources().playerAttack(attacker), Float.MAX_VALUE), "lethal logger hit was refused");
            check(!f.match.isAlive(id) && !f.match.involves(id) && UhcCombatLogger.zombie(id) == null, "dead logger can still rejoin");
            check(!UhcCombatLogger.canAttack(attacker, doomed), "dead logger remained an eligible target");
            check(chatCount(f.channels.getFirst(), "was killed while disconnected!") == chatBefore + 1, "logger death message missing");
            var chest = ChestBlock.getContainer((ChestBlock) Blocks.CHEST, doomed.level().getBlockState(death), doomed.level(), death, true);
            check(chest != null && chest.countItem(Items.DIAMOND) == 3, "anti-janitor did not retain disconnected inventory in death chest");
            var last = f.players.get(3);
            f.server.getGameRules().set(UhcModeRules.COMBAT_LOGGER, false, f.server);
            MatchManager.handleDisconnect(last);
            check(!f.match.isAlive(last.getUUID()) && UhcCombatLogger.zombie(last.getUUID()) == null, "disabled logger did not eliminate on disconnect");
            context.succeed();
        });
    }

    public static void endPortals(GameTestHelper context) {
        withMatch(context, Minigames.UHC, false, f -> {
            var player = f.players.getFirst();
            int before = chatCount(f.channels.getFirst(), "End portals are disabled");
            var portal = (EndPortalBlock) Blocks.END_PORTAL;
            check(portal.getPortalDestination(player.level(), player, player.blockPosition()) == null, "End portal let a UHC participant escape to the real End");
            check(chatCount(f.channels.getFirst(), "End portals are disabled") == before + 1, "blocked End portal did not explain refusal");
            check(f.match.isAlive(player.getUUID()) && player.level().dimension().equals(ModDimensions.UHC), "End refusal eliminated or moved participant");
            var disconnected = f.players.get(1);
            MatchManager.handleDisconnect(disconnected); f.server.getPlayerList().remove(disconnected);
            var logger = UhcCombatLogger.zombie(disconnected.getUUID());
            check(logger != null && portal.getPortalDestination((net.minecraft.server.level.ServerLevel) logger.level(),
                    logger, logger.blockPosition()) == null, "End portal let a combat logger escape");
            context.succeed();
        });
    }

    public static void netherBorder(GameTestHelper context) {
        var server = context.getLevel().getServer();
        var oldRules = server.getGameRules().copy(context.getLevel().enabledFeatures());
        var settingsId = BrainageMinigames.id("settings");
        var oldSettings = server.getCommandStorage().get(settingsId).copy();
        io.github.brainage04.brainage_minigames.game.arena.Arena[] arena = new io.github.brainage04.brainage_minigames.game.arena.Arena[1];
        Runnable cleanup = () -> {
            if (arena[0] != null) arena[0].close();
            server.getGameRules().setAll(oldRules, server);
            server.getCommandStorage().set(settingsId, oldSettings);
        };
        GameTestLifecycle.afterTest(context, cleanup);
        try {
            server.getGameRules().set(UhcModeRules.DEATHMATCH, false, server);
            SettingsStorage.set(server, Minigames.UHC, UhcGame.NETHER_CLOSE_TIME, 20);
            var values = SettingsStorage.resolve(server, Minigames.UHC);
            for (int divisor : List.of(1, 8)) {
                server.getGameRules().set(UhcModeRules.NETHER_BORDER_SCALE, divisor, server);
                arena[0] = Minigames.UHC.openArena(server, values);
                var surface = server.getLevel(ModDimensions.UHC).getWorldBorder();
                var nether = server.getLevel(ModDimensions.UHC_NETHER).getWorldBorder();
                check(Math.abs(nether.getSize() - surface.getSize() / divisor) < 0.001, "Nether width divisor ignored");
                check(Math.abs(nether.getCenterX() - surface.getCenterX() / 8) < 0.001, "Nether width rule changed portal-coordinate scaling");
                var shrink = UhcArena.class.getDeclaredMethod("shrinkBorder", double.class, long.class);
                shrink.setAccessible(true); shrink.invoke(arena[0], 100.0, 1200L);
                check(Math.abs(nether.getLerpTarget() - 100.0 / divisor) < 0.001, "Nether shrink ignored divisor");
                arena[0].close(); arena[0] = null;
            }
            context.succeed();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
        finally { cleanup.run(); }
    }

    private static void expireDuels(Match match) throws Exception {
        var protection = Match.class.getDeclaredField("antiJanitor");
        protection.setAccessible(true);
        var duels = AntiJanitor.class.getDeclaredField("duels");
        duels.setAccessible(true);
        for (Object duel : ((java.util.Map<?, ?>) duels.get(protection.get(match))).values()) {
            var until = duel.getClass().getDeclaredField("until");
            until.setAccessible(true); until.setLong(duel, 0);
        }
    }
    private static long coins(Fixture f, ServerPlayer player) { return UhcProgression.coins(f.server, player.getUUID()); }
    private static void hit(ServerPlayer victim, ServerPlayer attacker, float amount) {
        victim.invulnerableTime = 0;
        check(victim.hurtServer(victim.level(), victim.damageSources().playerAttack(attacker), amount), "player hit failed");
    }
    private static BlockPos miningSpot(Fixture f, ServerPlayer player) {
        BlockPos pos = player.blockPosition().east();
        var level = player.level();
        var original = level.getBlockState(pos);
        f.cleanup.add(() -> level.setBlockAndUpdate(pos, original));
        f.cleanup.add(() -> level.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(5),
                entity -> entity instanceof net.minecraft.world.entity.item.ItemEntity
                        || entity instanceof net.minecraft.world.entity.ExperienceOrb).forEach(Entity::discard));
        return pos;
    }
    private static void mine(ServerPlayer player, BlockPos pos, net.minecraft.world.level.block.Block block) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        player.level().setBlockAndUpdate(pos, block.defaultBlockState());
        check(player.gameMode.destroyBlock(pos), "ore mining failed");
    }
    private static Mob mob(Fixture f, ServerPlayer player, EntityType<? extends Mob> type) {
        Mob mob = type.create(player.level(), EntitySpawnReason.TRIGGERED);
        check(mob != null, "mob creation failed");
        mob.setPos(player.position().add(1, 0, 1));
        check(player.level().addFreshEntity(mob), "mob insertion failed");
        f.cleanup.add(mob::discard);
        return mob;
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().getNonEquipmentItems().stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static int command(ServerPlayer player, String command) throws Exception {
        return player.level().getServer().getCommands().getDispatcher().execute(command, player.createCommandSourceStack());
    }
    private static void flatten(Object packet, List<Component> chat) {
        if (packet instanceof ClientboundSystemChatPacket message) chat.add(message.content());
        else if (packet instanceof BundlePacket<?> bundle) for (var child : bundle.subPackets()) flatten(child, chat);
    }
    private static List<Component> chats(EmbeddedChannel channel) {
        channel.flushOutbound(); List<Component> chat = new ArrayList<>();
        for (var packet : channel.outboundMessages()) flatten(packet, chat);
        return chat;
    }
    private static int chatCount(EmbeddedChannel channel, String text) { return (int) chats(channel).stream().filter(message -> message.getString().contains(text)).count(); }
    private static Component chat(EmbeddedChannel channel, String text) { return chats(channel).stream().filter(message -> message.getString().contains(text)).findFirst().orElseThrow(); }
    @FunctionalInterface private interface Action { void run(Fixture fixture) throws Exception; }
    private static void withMatch(GameTestHelper context, Minigame game, boolean deathmatch, Action action) {
        withMatch(context, game, deathmatch, TeamLayout.FREE_FOR_ALL, 4, false, action);
    }
    private static void withMatch(GameTestHelper context, Minigame game, boolean deathmatch,
            TeamLayout layout, int playerCount, boolean badlion, Action action) {
        Fixture f = new Fixture(context, game, deathmatch, layout, playerCount, badlion);
        UhcSpawnGameTestFunctions.awaitReady(context, f.match, () -> {
            try {
                MatchManager.tick();
                check(f.match.phase() == MatchPhase.ACTIVE, "fixture match did not become active");
                for (ServerPlayer player : f.players) {
                    player.hasChangedDimension();
                    player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
                }
                action.run(f);
            } catch (RuntimeException | Error exception) { throw exception; }
            catch (Exception exception) { throw new IllegalStateException(exception); }
            finally { f.close(); }
        });
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper context;
        final MinecraftServer server;
        final Minigame game;
        final GameRules rules;
        final net.minecraft.nbt.CompoundTag settings;
        final List<ServerPlayer> players = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>();
        final List<Runnable> cleanup = new ArrayList<>();
        Match match;
        boolean closed;
        Fixture(GameTestHelper context, Minigame game, boolean deathmatch, TeamLayout layout, int playerCount, boolean badlion) {
            this.context = context; this.server = context.getLevel().getServer(); this.game = game;
            rules = server.getGameRules().copy(context.getLevel().enabledFeatures());
            settings = server.getCommandStorage().get(BrainageMinigames.id("settings")).copy();
            GameTestLifecycle.afterTest(context, this::close);
            try {
                for (GameSetting setting : game.settings()) {
                    SettingsStorage.set(server, game, setting, setting.defaultValue());
                }
                server.getGameRules().set(UhcProgression.MAX_ALL, false, server);
                server.getGameRules().set(UhcProgression.MAX_ALL_KITS, false, server);
                server.getGameRules().set(UhcProgression.CHOOSE_PRESTIGE, false, server);
                server.getGameRules().set(UhcProgression.COIN_MULTIPLIER, 100, server);
                server.getGameRules().set(UhcProgression.UNCAPPED_COIN_AWARDS, true, server);
                server.getGameRules().set(UhcModeRules.BORDER_STYLE, badlion ? 1 : 0, server);
                server.getGameRules().set(UhcModeRules.DEATHMATCH_AFTER_GRACE, 0, server);
                server.getGameRules().set(UhcModeRules.DEATHMATCH_DURATION, 0, server);
                server.getGameRules().set(UhcModeRules.DEATHMATCH_SKIP_PLAYERS, 0, server);
                server.getGameRules().set(UhcModeRules.TIMEOUT_MOST_KILLS, false, server);
                server.getGameRules().set(UhcModeRules.COMBAT_LOGGER, true, server);
                server.getGameRules().set(UhcModeRules.DEATHMATCH, deathmatch, server);
                server.getGameRules().set(UhcModeRules.DEATHMATCH_BORDER_START, 100, server);
                server.getGameRules().set(UhcModeRules.DEATHMATCH_BORDER_FINAL, 40, server);
                server.getGameRules().set(CombatRules.COMBAT_1_8, false, server);
                server.getGameRules().set(AntiJanitor.ENABLED, true, server);
                SettingsStorage.set(server, game, game.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow(), 0);
                if (game instanceof UhcGame) {
                    SettingsStorage.set(server, game, UhcGame.GRACE_PERIOD, 0);
                    SettingsStorage.set(server, game, UhcGame.NETHER_CLOSE_TIME, 0);
                }
                var level = server.getLevel(ModDimensions.UHC);
                BlockPos center = context.absolutePos(new BlockPos(2, 2, 2));
                match = MatchManager.open(server, game, layout, null, (ignored, values) -> {
                    if (!(game instanceof UhcGame)) return NaturalArena.at(level, center.getX(), center.getZ(), 200);
                    if (deathmatch) return game.openArena(ignored, values);
                    return UhcArena.at(level, center.getX(), center.getZ(), 200);
                });
                for (int i = 0; i < playerCount; i++) {
                    ServerPlayer player = connect(UUID.randomUUID(), "NewUhc" + i);
                    MatchManager.join(player, match, 0);
                }
                match.start();
            } catch (Exception exception) { close(); throw new IllegalStateException(exception); }
        }
        ServerPlayer connect(UUID id, String name) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(id, name), false);
            var player = new ServerPlayer(server, context.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channels.add(new EmbeddedChannel(connection));
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            players.add(player); return player;
        }
        @Override public void close() {
            if (closed) return; closed = true;
            if (match != null) MatchManager.stop(match);
            cleanup.forEach(Runnable::run);
            for (ServerPlayer player : players) {
                if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player);
                var root = server.getCommandStorage().get(UhcProgression.STORAGE);
                root.remove(player.getUUID().toString()); server.getCommandStorage().set(UhcProgression.STORAGE, root);
                var snapshotsId = BrainageMinigames.id("player_snapshots");
                var snapshots = server.getCommandStorage().get(snapshotsId);
                snapshots.getCompoundOrEmpty("players").remove(player.getUUID().toString());
                server.getCommandStorage().set(snapshotsId, snapshots);
            }
            server.getCommandStorage().set(BrainageMinigames.id("settings"), settings);
            server.getGameRules().setAll(rules, server);
        }
    }
}
