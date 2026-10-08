package io.github.brainage04.brainage_minigames;

import static io.github.brainage04.brainage_minigames.UhcResourceGameTestFunctions.field;
import static io.github.brainage04.brainage_minigames.UhcResourceGameTestFunctions.isolated;

import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.uhc.NaturalArena;
import io.github.brainage04.brainage_minigames.game.uhc.UhcChainBreaks;
import io.github.brainage04.brainage_minigames.game.uhc.UhcCrafting;
import io.github.brainage04.brainage_minigames.game.uhc.UhcResourceRules;
import io.github.brainage04.brainage_minigames.game.uhc.UhcResourceRules.Ore;
import io.github.brainage04.brainage_minigames.game.uhc.UhcResourceScenarios;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The resource scenarios, each against its own fresh UHC dimension and an Overworld control
 * (block and mob loot, Timber, Vein Miner, Blood Diamonds), or in a real Meetup match (Hastey Boys
 * and kill rewards).
 */
public final class UhcScenarioGameTestFunctions {
    /** Every test fixture sits in chunk (0, 0) of its disposable level. */
    private static final BlockPos ORIGIN = new BlockPos(2, 100, 2);

    private UhcScenarioGameTestFunctions() {}

    public static void cutClean(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        restoreAfter(context, UhcResourceScenarios.CUT_CLEAN);
        restorePercentsAfter(context, Ore.IRON, Ore.GOLD, Ore.COPPER, Ore.ANCIENT_DEBRIS);
        for (Ore ore : List.of(Ore.IRON, Ore.GOLD, Ore.ANCIENT_DEBRIS)) server.getGameRules().set(ore.dropPercent, 200, server);
        server.getGameRules().set(Ore.COPPER.dropPercent, 100, server);
        each(context, (level, uhc) -> withPlayer(level, player -> {
            set(server, UhcResourceScenarios.CUT_CLEAN, false);
            check(mine(player, Blocks.IRON_ORE, Items.IRON_PICKAXE).count(Items.RAW_IRON) == (uhc ? 2 : 1), "raw iron without CutClean");
            set(server, UhcResourceScenarios.CUT_CLEAN, true);
            Drops iron = mine(player, Blocks.IRON_ORE, Items.IRON_PICKAXE);
            // Smelted first, then the 200% iron rule doubles the ingots.
            check(uhc ? iron.count(Items.IRON_INGOT) == 2 && iron.count(Items.RAW_IRON) == 0
                    : iron.count(Items.RAW_IRON) == 1 && iron.count(Items.IRON_INGOT) == 0, level.dimension() + " CutClean iron: " + iron);
            Drops deepGold = mine(player, Blocks.DEEPSLATE_GOLD_ORE, Items.IRON_PICKAXE);
            check(uhc ? deepGold.count(Items.GOLD_INGOT) == 2 && deepGold.count(Items.RAW_GOLD) == 0
                    : deepGold.count(Items.RAW_GOLD) == 1, level.dimension() + " CutClean gold: " + deepGold);
            Drops copper = mine(player, Blocks.COPPER_ORE, Items.STONE_PICKAXE);
            check(uhc ? copper.count(Items.COPPER_INGOT) >= 2 && copper.count(Items.RAW_COPPER) == 0
                    : copper.count(Items.RAW_COPPER) >= 2, level.dimension() + " CutClean copper: " + copper);
            Drops debris = mine(player, Blocks.ANCIENT_DEBRIS, Items.DIAMOND_PICKAXE);
            check(uhc ? debris.count(Items.NETHERITE_SCRAP) == 2 && debris.count(Items.ANCIENT_DEBRIS) == 0
                    : debris.count(Items.ANCIENT_DEBRIS) == 1, level.dimension() + " CutClean debris: " + debris);
            ItemStack silk = enchanted(level, Items.DIAMOND_PICKAXE, Enchantments.SILK_TOUCH, 1);
            Drops silkIron = mine(player, Blocks.IRON_ORE, silk);
            check(silkIron.count(Items.IRON_ORE) == 1 && silkIron.count(Items.IRON_INGOT) == 0, "Silk Touch ore was smelted: " + silkIron);
            Drops silkDebris = mine(player, Blocks.ANCIENT_DEBRIS, silk);
            check(silkDebris.count(Items.ANCIENT_DEBRIS) == 1 && silkDebris.count(Items.NETHERITE_SCRAP) == 0, "Silk Touch debris was smelted: " + silkDebris);
            for (int attempt = 0; attempt < 12; attempt++) {
                Drops gravel = mine(player, Blocks.GRAVEL, Items.IRON_SHOVEL);
                check(uhc ? gravel.count(Items.FLINT) == 1 && gravel.count(Items.GRAVEL) == 0
                        : gravel.count(Items.FLINT) + gravel.count(Items.GRAVEL) == 1, level.dimension() + " CutClean gravel: " + gravel);
            }
            check(mine(player, Blocks.GRAVEL, silk).count(Items.GRAVEL) == 1, "Silk Touch gravel became flint");
            checkAnimals(level, player, uhc);
            set(server, UhcResourceScenarios.CUT_CLEAN, false);
            for (int attempt = 0; attempt < 4; attempt++) {
                Drops cow = kill(level, player, EntityTypes.COW);
                check(cow.count(Items.COOKED_BEEF) == 0 && cow.count(Items.BEEF) >= 1, "cooked beef without CutClean: " + cow);
            }
            checkRecipes(player, uhc);
        }));
        context.succeed();
    }

    private static void checkAnimals(ServerLevel level, ServerPlayer player, boolean uhc) {
        for (int attempt = 0; attempt < 4; attempt++) {
            Drops cow = kill(level, player, EntityTypes.COW);
            check(uhc ? cow.count(Items.COOKED_BEEF) >= 3 && cow.count(Items.BEEF) == 0 && cow.count(Items.LEATHER) >= 1
                    : cow.count(Items.COOKED_BEEF) == 0 && cow.count(Items.BEEF) >= 1, level.dimension() + " CutClean cow: " + cow);
            Drops pig = kill(level, player, EntityTypes.PIG);
            check(uhc ? pig.count(Items.COOKED_PORKCHOP) >= 3 && pig.count(Items.PORKCHOP) == 0
                    : pig.count(Items.COOKED_PORKCHOP) == 0, level.dimension() + " CutClean pig: " + pig);
            Drops chicken = kill(level, player, EntityTypes.CHICKEN);
            check(uhc ? chicken.count(Items.COOKED_CHICKEN) >= 3 && chicken.count(Items.CHICKEN) == 0 && chicken.count(Items.FEATHER) >= 1
                    : chicken.count(Items.COOKED_CHICKEN) == 0, level.dimension() + " CutClean chicken: " + chicken);
            Drops sheep = kill(level, player, EntityTypes.SHEEP);
            check(uhc ? sheep.count(Items.COOKED_MUTTON) >= 1 && sheep.count(Items.MUTTON) == 0
                    : sheep.count(Items.COOKED_MUTTON) == 0, level.dimension() + " CutClean sheep: " + sheep);
        }
    }

    /** Quick Pick takes ingots with CutClean; Iron Economy and Gold Pack never do. */
    private static void checkRecipes(ServerPlayer player, boolean uhc) {
        set(player.level().getServer(), UhcResourceScenarios.CUT_CLEAN, true);
        check(UhcCrafting.smelted(player) == uhc, "CutClean crafting outside its dimensions");
        ItemStack ingot = new ItemStack(Items.IRON_INGOT), coal = new ItemStack(Items.COAL), stick = new ItemStack(Items.STICK);
        CraftingInput quickPick = CraftingInput.of(3, 3, List.of(ingot, ingot, ingot, coal, stick, coal, ItemStack.EMPTY, stick, ItemStack.EMPTY));
        check(recipe("quick_pick").matches(quickPick, true) && !recipe("quick_pick").matches(quickPick, false),
                "Quick Pick did not take ingots only with CutClean");
        CraftingInput economy = CraftingInput.of(3, 3, List.of(ingot, ingot, ingot, ingot, coal, ingot, ingot, ingot, ingot));
        check(!recipe("iron_economy").matches(economy, true), "Iron Economy multiplied ingots with CutClean");
        ItemStack gold = new ItemStack(Items.GOLD_INGOT);
        CraftingInput pack = CraftingInput.of(3, 3, List.of(gold, gold, gold, gold, coal, gold, gold, gold, gold));
        check(!recipe("gold_pack").matches(pack, true), "Gold Pack multiplied ingots with CutClean");
    }

    public static void timber(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        restoreAfter(context, UhcResourceScenarios.TIMBER);
        each(context, (level, uhc) -> withPlayer(level, player -> {
            set(server, UhcResourceScenarios.TIMBER, false);
            check(fell(level, player, new ItemStack(Items.IRON_AXE)) == 1, "a tree fell without Timber");
            set(server, UhcResourceScenarios.TIMBER, true);
            ItemStack axe = new ItemStack(Items.IRON_AXE);
            int felled = fell(level, player, axe);
            check(felled == (uhc ? 8 : 1), level.dimension() + " Timber felled " + felled + " of 8 logs");
            check(axe.getDamageValue() == felled, "Timber did not wear the axe once per log: " + axe.getDamageValue());
            check(level.getBlockState(ORIGIN.offset(-1, 1, 0)).is(Blocks.OAK_LOG), "Timber felled a placed log");
            check(level.getBlockState(ORIGIN.offset(0, 6, 0)).is(Blocks.BIRCH_LOG), "Timber felled another kind of log");
            List<ItemEntity> logs = items(level, Items.OAK_LOG);
            check(logs.stream().mapToInt(entity -> entity.getItem().getCount()).sum() == felled, "felled logs did not all drop");
            check(logs.stream().allMatch(entity -> entity.position().distanceTo(Vec3.atCenterOf(ORIGIN)) < 1.2),
                    "felled logs did not drop at the broken log");
            if (!uhc) return;
            // An axe that breaks on the second log stops the tree there.
            ItemStack worn = new ItemStack(Items.IRON_AXE);
            worn.setDamageValue(worn.getMaxDamage() - 2);
            check(fell(level, player, worn) == 2 && worn.isEmpty(), "Timber kept felling with a broken axe");
            // A huge connected block of logs stops at the limit.
            clear(level);
            for (BlockPos pos : BlockPos.betweenClosed(4, 150, 4, 10, 156, 10)) level.setBlock(pos, Blocks.SPRUCE_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
            long started = System.nanoTime();
            check(player.gameMode.destroyBlock(new BlockPos(7, 150, 7)), "the limit log did not break");
            long micros = (System.nanoTime() - started) / 1_000;
            int left = 0;
            for (BlockPos pos : BlockPos.betweenClosed(4, 150, 4, 10, 156, 10)) if (level.getBlockState(pos).is(Blocks.SPRUCE_LOG)) left++;
            check(343 - left == UhcChainBreaks.TIMBER_LIMIT, "Timber broke " + (343 - left) + " logs, not its limit");
            BrainageMinigames.LOGGER.info("Timber felled {} logs in {} µs", UhcChainBreaks.TIMBER_LIMIT, micros);
        }));
        context.succeed();
    }

    /** An eight-log oak with a diagonal branch, a placed oak log and a touching birch; returns logs broken. */
    private static int fell(ServerLevel level, ServerPlayer player, ItemStack axe) {
        clear(level);
        List<BlockPos> oak = new ArrayList<>();
        for (int y = 0; y < 6; y++) oak.add(ORIGIN.above(y));
        oak.add(ORIGIN.offset(1, 4, 1));
        oak.add(ORIGIN.offset(2, 5, 2));
        for (BlockPos pos : oak) level.setBlock(pos, Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(ORIGIN.offset(0, 6, 0), Blocks.BIRCH_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
        place(player, ORIGIN.offset(-1, 0, 0), ORIGIN.offset(-1, 1, 0), Direction.UP, new ItemStack(Items.OAK_LOG));
        player.setItemInHand(InteractionHand.MAIN_HAND, axe);
        check(player.gameMode.destroyBlock(ORIGIN), "the log did not break");
        int broken = 0;
        for (BlockPos pos : oak) if (level.getBlockState(pos).isAir()) broken++;
        return broken;
    }

    public static void veinMiner(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        restoreAfter(context, UhcResourceScenarios.VEIN_MINER);
        restorePercentsAfter(context, Ore.IRON);
        server.getGameRules().set(Ore.IRON.dropPercent, 200, server);
        each(context, (level, uhc) -> withPlayer(level, player -> {
            set(server, UhcResourceScenarios.VEIN_MINER, false);
            check(vein(level, player, new ItemStack(Items.IRON_PICKAXE)) == 1, "a vein was mined without Vein Miner");
            set(server, UhcResourceScenarios.VEIN_MINER, true);
            ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
            int mined = vein(level, player, pickaxe);
            check(mined == (uhc ? 6 : 1), level.dimension() + " Vein Miner mined " + mined + " of 6 iron ores");
            check(pickaxe.getDamageValue() == mined, "Vein Miner did not wear the pickaxe once per ore: " + pickaxe.getDamageValue());
            check(level.getBlockState(ORIGIN.offset(0, 0, 1)).is(Blocks.COAL_ORE), "Vein Miner mined another ore");
            check(level.getBlockState(ORIGIN.offset(-1, 0, 0)).is(Blocks.IRON_ORE), "Vein Miner mined a placed ore");
            List<ItemEntity> raw = items(level, Items.RAW_IRON);
            // Each ore rolls its own loot, doubled by the 200% iron rule in the UHC dimension.
            check(raw.stream().mapToInt(entity -> entity.getItem().getCount()).sum() == (uhc ? 2 : 1) * mined, "vein drops were not per ore: " + raw.size());
            check(raw.stream().allMatch(entity -> entity.position().distanceTo(Vec3.atCenterOf(ORIGIN)) < 1.2), "vein drops did not drop at the mined ore");
            if (!uhc) return;
            check(vein(level, player, new ItemStack(Items.WOODEN_PICKAXE)) == 1, "a pickaxe that cannot harvest iron mined the vein");
            clear(level);
            for (BlockPos pos : BlockPos.betweenClosed(4, 60, 4, 8, 62, 8)) level.setBlock(pos, Blocks.IRON_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            check(player.gameMode.destroyBlock(new BlockPos(6, 60, 6)), "the limit ore did not break");
            int left = 0;
            for (BlockPos pos : BlockPos.betweenClosed(4, 60, 4, 8, 62, 8)) if (level.getBlockState(pos).is(Blocks.IRON_ORE)) left++;
            check(75 - left == UhcChainBreaks.VEIN_LIMIT, "Vein Miner mined " + (75 - left) + " ores, not its limit");
            // The search never loads a chunk: a vein reaching the edge of chunk (0, 0) stops there.
            clear(level);
            // Known shapes: no neighbour updates, which would load the next chunk themselves.
            for (int x = 12; x <= 15; x++) level.setBlock(new BlockPos(x, 60, 8), Blocks.IRON_ORE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            check(level.getChunkSource().getChunkNow(1, 0) == null, "the neighbouring chunk was already loaded");
            List<BlockPos> found = UhcChainBreaks.connected(level, new BlockPos(12, 60, 8), state -> state.is(Blocks.IRON_ORE), 63);
            check(found.size() == 3 && level.getChunkSource().getChunkNow(1, 0) == null, "the vein search loaded the next chunk: " + found);
        }));
        context.succeed();
    }

    /** A six-ore iron vein (one deepslate, two diagonal), a placed iron ore and touching coal; returns ores mined. */
    private static int vein(ServerLevel level, ServerPlayer player, ItemStack pickaxe) {
        clear(level);
        List<BlockPos> iron = List.of(ORIGIN, ORIGIN.east(), ORIGIN.offset(1, 1, 0), ORIGIN.offset(2, 2, 1), ORIGIN.offset(2, 2, 2), ORIGIN.below());
        for (BlockPos pos : iron) level.setBlock(pos, Blocks.IRON_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(ORIGIN.below(), Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(ORIGIN.offset(0, 0, 1), Blocks.COAL_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
        place(player, ORIGIN.offset(-1, -1, 0), ORIGIN.offset(-1, 0, 0), Direction.UP, new ItemStack(Items.IRON_ORE));
        player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
        check(player.gameMode.destroyBlock(ORIGIN), "the ore did not break");
        int mined = 0;
        for (BlockPos pos : iron) if (level.getBlockState(pos).isAir()) mined++;
        return mined;
    }

    public static void bloodDiamonds(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        restoreAfter(context, UhcResourceScenarios.BLOOD_DIAMONDS, UhcResourceScenarios.VEIN_MINER);
        set(server, UhcResourceScenarios.VEIN_MINER, false);
        each(context, (level, uhc) -> withPlayer(level, player -> {
            set(server, UhcResourceScenarios.BLOOD_DIAMONDS, false);
            player.setHealth(20);
            mine(player, Blocks.DIAMOND_ORE, Items.IRON_PICKAXE);
            check(player.getHealth() == 20, "a diamond cost health without Blood Diamonds");
            set(server, UhcResourceScenarios.BLOOD_DIAMONDS, true);
            // Armour, Protection, Resistance and immunity frames do not soften it.
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                Item piece = switch (slot) { case HEAD -> Items.DIAMOND_HELMET; case CHEST -> Items.DIAMOND_CHESTPLATE; case LEGS -> Items.DIAMOND_LEGGINGS; default -> Items.DIAMOND_BOOTS; };
                player.setItemSlot(slot, enchanted(level, piece, Enchantments.PROTECTION, 4));
            }
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200, 4));
            mine(player, Blocks.DIAMOND_ORE, Items.IRON_PICKAXE);
            mine(player, Blocks.DEEPSLATE_DIAMOND_ORE, Items.IRON_PICKAXE);
            check(player.getHealth() == (uhc ? 18 : 20), level.dimension() + " two diamonds left " + player.getHealth() + " health");
            mine(player, Blocks.DIAMOND_ORE, Items.STONE_PICKAXE);
            check(player.getHealth() == (uhc ? 18 : 20), "a diamond ore the pickaxe cannot harvest cost health");
            mine(player, Blocks.IRON_ORE, Items.IRON_PICKAXE);
            check(player.getHealth() == (uhc ? 18 : 20), "iron ore cost health");
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0));
            mine(player, Blocks.DIAMOND_ORE, Items.IRON_PICKAXE);
            check(player.getHealth() == (uhc ? 18 : 20) && player.getAbsorptionAmount() == (uhc ? 3 : 4), "absorption was not spent first");
            if (!uhc) return;
            player.removeEffect(MobEffects.ABSORPTION);
            player.setAbsorptionAmount(0);
            player.setHealth(1);
            mine(player, Blocks.DIAMOND_ORE, Items.IRON_PICKAXE);
            check(player.isDeadOrDying(), "the last half heart did not kill");
        }));
        context.succeed();
    }

    public static void oreless(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        restoreAfter(context, UhcResourceScenarios.DIAMONDLESS, UhcResourceScenarios.GOLDLESS);
        each(context, (level, uhc) -> withPlayer(level, player -> {
            set(server, UhcResourceScenarios.DIAMONDLESS, false);
            set(server, UhcResourceScenarios.GOLDLESS, false);
            check(mine(player, Blocks.DIAMOND_ORE, Items.IRON_PICKAXE).count(Items.DIAMOND) >= 1, "no diamond without Diamondless");
            check(mine(player, Blocks.GOLD_ORE, Items.IRON_PICKAXE).count(Items.RAW_GOLD) >= 1, "no gold without Goldless");
            set(server, UhcResourceScenarios.DIAMONDLESS, true);
            ItemStack silk = enchanted(level, Items.DIAMOND_PICKAXE, Enchantments.SILK_TOUCH, 1);
            for (Block block : List.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE)) {
                Drops drops = mine(player, block, Items.IRON_PICKAXE);
                check(uhc ? drops.total() == 0 && drops.experience() > 0 : drops.count(Items.DIAMOND) >= 1, level.dimension() + " Diamondless " + block + ": " + drops);
                check(mine(player, block, silk).total() == (uhc ? 0 : 1), "Silk Touch kept a Diamondless ore");
            }
            check(mine(player, Blocks.GOLD_ORE, Items.IRON_PICKAXE).count(Items.RAW_GOLD) >= 1, "Diamondless removed gold");
            set(server, UhcResourceScenarios.GOLDLESS, true);
            for (Block block : List.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE)) {
                Drops drops = mine(player, block, Items.IRON_PICKAXE);
                check(uhc == (drops.total() == 0), level.dimension() + " Goldless " + block + ": " + drops);
            }
            check(mine(player, Blocks.IRON_ORE, Items.IRON_PICKAXE).count(Items.RAW_IRON) >= 1, "Goldless removed iron");
        }));
        context.succeed();
    }

    /**
     * Hastey Boys and the Diamondless/Goldless kill rewards need a UHC-style match: a Meetup in the
     * GameTest level, whose kit holds tools.
     */
    public static void matchRules(GameTestHelper context) throws MatchException {
        MinecraftServer server = context.getLevel().getServer();
        restoreAfter(context, UhcResourceScenarios.HASTEY_BOYS, UhcResourceScenarios.DIAMONDLESS, UhcResourceScenarios.GOLDLESS);
        GameSetting countdown = Minigames.MEETUP.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow();
        int oldCountdown = SettingsStorage.resolve(server, Minigames.MEETUP).get(countdown);
        List<ServerPlayer> players = new ArrayList<>();
        Match[] match = new Match[1];
        GameTestLifecycle.afterTest(context, () -> {
            if (match[0] != null) MatchManager.stop(match[0]);
            players.forEach(player -> server.getPlayerList().remove(player));
            SettingsStorage.set(server, Minigames.MEETUP, countdown, oldCountdown);
        });
        SettingsStorage.set(server, Minigames.MEETUP, countdown, 0);
        BlockPos centre = context.absolutePos(new BlockPos(2, 2, 2));
        match[0] = MatchManager.open(server, Minigames.MEETUP, TeamLayout.FREE_FOR_ALL, null,
                (ignored, values) -> NaturalArena.at(context.getLevel(), centre.getX(), centre.getZ(), 200));
        // Four players, so the match goes on after both deaths and keeps the dropped rewards.
        for (int index = 0; index < 4; index++) {
            ServerPlayer player = TestPlayers.connect(context, "scenario" + index);
            players.add(player);
            MatchManager.join(player, match[0], 0);
        }
        match[0].start();
        GameTestLifecycle.awaitPreparation(context, () -> match[0].phase() == MatchPhase.ACTIVE, () -> {
            ServerPlayer holder = players.getFirst();
            var enchantments = holder.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> efficiency = enchantments.getOrThrow(Enchantments.EFFICIENCY), unbreaking = enchantments.getOrThrow(Enchantments.UNBREAKING);
            List<ItemStack> kit = new ArrayList<>();
            for (int slot = 0; slot < holder.getInventory().getContainerSize(); slot++) {
                ItemStack stack = holder.getInventory().getItem(slot);
                if (stack.is(net.minecraft.tags.ItemTags.MINING_ENCHANTABLE)) kit.add(stack);
            }
            check(!kit.isEmpty(), "the Meetup kit held no tool to check");
            ItemStack plain = new ItemStack(Items.IRON_PICKAXE);
            put(holder, plain);
            set(server, UhcResourceScenarios.HASTEY_BOYS, false);
            UhcResourceScenarios.tick(server);
            check(plain.getEnchantments().isEmpty(), "a tool was enchanted without Hastey Boys");
            set(server, UhcResourceScenarios.HASTEY_BOYS, true);
            ItemStack fast = enchanted(holder.level(), Items.DIAMOND_SHOVEL, Enchantments.EFFICIENCY, 5);
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            ItemStack carried = new ItemStack(Items.STONE_AXE);
            ItemStack philosophers = UhcCrafting.output(holder, recipe("philosophers_pickaxe"));
            put(holder, fast);
            put(holder, sword);
            put(holder, philosophers);
            holder.containerMenu.setCarried(carried);
            UhcResourceScenarios.tick(server);
            for (ItemStack tool : kit) check(tool.getEnchantments().getLevel(efficiency) >= 3 && tool.getEnchantments().getLevel(unbreaking) >= 3, "a kit tool missed Hastey Boys: " + tool);
            for (ItemStack tool : List.of(plain, carried)) {
                check(tool.getEnchantments().getLevel(efficiency) == 3 && tool.getEnchantments().getLevel(unbreaking) == 3, "Hastey Boys skipped " + tool);
            }
            check(fast.getEnchantments().getLevel(efficiency) == 5 && fast.getEnchantments().getLevel(unbreaking) == 3, "Hastey Boys lowered Efficiency V");
            check(sword.getEnchantments().isEmpty(), "Hastey Boys enchanted a sword");
            check(philosophers.getEnchantments().getLevel(unbreaking) == 0, "Hastey Boys stretched the Philosopher's Pickaxe's three uses");
            ServerPlayer plainVictim = players.get(1), rewarded = players.get(2);
            set(server, UhcResourceScenarios.DIAMONDLESS, false);
            set(server, UhcResourceScenarios.GOLDLESS, false);
            Drops none = die(context, match[0], plainVictim);
            check(none.count(Items.DIAMOND) == 0 && none.count(Items.GOLD_INGOT) == 0 && none.golden() == 0, "kill rewards without the rules: " + none);
            set(server, UhcResourceScenarios.DIAMONDLESS, true);
            set(server, UhcResourceScenarios.GOLDLESS, true);
            Drops rewards = die(context, match[0], rewarded);
            check(rewards.count(Items.DIAMOND) == 1 && rewards.count(Items.GOLD_INGOT) == 8 && rewards.golden() == 1,
                    "Diamondless/Goldless kill rewards: " + rewards);
            context.succeed();
        });
    }

    /**
     * Kills an emptied participant through the match's death path, in the test's own (entity-tracking)
     * chunk, and collects what dropped.
     */
    private static Drops die(GameTestHelper context, Match match, ServerPlayer victim) {
        ServerLevel level = victim.level();
        Vec3 at = Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(1, 2, 1)));
        victim.teleportTo(at.x, at.y, at.z);
        victim.getInventory().clearContent();
        AABB around = victim.getBoundingBox().inflate(4);
        level.getEntitiesOfClass(ItemEntity.class, around).forEach(ItemEntity::discard);
        victim.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
        check(!match.isAlive(victim.getUUID()), victim.getScoreboardName() + " survived the kill");
        return new Drops(level.getEntitiesOfClass(ItemEntity.class, around), 0);
    }

    // Shared fixtures

    private interface Check {
        void run(ServerLevel level, boolean uhc);
    }

    /** Runs {@code check} in a fresh UHC dimension and a fresh Overworld, chunk (0, 0) loaded and entity-tracking. */
    private static void each(GameTestHelper context, Check check) {
        MinecraftServer server = context.getLevel().getServer();
        for (ResourceKey<Level> dimension : List.of(ModDimensions.UHC, Level.OVERWORLD)) {
            isolated(server, dimension, context.getLevel().getChunkSource().getGenerator(), level -> {
                level.getChunkAt(ORIGIN);
                field(level, "entityManager", PersistentEntitySectionManager.class).updateChunkStatus(new ChunkPos(0, 0), Visibility.TRACKED);
                check(UhcResourceRules.applies(level) == dimension.equals(ModDimensions.UHC), "unexpected scenario scope");
                check.run(level, UhcResourceRules.applies(level));
                return null;
            });
        }
    }

    /** A survival player without a match in {@code level}, like the resource-rule tests use. */
    private static void withPlayer(ServerLevel level, Consumer<ServerPlayer> action) {
        MinecraftServer server = level.getServer();
        CommonListenerCookie cookie = TestPlayers.cookie("scenarioMiner");
        ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            player.connection = new ServerGamePacketListenerImpl(server, connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            // A player whose client has not loaded takes no damage at all.
            player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
            player.snapTo(ORIGIN.getX() + 3.5, ORIGIN.getY(), ORIGIN.getZ() + 0.5);
            action.accept(player);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    /** Item entities and experience near {@link #ORIGIN}. */
    private record Drops(List<ItemEntity> items, int experience) {
        int count(Item item) {
            return items.stream().filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
        }

        int total() {
            return items.stream().mapToInt(entity -> entity.getItem().getCount()).sum();
        }

        int golden() {
            return items.stream().filter(entity -> UhcCrafting.kind(entity.getItem()).equals("golden_head")).mapToInt(entity -> entity.getItem().getCount()).sum();
        }

        @Override public String toString() {
            return items.stream().map(entity -> entity.getItem().toString()).toList() + " xp=" + experience;
        }
    }

    private static Drops mine(ServerPlayer player, Block block, Item tool) {
        return mine(player, block, new ItemStack(tool));
    }

    private static Drops mine(ServerPlayer player, Block block, ItemStack tool) {
        ServerLevel level = player.level();
        clear(level);
        level.setBlock(ORIGIN, block.defaultBlockState(), Block.UPDATE_CLIENTS);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool.copy());
        check(player.gameMode.destroyBlock(ORIGIN), block + " did not break");
        AABB box = new AABB(ORIGIN).inflate(2);
        int experience = level.getEntitiesOfClass(ExperienceOrb.class, box).stream().mapToInt(ExperienceOrb::getValue).sum();
        return new Drops(level.getEntitiesOfClass(ItemEntity.class, box), experience);
    }

    private static Drops kill(ServerLevel level, ServerPlayer player, EntityType<? extends LivingEntity> type) {
        clear(level);
        LivingEntity mob = type.create(level, EntitySpawnReason.COMMAND);
        mob.snapTo(ORIGIN.getX() + 0.5, ORIGIN.getY(), ORIGIN.getZ() + 0.5);
        check(level.addFreshEntity(mob), type + " was not added");
        mob.hurtServer(level, level.damageSources().playerAttack(player), 1000);
        check(mob.isDeadOrDying(), type + " did not die");
        return new Drops(level.getEntitiesOfClass(ItemEntity.class, new AABB(ORIGIN).inflate(3)), 0);
    }

    private static List<ItemEntity> items(ServerLevel level, Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(ORIGIN).inflate(8)).stream().filter(entity -> entity.getItem().is(item)).toList();
    }

    /** Puts {@code stack} itself, not a copy, into a free inventory slot. */
    private static void put(ServerPlayer player, ItemStack stack) {
        int slot = player.getInventory().getFreeSlot();
        check(slot >= 0, "no free inventory slot for " + stack);
        player.getInventory().setItem(slot, stack);
    }

    /** Empties the fixture area and its item and experience entities. */
    private static void clear(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(0, 55, 0, 14, 160, 14)) {
            if (!level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        AABB all = new AABB(-4, 50, -4, 18, 170, 18);
        level.getEntitiesOfClass(ItemEntity.class, all).forEach(ItemEntity::discard);
        level.getEntitiesOfClass(ExperienceOrb.class, all).forEach(ExperienceOrb::discard);
    }

    /** Places {@code stack} on top of {@code support} at {@code pos}, as a player, so it is recorded as placed. */
    private static void place(ServerPlayer player, BlockPos support, BlockPos pos, Direction face, ItemStack stack) {
        ServerLevel level = player.level();
        if (level.getBlockState(support).isAir()) level.setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos), face, support, false);
        var result = ((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
        check(result.consumesAction() && !level.getBlockState(pos).isAir(), "placing " + stack + " failed");
        check(UhcResourceRules.placed(level, pos) == UhcResourceRules.applies(level), "a placed resource was not recorded");
    }

    private static ItemStack enchanted(Level level, Item item, ResourceKey<Enchantment> enchantment, int strength) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), strength);
        return stack;
    }

    private static UhcCrafting.Recipe recipe(String id) {
        return UhcCrafting.recipes().stream().filter(recipe -> recipe.id().equals(id)).findFirst().orElseThrow();
    }

    private static void set(MinecraftServer server, GameRule<Boolean> rule, boolean value) {
        server.getGameRules().set(rule, value, server);
    }

    @SafeVarargs
    private static void restoreAfter(GameTestHelper context, GameRule<Boolean>... rules) {
        MinecraftServer server = context.getLevel().getServer();
        boolean[] previous = new boolean[rules.length];
        for (int index = 0; index < rules.length; index++) previous[index] = server.getGameRules().get(rules[index]);
        GameTestLifecycle.afterTest(context, () -> {
            for (int index = 0; index < rules.length; index++) set(server, rules[index], previous[index]);
        });
    }

    private static void restorePercentsAfter(GameTestHelper context, Ore... ores) {
        MinecraftServer server = context.getLevel().getServer();
        int[] previous = new int[ores.length];
        for (int index = 0; index < ores.length; index++) previous[index] = server.getGameRules().get(ores[index].dropPercent);
        GameTestLifecycle.afterTest(context, () -> {
            for (int index = 0; index < ores.length; index++) server.getGameRules().set(ores[index].dropPercent, previous[index], server);
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(Component.literal(message), 0);
    }
}
