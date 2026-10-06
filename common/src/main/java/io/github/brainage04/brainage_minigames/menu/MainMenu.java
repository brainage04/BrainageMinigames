package io.github.brainage04.brainage_minigames.menu;

import io.github.brainage04.brainage_minigames.feedback.FeedbackReminders;
import io.github.brainage04.brainage_minigames.game.DuelRequests;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.menu.GameCatalog.Category;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

/** {@code /minigames} and the game menu compass: play, open matches, duels and settings. */
public final class MainMenu {
    public static final String TITLE = "Minigames";
    static final String SETTINGS_TITLE = "Settings";
    static final String INVITES_TITLE = "Duel Invites";

    private MainMenu() {}

    public static void open(ServerPlayer player) {
        Menu menu = new Menu(TITLE, 4);
        menu.set(
                10,
                Icon.of(Items.DIAMOND_SWORD)
                        .name(PlayMenus.BROWSE_TITLE, ChatFormatting.GREEN)
                        .text("Pick a game, layout, kit and map, add bots, then open a match"
                                + " anyone can join.")
                        .blank()
                        .action("Click to browse!"),
                (clicker, click) -> PlayMenus.browse(clicker, Draft.Purpose.OPEN, Category.ALL, 0));
        long running = MatchManager.matches().stream()
                .filter(match -> match.phase() != MatchPhase.ENDED)
                .count();
        menu.set(
                12,
                Icon.of(Items.OAK_SIGN)
                        .name(MatchMenus.LIST_TITLE, ChatFormatting.GREEN)
                        .text("Join a lobby or watch a match in progress.")
                        .blank()
                        .value("Matches", String.valueOf(running), ChatFormatting.AQUA)
                        .blank()
                        .action("Click to view!"),
                (clicker, click) -> MatchMenus.list(clicker, 0));
        menu.set(
                14,
                Icon.of(Items.IRON_SWORD)
                        .name("Duel Builder", ChatFormatting.GREEN)
                        .text("Challenge online players to a private match, with bots in any"
                                + " slots you like.")
                        .blank()
                        .action("Click to build a duel!"),
                (clicker, click) -> openDuelBuilder(clicker));
        menu.set(
                16,
                Icon.of(Items.COMPARATOR)
                        .name(SETTINGS_TITLE, ChatFormatting.GREEN)
                        .text("Feedback reminders and how to send feedback.")
                        .blank()
                        .action("Click to open!"),
                (clicker, click) -> settings(clicker));
        Optional<Match> current = MatchManager.matchOf(player.getUUID());
        List<String> challengers = DuelRequests.challengersOf(player.getUUID());
        if (current.isPresent()) {
            Match match = current.get();
            menu.set(
                    22,
                    Icon.of(Items.BED.pick(DyeColor.RED))
                            .name("Leave Match #" + match.id(), ChatFormatting.RED)
                            .text("Leave " + match.game().displayName() + " and get your inventory"
                                    + " back. Leaving an active game forfeits.")
                            .blank()
                            .action("Click to leave!"),
                    (clicker, click) -> {
                        clicker.closeContainer();
                        LobbyItems.leave(clicker);
                    });
        } else if (!challengers.isEmpty()) {
            menu.set(
                    22,
                    Icon.of(Items.PAPER)
                            .name(INVITES_TITLE, ChatFormatting.GREEN)
                            .count(challengers.size())
                            .text("Players waiting for your answer: " + String.join(", ", challengers) + ".")
                            .blank()
                            .action("Click to answer!"),
                    (clicker, click) -> invites(clicker));
        }
        menu.close();
        menu.open(player);
    }

    /** The duel builder, starting with the game browser; {@code /duel} opens it. */
    public static void openDuelBuilder(ServerPlayer player) {
        PlayMenus.browse(player, Draft.Purpose.DUEL, Category.ALL, 0);
    }

    /** Pending duel challenges to the player: left-click accepts, right-click denies. */
    static void invites(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Menu menu = new Menu(INVITES_TITLE, 4);
        int[] slots = Menu.inner(1, 2);
        List<String> challengers = DuelRequests.challengersOf(player.getUUID());
        for (int index = 0; index < challengers.size() && index < slots.length; index++) {
            ServerPlayer challenger = server.getPlayerList().getPlayerByName(challengers.get(index));
            if (challenger == null) {
                continue;
            }
            menu.set(
                    slots[index],
                    Icon.head(challenger.getGameProfile())
                            .name(challenger.getScoreboardName(), ChatFormatting.GREEN)
                            .text("Challenged you to a duel; the details are in chat.")
                            .blank()
                            .action("Left-click to accept!")
                            .action("Right-click to deny!"),
                    (clicker, click) -> {
                        clicker.closeContainer();
                        if (click.right()) {
                            DuelRequests.deny(clicker, challenger);
                        } else {
                            DuelRequests.accept(clicker, challenger);
                        }
                    });
        }
        menu.back(TITLE, MainMenu::open).close();
        menu.open(player);
    }

    /** Personal settings, laid out as icons with a toggle under each. */
    static void settings(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        boolean reminders = FeedbackReminders.enabled(server, player.getUUID());
        Menu menu = new Menu(SETTINGS_TITLE, 6);
        Icon remindersIcon = Icon.of(Items.CLOCK)
                .name("Feedback Reminders", ChatFormatting.GREEN)
                .text("An hourly chat reminder that /feedback <message> reaches the server's"
                        + " operators.")
                .blank()
                .line(Component.literal("Currently: ").withStyle(ChatFormatting.GRAY)
                        .append(reminders
                                ? Component.literal("ENABLED").withStyle(ChatFormatting.GREEN)
                                : Component.literal("DISABLED").withStyle(ChatFormatting.RED)))
                .blank()
                .action(reminders ? "Click to disable!" : "Click to enable!");
        Menu.Action toggle = (clicker, click) -> {
            FeedbackReminders.setEnabled(server, clicker.getUUID(), !reminders);
            settings(clicker);
        };
        menu.set(Menu.slot(2, 3), remindersIcon, toggle);
        menu.set(
                Menu.slot(3, 3),
                Icon.of(reminders ? Items.DYE.pick(DyeColor.LIME) : Items.DYE.pick(DyeColor.GRAY))
                        .name("Feedback Reminders", ChatFormatting.GREEN)
                        .line(Component.literal("Currently: ").withStyle(ChatFormatting.GRAY)
                                .append(reminders
                                        ? Component.literal("ENABLED").withStyle(ChatFormatting.GREEN)
                                        : Component.literal("DISABLED").withStyle(ChatFormatting.RED)))
                        .blank()
                        .action(reminders ? "Click to disable!" : "Click to enable!"),
                toggle);
        menu.set(
                Menu.slot(2, 5),
                Icon.of(Items.WRITABLE_BOOK)
                        .name("Send Feedback", ChatFormatting.GREEN)
                        .text("Report a bug or suggest an idea with /feedback <message>.")
                        .blank()
                        .action("Click to write it in chat!"),
                (clicker, click) -> {
                    clicker.closeContainer();
                    clicker.sendSystemMessage(Component.literal("[Click to write feedback]")
                            .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                                    .withClickEvent(new ClickEvent.SuggestCommand("/feedback "))));
                });
        menu.back(TITLE, MainMenu::open).close();
        menu.open(player);
    }
}
