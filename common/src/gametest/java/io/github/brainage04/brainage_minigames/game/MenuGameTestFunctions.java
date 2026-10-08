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
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsGame;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsPerk;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsProgression;
import io.github.brainage04.brainage_minigames.menu.SkyWarsMenus;
import net.minecraft.nbt.CompoundTag;

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

    /**
     * UHC-style games offer solo and teams of two, three and four sized to fill their lobby, in the
     * layout menu and in {@code /minigames open} suggestions; other games keep the duel layouts.
     */
    public static void uhcLayoutsOfferSoloAndTeams(GameTestHelper context) throws Exception {
        ChatPlayer player = player(context, "Teams");
        MinecraftServer server = context.getLevel().getServer();
        try {
            for (Minigame game : List.of(Minigames.UHC, Minigames.MEETUP, Minigames.FINAL_UHC)) {
                List<String> presets = game.layoutPresets(SettingsStorage.resolve(server, game)).stream()
                        .map(TeamLayout::toString).toList();
                check(presets.equals(List.of("ffa", "2v2v2v2", "3v3v3", "4v4")),
                        game.id() + " offers " + presets + " for an 8-player lobby");
            }
            MainMenu.open(player);
            clickNamed(player, "Play a Game");
            // The category row also has a "UHC" button; the game is the last icon of that name.
            int uhc = -1;
            for (int slot = 0; slot < view(player).menu().size(); slot++) {
                if (name(player, slot).equals("UHC")) uhc = slot;
            }
            check(uhc >= 9, "no UHC game below the category row");
            click(player, uhc, 0, ContainerInput.PICKUP);
            check(title(player).equals("Choose a Layout"), "UHC opened " + title(player));
            check(lore(player, slotNamed(player, "Solo")).contains("Everyone for themselves"),
                    "Solo does not explain itself: " + lore(player, slotNamed(player, "Solo")));
            for (String layout : List.of("2v2v2v2", "3v3v3", "4v4")) {
                String name = "Teams of " + layout.charAt(0);
                check(lore(player, slotNamed(player, name)).contains("Layout: " + layout),
                        name + " is not " + layout + ": " + lore(player, slotNamed(player, name)));
            }
            check(slotNamedOrMinus(player, "1v1") < 0, "UHC still offers duel layouts");
            clickNamed(player, "Teams of 3");
            check(title(player).equals("Choose a Kit"), "Teams of 3 opened " + title(player));
            player.closeContainer();

            MainMenu.open(player);
            clickNamed(player, "Play a Game");
            clickNamed(player, "Classic");
            check(slotNamedOrMinus(player, "2v2") >= 0 && slotNamedOrMinus(player, "Free for All") >= 0,
                    "Classic lost its duel layouts");
            player.closeContainer();

            var dispatcher = server.getCommands().getDispatcher();
            for (var entry : java.util.Map.of("minigames open uhc ", List.of("ffa", "2v2v2v2", "3v3v3", "4v4"),
                    "minigames open classic ", Minigame.COMMON_LAYOUTS).entrySet()) {
                var suggestions = dispatcher.getCompletionSuggestions(
                        dispatcher.parse(entry.getKey(), player.createCommandSourceStack())).get();
                List<String> offered = suggestions.getList().stream().map(suggestion -> suggestion.getText()).toList();
                check(offered.containsAll(entry.getValue()) && offered.size() == entry.getValue().size(),
                        "/" + entry.getKey() + "suggests " + offered);
            }
        } finally {
            player.closeContainer();
            TestPlayers.disconnect(player);
        }
        context.succeed();
    }

    /** UHC settings link to the scenario gamerules, which only operators toggle there. */
    public static void uhcScenariosToggleFromSettings(GameTestHelper context) {
        ChatPlayer player = player(context, "Scenarios");
        MinecraftServer server = context.getLevel().getServer();
        var rule = io.github.brainage04.brainage_minigames.game.uhc.UhcResourceScenarios.CUT_CLEAN;
        boolean before = server.getGameRules().get(rule);
        try {
            server.getGameRules().set(rule, false, server);
            MainMenu.open(player);
            clickNamed(player, "Play a Game");
            int uhc = -1;
            for (int slot = 0; slot < view(player).menu().size(); slot++) {
                if (name(player, slot).equals("UHC")) uhc = slot;
            }
            click(player, uhc, 0, ContainerInput.PICKUP);
            clickNamed(player, "Solo");
            clickNamed(player, "UHC Starter");
            check(title(player).equals("Match Setup"), "the UHC kit opened " + title(player));
            clickNamed(player, "Game Settings");
            check(title(player).equals("UHC Settings"), "Game Settings opened " + title(player));
            clickNamed(player, "UHC Scenarios");
            check(title(player).equals("UHC Scenarios"), "UHC Scenarios opened " + title(player));
            for (String name : List.of("Cutclean", "Timber", "Vein Miner", "Hastey Boys", "Blood Diamonds", "Diamondless", "Goldless")) {
                check(lore(player, slotNamed(player, name)).contains("Currently: "), name + " shows no value");
            }
            clickNamed(player, "Cutclean");
            check(!server.getGameRules().get(rule), "a non-operator toggled CutClean");
            TestPlayers.setOperator(player, true);
            // Menus are built for their viewer: reopen the page as an operator.
            clickNamed(player, "Go Back");
            clickNamed(player, "UHC Scenarios");
            clickNamed(player, "Cutclean");
            check(server.getGameRules().get(rule), "an operator's click did not enable CutClean");
            check(lore(player, slotNamed(player, "Cutclean")).contains("Currently: ENABLED"), "the menu shows " + lore(player, slotNamed(player, "Cutclean")));
            clickNamed(player, "Go Back");
            check(title(player).equals("UHC Settings") && lore(player, slotNamed(player, "UHC Scenarios")).contains("Cutclean: ENABLED"),
                    "the settings do not list the enabled scenario: " + lore(player, slotNamed(player, "UHC Scenarios")));
            player.closeContainer();
            MainMenu.open(player);
            clickNamed(player, "Play a Game");
            clickNamed(player, "Classic");
            clickNamed(player, "1v1");
            clickNamed(player, "Classic");
            clickNamed(player, "Game Settings");
            check(slotNamedOrMinus(player, "UHC Scenarios") < 0, "Classic settings link to the UHC scenarios");
        } finally {
            server.getGameRules().set(rule, before, server);
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

    /**
     * A SkyWars lobby's kits item opens Kits &amp; Perks; kit and perk clicks select and toggle, and
     * no container input moves an item out of those menus or into the inventory.
     */
    public static void skyWarsKitsAndPerksMenus(GameTestHelper context) throws MatchException {
        ChatPlayer alice = player(context, "Kits");
        MinecraftServer server = context.getLevel().getServer();
        boolean maxKits = server.getGameRules().get(SkyWarsProgression.MAX_ALL_KITS);
        boolean maxPerks = server.getGameRules().get(SkyWarsProgression.MAX_ALL_PERKS);
        server.getGameRules().set(SkyWarsProgression.MAX_ALL_KITS, true, server);
        server.getGameRules().set(SkyWarsProgression.MAX_ALL_PERKS, true, server);
        Match match = null;
        try {
            Identifier map = MapArena.maps(server, SkyWarsGame.ID).getFirst();
            match = MatchManager.open(server, Minigames.SKYWARS, TeamLayout.FREE_FOR_ALL, null,
                    (unused, settings) -> Minigames.SKYWARS.prepare(MapArena.open(context.getLevel(), map)));
            MatchManager.join(alice, match, 0);
            ItemStack kits = alice.getInventory().getItem(1);
            check(MenuItems.kind(kits).orElse(null) == MenuItems.Kind.SKYWARS_KITS, "slot 1 holds " + kits);
            alice.gameMode.useItem(alice, alice.level(), kits, InteractionHand.MAIN_HAND);
            check(title(alice).equals(SkyWarsMenus.TITLE), "the kits item opened " + title(alice));
            clickNamed(alice, "Insane Kits");
            check(title(alice).equals("Insane Kits"), "opened " + title(alice));
            check(name(alice, 53).equals("Left-click for next page!"), "the kits have no second page");
            int farmer = slotNamed(alice, "Farmer");
            check(lore(alice, farmer).contains("Projectile Protection IV") && lore(alice, farmer).contains("Egg x16"),
                    "Farmer shows " + lore(alice, farmer));
            List<ItemStack> before = new java.util.ArrayList<>();
            for (int slot = 0; slot < alice.getInventory().getContainerSize(); slot++) {
                before.add(alice.getInventory().getItem(slot).copy());
            }
            int hotbar = 54 + 27;
            for (int key = 0; key < 9; key++) click(alice, farmer, key, ContainerInput.SWAP);
            click(alice, farmer, 40, ContainerInput.SWAP);
            click(alice, farmer, 2, ContainerInput.CLONE);
            click(alice, farmer, 0, ContainerInput.THROW);
            click(alice, farmer, 1, ContainerInput.THROW);
            click(alice, farmer, 0, ContainerInput.PICKUP_ALL);
            click(alice, -999, 0, ContainerInput.QUICK_CRAFT);
            click(alice, farmer, 1, ContainerInput.QUICK_CRAFT);
            click(alice, -999, 2, ContainerInput.QUICK_CRAFT);
            click(alice, hotbar, 0, ContainerInput.PICKUP);
            click(alice, farmer, 0, ContainerInput.PICKUP);
            click(alice, -999, 0, ContainerInput.PICKUP);
            click(alice, hotbar, 0, ContainerInput.QUICK_MOVE);
            click(alice, farmer, 0, ContainerInput.QUICK_MOVE);
            check(alice.containerMenu.getCarried().isEmpty(), "cursor holds " + alice.containerMenu.getCarried());
            check(name(alice, farmer).equals("Farmer"), "the Farmer icon left the menu");
            for (int slot = 0; slot < alice.getInventory().getContainerSize(); slot++) {
                check(ItemStack.matches(before.get(slot), alice.getInventory().getItem(slot)),
                        "inventory slot " + slot + " became " + alice.getInventory().getItem(slot));
            }
            check(droppedNear(alice).isEmpty(), "items were dropped: " + droppedNear(alice));
            check(SkyWarsProgression.selectedKit(server, alice.getUUID(), SkyWarsGame.MODE).id().equals("farmer"),
                    "clicking Farmer did not select it");
            check(lore(alice, farmer).contains("SELECTED"), "Farmer does not show SELECTED");

            clickNamed(alice, "Go Back");
            clickNamed(alice, "Toggle Insane Perks");
            check(title(alice).equals("Toggle Insane Perks"), "opened " + title(alice));
            SkyWarsPerk bridger = SkyWarsPerk.find(SkyWarsGame.MODE, SkyWarsPerk.BRIDGER);
            check(lore(alice, slotNamed(alice, "Bridger")).contains("ENABLED"), "Bridger does not start enabled");
            clickNamed(alice, "Bridger");
            check(!SkyWarsProgression.enabled(server, alice.getUUID(), bridger), "clicking Bridger did not disable it");
            check(lore(alice, slotNamed(alice, "Bridger")).contains("DISABLED"), "Bridger does not show DISABLED");
            clickNamed(alice, "Bridger");
            check(SkyWarsProgression.enabled(server, alice.getUUID(), bridger), "clicking Bridger again did not enable it");
            clickNamed(alice, "Left-click for next page!");
            check(slotNamedOrMinus(alice, "Tenacity") >= 0, "the second perk page lacks Tenacity");
            check(lore(alice, slotNamed(alice, "Dragon's Pledge")).contains("DISABLED"), "Dragon's Pledge starts enabled");
        } finally {
            if (match != null) MatchManager.stop(match);
            alice.closeContainer();
            CompoundTag root = server.getCommandStorage().get(SkyWarsProgression.STORAGE);
            root.remove(alice.getUUID().toString());
            server.getCommandStorage().set(SkyWarsProgression.STORAGE, root);
            server.getGameRules().set(SkyWarsProgression.MAX_ALL_KITS, maxKits, server);
            server.getGameRules().set(SkyWarsProgression.MAX_ALL_PERKS, maxPerks, server);
            TestPlayers.disconnect(alice);
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
