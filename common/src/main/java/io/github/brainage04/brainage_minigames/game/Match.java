package io.github.brainage04.brainage_minigames.game;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.api.MatchBots;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.game.uhc.UhcGame;
import io.github.brainage04.brainage_minigames.scoreboard.ModScoreboard;
import io.github.brainage04.brainage_minigames.scoreboard.EloRatings;
import io.github.brainage04.brainage_minigames.storage.KitStorage;
import io.github.brainage04.brainage_minigames.storage.PlayerSnapshotStorage;
import io.github.brainage04.brainage_minigames.util.LootUtils;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;
import org.jspecify.annotations.Nullable;

/**
 * One running instance of a {@link Minigame}, from its lobby until every player has been restored.
 */
public final class Match {
    /**
     * Prefix of the scoreboard teams matches create; leftovers from a crash are removed on server
     * start.
     */
    public static final String TEAM_PREFIX = "bm_";

    private static final Identifier REWARDS = BrainageMinigames.id("rewards/default");
    private static final int ENDED_TICKS = 5 * 20;

    /** A lobby that could not start on its own tries again this much later. */
    private static final int RETRY_TICKS = 5 * 20;

    /** A player who hurt someone this recently gets the kill when they die. */
    private static final int KILL_CREDIT_TICKS = 10 * 20;

    /** A lava source this close to a participant in lava is taken to be where the lava came from. */
    private static final int LAVA_FLOW_REACH = 8;

    /** A chat message starting with this goes to everyone in a team match, not just the team. */
    public static final String SHOUT_PREFIX = "!";

    private static final List<TeamColor> COLORS =
            List.of(
                    TeamColor.RED,
                    TeamColor.BLUE,
                    TeamColor.GREEN,
                    TeamColor.YELLOW,
                    TeamColor.AQUA,
                    TeamColor.LIGHT_PURPLE,
                    TeamColor.GOLD,
                    TeamColor.WHITE,
                    TeamColor.GRAY);

    private final int id;
    private final MinecraftServer server;
    private final Minigame game;
    private final TeamLayout layout;
    private final GameSettings settings;
    private final Identifier kit;
    private final Arena arena;

    /** Everyone allowed to join a private match; empty for a public one. */
    private final Set<UUID> invited;

    /** The player who opened the match, who may start and stop it; null when the server did. */
    private @Nullable UUID owner;

    private @Nullable String ownerName;

    /** Lobby players and the team number they asked for (0 for any). */
    private final Map<UUID, Integer> lobby = new LinkedHashMap<>();

    /**
     * Everyone whose state this match saved and must restore: lobby players, participants and
     * watchers.
     */
    private final Set<UUID> members = new LinkedHashSet<>();

    /** Members who joined while the arena was still preparing its lobby, to move there once ready. */
    private final Set<UUID> arriving = new LinkedHashSet<>();

    private final Set<UUID> alive = new LinkedHashSet<>();
    private final List<MatchTeam> teams = new ArrayList<>();
    private final Map<UUID, MatchTeam> teamByPlayer = new HashMap<>();
    private final Map<UUID, EloRatings.Player> ratingPlayers = new LinkedHashMap<>();

    /** Names of everyone who ever joined, so offline players can still be listed. */
    private final Map<UUID, String> names = new HashMap<>();

    private MatchPhase phase = MatchPhase.LOBBY;
    private int phaseTicks;
    private List<MatchTeam> winners = List.of();
    private boolean closed;
    private final MatchSidebar sidebar = new MatchSidebar();

    /** Blocks and fluids participants placed during this match and have not broken since. */
    private final LongSet placedBlocks = new LongOpenHashSet();

    /** The last participant who hurt each participant, for kill credit. */
    private final Map<UUID, LastAttack> lastAttacks = new HashMap<>();

    private final AntiJanitor antiJanitor = new AntiJanitor(this);

    /**
     * Lava sources and fire participants placed, by level and position, so a teammate's lava or
     * fire cannot hurt them.
     */
    private final Map<ServerLevel, Long2ObjectOpenHashMap<Hazard>> hazards = new HashMap<>();

    /** How many times each team number has been given a spawn, to rotate through its spawns. */
    private final Map<Integer, Integer> spawnsGiven = new HashMap<>();

    /** Teams that started with at least one participant. */
    private int contestingTeams;

    /**
     * The arena's spawn for each team, chosen once when the match starts; arenas other than maps
     * may pick them at random.
     */
    private List<Arena.Spawn> teamSpawns = List.of();
    private boolean preparingSpawns;

    /** Countdown time does not advance until the arena's spawn terrain is ready. */
    public boolean preparingSpawns() {
        return preparingSpawns;
    }

    private record LastAttack(UUID attacker, int tick) {}

    /** Bot slots reserved in the lobby, by team number; 0 is any team, or a free-for-all slot. */
    private final Map<Integer, Integer> reservedBots = new TreeMap<>();

    /** Lobby players who voted to start now. */
    private final Set<UUID> startVotes = new LinkedHashSet<>();

    /** Participants a bot provider spawned for this match. */
    private final Set<UUID> bots = new LinkedHashSet<>();

    /** Eliminated bots to hand back to their provider on the next tick. */
    private final List<UUID> dismissals = new ArrayList<>();

    private String botDifficulty = MatchBots.MIXED;

    /** Lobby ticks since its first player started waiting; 0 while nobody waits. */
    private int waitingTicks;

    /** Lobby tick of the next attempt to start after one failed. */
    private int retryTick;

    /** Why the lobby last could not start on its own, so it is announced once. */
    private @Nullable String lobbyNotice;

    Match(
            int id,
            MinecraftServer server,
            Minigame game,
            TeamLayout layout,
            GameSettings settings,
            Identifier kit,
            Arena arena,
            Set<UUID> invited) {
        this.id = id;
        this.server = server;
        this.game = game;
        this.layout = layout;
        this.settings = settings;
        this.kit = kit;
        this.arena = arena;
        this.invited = invited;
        if (arena instanceof MapArena map) {
            map.onReset(() -> {
                placedBlocks.clear();
                hazards.clear();
                ContainerProtection.clear(this);
            });
        }
    }

    /** Ticks spent in the current phase. */
    int phaseTicks() {
        return phaseTicks;
    }

    /** Players waiting in the lobby, in the order they joined. */
    List<UUID> waiting() {
        return List.copyOf(lobby.keySet());
    }

    public boolean isWaiting(UUID playerId) {
        return lobby.containsKey(playerId);
    }

    /** The team a lobby player asked for, or 0 for any. */
    int requestedTeam(UUID playerId) {
        return lobby.getOrDefault(playerId, 0);
    }

    public String nameOf(UUID playerId) {
        return names.getOrDefault(playerId, playerId.toString());
    }

    public int id() {
        return id;
    }

    public MinecraftServer server() {
        return server;
    }

    public Minigame game() {
        return game;
    }

    public TeamLayout layout() {
        return layout;
    }

    public GameSettings settings() {
        return settings;
    }

    public Identifier kit() {
        return kit;
    }

    public Arena arena() {
        return arena;
    }

    public MatchPhase phase() {
        return phase;
    }

    /** Ticks since the match became active; 0 in every other phase. */
    public int activeTicks() {
        return phase == MatchPhase.ACTIVE ? phaseTicks : 0;
    }

    public int lobbySize() {
        return lobby.size();
    }

    public List<MatchTeam> teams() {
        return List.copyOf(teams);
    }

    /**
     * Teams that started with at least one participant: those competing for the win. A lobby that
     * started before filling up may leave some of a layout's teams empty.
     */
    public int contestingTeams() {
        return contestingTeams;
    }

    public List<MatchTeam> winners() {
        return winners;
    }

    /** Whether anyone may join: the match is public and still in its lobby. */
    public boolean isOpenLobby() {
        return phase == MatchPhase.LOBBY && invited.isEmpty();
    }

    public boolean isPrivate() {
        return !invited.isEmpty();
    }

    public boolean isClosed() {
        return closed;
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public Optional<String> ownerName() {
        return Optional.ofNullable(ownerName);
    }

    public boolean isOwner(UUID playerId) {
        return playerId.equals(owner);
    }

    /** Makes {@code player} the owner; {@link MatchManager} calls this as the match opens. */
    void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getScoreboardName();
    }

    public boolean involves(UUID playerId) {
        return members.contains(playerId);
    }

    public boolean isAlive(UUID playerId) {
        return alive.contains(playerId);
    }
    public int aliveCount() { return alive.size(); }

    /** Whether the player is alive in the active phase: the only time they may play. */
    public boolean isActiveParticipant(UUID playerId) {
        return phase == MatchPhase.ACTIVE && alive.contains(playerId);
    }

    public Optional<MatchTeam> teamOf(UUID playerId) {
        return Optional.ofNullable(teamByPlayer.get(playerId));
    }

    /** The team with {@code number}, counting from 1, once teams exist. */
    public Optional<MatchTeam> teamNumbered(int number) {
        return number >= 1 && number <= teams.size()
                ? Optional.of(teams.get(number - 1))
                : Optional.empty();
    }

    /** The map's name when the match is played on a {@link MapArena}. */
    public Optional<String> mapName() {
        return arena instanceof MapArena map ? Optional.of(map.mapName()) : Optional.empty();
    }

    /**
     * Whether a participant placed a block or fluid at {@code pos} during this match that has not
     * been broken since; a {@link MapArena#reset} forgets every placed block.
     */
    public boolean isPlacedBlock(BlockPos pos) {
        return placedBlocks.contains(pos.asLong());
    }

    public List<ServerPlayer> alivePlayers() {
        return online(alive);
    }

    public List<ServerPlayer> onlineMembers() {
        return online(members);
    }

    public Component title() {
        return Component.literal("Match #%d (%s %s)".formatted(id, game.displayName(), layout.displayName()));
    }

    void join(ServerPlayer player, int teamNumber) throws MatchException {
        if (phase != MatchPhase.LOBBY) {
            throw new MatchException("Match #" + id + " has already started.");
        }
        if (!invited.isEmpty() && !invited.contains(player.getUUID())) {
            throw new MatchException("Match #" + id + " is a private duel.");
        }
        if (lobby.size() + reservedBots() >= layout.capacity()) {
            throw new MatchException("Match #" + id + " is full.");
        }
        if (layout.isFreeForAll() && lobby.size() + reservedBots() >= arena.maxTeams()) {
            throw new MatchException(
                    "Match #%d is full: the map has room for %d players."
                            .formatted(id, arena.maxTeams()));
        }
        if (teamNumber != 0) {
            if (layout.isFreeForAll()) {
                throw new MatchException("Free-for-all matches have no teams to choose from.");
            }
            if (teamNumber > layout.teamSizes().size()) {
                throw new MatchException(
                        "Match #%d has %d teams.".formatted(id, layout.teamSizes().size()));
            }
            if (requests(teamNumber) + reservedBots(teamNumber)
                    >= layout.teamSizes().get(teamNumber - 1)) {
                throw new MatchException("Team " + teamNumber + " is full.");
            }
        }
        enter(player, GameType.ADVENTURE);
        lobby.put(player.getUUID(), teamNumber);
        io.github.brainage04.brainage_minigames.menu.LobbyItems.give(player);
        retryTick = 0;
        broadcast(
                Component.literal(
                                "%s joined (%s)."
                                        .formatted(player.getScoreboardName(), lobbyCount()))
                        .withStyle(ChatFormatting.GREEN));
        try {
            startIfFull();
        } catch (MatchException exception) {
            announce(exception.getMessage());
        }
        int seconds = autoStartSeconds();
        if (seconds > 0 && phase == MatchPhase.LOBBY) {
            int left = Math.ceilDiv(Math.max(0, seconds * 20 - waitingTicks), 20);
            player.sendSystemMessage(
                    Component.literal(
                                    "The match starts in %d seconds, or as soon as most players here vote, with %s. "
                                            .formatted(
                                                    left,
                                                    fillsEmptySlots()
                                                            ? "bots in the empty slots"
                                                            : "whoever is here (at least 2 players)"))
                            .withStyle(ChatFormatting.GOLD)
                            .append(voteButton()));
        }
    }

    private static Component voteButton() {
        return Component.literal("[Vote to start]")
                .withStyle(
                        style ->
                                style.withColor(ChatFormatting.GREEN)
                                        .withClickEvent(new ClickEvent.RunCommand("/minigames vote")));
    }

    /** Lobby occupancy for messages: {@code 3/8}, plus reserved bots, or a free-for-all count. */
    private String lobbyCount() {
        String count =
                layout.isFreeForAll()
                        ? lobby.size() + " players"
                        : lobby.size() + reservedBots() + "/" + layout.capacity();
        int reserved = reservedBots();
        return reserved == 0 ? count : count + ", " + reserved + (reserved == 1 ? " bot" : " bots");
    }

    private long requests(int teamNumber) {
        return lobby.values().stream().filter(number -> number == teamNumber).count();
    }

    /** A fixed layout whose slots are all taken by players and reserved bots starts at once. */
    private void startIfFull() throws MatchException {
        if (layout.isFreeForAll()
                || lobby.isEmpty()
                || lobby.size() + reservedBots() < layout.capacity()) {
            return;
        }
        start();
    }

    public boolean isBot(UUID playerId) {
        return bots.contains(playerId);
    }

    /** Bot slots reserved in the lobby, on every team. */
    public int reservedBots() {
        int total = 0;
        for (int count : reservedBots.values()) total += count;
        return total;
    }

    /** Bot slots reserved on team {@code teamNumber}; 0 counts those on any team. */
    public int reservedBots(int teamNumber) {
        return reservedBots.getOrDefault(teamNumber, 0);
    }

    /** Slots no player has joined and no bot is reserved for: of the layout, or of the map. */
    public int freeSlots() {
        int capacity = layout.isFreeForAll() ? arena.maxTeams() : layout.capacity();
        return phase == MatchPhase.LOBBY ? Math.max(0, capacity - lobby.size() - reservedBots()) : 0;
    }

    /** Free slots on a team of a fixed layout, counting only players and bots that asked for it. */
    public int freeSlots(int teamNumber) {
        if (layout.isFreeForAll() || teamNumber < 1 || teamNumber > layout.teamSizes().size()) return 0;
        return (int) Math.max(0, Math.min(freeSlots(),
                layout.teamSizes().get(teamNumber - 1) - requests(teamNumber) - reservedBots(teamNumber)));
    }

    /**
     * Reserves {@code count} slots of the lobby for bots, on team {@code teamNumber} or, with 0, on
     * any team (always 0 in a free-for-all). The bots are spawned when the match starts; a fixed
     * layout whose slots are then all taken starts at once.
     */
    public void addBots(int teamNumber, int count) throws MatchException {
        if (phase != MatchPhase.LOBBY) {
            throw new MatchException("Match #" + id + " has already started.");
        }
        if (!game.supportsBots()) {
            throw new MatchException(game.displayName() + " cannot be played by bots.");
        }
        if (!MatchBots.available()) {
            throw new MatchException("No bot provider is installed on this server.");
        }
        if (count < 1) {
            throw new MatchException("Add at least one bot.");
        }
        if (teamNumber != 0) {
            if (layout.isFreeForAll()) {
                throw new MatchException("Free-for-all matches have no teams to choose from.");
            }
            if (teamNumber > layout.teamSizes().size()) {
                throw new MatchException(
                        "Match #%d has %d teams.".formatted(id, layout.teamSizes().size()));
            }
            if (count > freeSlots(teamNumber)) {
                throw new MatchException(
                        "Team %d has room for %d more.".formatted(teamNumber, freeSlots(teamNumber)));
            }
        } else if (count > freeSlots()) {
            throw new MatchException("Match #%d has room for %d more.".formatted(id, freeSlots()));
        }
        reservedBots.merge(teamNumber, count, Integer::sum);
        retryTick = 0;
        broadcast(
                Component.literal(
                                "%d %s reserved%s (%s)."
                                        .formatted(
                                                count,
                                                count == 1 ? "bot slot" : "bot slots",
                                                teamNumber == 0 ? "" : " on team " + teamNumber,
                                                lobbyCount()))
                        .withStyle(ChatFormatting.GREEN));
        startIfFull();
    }

    /** Frees every reserved bot slot. */
    public void clearBots() throws MatchException {
        if (phase != MatchPhase.LOBBY) {
            throw new MatchException("Match #" + id + " has already started.");
        }
        reservedBots.clear();
        broadcast(Component.literal("Bot slots cleared (%s).".formatted(lobbyCount())).withStyle(ChatFormatting.YELLOW));
    }

    public String botDifficulty() {
        return botDifficulty;
    }

    /** How the provider plays this match's bots: {@link MatchBots#DIFFICULTIES}. */
    public void setBotDifficulty(String difficulty) throws MatchException {
        if (!MatchBots.DIFFICULTIES.contains(difficulty)) {
            throw new MatchException(
                    "Bot difficulty must be one of " + String.join(", ", MatchBots.DIFFICULTIES) + ".");
        }
        botDifficulty = difficulty;
    }

    /**
     * Whether this lobby fills its empty slots with bots when it starts before it is full (by a
     * vote, the lobby timer, {@code /minigames start} or the menu): while {@link
     * MatchService#FILL_BOTS_ON_EARLY_START} is on, the game can be played by bots, a provider is
     * installed, and the match is public. Reserved bot slots are filled either way.
     */
    public boolean fillsEmptySlots() {
        return invited.isEmpty() && game.supportsBots() && MatchBots.available()
                && server.getGameRules().get(MatchService.FILL_BOTS_ON_EARLY_START);
    }

    /** Seconds after its first player started waiting that the lobby starts; 0 when it does not. */
    private int autoStartSeconds() {
        return invited.isEmpty() ? game.setting(GameSetting.LOBBY_SECONDS).map(settings::get).orElse(0) : 0;
    }

    /** Seconds until the lobby starts on its own, while a player is waiting and it counts down. */
    public OptionalInt autoStartSecondsLeft() {
        int seconds = autoStartSeconds();
        if (phase != MatchPhase.LOBBY || seconds == 0 || lobby.isEmpty()) return OptionalInt.empty();
        return OptionalInt.of(Math.ceilDiv(Math.max(0, seconds * 20 - waitingTicks), 20));
    }

    public boolean canVoteStart() {
        return phase == MatchPhase.LOBBY;
    }

    public int startVotes() {
        return startVotes.size();
    }

    /** Votes that start the lobby now: a majority of the players waiting in it. */
    public int votesNeeded() {
        return lobby.size() / 2 + 1;
    }

    public boolean hasVotedStart(UUID playerId) {
        return startVotes.contains(playerId);
    }

    /**
     * Records a waiting player's vote to start now. Once most of the players waiting have voted,
     * the match starts with them, filling empty slots with bots where the lobby does.
     */
    public void voteStart(ServerPlayer player) throws MatchException {
        if (phase != MatchPhase.LOBBY || !lobby.containsKey(player.getUUID())) {
            throw new MatchException("Only players waiting in a lobby can vote to start it.");
        }
        if (!startVotes.add(player.getUUID())) {
            throw new MatchException("You already voted to start match #" + id + ".");
        }
        retryTick = 0;
        broadcast(
                Component.literal(
                                "%s voted to start (%d/%d)."
                                        .formatted(
                                                player.getScoreboardName(),
                                                startVotes.size(),
                                                votesNeeded()))
                        .withStyle(ChatFormatting.GREEN));
        if (startVotes.size() >= votesNeeded()) {
            tryLaunch();
        }
    }

    /**
     * Starts with the players waiting, filling empty slots with bots where the lobby does; reserved
     * bot slots are always filled. Needs two participants on two teams.
     */
    public void startNow() throws MatchException {
        launch(true);
    }

    /** Starts a lobby that waited long enough, or that most of its players voted to start. */
    private void tickLobby() {
        if (lobby.isEmpty()) {
            waitingTicks = 0;
            retryTick = 0;
            lobbyNotice = null;
            return;
        }
        waitingTicks++;
        int seconds = autoStartSeconds();
        boolean due = seconds > 0 && waitingTicks >= seconds * 20;
        if ((due || startVotes.size() >= votesNeeded()) && waitingTicks >= retryTick) {
            tryLaunch();
        }
    }

    /**
     * Starts the lobby now if it can; otherwise tells its players why once and tries again a few
     * seconds later, or as soon as someone joins, votes or reserves a bot.
     */
    private void tryLaunch() {
        try {
            launch(true);
        } catch (MatchException exception) {
            retryTick = waitingTicks + RETRY_TICKS;
            announce(exception.getMessage());
        }
    }

    /** Tells the lobby why it cannot start yet, once per reason. */
    private void announce(String reason) {
        if (!reason.equals(lobbyNotice)) {
            lobbyNotice = reason;
            broadcast(Component.literal(reason + " Waiting for more players.").withStyle(ChatFormatting.YELLOW));
        }
    }

    void watch(ServerPlayer player) throws MatchException {
        if (phase == MatchPhase.ENDED) {
            throw new MatchException("Match #" + id + " has already ended.");
        }
        enter(player, GameType.SPECTATOR);
        player.sendSystemMessage(
                Component.literal("You are watching ")
                        .append(title())
                        .append(".")
                        .withStyle(ChatFormatting.GREEN));
    }

    /**
     * Saves the player's state, resets them and moves them to the lobby; while the arena is still
     * preparing its lobby they wait where they are and are moved once it is ready.
     */
    private void enter(ServerPlayer player, GameType gameType) throws MatchException {
        if (!PlayerSnapshotStorage.save(player)) {
            throw new MatchException(
                    "Your current state could not be saved, so you were not moved.");
        }
        PlayerUtils.reset(player, gameType);
        if (!arena.lobbyReady()) {
            arriving.add(player.getUUID());
            player.sendSystemMessage(
                    Component.literal("Preparing the arena; you will be moved there in a moment.")
                            .withStyle(ChatFormatting.GOLD));
        } else if (PlayerUtils.teleport(player, arena.level(), arena.lobbyPosition(), 0.0F) == null) {
            PlayerSnapshotStorage.restore(player);
            throw new MatchException("The arena could not be reached.");
        }
        members.add(player.getUUID());
        names.put(player.getUUID(), player.getScoreboardName());
    }

    /**
     * Moves members who joined before the lobby was ready there; one who cannot be moved leaves
     * the match.
     */
    private void moveArrivals() {
        List<ServerPlayer> moving = online(arriving);
        arriving.clear();
        for (ServerPlayer player : moving) {
            if (!members.contains(player.getUUID())) continue;
            if (PlayerUtils.teleport(player, arena.level(), arena.lobbyPosition(), 0.0F) == null) {
                player.sendSystemMessage(
                        Component.literal("The arena could not be reached.").withStyle(ChatFormatting.RED));
                leave(player);
            }
        }
    }

    void leave(ServerPlayer player) {
        player.closeContainer();
        releaseCombatBalance(player);
        UUID playerId = player.getUUID();
        members.remove(playerId);
        arriving.remove(playerId);
        lobby.remove(playerId);
        startVotes.remove(playerId);
        if (alive.contains(playerId)) antiJanitor.storeDrops(player);
        io.github.brainage04.brainage_minigames.game.uhc.UhcProgression.eliminated(this, player);
        if (alive.remove(playerId)) {
            broadcast(
                    Component.literal(player.getScoreboardName() + " forfeited.")
                            .withStyle(ChatFormatting.RED));
        }
        release(player);
        PlayerSnapshotStorage.restore(player);
    }

    /** The player's snapshot stays stored and is restored when they reconnect. */
    void disconnect(ServerPlayer player) {
        releaseCombatBalance(player);
        UUID playerId = player.getUUID();
        if (members.contains(playerId)
                && !bots.contains(playerId)
                && UhcCombatLogger.disconnect(this, player)) {
            sidebar.forget(playerId);
            return;
        }
        if (!members.remove(playerId)) {
            return;
        }
        lobby.remove(playerId);
        arriving.remove(playerId);
        startVotes.remove(playerId);
        sidebar.forget(playerId);
        game.onRelease(this, player);
        if (alive.contains(playerId)) antiJanitor.storeDrops(player);
        io.github.brainage04.brainage_minigames.game.uhc.UhcProgression.eliminated(this, player);
        if (alive.remove(playerId)) {
            broadcast(
                    Component.literal(
                                    player.getScoreboardName()
                                            + " disconnected and was eliminated.")
                            .withStyle(ChatFormatting.RED));
        }
        // A provider that removed its bot gets the remover call it expects; it ignores repeats.
        if (bots.contains(playerId)) MatchBots.remove(player);
    }

    boolean reconnect(ServerPlayer player) {
        if (!UhcCombatLogger.reconnect(this, player)) return false;
        updateCombatBalance(player);
        if (arena instanceof io.github.brainage04.brainage_minigames.game.uhc.NaturalArena natural) {
            natural.sendBorder(player);
        } else if (arena instanceof io.github.brainage04.brainage_minigames.game.uhc.UhcArena uhc) {
            uhc.sendBorder(player);
            if (io.github.brainage04.brainage_minigames.game.uhc.UhcGame.deathmatchFrozen(this)
                    && game instanceof io.github.brainage04.brainage_minigames.game.uhc.UhcGame mode) {
                freeze(player, Math.max(0, mode.deathmatchStartTicks(this)
                        + io.github.brainage04.brainage_minigames.game.uhc.UhcGame.DEATHMATCH_FREEZE_TICKS - activeTicks()));
            }
        }
        sidebar.show(player, this);
        return true;
    }

    void loggerKilled(ServerPlayer player) {
        die(player, Component.literal(player.getScoreboardName() + " was killed while disconnected!"), false);
        members.remove(player.getUUID());
        game.onRelease(this, player);
    }

    void loggerEnded(ServerPlayer player) {
        members.remove(player.getUUID());
        game.onRelease(this, player);
    }

    /**
     * Locks the teams, prepares their terrain, then places them for the countdown. Reserved bot
     * slots are filled first; a lobby that then has fewer players than its layout starts as long
     * as two participants are on two teams.
     */
    public void start() throws MatchException {
        launch(false);
    }

    /** {@code now}: started before filling up, by a vote, the lobby timer or a start command. */
    private void launch(boolean now) throws MatchException {
        if (phase != MatchPhase.LOBBY) {
            throw new MatchException("Match #" + id + " has already started.");
        }
        boolean provider = game.supportsBots() && MatchBots.available();
        int fill = now && fillsEmptySlots() ? emptySlots() : 0;
        int wanted = provider ? reservedBots() + fill : 0;
        boolean partial = now || reservedBots() > 0;
        if (!partial && lobby.size() < layout.requiredPlayers()) {
            throw new MatchException(
                    "Match #%d needs %d players to start; %d joined."
                            .formatted(id, layout.requiredPlayers(), lobby.size()));
        }
        if (lobby.size() + wanted < 2) {
            throw new MatchException(
                    "Match #%d needs at least 2 players to start; %d joined."
                            .formatted(id, lobby.size()));
        }
        List<ServerPlayer> added = spawnBots(wanted);
        Optional<String> unplayable = partial ? unplayableReason() : Optional.empty();
        if (unplayable.isPresent()) {
            added.forEach(this::dismiss);
            throw new MatchException(unplayable.get());
        }
        reservedBots.clear();
        startVotes.clear();
        lobbyNotice = null;
        createTeams();
        refreshBotNames();
        alive.addAll(lobby.keySet());
        lobby.clear();
        phase = MatchPhase.COUNTDOWN;
        phaseTicks = 0;
        // Everyone reaches the lobby before anyone is placed at a spawn.
        boolean lobbyReady = arena.lobbyReady();
        preparingSpawns = !lobbyReady || !arena.prepareSpawns(teams.size());
        if (preparingSpawns) {
            broadcast(Component.literal(lobbyReady ? "Preparing spawn terrain..." : "Preparing the arena...")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            placeAtSpawns();
        }
    }

    /** Most participants a free-for-all fills to when neither the game nor its arena limits it. */
    public static final int FREE_FOR_ALL_FILL = 8;

    /**
     * Slots a lobby that starts early fills with bots: the rest of a fixed layout, or in a
     * free-for-all up to the game's {@code lobby_size}, else every spawn of its map (SkyWars cages,
     * Quake spawns), else {@link #FREE_FOR_ALL_FILL}; never more than the map has spawns for.
     */
    private int emptySlots() {
        int target = layout.capacity();
        if (layout.isFreeForAll()) {
            int spawns = arena.maxTeams();
            int fallback = spawns == Integer.MAX_VALUE ? FREE_FOR_ALL_FILL : spawns;
            target = Math.min(spawns, game.setting(GameSetting.LOBBY_SIZE).map(settings::get).orElse(fallback));
        }
        return Math.max(0, target - lobby.size() - reservedBots());
    }

    /** Why the players in the lobby cannot play a match as they are, if they cannot. */
    private Optional<String> unplayableReason() {
        if (lobby.size() < 2) {
            return Optional.of(
                    "Match #%d needs at least 2 players to start; %d joined."
                            .formatted(id, lobby.size()));
        }
        if (layout.isFreeForAll() || lobby.containsValue(0)) return Optional.empty();
        return lobby.values().stream().distinct().count() >= 2
                ? Optional.empty()
                : Optional.of("Match #%d needs players on at least two teams to start.".formatted(id));
    }

    /**
     * Spawns {@code count} bots and puts them in the lobby like joining players: on the teams their
     * slots were reserved on, then on any team. The provider may spawn fewer.
     */
    private List<ServerPlayer> spawnBots(int count) {
        if (count == 0) return List.of();
        List<ServerPlayer> spawned =
                MatchBots.spawn(server, game.id(), String.valueOf(id), count, botDifficulty);
        List<Integer> slots = new ArrayList<>(count);
        reservedBots.forEach(
                (team, reserved) -> {
                    if (team > 0) slots.addAll(Collections.nCopies(reserved, team));
                });
        while (slots.size() < count) slots.add(0);
        List<ServerPlayer> added = new ArrayList<>(spawned.size());
        for (int index = 0; index < spawned.size(); index++) {
            ServerPlayer bot = spawned.get(index);
            try {
                enter(bot, GameType.ADVENTURE);
            } catch (MatchException exception) {
                BrainageMinigames.LOGGER.warn(
                        "Bot {} could not join match #{}: {}",
                        bot.getScoreboardName(),
                        id,
                        exception.getMessage());
                MatchBots.remove(bot);
                continue;
            }
            bots.add(bot.getUUID());
            lobby.put(bot.getUUID(), slots.get(index));
            added.add(bot);
        }
        if (!added.isEmpty()) {
            broadcast(
                    Component.literal(
                                    "%d %s joined: %s."
                                            .formatted(
                                                    added.size(),
                                                    added.size() == 1 ? "bot" : "bots",
                                                    String.join(
                                                            ", ",
                                                            added.stream()
                                                                    .map(ServerPlayer::getScoreboardName)
                                                                    .toList())))
                            .withStyle(ChatFormatting.GREEN));
        }
        if (added.size() < count) {
            broadcast(
                    Component.literal(
                                    "Only %d of %d bots could join.".formatted(added.size(), count))
                            .withStyle(ChatFormatting.YELLOW));
        }
        return added;
    }

    /**
     * Takes a bot out of the match, restores it like a leaving player and hands it back to its
     * provider.
     */
    private void dismiss(ServerPlayer bot) {
        UUID botId = bot.getUUID();
        if (lobby.remove(botId) != null) bots.remove(botId);
        alive.remove(botId);
        members.remove(botId);
        arriving.remove(botId);
        release(bot);
        PlayerSnapshotStorage.restore(bot);
        MatchBots.remove(bot);
    }

    /** Sends the tab list's {@code [BOT]} names, which follow the bots' teams. */
    private void refreshBotNames() {
        List<ServerPlayer> online = online(bots);
        if (!online.isEmpty()) {
            server.getPlayerList()
                    .broadcastAll(
                            new ClientboundPlayerInfoUpdatePacket(
                                    java.util.EnumSet.of(
                                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                                    online));
        }
    }

    /** The tab list name of a bot in this match, or null for anyone else. */
    public @Nullable Component botTabName(ServerPlayer player) {
        if (!bots.contains(player.getUUID()) || !members.contains(player.getUUID())) return null;
        return Component.literal("[BOT] ")
                .withStyle(ChatFormatting.GRAY)
                .append(PlayerTeam.formatNameForTeam(player.getTeam(), Component.literal(player.getScoreboardName())));
    }

    private void placeAtSpawns() {
        if (!(arena instanceof MapArena)) teamSpawns = arena.spawns(teams.size());

        int countdownTicks = settings.get(GameSetting.COUNTDOWN_SECONDS) * 20;
        for (MatchTeam team : teams) {
            for (ServerPlayer player : online(team.members())) {
                if (!alive.contains(player.getUUID())) continue;
                Arena.Spawn spawn = nextSpawn(team);
                PlayerUtils.teleport(player, arena.level(), spawn.position(), spawn.yaw());
                freeze(player, countdownTicks);
            }
        }
        arena.releaseSpawns();
        phaseTicks = 0;
        if (countdownTicks > 0) {
            broadcast(
                    Component.literal("Starting in %d seconds.".formatted(countdownTicks / 20))
                            .withStyle(ChatFormatting.GOLD));
        }
    }

    private void createTeams() {
        List<Integer> sizes =
                layout.isFreeForAll() ? Collections.nCopies(lobby.size(), 1) : layout.teamSizes();
        ServerScoreboard scoreboard = server.getScoreboard();
        for (int index = 0; index < sizes.size(); index++) {
            String name = TEAM_PREFIX + id + "_" + (index + 1);
            PlayerTeam existing = scoreboard.getPlayerTeam(name);
            if (existing != null) {
                scoreboard.removePlayerTeam(existing);
            }
            PlayerTeam scoreboardTeam = scoreboard.addPlayerTeam(name);
            TeamColor color = teamColor(index + 1);
            scoreboardTeam.setColor(Optional.of(color));
            scoreboardTeam.setDisplayName(Component.literal(teamName(index + 1)));
            scoreboardTeam.setAllowFriendlyFire(false);
            scoreboardTeam.setSeeFriendlyInvisibles(true);
            teams.add(new MatchTeam(index + 1, sizes.get(index), scoreboardTeam));
        }

        List<UUID> unrequested = new ArrayList<>();
        lobby.forEach(
                (playerId, teamNumber) -> {
                    if (teamNumber > 0) {
                        assign(playerId, teams.get(teamNumber - 1));
                    } else {
                        unrequested.add(playerId);
                    }
                });
        if (game.setting(UhcGame.REGION_SEED.key()).isPresent() && settings.isOverridden(UhcGame.REGION_SEED)) {
            // Keep named participants on the same seeded starts, not just the same set of positions.
            Collections.shuffle(unrequested, new Random(settings.get(UhcGame.REGION_SEED) ^ 0x5445414dL));
        } else {
            Collections.shuffle(unrequested);
        }
        // A lobby that started before filling up spreads its players out, so no team is left empty.
        boolean partial = !layout.isFreeForAll() && lobby.size() < layout.capacity();
        for (UUID playerId : unrequested) {
            assign(
                    playerId,
                    partial
                            ? teams.stream()
                                    .filter(team -> !team.isFull())
                                    .min(java.util.Comparator.comparingInt(team -> team.members().size()))
                                    .orElseThrow()
                            : teams.stream().filter(team -> !team.isFull()).findFirst().orElseThrow());
        }
        contestingTeams = (int) teams.stream().filter(team -> !team.members().isEmpty()).count();
    }

    private void assign(UUID playerId, MatchTeam team) {
        team.add(playerId);
        teamByPlayer.put(playerId, team);
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            EloRatings.publish(player);
            ratingPlayers.put(playerId, EloRatings.player(player));
            server.getScoreboard()
                    .addPlayerToTeam(player.getScoreboardName(), team.scoreboardTeam());
            if (layout.isFreeForAll()) {
                team.scoreboardTeam()
                        .setDisplayName(
                                Component.literal(
                                        (bots.contains(playerId) ? "[BOT] " : "")
                                                + player.getScoreboardName()));
            }
        }
    }

    /**
     * The next spawn of the team: a map's spawns for the team in turn, or the team's spawn from the
     * start of the match.
     */
    private Arena.Spawn nextSpawn(MatchTeam team) {
        if (arena instanceof MapArena map) {
            List<Arena.Spawn> spawns = map.spawnsOf(team.number());
            int given = spawnsGiven.merge(team.number(), 1, Integer::sum) - 1;
            return spawns.get(given % spawns.size());
        }
        return teamSpawns.get(team.number() - 1);
    }

    /**
     * Puts a participant back into play: stops them riding, teleports them to their team's next
     * spawn, resets them to the game's mode, gives them the kit again and calls {@link
     * Minigame#onRespawn}.
     */
    public void respawn(ServerPlayer player) {
        MatchTeam team = teamByPlayer.get(player.getUUID());
        if (team == null) {
            return;
        }
        releaseCombatBalance(player);
        player.stopRiding();
        PlayerUtils.reset(player, game.playerGameMode());
        player.clearFire();
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
        Arena.Spawn spawn = nextSpawn(team);
        PlayerUtils.teleport(player, arena.level(), spawn.position(), spawn.yaw());
        player.resetFallDistance();
        lastAttacks.remove(player.getUUID());
        KitStorage.give(server, kit, List.of(player));
        game.onRespawn(this, player);
        updateCombatBalance(player);
    }

    /** The colour of team {@code number}, counting from 1; colours repeat after nine teams. */
    public static TeamColor teamColor(int number) {
        return COLORS.get((number - 1) % COLORS.size());
    }

    /** The name of team {@code number}, counting from 1: "Red", or "Red 2" once colours repeat. */
    public static String teamName(int number) {
        int cycle = (number - 1) / COLORS.size();
        return colorName(teamColor(number)) + (cycle == 0 ? "" : " " + (cycle + 1));
    }

    private static String colorName(TeamColor color) {
        StringBuilder name = new StringBuilder();
        for (String word : color.getSerializedName().split("_")) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    /**
     * Stops the player moving, attacking and mining for {@code ticks}, as in the countdown; the
     * effects are cleared when they run out or the player is reset.
     */
    public void freeze(ServerPlayer player, int ticks) {
        if (ticks <= 0) {
            return;
        }
        int duration = ticks + 40;
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 255, false, false));
        player.addEffect(
                new MobEffectInstance(MobEffects.MINING_FATIGUE, duration, 255, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 255, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, duration, 0, false, false));
    }

    void tick() {
        phaseTicks++;
        arena.prepare();
        if (!arriving.isEmpty() && arena.lobbyReady()) {
            moveArrivals();
        }
        if (!dismissals.isEmpty()) {
            for (ServerPlayer bot : online(dismissals)) {
                if (members.contains(bot.getUUID())) dismiss(bot);
            }
            dismissals.clear();
        }
        switch (phase) {
            case LOBBY -> {
                List<ServerPlayer> waiting = online(lobby.keySet());
                if (arena.lobbyReady()) waiting.forEach(arena::holdInLobby);
                if (phaseTicks % 20 == 0) {
                    waiting.forEach(PlayerUtils::heal);
                }
                tickLobby();
            }
            case COUNTDOWN -> tickCountdown();
            case ACTIVE -> tickActive();
            case ENDED -> tickEnded();
        }
        if (phase != MatchPhase.ACTIVE) {
            for (ServerPlayer player : onlineMembers()) releaseCombatBalance(player);
        }
        if (!closed && server.getTickCount() % MatchSidebar.REFRESH_TICKS == 0) {
            for (ServerPlayer player : onlineMembers()) {
                sidebar.show(player, this);
            }
        }
    }

    /** Undoes everything the match applied to the player that their snapshot does not cover. */
    private void release(ServerPlayer player) {
        player.closeContainer();
        player.stopRiding();
        sidebar.hide(player);
        game.onRelease(this, player);
        releaseCombatBalance(player);
    }

    private void updateCombatBalance(ServerPlayer player) {
        CombatBalance.updateInventory(player, isActiveParticipant(player.getUUID()) && CombatRules.classic(player));
    }

    /** Remove client-use metadata before collecting loot or restoring a snapshot. */
    private void releaseCombatBalance(ServerPlayer player) {
        CombatBalance.updateInventory(player, false);
    }

    private void tickCountdown() {
        if (preparingSpawns) {
            if (arena.lobbyReady()) alivePlayers().forEach(arena::holdInLobby);
            phaseTicks = 0;
            if (arena.lobbyReady() && arena.prepareSpawns(teams.size())) {
                preparingSpawns = false;
                placeAtSpawns();
            }
            return;
        }
        int total = settings.get(GameSetting.COUNTDOWN_SECONDS) * 20;
        if (phaseTicks >= total) {
            begin();
        } else if ((total - phaseTicks) % 20 == 0) {
            int seconds = (total - phaseTicks) / 20;
            for (ServerPlayer player : onlineMembers()) {
                player.sendSystemMessage(
                        Component.literal(seconds + "...").withStyle(ChatFormatting.GOLD));
                player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
            }
        }
    }

    private void begin() {
        phase = MatchPhase.ACTIVE;
        phaseTicks = 0;
        List<ServerPlayer> players = alivePlayers();
        for (ServerPlayer player : players) {
            PlayerUtils.reset(player, game.playerGameMode());
        }
        if (!KitStorage.give(server, kit, players)) {
            broadcast(
                    Component.literal("Kit " + KitStorage.displayName(kit) + " no longer exists; playing without it.")
                            .withStyle(ChatFormatting.RED));
        }
        game.onStart(this);
        for (ServerPlayer player : players) {
            updateCombatBalance(player);
        }
        showTitle(
                Component.literal("Fight!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal(game.displayName()),
                SoundEvents.ENDER_DRAGON_GROWL);
    }

    private void tickActive() {
        antiJanitor.tick();
        UhcCombatLogger.tick(this);
        double voidY = arena.voidY();
        for (ServerPlayer player : alivePlayers()) {
            updateCombatBalance(player);
            // A participant the game has watching for now (a Sumo player out until the round ends) may fly anywhere.
            if (player.getY() < voidY && player.level() == arena.level() && !player.isSpectator()) {
                die(player, game.voidDeathMessage(player, killerOf(player)), true);
            }
        }
        if (phase != MatchPhase.ACTIVE) {
            return;
        }
        game.tick(this);
        if (phase != MatchPhase.ACTIVE) {
            return;
        }
        List<MatchTeam> standing = standingTeams();
        if (standing.size() <= 1) {
            finish(standing);
            return;
        }
        int limit = settings.get(GameSetting.TIME_LIMIT_MINUTES) * 60 * 20;
        if (limit > 0 && phaseTicks >= limit
                && !(game instanceof io.github.brainage04.brainage_minigames.game.uhc.UhcGame uhc && uhc.controlsTimeout(this))) {
            int best = standing.stream().mapToInt(MatchTeam::score).max().orElse(0);
            finish(game instanceof io.github.brainage04.brainage_minigames.game.uhc.UhcGame uhc
                    ? uhc.timeoutWinners(this) : standing.stream().filter(team -> team.score() == best).toList());
        }
    }

    /** Teams with at least one member still alive. */
    public List<MatchTeam> standingTeams() {
        return teams.stream()
                .filter(team -> team.members().stream().anyMatch(alive::contains))
                .toList();
    }

    /** Ends the match: one team is a win, several a draw, none a match without a winner. */
    public void finish(List<MatchTeam> winningTeams) {
        if (phase == MatchPhase.ENDED || phase == MatchPhase.LOBBY) {
            return;
        }
        phase = MatchPhase.ENDED;
        phaseTicks = 0;
        winners = List.copyOf(winningTeams);
        updateMatchRatings();
        if (game instanceof io.github.brainage04.brainage_minigames.game.uhc.UhcGame) {
            io.github.brainage04.brainage_minigames.game.uhc.UhcProgression.won(this, winners);
        }
        UhcCombatLogger.end(this);

        MutableComponent result = Component.empty().append(title()).append(": ");
        if (winners.isEmpty()) {
            result.append("no winner.");
        } else if (winners.size() == 1) {
            result.append(winners.getFirst().displayName()).append(" won!");
        } else {
            result.append("draw between ").append(joinTeamNames(winners)).append(".");
        }
        server.getPlayerList().broadcastSystemMessage(result.withStyle(ChatFormatting.GOLD), false);
        showTitle(
                winners.size() == 1
                        ? Component.empty().append(winners.getFirst().displayName()).append(" won!")
                        : Component.literal(winners.isEmpty() ? "No winner" : "Draw"),
                Component.literal(game.displayName()),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);

        if (winners.size() == 1) {
            for (ServerPlayer player : online(winners.getFirst().members())) {
                if (members.contains(player.getUUID())) {
                    ModScoreboard.incrementGamesWon(server.getScoreboard(), player);
                    PlayerSnapshotStorage.addRewards(player, LootUtils.roll(player, REWARDS));
                }
            }
        }
    }

    private void updateMatchRatings() {
        if (winners.isEmpty()) return;
        List<EloRatings.Player> participants = ratingPlayers.values().stream()
                .filter(player -> !layout.isFreeForAll() || winners.size() > 1 && alive.contains(player.id()))
                .toList();
        if (layout.isFreeForAll()) {
            EloRatings.draws(server, participants);
            return;
        }
        int[] ratingTeams = participants.stream().mapToInt(player -> teamByPlayer.get(player.id()).number()).toArray();
        EloRatings.results(server, participants, ratingTeams, winners.stream().map(MatchTeam::number).toList());
    }

    private static Component joinTeamNames(List<MatchTeam> teams) {
        MutableComponent names = Component.empty();
        for (int index = 0; index < teams.size(); index++) {
            if (index > 0) {
                names.append(index == teams.size() - 1 ? " and " : ", ");
            }
            names.append(teams.get(index).displayName());
        }
        return names;
    }

    private void tickEnded() {
        if (phaseTicks % 20 == 0) {
            for (MatchTeam team : winners) {
                for (ServerPlayer player : online(team.members())) {
                    if (members.contains(player.getUUID())) {
                        player.level()
                                .addFreshEntity(
                                        new FireworkRocketEntity(
                                                player.level(),
                                                player.getX(),
                                                player.getY() + 3.0,
                                                player.getZ(),
                                                new ItemStack(Items.FIREWORK_ROCKET)));
                    }
                }
            }
        }
        if (phaseTicks >= ENDED_TICKS) {
            close();
        }
    }

    /**
     * Cancels the death of any member; an alive participant in the active phase respawns or is
     * eliminated instead, as the game decides.
     */
    void handleDeath(ServerPlayer player) {
        die(player, player.getCombatTracker().getDeathMessage(), false);
    }

    private void die(ServerPlayer player, Component deathMessage, boolean inVoid) {
        player.closeContainer();
        releaseCombatBalance(player);
        PlayerUtils.heal(player);
        UUID playerId = player.getUUID();
        if (phase != MatchPhase.ACTIVE || !alive.contains(playerId)) {
            return;
        }
        ServerPlayer killer = killerOf(player);
        lastAttacks.remove(playerId);
        if (layout.isFreeForAll() && killer != null && killer != player) {
            EloRatings.Player winner = ratingPlayers.get(killer.getUUID());
            EloRatings.Player loser = ratingPlayers.get(playerId);
            if (winner != null && loser != null) EloRatings.result(server, winner, loser, 1.0);
        }
        if (game.onDeath(this, player, killer) == Minigame.DeathResult.RESPAWN
                && phase == MatchPhase.ACTIVE
                && alive.contains(playerId)) {
            broadcast(Component.empty().append(deathMessage).withStyle(ChatFormatting.RED));
            respawn(player);
            return;
        }
        io.github.brainage04.brainage_minigames.game.uhc.UhcProgression.eliminated(this, player);
        if (!alive.remove(playerId)) {
            return;
        }
        if (bots.contains(playerId)) dismissals.add(playerId);
        antiJanitor.killed(player, killer);
        player.stopRiding();
        if (!antiJanitor.storeDrops(player) && game.dropsInventoryOnElimination()) {
            player.getInventory().dropAll();
        }
        player.removeAllEffects();
        player.setGameMode(GameType.SPECTATOR);
        if (inVoid) {
            PlayerUtils.teleport(player, arena.level(), arena.lobbyPosition(), player.getYRot());
        }
        broadcast(
                Component.empty()
                        .append(deathMessage)
                        .append(" (%d left)".formatted(alive.size()))
                        .withStyle(ChatFormatting.RED));
    }

    /**
     * The participant credited with killing {@code victim}: whoever last hurt them, directly or
     * with a projectile, within {@link #KILL_CREDIT_TICKS}, or else the combat tracker's killer.
     */
    private @Nullable ServerPlayer killerOf(ServerPlayer victim) {
        LastAttack last = lastAttacks.get(victim.getUUID());
        if (last != null && server.getTickCount() - last.tick() <= KILL_CREDIT_TICKS) {
            ServerPlayer attacker = server.getPlayerList().getPlayer(last.attacker());
            if (attacker != null && teamByPlayer.containsKey(attacker.getUUID())) {
                return attacker;
            }
        }
        if (victim.getKillCredit() instanceof ServerPlayer credited
                && credited != victim
                && teamByPlayer.containsKey(credited.getUUID())) {
            return credited;
        }
        return null;
    }

    /** Read-only damage eligibility shared by permission probes and actual damage. */
    boolean canDamage(ServerPlayer victim, DamageSource source) {
        if (phase != MatchPhase.ACTIVE || !alive.contains(victim.getUUID())) {
            return false;
        }
        ServerPlayer attacker = source.getEntity() instanceof ServerPlayer player ? player : null;
        if (attacker != null && !alive.contains(attacker.getUUID())) {
            return false;
        }
        if (attacker != null && attacker != victim
                && teamByPlayer.get(attacker.getUUID()) == teamByPlayer.get(victim.getUUID())) {
            return false;
        }
        if (attacker != null && !antiJanitor.allows(victim, attacker)) {
            return false;
        }
        if (!game.allowDamage(this, victim, source)) {
            return false;
        }
        return true;
    }

    /** Damage rules shared by every game, then the game's own rules. */
    boolean allowDamage(ServerPlayer victim, DamageSource source) {
        if (!canDamage(victim, source)) return false;
        if (source.getEntity() == null && touchesTeammateHazard(victim, source)) {
            // The lava or fire already set them alight; a teammate's must not keep them burning.
            victim.clearFire();
            return false;
        }
        ServerPlayer attacker = source.getEntity() instanceof ServerPlayer player ? player : null;
        if (attacker != null && attacker != victim) {
            lastAttacks.put(
                    victim.getUUID(), new LastAttack(attacker.getUUID(), server.getTickCount()));
        }
        return true;
    }

    /**
     * Whether lava or fire damage comes from a teammate's: fire they lit that the victim stands in,
     * or lava the victim is in within {@link #LAVA_FLOW_REACH} blocks of a lava source they poured.
     * Lava and fire that spread from them further than that count as the world's.
     */
    private boolean touchesTeammateHazard(ServerPlayer victim, DamageSource source) {
        boolean lava = source.is(DamageTypes.LAVA);
        if (!lava && !source.is(DamageTypes.IN_FIRE) && !source.is(DamageTypes.ON_FIRE)) return false;
        MatchTeam team = teamByPlayer.get(victim.getUUID());
        Long2ObjectOpenHashMap<Hazard> placed = hazards.get(victim.level());
        if (team == null || placed == null || placed.isEmpty()) return false;
        net.minecraft.world.phys.AABB body = victim.getBoundingBox();
        BlockPos feet = victim.blockPosition();
        var entries = placed.long2ObjectEntrySet().fastIterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            Hazard hazard = entry.getValue();
            if (hazard.lava() != lava) continue;
            BlockPos pos = BlockPos.of(entry.getLongKey());
            boolean near = lava
                    ? feet.distChessboard(pos) <= LAVA_FLOW_REACH
                    : body.intersects(new net.minecraft.world.phys.AABB(pos).inflate(1.0E-3));
            if (!near) continue;
            // Whatever is there now is no longer theirs once their lava or fire is gone.
            if (hazardKind(victim.level().getBlockState(pos)) != hazard.kind()) {
                entries.remove();
                continue;
            }
            if (!hazard.owner().equals(victim.getUUID()) && teamByPlayer.get(hazard.owner()) == team) {
                return true;
            }
        }
        return false;
    }

    /** Lava sources and fire, the blocks whose damage is credited to whoever placed them. */
    private enum HazardKind { NONE, LAVA, FIRE }

    /** Who placed a lava source or lit a fire, and which it was. */
    private record Hazard(UUID owner, HazardKind kind) {
        boolean lava() {
            return kind == HazardKind.LAVA;
        }
    }

    private static HazardKind hazardKind(BlockState state) {
        if (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) && state.getFluidState().isSource()) {
            return HazardKind.LAVA;
        }
        return state.is(net.minecraft.tags.BlockTags.FIRE) ? HazardKind.FIRE : HazardKind.NONE;
    }

    /** Positive accepted damage (including absorption), never a permission probe or zero hit. */
    public void damaged(ServerPlayer victim, ServerPlayer attacker) {
        antiJanitor.damaged(victim, attacker);
        io.github.brainage04.brainage_minigames.game.uhc.UhcProgression.damaged(this, victim, attacker);
    }

    /** Block rules shared by every game: only alive participants of the active match may play. */
    boolean allowBreak(ServerPlayer player, BlockPos pos, BlockState state) {
        return isActiveParticipant(player.getUUID()) && game.allowBreak(this, player, pos, state);
    }

    boolean allowPlace(ServerPlayer player, BlockPos pos, BlockState state) {
        return isActiveParticipant(player.getUUID()) && game.allowPlace(this, player, pos, state);
    }

    void blockPlaced(ServerPlayer player, BlockPos pos) {
        if (isActiveParticipant(player.getUUID())) {
            placedBlocks.add(pos.asLong());
            ContainerProtection.placed(this, player, pos);
            hazardPlaced(player, pos);
        }
    }

    /** Remembers who placed a lava source or lit a fire at {@code pos}, if one is there. */
    void hazardPlaced(ServerPlayer player, BlockPos pos) {
        HazardKind kind = hazardKind(player.level().getBlockState(pos));
        if (isActiveParticipant(player.getUUID()) && kind != HazardKind.NONE) {
            hazards.computeIfAbsent(player.level(), level -> new Long2ObjectOpenHashMap<>())
                    .put(pos.asLong(), new Hazard(player.getUUID(), kind));
        }
    }

    void blockBroken(BlockPos pos) {
        placedBlocks.remove(pos.asLong());
    }

    InteractionResult useItem(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (!isActiveParticipant(player.getUUID())) {
            return player.isSpectator() ? InteractionResult.PASS : InteractionResult.FAIL;
        }
        return game.onUseItem(this, player, hand, stack);
    }

    void projectileHitBlock(Projectile projectile, BlockHitResult hit) {
        game.onProjectileHitBlock(this, projectile, hit);
    }

    /** Announces that an operator stopped the match, then restores everyone. */
    void stop() {
        if (phase != MatchPhase.ENDED) {
            server.getPlayerList()
                    .broadcastSystemMessage(
                            Component.empty()
                                    .append(title())
                                    .append(" was stopped.")
                                    .withStyle(ChatFormatting.RED),
                            false);
        }
        close();
    }

    void close() {
        if (closed) {
            return;
        }
        closed = true;
        UhcCombatLogger.end(this);
        antiJanitor.clear();
        ContainerProtection.clear(this);
        List<ServerPlayer> leavingBots = online(bots);
        for (ServerPlayer player : onlineMembers()) {
            release(player);
            PlayerSnapshotStorage.restore(player);
        }
        members.clear();
        lobby.clear();
        alive.clear();
        for (ServerPlayer bot : leavingBots) MatchBots.remove(bot);
        ServerScoreboard scoreboard = server.getScoreboard();
        for (MatchTeam team : teams) {
            if (scoreboard.getPlayerTeam(team.scoreboardTeam().getName()) != null) {
                scoreboard.removePlayerTeam(team.scoreboardTeam());
            }
        }
        game.onClose(this);
        arena.closeGradually();
        placedBlocks.clear();
        hazards.clear();
        lastAttacks.clear();
    }

    /**
     * Sends {@code sender}'s chat message to their team alone while the teams play (the countdown
     * and the active phase), when the team has other members; a message starting with {@link
     * #SHOUT_PREFIX} still goes to everyone. Returns whether the message was sent here.
     */
    boolean teamChat(ServerPlayer sender, PlayerChatMessage message) {
        if (phase != MatchPhase.COUNTDOWN && phase != MatchPhase.ACTIVE
                || message.signedContent().startsWith(SHOUT_PREFIX)) {
            return false;
        }
        MatchTeam team = teamByPlayer.get(sender.getUUID());
        if (team == null || team.members().size() < 2) return false;
        ChatType.Bound incoming = ChatType.bind(ChatType.TEAM_MSG_COMMAND_INCOMING, sender)
                .withTargetName(team.displayName());
        ChatType.Bound outgoing = ChatType.bind(ChatType.TEAM_MSG_COMMAND_OUTGOING, sender)
                .withTargetName(team.displayName());
        OutgoingChatMessage outgoingMessage = OutgoingChatMessage.create(message);
        for (ServerPlayer member : online(team.members())) {
            member.sendChatMessage(outgoingMessage, sender.shouldFilterMessageTo(member),
                    member == sender ? outgoing : incoming);
        }
        server.logChatMessage(message.decoratedContent(), incoming, "Team");
        return true;
    }

    public void broadcast(Component message) {
        for (ServerPlayer player : onlineMembers()) {
            player.sendSystemMessage(message);
        }
    }

    public void broadcastActionBar(Component message) {
        for (ServerPlayer player : onlineMembers()) {
            player.sendSystemMessage(message, true);
        }
    }

    private void showTitle(Component title, Component subtitle, SoundEvent sound) {
        for (ServerPlayer player : onlineMembers()) {
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.playSound(sound, 1.0F, 1.0F);
        }
    }

    /** A one-line summary for {@code /minigames list} and {@code /minigames status}. */
    public Component describe() {
        MutableComponent line =
                Component.empty()
                        .append(title())
                        .append(" - ")
                        .append(phase.name().toLowerCase(Locale.ROOT));
        mapName().ifPresent(name -> line.append(", map " + name));
        ownerName().ifPresent(name -> line.append(", opened by " + name));
        switch (phase) {
            case LOBBY -> {
                line.append(
                        layout.isFreeForAll()
                                ? ", %d joined".formatted(lobby.size())
                                : ", %d/%d joined".formatted(lobby.size(), layout.capacity()));
                if (reservedBots() > 0) line.append(", %d bot slots".formatted(reservedBots()));
                autoStartSecondsLeft()
                        .ifPresent(seconds -> line.append(", starts in %ds".formatted(seconds)));
            }
            case COUNTDOWN, ACTIVE -> {
                line.append(", %d:%02d".formatted(activeTicks() / 1200, activeTicks() / 20 % 60));
                for (MatchTeam team : teams) {
                    long remaining = team.members().stream().filter(alive::contains).count();
                    line.append(", ")
                            .append(team.displayName())
                            .append(" %d alive".formatted(remaining));
                    if (team.score() > 0) {
                        line.append(" %d points".formatted(team.score()));
                    }
                }
            }
            case ENDED -> {}
        }
        return line;
    }

    private List<ServerPlayer> online(Collection<UUID> playerIds) {
        List<ServerPlayer> players = new ArrayList<>(playerIds.size());
        for (UUID playerId : playerIds) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }
}
