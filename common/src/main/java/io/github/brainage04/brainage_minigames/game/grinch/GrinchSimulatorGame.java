package io.github.brainage04.brainage_minigames.game.grinch;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.MatchSidebar;
import io.github.brainage04.brainage_minigames.game.MatchTeam;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.mixin.MapItemSavedDataAccess;
import io.github.brainage04.brainage_minigames.storage.KitStorage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.scores.Team;
import org.jspecify.annotations.Nullable;

/**
 * Grinch Simulator, after Hypixel's seasonal Arcade game as revised in 2020: every player is a
 * Grinch let loose in a snowy village for {@code time_limit_minutes} (4), stealing the presents
 * hidden in, on and around its houses. Right-clicking a present steals it for a point at once;
 * the player with the most presents when time runs out wins, and the game ends early once every
 * present is gone. Each match puts {@link #PRESENTS} presents at random among the map's present
 * spots, so no route can be learnt by heart, and gives every player a map of the village that
 * marks the presents still there and every player. Nobody can hurt, push or hinder anyone: the
 * original game's snowballs and punches were taken out in that revision.
 *
 * <p>Maps mark every place a present may be put with {@code point present}; see the README's
 * Grinch Simulator section.
 */
public final class GrinchSimulatorGame implements Minigame {
    public static final String ID = "grinch_simulator";

    public static final GameSetting PRESENTS =
            new GameSetting(
                    "presents",
                    150,
                    1,
                    1000,
                    "Presents hidden at random among the map's present spots when the match starts");

    private static final List<GameSetting> SETTINGS;

    static {
        List<GameSetting> all = new ArrayList<>(GameSetting.common(10, 4, true));
        all.add(PRESENTS);
        SETTINGS = List.copyOf(all);
    }

    /** Present skins (textures.minecraft.net hashes) the heads are given in turn. */
    private static final List<ResolvableProfile> PRESENT_SKINS =
            List.of(
                    "dc2f5c5be6b575016773735974c9ca8807e3025e01b0e676ca7147e00545b510",
                    "affb9f3aa1d1be96cace79574e72fb31b646cb6a2d19101ea127f920efe099",
                    "f39b4fc5986c025df4e411782bad88e5da0d7501ff2d99cdfc52226febe92d")
                    .stream()
                    .map(GrinchSimulatorGame::presentSkin)
                    .toList();

    /** Ticks between checks that every Grinch still has their map. */
    private static final int MAP_CHECK_TICKS = 20;

    /** Map ids by the centre they show, so arenas opened again reuse theirs. */
    private final Map<Long, MapId> mapIds = new HashMap<>();

    private final Map<Match, State> states = new HashMap<>();

    /** One match's presents and map. */
    private static final class State {
        /** Presents still to steal, in the order they were put down. */
        private final Set<BlockPos> presents = new LinkedHashSet<>();

        private final int placed;
        private final Map<UUID, Integer> stolen = new HashMap<>();
        private final MapId mapId;
        private final MapItemSavedData map;

        private State(List<BlockPos> presents, MapId mapId, MapItemSavedData map) {
            this.presents.addAll(presents);
            this.placed = presents.size();
            this.mapId = mapId;
            this.map = map;
        }
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Grinch Simulator";
    }

    @Override
    public List<GameSetting> settings() {
        return SETTINGS;
    }

    @Override
    public Identifier defaultKit() {
        return KitStorage.EMPTY_KIT;
    }

    @Override
    public GameType playerGameMode() {
        return GameType.ADVENTURE;
    }

    /** Free-for-all, as Grinch Simulator is played; teams add up their members' presents. */
    @Override
    public List<TeamLayout> layoutPresets(GameSettings settings) {
        return List.of("ffa", "1v1", "2v2", "3v3", "1v1v1v1").stream()
                .map(layout -> TeamLayout.parse(layout).orElseThrow())
                .toList();
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings settings) throws MatchException {
        return openArena(server, settings, TeamLayout.FREE_FOR_ALL);
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings settings, TeamLayout layout)
            throws MatchException {
        MapArena arena =
                MapArena.openRandom(server, ID, layout.isFreeForAll() ? 2 : layout.teamSizes().size());
        if (arena.points("present").isEmpty()) {
            arena.close();
            throw new MatchException("Map %s has no present spots.".formatted(arena.mapName()));
        }
        return arena;
    }

    @Override
    public void onStart(Match match) {
        MapArena arena = (MapArena) match.arena();
        ServerLevel level = arena.level();
        List<BlockPos> spots = new ArrayList<>();
        for (MapArena.Point point : arena.points("present")) {
            spots.add(BlockPos.containing(point.position()));
        }
        Collections.shuffle(spots, ThreadLocalRandom.current());
        List<BlockPos> presents =
                List.copyOf(spots.subList(0, Math.min(spots.size(), match.settings().get(PRESENTS))));
        MapItemSavedData map = drawMap(level, arena.bounds());
        long centre = BlockPos.asLong(map.centerX, 0, map.centerZ);
        MapId mapId = mapIds.computeIfAbsent(centre, unused -> level.getFreeMapId());
        level.setMapData(mapId, map);
        for (int index = 0; index < presents.size(); index++) {
            BlockPos pos = presents.get(index);
            putPresent(level, pos, PRESENT_SKINS.get(index % PRESENT_SKINS.size()));
            ((MapItemSavedDataAccess) map)
                    .brainage_minigames$addDecoration(
                            MapDecorationTypes.TARGET_POINT, level, key(pos), pos.getX() + 0.5, pos.getZ() + 0.5, 180.0, null);
        }
        State state = new State(presents, mapId, map);
        states.put(match, state);
        for (MatchTeam team : match.teams()) {
            // Grinches pass through each other: nobody can block a doorway or a chimney.
            team.scoreboardTeam().setCollisionRule(Team.CollisionRule.NEVER);
        }
        for (ServerPlayer player : match.alivePlayers()) {
            giveMap(player, state);
        }
        match.broadcast(
                Component.literal(
                                "Steal as many presents as you can! Right-click a present to take it;"
                                        + " your map shows the presents left.")
                        .withStyle(ChatFormatting.GREEN));
    }

    private static ResolvableProfile presentSkin(String hash) {
        String json =
                "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
        String value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        PropertyMap properties =
                new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", value)));
        UUID id = UUID.nameUUIDFromBytes(hash.getBytes(StandardCharsets.UTF_8));
        return ResolvableProfile.createResolved(new GameProfile(id, "Present", properties));
    }

    private static void putPresent(ServerLevel level, BlockPos pos, ResolvableProfile skin) {
        BlockState head =
                Blocks.PLAYER_HEAD
                        .defaultBlockState()
                        .setValue(SkullBlock.ROTATION, ThreadLocalRandom.current().nextInt(16));
        level.setBlock(pos, head, Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(pos) instanceof SkullBlockEntity skull) {
            skull.applyComponents(
                    DataComponentMap.builder().set(DataComponents.PROFILE, skin).build(),
                    DataComponentPatch.EMPTY);
            skull.setChanged();
            level.sendBlockUpdated(pos, head, head, Block.UPDATE_CLIENTS);
        }
    }

    private static String key(BlockPos pos) {
        return "present " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /**
     * A locked map of the arena, one block per pixel, centred on it: each column is the colour of
     * its highest visible block, shaded lighter or darker than the column north of it as vanilla
     * maps are.
     */
    private static MapItemSavedData drawMap(ServerLevel level, BoundingBox bounds) {
        int centerX = (bounds.minX() + bounds.maxX() + 1) / 2;
        int centerZ = (bounds.minZ() + bounds.maxZ() + 1) / 2;
        MapItemSavedData map =
                MapItemSavedDataAccess.brainage_minigames$create(
                        centerX, centerZ, (byte) 0, true, false, true, level.dimension());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int pixelX = 0; pixelX < 128; pixelX++) {
            int x = centerX - 64 + pixelX;
            if (x < bounds.minX() || x > bounds.maxX()) continue;
            int northHeight = Integer.MIN_VALUE;
            for (int pixelZ = 0; pixelZ < 128; pixelZ++) {
                int z = centerZ - 64 + pixelZ;
                if (z < bounds.minZ() || z > bounds.maxZ()) continue;
                int y = Math.min(bounds.maxY(), level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
                MapColor colour = MapColor.NONE;
                for (; y >= bounds.minY(); y--) {
                    cursor.set(x, y, z);
                    colour = level.getBlockState(cursor).getMapColor(level, cursor);
                    if (colour != MapColor.NONE) break;
                }
                if (colour == MapColor.NONE) continue;
                MapColor.Brightness brightness =
                        northHeight == Integer.MIN_VALUE || y == northHeight
                                ? MapColor.Brightness.NORMAL
                                : y > northHeight ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
                northHeight = y;
                map.setColor(pixelX, pixelZ, colour.getPackedId(brightness));
            }
        }
        return map;
    }

    /** The village map, in the first hotbar slot. */
    private static void giveMap(ServerPlayer player, State state) {
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        map.set(DataComponents.MAP_ID, state.mapId);
        map.set(DataComponents.CUSTOM_NAME, Component.literal("Village Map").withStyle(ChatFormatting.GREEN));
        player.getInventory().setItem(0, map);
    }

    private static boolean hasMap(ServerPlayer player, State state) {
        return player.getInventory().contains(stack -> state.mapId.equals(stack.get(DataComponents.MAP_ID)));
    }

    @Override
    public void tick(Match match) {
        State state = states.get(match);
        if (state == null) return;
        for (ServerPlayer player : match.alivePlayers()) {
            player.getFoodData().setFoodLevel(20);
        }
        if (match.activeTicks() % MAP_CHECK_TICKS == 0) {
            MapArena arena = (MapArena) match.arena();
            // A map thrown away comes back to its Grinch; the thrown one disappears.
            arena.level()
                    .getEntitiesOfClass(ItemEntity.class, AABB.of(arena.bounds()).inflate(4),
                            item -> state.mapId.equals(item.getItem().get(DataComponents.MAP_ID)))
                    .forEach(ItemEntity::discard);
            for (ServerPlayer player : match.alivePlayers()) {
                if (!hasMap(player, state)) giveMap(player, state);
                player.sendSystemMessage(
                        Component.literal("Presents: " + state.stolen.getOrDefault(player.getUUID(), 0))
                                .withStyle(ChatFormatting.GREEN)
                                .append(Component.literal("   Left: " + state.presents.size())
                                        .withStyle(ChatFormatting.YELLOW)),
                        true);
            }
        }
        if (state.presents.isEmpty()) {
            match.broadcast(Component.literal("Every present has been stolen!").withStyle(ChatFormatting.GOLD));
            match.finish(leaders(match));
        }
    }

    /** The teams with the most presents. */
    private static List<MatchTeam> leaders(Match match) {
        int best = match.teams().stream().mapToInt(MatchTeam::score).max().orElse(0);
        return match.teams().stream().filter(team -> team.score() == best).toList();
    }

    /** Right-clicking a present steals it; nothing else in the village can be used. */
    @Override
    public InteractionResult onUseBlock(
            Match match, ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        State state = states.get(match);
        if (state == null || !state.presents.remove(hit.getBlockPos())) return InteractionResult.FAIL;
        BlockPos pos = hit.getBlockPos();
        ServerLevel level = match.arena().level();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        ((MapItemSavedDataAccess) state.map).brainage_minigames$removeDecoration(key(pos));
        int stolen = state.stolen.merge(player.getUUID(), 1, Integer::sum);
        match.teamOf(player.getUUID()).ifPresent(team -> team.addScore(1));
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.2F);
        player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F, 1.0F);
        player.sendSystemMessage(
                Component.literal("+1 Present (" + stolen + ")").withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean allowDamage(Match match, ServerPlayer victim, DamageSource source) {
        return false;
    }

    @Override
    public boolean allowBreak(Match match, ServerPlayer player, BlockPos pos, BlockState state) {
        return false;
    }

    @Override
    public boolean allowPlace(Match match, ServerPlayer player, BlockPos pos, BlockState state) {
        return false;
    }

    @Override
    public DeathResult onDeath(Match match, ServerPlayer victim, @Nullable ServerPlayer killer) {
        return DeathResult.RESPAWN;
    }

    @Override
    public void onRespawn(Match match, ServerPlayer player) {
        State state = states.get(match);
        if (state != null) giveMap(player, state);
    }

    @Override
    public void onClose(Match match) {
        states.remove(match);
    }

    @Override
    public void addSidebarLines(Match match, List<Component> lines) {
        State state = states.get(match);
        if (state != null) {
            lines.add(MatchSidebar.label("Presents left: ", state.presents.size() + "/" + state.placed));
        }
    }

    /** Each player's (or team's) presents, so a free-for-all sidebar lists the standings. */
    @Override
    public Component sidebarTeamSuffix(Match match, MatchTeam team) {
        if (match.phase() != MatchPhase.ACTIVE && match.phase() != MatchPhase.ENDED) return Component.empty();
        return Component.literal(" " + team.score()).withStyle(ChatFormatting.YELLOW);
    }

    /** The presents still to steal in the match. */
    public List<BlockPos> presents(Match match) {
        State state = states.get(match);
        return state == null ? List.of() : List.copyOf(state.presents);
    }

    /** How many presents the player stole in the match. */
    public int stolen(Match match, UUID player) {
        State state = states.get(match);
        return state == null ? 0 : state.stolen.getOrDefault(player, 0);
    }

    /** The id of the match's village map. */
    public @Nullable MapId mapId(Match match) {
        State state = states.get(match);
        return state == null ? null : state.mapId;
    }

    /**
     * What a bot needs to play the match {@code player} is in, using only JDK and Minecraft types,
     * or null outside an active Grinch Simulator match: {@code "presents"} (List of BlockPos, the
     * presents still to steal) and {@code "stolen"} (Integer, the player's own presents).
     */
    public static @Nullable Map<String, Object> botView(ServerPlayer player) {
        Match match = MatchManager.activeMatch(player.getUUID());
        if (match == null || !(match.game() instanceof GrinchSimulatorGame game)) return null;
        State state = game.states.get(match);
        if (state == null) return null;
        return Map.of(
                "presents", List.copyOf(state.presents),
                "stolen", state.stolen.getOrDefault(player.getUUID(), 0));
    }
}
