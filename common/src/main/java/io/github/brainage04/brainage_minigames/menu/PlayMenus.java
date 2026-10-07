package io.github.brainage04.brainage_minigames.menu;

import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.MatchService;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.menu.GameCatalog.Category;
import io.github.brainage04.brainage_minigames.storage.KitStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Setting up a match step by step: browse games, pick a layout (a preset or custom team sizes),
 * a kit, a map, then review the players, bots and settings and open it. Duels share every step
 * up to the review, which {@link DuelMenus#lineup} replaces.
 */
final class PlayMenus {
    static final String BROWSE_TITLE = "Play a Game";
    static final String DUEL_BROWSE_TITLE = "Duel: Choose a Game";
    static final String LAYOUTS_TITLE = "Choose a Layout";
    static final String CUSTOM_TITLE = "Custom Layout";
    static final String KITS_TITLE = "Choose a Kit";
    static final String MAPS_TITLE = "Choose a Map";
    static final String SETUP_TITLE = "Match Setup";

    /** Most bots a free-for-all match can be opened with from the menu. */
    static final int MAX_FFA_BOTS = 15;

    /** Most teams the custom layout editor shows. */
    private static final int MAX_CUSTOM_TEAMS = 21;

    /** Opens the next screen for a draft; the steps link to each other with these. */
    @FunctionalInterface
    interface Step {
        void open(ServerPlayer player, Draft draft);
    }

    private PlayMenus() {}

    // Browse games

    static void browse(ServerPlayer player, Draft.Purpose purpose, Category category, int page) {
        browse(player, purpose, category, page, null);
    }

    /**
     * The game browser; for a duel against a chosen {@code opponent} (a player right-clicked with
     * the game menu) the opponent fills the first slot.
     */
    static void browse(
            ServerPlayer player,
            Draft.Purpose purpose,
            Category category,
            int page,
            Draft.@Nullable Slot opponent) {
        boolean duel = purpose == Draft.Purpose.DUEL;
        Menu menu = new Menu(
                opponent != null ? "Duel " + opponent.name() : duel ? DUEL_BROWSE_TITLE : BROWSE_TITLE, 6);
        Category[] categories = Category.values();
        for (int index = 0; index < categories.length; index++) {
            Category shown = categories[index];
            menu.set(
                    index,
                    Icon.of(shown.icon)
                            .name(shown.title, ChatFormatting.GREEN)
                            .text(shown.summary)
                            .blank()
                            .action(shown == category ? "Selected" : "Click to view!")
                            .glint(shown == category),
                    (clicker, click) -> browse(clicker, purpose, shown, 0, opponent));
        }
        menu.separators(1, category.ordinal(), "Categories", "Games");
        menu.page(
                GameCatalog.games(category),
                page,
                Menu.inner(2, 4),
                (target, slot, game) ->
                        target.set(
                                slot,
                                gameIcon(game)
                                        .blank()
                                        .action(opponent != null
                                                ? "Click to challenge " + opponent.name() + "!"
                                                : duel
                                                        ? "Click to set up a duel!"
                                                        : "Click to choose a layout!"),
                                (clicker, click) ->
                                        layouts(clicker, duel ? Draft.duel(game, opponent) : Draft.open(game))),
                (clicker, shownPage) -> browse(clicker, purpose, category, shownPage, opponent));
        menu.back(MainMenu.TITLE, MainMenu::open).close();
        menu.open(player);
    }

    /** A game's icon with its name, category, summary and how many of its matches are open. */
    static Icon gameIcon(Minigame game) {
        GameCatalog.Entry entry = GameCatalog.entry(game);
        long open = MatchManager.matches().stream()
                .filter(match -> match.game() == game && match.phase() != MatchPhase.ENDED)
                .count();
        return Icon.of(entry.icon())
                .name(game.displayName(), ChatFormatting.GREEN)
                .line(Component.literal(entry.category().title).withStyle(ChatFormatting.DARK_GRAY))
                .blank()
                .text(entry.summary())
                .blank()
                .value("Matches running", String.valueOf(open), ChatFormatting.AQUA);
    }

    // Layouts

    static void layouts(ServerPlayer player, Draft draft) {
        Menu menu = new Menu(LAYOUTS_TITLE, 6);
        menu.set(4, gameIcon(draft.game()));
        int[] slots = Menu.inner(1, 2);
        int index = 0;
        for (TeamLayout layout :
                draft.game().layoutPresets(SettingsStorage.resolve(player.level().getServer(), draft.game()))) {
            menu.set(
                    slots[index++],
                    layoutIcon(draft.game(), layout)
                            .blank()
                            .action("Click to pick!")
                            .glint(layout.equals(draft.layout())),
                    (clicker, click) -> kits(clicker, draft.withLayout(layout), 0, PlayMenus::afterKit));
        }
        menu.set(
                Menu.slot(3, 4),
                Icon.of(Items.ANVIL)
                        .name("Custom Layout", ChatFormatting.GREEN)
                        .text("Any number of teams of any size, such as 1v2 or 2v3v4.")
                        .blank()
                        .action("Click to build one!"),
                (clicker, click) -> custom(clicker, draft, startingSizes(draft.layout())));
        menu.back(draft.duel() ? DUEL_BROWSE_TITLE : BROWSE_TITLE,
                        clicker -> browse(clicker, draft.purpose(), Category.ALL, 0))
                .close();
        menu.open(player);
    }

    /**
     * A layout's icon. Games that fill a lobby call a free-for-all "Solo" and equal teams "Teams of
     * N", as UHC queues do; other games use the layout itself, such as "2v2".
     */
    static Icon layoutIcon(Minigame game, TeamLayout layout) {
        boolean lobby = game.setting(GameSetting.LOBBY_SIZE).isPresent();
        int size = layout.commonTeamSize();
        String name = layout.isFreeForAll()
                ? lobby ? "Solo" : "Free for All"
                : lobby && size > 1 ? "Teams of " + size : layout.displayName();
        Icon icon = Icon.of(GameCatalog.entry(game).icon()).name(name, ChatFormatting.GREEN);
        if (layout.isFreeForAll()) {
            return icon.text("Everyone for themselves; starts with at least two players.");
        }
        List<Integer> sizes = layout.teamSizes();
        icon.count(sizes.getFirst());
        if (!name.equals(layout.displayName())) icon.value("Layout", layout.displayName(), ChatFormatting.AQUA);
        return icon.value("Teams", String.valueOf(sizes.size()), ChatFormatting.AQUA)
                .value("Players", String.valueOf(layout.capacity()), ChatFormatting.AQUA);
    }

    private static List<Integer> startingSizes(@Nullable TeamLayout layout) {
        return layout == null || layout.isFreeForAll() ? List.of(1, 1) : layout.teamSizes();
    }

    /** The custom layout editor: one wool block per team, its count the team's size. */
    static void custom(ServerPlayer player, Draft draft, List<Integer> sizes) {
        Menu menu = new Menu(CUSTOM_TITLE, 6);
        int[] slots = Menu.inner(1, 3);
        for (int index = 0; index < sizes.size(); index++) {
            int team = index;
            int size = sizes.get(index);
            menu.set(
                    slots[index],
                    Icon.of(TeamIcons.wool(team + 1))
                            .name(TeamIcons.name(team + 1), TeamIcons.color(team + 1))
                            .count(size)
                            .value("Players", String.valueOf(size), ChatFormatting.AQUA)
                            .blank()
                            .action("Left-click to add a player!")
                            .action("Right-click to remove a player!")
                            .action("Shift-click to remove the team!"),
                    (clicker, click) -> {
                        List<Integer> next = new ArrayList<>(sizes);
                        if (click.shift()) {
                            if (next.size() <= 2) {
                                throw new MatchException("A layout needs at least two teams.");
                            }
                            next.remove(team);
                        } else if (click.right()) {
                            next.set(team, Math.max(1, size - 1));
                        } else {
                            next.set(team, Math.min(TeamLayout.MAX_TEAM_SIZE, size + 1));
                        }
                        custom(clicker, draft, next);
                    });
        }
        TeamLayout layout = new TeamLayout(sizes);
        menu.set(
                Menu.slot(4, 2),
                Icon.of(Items.DYE.pick(DyeColor.LIME))
                        .name("Add a Team", ChatFormatting.GREEN)
                        .text("Adds a team of one player.")
                        .blank()
                        .action(sizes.size() < MAX_CUSTOM_TEAMS ? "Click to add!" : "No room for more teams."),
                (clicker, click) -> {
                    if (sizes.size() >= MAX_CUSTOM_TEAMS) {
                        throw new MatchException(
                                "The menu builds up to %d teams; use /minigames open for more."
                                        .formatted(MAX_CUSTOM_TEAMS));
                    }
                    List<Integer> next = new ArrayList<>(sizes);
                    next.add(1);
                    custom(clicker, draft, next);
                });
        menu.set(
                Menu.slot(4, 4),
                Icon.of(Items.PAPER)
                        .name("Layout: " + layout.displayName(), ChatFormatting.GREEN)
                        .value("Teams", String.valueOf(sizes.size()), ChatFormatting.AQUA)
                        .value("Players", String.valueOf(layout.capacity()), ChatFormatting.AQUA));
        menu.set(
                Menu.slot(4, 6),
                Icon.of(Items.EMERALD)
                        .name("Use This Layout", ChatFormatting.GREEN)
                        .text("Continue with " + layout.displayName() + ".")
                        .blank()
                        .action("Click to continue!"),
                (clicker, click) -> kits(clicker, draft.withLayout(layout), 0, PlayMenus::afterKit));
        menu.back(LAYOUTS_TITLE, clicker -> layouts(clicker, draft)).close();
        menu.open(player);
    }

    // Kits and maps

    /** Kits to choose from: the game's own first, then every other kit except the empty one. */
    static List<Identifier> kitChoices(MinecraftServer server, Minigame game) {
        List<Identifier> kits = new ArrayList<>();
        kits.add(game.defaultKit());
        for (Identifier kit : KitStorage.kits(server)) {
            if (!kit.equals(game.defaultKit()) && !kit.equals(KitStorage.EMPTY_KIT)) {
                kits.add(kit);
            }
        }
        return kits;
    }

    static void kits(ServerPlayer player, Draft draft, int page, Step next) {
        MinecraftServer server = player.level().getServer();
        Menu menu = new Menu(KITS_TITLE, 6);
        Identifier selected = draft.kit() == null ? draft.game().defaultKit() : draft.kit();
        menu.page(
                kitChoices(server, draft.game()),
                page,
                Menu.inner(1, 4),
                (target, slot, kit) -> {
                    boolean own = kit.equals(draft.game().defaultKit());
                    Icon icon = kitIcon(server, kit);
                    if (own) {
                        icon.line(Component.literal("The game's own kit").withStyle(ChatFormatting.DARK_GRAY));
                    }
                    target.set(
                            slot,
                            icon.blank()
                                    .action(kit.equals(selected) ? "Selected" : "Click to select!")
                                    .glint(kit.equals(selected)),
                            (clicker, click) -> next.open(clicker, draft.withKit(own ? null : kit)));
                },
                (clicker, shown) -> kits(clicker, draft, shown, next));
        menu.back(LAYOUTS_TITLE, clicker -> layouts(clicker, draft)).close();
        menu.open(player);
    }

    /** A kit's icon, the icon of the game it belongs to or a chest, named and described. */
    static Icon kitIcon(MinecraftServer server, Identifier kit) {
        return Icon.of(Minigames.ALL.stream()
                        .filter(game -> game.defaultKit().equals(kit))
                        .findFirst()
                        .map(game -> GameCatalog.entry(game).icon())
                        .orElse(Items.CHEST))
                .name(KitStorage.displayName(kit), ChatFormatting.GREEN)
                .text(KitStorage.description(server, kit));
    }

    /** {@code natural_regeneration} reads "Natural Regeneration". */
    static String title(String key) {
        StringBuilder title = new StringBuilder();
        for (String word : key.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!title.isEmpty()) {
                title.append(' ');
            }
            title.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return title.toString();
    }

    static List<Identifier> maps(MinecraftServer server, Minigame game) {
        return MapArena.maps(server, game.mapDirectory());
    }

    /** After the kit: the map picker for games with maps, otherwise the review. */
    static void afterKit(ServerPlayer player, Draft draft) {
        if (maps(player.level().getServer(), draft.game()).isEmpty()) {
            review(player, draft);
        } else {
            maps(player, draft, PlayMenus::review);
        }
    }

    static void maps(ServerPlayer player, Draft draft, Step next) {
        MinecraftServer server = player.level().getServer();
        Menu menu = new Menu(MAPS_TITLE, 6);
        TeamLayout layout = draft.requireLayout();
        int needed = layout.isFreeForAll() ? 2 : layout.teamSizes().size();
        int[] slots = Menu.inner(1, 4);
        menu.set(
                slots[0],
                Icon.of(Items.MAP)
                        .name("Random Map", ChatFormatting.GREEN)
                        .text("The game picks a map with room for every team.")
                        .blank()
                        .action(draft.map() == null ? "Selected" : "Click to select!")
                        .glint(draft.map() == null),
                (clicker, click) -> next.open(clicker, draft.withMap(null)));
        List<Identifier> maps = maps(server, draft.game());
        for (int index = 0; index < maps.size() && index + 1 < slots.length; index++) {
            Identifier map = maps.get(index);
            int teams = MapArena.teamSlots(server, map);
            Icon icon = Icon.of(Items.FILLED_MAP)
                    .name(title(MapArena.nameOf(map)), ChatFormatting.GREEN)
                    .value("Teams", String.valueOf(teams), ChatFormatting.AQUA)
                    .blank();
            if (teams < needed) {
                icon.refusal("Too small for " + layout.displayName() + ".");
            } else {
                icon.action(map.equals(draft.map()) ? "Selected" : "Click to select!");
            }
            menu.set(
                    slots[index + 1],
                    icon.glint(map.equals(draft.map())),
                    (clicker, click) -> {
                        if (teams < needed) {
                            throw new MatchException(
                                    "%s has room for %d teams; %s needs %d."
                                            .formatted(title(MapArena.nameOf(map)), teams,
                                                    layout.displayName(), needed));
                        }
                        next.open(clicker, draft.withMap(map));
                    });
        }
        menu.back(KITS_TITLE, clicker -> kits(clicker, draft, 0, PlayMenus::afterKit)).close();
        menu.open(player);
    }

    /** The last step: the match setup for a public match, the lineup for a duel. */
    static void review(ServerPlayer player, Draft draft) {
        if (draft.duel()) {
            DuelMenus.lineup(player, draft);
        } else {
            setup(player, draft);
        }
    }

    /** The title of the screen {@link #beforeReview} opens. */
    static String previousTitle(ServerPlayer player, Draft draft) {
        return maps(player.level().getServer(), draft.game()).isEmpty() ? KITS_TITLE : MAPS_TITLE;
    }

    /** The screen "Go Back" from the review returns to. */
    static void beforeReview(ServerPlayer player, Draft draft) {
        if (maps(player.level().getServer(), draft.game()).isEmpty()) {
            kits(player, draft, 0, PlayMenus::afterKit);
        } else {
            maps(player, draft, PlayMenus::review);
        }
    }

    /**
     * The summary row shared by the match setup and the duel lineup: game, layout, kit, map and
     * settings, each opening its step again.
     */
    static void summary(Menu menu, ServerPlayer player, Draft draft) {
        MinecraftServer server = player.level().getServer();
        TeamLayout layout = draft.requireLayout();
        menu.set(
                0,
                gameIcon(draft.game()).blank().action("Click to change the game!"),
                (clicker, click) -> browse(clicker, draft.purpose(), Category.ALL, 0));
        menu.set(
                1,
                layoutIcon(draft.game(), layout).blank().action("Click to change the layout!"),
                (clicker, click) -> layouts(clicker, draft));
        Identifier kit = draft.kit() == null ? draft.game().defaultKit() : draft.kit();
        menu.set(
                2,
                kitIcon(server, kit).name("Kit: " + KitStorage.displayName(kit), ChatFormatting.GREEN)
                        .blank()
                        .action("Click to change the kit!"),
                (clicker, click) -> kits(clicker, draft, 0, PlayMenus::review));
        if (maps(server, draft.game()).isEmpty()) {
            menu.set(
                    3,
                    Icon.of(Items.GRASS_BLOCK)
                            .name("Arena", ChatFormatting.GREEN)
                            .text("This game builds its arena for each match."));
        } else {
            menu.set(
                    3,
                    Icon.of(draft.map() == null ? Items.MAP : Items.FILLED_MAP)
                            .name("Map: " + (draft.map() == null
                                    ? "Random"
                                    : title(MapArena.nameOf(draft.map()))), ChatFormatting.GREEN)
                            .blank()
                            .action("Click to change the map!"),
                    (clicker, click) -> maps(clicker, draft, PlayMenus::review));
        }
        GameSettings values = SettingsStorage.resolve(server, draft.game());
        Icon settings = Icon.of(Items.COMPARATOR).name("Game Settings", ChatFormatting.GREEN);
        List<GameSetting> all = draft.game().settings();
        for (GameSetting setting : all.subList(0, Math.min(6, all.size()))) {
            settings.value(title(setting.key()), String.valueOf(values.get(setting)), ChatFormatting.AQUA);
        }
        if (all.size() > 6) {
            settings.line(Component.literal("and %d more".formatted(all.size() - 6))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        menu.set(
                4,
                settings.blank().action("Click to view!"),
                (clicker, click) -> settings(clicker, draft, 0));
    }

    // Match setup

    static void setup(ServerPlayer player, Draft draft) {
        Menu menu = new Menu(SETUP_TITLE, 6);
        summary(menu, player, draft);
        menu.set(
                6,
                Icon.of(draft.join() ? Items.DYE.pick(DyeColor.LIME) : Items.DYE.pick(DyeColor.GRAY))
                        .name("Join the Match", ChatFormatting.GREEN)
                        .text("Whether you play in the match you open.")
                        .blank()
                        .line(Component.literal("Currently: ").withStyle(ChatFormatting.GRAY)
                                .append(draft.join()
                                        ? Component.literal("YES").withStyle(ChatFormatting.GREEN)
                                        : Component.literal("NO").withStyle(ChatFormatting.RED)))
                        .blank()
                        .action(draft.join() ? "Click to only open it!" : "Click to play in it!"),
                (clicker, click) -> setup(clicker, draft.withJoin(!draft.join())));
        menu.set(
                8,
                Icon.of(Items.EMERALD_BLOCK)
                        .name("Open Match", ChatFormatting.GREEN)
                        .text("Opens the match for everyone to join"
                                + (draft.totalBots() > 0 ? ", with its bots." : "."))
                        .blank()
                        .action("Click to open!"),
                (clicker, click) -> open(clicker, draft));
        if (draft.game() instanceof io.github.brainage04.brainage_minigames.game.skywars.SkyWarsGame skyWars) {
            menu.set(
                    7,
                    Icon.of(Items.BOW)
                            .name(SkyWarsMenus.TITLE, ChatFormatting.GREEN)
                            .text("Your kit when the cages open and which of your perks are on.")
                            .blank()
                            .action("Click to choose!"),
                    (clicker, click) -> SkyWarsMenus.open(clicker, skyWars.mode(), SETUP_TITLE, back -> setup(back, draft)));
        }
        menu.separators(1, -1, "Match", "Players and bots");
        if (draft.requireLayout().isFreeForAll()) {
            freeForAllSlots(menu, player, draft);
        } else {
            teamSlots(menu, player, draft);
        }
        menu.back(previousTitle(player, draft), clicker -> beforeReview(clicker, draft)).close();
        menu.open(player);
    }

    /** Every slot of every team in layout order: you, then the team's bots, then open slots. */
    private static void teamSlots(Menu menu, ServerPlayer player, Draft draft) {
        TeamLayout layout = draft.requireLayout();
        record Seat(int team, int kind) {}
        int you = 0;
        int bot = 1;
        int open = 2;
        List<Seat> seats = new ArrayList<>();
        for (int index = 0; index < layout.teamSizes().size(); index++) {
            int size = layout.teamSizes().get(index);
            boolean mine = draft.join() && draft.team() == index + 1;
            if (mine) {
                seats.add(new Seat(index, you));
            }
            int bots = draft.bots().get(index);
            for (int count = 0; count < bots; count++) {
                seats.add(new Seat(index, bot));
            }
            for (int count = (mine ? 1 : 0) + bots; count < size; count++) {
                seats.add(new Seat(index, open));
            }
        }
        int[] slots = Menu.inner(2, 4);
        boolean botsAvailable = draft.botsAvailable();
        for (int index = 0; index < seats.size() && index < slots.length; index++) {
            Seat seat = seats.get(index);
            int team = seat.team() + 1;
            int teamBots = draft.bots().get(seat.team());
            String teamName = TeamIcons.name(team);
            if (seat.kind() == you) {
                menu.set(slots[index], Icon.head(player.getGameProfile())
                        .name("You", TeamIcons.color(team))
                        .line(Component.literal(teamName).withStyle(TeamIcons.color(team))));
            } else if (seat.kind() == bot) {
                menu.set(
                        slots[index],
                        Icon.of(Items.SKELETON_SKULL)
                                .name("Bot", TeamIcons.color(team))
                                .line(Component.literal(teamName).withStyle(TeamIcons.color(team)))
                                .text("Joins when the match starts.")
                                .blank()
                                .action("Click to open this slot for a player!"),
                        (clicker, click) -> setup(clicker, draft.withBots(seat.team(), teamBots - 1)));
            } else {
                Icon icon = Icon.of(TeamIcons.glass(team))
                        .name("Open Slot", TeamIcons.color(team))
                        .line(Component.literal(teamName).withStyle(TeamIcons.color(team)))
                        .text("Any player can join here.")
                        .blank();
                if (botsAvailable) {
                    icon.action("Click to fill with a bot!");
                } else {
                    icon.line(Component.literal("Bots are not available for this game.")
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
                if (draft.join()) {
                    icon.action("Shift-click to play on this team!");
                }
                menu.set(slots[index], icon, (clicker, click) -> {
                    if (click.shift() && draft.join()) {
                        setup(clicker, draft.withTeam(team));
                    } else if (botsAvailable) {
                        setup(clicker, draft.withBots(seat.team(), teamBots + 1));
                    } else {
                        throw new MatchException("Bots are not available for " + draft.game().displayName() + ".");
                    }
                });
            }
        }
        if (seats.size() > slots.length) {
            menu.set(Menu.slot(4, 8), Icon.of(Items.PAPER)
                    .name("%d more slots".formatted(seats.size() - slots.length), ChatFormatting.GRAY)
                    .text("Use /minigames bots to add bots to large layouts."));
        }
    }

    /** Free-for-all: you, the bots, and buttons to add or remove bots. */
    private static void freeForAllSlots(Menu menu, ServerPlayer player, Draft draft) {
        int[] slots = Menu.inner(2, 4);
        int index = 0;
        if (draft.join()) {
            menu.set(slots[index++], Icon.head(player.getGameProfile()).name("You", ChatFormatting.GREEN));
        }
        int bots = draft.bots().getFirst();
        for (int count = 0; count < bots && index < slots.length; count++) {
            menu.set(
                    slots[index++],
                    Icon.of(Items.SKELETON_SKULL)
                            .name("Bot", ChatFormatting.GRAY)
                            .text("Joins when the match starts.")
                            .blank()
                            .action("Click to remove!"),
                    (clicker, click) -> setup(clicker, draft.withBots(0, bots - 1)));
        }
        if (index < slots.length) {
            Icon add = Icon.of(Items.STAINED_GLASS_PANE.pick(DyeColor.LIME))
                    .name("Add a Bot", ChatFormatting.GREEN)
                    .text("Players can still join; bots fill in when the match starts.")
                    .blank();
            if (!draft.botsAvailable()) {
                add.line(Component.literal("Bots are not available for this game.")
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else if (bots >= MAX_FFA_BOTS) {
                add.refusal("The menu adds up to %d bots.".formatted(MAX_FFA_BOTS));
            } else {
                add.action("Click to add!");
            }
            menu.set(slots[index], add, (clicker, click) -> {
                if (!draft.botsAvailable()) {
                    throw new MatchException("Bots are not available for " + draft.game().displayName() + ".");
                }
                if (bots >= MAX_FFA_BOTS) {
                    throw new MatchException("The menu adds up to %d bots.".formatted(MAX_FFA_BOTS));
                }
                setup(clicker, draft.withBots(0, bots + 1));
            });
        }
    }

    /**
     * Opens the drafted match as {@code player}'s own, joins them to their team when they play,
     * and reserves its bots. A failure after opening stops the match again.
     */
    static Match open(ServerPlayer player, Draft draft) throws MatchException {
        TeamLayout layout = draft.requireLayout();
        if (draft.join()) {
            Optional<String> reason = MatchManager.unavailableReason(player);
            if (reason.isPresent()) {
                throw new MatchException("You cannot play: " + player.getScoreboardName() + " " + reason.get() + ".");
            }
        }
        Minigame game = draft.game();
        Identifier map = draft.map();
        Match match = MatchService.open(
                player,
                game,
                layout,
                draft.kit(),
                (server, settings) -> MapArena.withChosenMap(map, () -> game.openArena(server, settings, layout)));
        try {
            if (draft.join()) {
                MatchManager.join(player, match, draft.team());
            }
            if (layout.isFreeForAll()) {
                if (draft.bots().getFirst() > 0) {
                    match.addBots(0, draft.bots().getFirst());
                }
            } else {
                for (int index = 0; index < draft.bots().size(); index++) {
                    if (draft.bots().get(index) > 0 && match.phase() == MatchPhase.LOBBY) {
                        match.addBots(index + 1, draft.bots().get(index));
                    }
                }
            }
        } catch (MatchException exception) {
            MatchManager.stop(match);
            throw exception;
        }
        player.closeContainer();
        if (!draft.join()) {
            player.sendSystemMessage(Component.literal("Opened ").append(match.title()).append(".")
                    .withStyle(ChatFormatting.GREEN));
        }
        return match;
    }

    // Settings

    static void settings(ServerPlayer player, Draft draft, int page) {
        Minigame game = draft.game();
        MinecraftServer server = player.level().getServer();
        boolean operator = MatchService.isOperator(player);
        GameSettings values = SettingsStorage.resolve(server, game);
        Menu menu = new Menu(game.displayName() + " Settings", 6);
        menu.set(
                4,
                Icon.of(Items.BOOK)
                        .name(game.displayName() + " Settings", ChatFormatting.GREEN)
                        .text("The server's settings for every new " + game.displayName() + " match.")
                        .blank()
                        .text(operator
                                ? "Left-click +1, right-click -1, shift-left +10, shift-right resets."
                                : "Only operators change settings."));
        menu.page(
                game.settings(),
                page,
                Menu.inner(1, 4),
                (target, slot, setting) -> {
                    int value = values.get(setting);
                    boolean toggle = setting.min() == 0 && setting.max() == 1;
                    Icon icon = Icon.of(toggle ? (value == 1 ? Items.DYE.pick(DyeColor.LIME) : Items.DYE.pick(DyeColor.GRAY)) : Items.REPEATER)
                            .name(title(setting.key()), ChatFormatting.GREEN)
                            .text(setting.description())
                            .blank()
                            .line(Component.literal("Currently: ").withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal(toggle
                                                    ? (value == 1 ? "ENABLED" : "DISABLED")
                                                    : String.valueOf(value))
                                            .withStyle(toggle && value == 0 ? ChatFormatting.RED : ChatFormatting.GREEN)))
                            .value("Default", String.valueOf(setting.defaultValue()), ChatFormatting.GRAY);
                    if (operator) {
                        icon.blank().action(toggle
                                ? (value == 1 ? "Click to disable!" : "Click to enable!")
                                : "Click to change!");
                    }
                    target.set(slot, icon, operator
                            ? (clicker, click) -> {
                                change(clicker, game, setting, value, toggle, click);
                                settings(clicker, draft, page);
                            }
                            : null);
                },
                (clicker, shown) -> settings(clicker, draft, shown));
        menu.back(draft.duel() ? DuelMenus.LINEUP_TITLE : SETUP_TITLE, clicker -> review(clicker, draft))
                .close();
        menu.open(player);
    }

    private static void change(
            ServerPlayer player, Minigame game, GameSetting setting, int value, boolean toggle, Click click)
            throws MatchException {
        if (!MatchService.isOperator(player)) {
            throw new MatchException("Only operators change settings.");
        }
        MinecraftServer server = player.level().getServer();
        if (click == Click.SHIFT_RIGHT) {
            SettingsStorage.reset(server, game, setting);
        } else {
            int next = toggle ? 1 - value
                    : click == Click.SHIFT_LEFT ? value + 10
                    : click.right() ? value - 1
                    : value + 1;
            SettingsStorage.set(server, game, setting, Math.clamp(next, setting.min(), setting.max()));
        }
        game.validate(SettingsStorage.resolve(server, game))
                .ifPresent(problem -> player.sendSystemMessage(Component.literal(
                                "New %s matches cannot open until this is fixed: %s"
                                        .formatted(game.id(), problem))
                        .withStyle(ChatFormatting.RED)));
    }
}
