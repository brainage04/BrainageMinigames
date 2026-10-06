package io.github.brainage04.brainage_minigames.menu;

import io.github.brainage04.brainage_minigames.game.DuelRequests;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.menu.GameCatalog.Category;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

/**
 * The duel builder: after the game, layout, kit and map, fill every slot of the lineup with an
 * online player or a bot and send the challenge. A lineup of bots only starts straight away.
 */
final class DuelMenus {
    static final String LINEUP_TITLE = "Duel Lineup";
    static final String PLAYERS_TITLE = "Choose a Player";

    private DuelMenus() {}

    /** The duel builder for a challenge against {@code opponent}, from right-clicking them. */
    static void challenge(ServerPlayer player, ServerPlayer opponent) {
        PlayMenus.browse(
                player,
                Draft.Purpose.DUEL,
                Category.ALL,
                0,
                Draft.Slot.player(opponent.getUUID(), opponent.getScoreboardName()));
    }

    static void lineup(ServerPlayer player, Draft draft) {
        TeamLayout layout = draft.requireLayout();
        Menu menu = new Menu(LINEUP_TITLE, 6);
        PlayMenus.summary(menu, player, draft);
        boolean humans = draft.slots().stream().anyMatch(slot -> slot.player() != null);
        menu.set(
                8,
                Icon.of(Items.EMERALD_BLOCK)
                        .name(humans ? "Send Challenge" : "Start Match", ChatFormatting.GREEN)
                        .text(humans
                                ? "Invites the players; the duel starts once all of them accept."
                                : "Starts the duel against the bots now.")
                        .blank()
                        .action(humans ? "Click to send!" : "Click to start!"),
                (clicker, click) -> send(clicker, draft));
        menu.separators(1, -1, "Duel", "Players and bots");
        int[] slots = Menu.inner(2, 4);
        menu.set(slots[0], Icon.head(player.getGameProfile())
                .name("You", TeamIcons.color(1))
                .line(Component.literal(teamLabel(layout, 0)).withStyle(TeamIcons.color(1))));
        for (int index = 0; index < draft.slots().size() && index + 1 < slots.length; index++) {
            menu.set(slots[index + 1], slotIcon(draft, index), slotAction(draft, index));
        }
        int next = draft.slots().size() + 1;
        if (layout.isFreeForAll() && next < slots.length && draft.slots().size() < DuelRequests.MAX_INVITEES) {
            Icon add = Icon.of(Items.STAINED_GLASS_PANE.pick(DyeColor.LIME))
                    .name("Add a Participant", ChatFormatting.GREEN)
                    .blank()
                    .action("Left-click to add a player!");
            if (draft.botsAvailable()) {
                add.action("Right-click to add a bot!");
            }
            menu.set(
                    slots[next],
                    add,
                    (clicker, click) -> {
                        if (click.right()) {
                            requireBots(draft);
                            lineup(clicker, draft.withSlot(draft.slots().size(), Draft.Slot.BOT));
                        } else {
                            players(clicker, draft, draft.slots().size(), 0);
                        }
                    });
        }
        menu.back(PlayMenus.previousTitle(player, draft), clicker -> PlayMenus.beforeReview(clicker, draft))
                .close();
        menu.open(player);
    }

    /** "Red Team" for the participant at {@code index} (the challenger is 0), or "Free for all". */
    private static String teamLabel(TeamLayout layout, int index) {
        int team = team(layout, index);
        return team == 0 ? "Free for all" : TeamIcons.name(team);
    }

    /** The team of the participant at {@code index}, counting from 1; 0 in free-for-all. */
    private static int team(TeamLayout layout, int index) {
        if (layout.isFreeForAll()) {
            return 0;
        }
        int end = 0;
        for (int team = 0; team < layout.teamSizes().size(); team++) {
            end += layout.teamSizes().get(team);
            if (index < end) {
                return team + 1;
            }
        }
        return 0;
    }

    private static Icon slotIcon(Draft draft, int index) {
        TeamLayout layout = draft.requireLayout();
        int team = Math.max(1, team(layout, index + 1));
        ChatFormatting color = layout.isFreeForAll() ? ChatFormatting.GRAY : TeamIcons.color(team);
        Component label = Component.literal(teamLabel(layout, index + 1)).withStyle(color);
        Draft.Slot slot = draft.slots().get(index);
        if (slot.bot()) {
            return Icon.of(Items.SKELETON_SKULL)
                    .name("Bot", color)
                    .line(label.copy())
                    .blank()
                    .action(layout.isFreeForAll() ? "Click to remove!" : "Click to empty this slot!");
        }
        if (slot.player() != null) {
            return Icon.of(Items.PLAYER_HEAD)
                    .name(slot.name(), color)
                    .line(label.copy())
                    .blank()
                    .action(layout.isFreeForAll() ? "Click to remove!" : "Click to empty this slot!");
        }
        Icon icon = Icon.of(layout.isFreeForAll() ? Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY) : TeamIcons.glass(team))
                .name("Empty Slot", color)
                .line(label.copy())
                .blank()
                .action("Left-click to choose a player!");
        if (draft.botsAvailable()) {
            icon.action("Right-click to add a bot!");
        }
        return icon;
    }

    private static Menu.Action slotAction(Draft draft, int index) {
        Draft.Slot slot = draft.slots().get(index);
        boolean freeForAll = draft.requireLayout().isFreeForAll();
        return (clicker, click) -> {
            if (!slot.empty()) {
                lineup(clicker, freeForAll ? draft.withoutSlot(index) : draft.withSlot(index, Draft.Slot.EMPTY));
            } else if (click.right()) {
                requireBots(draft);
                lineup(clicker, draft.withSlot(index, Draft.Slot.BOT));
            } else {
                players(clicker, draft, index, 0);
            }
        };
    }

    private static void requireBots(Draft draft) throws MatchException {
        if (!draft.botsAvailable()) {
            throw new MatchException("Bots are not available for " + draft.game().displayName() + ".");
        }
    }

    /** Online players to put in lineup slot {@code index}; busy players are shown but refused. */
    static void players(ServerPlayer player, Draft draft, int index, int page) {
        Menu menu = new Menu(PLAYERS_TITLE, 6);
        List<UUID> chosen = draft.slots().stream().map(Draft.Slot::player).filter(Objects::nonNull).toList();
        List<ServerPlayer> candidates = player.level().getServer().getPlayerList().getPlayers().stream()
                .filter(other -> other != player && !chosen.contains(other.getUUID()))
                .sorted(Comparator.comparing(ServerPlayer::getScoreboardName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (candidates.isEmpty()) {
            Icon nobody = Icon.of(Items.BARRIER).name("Nobody Else Is Online", ChatFormatting.RED);
            if (draft.botsAvailable()) {
                nobody.text("Right-click a slot to add a bot instead.");
            }
            menu.set(Menu.slot(2, 4), nobody);
        }
        menu.page(
                candidates,
                page,
                Menu.inner(1, 4),
                (target, slot, candidate) -> {
                    Optional<String> busy = MatchManager.unavailableReason(candidate);
                    Icon icon = Icon.head(candidate.getGameProfile())
                            .name(candidate.getScoreboardName(), busy.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                            .blank();
                    if (busy.isPresent()) {
                        icon.refusal(candidate.getScoreboardName() + " " + busy.get() + ".");
                    } else {
                        icon.action("Click to choose!");
                    }
                    target.set(slot, icon, (clicker, click) -> {
                        if (busy.isPresent()) {
                            throw new MatchException(candidate.getScoreboardName() + " " + busy.get() + ".");
                        }
                        lineup(clicker, draft.withSlot(
                                index, Draft.Slot.player(candidate.getUUID(), candidate.getScoreboardName())));
                    });
                },
                (clicker, shown) -> players(clicker, draft, index, shown));
        menu.back(LINEUP_TITLE, clicker -> lineup(clicker, draft)).close();
        menu.open(player);
    }

    /** Sends the challenge, or starts the duel when every other slot is a bot. */
    static void send(ServerPlayer player, Draft draft) throws MatchException {
        TeamLayout layout = draft.requireLayout();
        if (draft.slots().isEmpty()) {
            throw new MatchException("Add at least one player or bot to duel.");
        }
        List<Optional<ServerPlayer>> participants = new ArrayList<>(draft.slots().size());
        for (Draft.Slot slot : draft.slots()) {
            if (slot.empty()) {
                throw new MatchException("Fill every slot with a player or a bot first.");
            }
            if (slot.bot()) {
                participants.add(Optional.empty());
                continue;
            }
            ServerPlayer invitee = player.level().getServer().getPlayerList().getPlayer(slot.player());
            if (invitee == null) {
                throw new MatchException(slot.name() + " is no longer online.");
            }
            participants.add(Optional.of(invitee));
        }
        Minigame game = draft.game();
        Identifier map = draft.map();
        DuelRequests.challenge(
                player,
                game,
                layout,
                participants,
                draft.kit(),
                (server, settings) -> MapArena.withChosenMap(map, () -> game.openArena(server, settings, layout)));
        player.closeContainer();
    }
}
