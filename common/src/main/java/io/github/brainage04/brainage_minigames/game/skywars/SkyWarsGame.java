package io.github.brainage04.brainage_minigames.game.skywars;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * SkyWars (Hypixel normal mode / Minemen SkyWars duels): every team starts in a glass cage above
 * its own island of a {@link MapArena}; the cages open when the countdown ends. Island chests hold
 * basic gear and blocks, the mid island's chests better gear, and every chest is refilled at the
 * refill times. Falling into the void or dying eliminates; the last team standing wins.
 */
public final class SkyWarsGame implements Minigame {
    public static final String ID = "skywars";

    public static final GameSetting FIRST_REFILL =
            new GameSetting(
                    "first_refill_seconds",
                    180,
                    0,
                    3600,
                    "Seconds after the cages open that every chest is refilled (0 disables)");
    public static final GameSetting SECOND_REFILL =
            new GameSetting(
                    "second_refill_seconds",
                    300,
                    0,
                    3600,
                    "Seconds after the cages open of the second refill (0 disables)");

    /** Chests whose loot table has this prefix are SkyWars chests the game fills and refills. */
    private static final String LOOT_PREFIX = "skywars/";

    private static final List<GameSetting> SETTINGS;

    static {
        List<GameSetting> all = new ArrayList<>(GameSetting.common(10, 9, true));
        all.add(FIRST_REFILL);
        all.add(SECOND_REFILL);
        SETTINGS = List.copyOf(all);
    }

    /** What the game prepared in each arena it opened; arenas are only held by their match. */
    private final Map<Arena, State> states = new WeakHashMap<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "SkyWars";
    }

    @Override
    public List<GameSetting> settings() {
        return SETTINGS;
    }

    @Override
    public Optional<String> validate(GameSettings settings) {
        int first = settings.get(FIRST_REFILL);
        int second = settings.get(SECOND_REFILL);
        if (first > 0 && second > 0 && second <= first) {
            return Optional.of(
                    "second_refill_seconds (%d) must be after first_refill_seconds (%d)."
                            .formatted(second, first));
        }
        return Optional.empty();
    }

    @Override
    public Identifier defaultKit() {
        return BrainageMinigames.id("kits/skywars");
    }

    @Override
    public GameType playerGameMode() {
        return GameType.SURVIVAL;
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings settings) throws MatchException {
        return openArena(server, settings, TeamLayout.FREE_FOR_ALL);
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings settings, TeamLayout layout)
            throws MatchException {
        int teams = layout.isFreeForAll() ? 2 : layout.teamSizes().size();
        return prepare(MapArena.openRandom(server, ID, teams));
    }

    /**
     * Builds a glass cage at every spawn of every island and records the map's SkyWars chests.
     * Public so tests can prepare a specific map pasted into the test level.
     */
    public MapArena prepare(MapArena arena) {
        State state = new State();
        ServerLevel level = arena.level();
        for (int team = 1; team <= arena.teamSlots(); team++) {
            for (Arena.Spawn spawn : arena.spawnsOf(team)) {
                buildCage(level, BlockPos.containing(spawn.position()), state.cage);
            }
        }
        BoundingBox bounds = arena.bounds();
        bounds.intersectingChunks()
                .forEach(
                        (ChunkPos chunk) -> {
                            for (BlockEntity entity :
                                    level.getChunk(chunk.x(), chunk.z())
                                            .getBlockEntities()
                                            .values()) {
                                if (entity instanceof RandomizableContainerBlockEntity container
                                        && bounds.isInside(entity.getBlockPos())
                                        && isSkyWarsLoot(container.getLootTable())) {
                                    state.chests.put(
                                            entity.getBlockPos().immutable(),
                                            container.getLootTable());
                                }
                            }
                        });
        states.put(arena, state);
        return arena;
    }

    private static boolean isSkyWarsLoot(@Nullable ResourceKey<LootTable> table) {
        return table != null
                && table.identifier().getNamespace().equals(BrainageMinigames.MOD_ID)
                && table.identifier().getPath().startsWith(LOOT_PREFIX);
    }

    /** A cage with a one-block interior: the spawn block and the two above it. */
    private static void buildCage(ServerLevel level, BlockPos feet, List<BlockPos> cage) {
        BlockState glass = Blocks.GLASS.defaultBlockState();
        for (int dy = -1; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean interior = dx == 0 && dz == 0 && dy >= 0 && dy <= 2;
                    BlockPos pos = feet.offset(dx, dy, dz);
                    if (!interior && level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, glass, Block.UPDATE_ALL);
                        cage.add(pos);
                    }
                }
            }
        }
    }

    @Override
    public void onStart(Match match) {
        State state = states.get(match.arena());
        if (state == null) {
            return;
        }
        ServerLevel level = match.arena().level();
        for (BlockPos pos : state.cage) {
            if (level.getBlockState(pos).is(Blocks.GLASS)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        state.cage.clear();
        // Lobby players could open mid chests, so every chest starts from a fresh roll.
        fillChests(level, state, true);
        match.broadcast(Component.literal("The cages opened!").withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void onClose(Match match) {
        states.remove(match.arena());
    }

    @Override
    public void tick(Match match) {
        State state = states.get(match.arena());
        if (state == null) {
            return;
        }
        if (match.activeTicks() % 20 != 0) {
            return;
        }
        int seconds = match.activeTicks() / 20;
        for (GameSetting refill : List.of(FIRST_REFILL, SECOND_REFILL)) {
            int at = match.settings().get(refill);
            if (at > 0 && seconds == at) {
                refill(match);
            }
        }
    }

    /** Rolls every surviving chest's loot table into its empty slots, as a refill. */
    public void refill(Match match) {
        State state = states.get(match.arena());
        if (state == null) {
            return;
        }
        fillChests(match.arena().level(), state, false);
        match.broadcast(
                Component.literal("All chests were refilled!").withStyle(ChatFormatting.GOLD));
        for (ServerPlayer player : match.onlineMembers()) {
            player.playSound(SoundEvents.CHEST_OPEN, 1.0F, 1.0F);
        }
    }

    private static void fillChests(ServerLevel level, State state, boolean clear) {
        for (Map.Entry<BlockPos, ResourceKey<LootTable>> chest : state.chests.entrySet()) {
            if (!(level.getBlockEntity(chest.getKey())
                    instanceof RandomizableContainerBlockEntity container)) {
                // Broken by a player.
                continue;
            }
            if (clear) {
                container.clearContent();
            }
            container.setLootTable(chest.getValue(), level.getRandom().nextLong());
            container.unpackLootTable(null);
        }
    }

    @Override
    public DeathResult onDeath(Match match, ServerPlayer victim, @Nullable ServerPlayer killer) {
        State state = states.get(match.arena());
        if (state != null && killer != null && killer != victim) {
            Optional<MatchTeam> team = match.teamOf(killer.getUUID());
            if (team.isPresent() && !team.equals(match.teamOf(victim.getUUID()))) {
                state.kills.merge(team.get().number(), 1, Integer::sum);
            }
        }
        return DeathResult.ELIMINATE;
    }

    /** The SkyWars chests found in an arena this game prepared. */
    public List<BlockPos> chests(Arena arena) {
        State state = states.get(arena);
        return state == null ? List.of() : List.copyOf(state.chests.keySet());
    }

    /** Kills credited to a team's members in this match. */
    public int kills(Match match, MatchTeam team) {
        State state = states.get(match.arena());
        return state == null ? 0 : state.kills.getOrDefault(team.number(), 0);
    }

    @Override
    public boolean dropsInventoryOnElimination() {
        return true;
    }

    @Override
    public void addSidebarLines(Match match, List<Component> lines) {
        lines.add(
                MatchSidebar.label("Players left: ", String.valueOf(match.alivePlayers().size())));
        lines.add(MatchSidebar.label("Next event: ", nextEvent(match)));
        if (match.arena() instanceof MapArena map) {
            lines.add(MatchSidebar.label("Map: ", titleCase(map.mapName())));
        }
    }

    private static String nextEvent(Match match) {
        int ticks = match.activeTicks();
        int first = match.settings().get(FIRST_REFILL) * 20;
        int second = match.settings().get(SECOND_REFILL) * 20;
        for (int refill : new int[] {first, second}) {
            if (refill > 0 && ticks < refill) {
                return "Refill " + MatchSidebar.countdown(refill - ticks);
            }
        }
        int limit = match.settings().get(GameSetting.TIME_LIMIT_MINUTES) * 60 * 20;
        if (limit > 0 && ticks < limit) {
            return "Game end " + MatchSidebar.countdown(limit - ticks);
        }
        return "None";
    }

    private static String titleCase(String id) {
        StringBuilder name = new StringBuilder();
        for (String word : id.split("_")) {
            if (!word.isEmpty()) {
                if (!name.isEmpty()) {
                    name.append(' ');
                }
                name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return name.toString();
    }

    @Override
    public Component sidebarTeamSuffix(Match match, MatchTeam team) {
        int kills = kills(match, team);
        return kills == 0
                ? Component.empty()
                : Component.literal(" " + kills + (kills == 1 ? " kill" : " kills"))
                        .withStyle(ChatFormatting.YELLOW);
    }

    private static final class State {
        /** Glass placed for the countdown cages. */
        private final List<BlockPos> cage = new ArrayList<>();

        /** Every SkyWars chest of the map and its loot table. */
        private final Map<BlockPos, ResourceKey<LootTable>> chests = new LinkedHashMap<>();

        /** Kills by team number. */
        private final Map<Integer, Integer> kills = new HashMap<>();
    }
}
