package io.github.brainage04.brainage_minigames.game;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.scoreboard.ModScoreboard;
import io.github.brainage04.brainage_minigames.storage.KitStorage;
import io.github.brainage04.brainage_minigames.storage.PlayerSnapshotStorage;
import io.github.brainage04.brainage_minigames.util.LootUtils;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
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
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
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

    /** A player who hurt someone this recently gets the kill when they die. */
    private static final int KILL_CREDIT_TICKS = 10 * 20;

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
                    TeamColor.DARK_RED,
                    TeamColor.DARK_BLUE,
                    TeamColor.DARK_GREEN,
                    TeamColor.DARK_AQUA,
                    TeamColor.DARK_PURPLE,
                    TeamColor.GRAY,
                    TeamColor.DARK_GRAY,
                    TeamColor.BLACK);

    private final int id;
    private final MinecraftServer server;
    private final Minigame game;
    private final TeamLayout layout;
    private final GameSettings settings;
    private final Identifier kit;
    private final Arena arena;

    /** Everyone allowed to join a private match; empty for a public one. */
    private final Set<UUID> invited;

    /** Lobby players and the team number they asked for (0 for any). */
    private final Map<UUID, Integer> lobby = new LinkedHashMap<>();

    /**
     * Everyone whose state this match saved and must restore: lobby players, participants and
     * watchers.
     */
    private final Set<UUID> members = new LinkedHashSet<>();

    private final Set<UUID> alive = new LinkedHashSet<>();
    private final List<MatchTeam> teams = new ArrayList<>();
    private final Map<UUID, MatchTeam> teamByPlayer = new HashMap<>();

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

    /** How many times each team number has been given a spawn, to rotate through its spawns. */
    private final Map<Integer, Integer> spawnsGiven = new HashMap<>();

    /**
     * The arena's spawn for each team, chosen once when the match starts; arenas other than maps
     * may pick them at random.
     */
    private List<Arena.Spawn> teamSpawns = List.of();

    private record LastAttack(UUID attacker, int tick) {}

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
            map.onReset(placedBlocks::clear);
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

    boolean isWaiting(UUID playerId) {
        return lobby.containsKey(playerId);
    }

    /** The team a lobby player asked for, or 0 for any. */
    int requestedTeam(UUID playerId) {
        return lobby.getOrDefault(playerId, 0);
    }

    String nameOf(UUID playerId) {
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

    public List<MatchTeam> winners() {
        return winners;
    }

    /** Whether anyone may join: the match is public and still in its lobby. */
    public boolean isOpenLobby() {
        return phase == MatchPhase.LOBBY && invited.isEmpty();
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean involves(UUID playerId) {
        return members.contains(playerId);
    }

    public boolean isAlive(UUID playerId) {
        return alive.contains(playerId);
    }

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
        return Component.literal("Match #%d (%s %s)".formatted(id, game.displayName(), layout));
    }

    void join(ServerPlayer player, int teamNumber) throws MatchException {
        if (phase != MatchPhase.LOBBY) {
            throw new MatchException("Match #" + id + " has already started.");
        }
        if (!invited.isEmpty() && !invited.contains(player.getUUID())) {
            throw new MatchException("Match #" + id + " is a private duel.");
        }
        if (lobby.size() >= layout.capacity()) {
            throw new MatchException("Match #" + id + " is full.");
        }
        if (layout.isFreeForAll() && lobby.size() >= arena.maxTeams()) {
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
            long requests = lobby.values().stream().filter(number -> number == teamNumber).count();
            if (requests >= layout.teamSizes().get(teamNumber - 1)) {
                throw new MatchException("Team " + teamNumber + " is full.");
            }
        }
        enter(player, GameType.ADVENTURE);
        lobby.put(player.getUUID(), teamNumber);
        broadcast(
                Component.literal(
                                "%s joined (%s)."
                                        .formatted(
                                                player.getScoreboardName(),
                                                layout.isFreeForAll()
                                                        ? lobby.size() + " players"
                                                        : lobby.size() + "/" + layout.capacity()))
                        .withStyle(ChatFormatting.GREEN));
        if (!layout.isFreeForAll() && lobby.size() == layout.capacity()) {
            start();
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

    private void enter(ServerPlayer player, GameType gameType) throws MatchException {
        if (!PlayerSnapshotStorage.save(player)) {
            throw new MatchException(
                    "Your current state could not be saved, so you were not moved.");
        }
        PlayerUtils.reset(player, gameType);
        if (PlayerUtils.teleport(player, arena.level(), arena.lobbyPosition(), 0.0F) == null) {
            PlayerSnapshotStorage.restore(player);
            throw new MatchException("The arena could not be reached.");
        }
        members.add(player.getUUID());
        names.put(player.getUUID(), player.getScoreboardName());
    }

    void leave(ServerPlayer player) {
        UUID playerId = player.getUUID();
        members.remove(playerId);
        lobby.remove(playerId);
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
        UUID playerId = player.getUUID();
        if (!members.remove(playerId)) {
            return;
        }
        lobby.remove(playerId);
        sidebar.forget(playerId);
        game.onRelease(this, player);
        if (alive.remove(playerId)) {
            broadcast(
                    Component.literal(
                                    player.getScoreboardName()
                                            + " disconnected and was eliminated.")
                            .withStyle(ChatFormatting.RED));
        }
    }

    /** Moves the lobby into the countdown: builds the teams and places them at their spawns. */
    public void start() throws MatchException {
        if (phase != MatchPhase.LOBBY) {
            throw new MatchException("Match #" + id + " has already started.");
        }
        if (lobby.size() < layout.requiredPlayers()) {
            throw new MatchException(
                    "Match #%d needs %d players to start; %d joined."
                            .formatted(id, layout.requiredPlayers(), lobby.size()));
        }
        createTeams();
        if (!(arena instanceof MapArena)) {
            teamSpawns = arena.spawns(teams.size());
        }

        int countdownTicks = settings.get(GameSetting.COUNTDOWN_SECONDS) * 20;
        for (MatchTeam team : teams) {
            for (ServerPlayer player : online(team.members())) {
                Arena.Spawn spawn = nextSpawn(team);
                PlayerUtils.teleport(player, arena.level(), spawn.position(), spawn.yaw());
                freeze(player, countdownTicks);
            }
        }
        alive.addAll(lobby.keySet());
        lobby.clear();
        phase = MatchPhase.COUNTDOWN;
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
            TeamColor color = COLORS.get(index % COLORS.size());
            int cycle = index / COLORS.size();
            scoreboardTeam.setColor(Optional.of(color));
            scoreboardTeam.setDisplayName(
                    Component.literal(colorName(color) + (cycle == 0 ? "" : " " + (cycle + 1))));
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
        Collections.shuffle(unrequested);
        for (UUID playerId : unrequested) {
            assign(
                    playerId,
                    teams.stream().filter(team -> !team.isFull()).findFirst().orElseThrow());
        }
    }

    private void assign(UUID playerId, MatchTeam team) {
        team.add(playerId);
        teamByPlayer.put(playerId, team);
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            server.getScoreboard()
                    .addPlayerToTeam(player.getScoreboardName(), team.scoreboardTeam());
            if (layout.isFreeForAll()) {
                team.scoreboardTeam().setDisplayName(Component.literal(player.getScoreboardName()));
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
        switch (phase) {
            case LOBBY -> {
                List<ServerPlayer> waiting = online(lobby.keySet());
                waiting.forEach(arena::holdInLobby);
                if (phaseTicks % 20 == 0) {
                    waiting.forEach(PlayerUtils::heal);
                }
            }
            case COUNTDOWN -> tickCountdown();
            case ACTIVE -> tickActive();
            case ENDED -> tickEnded();
        }
        if (!closed && server.getTickCount() % MatchSidebar.REFRESH_TICKS == 0) {
            for (ServerPlayer player : onlineMembers()) {
                sidebar.show(player, this);
            }
        }
    }

    /** Undoes everything the match applied to the player that their snapshot does not cover. */
    private void release(ServerPlayer player) {
        player.stopRiding();
        sidebar.hide(player);
        game.onRelease(this, player);
    }

    private void tickCountdown() {
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
                    Component.literal("Kit " + kit + " no longer exists; playing without it.")
                            .withStyle(ChatFormatting.RED));
        }
        game.onStart(this);
        showTitle(
                Component.literal("Fight!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal(game.displayName()),
                SoundEvents.ENDER_DRAGON_GROWL);
    }

    private void tickActive() {
        double voidY = arena.voidY();
        for (ServerPlayer player : alivePlayers()) {
            if (player.getY() < voidY && player.level() == arena.level()) {
                ServerPlayer killer = killerOf(player);
                MutableComponent message = Component.empty().append(player.getDisplayName());
                if (killer == null) {
                    message.append(" fell into the void");
                } else {
                    message.append(" was knocked into the void by ")
                            .append(killer.getDisplayName());
                }
                die(player, message, true);
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
        if (limit > 0 && phaseTicks >= limit) {
            int best = standing.stream().mapToInt(MatchTeam::score).max().orElse(0);
            finish(standing.stream().filter(team -> team.score() == best).toList());
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
        PlayerUtils.heal(player);
        UUID playerId = player.getUUID();
        if (phase != MatchPhase.ACTIVE || !alive.contains(playerId)) {
            return;
        }
        ServerPlayer killer = killerOf(player);
        lastAttacks.remove(playerId);
        if (game.onDeath(this, player, killer) == Minigame.DeathResult.RESPAWN
                && phase == MatchPhase.ACTIVE
                && alive.contains(playerId)) {
            broadcast(Component.empty().append(deathMessage).withStyle(ChatFormatting.RED));
            respawn(player);
            return;
        }
        if (!alive.remove(playerId)) {
            return;
        }
        player.stopRiding();
        if (game.dropsInventoryOnElimination()) {
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

    /** Damage rules shared by every game, then the game's own rules. */
    boolean allowDamage(ServerPlayer victim, DamageSource source) {
        if (phase != MatchPhase.ACTIVE || !alive.contains(victim.getUUID())) {
            return false;
        }
        ServerPlayer attacker = source.getEntity() instanceof ServerPlayer player ? player : null;
        if (attacker != null && !alive.contains(attacker.getUUID())) {
            return false;
        }
        if (!game.allowDamage(this, victim, source)) {
            return false;
        }
        if (attacker != null && attacker != victim) {
            lastAttacks.put(
                    victim.getUUID(), new LastAttack(attacker.getUUID(), server.getTickCount()));
        }
        return true;
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
        for (ServerPlayer player : onlineMembers()) {
            release(player);
            PlayerSnapshotStorage.restore(player);
        }
        members.clear();
        lobby.clear();
        alive.clear();
        ServerScoreboard scoreboard = server.getScoreboard();
        for (MatchTeam team : teams) {
            if (scoreboard.getPlayerTeam(team.scoreboardTeam().getName()) != null) {
                scoreboard.removePlayerTeam(team.scoreboardTeam());
            }
        }
        game.onClose(this);
        arena.close();
        placedBlocks.clear();
        lastAttacks.clear();
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
        switch (phase) {
            case LOBBY ->
                    line.append(
                            layout.isFreeForAll()
                                    ? ", %d joined".formatted(lobby.size())
                                    : ", %d/%d joined".formatted(lobby.size(), layout.capacity()));
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
