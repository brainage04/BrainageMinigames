package io.github.brainage04.brainage_minigames.menu;

import io.github.brainage04.brainage_minigames.api.MatchBots;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.MatchService;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

/** Every match on the server, and per match: join a team, watch, and for its managers start or stop. */
final class MatchMenus {
    static final String LIST_TITLE = "Open Matches";

    private MatchMenus() {}

    static void list(ServerPlayer player, int page) {
        Menu menu = new Menu(LIST_TITLE, 6);
        List<Match> matches = MatchManager.matches().stream()
                .filter(match -> match.phase() != MatchPhase.ENDED)
                .toList();
        if (matches.isEmpty()) {
            menu.set(
                    Menu.slot(2, 4),
                    Icon.of(Items.BARRIER)
                            .name("No Matches", ChatFormatting.RED)
                            .text("Nobody is playing right now. Open one from " + PlayMenus.BROWSE_TITLE + "!")
                            .blank()
                            .action("Click to browse games!"),
                    (clicker, click) -> PlayMenus.browse(clicker, Draft.Purpose.OPEN, GameCatalog.Category.ALL, 0));
        }
        menu.page(
                matches,
                page,
                Menu.inner(1, 4),
                (target, slot, match) -> target.set(
                        slot,
                        matchIcon(match).blank().action("Click to view!"),
                        (clicker, click) -> details(clicker, match.id())),
                MatchMenus::list);
        menu.set(
                menu.bottom() + 5,
                Icon.of(Items.CLOCK).name("Refresh", ChatFormatting.GREEN).action("Click to refresh!"),
                (clicker, click) -> list(clicker, page));
        menu.back(MainMenu.TITLE, MainMenu::open).close();
        menu.open(player);
    }

    /** A match's icon: its game, number, status, players and owner. */
    static Icon matchIcon(Match match) {
        Icon icon = Icon.of(GameCatalog.entry(match.game()).icon())
                .name("#%d %s %s".formatted(match.id(), match.game().displayName(), match.layout().displayName()),
                        ChatFormatting.GREEN)
                .value("Status", status(match), statusColor(match));
        match.mapName().ifPresent(map -> icon.value("Map", PlayMenus.title(map), ChatFormatting.AQUA));
        match.ownerName().ifPresent(owner -> icon.value("Owner", owner, ChatFormatting.AQUA));
        if (match.isPrivate()) {
            icon.line(Component.literal("Private duel").withStyle(ChatFormatting.DARK_GRAY));
        }
        List<String> players = players(match);
        if (!players.isEmpty()) {
            icon.blank().text(String.join(", ", players.subList(0, Math.min(12, players.size())))
                    + (players.size() > 12 ? " and %d more".formatted(players.size() - 12) : ""));
        }
        return icon.count(Math.max(1, players.size()));
    }

    private static String status(Match match) {
        TeamLayout layout = match.layout();
        return switch (match.phase()) {
            case LOBBY -> {
                int taken = match.lobbySize() + match.reservedBots();
                String count = layout.isFreeForAll()
                        ? taken + " joined"
                        : taken + "/" + layout.capacity();
                yield "Waiting (" + count + ")";
            }
            case COUNTDOWN -> "Starting";
            case ACTIVE -> "In progress (" + match.aliveCount() + " alive)";
            case ENDED -> "Ended";
        };
    }

    private static ChatFormatting statusColor(Match match) {
        return switch (match.phase()) {
            case LOBBY -> ChatFormatting.GREEN;
            case COUNTDOWN -> ChatFormatting.YELLOW;
            case ACTIVE -> ChatFormatting.GOLD;
            case ENDED -> ChatFormatting.RED;
        };
    }

    /** Players taking part: those waiting in the lobby, then those still alive; not watchers. */
    private static List<String> players(Match match) {
        return match.onlineMembers().stream()
                .filter(member -> match.phase() == MatchPhase.LOBBY
                        ? match.isWaiting(member.getUUID())
                        : match.isAlive(member.getUUID()))
                .map(ServerPlayer::getScoreboardName)
                .toList();
    }

    static void details(ServerPlayer player, int matchId) {
        Optional<Match> found = MatchManager.get(matchId);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal("Match #" + matchId + " has ended.")
                    .withStyle(ChatFormatting.RED));
            list(player, 0);
            return;
        }
        Match match = found.get();
        Menu menu = new Menu("Match #" + matchId, 5);
        menu.set(4, matchIcon(match));
        boolean member = match.involves(player.getUUID());
        if (match.isOpenLobby() && !member) {
            joinButtons(menu, match);
        }
        if (match.phase() != MatchPhase.ENDED && !member) {
            menu.set(
                    Menu.slot(3, 1),
                    Icon.of(Items.ENDER_EYE)
                            .name("Watch", ChatFormatting.GREEN)
                            .text("Spectate the match; your inventory is restored when you leave.")
                            .blank()
                            .action("Click to watch!"),
                    (clicker, click) -> {
                        clicker.closeContainer();
                        MatchManager.watch(clicker, match);
                    });
        }
        if (member) {
            menu.set(
                    Menu.slot(3, 1),
                    Icon.of(Items.BED.pick(DyeColor.RED))
                            .name("Leave", ChatFormatting.RED)
                            .text("Leave and get your inventory back. Leaving an active game forfeits.")
                            .blank()
                            .action("Click to leave!"),
                    (clicker, click) -> {
                        clicker.closeContainer();
                        LobbyItems.leave(clicker);
                    });
        }
        if (MatchService.canManage(player, match)) {
            manageButtons(menu, match);
        }
        menu.back(LIST_TITLE, clicker -> list(clicker, 0)).close();
        menu.open(player);
    }

    /** One button per team with room, or a single one for free-for-all. */
    private static void joinButtons(Menu menu, Match match) {
        TeamLayout layout = match.layout();
        int[] slots = Menu.inner(1, 2);
        if (layout.isFreeForAll()) {
            menu.set(
                    Menu.slot(1, 4),
                    Icon.of(Items.DYE.pick(DyeColor.LIME))
                            .name("Join", ChatFormatting.GREEN)
                            .value("Free slots", String.valueOf(match.freeSlots()), ChatFormatting.AQUA)
                            .blank()
                            .action("Click to join!"),
                    (clicker, click) -> join(clicker, match, 0));
            return;
        }
        for (int team = 1; team <= layout.teamSizes().size() && team <= slots.length; team++) {
            int number = team;
            int free = match.freeSlots(number);
            Icon icon = Icon.of(TeamIcons.wool(number))
                    .name("Join " + TeamIcons.name(number), TeamIcons.color(number))
                    .count(layout.teamSizes().get(number - 1))
                    .value("Free slots", free + "/" + layout.teamSizes().get(number - 1), ChatFormatting.AQUA)
                    .value("Bots", String.valueOf(match.reservedBots(number)), ChatFormatting.AQUA)
                    .blank();
            if (free > 0) {
                icon.action("Click to join!");
            } else {
                icon.refusal("This team is full.");
            }
            menu.set(slots[team - 1], icon, (clicker, click) -> join(clicker, match, number));
        }
        if (layout.teamSizes().size() > slots.length) {
            menu.set(
                    Menu.slot(3, 4),
                    Icon.of(Items.NETHER_STAR)
                            .name("Join Any Team", ChatFormatting.GREEN)
                            .value("Free slots", String.valueOf(match.freeSlots()), ChatFormatting.AQUA)
                            .blank()
                            .action("Click to join!"),
                    (clicker, click) -> join(clicker, match, 0));
        }
    }

    private static void join(ServerPlayer player, Match match, int team) throws MatchException {
        player.closeContainer();
        MatchManager.join(player, match, team);
    }

    /** Start and stop for the owner and operators, and bot slots while the lobby waits. */
    private static void manageButtons(Menu menu, Match match) {
        int id = match.id();
        if (match.phase() == MatchPhase.LOBBY) {
            menu.set(
                    Menu.slot(3, 5),
                    Icon.of(Items.EMERALD_BLOCK)
                            .name("Start Now", ChatFormatting.GREEN)
                            .text("Start with the players waiting; reserved bot slots are filled.")
                            .blank()
                            .action("Click to start!"),
                    (clicker, click) -> {
                        MatchService.start(clicker, match);
                        details(clicker, id);
                    });
            if (MatchBots.available() && match.game().supportsBots()) {
                menu.set(
                        Menu.slot(3, 6),
                        Icon.of(Items.SKELETON_SKULL)
                                .name("Bots", ChatFormatting.GREEN)
                                .value("Reserved", String.valueOf(match.reservedBots()), ChatFormatting.AQUA)
                                .blank()
                                .action("Left-click to add a bot!")
                                .action("Right-click to clear the bots!"),
                        (clicker, click) -> {
                            if (click.right()) {
                                match.clearBots();
                            } else {
                                match.addBots(0, 1);
                            }
                            details(clicker, id);
                        });
            }
        }
        menu.set(
                Menu.slot(3, 7),
                Icon.of(Items.TNT)
                        .name("Stop Match", ChatFormatting.RED)
                        .text("Ends the match for everyone and restores their inventories.")
                        .blank()
                        .action("Shift-click to stop!"),
                (clicker, click) -> {
                    if (!click.shift()) {
                        throw new MatchException("Shift-click to stop the match.");
                    }
                    MatchService.stop(clicker, match);
                    list(clicker, 0);
                });
    }
}
