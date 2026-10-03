package io.github.brainage04.brainage_minigames;

import static io.github.brainage04.brainage_minigames.UhcProgressionGameTestFunctions.*;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.uhc.UhcAdvancedRecipes;
import io.github.brainage04.brainage_minigames.game.uhc.UhcCrafting;
import io.github.brainage04.brainage_minigames.game.uhc.UhcGame;
import io.github.brainage04.brainage_minigames.game.uhc.UhcProgression;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class UhcAdvancedGameTestFunctions {
    private UhcAdvancedGameTestFunctions() {}
    public static void advanced(GameTestHelper context) {
        var server = context.getLevel().getServer();
        boolean oldMax = server.getGameRules().get(UhcProgression.MAX_ALL);
        boolean oldUnlimited = server.getGameRules().get(UhcProgression.UNLIMITED_CRAFTS);
        boolean oldUnique = server.getGameRules().get(UhcProgression.NO_DUPLICATE_CRAFTS);
        var countdown = Minigames.UHC.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow();
        int oldCountdown = SettingsStorage.resolve(server, Minigames.UHC).get(GameSetting.COUNTDOWN_SECONDS);
        int oldGrace = SettingsStorage.resolve(server, Minigames.UHC).get(UhcGame.GRACE_PERIOD);
        List<ServerPlayer> players = new ArrayList<>();
        Match[] opened = new Match[1];
        Runnable cleanup = () -> {
            if (opened[0] != null) MatchManager.stop(opened[0]);
            for (ServerPlayer player : players) { server.getPlayerList().remove(player); var root = server.getCommandStorage().get(UhcProgression.STORAGE); root.remove(player.getUUID().toString()); server.getCommandStorage().set(UhcProgression.STORAGE, root); }
            SettingsStorage.set(server, Minigames.UHC, countdown, oldCountdown); SettingsStorage.set(server, Minigames.UHC, UhcGame.GRACE_PERIOD, oldGrace);
            server.getGameRules().set(UhcProgression.MAX_ALL, oldMax, server); server.getGameRules().set(UhcProgression.UNLIMITED_CRAFTS, oldUnlimited, server); server.getGameRules().set(UhcProgression.NO_DUPLICATE_CRAFTS, oldUnique, server);
        };
        context.runBeforeTestEnd(cleanup);
        try {
            server.getGameRules().set(UhcProgression.MAX_ALL, true, server);
            server.getGameRules().set(UhcProgression.UNLIMITED_CRAFTS, true, server);
            server.getGameRules().set(UhcProgression.NO_DUPLICATE_CRAFTS, true, server);
            SettingsStorage.set(server, Minigames.UHC, countdown, 0);
            SettingsStorage.set(server, Minigames.UHC, UhcGame.GRACE_PERIOD, 0);
            players.add(connected(context, "Advanced0")); players.add(connected(context, "Advanced1"));
            ServerPlayer player = players.getFirst(), enemy = players.get(1);
            Match match = MatchManager.open(server, Minigames.UHC, TeamLayout.FREE_FOR_ALL, null);
            opened[0] = match;
            for (ServerPlayer member : players) MatchManager.join(member, match, 0);
            match.start();
            io.github.brainage04.brainage_minigames.game.uhc.UhcSpawnGameTestFunctions.awaitReady(context, match, () -> {
                try {
                    MatchManager.tick();
            check(match.phase() == MatchPhase.ACTIVE, "advanced fixture did not start");
            for (ServerPlayer member : players) { member.hasChangedDimension(); track(member.level(), member.blockPosition()); }
            player.getInventory().clearContent(); player.removeAllEffects(); enemy.removeAllEffects();
            convenience(player);
            ordinaryArrow(player);
            CraftingMenu menu = grid(player); Set<Item> pieces = new HashSet<>();
            for (int i = 0; i < 4; i++) {
                player.getRandom().setSeed(42); fill(menu, recipe("fusion_armor"), player, 1);
                Item expected = menu.getResultSlot().getItem().getItem();
                menu.slotsChanged(menu.getInputGridSlots().getFirst().container);
                check(menu.getResultSlot().getItem().is(expected), "Fusion rerolled an unchanged preview");
                menu.clicked(0, 0, ContainerInput.PICKUP, player);
                check(pieces.add(menu.getCarried().getItem()), "Fusion repeated before completing its four-piece pool"); menu.setCarried(ItemStack.EMPTY);
            }
            fill(menu, recipe("fusion_armor"), player, 1); menu.clicked(0, 0, ContainerInput.PICKUP, player);
            check(pieces.contains(menu.getCarried().getItem()), "Fusion did not reset its exhausted pool"); menu.setCarried(ItemStack.EMPTY);
            server.getGameRules().set(UhcProgression.NO_DUPLICATE_CRAFTS, false, server);
            Item repeated = null;
            for (int i = 0; i < 2; i++) {
                player.getRandom().setSeed(123); fill(menu, recipe("fusion_armor"), player, 1); menu.clicked(0, 0, ContainerInput.PICKUP, player);
                if (i == 0) repeated = menu.getCarried().getItem(); else check(menu.getCarried().is(repeated), "disabling no-duplicates still excluded a repeat");
                menu.setCarried(ItemStack.EMPTY);
            }
            player.setHealth(10); fill(menu, recipe("lucky_shears"), player, 1);
            check(menu.getResultSlot().getItem().isEmpty(), "Lucky Shears allowed exactly five hearts");
            player.setHealth(9); fill(menu, recipe("lucky_shears"), player, 1);
            check(menu.getResultSlot().getItem().is(Items.SHEARS), "Lucky Shears refused less than five hearts");
            fill(menu, recipe("swan_song"), player, 1);
            check(menu.getResultSlot().getItem().is(Items.ENCHANTED_BOOK), "solo Swan Song below five hearts refused"); player.closeContainer();
            AnvilMenu anvil = new AnvilMenu(45, player.getInventory()); player.containerMenu = anvil; player.giveExperienceLevels(40);
            anvil.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD)); anvil.getSlot(1).set(UhcCrafting.output(player, recipe("enhancement_book"))); anvil.createResult();
            check(!anvil.getSlot(2).getItem().getEnchantments().isEmpty() && anvil.getCost() == 3, "Enhancement Book failed level-30 enchantment/anvil charge");
            int xp = player.experienceLevel; anvil.clicked(2, 0, ContainerInput.PICKUP, player);
            check(player.experienceLevel == xp - 3 && anvil.getSlot(0).getItem().isEmpty() && anvil.getSlot(1).getItem().isEmpty(), "Enhancement take failed charge/input consumption"); anvil.setCarried(ItemStack.EMPTY);
            anvil.getSlot(0).set(UhcCrafting.output(player, recipe("swan_song"))); anvil.getSlot(1).set(UhcCrafting.output(player, recipe("swan_song"))); anvil.createResult();
            ItemStack swanBook = anvil.getSlot(2).getItem().copy(); check(UhcAdvancedRecipes.swanLevel(swanBook) == 2, "Swan Song I books did not combine to II");
            anvil.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD)); anvil.getSlot(1).set(swanBook); anvil.createResult();
            player.setItemInHand(InteractionHand.MAIN_HAND, anvil.getSlot(2).getItem().copy()); player.setHealth(10); enemy.setAbsorptionAmount(0); enemy.setHealth(20);
            enemy.hurtServer(enemy.level(), enemy.damageSources().playerAttack(player), 10);
            check(Math.abs(enemy.getHealth() - 9) < 0.001, "Swan Song II did not add 10% melee damage at five hearts: " + enemy.getHealth()); player.closeContainer();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); player.setOnGround(true); player.setSprinting(false); player.fallDistance = 0;
            player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(4);
            player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED).setBaseValue(40);
            for (int level = 1; level <= 2; level++) {
                player.removeAllEffects(); enemy.removeAllEffects(); enemy.setHealth(20); enemy.invulnerableTime = 0;
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.STRENGTH, 200, level - 1));
                // Apply the changed held-item attributes and charge each real modern attack.
                player.doTick(); player.doTick(); player.setOnGround(true); player.fallDistance = 0;
                player.attack(enemy);
                check(Math.abs(enemy.getHealth() - (20 - 4 * (1 + 0.3 * level))) < 0.001,
                        "Strength used vanilla flat damage instead of Hypixel's released percentage: " + enemy.getHealth());
            }
            player.removeAllEffects(); enemy.removeAllEffects();
            ItemStack velocity = UhcCrafting.output(player, recipe("velocity"));
            check(velocity.is(Items.SPLASH_POTION), "Velocity was not a throwable potion");
            var splash = new net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion(player.level(), player, velocity);
            splash.onHitAsPotion(player.level(), velocity, new net.minecraft.world.phys.EntityHitResult(enemy));
            check(player.getEffect(MobEffects.SPEED).getDuration() == 288 && !enemy.hasEffect(MobEffects.SPEED), "Velocity did not give its thrower full duration exclusively");
            player.removeAllEffects();
            BlockPos furnacePos = player.blockPosition().offset(5, 0, 0);
            track(player.level(), furnacePos);
            player.level().setBlockAndUpdate(furnacePos, Blocks.AIR.defaultBlockState()); player.level().setBlockAndUpdate(furnacePos.below(), Blocks.COBBLESTONE.defaultBlockState());
            for (ItemEntity oldDrop : player.level().getEntitiesOfClass(ItemEntity.class, new AABB(furnacePos).inflate(3))) oldDrop.discard();
            player.setItemInHand(InteractionHand.MAIN_HAND, UhcCrafting.output(player, recipe("forge")));
            var hit = new BlockHitResult(Vec3.atCenterOf(furnacePos.below()), Direction.UP, furnacePos.below(), false);
            player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            check(player.level().getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity, "Forge placement failed");
            var furnace = (AbstractFurnaceBlockEntity) player.level().getBlockEntity(furnacePos); furnace.setItem(0, new ItemStack(Items.RAW_IRON, 16));
            AbstractFurnaceBlockEntity.serverTick(player.level(), furnacePos, player.level().getBlockState(furnacePos), furnace);
            check(player.level().getBlockState(furnacePos).isAir(), "Forge did not self-destruct after ten smelts");
            int iron = 0, raw = 0;
            for (var drop : player.level().getEntitiesOfClass(ItemEntity.class, new AABB(furnacePos).inflate(3))) {
                if (drop.getItem().is(Items.IRON_INGOT)) iron += drop.getItem().getCount(); if (drop.getItem().is(Items.RAW_IRON)) raw += drop.getItem().getCount();
            }
            check(iron == 10 && raw == 6, "Forge lost items or exceeded ten smelts without fuel: iron=" + iron + ", raw=" + raw);
            BlockPos tree = player.blockPosition().offset(0, 0, 5);
            for (int i = 0; i < 3; i++) player.level().setBlockAndUpdate(tree.above(i), Blocks.OAK_LOG.defaultBlockState());
            ItemStack axe = UhcCrafting.output(player, recipe("lumberjacks_axe")); player.setItemInHand(InteractionHand.MAIN_HAND, axe); player.gameMode.destroyBlock(tree);
            check(player.level().getBlockState(tree.above()).isAir() && player.level().getBlockState(tree.above(2)).isAir() && axe.getDamageValue() == 1, "Lumberjack axe did not cut a tree for one use");
            ItemStack backpack = UhcCrafting.output(player, recipe("backpack")); player.setItemInHand(InteractionHand.MAIN_HAND, backpack); UhcCrafting.use(player, backpack);
            check(!player.containerMenu.getSlot(0).mayPlace(UhcCrafting.output(player, recipe("backpack"))), "Backpack allowed nesting");
            player.containerMenu.getSlot(0).set(new ItemStack(Items.GOLD_INGOT, 3)); player.closeContainer(); UhcCrafting.use(player, backpack);
            check(player.containerMenu.getSlot(0).getItem().is(Items.GOLD_INGOT) && player.containerMenu.getSlot(0).getItem().getCount() == 3, "Backpack contents did not survive reopen"); player.closeContainer();
            player.setHealth(1);
            for (int i = 0; i < 16; i++) check(UhcAdvancedRecipes.deepCatch(player, 0, true).is(Items.GOLD_INGOT), "Deep refused gold before its cap");
            check(UhcAdvancedRecipes.deepCatch(player, 0, true).is(Items.COD), "Deep exceeded sixteen gold ingots");
            int ticks = match.activeTicks(); player.hurtServer(player.level(), player.damageSources().generic(), 1); player.setHealth(8);
            setTicks(match, ticks + 28 * 20 - 2); MatchManager.tick(); check(player.getHealth() == 8, "Strategist healed before its quiet interval");
            MatchManager.tick(); check(player.getHealth() == 9, "Strategist max second-prestige quiet healing missing");
            player.setHealth(20); enemy.setHealth(20); enemy.setAbsorptionAmount(0); player.removeAllEffects(); enemy.removeAllEffects(); player.getInventory().clearContent();
            io.github.brainage04.brainage_minigames.util.PlayerUtils.teleport(enemy, player.level(), player.position().add(2, 0, 0), 0); enemy.hasChangedDimension();
            player.setItemInHand(InteractionHand.MAIN_HAND, UhcCrafting.output(player, recipe("axe_of_perun"))); enemy.invulnerableTime = 0;
            enemy.hurtServer(enemy.level(), enemy.damageSources().playerAttack(player), 1);
            check(enemy.getHealth() == 15, "Perun did not apply four true damage on first landed hit: " + enemy.getHealth());
            enemy.invulnerableTime = 0; enemy.hurtServer(enemy.level(), enemy.damageSources().playerAttack(player), 1);
            check(enemy.getHealth() == 14, "Perun ignored its four-second cooldown");
            player.setItemInHand(InteractionHand.MAIN_HAND, UhcCrafting.output(player, recipe("bloodlust")));
            var sharpness = player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS);
            for (int kills = 1; kills <= 10; kills++) {
                io.github.brainage04.brainage_minigames.game.uhc.UhcExtraRecipes.killed(player);
                int expected = kills >= 10 ? 5 : kills >= 6 ? 4 : kills >= 3 ? 3 : 2;
                check(net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(sharpness, player.getMainHandItem()) == expected, "Bloodlust crossed the wrong kill threshold");
            }
            ItemStack seal = UhcCrafting.output(player, recipe("expert_seal")); UhcCrafting.use(player, seal);
            check(seal.isEmpty() && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(sharpness, player.getMainHandItem()) == 6, "Expert Seal did not consume itself and raise equipped enchantments beyond vanilla caps");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); player.removeAllEffects(); enemy.removeAllEffects();
            ItemStack vitality = UhcCrafting.output(player, recipe("potion_of_vitality"));
            new net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion(player.level(), player, vitality).onHitAsPotion(player.level(), vitality, new net.minecraft.world.phys.EntityHitResult(enemy));
            check(player.hasEffect(MobEffects.REGENERATION) && !player.hasEffect(MobEffects.WITHER) && enemy.hasEffect(MobEffects.WITHER) && !enemy.hasEffect(MobEffects.REGENERATION), "Vitality applied benefits to its victim or harm to its thrower");
            ItemStack cleansing = UhcCrafting.output(player, recipe("flask_of_cleansing"));
            new net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion(player.level(), player, cleansing).onHitAsPotion(player.level(), cleansing, new net.minecraft.world.phys.EntityHitResult(enemy));
            check(!player.hasEffect(MobEffects.REGENERATION) && !enemy.hasEffect(MobEffects.WITHER) && player.hasEffect(MobEffects.WEAKNESS) && enemy.hasEffect(MobEffects.WEAKNESS), "Cleansing failed to remove effects before applying Weakness");
            player.removeAllEffects(); enemy.removeAllEffects();
            BlockPos brewingPos = player.blockPosition().offset(7, 0, 0); player.level().setBlockAndUpdate(brewingPos, Blocks.BREWING_STAND.defaultBlockState());
            var stand = (net.minecraft.world.level.block.entity.BrewingStandBlockEntity) player.level().getBlockEntity(brewingPos);
            ItemStack speedPotion = new ItemStack(Items.POTION); speedPotion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.LONG_SWIFTNESS));
            stand.setItem(0, speedPotion); stand.setItem(3, UhcCrafting.output(player, recipe("ambrosia"))); stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
            for (int tick = 0; tick <= 400; tick++) net.minecraft.world.level.block.entity.BrewingStandBlockEntity.serverTick(player.level(), brewingPos, player.level().getBlockState(brewingPos), stand);
            var brewed = stand.getItem(0).get(net.minecraft.core.component.DataComponents.POTION_CONTENTS).getAllEffects().iterator().next();
            check(brewed.getAmplifier() == 2 && brewed.getDuration() == 1200 && stand.getItem(3).isEmpty(), "Ambrosia did not brew level III capped at one minute or consume its ingredient");
            ItemStack miner = UhcCrafting.output(player, recipe("miners_blessing")); player.setItemInHand(InteractionHand.MAIN_HAND, miner);
            for (int i = 0; i < 250; i++) { player.level().setBlockAndUpdate(tree, Blocks.STONE.defaultBlockState()); player.gameMode.destroyBlock(tree); }
            check(player.hasEffect(MobEffects.REGENERATION) && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(sharpness, miner) == 1, "Miner's Blessing missed durability-based healing or its 250-use upgrade");
            server.getGameRules().set(UhcProgression.NO_DUPLICATE_CRAFTS, true, server); Set<String> diceResults = new HashSet<>();
            for (int i = 0; i < 6; i++) { player.getInventory().clearContent(); UhcCrafting.use(player, UhcCrafting.output(player, recipe("dice_of_god"))); String reward = UhcCrafting.kind(player.getInventory().getNonEquipmentItems().stream().filter(item -> !item.isEmpty()).findFirst().orElseThrow()); check(diceResults.add(reward), "Dice repeated before exhausting its six-result pool"); }
            player.getInventory().clearContent(); UhcCrafting.use(player, UhcCrafting.output(player, recipe("dice_of_god")));
            check(diceResults.contains(UhcCrafting.kind(player.getInventory().getNonEquipmentItems().stream().filter(item -> !item.isEmpty()).findFirst().orElseThrow())), "Dice did not reset its exhausted result pool");
            context.succeed();
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtime) throw runtime; throw new RuntimeException(exception);
                } finally {
                    cleanup.run();
                }
            });
        } catch (Exception exception) {
            cleanup.run();
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new RuntimeException(exception);
        }
    }

    private static void ordinaryArrow(ServerPlayer player) {
        var arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(
                player.level(), player, new ItemStack(Items.ARROW), null);
        Vec3 origin = player.position().add(0, 32, 0);
        arrow.setPos(origin);
        arrow.setDeltaMovement(1, 0, 0);
        try {
            arrow.tick();
            check(arrow.position().distanceToSqr(origin.add(1, 0, 0)) < 0.000001,
                    "Ordinary weaponless arrow did not retain vanilla forward flight in regular UHC");
        } finally {
            arrow.discard();
        }
    }

    private static void convenience(ServerPlayer player) {
        var server = player.level().getServer();
        boolean previousMax = server.getGameRules().get(UhcProgression.MAX_ALL);
        boolean previousGround = player.onGround();
        ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
        var efficiency = player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY);
        shovel.enchant(efficiency, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, shovel);
        player.doTick();
        player.setOnGround(true);
        var sand = Blocks.SAND.defaultBlockState();
        var dirt = Blocks.DIRT.defaultBlockState();
        try {
            server.getGameRules().set(UhcProgression.MAX_ALL, false, server);
            float ordinarySand = player.getDestroySpeed(sand);
            float ordinaryDirt = player.getDestroySpeed(dirt);
            server.getGameRules().set(UhcProgression.MAX_ALL, true, server);
            float convenienceSand = player.getDestroySpeed(sand);
            check(Math.abs(convenienceSand - ordinarySand - 140) < 0.001,
                    "Convenience did not combine Efficiency II with ten Invention levels before speed scaling");
            check(player.getDestroySpeed(dirt) == ordinaryDirt, "Convenience accelerated a non-convenience block");
            player.setOnGround(false);
            check(Math.abs(player.getDestroySpeed(sand) - convenienceSand / 5) < 0.001,
                    "Convenience bypassed the airborne mining penalty");
            player.setOnGround(true);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.MINING_FATIGUE, 100, 0));
            check(Math.abs(player.getDestroySpeed(sand) - convenienceSand * 0.3) < 0.001,
                    "Convenience bypassed the Mining Fatigue penalty");
        } finally {
            player.removeEffect(MobEffects.MINING_FATIGUE);
            player.setOnGround(previousGround);
            player.getInventory().clearContent();
            server.getGameRules().set(UhcProgression.MAX_ALL, previousMax, server);
        }
    }
    private static void track(net.minecraft.server.level.ServerLevel level, BlockPos pos) throws Exception {
        // Follow the resource GameTests: embedded players do not acknowledge chunk delivery.
        var field = net.minecraft.server.level.ServerLevel.class.getDeclaredField("entityManager"); field.setAccessible(true);
        var manager = (net.minecraft.world.level.entity.PersistentEntitySectionManager<?>) field.get(level);
        manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(pos.getX() >> 4, pos.getZ() >> 4), net.minecraft.world.level.entity.Visibility.TRACKED);
    }
}
