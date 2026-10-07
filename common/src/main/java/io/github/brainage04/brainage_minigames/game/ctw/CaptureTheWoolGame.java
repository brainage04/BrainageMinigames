package io.github.brainage04.brainage_minigames.game.ctw;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.MatchTeam;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.game.bridge.BridgeGame;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Capture the Wool, after Hypixel's Wool Games mode: every team keeps wools in wool rooms at the
 * back of its base, which it may not enter, and has a monument with a slot for each wool it must
 * steal from the other side. Breaking a wool in an enemy wool room takes it; placing it on its slot
 * on your own monument captures it for good, and the first team to fill its monument wins. A
 * killed carrier drops the wool where they died, where their teammates can pick it up; it returns
 * to its wool room after {@link #WOOL_RETURN_SECONDS}, at once when it falls into the void, and as
 * soon as a player of any other team touches it. Killed players watch for {@link #RESPAWN_SECONDS}
 * and then respawn at their base with the kit. Only blocks placed during the match can be broken,
 * and nothing can be placed in a wool room or on a monument except its wool. At the time limit the
 * team with the most wool placed wins, then the one carrying the most.
 *
 * <p>Maps mark the wools with {@code point wool_<team>_<colour>}, the monument slots with {@code
 * point monument_<team>_<colour>} and the wool rooms with {@code region woolroom_<team>_<n>}; see
 * {@link WoolLayout}.
 */
public final class CaptureTheWoolGame implements Minigame {
    public static final String ID = "capture_the_wool";

    public static final GameSetting RESPAWN_SECONDS =
            new GameSetting(
                    "respawn_seconds",
                    5,
                    0,
                    60,
                    "Seconds a killed player watches before respawning at their base");
    public static final GameSetting WOOL_RETURN_SECONDS =
            new GameSetting(
                    "wool_return_seconds",
                    10,
                    1,
                    120,
                    "Seconds a dropped wool lies where it fell before it returns to its wool room");

    /** How far from a carrier a wool they no longer hold is looked for on the ground. */
    private static final double TOSS_REACH = 8.0;

    private static final List<GameSetting> SETTINGS;

    static {
        List<GameSetting> all = new ArrayList<>(GameSetting.common(10, 20, true));
        all.add(RESPAWN_SECONDS);
        all.add(WOOL_RETURN_SECONDS);
        SETTINGS = List.copyOf(all);
    }

    /** Where a wool is. */
    public enum WoolState {
        /** In its wool room, waiting to be taken. */
        HOME,
        /** In a player's inventory. */
        CARRIED,
        /** On the ground where its carrier died or dropped it. */
        DROPPED,
        /** On its monument slot, for good. */
        PLACED
    }

    private final Map<Match, State> states = new HashMap<>();

    /** One wool of a match. */
    private static final class Wool {
        private final WoolLayout.Spec spec;
        private WoolState state = WoolState.HOME;

        /** Who carries it, or who placed it. */
        private @Nullable UUID carrier;

        private @Nullable ItemEntity dropped;
        private int droppedTicks;

        private Wool(WoolLayout.Spec spec) {
            this.spec = spec;
        }

        private Block block() {
            return Blocks.WOOL.pick(spec.colour());
        }

        private Item item() {
            return Items.WOOL.pick(spec.colour());
        }
    }

    /** Per-match state. */
    private static final class State {
        private final WoolLayout layout;
        private final List<Wool> wools;

        /** Killed players and the ticks until they respawn. */
        private final Map<UUID, Integer> respawning = new LinkedHashMap<>();

        /** Where each player last stood outside their own wool rooms. */
        private final Map<UUID, Vec3> outside = new HashMap<>();

        private State(WoolLayout layout) {
            this.layout = layout;
            this.wools = layout.wools().stream().map(Wool::new).toList();
        }
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Capture the Wool";
    }

    @Override
    public List<GameSetting> settings() {
        return SETTINGS;
    }

    @Override
    public Identifier defaultKit() {
        return BrainageMinigames.id("kits/capture_the_wool");
    }

    @Override
    public GameType playerGameMode() {
        return GameType.SURVIVAL;
    }

    /** Two even teams, as Capture the Wool is played; any other team layout can still be built. */
    @Override
    public List<TeamLayout> layoutPresets(GameSettings settings) {
        return List.of("1v1", "2v2", "3v3", "4v4", "6v6").stream()
                .map(layout -> TeamLayout.parse(layout).orElseThrow())
                .toList();
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings settings) throws MatchException {
        return MapArena.openRandom(server, ID, 2);
    }

    /**
     * A map for exactly as many teams as the layout has, since every team needs a monument and wool
     * to defend; a map a player picked must be one too. Free-for-all has no teams to steal from.
     */
    @Override
    public Arena openArena(MinecraftServer server, GameSettings settings, TeamLayout layout)
            throws MatchException {
        if (layout.isFreeForAll()) {
            throw new MatchException("Capture the Wool is played in teams, such as 2v2.");
        }
        int teams = layout.teamSizes().size();
        MapArena arena;
        if (MapArena.hasChosenMap()) {
            arena = MapArena.openRandom(server, ID, teams);
        } else {
            ServerLevel level = server.getLevel(ModDimensions.MINIGAMES);
            if (level == null) {
                throw new MatchException("The minigames dimension is unavailable.");
            }
            List<Identifier> exact =
                    MapArena.maps(server, ID).stream()
                            .filter(map -> MapArena.teamSlots(server, map) == teams)
                            .sorted()
                            .toList();
            if (exact.isEmpty()) {
                throw new MatchException(
                        "No Capture the Wool map is for %d teams.".formatted(teams));
            }
            arena = MapArena.reserve(level, exact.get(ThreadLocalRandom.current().nextInt(exact.size())));
        }
        try {
            WoolLayout.read(arena, teams);
        } catch (MatchException exception) {
            arena.close();
            throw exception;
        }
        return arena;
    }

    @Override
    public void onStart(Match match) {
        MapArena arena = (MapArena) match.arena();
        WoolLayout layout;
        try {
            layout = WoolLayout.read(arena, match.teams().size());
        } catch (MatchException exception) {
            throw new IllegalStateException("The map was checked when the match opened.", exception);
        }
        State state = new State(layout);
        states.put(match, state);
        for (Wool wool : state.wools) {
            arena.level().setBlock(wool.spec.source(), wool.block().defaultBlockState(), Block.UPDATE_CLIENTS);
            arena.level().setBlock(wool.spec.slot(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (ServerPlayer player : match.alivePlayers()) {
            dress(match, player);
            state.outside.put(player.getUUID(), player.position());
        }
    }

    @Override
    public void onClose(Match match) {
        State state = states.remove(match);
        if (state == null) return;
        for (Wool wool : state.wools) {
            if (wool.dropped != null) wool.dropped.discard();
        }
    }

    @Override
    public void tick(Match match) {
        State state = states.get(match);
        if (state == null) return;
        int limit = match.settings().get(GameSetting.TIME_LIMIT_MINUTES) * 60 * 20;
        if (limit > 0 && match.activeTicks() >= limit) {
            match.finish(timeoutWinners(match));
            return;
        }
        tickRespawns(match, state);
        for (ServerPlayer player : match.alivePlayers()) {
            if (player.isSpectator()) continue;
            // Nobody gets hungry; health comes back through natural regeneration.
            player.getFoodData().setFoodLevel(20);
            keepOutOfOwnWoolRooms(match, state, player);
        }
        for (Wool wool : state.wools) {
            switch (wool.state) {
                case CARRIED -> tickCarried(match, state, wool);
                case DROPPED -> tickDropped(match, state, wool);
                case HOME, PLACED -> {}
            }
            if (match.phase() != MatchPhase.ACTIVE) return;
        }
    }

    private void tickRespawns(Match match, State state) {
        for (Iterator<Map.Entry<UUID, Integer>> entries = state.respawning.entrySet().iterator(); entries.hasNext(); ) {
            Map.Entry<UUID, Integer> entry = entries.next();
            ServerPlayer player = match.server().getPlayerList().getPlayer(entry.getKey());
            if (player == null || !match.isAlive(entry.getKey())) {
                entries.remove();
                continue;
            }
            int left = entry.getValue() - 1;
            if (left <= 0) {
                entries.remove();
                match.respawn(player);
                continue;
            }
            entry.setValue(left);
            if (left % 20 == 0) {
                player.sendSystemMessage(
                        Component.literal("Respawning in " + left / 20 + "...").withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    /** Puts a player who stepped into their own team's wool room back where they last stood outside. */
    private static void keepOutOfOwnWoolRooms(Match match, State state, ServerPlayer player) {
        int team = match.teamOf(player.getUUID()).map(MatchTeam::number).orElse(0);
        if (!state.layout.inWoolRoom(team, player.position())) {
            state.outside.put(player.getUUID(), player.position());
            return;
        }
        Vec3 back = state.outside.get(player.getUUID());
        if (back == null) {
            match.respawn(player);
        } else {
            player.setDeltaMovement(Vec3.ZERO);
            player.teleportTo(back.x, back.y, back.z);
        }
        player.sendSystemMessage(
                Component.literal("You can't enter your own wool room.").withStyle(ChatFormatting.RED), true);
    }

    /** Captures a wool its carrier placed, or notices that they no longer hold it. */
    private void tickCarried(Match match, State state, Wool wool) {
        MapArena arena = (MapArena) match.arena();
        ServerPlayer carrier = wool.carrier == null ? null : match.server().getPlayerList().getPlayer(wool.carrier);
        if (arena.level().getBlockState(wool.spec.slot()).is(wool.block())) {
            capture(match, state, wool, carrier);
            return;
        }
        if (carrier != null && match.isAlive(carrier.getUUID()) && !carrier.isSpectator() && holds(carrier, wool)) {
            return;
        }
        ItemEntity tossed = null;
        if (carrier != null && carrier.level() == arena.level()) {
            tossed = arena.level().getEntitiesOfClass(ItemEntity.class, carrier.getBoundingBox().inflate(TOSS_REACH),
                    entity -> entity.getItem().is(wool.item())).stream().findFirst().orElse(null);
        }
        if (tossed != null) {
            drop(match, wool, tossed);
            match.broadcast(name(match, wool.carrier).append(Component.literal(" dropped the ").withStyle(ChatFormatting.GRAY))
                    .append(woolName(wool)).append(Component.literal("!").withStyle(ChatFormatting.GRAY)));
        } else {
            returnHome(match, state, wool);
        }
    }

    /** Returns a dropped wool once it has lain long enough, or hands it to whoever picked it up. */
    private void tickDropped(Match match, State state, Wool wool) {
        ItemEntity dropped = wool.dropped;
        if (dropped != null && dropped.isAlive()) {
            wool.droppedTicks++;
            if (dropped.getY() < match.arena().voidY()
                    || wool.droppedTicks >= match.settings().get(WOOL_RETURN_SECONDS) * 20) {
                returnHome(match, state, wool);
            }
            return;
        }
        wool.dropped = null;
        ServerPlayer holder = null;
        for (ServerPlayer player : match.alivePlayers()) {
            if (holds(player, wool)) {
                holder = player;
                break;
            }
        }
        if (holder == null) {
            returnHome(match, state, wool);
            return;
        }
        int team = match.teamOf(holder.getUUID()).map(MatchTeam::number).orElse(0);
        if (team == wool.spec.capturer()) {
            wool.state = WoolState.CARRIED;
            wool.carrier = holder.getUUID();
            match.broadcast(name(match, holder.getUUID()).append(Component.literal(" picked up the ").withStyle(ChatFormatting.GRAY))
                    .append(woolName(wool)).append(Component.literal("!").withStyle(ChatFormatting.GRAY)));
            return;
        }
        // Touching a wool your team does not need returns it.
        match.broadcast(name(match, holder.getUUID()).append(Component.literal(" returned the ").withStyle(ChatFormatting.GRAY))
                .append(woolName(wool)).append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
        returnHome(match, state, wool, false);
    }

    private void capture(Match match, State state, Wool wool, @Nullable ServerPlayer carrier) {
        wool.state = WoolState.PLACED;
        if (carrier != null) removeAll(carrier, wool);
        MatchTeam team = match.teamNumbered(wool.spec.capturer()).orElseThrow();
        team.addScore(1);
        int placed = placed(state, team.number());
        int slots = state.layout.monument(team.number()).size();
        match.broadcast(name(match, wool.carrier).append(Component.literal(" placed the ").withStyle(ChatFormatting.GOLD))
                .append(woolName(wool)).append(Component.literal(" on ").withStyle(ChatFormatting.GOLD))
                .append(team.displayName()).append(Component.literal("'s monument! (" + placed + "/" + slots + ")")
                        .withStyle(ChatFormatting.GOLD)));
        Component title = Component.empty().append(woolName(wool)).append(Component.literal(" captured!").withStyle(ChatFormatting.GOLD));
        Component subtitle = Component.empty().append(team.displayName())
                .append(Component.literal(" " + placed + "/" + slots).withStyle(ChatFormatting.GRAY));
        for (ServerPlayer player : match.onlineMembers()) {
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);
        }
        if (placed >= slots) {
            match.finish(List.of(team));
        }
    }

    /** Leaves the wool on the ground as {@code item}, glowing, until it is picked up or returns. */
    private static void drop(Match match, Wool wool, ItemEntity item) {
        item.setGlowingTag(true);
        item.setUnlimitedLifetime();
        wool.state = WoolState.DROPPED;
        wool.dropped = item;
        wool.droppedTicks = 0;
        wool.carrier = null;
    }

    private void returnHome(Match match, State state, Wool wool) {
        returnHome(match, state, wool, true);
    }

    /** Puts the wool back in its wool room, taking it from anyone who still has it. */
    private void returnHome(Match match, State state, Wool wool, boolean announce) {
        if (wool.dropped != null) wool.dropped.discard();
        wool.dropped = null;
        wool.carrier = null;
        wool.state = WoolState.HOME;
        for (ServerPlayer player : match.alivePlayers()) removeAll(player, wool);
        match.arena().level().setBlock(wool.spec.source(), wool.block().defaultBlockState(), Block.UPDATE_CLIENTS);
        if (announce) {
            match.broadcast(Component.literal("The ").withStyle(ChatFormatting.GRAY).append(woolName(wool))
                    .append(Component.literal(" returned to ").withStyle(ChatFormatting.GRAY))
                    .append(teamName(match, wool.spec.owner()))
                    .append(Component.literal("'s wool room.").withStyle(ChatFormatting.GRAY)));
        }
    }

    /** At the time limit: the most wool placed, then the most wool carried, wins; level teams draw. */
    public List<MatchTeam> timeoutWinners(Match match) {
        State state = states.get(match);
        List<MatchTeam> standing = match.standingTeams();
        if (state == null) return standing;
        Comparator<MatchTeam> order = Comparator.<MatchTeam>comparingInt(team -> placed(state, team.number()))
                .thenComparingInt(team -> carried(state, team.number()));
        MatchTeam best = standing.stream().max(order).orElse(null);
        if (best == null) return List.of();
        return standing.stream().filter(team -> order.compare(team, best) == 0).toList();
    }

    private static int placed(State state, int team) {
        return (int) state.wools.stream()
                .filter(wool -> wool.spec.capturer() == team && wool.state == WoolState.PLACED).count();
    }

    private static int carried(State state, int team) {
        return (int) state.wools.stream()
                .filter(wool -> wool.spec.capturer() == team && wool.state == WoolState.CARRIED).count();
    }

    /**
     * Breaking a wool in its wool room takes it, if the breaker's team places it; nothing drops and
     * the block is gone until the wool returns. Otherwise only blocks placed during the match can be
     * broken, never a placed wool.
     */
    @Override
    public boolean allowBreak(Match match, ServerPlayer player, BlockPos pos, BlockState block) {
        State state = states.get(match);
        if (state == null) return false;
        for (Wool wool : state.wools) {
            if (wool.spec.slot().equals(pos)) return false;
            if (wool.spec.source().equals(pos) && wool.state == WoolState.HOME) {
                take(match, wool, player);
                return false;
            }
        }
        return match.isPlacedBlock(pos);
    }

    private void take(Match match, Wool wool, ServerPlayer player) {
        int team = match.teamOf(player.getUUID()).map(MatchTeam::number).orElse(0);
        if (team != wool.spec.capturer()) {
            player.sendSystemMessage(Component.literal("Only ").withStyle(ChatFormatting.RED)
                    .append(teamName(match, wool.spec.capturer()))
                    .append(Component.literal(" can take the ").withStyle(ChatFormatting.RED))
                    .append(woolName(wool)).append(Component.literal(".").withStyle(ChatFormatting.RED)), true);
            return;
        }
        match.arena().level().setBlock(wool.spec.source(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        PlayerUtils.giveOrDrop(player, new ItemStack(wool.item()));
        wool.state = WoolState.CARRIED;
        wool.carrier = player.getUUID();
        match.broadcast(name(match, player.getUUID()).append(Component.literal(" took the ").withStyle(ChatFormatting.GOLD))
                .append(woolName(wool)).append(Component.literal(" from ").withStyle(ChatFormatting.GOLD))
                .append(teamName(match, wool.spec.owner()))
                .append(Component.literal("'s wool room!").withStyle(ChatFormatting.GOLD)));
        for (ServerPlayer member : match.onlineMembers()) {
            member.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.0F);
        }
    }

    /**
     * Inside the map's build regions, never in a wool room, on a wool's place or on a monument
     * slot; a wool only on its own slot by the player carrying it.
     */
    @Override
    public boolean allowPlace(Match match, ServerPlayer player, BlockPos pos, BlockState block) {
        State state = states.get(match);
        if (state == null) return false;
        for (Wool wool : state.wools) {
            if (block.is(wool.block())) {
                return wool.spec.slot().equals(pos) && wool.state == WoolState.CARRIED
                        && player.getUUID().equals(wool.carrier);
            }
        }
        for (Wool wool : state.wools) {
            if (wool.spec.slot().equals(pos) || wool.spec.source().equals(pos)) return false;
        }
        return !state.layout.inAnyWoolRoom(Vec3.atCenterOf(pos)) && match.arena().canBuild(pos);
    }

    /**
     * A killed carrier drops their wool where they died (it returns at once from the void); the
     * player then watches until they respawn.
     */
    @Override
    public DeathResult onDeath(Match match, ServerPlayer victim, @Nullable ServerPlayer killer) {
        State state = states.get(match);
        if (state == null) return DeathResult.RESPAWN;
        boolean inVoid = victim.getY() < match.arena().voidY();
        for (Wool wool : state.wools) {
            if (wool.state != WoolState.CARRIED || !victim.getUUID().equals(wool.carrier)) continue;
            removeAll(victim, wool);
            if (inVoid) {
                returnHome(match, state, wool);
                continue;
            }
            ItemEntity item = new ItemEntity(victim.level(), victim.getX(), victim.getY() + 0.5, victim.getZ(),
                    new ItemStack(wool.item()), 0.0, 0.2, 0.0);
            item.setPickUpDelay(10);
            victim.level().addFreshEntity(item);
            drop(match, wool, item);
            match.broadcast(name(match, victim.getUUID()).append(Component.literal(" dropped the ").withStyle(ChatFormatting.GRAY))
                    .append(woolName(wool)).append(Component.literal("!").withStyle(ChatFormatting.GRAY)));
        }
        int ticks = match.settings().get(RESPAWN_SECONDS) * 20;
        if (ticks > 0) state.respawning.put(victim.getUUID(), ticks);
        return DeathResult.RESPAWN;
    }

    /** Back at the base with the kit, or, while the respawn timer runs, watching from above the map. */
    @Override
    public void onRespawn(Match match, ServerPlayer player) {
        State state = states.get(match);
        if (state != null && state.respawning.containsKey(player.getUUID())) {
            player.getInventory().clearContent();
            player.setGameMode(GameType.SPECTATOR);
            PlayerUtils.teleport(player, match.arena().level(), match.arena().lobbyPosition(), player.getYRot());
            return;
        }
        dress(match, player);
        if (state != null) state.outside.put(player.getUUID(), player.position());
    }

    /** A player who leaves takes no wool with them: whatever they carry returns to its room. */
    @Override
    public void onRelease(Match match, ServerPlayer player) {
        State state = states.get(match);
        if (state == null) return;
        state.respawning.remove(player.getUUID());
        state.outside.remove(player.getUUID());
        for (Wool wool : state.wools) {
            if (wool.state == WoolState.CARRIED && player.getUUID().equals(wool.carrier)) {
                removeAll(player, wool);
                returnHome(match, state, wool, match.phase() == MatchPhase.ACTIVE);
            }
        }
    }

    /**
     * A line for every wool away from both its room and its slot: who carries it, or how long until a dropped one
     * returns. Each team's line already shows its monument (see {@link #sidebarTeamSuffix}).
     */
    @Override
    public void addSidebarLines(Match match, List<Component> lines) {
        State state = states.get(match);
        if (state == null) return;
        List<Wool> wools = new ArrayList<>(state.wools);
        wools.sort(Comparator.comparingInt(wool -> wool.spec.capturer()));
        for (Wool wool : wools) {
            if (wool.state != WoolState.CARRIED && wool.state != WoolState.DROPPED) continue;
            MutableComponent line = Component.empty()
                    .append(Component.literal("▣ ").withStyle(style -> style.withColor(color(wool))))
                    .append(Component.literal(WoolLayout.name(wool.spec.colour()) + ": ").withStyle(ChatFormatting.WHITE));
            if (wool.state == WoolState.CARRIED) {
                line.append(Component.literal(wool.carrier == null ? "carried" : match.nameOf(wool.carrier))
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                int ticks = match.settings().get(WOOL_RETURN_SECONDS) * 20 - wool.droppedTicks;
                line.append(Component.literal("dropped, " + Math.max(0, (ticks + 19) / 20) + "s")
                        .withStyle(ChatFormatting.RED));
            }
            lines.add(line);
        }
    }

    /** The team's monument: one square per slot in the wool's colour, filled once placed. */
    @Override
    public Component sidebarTeamSuffix(Match match, MatchTeam team) {
        State state = states.get(match);
        if (state == null) return Component.empty();
        MutableComponent suffix = Component.literal(" ");
        for (Wool wool : state.wools) {
            if (wool.spec.capturer() != team.number()) continue;
            String mark = switch (wool.state) {
                case PLACED -> "■";
                case CARRIED -> "▣";
                case HOME, DROPPED -> "□";
            };
            suffix.append(Component.literal(mark).withStyle(style -> style.withColor(color(wool))));
        }
        return suffix;
    }

    /** Where the wool of {@code colour} is, or empty when the match plays no such wool. */
    public Optional<WoolState> woolState(Match match, DyeColor colour) {
        return wool(match, colour).map(wool -> wool.state);
    }

    /** Who carries the wool of {@code colour}, or placed it. */
    public Optional<UUID> carrier(Match match, DyeColor colour) {
        return wool(match, colour).map(wool -> wool.carrier);
    }

    /** Where the wool of {@code colour} lies in its wool room and where it is placed. */
    public Optional<BlockPos> source(Match match, DyeColor colour) {
        return wool(match, colour).map(wool -> wool.spec.source());
    }

    public Optional<BlockPos> slot(Match match, DyeColor colour) {
        return wool(match, colour).map(wool -> wool.spec.slot());
    }

    /** Whether the player was killed and waits to respawn. */
    public boolean isRespawning(Match match, UUID player) {
        State state = states.get(match);
        return state != null && state.respawning.containsKey(player);
    }

    private Optional<Wool> wool(Match match, DyeColor colour) {
        State state = states.get(match);
        if (state == null) return Optional.empty();
        return state.wools.stream().filter(wool -> wool.spec.colour() == colour).findFirst();
    }

    /**
     * What a bot needs to play the match {@code player} is in, using only JDK and Minecraft types,
     * or null outside an active Capture the Wool match: {@code "team"} (Integer), {@code "slot"}
     * (Integer, the player's place in its team from 0), {@code "teamSize"} (Integer), {@code
     * "guard"} (Vec3, where the team's defenders stand), {@code "woolRooms"} (List of AABB, the
     * team's own wool rooms, which it may not enter) and {@code "wools"}: a List of Maps, one per
     * wool, with {@code "colour"} (String, the dye name), {@code "owner"} and {@code "capturer"}
     * (Integer team numbers), {@code "source"} and {@code "slot"} (BlockPos), {@code "state"}
     * (String: home, carried, dropped or placed), and {@code "carrier"} (UUID) while carried or
     * {@code "dropped"} (Vec3) while on the ground.
     */
    public static @Nullable Map<String, Object> botView(ServerPlayer player) {
        Match match = MatchManager.activeMatch(player.getUUID());
        if (match == null || !(match.game() instanceof CaptureTheWoolGame game)) return null;
        State state = game.states.get(match);
        MatchTeam team = match.teamOf(player.getUUID()).orElse(null);
        if (state == null || team == null) return null;
        List<Map<String, Object>> wools = new ArrayList<>();
        for (Wool wool : state.wools) {
            Map<String, Object> view = new HashMap<>();
            view.put("colour", wool.spec.colour().getName());
            view.put("owner", wool.spec.owner());
            view.put("capturer", wool.spec.capturer());
            view.put("source", wool.spec.source());
            view.put("slot", wool.spec.slot());
            view.put("state", wool.state.name().toLowerCase(java.util.Locale.ROOT));
            if (wool.state == WoolState.CARRIED && wool.carrier != null) view.put("carrier", wool.carrier);
            if (wool.state == WoolState.DROPPED && wool.dropped != null) view.put("dropped", wool.dropped.position());
            wools.add(Map.copyOf(view));
        }
        Vec3 guard = state.layout.guard(team.number()).orElseGet(() -> {
            List<Arena.Spawn> spawns = ((MapArena) match.arena()).spawnsOf(team.number());
            return spawns.getFirst().position();
        });
        List<AABB> rooms = state.layout.woolRooms().getOrDefault(team.number(), List.of());
        return Map.of(
                "team", team.number(),
                "slot", Math.max(0, team.members().indexOf(player.getUUID())),
                "teamSize", team.members().size(),
                "guard", guard,
                "woolRooms", List.copyOf(rooms),
                "wools", List.copyOf(wools));
    }

    /** Dyes the kit's leather armour in the team's colour. */
    private static void dress(Match match, ServerPlayer player) {
        Optional<DyeColor> dye = match.teamOf(player.getUUID())
                .flatMap(team -> team.scoreboardTeam().getColor())
                .map(BridgeGame::dyeOf);
        if (dye.isEmpty()) return;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ItemTags.CAULDRON_CAN_REMOVE_DYE)) {
                stack.set(DataComponents.DYED_COLOR, new DyedItemColor(dye.get().getTextureDiffuseColor()));
            }
        }
    }

    private static boolean holds(ServerPlayer player, Wool wool) {
        return player.getInventory().contains(stack -> stack.is(wool.item()))
                || player.containerMenu.getCarried().is(wool.item());
    }

    private static void removeAll(ServerPlayer player, Wool wool) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(wool.item())) inventory.setItem(slot, ItemStack.EMPTY);
        }
        if (player.containerMenu.getCarried().is(wool.item())) player.containerMenu.setCarried(ItemStack.EMPTY);
    }

    private static TextColor color(Wool wool) {
        return TextColor.fromRgb(wool.spec.colour().getTextColor());
    }

    private static MutableComponent woolName(Wool wool) {
        return Component.literal(WoolLayout.name(wool.spec.colour()) + " wool").withStyle(style -> style.withColor(color(wool)));
    }

    private static MutableComponent teamName(Match match, int number) {
        return match.teamNumbered(number).map(team -> Component.empty().append(team.displayName()))
                .orElseGet(() -> Component.literal(Match.teamName(number)));
    }

    private static MutableComponent name(Match match, @Nullable UUID player) {
        if (player == null) return Component.literal("Someone");
        String text = match.nameOf(player);
        return Component.literal(text).withStyle(style -> match.teamOf(player)
                .flatMap(MatchTeam::color).map(style::withColor).orElse(style));
    }
}
