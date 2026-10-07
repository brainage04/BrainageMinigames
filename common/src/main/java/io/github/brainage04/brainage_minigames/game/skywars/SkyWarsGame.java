package io.github.brainage04.brainage_minigames.game.skywars;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.AntiJanitor;
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
import io.github.brainage04.brainage_minigames.storage.KitStorage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
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
 * SkyWars, played as Hypixel's Insane mode: every team starts in a glass cage above its own island
 * of a {@link MapArena}; the cages open when the countdown ends and each player gets their chosen
 * {@link SkyWarsKit} and active {@link SkyWarsPerk}s. Island and mid chests roll the mode's loot
 * tables at the start and at each refill. Falling into the void or dying eliminates; the last
 * team standing wins.
 */
public final class SkyWarsGame implements Minigame {
    public static final String ID = "skywars";
    public static final SkyWarsMode MODE = SkyWarsMode.INSANE;

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
    private static final String MID_MARKER = "skywars/mid";

    /** Kits bots pick from: armour and a weapon their combat brain uses straight away. */
    public static final List<String> BOT_KITS = List.of("armorer", "knight", "pro", "scout", "baseball_player",
            "speleologist", "pig_rider", "farmer", "salmon", "ecologist", "fallen_angel", "golem");

    private static final List<GameSetting> SETTINGS;

    static {
        List<GameSetting> all = new ArrayList<>(GameSetting.common(10, 9, true));
        all.add(AntiJanitor.SECONDS);
        all.add(FIRST_REFILL);
        all.add(SECOND_REFILL);
        SETTINGS = List.copyOf(all);
    }

    /** What the game prepared in each arena it opened; arenas are only held by their match. */
    private final Map<Arena, SkyWarsMatch> states = new WeakHashMap<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean antiJanitor() {
        return true;
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

    /** The SkyWars state of an active SkyWars match, or {@code null} for any other match. */
    public static @Nullable SkyWarsMatch state(Match match) {
        return match.game() instanceof SkyWarsGame game ? game.states.get(match.arena()) : null;
    }

    /**
     * Once the map is pasted, builds a glass cage at every spawn of every island and records the
     * map's SkyWars chests. Public so tests can prepare a specific map pasted into the test level.
     */
    public MapArena prepare(MapArena arena) {
        arena.whenPasted(() -> buildCagesAndFindChests(arena));
        return arena;
    }

    private void buildCagesAndFindChests(MapArena arena) {
        SkyWarsMatch state = new SkyWarsMatch(MODE);
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
                                    boolean mid = container.getLootTable().identifier().getPath().equals(MID_MARKER);
                                    state.chests.put(
                                            entity.getBlockPos().immutable(),
                                            mid ? SkyWarsMatch.ChestKind.MID : SkyWarsMatch.ChestKind.ISLAND);
                                }
                            }
                        });
        states.put(arena, state);
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
        SkyWarsMatch state = states.get(match.arena());
        if (state == null) {
            return;
        }
        state.match = match;
        ServerLevel level = match.arena().level();
        for (BlockPos pos : state.cage) {
            if (level.getBlockState(pos).is(Blocks.GLASS)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        state.cage.clear();
        MinecraftServer server = match.server();
        state.upgradedPerks = SkyWarsProgression.maxPerks(server);
        boolean ownKits = match.kit().equals(defaultKit());
        for (ServerPlayer player : match.alivePlayers()) {
            Set<String> active = new HashSet<>();
            for (SkyWarsPerk perk : SkyWarsPerk.of(state.mode)) {
                if (SkyWarsProgression.active(server, player.getUUID(), perk)) active.add(perk.id());
            }
            state.perks.put(player.getUUID(), active);
            if (ownKits) {
                SkyWarsKit kit = kitFor(match, player, state.mode);
                state.kits.put(player.getUUID(), kit);
                grant(state, player, kit);
            }
            SkyWarsPerks.start(state, player);
        }
        // Lobby players could open mid chests, so every chest starts from a fresh roll.
        fillChests(level, state, true);
        SkyWarsPerks.pledgeChests(state, match);
        match.broadcast(Component.literal("The cages opened!").withStyle(ChatFormatting.GOLD));
    }

    /** The player's saved kit; a bot without one picks one of {@link #BOT_KITS} it owns. */
    static SkyWarsKit kitFor(Match match, ServerPlayer player, SkyWarsMode mode) {
        MinecraftServer server = match.server();
        if (match.isBot(player.getUUID()) && !SkyWarsProgression.hasSelection(server, player.getUUID(), mode)) {
            List<SkyWarsKit> owned = new ArrayList<>();
            for (String id : BOT_KITS) {
                SkyWarsKit kit = SkyWarsKits.find(mode, id);
                if (kit != null && SkyWarsProgression.owns(server, player.getUUID(), kit)) owned.add(kit);
            }
            return owned.get(player.getRandom().nextInt(owned.size()));
        }
        return SkyWarsProgression.selectedKit(server, player.getUUID(), mode);
    }

    /** Replaces the player's inventory with the kit, wearing its armour. */
    static void grant(SkyWarsMatch state, ServerPlayer player, SkyWarsKit kit) {
        player.getInventory().clearContent();
        for (ItemStack item : kit.items(player.registryAccess(), player.getRandom())) {
            KitStorage.equipOrGive(player, item);
        }
        switch (kit.id()) {
            case "sloth" -> player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, MobEffectInstance.INFINITE_DURATION, 1));
            case "enderchest" -> {
                BlockPos pos = player.blockPosition().below();
                ServerLevel level = player.level();
                if (level.getBlockState(pos).isAir() || level.getBlockState(pos).is(Blocks.GLASS)) {
                    level.setBlock(pos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
                    state.chests.put(pos.immutable(), SkyWarsMatch.ChestKind.ISLAND);
                }
            }
            default -> {}
        }
    }

    @Override
    public void onRelease(Match match, ServerPlayer player) {
        SkyWarsPerks.release(player);
    }

    @Override
    public void onClose(Match match) {
        SkyWarsMatch state = states.remove(match.arena());
        if (state == null) {
            return;
        }
        state.friendlyMobs.keySet().forEach(Mob::discard);
        state.thrown.keySet().forEach(Projectile::discard);
    }

    @Override
    public void tick(Match match) {
        SkyWarsMatch state = states.get(match.arena());
        if (state == null || state.match == null) {
            return;
        }
        SkyWarsItems.tick(state, match);
        SkyWarsPerks.tick(state, match);
        int ticks = match.activeTicks();
        for (GameSetting refill : List.of(FIRST_REFILL, SECOND_REFILL)) {
            int at = match.settings().get(refill) * 20;
            if (at > 0 && ticks < at) {
                SkyWarsPerks.beforeRefill(state, match, at - ticks);
            }
            if (at > 0 && ticks == at) {
                refill(match);
            }
        }
    }

    /** Rolls every surviving chest's loot table into its empty slots, as a refill. */
    public void refill(Match match) {
        SkyWarsMatch state = states.get(match.arena());
        if (state == null) {
            return;
        }
        fillChests(match.arena().level(), state, false);
        SkyWarsPerks.refilled(state);
        match.broadcast(
                Component.literal("All chests were refilled!").withStyle(ChatFormatting.GOLD));
        for (ServerPlayer player : match.onlineMembers()) {
            player.playSound(SoundEvents.CHEST_OPEN, 1.0F, 1.0F);
        }
    }

    private static void fillChests(ServerLevel level, SkyWarsMatch state, boolean clear) {
        for (Map.Entry<BlockPos, SkyWarsMatch.ChestKind> chest : state.chests.entrySet()) {
            if (!(level.getBlockEntity(chest.getKey())
                    instanceof RandomizableContainerBlockEntity container)) {
                // Broken by a player.
                continue;
            }
            if (clear) {
                container.clearContent();
            }
            container.setLootTable(lootTable(state.mode, chest.getValue()), level.getRandom().nextLong());
            container.unpackLootTable(null);
        }
    }

    /** The loot table a chest of this kind rolls in this mode. */
    public static ResourceKey<LootTable> lootTable(SkyWarsMode mode, SkyWarsMatch.ChestKind kind) {
        return kind == SkyWarsMatch.ChestKind.MID ? mode.midLoot : mode.islandLoot;
    }

    @Override
    public DeathResult onDeath(Match match, ServerPlayer victim, @Nullable ServerPlayer killer) {
        SkyWarsMatch state = states.get(match.arena());
        if (state != null && killer != null && killer != victim) {
            Optional<MatchTeam> team = match.teamOf(killer.getUUID());
            if (team.isPresent() && !team.equals(match.teamOf(victim.getUUID()))) {
                state.kills.merge(team.get().number(), 1, Integer::sum);
                if (state.match != null && match.isAlive(killer.getUUID())) {
                    SkyWarsPerks.killed(state, match, killer, victim, victim.getY() < match.arena().voidY());
                }
            }
        }
        return DeathResult.ELIMINATE;
    }

    @Override
    public InteractionResult onUseItem(Match match, ServerPlayer player, InteractionHand hand, ItemStack stack) {
        SkyWarsMatch state = states.get(match.arena());
        return state == null || state.match == null
                ? InteractionResult.PASS
                : SkyWarsItems.use(state, match, player, hand, stack);
    }

    /** The SkyWars chests found in an arena this game prepared. */
    public List<BlockPos> chests(Arena arena) {
        SkyWarsMatch state = states.get(arena);
        return state == null ? List.of() : List.copyOf(state.chests.keySet());
    }

    /** The SkyWars chests of one kind found in an arena this game prepared. */
    public List<BlockPos> chests(Arena arena, SkyWarsMatch.ChestKind kind) {
        SkyWarsMatch state = states.get(arena);
        return state == null ? List.of() : state.chests.entrySet().stream()
                .filter(entry -> entry.getValue() == kind).map(Map.Entry::getKey).toList();
    }

    /** The kit a participant got when the cages opened, if the match used players' own kits. */
    public Optional<SkyWarsKit> kitOf(Match match, ServerPlayer player) {
        SkyWarsMatch state = states.get(match.arena());
        return state == null ? Optional.empty() : Optional.ofNullable(state.kits.get(player.getUUID()));
    }

    /** Kills credited to a team's members in this match. */
    public int kills(Match match, MatchTeam team) {
        SkyWarsMatch state = states.get(match.arena());
        return state == null ? 0 : state.kills.getOrDefault(team.number(), 0);
    }

    @Override
    public boolean dropsInventoryOnElimination() {
        return true;
    }

    @Override
    public void addSidebarLines(Match match, List<Component> lines) {
        lines.add(MatchSidebar.label("Mode: ", MODE.displayName));
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
}
