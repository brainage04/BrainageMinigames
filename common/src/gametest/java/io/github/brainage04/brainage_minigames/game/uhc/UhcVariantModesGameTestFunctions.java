package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.GameTestLifecycle;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.menu.MenuItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.phys.AABB;

/** Speed UHC and MiniUHC: their schedules, Speed UHC's kits, perks, Masteries, drops and brewing. */
public final class UhcVariantModesGameTestFunctions {
    private UhcVariantModesGameTestFunctions() {}

    /** Both games are UHC variants with their own defaults, layouts and profession choice. */
    public static void registered(GameTestHelper context) {
        UhcGame speed = (UhcGame) Minigames.byId("speed_uhc").orElseThrow();
        UhcGame mini = (UhcGame) Minigames.byId("mini_uhc").orElseThrow();
        check(speed.variant() == UhcGame.Variant.SPEED && mini.variant() == UhcGame.Variant.MINI, "wrong variants");
        check(!speed.variant().professions && mini.variant().professions, "profession flags are wrong");
        check(speed.defaultKit().equals(SpeedUhcKit.DEFAULT_KIT), "Speed UHC does not give its Default kit");
        GameSettings speedValues = SettingsStorage.resolve(context.getLevel().getServer(), speed);
        int[] speedExpected = {2, 300, 5, 10, 50, 11, 5, 20, 12};
        int[] miniExpected = {8, 600, 15, 30, 100, 35, 10, 45, 8};
        GameSettings miniValues = SettingsStorage.resolve(context.getLevel().getServer(), mini);
        GameSetting[] keys = {UhcGame.GRACE_PERIOD, UhcGame.BORDER_START_SIZE, UhcGame.FIRST_SHRINK_TIME,
                UhcGame.FINAL_SHRINK_TIME, UhcGame.FINAL_SHRINK_SIZE, UhcGame.DEATHMATCH_TIME, UhcGame.DEATHMATCH_DURATION};
        for (int i = 0; i < keys.length; i++) {
            check(speed.setting(keys[i].key()).orElseThrow().defaultValue() == speedExpected[i], "Speed UHC " + keys[i].key());
            check(mini.setting(keys[i].key()).orElseThrow().defaultValue() == miniExpected[i], "MiniUHC " + keys[i].key());
        }
        check(speed.setting(GameSetting.TIME_LIMIT_MINUTES).orElseThrow().defaultValue() == 20, "Speed UHC time limit");
        check(mini.setting(GameSetting.TIME_LIMIT_MINUTES).orElseThrow().defaultValue() == 45, "MiniUHC time limit");
        check(speed.setting(GameSetting.LOBBY_SIZE).orElseThrow().defaultValue() == 12, "Speed UHC lobby size");
        check(mini.setting(UhcGame.DEATHMATCH_ENABLED.key()).orElseThrow().defaultValue() == 0, "MiniUHC has an arena deathmatch");
        check(speed.validate(speedValues).isEmpty() && mini.validate(miniValues).isEmpty(), "a default schedule is invalid");
        List<TeamLayout> speedLayouts = speed.layoutPresets(speedValues);
        check(speedLayouts.equals(List.of(TeamLayout.FREE_FOR_ALL, TeamLayout.teamsOf(2, 6))),
                "Speed UHC offers " + speedLayouts);
        check(mini.layoutPresets(miniValues).size() == 4, "MiniUHC does not offer solo and teams of 2-4");
        check(Minigames.UHC.id().equals("uhc") && ((UhcGame) Minigames.UHC).variant().professions, "regular UHC changed");
        context.succeed();
    }

    /**
     * Speed UHC: continuous border from 300 at 5:00 to 50 at 10:00 even with the Badlion border rule,
     * deathmatch at 11:00, the Default kit, no profession crafts and Fire Resistance only for the grace.
     */
    public static void speedSchedule(GameTestHelper context) {
        var fixture = new UhcModeGameTestFunctions.Fixture(context, Minigames.SPEED_UHC, true, true);
        UhcModeGameTestFunctions.withFixture(context, fixture, ready -> {
            Match match = ready.match;
            UhcArena arena = (UhcArena) match.arena();
            ServerPlayer player = ready.players.getFirst();
            check(!arena.badlion(), "Speed UHC followed the Badlion border rule");
            check(UhcProgression.match(player) == null, "profession crafts apply in Speed UHC");
            check(player.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "no Default iron chestplate");
            check(player.getInventory().countItem(Items.OAK_PLANKS) == 6, "no Default oak planks");
            MobEffectInstance fire = player.getEffect(MobEffects.FIRE_RESISTANCE);
            check(fire != null && fire.getDuration() <= 2 * 60 * 20, "Fire Resistance outlasts the grace: " + fire);
            check(SpeedUhc.mastery(match, player.getUUID()).orElseThrow() == SpeedUhcMastery.WILD_SPECIALIST,
                    "a player without a choice did not get Wild Specialist");
            UhcGame game = (UhcGame) match.game();
            check(game.deathmatchStartTicks(match) == 11 * 60 * 20, "deathmatch is not at 11:00");
            var border = arena.border();
            UhcModeGameTestFunctions.advance(match, 5 * 60 * 20 - 1);
            near(300, border.getSize(), "width before 5:00");
            UhcModeGameTestFunctions.advance(match, 5 * 60 * 20);
            for (int i = 0; i < 5 * 60 * 20; i++) border.tick();
            near(50, border.getSize(), "width at 10:00");
            context.succeed();
        });
    }

    /** MiniUHC: instant shrinks to 400, 300, 200 and 100 every five minutes from 15:00, no arena deathmatch, professions on. */
    public static void miniSchedule(GameTestHelper context) {
        var fixture = new UhcModeGameTestFunctions.Fixture(context, Minigames.MINI_UHC, false, true);
        UhcModeGameTestFunctions.withFixture(context, fixture, ready -> {
            Match match = ready.match;
            UhcArena arena = (UhcArena) match.arena();
            check(arena.badlion(), "MiniUHC followed the Hypixel border rule");
            check(!arena.deathmatchEnabled(), "MiniUHC has an arena deathmatch");
            check(UhcProgression.match(ready.players.getFirst()) != null, "profession crafts are off in MiniUHC");
            var border = arena.border();
            near(600, border.getSize(), "starting width");
            int[] widths = {400, 300, 200, 100};
            for (int stage = 0; stage < widths.length; stage++) {
                UhcModeGameTestFunctions.advance(match, (15 + 5 * stage) * 60 * 20);
                near(widths[stage], border.getSize(), "width at " + (15 + 5 * stage) + ":00");
            }
            context.succeed();
        });
    }

    /** A saved kit replaces Default's items, a saved Mastery is active and a perk turned off is not taken. */
    public static void speedChoices(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        rules(context, true);
        var fixture = new UhcModeGameTestFunctions.Fixture(context, Minigames.SPEED_UHC, false, true);
        ServerPlayer archer = fixture.players.getFirst();
        SpeedUhcProgression.selectKit(server, archer.getUUID(), SpeedUhcKit.find("archer"));
        SpeedUhcProgression.selectMastery(server, archer.getUUID(), SpeedUhcMastery.GUARDIAN);
        SpeedUhcProgression.setEnabled(server, archer.getUUID(), SpeedUhcPerk.TELEKINESIS, false);
        check(MenuItems.kind(archer.getInventory().getItem(1)).orElse(null) == MenuItems.Kind.SPEED_UHC_SHOP,
                "the Speed UHC lobby hotbar has no Speed UHC Shop item");
        UhcModeGameTestFunctions.withFixture(context, fixture, ready -> {
            check(archer.getInventory().countItem(Items.ARROW) == 10 && archer.getInventory().countItem(Items.STRING) == 3,
                    "the Archer kit was not given");
            check(archer.getInventory().countItem(Items.OAK_PLANKS) == 0
                    && !archer.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "Default's items were kept");
            ItemStack book = findItem(archer, Items.ENCHANTED_BOOK);
            check(book != null && book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).size() == 1,
                    "no Power I book");
            check(SpeedUhc.kit(ready.match, archer.getUUID()).orElseThrow().id().equals("archer"), "kit not recorded");
            check(SpeedUhc.mastery(ready.match, archer.getUUID()).orElseThrow() == SpeedUhcMastery.GUARDIAN, "Mastery not active");
            SpeedUhc.State state = SpeedUhc.state(archer);
            check(state != null && !state.has(archer, SpeedUhcPerk.TELEKINESIS) && state.has(archer, SpeedUhcPerk.VITAMINS),
                    "perk toggles were not applied");
            MobEffectInstance resistance = archer.getEffect(MobEffects.RESISTANCE);
            check(resistance != null && resistance.getDuration() > 60 * 20, "maxed Tenacity did not give 75 s of Resistance");
            context.succeed();
        });
    }

    /** What each Mastery and perk does to damage, kills, golden apples, potions and experience. */
    public static void speedPerksAndMasteries(GameTestHelper context) {
        rules(context, true);
        var fixture = new UhcModeGameTestFunctions.Fixture(context, Minigames.SPEED_UHC, false, true);
        UhcModeGameTestFunctions.withFixture(context, fixture, ready -> {
            Match match = ready.match;
            ServerPlayer hero = ready.players.getFirst();
            ServerPlayer rival = ready.players.getLast();
            SpeedUhc.State state = SpeedUhc.state(hero);
            check(state != null && state.maxed, "no maxed Speed UHC state");
            var sources = hero.damageSources();
            near(2, SpeedUhc.hurt(hero, sources.cactus(), 4), "Wild Specialist on cactus");
            near(10, SpeedUhc.hurt(hero, sources.playerAttack(rival), 10), "Wild Specialist on a player hit");
            state.masteries.put(hero.getUUID(), SpeedUhcMastery.GUARDIAN);
            near(9.5, SpeedUhc.hurt(hero, sources.playerAttack(rival), 10), "Guardian");
            near(6, SpeedUhc.hurt(hero, sources.fall(), 8), "Low Gravity V");
            near(2, SpeedUhc.hurt(hero, sources.lava(), 4), "Cold Blood at the first touch of lava");
            long[] episode = state.coldBlood.get(hero.getUUID());
            episode[0] -= 6 * 20;
            near(4, SpeedUhc.hurt(hero, sources.lava(), 4), "Cold Blood after its 5 seconds");
            Zombie zombie = new Zombie(EntityTypes.ZOMBIE, hero.level());
            hero.setHealth(9);
            near(0, SpeedUhc.hurt(hero, sources.mobAttack(zombie), 3), "Monster Tamer below 5 hearts");
            hero.setHealth(hero.getMaxHealth());
            near(3, SpeedUhc.hurt(hero, sources.mobAttack(zombie), 3), "Monster Tamer at full health");

            state.masteries.put(hero.getUUID(), SpeedUhcMastery.HUNTSMAN);
            hero.getFoodData().setFoodLevel(5);
            SpeedUhc.killed(match, rival, hero);
            MobEffectInstance speed = hero.getEffect(MobEffects.SPEED);
            check(speed != null && speed.getAmplifier() == 1 && speed.getDuration() > 29 * 20, "Huntsman: " + speed);
            check(hero.getFoodData().getFoodLevel() == 20, "Nourishment did not refill hunger");
            state.masteries.put(hero.getUUID(), SpeedUhcMastery.INVIGORATE);
            double before = hero.getAttributeValue(Attributes.MAX_HEALTH);
            for (int kill = 0; kill < 6; kill++) SpeedUhc.killed(match, rival, hero);
            near(before + 4, hero.getAttributeValue(Attributes.MAX_HEALTH), "Invigorate caps at 4 extra health");
            SpeedUhc.release(match, hero);
            near(before, hero.getAttributeValue(Attributes.MAX_HEALTH), "Invigorate outlived the match");

            hero.removeAllEffects();
            state.masteries.put(hero.getUUID(), SpeedUhcMastery.MASTER_BAKER);
            hero.setHealth(10);
            SpeedUhc.ateGoldenApple(hero);
            near(12, hero.getHealth(), "Master Baker");
            MobEffectInstance vitamins = hero.getEffect(MobEffects.SPEED);
            check(vitamins != null && vitamins.getAmplifier() == 1 && vitamins.getDuration() == 15 * 20, "Vitamins V: " + vitamins);
            check(SpeedUhc.potion(hero, new MobEffectInstance(MobEffects.REGENERATION, 100)).getDuration() == 125, "Master Brewer V");
            check(SpeedUhc.potion(hero, new MobEffectInstance(MobEffects.POISON, 100)).getDuration() == 50, "Medicine V");
            check(SpeedUhc.experience(hero, 100) == 125, "Expert Miner V");
            check(state.value(hero, SpeedUhcPerk.ARROW_RECOVERY) == 75, "Arrow Recovery V is not 75%");
            context.succeed();
        });
    }

    /** Gravel and chickens add arrows, sugar cane gives a book and a sugar, Telekinesis takes ore drops. */
    public static void speedDrops(GameTestHelper context) {
        rules(context, true);
        var fixture = new UhcModeGameTestFunctions.Fixture(context, Minigames.SPEED_UHC, false, true);
        UhcModeGameTestFunctions.withFixture(context, fixture, ready -> {
            ServerPlayer player = ready.players.getFirst();
            ServerLevel level = player.level();
            BlockPos pos = player.blockPosition().above(3);
            List<ItemStack> gravel = Block.getDrops(Blocks.GRAVEL.defaultBlockState(), level, pos, null, player, ItemStack.EMPTY);
            check(count(gravel, Items.ARROW) == SpeedUhc.GRAVEL_ARROWS, "gravel dropped " + gravel);
            List<ItemStack> cane = Block.getDrops(Blocks.SUGAR_CANE.defaultBlockState(), level, pos, null, player, ItemStack.EMPTY);
            check(count(cane, Items.BOOK) == 1 && count(cane, Items.SUGAR) == 1 && count(cane, Items.SUGAR_CANE) == 0,
                    "sugar cane dropped " + cane);
            List<ItemStack> outsider = Block.getDrops(Blocks.GRAVEL.defaultBlockState(), level, pos, null, null, ItemStack.EMPTY);
            check(count(outsider, Items.ARROW) == 0, "gravel nobody broke dropped arrows");
            int coal = player.getInventory().countItem(Items.COAL);
            List<ItemStack> ore = Block.getDrops(Blocks.COAL_ORE.defaultBlockState(), level, pos, null, player,
                    new ItemStack(Items.IRON_PICKAXE));
            check(ore.isEmpty() && player.getInventory().countItem(Items.COAL) > coal, "Telekinesis left " + ore);
            Chicken chicken = new Chicken(EntityTypes.CHICKEN, level);
            chicken.setPos(player.position().add(0, 2, 0));
            level.addFreshEntity(chicken);
            chicken.hurtServer(level, player.damageSources().playerAttack(player), 100);
            int arrows = 0;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(chicken.blockPosition()).inflate(3))) {
                if (item.getItem().is(Items.ARROW)) arrows += item.getItem().getCount();
                item.discard();
            }
            check(arrows == SpeedUhc.CHICKEN_ARROWS, "a killed chicken dropped " + arrows + " arrows");
            context.succeed();
        });
    }

    /** A brewing stand inside a Speed UHC's border brews on its next tick; one outside takes the usual 20 seconds. */
    public static void speedInstantBrewing(GameTestHelper context) {
        var fixture = new UhcModeGameTestFunctions.Fixture(context, Minigames.SPEED_UHC, false, true);
        UhcModeGameTestFunctions.withFixture(context, fixture, ready -> {
            ServerPlayer player = ready.players.getFirst();
            BrewingStandBlockEntity inside = brewingStand(player.level(), player.blockPosition().above(4));
            BrewingStandBlockEntity outside = brewingStand(context.getLevel(), context.absolutePos(new BlockPos(1, 2, 1)));
            for (int tick = 0; tick < 3; tick++) {
                BrewingStandBlockEntity.serverTick(inside.getLevel(), inside.getBlockPos(), inside.getBlockState(), inside);
                BrewingStandBlockEntity.serverTick(outside.getLevel(), outside.getBlockPos(), outside.getBlockState(), outside);
            }
            check(brewed(inside), "the stand inside the Speed UHC border did not brew at once");
            check(!brewed(outside), "a stand outside every Speed UHC brewed at once");
            inside.getLevel().removeBlock(inside.getBlockPos(), false);
            context.succeed();
        });
    }

    private static BrewingStandBlockEntity brewingStand(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, Blocks.BREWING_STAND.defaultBlockState());
        BrewingStandBlockEntity stand = (BrewingStandBlockEntity) level.getBlockEntity(pos);
        stand.setItem(0, PotionContents.createItemStack(Items.POTION, Potions.AWKWARD));
        stand.setItem(3, new ItemStack(Items.SUGAR));
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        return stand;
    }

    private static boolean brewed(BrewingStandBlockEntity stand) {
        return stand.getItem(0).getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.SWIFTNESS);
    }

    /** Sets the three Speed UHC max-all rules for the test and restores them afterwards. */
    private static void rules(GameTestHelper context, boolean value) {
        MinecraftServer server = context.getLevel().getServer();
        for (GameRule<Boolean> rule : List.of(SpeedUhcProgression.MAX_ALL_KITS, SpeedUhcProgression.MAX_ALL_PERKS,
                SpeedUhcProgression.MAX_ALL_MASTERIES)) {
            boolean old = server.getGameRules().get(rule);
            server.getGameRules().set(rule, value, server);
            GameTestLifecycle.afterTest(context, () -> server.getGameRules().set(rule, old, server));
        }
    }

    private static ItemStack findItem(ServerPlayer player, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) return player.getInventory().getItem(slot);
        }
        return null;
    }

    private static int count(List<ItemStack> drops, net.minecraft.world.item.Item item) {
        return drops.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void near(double expected, double actual, String label) {
        check(Math.abs(expected - actual) < 0.0001, label + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(Component.literal(message), 0);
    }
}
