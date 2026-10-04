package io.github.brainage04.brainage_minigames;

import com.mojang.authlib.GameProfile;
import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.uhc.UhcCrafting;
import io.github.brainage04.brainage_minigames.game.uhc.UhcGame;
import io.github.brainage04.brainage_minigames.game.uhc.UhcProgression;
import io.github.brainage04.brainage_minigames.game.uhc.UhcSpawnGameTestFunctions;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** A real match, commands and vanilla grid clicks, not a recipe factory-only test. */
public final class UhcProgressionGameTestFunctions {
    private UhcProgressionGameTestFunctions() {}

    public static void progression(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        boolean oldMax = server.getGameRules().get(UhcProgression.MAX_ALL);
        boolean oldUnlimited = server.getGameRules().get(UhcProgression.UNLIMITED_CRAFTS);
        var countdown = Minigames.UHC.setting(GameSetting.COUNTDOWN_SECONDS).orElseThrow();
        int oldCountdown = SettingsStorage.resolve(server, Minigames.UHC).get(countdown);
        int oldGrace = SettingsStorage.resolve(server, Minigames.UHC).get(UhcGame.GRACE_PERIOD);
        List<ServerPlayer> players = new ArrayList<>();
        Match[] opened = new Match[1];
        Runnable cleanup = () -> {
            if (opened[0] != null) MatchManager.stop(opened[0]);
            for (ServerPlayer player : players) {
                server.getPlayerList().remove(player);
                var root = server.getCommandStorage().get(UhcProgression.STORAGE);
                root.remove(player.getUUID().toString()); server.getCommandStorage().set(UhcProgression.STORAGE, root);
            }
            SettingsStorage.set(server, Minigames.UHC, countdown, oldCountdown);
            SettingsStorage.set(server, Minigames.UHC, UhcGame.GRACE_PERIOD, oldGrace);
            server.getGameRules().set(UhcProgression.MAX_ALL, oldMax, server);
            server.getGameRules().set(UhcProgression.UNLIMITED_CRAFTS, oldUnlimited, server);
        };
        context.runBeforeTestEnd(cleanup);
        try {
            server.getGameRules().set(UhcProgression.MAX_ALL, false, server);
            server.getGameRules().set(UhcProgression.UNLIMITED_CRAFTS, false, server);
            SettingsStorage.set(server, Minigames.UHC, countdown, 0);
            SettingsStorage.set(server, Minigames.UHC, UhcGame.GRACE_PERIOD, 0);
            for (int i = 0; i < 4; i++) players.add(connected(context, "Perks" + i));
            ServerPlayer crafter = players.getFirst();
            check(command(crafter, "minigames uhc unlock weaponsmith recipe1") == 0, "purchase without coins succeeded");
            UhcProgression.award(server, crafter.getUUID(), 125);
            check(command(crafter, "minigames uhc unlock weaponsmith recipe1") == 1, "funded root purchase failed");
            check(UhcProgression.coins(server, crafter.getUUID()) == 0, "root purchase did not deduct exact cost");
            check(command(crafter, "minigames uhc unlock weaponsmith recipe1") == 0 && UhcProgression.coins(server, crafter.getUUID()) == 0, "duplicate purchase changed balance");
            UhcProgression.award(server, crafter.getUUID(), 2_000_000);
            check(command(crafter, "minigames uhc unlock cooking recipe4") == 0, "ultimate bypassed prerequisites");
            check(UhcProgression.bought(server, crafter.getUUID(), UhcProgression.Tree.WEAPONSMITH, "recipe1"), "persistent entitlement missing");
            var vorpal = recipe("vorpal_sword");
            check(UhcCrafting.preview(crafter, vorpal).isEmpty(), "unlocked recipe available outside UHC");
            check(command(crafter, "minigames uhc unlock extras cornucopia") == 0, "Extra Ultimate bypassed its profession prerequisites");
            long kitBalance = UhcProgression.coins(server, crafter.getUUID());
            check(command(crafter, "minigames uhc kit_upgrade ecologist level1") == 1 && UhcProgression.coins(server, crafter.getUUID()) == kitBalance - 2500, "kit upgrade did not charge its discounted price");
            check(command(crafter, "minigames uhc kit ecologist") == 1, "kit selection failed");
            Match match = MatchManager.open(server, Minigames.UHC, TeamLayout.parse("2v2").orElseThrow(), null);
            opened[0] = match;
            for (int i = 0; i < 4; i++) MatchManager.join(players.get(i), match, i < 2 ? 1 : 2);
            UhcSpawnGameTestFunctions.awaitReady(context, match, () -> {
                try {
            MatchManager.tick();
            check(match.phase() == MatchPhase.ACTIVE, "zero-countdown match did not start");
            check(count(crafter, Items.OAK_LOG) == 16 && count(crafter, Items.LILY_PAD) == 16, "selected upgraded Ecologist kit was not applied at match start");
            check(UhcCrafting.preview(crafter, recipe("light_apple")).isEmpty(), "locked Light Apple available");
            for (var node : UhcProgression.nodes(UhcProgression.Tree.COOKING)) {
                if (!node.id().equals("prestige")) check(command(crafter, "minigames uhc unlock cooking " + node.id()) == 1, "tree purchase failed: " + node.id());
            }
            for (var node : UhcProgression.nodes(UhcProgression.Tree.ENGINEERING)) if (!node.id().equals("prestige")) check(command(crafter, "minigames uhc unlock engineering " + node.id()) == 1, "engineering prerequisite purchase failed");
            long extraBalance = UhcProgression.coins(server, crafter.getUUID());
            check(command(crafter, "minigames uhc unlock extras cornucopia") == 1 && UhcProgression.coins(server, crafter.getUUID()) == extraBalance - 50000, "Extra Ultimate purchase did not charge exact coins");
            CraftingMenu menu = grid(crafter);
            fill(menu, recipe("light_apple"), crafter, 2);
            check(menu.getResultSlot().getItem().is(Items.GOLDEN_APPLE), "four-gold Light Apple missing");
            menu.clicked(0, 0, ContainerInput.PICKUP, crafter);
            check(menu.getCarried().is(Items.GOLDEN_APPLE) && menu.getCarried().getCount() == 1, "Light Apple click did not yield one apple");
            check(menu.getInputGridSlots().get(1).getItem().getCount() == 1 && menu.getInputGridSlots().get(4).getItem().getCount() == 1, "Light Apple did not consume exactly four gold and one apple");
            check(menu.getResultSlot().getItem().isEmpty(), "ultimate craft limit was not enforced");
            menu.setCarried(ItemStack.EMPTY);
            check(command(crafter, "minigames uhc unlock cooking prestige") == 1, "prestige purchase failed");
            fill(menu, recipe("light_apple"), crafter, 1);
            check(menu.getResultSlot().getItem().is(Items.GOLDEN_APPLE), "prestige did not add an ultimate craft");
            menu.clicked(0, 0, ContainerInput.PICKUP, crafter);
            menu.setCarried(ItemStack.EMPTY);
            fill(menu, recipe("light_apple"), crafter, 1);
            check(menu.getResultSlot().getItem().isEmpty(), "prestige allowed a third ultimate craft");

            server.getGameRules().set(UhcProgression.MAX_ALL, true, server);
            check(UhcProgression.level(crafter, UhcProgression.Tree.SURVIVALISM) == 10, "max-all did not max unpurchased perks");
            fill(menu, recipe("artemis_bow"), crafter, 2);
            check(menu.getResultSlot().getItem().is(Items.BOW), "max-all did not unlock unpurchased Extra Ultimate");
            menu.clicked(0, 0, ContainerInput.PICKUP, crafter); menu.setCarried(ItemStack.EMPTY);
            check(menu.getResultSlot().getItem().isEmpty(), "Extra Ultimate incorrectly gained prestige's second craft");
            fill(menu, recipe("sharpness_book"), crafter, 1);
            ItemStack book = menu.getResultSlot().getItem();
            check(book.is(Items.ENCHANTED_BOOK) && book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).getLevel(crafter.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS)) == 1, "paper/flint/sword recipe did not give Sharpness I book");
            fill(menu, recipe("golden_head"), crafter, 1);
            ItemStack head = menu.getResultSlot().getItem();
            check(head.is(Items.PLAYER_HEAD) && head.has(DataComponents.CONSUMABLE), "Golden Head recipe missing edible head");
            menu.clicked(0, 0, ContainerInput.PICKUP, crafter);
            crafter.removeAllEffects();
            menu.getCarried().finishUsingItem(crafter.level(), crafter);
            check(crafter.getEffect(MobEffects.REGENERATION).getDuration() == 200 && crafter.getEffect(MobEffects.SPEED).getDuration() == 300, "Golden Head healing/Celerity effects incorrect");
            menu.setCarried(ItemStack.EMPTY);
            fill(menu, recipe("obsidian"), crafter, 1);
            menu.clicked(0, 0, ContainerInput.PICKUP, crafter);
            check(menu.getInputGridSlots().stream().filter(slot -> slot.getItem().is(Items.BUCKET)).count() == 2, "bucket recipe failed to return both buckets");
            menu.setCarried(ItemStack.EMPTY);
            crafter.getInventory().clearContent();
            fill(menu, recipe("iron_economy"), crafter, 5);
            menu.clicked(0, 0, ContainerInput.QUICK_MOVE, crafter);
            check(count(crafter, Items.IRON_INGOT) == 40, "shift craft bypassed four-craft prestige limit or lost output");
            check(menu.getInputGridSlots().getFirst().getItem().getCount() == 1, "shift craft consumed wrong ingredient count");
            server.getGameRules().set(UhcProgression.UNLIMITED_CRAFTS, true, server);
            crafter.getInventory().clearContent();
            fill(menu, recipe("iron_economy"), crafter, 5);
            menu.clicked(0, 0, ContainerInput.QUICK_MOVE, crafter);
            check(count(crafter, Items.IRON_INGOT) == 50, "unlimited-crafts rule retained the prestige cap");
            fill(menu, recipe("light_apple"), crafter, 1);
            check(menu.getResultSlot().getItem().is(Items.GOLDEN_APPLE), "unlimited-crafts rule retained the ultimate cap");
            fill(menu, recipe("artemis_bow"), crafter, 1);
            check(menu.getResultSlot().getItem().is(Items.BOW), "unlimited-crafts did not lift Extra Ultimate cap");
            server.getGameRules().set(UhcProgression.MAX_ALL, false, server);
            check(UhcProgression.level(crafter, UhcProgression.Tree.SURVIVALISM) == 0 && !UhcProgression.bought(server, crafter.getUUID(), UhcProgression.Tree.BLOODCRAFT, "recipe1"), "max-all persisted fake purchases");
            fill(menu, recipe("golden_head"), crafter, 1);
            check(menu.getResultSlot().getItem().isEmpty(), "disabling max-all retained recipe access");
            crafter.closeContainer();

            long[] before = players.stream().mapToLong(p -> UhcProgression.coins(server, p.getUUID())).toArray();
            setTicks(match, 5 * 60 * 20 - 2);
            MatchManager.tick();
            check(UhcProgression.coins(server, crafter.getUUID()) == before[0], "survival reward arrived before five minutes");
            MatchManager.tick();
            check(UhcProgression.coins(server, crafter.getUUID()) == before[0] + 10, "five-minute survival reward missing");
            MatchManager.tick();
            check(UhcProgression.coins(server, crafter.getUUID()) == before[0] + 10, "survival reward repeated");
            Vec3 returnPos = crafter.position();
            var uhc = crafter.level();
            var nether = server.getLevel(ModDimensions.UHC_NETHER);
            PlayerUtils.teleport(crafter, nether, new Vec3(0, 100, 0), 0);
            MatchManager.tick(); MatchManager.tick();
            check(UhcProgression.coins(server, crafter.getUUID()) == before[0] + 25, "Nether entry reward missing or repeated");
            PlayerUtils.teleport(crafter, uhc, returnPos, 0);
            PlayerUtils.teleport(crafter, nether, new Vec3(0, 100, 0), 0);
            MatchManager.tick();
            check(UhcProgression.coins(server, crafter.getUUID()) == before[0] + 25, "Nether portal cycling farmed coins");
            PlayerUtils.teleport(crafter, uhc, returnPos, 0);
            ServerPlayer teammate = players.get(1), firstVictim = players.get(2), lastVictim = players.get(3);
            PlayerUtils.teleport(teammate, uhc, returnPos.add(200, 0, 0), 0);
            check(MatchManager.allowDamage(firstVictim, firstVictim.damageSources().playerAttack(crafter)), "active enemy damage refused");
            MatchManager.allowDeath(firstVictim);
            check(UhcProgression.coins(server, crafter.getUUID()) == before[0] + 100 && UhcProgression.coins(server, teammate.getUUID()) == before[1] + 60, "kill, first-blood award or inclusive 200-block team radius incorrect");
            PlayerUtils.teleport(teammate, uhc, returnPos.add(201, 0, 0), 0);
            MatchManager.allowDamage(lastVictim, lastVictim.damageSources().playerAttack(crafter));
            MatchManager.allowDeath(lastVictim);
            MatchManager.tick();
            check(match.phase() == MatchPhase.ENDED, "eliminating opposition did not finish match");
            check(UhcProgression.coins(server, crafter.getUUID()) == before[0] + 300 && UhcProgression.coins(server, teammate.getUUID()) == before[1] + 210, "win award or out-of-range kill exclusion incorrect");
            MatchManager.tick();
            check(UhcProgression.coins(server, crafter.getUUID()) == before[0] + 300, "win coins awarded twice");
            server.getGameRules().set(UhcProgression.MAX_ALL, true, server);
            check(UhcCrafting.preview(crafter, vorpal).isEmpty(), "max-all leaked recipe into ended match");
            context.succeed();
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new RuntimeException(exception);
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

    static void setTicks(Match match, int ticks) throws Exception { Field field = Match.class.getDeclaredField("phaseTicks"); field.setAccessible(true); field.setInt(match, ticks); }
    static UhcCrafting.Recipe recipe(String id) { return UhcCrafting.recipes().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow(); }
    private static int count(ServerPlayer player, Item item) { int count = 0; for (ItemStack stack : player.getInventory().getNonEquipmentItems()) if (stack.is(item)) count += stack.getCount(); return count; }
    private static int command(ServerPlayer player, String command) throws Exception { return player.level().getServer().getCommands().getDispatcher().execute(command, player.createCommandSourceStack()); }
    static void check(boolean condition, String message) { if (!condition) throw new GameTestAssertException(Component.literal(message), 0); }
    static CraftingMenu grid(ServerPlayer player) {
        BlockPos pos = player.blockPosition(); player.level().setBlockAndUpdate(pos.below(), Blocks.CRAFTING_TABLE.defaultBlockState());
        CraftingMenu menu = new CraftingMenu(44, player.getInventory(), ContainerLevelAccess.create(player.level(), pos.below())); player.containerMenu = menu; return menu;
    }
    static void fill(CraftingMenu menu, UhcCrafting.Recipe recipe, ServerPlayer player, int count) {
        for (int i = 0; i < 9; i++) {
            Item item = recipe.grid()[i];
            ItemStack stack = item == Items.AIR ? ItemStack.EMPTY : item == Items.PLAYER_HEAD ? UhcCrafting.playerHead(player) : new ItemStack(item, count);
            if (!stack.isEmpty()) stack.setCount(count);
            menu.getInputGridSlots().get(i).set(stack);
        }
        menu.slotsChanged(menu.getInputGridSlots().getFirst().container);
    }
    static ServerPlayer connected(GameTestHelper context, String name) {
        MinecraftServer server = context.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(server, context.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket()); return player;
    }
}
