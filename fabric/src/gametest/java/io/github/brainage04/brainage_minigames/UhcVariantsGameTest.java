package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.uhc.MeetupGame;
import io.github.brainage04.brainage_minigames.game.uhc.NaturalArena;
import io.github.brainage04.brainage_minigames.storage.KitStorage;
import io.github.brainage04.brainage_minigames.util.LootUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Meetup and FinalUHC: their kits, their natural-terrain arena and Meetup's shrinking border. */
public final class UhcVariantsGameTest {
    private static final EquipmentSlot[] ARMOUR = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    @GameTest
    public void finalUhcKitIsTheMinemenLoadout(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        assertTrue(
                KitStorage.give(server, Minigames.FINAL_UHC.defaultKit(), List.of(player)),
                "Expected the FinalUHC kit to exist.");
        Map<Item, Integer> expected = new HashMap<>();
        expected.put(Items.DIAMOND_SWORD, 1);
        expected.put(Items.DIAMOND_AXE, 1);
        expected.put(Items.DIAMOND_PICKAXE, 1);
        expected.put(Items.FISHING_ROD, 1);
        expected.put(Items.FLINT_AND_STEEL, 1);
        expected.put(Items.GOLDEN_APPLE, 16);
        expected.put(Items.COOKED_BEEF, 64);
        expected.put(Items.OAK_PLANKS, 128);
        expected.put(Items.COBBLESTONE, 128);
        expected.put(Items.WATER_BUCKET, 3);
        expected.put(Items.LAVA_BUCKET, 3);
        assertEquals(expected, counts(player.getInventory().getNonEquipmentItems()), "kit items");
        assertEquals(
                3,
                level(context, Enchantments.SHARPNESS, find(player, Items.DIAMOND_SWORD)),
                "sword sharpness");
        Item[] diamond = {
            Items.DIAMOND_HELMET,
            Items.DIAMOND_CHESTPLATE,
            Items.DIAMOND_LEGGINGS,
            Items.DIAMOND_BOOTS
        };
        for (int index = 0; index < ARMOUR.length; index++) {
            ItemStack worn = player.getItemBySlot(ARMOUR[index]);
            assertTrue(
                    worn.is(diamond[index]), "Expected " + diamond[index] + " worn, found " + worn);
            assertEquals(2, level(context, Enchantments.PROTECTION, worn), "armour protection");
        }
        context.succeed();
    }

    /**
     * Every Meetup kit is one of three tiers, told apart by how many diamond pieces it has: fewer
     * diamond pieces come with more protection, sharpness, power and healing. Which pieces are
     * diamond and which blocks come with it vary too.
     */
    @GameTest(maxTicks = 100)
    public void meetupKitsAreRandomWithinFairTiers(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        // diamond pieces -> {protection, sharpness, power, golden apples, golden heads}
        Map<Integer, int[]> tiers =
                Map.of(
                        3, new int[] {1, 1, 1, 4, 2},
                        2, new int[] {2, 1, 2, 5, 2},
                        1, new int[] {2, 2, 3, 6, 3});
        Set<Integer> tiersSeen = new HashSet<>();
        Set<String> diamondSlotsSeen = new HashSet<>();
        Set<Item> blocksSeen = new HashSet<>();
        for (int roll = 0; roll < 80; roll++) {
            List<ItemStack> kit = LootUtils.roll(player, Minigames.MEETUP.defaultKit());
            int diamonds = 0;
            StringBuilder slots = new StringBuilder();
            int protection = -1;
            for (EquipmentSlot slot : ARMOUR) {
                List<ItemStack> pieces =
                        kit.stream()
                                .filter(
                                        stack ->
                                                player.getEquipmentSlotForItem(stack) == slot
                                                        && slot.getType()
                                                                == EquipmentSlot.Type
                                                                        .HUMANOID_ARMOR)
                                .toList();
                assertEquals(1, pieces.size(), "armour pieces for " + slot + " in " + kit);
                ItemStack piece = pieces.getFirst();
                String name = piece.getItem().toString();
                assertTrue(
                        name.contains("diamond_") || name.contains("iron_"),
                        "Expected iron or diamond armour, found " + piece);
                if (name.contains("diamond_")) {
                    diamonds++;
                    slots.append(slot.getName());
                }
                int level = level(context, Enchantments.PROTECTION, piece);
                assertTrue(
                        protection == -1 || protection == level,
                        "Expected one protection level per kit.");
                protection = level;
            }
            int[] tier = tiers.get(diamonds);
            assertTrue(tier != null, "Expected 1 to 3 diamond pieces, found " + diamonds + ".");
            tiersSeen.add(diamonds);
            diamondSlotsSeen.add(slots.toString());
            assertEquals(tier[0], protection, "protection with " + diamonds + " diamond pieces");
            assertEquals(
                    tier[1],
                    level(context, Enchantments.SHARPNESS, single(kit, Items.DIAMOND_SWORD)),
                    "sharpness with " + diamonds + " diamond pieces");
            assertEquals(
                    tier[2],
                    level(context, Enchantments.POWER, single(kit, Items.BOW)),
                    "power with " + diamonds + " diamond pieces");
            int apples = 0;
            int heads = 0;
            for (ItemStack stack : kit) {
                if (!stack.is(Items.GOLDEN_APPLE)) {
                    continue;
                }
                if (stack.has(DataComponents.ITEM_NAME)
                        && stack.getHoverName().getString().equals("Golden Head")) {
                    heads += stack.getCount();
                    assertGoldenHead(stack);
                } else {
                    apples += stack.getCount();
                }
            }
            assertEquals(tier[3], apples, "golden apples with " + diamonds + " diamond pieces");
            assertEquals(tier[4], heads, "golden heads with " + diamonds + " diamond pieces");

            Map<Item, Integer> counts = counts(kit);
            for (Map.Entry<Item, Integer> common :
                    Map.of(
                                    Items.FISHING_ROD, 1,
                                    Items.ARROW, 32,
                                    Items.COOKED_BEEF, 64,
                                    Items.WATER_BUCKET, 2,
                                    Items.LAVA_BUCKET, 2,
                                    Items.FLINT_AND_STEEL, 1,
                                    Items.DIAMOND_AXE, 1,
                                    Items.DIAMOND_PICKAXE, 1)
                            .entrySet()) {
                assertEquals(
                        common.getValue(),
                        counts.getOrDefault(common.getKey(), 0),
                        "count of " + common.getKey());
            }
            int cobblestone = counts.getOrDefault(Items.COBBLESTONE, 0);
            int planks = counts.getOrDefault(Items.OAK_PLANKS, 0);
            assertEquals(64, cobblestone + planks, "building blocks");
            blocksSeen.add(cobblestone > 0 ? Items.COBBLESTONE : Items.OAK_PLANKS);
        }
        assertEquals(Set.of(1, 2, 3), tiersSeen, "tiers rolled");
        assertTrue(
                diamondSlotsSeen.size() > 3,
                "Expected the diamond pieces to vary, found " + diamondSlotsSeen + ".");
        assertEquals(Set.of(Items.COBBLESTONE, Items.OAK_PLANKS), blocksSeen, "blocks rolled");
        context.succeed();
    }

    /**
     * A natural arena's lobby and spawns stand on solid, dry ground inside its own border even with
     * a lake in its middle, and building is limited to the border.
     */
    @GameTest(maxTicks = 200)
    public void finalUhcArenaIsDryNaturalGroundInsideItsBorder(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        int centerX = -4_000_000 - level.getRandom().nextInt(10_000) * 64;
        int centerZ = 4_000_000 + level.getRandom().nextInt(10_000) * 64;
        int lake = 12;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = centerX - lake; x <= centerX + lake; x++) {
            for (int z = centerZ - lake; z <= centerZ + lake; z++) {
                level.getChunk(
                        SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                level.setBlock(pos.set(x, top - 1, z), Blocks.WATER.defaultBlockState(), 2);
            }
        }
        int size = Minigames.FINAL_UHC.setting("border_size").orElseThrow().defaultValue();
        NaturalArena arena = NaturalArena.at(level, centerX, centerZ, size);
        assertEquals(size, arena.size(), "border size");
        assertDryInside(level, arena, arena.lobbyPosition(), "lobby");
        for (int teams : new int[] {2, 8}) {
            List<Arena.Spawn> spawns = arena.spawns(teams);
            assertEquals(teams, spawns.size(), "spawn count");
            for (Arena.Spawn spawn : spawns) {
                assertDryInside(level, arena, spawn.position(), "spawn");
            }
        }
        BlockPos inside = BlockPos.containing(arena.lobbyPosition());
        assertTrue(arena.canBuild(inside), "Expected building inside the border.");
        assertTrue(
                !arena.canBuild(inside.offset(size, 0, 0)),
                "Expected no building outside the border.");
        context.succeed();
    }

    /**
     * Meetup: players can hurt each other the moment the match starts, the border shrinks on
     * schedule, and players outside it are hurt.
     */
    @GameTest(maxTicks = 300)
    public void meetupHasPvpFromTheStartAndAShrinkingBorder(GameTestHelper context)
            throws MatchException {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        Minigame meetup = Minigames.MEETUP;
        GameSetting countdown = meetup.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow();
        SettingsStorage.set(server, meetup, countdown, 0);
        SettingsStorage.set(server, meetup, MeetupGame.FIRST_SHRINK_TIME, 1);
        SettingsStorage.set(server, meetup, MeetupGame.SHRINK_INTERVAL, 2);
        SettingsStorage.set(server, meetup, MeetupGame.SHRINK_DURATION, 1);
        boolean pvp = server.getGameRules().get(GameRules.PVP);
        server.getGameRules().set(GameRules.PVP, true, server);
        int centerX = 4_000_000 + level.getRandom().nextInt(10_000) * 64;
        int centerZ = 4_000_000 + level.getRandom().nextInt(10_000) * 64;
        NaturalArena arena = NaturalArena.at(level, centerX, centerZ, 100);
        ServerPlayer attacker = loadedPlayer(context);
        ServerPlayer victim = loadedPlayer(context);
        Match match =
                MatchManager.open(
                        server,
                        meetup,
                        TeamLayout.parse("1v1").orElseThrow(),
                        null,
                        (unused, settings) -> arena);
        Runnable cleanup =
                () -> {
                    MatchManager.stop(match);
                    SettingsStorage.reset(server, meetup, countdown);
                    SettingsStorage.reset(server, meetup, MeetupGame.FIRST_SHRINK_TIME);
                    SettingsStorage.reset(server, meetup, MeetupGame.SHRINK_INTERVAL);
                    SettingsStorage.reset(server, meetup, MeetupGame.SHRINK_DURATION);
                    server.getGameRules().set(GameRules.PVP, pvp, server);
                };
        try {
            MatchManager.join(attacker, match, 1);
            MatchManager.join(victim, match, 2);
        } catch (MatchException | RuntimeException exception) {
            cleanup.run();
            throw exception;
        }
        context.runAfterDelay(
                3,
                () -> {
                    try {
                        assertEquals(MatchPhase.ACTIVE, match.phase(), "phase after the countdown");
                        assertEquals(100, arena.size(), "border before the first shrink");
                        victim.invulnerableTime = 0;
                        float before = victim.getHealth();
                        victim.hurtServer(
                                level, victim.damageSources().playerAttack(attacker), 4.0F);
                        assertTrue(
                                victim.getHealth() < before,
                                "Expected PvP damage at the start of the match.");
                    } catch (RuntimeException exception) {
                        cleanup.run();
                        throw exception;
                    }
                });
        // First shrink starts at 1 s and takes 1 s: 100 -> 75 by tick 40.
        context.runAfterDelay(
                50,
                () -> {
                    try {
                        assertEquals(75, arena.size(), "border after the first shrink");
                        assertTrue(
                                match.phase() == MatchPhase.ACTIVE,
                                "Expected the match still running.");
                        victim.setHealth(victim.getMaxHealth());
                        victim.invulnerableTime = 0;
                        victim.teleportTo(
                                centerX + 0.5 + 75 / 2.0 + 10, victim.getY(), centerZ + 0.5);
                    } catch (RuntimeException exception) {
                        cleanup.run();
                        throw exception;
                    }
                });
        context.runAfterDelay(
                53,
                () -> {
                    try {
                        assertTrue(
                                victim.getHealth() < victim.getMaxHealth(),
                                "Expected a player outside the border to be hurt.");
                        // Back inside, so the border does not kill them and end the match.
                        victim.setHealth(victim.getMaxHealth());
                        victim.teleportTo(centerX + 0.5, victim.getY(), centerZ + 0.5);
                    } catch (RuntimeException exception) {
                        cleanup.run();
                        throw exception;
                    }
                });
        // Second shrink starts at 3 s: 75 -> 50 by tick 80.
        context.runAfterDelay(
                90,
                () -> {
                    try {
                        assertEquals(50, arena.size(), "border after the second shrink");
                    } finally {
                        cleanup.run();
                    }
                    context.succeed();
                });
    }

    private static void assertGoldenHead(ItemStack stack) {
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        assertTrue(consumable != null, "Expected a golden head to be edible.");
        boolean regeneration = false;
        for (var effect : consumable.onConsumeEffects()) {
            if (effect instanceof ApplyStatusEffectsConsumeEffect apply) {
                for (MobEffectInstance instance : apply.effects()) {
                    if (instance.getEffect().equals(MobEffects.REGENERATION)
                            && instance.getAmplifier() == 1
                            && instance.getDuration() == 200) {
                        regeneration = true;
                    }
                }
            }
        }
        assertTrue(regeneration, "Expected a golden head to give Regeneration II for 10 s.");
    }

    private static void assertDryInside(
            ServerLevel level, NaturalArena arena, Vec3 position, String what) {
        BlockPos ground = BlockPos.containing(position).below();
        assertTrue(
                level.getFluidState(ground).isEmpty()
                        && level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP),
                "Expected the "
                        + what
                        + " "
                        + position
                        + " on solid, dry ground, found "
                        + level.getBlockState(ground)
                        + ".");
        assertTrue(
                arena.border().isWithinBounds(position),
                "Expected the " + what + " " + position + " inside the border.");
    }

    private static Map<Item, Integer> counts(List<ItemStack> stacks) {
        Map<Item, Integer> counts = new HashMap<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    private static ItemStack find(ServerPlayer player, Item item) {
        return player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(item))
                .findFirst()
                .orElseThrow(() -> failure("Expected " + item + " in the kit."));
    }

    private static ItemStack single(List<ItemStack> kit, Item item) {
        List<ItemStack> matches = kit.stream().filter(stack -> stack.is(item)).toList();
        assertEquals(1, matches.size(), "stacks of " + item);
        return matches.getFirst();
    }

    private static int level(
            GameTestHelper context, ResourceKey<Enchantment> enchantment, ItemStack stack) {
        return EnchantmentHelper.getItemEnchantmentLevel(
                context.getLevel()
                        .registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(enchantment),
                stack);
    }

    private static ServerPlayer loadedPlayer(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        return player;
    }

    private static void assertEquals(Object expected, Object actual, String description) {
        if (!expected.equals(actual)) {
            throw failure("Expected " + description + " " + expected + ", found " + actual + ".");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw failure(message);
        }
    }

    private static GameTestAssertException failure(String message) {
        return new GameTestAssertException(Component.literal(message), 0);
    }
}
