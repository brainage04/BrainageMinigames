package io.github.brainage04.brainage_minigames.game.quake;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Quakecraft: every player has a railgun that kills with one instant beam and a dash; killed
 * players respawn at once at a respawn point away from their opponents, and the first team to the
 * kill target wins.
 */
public final class QuakeGame implements Minigame {
    public static final String ID = "quake";

    public static final GameSetting KILLS_TO_WIN =
            new GameSetting(
                    "kills_to_win",
                    25,
                    1,
                    1_000,
                    "Kills a player needs to win when every team has one player");
    public static final GameSetting TEAM_KILLS_TO_WIN =
            new GameSetting(
                    "team_kills_to_win",
                    100,
                    1,
                    10_000,
                    "Kills a team needs to win when teams have several players");
    public static final GameSetting RELOAD_TICKS =
            new GameSetting(
                    "reload_ticks", 24, 0, 200, "Ticks between railgun shots (20 = 1 second)");
    public static final GameSetting DASH_COOLDOWN_TICKS =
            new GameSetting(
                    "dash_cooldown_ticks", 40, 0, 400, "Ticks between dashes (20 = 1 second)");
    public static final GameSetting DASH =
            new GameSetting("dash", 1, 0, 1, "Whether the Dash feather works (1) or not (0)");
    public static final GameSetting SPEED_LEVEL =
            new GameSetting(
                    "speed_level", 2, 0, 5, "Level of the Speed effect players have (0 for none)");

    /** How far a railgun beam reaches. */
    public static final double RANGE = 100.0;

    /** How much wider than a player's hitbox a beam may pass and still hit them. */
    private static final double HIT_LENIENCY = 0.15;

    private static final double DASH_SPEED = 1.8;

    private static final List<GameSetting> SETTINGS;

    static {
        List<GameSetting> all = new ArrayList<>(GameSetting.common(5, 10, false));
        all.addAll(
                List.of(
                        KILLS_TO_WIN,
                        TEAM_KILLS_TO_WIN,
                        RELOAD_TICKS,
                        DASH_COOLDOWN_TICKS,
                        DASH,
                        SPEED_LEVEL));
        SETTINGS = List.copyOf(all);
    }

    private static final String[] STREAK_MESSAGES = {
        "is on a Killing Spree!",
        "is on a Rampage!",
        "is Dominating!",
        "is Unstoppable!",
        "is Godlike!"
    };

    /** Server tick at which each player may fire again. */
    private final Map<UUID, Long> nextShot = new HashMap<>();

    private final Map<UUID, Long> nextDash = new HashMap<>();
    private final Map<UUID, Integer> streaks = new HashMap<>();

    /** The player whose beam is being resolved, and how many players it has killed so far. */
    private @Nullable ServerPlayer shooter;

    private int shotKills;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Quake";
    }

    @Override
    public List<GameSetting> settings() {
        return SETTINGS;
    }

    @Override
    public Identifier defaultKit() {
        return BrainageMinigames.id("kits/quake");
    }

    @Override
    public GameType playerGameMode() {
        return GameType.ADVENTURE;
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings settings) throws MatchException {
        return MapArena.openRandom(server, ID);
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings settings, TeamLayout layout)
            throws MatchException {
        return MapArena.openRandom(
                server, ID, layout.isFreeForAll() ? 2 : layout.teamSizes().size());
    }

    /** The kills a team needs, which depends on whether the teams are single players. */
    public static int killsToWin(Match match) {
        boolean solo =
                match.layout().isFreeForAll()
                        || match.layout().teamSizes().stream().allMatch(size -> size == 1);
        return match.settings().get(solo ? KILLS_TO_WIN : TEAM_KILLS_TO_WIN);
    }

    @Override
    public void onStart(Match match) {
        for (ServerPlayer player : match.alivePlayers()) {
            applyEffects(match, player);
        }
    }

    @Override
    public void onRespawn(Match match, ServerPlayer player) {
        if (match.arena() instanceof MapArena map) {
            chooseRespawn(match, map, player)
                    .ifPresent(
                            point ->
                                    PlayerUtils.teleport(
                                            player, map.level(), point.position(), point.yaw()));
        }
        applyEffects(match, player);
    }

    private void applyEffects(Match match, ServerPlayer player) {
        int speed = match.settings().get(SPEED_LEVEL);
        if (speed > 0) {
            player.addEffect(
                    new MobEffectInstance(
                            MobEffects.SPEED,
                            MobEffectInstance.INFINITE_DURATION,
                            speed - 1,
                            false,
                            false));
        }
        player.addEffect(
                new MobEffectInstance(
                        MobEffects.SATURATION,
                        MobEffectInstance.INFINITE_DURATION,
                        0,
                        false,
                        false));
    }

    /**
     * A random respawn point from the half that is farthest from the nearest opponent, so players
     * do not respawn in front of a railgun.
     */
    private static Optional<MapArena.Point> chooseRespawn(
            Match match, MapArena map, ServerPlayer player) {
        List<MapArena.Point> points = map.points("respawn_");
        if (points.isEmpty()) {
            return Optional.empty();
        }
        List<Vec3> opponents = new ArrayList<>();
        for (ServerPlayer other : match.alivePlayers()) {
            if (other != player && !sameTeam(match, player, other)) {
                opponents.add(other.position());
            }
        }
        List<MapArena.Point> sorted = new ArrayList<>(points);
        sorted.sort(
                Comparator.comparingDouble(
                                (MapArena.Point point) -> nearest(point.position(), opponents))
                        .reversed());
        int choices = Math.max(1, (sorted.size() + 1) / 2);
        return Optional.of(sorted.get(ThreadLocalRandom.current().nextInt(choices)));
    }

    private static double nearest(Vec3 position, List<Vec3> others) {
        double best = Double.MAX_VALUE;
        for (Vec3 other : others) {
            best = Math.min(best, other.distanceToSqr(position));
        }
        return best;
    }

    private static boolean sameTeam(Match match, ServerPlayer a, ServerPlayer b) {
        Optional<MatchTeam> team = match.teamOf(a.getUUID());
        return team.isPresent() && team.equals(match.teamOf(b.getUUID()));
    }

    @Override
    public InteractionResult onUseItem(
            Match match, ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (stack.is(ItemTags.HOES)) {
            fire(match, player, stack);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (stack.is(Items.FEATHER)) {
            dash(match, player, stack);
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
    }

    /**
     * Fires the railgun if it has reloaded: an instant beam from the player's eyes that stops at
     * the first solid block and kills every opponent it passes through. Returns how many it killed,
     * or -1 while reloading.
     */
    public int fire(Match match, ServerPlayer player, ItemStack stack) {
        long now = match.server().getTickCount();
        UUID playerId = player.getUUID();
        if (now < nextShot.getOrDefault(playerId, Long.MIN_VALUE)) {
            return -1;
        }
        int reload = match.settings().get(RELOAD_TICKS);
        nextShot.put(playerId, now + reload);
        if (reload > 0) {
            player.getCooldowns().addCooldown(stack, reload);
        }

        ServerLevel level = (ServerLevel) player.level();
        Vec3 from = player.getEyePosition();
        Vec3 direction = player.getLookAngle();
        Vec3 limit = from.add(direction.scale(RANGE));
        HitResult wall =
                level.clip(
                        new ClipContext(
                                from,
                                limit,
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,
                                player));
        Vec3 to = wall.getType() == HitResult.Type.MISS ? limit : wall.getLocation();

        List<ServerPlayer> victims = new ArrayList<>();
        for (ServerPlayer other : match.alivePlayers()) {
            if (other == player
                    || other.level() != level
                    || other.isSpectator()
                    || sameTeam(match, player, other)) {
                continue;
            }
            AABB box = other.getBoundingBox().inflate(HIT_LENIENCY);
            if (box.contains(from) || box.clip(from, to).isPresent()) {
                victims.add(other);
            }
        }

        drawBeam(level, match, player, from, to);
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS,
                1.0F,
                1.6F);

        shooter = player;
        shotKills = 0;
        try {
            DamageSource source =
                    new DamageSource(
                            level.registryAccess()
                                    .lookupOrThrow(Registries.DAMAGE_TYPE)
                                    .getOrThrow(DamageTypes.ARROW),
                            player,
                            player);
            for (ServerPlayer victim : victims) {
                Vec3 at = victim.position();
                // A beam always kills, even straight after the victim's last death.
                victim.invulnerableTime = 0;
                if (victim.hurtServer(level, source, Float.MAX_VALUE)) {
                    level.sendParticles(
                            ParticleTypes.FIREWORK,
                            true,
                            true,
                            at.x,
                            at.y + 1.0,
                            at.z,
                            40,
                            0.3,
                            0.5,
                            0.3,
                            0.2);
                    level.playSound(
                            null,
                            at.x,
                            at.y,
                            at.z,
                            SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                            SoundSource.PLAYERS,
                            1.0F,
                            1.0F);
                }
            }
        } finally {
            shooter = null;
        }
        int kills = shotKills;
        if (kills >= 2) {
            String name =
                    switch (kills) {
                        case 2 -> "DOUBLE KILL";
                        case 3 -> "TRIPLE KILL";
                        case 4 -> "QUADRUPLE KILL";
                        default -> kills + "x KILL";
                    };
            match.broadcast(
                    Component.empty()
                            .append(player.getDisplayName())
                            .append(" got a ")
                            .append(Component.literal(name).withStyle(ChatFormatting.BOLD))
                            .append("!")
                            .withStyle(ChatFormatting.GOLD));
        }
        return kills;
    }

    private static void drawBeam(
            ServerLevel level, Match match, ServerPlayer player, Vec3 from, Vec3 to) {
        int color =
                match.teamOf(player.getUUID())
                        .flatMap(MatchTeam::color)
                        .map(TextColor::getValue)
                        .orElse(0xFFFF55);
        DustParticleOptions dust = new DustParticleOptions(color, 1.0F);
        Vec3 step = to.subtract(from);
        double length = step.length();
        if (length < 1.0E-6) {
            return;
        }
        step = step.scale(0.5 / length);
        // Start a little in front of the eyes so the beam does not block the shooter's view.
        Vec3 point = from.add(step.scale(2.0));
        for (double travelled = 1.0; travelled < length; travelled += 0.5) {
            level.sendParticles(dust, true, true, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
            point = point.add(step);
        }
    }

    /** Flings the player the way they are looking, if the dash has recharged. */
    public boolean dash(Match match, ServerPlayer player, ItemStack stack) {
        if (match.settings().get(DASH) == 0) {
            return false;
        }
        long now = match.server().getTickCount();
        UUID playerId = player.getUUID();
        if (now < nextDash.getOrDefault(playerId, Long.MIN_VALUE)) {
            return false;
        }
        int cooldown = match.settings().get(DASH_COOLDOWN_TICKS);
        nextDash.put(playerId, now + cooldown);
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(stack, cooldown);
        }
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() > 1.0E-6) {
            horizontal = horizontal.normalize();
        }
        double lift = Math.clamp(0.35 + look.y * 0.6, 0.2, 0.9);
        player.setDeltaMovement(horizontal.scale(DASH_SPEED).add(0.0, lift, 0.0));
        player.hurtMarked = true;
        player.resetFallDistance();
        player.level()
                .playSound(
                        null,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        SoundEvents.BAT_TAKEOFF,
                        SoundSource.PLAYERS,
                        0.8F,
                        1.4F);
        return true;
    }

    /** Only railgun beams hurt: they are the one arrow-type damage a player deals directly. */
    @Override
    public boolean allowDamage(Match match, ServerPlayer victim, DamageSource source) {
        return source.is(DamageTypes.ARROW)
                && source.getEntity() instanceof ServerPlayer
                && source.getDirectEntity() == source.getEntity();
    }

    @Override
    public DeathResult onDeath(Match match, ServerPlayer victim, @Nullable ServerPlayer killer) {
        int victimStreak = streaks.getOrDefault(victim.getUUID(), 0);
        streaks.remove(victim.getUUID());
        if (killer != null && killer != victim && !sameTeam(match, killer, victim)) {
            match.teamOf(killer.getUUID()).ifPresent(team -> team.addScore(1));
            if (killer == shooter) {
                shotKills++;
            }
            if (victimStreak >= 5) {
                match.broadcast(
                        Component.empty()
                                .append(killer.getDisplayName())
                                .append(" shut down ")
                                .append(victim.getDisplayName())
                                .append("'s " + victimStreak + " kill streak!")
                                .withStyle(ChatFormatting.AQUA));
            }
            int streak = streaks.merge(killer.getUUID(), 1, Integer::sum);
            if (streak % 5 == 0) {
                String message = STREAK_MESSAGES[Math.min(streak / 5, STREAK_MESSAGES.length) - 1];
                match.broadcast(
                        Component.empty()
                                .append(killer.getDisplayName())
                                .append(" " + message)
                                .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        return DeathResult.RESPAWN;
    }

    @Override
    public void tick(Match match) {
        int target = killsToWin(match);
        List<MatchTeam> reached =
                match.teams().stream().filter(team -> team.score() >= target).toList();
        if (!reached.isEmpty()) {
            int best = reached.stream().mapToInt(MatchTeam::score).max().orElse(target);
            match.finish(reached.stream().filter(team -> team.score() == best).toList());
        }
    }

    @Override
    public void addSidebarLines(Match match, List<Component> lines) {
        lines.add(MatchSidebar.label("First to: ", killsToWin(match) + " kills"));
    }

    @Override
    public Component sidebarTeamSuffix(Match match, MatchTeam team) {
        return Component.literal(" " + team.score() + "/" + killsToWin(match))
                .withStyle(ChatFormatting.YELLOW);
    }

    @Override
    public void onRelease(Match match, ServerPlayer player) {
        forget(player.getUUID());
    }

    @Override
    public void onClose(Match match) {
        for (MatchTeam team : match.teams()) {
            team.members().forEach(this::forget);
        }
    }

    private void forget(UUID playerId) {
        nextShot.remove(playerId);
        nextDash.remove(playerId);
        streaks.remove(playerId);
    }
}
