package io.github.brainage04.brainage_minigames.game.race;

import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchSidebar;
import io.github.brainage04.brainage_minigames.game.MatchTeam;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import org.jspecify.annotations.Nullable;

/**
 * A race on a {@link MapArena}: everyone starts together, passes the {@code checkpoint_<n>} regions
 * in order and then the {@code finish} region, as many times as the race has laps; the first to
 * complete the last lap wins for their team. Nobody is eliminated: a fall, a {@code fail} region or
 * the void puts the player back at their last checkpoint. Players cannot hurt or push each other.
 * When the time limit runs out, whoever got furthest wins, and a tie is a draw.
 */
public abstract class RaceGame implements Minigame {
    /** Ticks between action bar updates. */
    private static final int ACTION_BAR_TICKS = 4;

    private final String id;
    private final String displayName;
    private final Identifier kit;
    private final List<GameSetting> settings;
    private final Map<Match, Race> races = new HashMap<>();

    protected RaceGame(
            String id, String displayName, Identifier kit, List<GameSetting> extraSettings) {
        this.id = id;
        this.displayName = displayName;
        this.kit = kit;
        List<GameSetting> all = new ArrayList<>(GameSetting.common(5, 10, true));
        all.addAll(extraSettings);
        this.settings = List.copyOf(all);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public List<GameSetting> settings() {
        return settings;
    }

    @Override
    public Identifier defaultKit() {
        return kit;
    }

    @Override
    public GameType playerGameMode() {
        return GameType.ADVENTURE;
    }

    /** Laps a racer must complete to win. */
    protected abstract int laps(GameSettings settings);

    /**
     * How far a racer may drop below the place they were last put back at before they are put back
     * there again; infinite when only {@code fail} regions and the void count.
     */
    protected abstract double fallMargin();

    /** Puts a racer at their last checkpoint (or the start), ready to go on. */
    protected abstract void returnToCheckpoint(Match match, ServerPlayer player, Racer racer);

    /** Rejects maps that cannot hold the layout; the course itself is already checked. */
    protected void checkCapacity(MapArena arena, TeamLayout layout) throws MatchException {}

    /** Prepares a racer at the start once the race is active. */
    protected void startRacer(Match match, ServerPlayer player, Racer racer, int slot) {}

    /**
     * Called every active tick before the racer's progress is checked; returns true when the racer
     * was put back and has nothing more to check this tick.
     */
    protected boolean enforce(Match match, ServerPlayer player, Racer racer) {
        return false;
    }

    /**
     * Removes whatever the racer still has in the world; called when they leave or the race ends.
     */
    protected void cleanUp(Racer racer) {}

    @Override
    public Arena openArena(MinecraftServer server, GameSettings values) throws MatchException {
        return openArena(server, values, TeamLayout.FREE_FOR_ALL);
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings values, TeamLayout layout)
            throws MatchException {
        int teams = layout.isFreeForAll() ? 2 : layout.teamSizes().size();
        MapArena arena = MapArena.openRandom(server, id, teams);
        try {
            Course.of(arena);
            checkCapacity(arena, layout);
        } catch (MatchException exception) {
            arena.close();
            throw exception;
        }
        return arena;
    }

    @Override
    public void onStart(Match match) {
        if (!(match.arena() instanceof MapArena arena)) {
            match.broadcast(
                    Component.literal(displayName + " needs a map; ending the match.")
                            .withStyle(ChatFormatting.RED));
            match.finish(List.of());
            return;
        }
        Course course;
        try {
            course = Course.of(arena);
        } catch (MatchException exception) {
            match.broadcast(
                    Component.literal(exception.getMessage()).withStyle(ChatFormatting.RED));
            match.finish(List.of());
            return;
        }
        Race race = new Race(course, laps(match.settings()));
        races.put(match, race);
        int slot = 0;
        for (MatchTeam team : match.teams()) {
            // Racers pass through each other; boats still bump.
            team.scoreboardTeam().setCollisionRule(Team.CollisionRule.NEVER);
            for (UUID member : team.members()) {
                ServerPlayer player = match.server().getPlayerList().getPlayer(member);
                if (player == null || !match.isAlive(member)) {
                    continue;
                }
                slot++;
                Racer racer = new Racer(player.position(), player.getYRot());
                race.racers.put(member, racer);
                startRacer(match, player, racer, slot);
                racer.last = player.getRootVehicle().position();
            }
        }
    }

    @Override
    public void tick(Match match) {
        Race race = races.get(match);
        if (race == null) {
            return;
        }
        for (ServerPlayer player : match.alivePlayers()) {
            Racer racer = race.racers.get(player.getUUID());
            if (racer == null) {
                continue;
            }
            if (enforce(match, player, racer)) {
                continue;
            }
            Vec3 before = racer.last;
            Vec3 now = player.getRootVehicle().position();
            racer.last = now;
            if (race.course.fails().stream().anyMatch(fail -> fail.contains(now))
                    || now.y() < racer.respawn.y() - fallMargin()) {
                sendBack(match, player, racer);
                continue;
            }
            Gate target = race.target(racer);
            if (crossed(target.box(), before, now) && advance(match, race, player, racer)) {
                return;
            }
        }
        if (match.activeTicks() % ACTION_BAR_TICKS == 0) {
            for (ServerPlayer player : match.alivePlayers()) {
                Racer racer = race.racers.get(player.getUUID());
                if (racer != null) {
                    player.sendSystemMessage(status(match, race, racer), true);
                }
            }
        }
    }

    /** Whether the move from {@code before} to {@code now} ends in or passes through the box. */
    private static boolean crossed(AABB box, Vec3 before, Vec3 now) {
        return box.contains(now) || box.clip(before, now).isPresent();
    }

    /** Records a passed gate; returns true when it won the race. */
    private boolean advance(Match match, Race race, ServerPlayer player, Racer racer) {
        int checkpoints = race.course.checkpoints().size();
        Component time =
                Component.literal(" (" + formatTime(match.activeTicks()) + ")")
                        .withStyle(ChatFormatting.GRAY);
        if (racer.next < checkpoints) {
            Gate gate = race.course.checkpoints().get(racer.next);
            racer.next++;
            racer.respawn = gate.respawn();
            racer.yaw = gate.yaw();
            player.sendSystemMessage(
                    Component.literal("Checkpoint %d/%d".formatted(racer.next, checkpoints))
                            .withStyle(ChatFormatting.GREEN)
                            .append(time));
            player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
        } else {
            racer.lap++;
            racer.next = 0;
            Gate finish = race.course.finish();
            racer.respawn = finish.respawn();
            racer.yaw = finish.yaw();
            if (racer.lap >= race.laps) {
                racer.finished = true;
                updateScore(match, race, player, racer);
                match.broadcast(
                        Component.empty()
                                .append(player.getDisplayName())
                                .append(" finished in " + formatTime(match.activeTicks()) + "!")
                                .withStyle(ChatFormatting.GOLD));
                match.teamOf(player.getUUID()).ifPresent(team -> match.finish(List.of(team)));
                return true;
            }
            player.sendSystemMessage(
                    Component.literal("Lap %d/%d".formatted(racer.lap + 1, race.laps))
                            .withStyle(ChatFormatting.AQUA)
                            .append(time));
            player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);
        }
        updateScore(match, race, player, racer);
        return false;
    }

    /** A team's score is the furthest any member got, so the time limit rewards the leader. */
    private static void updateScore(Match match, Race race, ServerPlayer player, Racer racer) {
        int progress = race.progress(racer);
        match.teamOf(player.getUUID())
                .filter(team -> progress > team.score())
                .ifPresent(team -> team.addScore(progress - team.score()));
    }

    private void sendBack(Match match, ServerPlayer player, Racer racer) {
        returnToCheckpoint(match, player, racer);
        racer.last = player.getRootVehicle().position();
    }

    /** Teleports the player (not riding anything) to the racer's last checkpoint. */
    protected static void teleportToCheckpoint(Match match, ServerPlayer player, Racer racer) {
        player.stopRiding();
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        PlayerUtils.teleport(player, match.arena().level(), racer.respawn, racer.yaw);
        player.resetFallDistance();
    }

    @Override
    public DeathResult onDeath(Match match, ServerPlayer victim, @Nullable ServerPlayer killer) {
        return DeathResult.RESPAWN;
    }

    @Override
    public void onRespawn(Match match, ServerPlayer player) {
        Race race = races.get(match);
        Racer racer = race == null ? null : race.racers.get(player.getUUID());
        if (racer != null) {
            sendBack(match, player, racer);
        }
    }

    @Override
    public boolean allowDamage(Match match, ServerPlayer victim, DamageSource source) {
        return false;
    }

    @Override
    public void onRelease(Match match, ServerPlayer player) {
        Race race = races.get(match);
        Racer racer = race == null ? null : race.racers.get(player.getUUID());
        if (racer != null) {
            cleanUp(racer);
        }
    }

    @Override
    public void onClose(Match match) {
        Race race = races.remove(match);
        if (race != null) {
            race.racers.values().forEach(this::cleanUp);
        }
    }

    @Override
    public void addSidebarLines(Match match, List<Component> lines) {
        Race race = races.get(match);
        if (race != null) {
            lines.add(
                    MatchSidebar.label(
                            "Checkpoints: ", String.valueOf(race.course.checkpoints().size())));
            if (race.laps > 1) {
                lines.add(MatchSidebar.label("Laps: ", String.valueOf(race.laps)));
            }
        } else if (laps(match.settings()) > 1) {
            lines.add(MatchSidebar.label("Laps: ", String.valueOf(laps(match.settings()))));
        }
    }

    @Override
    public Component sidebarTeamSuffix(Match match, MatchTeam team) {
        Race race = races.get(match);
        if (race == null) {
            return Component.empty();
        }
        Racer best = null;
        for (UUID member : team.members()) {
            Racer racer = race.racers.get(member);
            if (racer != null && (best == null || race.progress(racer) > race.progress(best))) {
                best = racer;
            }
        }
        if (best == null) {
            return Component.empty();
        }
        String text =
                best.finished
                        ? " finished"
                        : race.laps > 1
                                ? " lap %d/%d, %d/%d"
                                        .formatted(
                                                best.lap + 1,
                                                race.laps,
                                                best.next,
                                                race.course.checkpoints().size())
                                : " %d/%d".formatted(best.next, race.course.checkpoints().size());
        return Component.literal(text).withStyle(ChatFormatting.YELLOW);
    }

    private static Component status(Match match, Race race, Racer racer) {
        StringBuilder text = new StringBuilder();
        if (race.laps > 1) {
            text.append("Lap %d/%d   ".formatted(Math.min(racer.lap + 1, race.laps), race.laps));
        }
        text.append(
                "Checkpoint %d/%d   %s"
                        .formatted(
                                racer.next,
                                race.course.checkpoints().size(),
                                formatTime(match.activeTicks())));
        return Component.literal(text.toString()).withStyle(ChatFormatting.YELLOW);
    }

    static String formatTime(int ticks) {
        int tenths = ticks / 2;
        return "%d:%02d.%d".formatted(tenths / 600, tenths / 10 % 60, tenths % 10);
    }

    /** The racer's progress in the match, for commands, tests and other games' use. */
    public Optional<Progress> progress(Match match, UUID playerId) {
        Race race = races.get(match);
        Racer racer = race == null ? null : race.racers.get(playerId);
        return racer == null
                ? Optional.empty()
                : Optional.of(
                        new Progress(
                                racer.lap,
                                racer.next,
                                race.course.checkpoints().size(),
                                racer.respawn));
    }

    /**
     * Where a racer is: laps completed, checkpoints passed in the current lap, the course's
     * checkpoint count and where they would be put back.
     */
    public record Progress(int laps, int checkpoints, int checkpointCount, Vec3 respawn) {}

    /** A checkpoint or the finish: the region to pass and where racers are put back. */
    public record Gate(AABB box, Vec3 respawn, float yaw) {}

    /**
     * A map's course: {@code checkpoint_1} to {@code checkpoint_<n>} in order, the finish and any
     * {@code fail} regions. A point with a gate's name sets where and which way racers are put
     * back; otherwise they stand at the bottom centre of its region, facing the next gate.
     */
    public record Course(List<Gate> checkpoints, Gate finish, List<MapArena.Region> fails) {
        public static Course of(MapArena arena) throws MatchException {
            Map<Integer, MapArena.Region> numbered = new LinkedHashMap<>();
            for (MapArena.Region region : arena.regions("checkpoint_")) {
                String suffix = region.name().substring("checkpoint_".length());
                int number;
                try {
                    number = Integer.parseInt(suffix);
                } catch (NumberFormatException exception) {
                    throw new MatchException(
                            "Map %s has a region '%s'; checkpoints are numbered."
                                    .formatted(arena.map(), region.name()));
                }
                if (numbered.put(number, region) != null) {
                    throw new MatchException(
                            "Map %s has two %s regions.".formatted(arena.map(), region.name()));
                }
            }
            List<MapArena.Region> ordered = new ArrayList<>();
            for (int number = 1; number <= numbered.size(); number++) {
                MapArena.Region region = numbered.get(number);
                if (region == null) {
                    throw new MatchException(
                            "Map %s has %d checkpoints but no checkpoint_%d."
                                    .formatted(arena.map(), numbered.size(), number));
                }
                ordered.add(region);
            }
            MapArena.Region finish =
                    arena.region("finish")
                            .orElseThrow(
                                    () ->
                                            new MatchException(
                                                    "Map %s has no finish region."
                                                            .formatted(arena.map())));
            ordered.add(finish);

            List<Gate> gates = new ArrayList<>(ordered.size());
            for (int index = 0; index < ordered.size(); index++) {
                MapArena.Region region = ordered.get(index);
                AABB box = region.box();
                Vec3 bottom = new Vec3(box.getCenter().x(), box.minY, box.getCenter().z());
                Optional<MapArena.Point> point = arena.point(region.name());
                if (point.isPresent()) {
                    gates.add(new Gate(box, point.get().position(), point.get().yaw()));
                } else {
                    Vec3 next = ordered.get((index + 1) % ordered.size()).box().getCenter();
                    float yaw =
                            (float)
                                    Math.toDegrees(
                                            Math.atan2(
                                                    -(next.x() - bottom.x()),
                                                    next.z() - bottom.z()));
                    gates.add(new Gate(box, bottom, yaw));
                }
            }
            return new Course(
                    List.copyOf(gates.subList(0, gates.size() - 1)),
                    gates.getLast(),
                    arena.regions("fail"));
        }
    }

    /** One match's race. */
    private static final class Race {
        private final Course course;
        private final int laps;
        private final Map<UUID, Racer> racers = new HashMap<>();

        private Race(Course course, int laps) {
            this.course = course;
            this.laps = laps;
        }

        Gate target(Racer racer) {
            return racer.next < course.checkpoints().size()
                    ? course.checkpoints().get(racer.next)
                    : course.finish();
        }

        /** Gates passed in total; higher is further. */
        int progress(Racer racer) {
            return racer.lap * (course.checkpoints().size() + 1) + racer.next;
        }
    }

    /** One player's run. */
    protected static final class Racer {
        /** Laps completed. */
        int lap;

        /** Checkpoints passed in the current lap; the finish is next once all are. */
        int next;

        boolean finished;

        /** Where the racer is put back, and which way they face there. */
        Vec3 respawn;

        float yaw;

        /** Where the racer (or their vehicle) was on the previous tick. */
        Vec3 last;

        /** The boat the racer is in, for games that give one. */
        @Nullable Entity vehicle;

        Racer(Vec3 start, float yaw) {
            this.respawn = start;
            this.yaw = yaw;
            this.last = start;
        }

        public Vec3 respawn() {
            return respawn;
        }

        public float yaw() {
            return yaw;
        }
    }
}
