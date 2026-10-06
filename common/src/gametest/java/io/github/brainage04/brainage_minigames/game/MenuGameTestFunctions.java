package io.github.brainage04.brainage_minigames.game;

import io.github.brainage04.brainage_minigames.TestPlayers;
import io.github.brainage04.brainage_minigames.TestPlayers.ChatPlayer;
import io.github.brainage04.brainage_minigames.feedback.FeedbackReminders;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.menu.Icon;
import io.github.brainage04.brainage_minigames.menu.MainMenu;
import io.github.brainage04.brainage_minigames.menu.Menu;
import io.github.brainage04.brainage_minigames.menu.MenuItems;
import io.github.brainage04.brainage_minigames.menu.MenuView;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Chest menus driven the way a client drives them: every click goes through the server's container
 * click packet handler, starting from {@code /minigames}, the game menu compass or {@code /duel}.
 */
public final class MenuGameTestFunctions {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private MenuGameTestFunctions() {}

    /**
     * No container input takes an item out of a menu or puts one into the player's inventory; only
     * plain and shift clicks on a button run it.
     */
    public static void clicksNeverMoveItems(GameTestHelper context) {
        ChatPlayer player = player(context, "Clicker");
        try {
            player.setGameMode(GameType.CREATIVE);
            player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 5));
            AtomicInteger presses = new AtomicInteger();
            Menu menu = new Menu("Test Menu", 3);
            menu.set(13, Icon.of(Items.DIAMOND).name("Button", ChatFormatting.GREEN),
                    (clicker, click) -> presses.incrementAndGet());
            menu.open(player);
            int button = 13;
            int hotbar = 27 + 27; // the first hotbar slot below a three-row chest
            for (int key = 0; key < 9; key++) {
                click(player, button, key, ContainerInput.SWAP);
            }
            click(player, button, 40, ContainerInput.SWAP);
            click(player, button, 2, ContainerInput.CLONE);
            click(player, button, 0, ContainerInput.THROW);
            click(player, button, 1, ContainerInput.THROW);
            click(player, button, 0, ContainerInput.PICKUP_ALL);
            for (int type = 0; type < 3; type++) {
                click(player, -999, type * 4, ContainerInput.QUICK_CRAFT);
                click(player, button, type * 4 + 1, ContainerInput.QUICK_CRAFT);
                click(player, hotbar + 1, type * 4 + 1, ContainerInput.QUICK_CRAFT);
                click(player, -999, type * 4 + 2, ContainerInput.QUICK_CRAFT);
            }
            click(player, hotbar, 0, ContainerInput.PICKUP);
            click(player, button, 0, ContainerInput.PICKUP);
            click(player, -999, 0, ContainerInput.PICKUP);
            click(player, hotbar, 0, ContainerInput.QUICK_MOVE);
            click(player, hotbar + 1, 0, ContainerInput.PICKUP);
            click(player, button, 1, ContainerInput.PICKUP);
            click(player, button, 0, ContainerInput.QUICK_MOVE);
            click(player, button, 1, ContainerInput.QUICK_MOVE);

            check(presses.get() == 4, "expected 4 button presses, found " + presses.get());
            check(player.containerMenu.getCarried().isEmpty(), "cursor holds " + player.containerMenu.getCarried());
            MenuView view = view(player);
            check(view.getSlot(button).getItem().is(Items.DIAMOND), "button left the menu");
            for (int slot = 0; slot < 27; slot++) {
                check(slot == button || view.getSlot(slot).getItem().isEmpty(), "menu slot " + slot + " filled");
            }
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (slot == 0) {
                    check(stack.is(Items.EMERALD) && stack.getCount() == 5, "hotbar emeralds became " + stack);
                } else {
                    check(stack.isEmpty(), "inventory slot " + slot + " gained " + stack);
                }
            }
            check(droppedNear(player).isEmpty(), "items were dropped: " + droppedNear(player));
        } finally {
            player.closeContainer();
            TestPlayers.disconnect(player);
        }
        context.succeed();
    }

    /** The hub compass opens the main menu, and neither dropping nor throwing it lets it go. */
    public static void hubItemOpensMenuAndStays(GameTestHelper context) {
        ChatPlayer player = player(context, "Hub");
        try {
            player.setGameMode(GameType.ADVENTURE);
            MenuItems.giveHubItems(player);
            MenuItems.giveHubItems(player);
            check(compasses(player) == 1, "expected one game menu, found " + compasses(player));
            ItemStack compass = player.getInventory().getItem(0);
            check(MenuItems.isMenuItem(compass), "slot 0 holds " + compass);
            player.getInventory().setSelectedSlot(0);
            player.gameMode.useItem(player, player.level(), compass, InteractionHand.MAIN_HAND);
            check(title(player).equals(MainMenu.TITLE), "using the compass opened " + title(player));
            player.closeContainer();

            player.drop(false);
            player.drop(true);
            check(MenuItems.isMenuItem(player.getInventory().getItem(0)), "dropping moved the compass");
            player.inventoryMenu.clicked(36, 0, ContainerInput.THROW, player);
            player.inventoryMenu.clicked(36, 0, ContainerInput.PICKUP, player);
            player.inventoryMenu.clicked(-999, 0, ContainerInput.PICKUP, player);
            check(compasses(player) == 1, "throwing lost the compass");
            check(droppedNear(player).isEmpty(), "the compass was dropped: " + droppedNear(player));
        } finally {
            TestPlayers.disconnect(player);
        }
        context.succeed();
    }

    /** Browse, layout, kit and map choices open the match the player chose, with them in it. */
    public static void openFlowOpensChosenMatch(GameTestHelper context) throws MatchException {
        ChatPlayer player = player(context, "Opener");
        MinecraftServer server = context.getLevel().getServer();
        Match match = null;
        try {
            List<Identifier> maps = MapArena.maps(server, "bridge");
            Identifier small = maps.stream().filter(map -> MapArena.teamSlots(server, map) < 4).findFirst().orElse(null);
            MainMenu.open(player);
            clickNamed(player, "Play a Game");
            clickNamed(player, "Goals");
            clickNamed(player, "Bridge");
            check(title(player).equals("Choose a Layout"), "Bridge opened " + title(player));
            if (small != null) {
                clickNamed(player, "1v1v1v1");
                click(player, 10, 0, ContainerInput.PICKUP);
                clickNamed(player, title(MapArena.nameOf(small)));
                check(title(player).equals("Choose a Map"), "a map without room for four teams was accepted");
                clickNamed(player, "Go Back");
                clickNamed(player, "Go Back");
            }
            for (Identifier chosen : maps) {
                if (MapArena.teamSlots(server, chosen) < 2) continue;
                if (!(player.containerMenu instanceof MenuView)) {
                    MainMenu.open(player);
                    clickNamed(player, "Play a Game");
                    clickNamed(player, "Bridge");
                }
                clickNamed(player, "2v2");
                check(title(player).equals("Choose a Kit"), "2v2 opened " + title(player));
                click(player, 10, 0, ContainerInput.PICKUP);
                check(title(player).equals("Choose a Map"), "the kit opened " + title(player));
                clickNamed(player, title(MapArena.nameOf(chosen)));
                check(title(player).equals("Match Setup"), "the map opened " + title(player));
                clickNamed(player, "Open Match");

                match = MatchManager.matchOf(player.getUUID()).orElseThrow(() -> failure("no match opened"));
                check(match.isOwner(player.getUUID()), "the opener does not own the match");
                check(match.game() == Minigames.BRIDGE, "opened " + match.game().id());
                check(match.layout().toString().equals("2v2"), "opened " + match.layout());
                check(match.mapName().orElse("").equals(MapArena.nameOf(chosen)),
                        "chose " + MapArena.nameOf(chosen) + " but opened on " + match.mapName());
                check(match.isWaiting(player.getUUID()), "the opener is not waiting in the lobby");
                check(!(player.containerMenu instanceof MenuView), "the menu stayed open");
                check(MenuItems.kind(player.getInventory().getItem(0)).orElse(null) == MenuItems.Kind.VOTE_START,
                        "the lobby hotbar lacks the vote item");
                MatchManager.stop(match);
                match = null;
            }
        } finally {
            if (match != null) MatchManager.stop(match);
            TestPlayers.disconnect(player);
        }
        context.succeed();
    }

    /**
     * Kits read as their names, with a line on what they give and never a raw id, in the kit menu,
     * the match setup and {@code /minigames kit list}.
     */
    public static void kitsShowNamesAndContents(GameTestHelper context) {
        ChatPlayer player = player(context, "Kits");
        try {
            MainMenu.open(player);
            clickNamed(player, "Play a Game");
            clickNamed(player, "Classic");
            clickNamed(player, "1v1");
            check(title(player).equals("Choose a Kit"), "1v1 opened " + title(player));
            List<String> names = new java.util.ArrayList<>();
            Menu menu = view(player).menu();
            for (int slot = 0; slot < menu.size(); slot++) {
                String name = name(player, slot);
                if (name.isEmpty()) continue;
                names.add(name);
                check(!lore(player, slot).contains("brainage_minigames:") && !lore(player, slot).contains("kits/"),
                        name + " shows an id: " + lore(player, slot));
            }
            for (String kit : List.of("Classic", "UHC Starter", "BuildUHC", "FinalUHC", "No Debuff", "Instant Firework Crossbow")) {
                check(names.contains(kit), "no kit named " + kit + " in " + names);
            }
            check(lore(player, slotNamed(player, "No Debuff")).replace('\n', ' ').contains("30 healing splashes"),
                    "No Debuff does not say what it gives: " + lore(player, slotNamed(player, "No Debuff")));
            check(lore(player, slotNamed(player, "Classic")).contains("The game's own kit"),
                    "Classic's own kit is not marked: " + lore(player, slotNamed(player, "Classic")));

            clickNamed(player, "No Debuff");
            check(title(player).equals("Match Setup"), "the kit opened " + title(player));
            check(slotNamedOrMinus(player, "Kit: No Debuff") >= 0, "the setup does not name the kit No Debuff");

            player.closeContainer();
            TestPlayers.setOperator(player, true);
            player.messages.clear();
            context.getLevel().getServer().getCommands()
                    .performPrefixedCommand(player.createCommandSourceStack(), "minigames kit list");
            List<String> lines = player.messages.stream().map(Component::getString).toList();
            check(lines.contains("UHC Starter (brainage_minigames:kits/uhc_starter) - "
                            + "Each player's chosen UHC kit (/minigames uhc kit); stone tools by default."),
                    "kit list: " + lines);
            check(lines.stream().anyMatch(line -> line.startsWith("No Debuff (brainage_minigames:kits/no_debuff) - ")),
                    "kit list lacks No Debuff: " + lines);
        } finally {
            TestPlayers.setOperator(player, false);
            player.closeContainer();
            TestPlayers.disconnect(player);
        }
        context.succeed();
    }

    /** A custom layout with a bot in a chosen team's slot; bot options only with a provider. */
    public static void customLayoutWithBotSlots(GameTestHelper context) throws MatchException {
        ChatPlayer player = player(context, "Custom");
        MinecraftServer server = context.getLevel().getServer();
        TestBotProvider provider = null;
        Match match = null;
        try {
            MainMenu.open(player);
            clickNamed(player, "Play a Game");
            clickNamed(player, "Classic");
            clickNamed(player, "Custom Layout");
            clickNamed(player, "Red Team");
            clickNamed(player, "Add a Team");
            check(view(player).menu().icon(10).getCount() == 2, "the red team did not grow to two");
            clickNamed(player, "Use This Layout");
            click(player, 10, 0, ContainerInput.PICKUP);
            check(title(player).equals("Match Setup"), "the kit opened " + title(player));
            int blueSlot = Menu.slot(2, 3);
            check(lore(player, blueSlot).contains("Blue Team"), "slot " + blueSlot + " is " + lore(player, blueSlot));
            check(lore(player, blueSlot).contains("Bots are not available for this game."),
                    "bot options shown without a provider: " + lore(player, blueSlot));
            click(player, blueSlot, 0, ContainerInput.PICKUP);
            check(name(player, blueSlot).equals("Open Slot"), "a bot was added without a provider");

            provider = TestBotProvider.register(server);
            clickNamed(player, "Join the Match");
            clickNamed(player, "Join the Match");
            click(player, blueSlot, 0, ContainerInput.PICKUP);
            check(name(player, blueSlot).equals("Bot"), "the blue slot is " + name(player, blueSlot));
            clickNamed(player, "Open Match");

            match = MatchManager.matchOf(player.getUUID()).orElseThrow(() -> failure("no match opened"));
            check(match.layout().toString().equals("2v1v1"), "opened " + match.layout());
            check(match.reservedBots(2) == 1 && match.reservedBots() == 1,
                    "reserved bots: blue " + match.reservedBots(2) + ", total " + match.reservedBots());
            check(match.phase() == MatchPhase.LOBBY && match.isWaiting(player.getUUID()), "the lobby is " + match.phase());
            check(match.freeSlots(1) == 1 && match.freeSlots(2) == 0 && match.freeSlots(3) == 1,
                    "free slots " + match.freeSlots(1) + "/" + match.freeSlots(2) + "/" + match.freeSlots(3));
        } finally {
            if (match != null) MatchManager.stop(match);
            if (provider != null) provider.close();
            TestPlayers.disconnect(player);
        }
        context.succeed();
    }

    /** The match list joins a team, watches, and lets only the owner stop the match. */
    public static void matchListJoinsWatchesAndManages(GameTestHelper context) throws MatchException {
        ChatPlayer owner = player(context, "Owner");
        ChatPlayer joiner = player(context, "Joiner");
        ChatPlayer watcher = player(context, "Watcher");
        Match match = null;
        try {
            match = MatchService.open(owner, Minigames.CLASSIC, TeamLayout.parse("1v1").orElseThrow(), null);
            String entry = "#%d Classic 1v1".formatted(match.id());

            MainMenu.open(joiner);
            clickNamed(joiner, "Open Matches");
            clickNamed(joiner, entry);
            check(title(joiner).equals("Match #" + match.id()), "the entry opened " + title(joiner));
            check(slotNamedOrMinus(joiner, "Stop Match") < 0, "a stranger may stop the match");
            clickNamed(joiner, "Join Blue Team");
            check(match.isWaiting(joiner.getUUID()), "the joiner is not waiting");

            MainMenu.open(watcher);
            clickNamed(watcher, "Open Matches");
            clickNamed(watcher, entry);
            clickNamed(watcher, "Watch");
            check(match.involves(watcher.getUUID()) && watcher.gameMode() == GameType.SPECTATOR,
                    "the watcher is not spectating");

            MainMenu.open(owner);
            clickNamed(owner, "Open Matches");
            clickNamed(owner, entry);
            clickNamed(owner, "Stop Match");
            check(MatchManager.get(match.id()).isPresent(), "a plain click stopped the match");
            shiftClickNamed(owner, "Stop Match");
            check(MatchManager.get(match.id()).isEmpty(), "shift-clicking Stop Match left it running");
            match = null;
            check(title(owner).equals("Open Matches"), "stopping opened " + title(owner));
        } finally {
            if (match != null) MatchManager.stop(match);
            TestPlayers.disconnect(owner, joiner, watcher);
        }
        context.succeed();
    }

    /**
     * The duel builder challenges a chosen player, right-clicking a player with the game menu
     * challenges them, and a lineup of bots starts straight away.
     */
    public static void duelBuilderChallengesAndStartsWithBots(GameTestHelper context) {
        ChatPlayer alice = player(context, "Alice");
        ChatPlayer bob = player(context, "Bob");
        ChatPlayer carol = player(context, "Carol");
        MinecraftServer server = context.getLevel().getServer();
        TestBotProvider provider = null;
        try {
            server.getCommands().performPrefixedCommand(alice.createCommandSourceStack(), "duel");
            check(title(alice).equals("Duel: Choose a Game"), "/duel opened " + title(alice));
            clickNamed(alice, "Classic");
            clickNamed(alice, "1v1");
            click(alice, 10, 0, ContainerInput.PICKUP);
            check(title(alice).equals("Duel Lineup"), "the kit opened " + title(alice));
            int opponent = Menu.slot(2, 2);
            click(alice, opponent, 0, ContainerInput.PICKUP);
            check(title(alice).equals("Choose a Player"), "the empty slot opened " + title(alice));
            clickNamed(alice, bob.getScoreboardName());
            check(name(alice, opponent).equals(bob.getScoreboardName()), "the slot holds " + name(alice, opponent));
            clickNamed(alice, "Send Challenge");
            check(DuelRequests.challengersOf(bob.getUUID()).equals(List.of(alice.getScoreboardName())),
                    "challengers of Bob: " + DuelRequests.challengersOf(bob.getUUID()));
            DuelRequests.cancel(alice);

            MenuItems.giveHubItems(alice);
            alice.getInventory().setSelectedSlot(0);
            alice.interactOn(bob, InteractionHand.MAIN_HAND, Vec3.ZERO);
            check(title(alice).equals("Duel " + bob.getScoreboardName()), "right-clicking Bob opened " + title(alice));
            clickNamed(alice, "Classic");
            clickNamed(alice, "1v1");
            click(alice, 10, 0, ContainerInput.PICKUP);
            check(name(alice, opponent).equals(bob.getScoreboardName()), "Bob is not in the lineup");
            clickNamed(alice, "Send Challenge");
            check(DuelRequests.challengersOf(bob.getUUID()).equals(List.of(alice.getScoreboardName())),
                    "challengers of Bob after right-clicking: " + DuelRequests.challengersOf(bob.getUUID()));
            DuelRequests.cancel(alice);

            provider = TestBotProvider.register(server);
            MainMenu.openDuelBuilder(carol);
            clickNamed(carol, "Classic");
            clickNamed(carol, "1v1");
            click(carol, 10, 0, ContainerInput.PICKUP);
            click(carol, opponent, 1, ContainerInput.PICKUP);
            check(name(carol, opponent).equals("Bot"), "right-clicking the slot made " + name(carol, opponent));
            clickNamed(carol, "Start Match");
            Match match = MatchManager.matchOf(carol.getUUID()).orElseThrow(() -> failure("the bot duel did not open"));
            check(match.isPrivate() && match.phase() != MatchPhase.LOBBY, "the bot duel is " + match.phase());
            MatchManager.stop(match);
        } catch (MatchException exception) {
            throw failure(exception.getMessage());
        } finally {
            DuelRequests.clear();
            if (provider != null) provider.close();
            TestPlayers.disconnect(alice, bob, carol);
        }
        context.succeed();
    }

    /** The lobby's vote item votes and its leave item leaves; the settings menu toggles reminders. */
    public static void lobbyItemsAndFeedbackToggle(GameTestHelper context) throws MatchException {
        ChatPlayer alice = player(context, "Voter");
        ChatPlayer bob = player(context, "Leaver");
        MinecraftServer server = context.getLevel().getServer();
        Match match = null;
        try {
            match = MatchService.open(alice, Minigames.CLASSIC, TeamLayout.FREE_FOR_ALL, null);
            MatchManager.join(alice, match, 0);
            MatchManager.join(bob, match, 0);
            ItemStack vote = alice.getInventory().getItem(0);
            check(MenuItems.kind(vote).orElse(null) == MenuItems.Kind.VOTE_START, "slot 0 holds " + vote);
            alice.gameMode.useItem(alice, alice.level(), vote, InteractionHand.MAIN_HAND);
            check(match.hasVotedStart(alice.getUUID()), "using the vote item did not vote");
            check(match.phase() == MatchPhase.LOBBY, "one vote of two started the match");
            ItemStack leave = bob.getInventory().getItem(8);
            check(MenuItems.kind(leave).orElse(null) == MenuItems.Kind.LEAVE, "slot 8 holds " + leave);
            bob.gameMode.useItem(bob, bob.level(), leave, InteractionHand.MAIN_HAND);
            check(!match.involves(bob.getUUID()), "using the leave item did not leave");
            check(bob.getInventory().isEmpty(), "leaving kept lobby items: " + bob.getInventory().getItem(8));

            FeedbackReminders.setEnabled(server, bob.getUUID(), true);
            MainMenu.open(bob);
            clickNamed(bob, "Settings");
            clickNamed(bob, "Feedback Reminders");
            check(!FeedbackReminders.enabled(server, bob.getUUID()), "the toggle did not turn reminders off");
            check(lore(bob, Menu.slot(2, 3)).contains("Currently: DISABLED"), "the toggle shows " + lore(bob, Menu.slot(2, 3)));
            clickNamed(bob, "Feedback Reminders");
            check(FeedbackReminders.enabled(server, bob.getUUID()), "the toggle did not turn reminders back on");
        } finally {
            if (match != null) MatchManager.stop(match);
            TestPlayers.disconnect(alice, bob);
        }
        context.succeed();
    }

    // Helpers

    private static ChatPlayer player(GameTestHelper context, String name) {
        return TestPlayers.chat(context, name + NAMES.incrementAndGet());
    }

    /** Sends a container click as a client would, with no predicted changes. */
    private static void click(ServerPlayer player, int slot, int button, ContainerInput input) {
        AbstractContainerMenu menu = player.containerMenu;
        player.connection.handleContainerClick(new ServerboundContainerClickPacket(
                menu.containerId, menu.getStateId(), (short) slot, (byte) button, input,
                Int2ObjectMaps.emptyMap(), HashedStack.EMPTY));
    }

    private static void clickNamed(ServerPlayer player, String name) {
        click(player, slotNamed(player, name), 0, ContainerInput.PICKUP);
    }

    private static void shiftClickNamed(ServerPlayer player, String name) {
        click(player, slotNamed(player, name), 0, ContainerInput.QUICK_MOVE);
    }

    private static MenuView view(ServerPlayer player) {
        if (player.containerMenu instanceof MenuView view) return view;
        throw failure("expected a menu, found " + player.containerMenu);
    }

    private static String title(ServerPlayer player) {
        return view(player).menu().title().getString();
    }

    private static String title(String key) {
        StringBuilder title = new StringBuilder();
        for (String word : key.split("_")) {
            if (!title.isEmpty()) title.append(' ');
            title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return title.toString();
    }

    private static String name(ServerPlayer player, int slot) {
        Component name = view(player).menu().icon(slot).get(DataComponents.CUSTOM_NAME);
        return name == null ? "" : name.getString();
    }

    private static String lore(ServerPlayer player, int slot) {
        ItemLore lore = view(player).menu().icon(slot).get(DataComponents.LORE);
        return lore == null ? "" : String.join("\n", lore.lines().stream().map(Component::getString).toList());
    }

    private static int slotNamedOrMinus(ServerPlayer player, String name) {
        Menu menu = view(player).menu();
        for (int slot = 0; slot < menu.size(); slot++) {
            if (name(player, slot).equals(name)) return slot;
        }
        return -1;
    }

    private static int slotNamed(ServerPlayer player, String name) {
        int slot = slotNamedOrMinus(player, name);
        if (slot < 0) throw failure("no '" + name + "' in " + title(player));
        return slot;
    }

    private static int compasses(ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (MenuItems.isMenuItem(player.getInventory().getItem(slot))) count++;
        }
        return count;
    }

    private static List<ItemEntity> droppedNear(ServerPlayer player) {
        return player.level().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(16));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw failure(message);
    }

    private static GameTestAssertException failure(String message) {
        return new GameTestAssertException(Component.literal(message), 0);
    }

}
