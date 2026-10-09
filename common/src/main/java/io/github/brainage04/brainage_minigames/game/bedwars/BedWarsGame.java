package io.github.brainage04.brainage_minigames.game.bedwars;

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
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsLayout.Bed;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Cost;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Currency;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Entry;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Kind;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Prices;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsUpgrades.Trap;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsUpgrades.Upgrade;
import io.github.brainage04.brainage_minigames.game.bridge.BridgeGame;
import io.github.brainage04.brainage_minigames.mixin.DisplayAccess;
import io.github.brainage04.brainage_minigames.mixin.TextDisplayAccess;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Bed Wars, after Hypixel's: every team starts on its own island with a bed, an island generator
 * (iron and gold) and two shopkeepers. While its bed stands a killed player respawns after {@link
 * #RESPAWN_SECONDS}; once it is broken their next death is a final kill. Players spend iron, gold
 * and emeralds at the Item Shop and diamonds on team upgrades and traps, bridge to the diamond and
 * emerald generators and to the other islands, and break enemy beds. The diamond and emerald
 * generators gain tiers every {@link #EVENT_SECONDS}; after the fourth tier every remaining bed
 * breaks, and {@link #SUDDEN_DEATH_SECONDS} later Sudden Death sends ender dragons (in a two-team
 * match the border closes in instead). The last team with a player alive wins; at the time limit
 * the match is a draw. Only blocks placed during the match can be broken, and a bed only by the
 * players of another team.
 *
 * <p>The variants in {@link BedWarsMode} (Castle and the Dream modes) are this game with their own
 * rules on top. Maps are read by {@link BedWarsLayout}.
 */
public final class BedWarsGame implements Minigame {
    public static final GameSetting RESPAWN_SECONDS =
            new GameSetting("respawn_seconds", 5, 0, 60, "Seconds a killed player whose bed stands watches before respawning");
    public static final GameSetting EVENT_SECONDS =
            new GameSetting("event_seconds", 360, 1, 3600,
                    "Seconds between generator upgrades (Diamond II, Emerald II, Diamond III, Emerald III) and on to bed destruction");
    public static final GameSetting SUDDEN_DEATH_SECONDS =
            new GameSetting("sudden_death_seconds", 600, 1, 3600,
                    "Seconds from bed destruction until Sudden Death (dragons, or the closing border with two teams)");

    /** Entity tag of everything the game spawns into a match: shopkeepers, holograms, mobs and dragons. */
    public static final String ENTITY_TAG = "brainage_minigames:bedwars";
    private static final String TEAM_TAG = "brainage_minigames:bedwars_team=";

    /** Seconds between diamonds by tier (the wiki gives 30 at tier I; 23 and 12 are players' timings on the forum). */
    private static final int[] DIAMOND_SECONDS = {30, 23, 12};
    /** Seconds between emeralds by tier (the wiki gives 65 at tier I; 50 and 35 are this mod's choice). */
    private static final int[] EMERALD_SECONDS = {65, 50, 35};
    /**
     * Island generator: ticks between iron ingots at Forge 0. With Solo/Doubles prices one every 1.5
     * seconds, inside the 0.6 to 0.8 a second players measured on most Solo/Doubles maps; with bigger
     * teams one a second (this mod's choice: Hypixel's team maps run faster).
     */
    private static final int SOLO_IRON_TICKS = 30;
    private static final int IRON_TICKS = 20;
    /** Ticks between gold ingots at Forge 0; this mod's choice. */
    private static final int GOLD_TICKS = 160;
    /** Emeralds from an Emerald Forge or better, one a minute; this mod's choice. */
    private static final int FORGE_EMERALD_TICKS = 1200;
    /** The most iron and gold that lie on an island generator; this mod's choice. */
    private static final int IRON_CAP = 48;
    private static final int GOLD_CAP = 16;
    private static final int FORGE_EMERALD_CAP = 4;
    /** Entity tag of an island generator's iron or gold, which its team's players standing on it all pick up. */
    private static final String FORGE_ITEM_TAG = "brainage_minigames:bedwars_forge_item";
    /** Ticks between two traps of one team going off; this mod's choice. */
    private static final int TRAP_COOLDOWN = 10 * 20;
    private static final int MAGIC_MILK_TICKS = 30 * 20;
    /** Demolition's Creeper Egg: how long its creeper hunts, and its blast; this mod's choice. */
    private static final int CREEPER_TICKS = 30 * 20;
    private static final float CREEPER_POWER = 3.0F;
    private static final int TNT_FUSE = 52;
    private static final int GOLEM_TICKS = 4 * 60 * 20;
    private static final int SILVERFISH_TICKS = 15 * 20;
    private static final int BRIDGE_EGG_TICKS = 30;
    private static final int FIREBALL_COOLDOWN = 10;
    private static final float FIREBALL_POWER = 2.0F;
    private static final float TNT_POWER = 4.0F;
    /** Blocks the shopkeepers and generators keep clear of placed blocks around them. */
    private static final double KEEP_CLEAR = 1.5;

    /** Swappage: the shortest and longest time between swaps (Hypixel's are random; one to two minutes is this mod's choice). */
    public static final GameSetting SWAP_MIN_SECONDS =
            new GameSetting("swap_min_seconds", 60, 1, 600, "Swappage: fewest seconds between two swaps");
    public static final GameSetting SWAP_MAX_SECONDS =
            new GameSetting("swap_max_seconds", 120, 1, 600, "Swappage: most seconds between two swaps");

    private final List<GameSetting> settings;

    private final String id;
    private final String displayName;
    private final BedWarsMode mode;
    private final List<String> layouts;
    final Map<Match, State> states = new HashMap<>();

    public BedWarsGame(String id, String displayName, BedWarsMode mode, List<String> layouts) {
        this.id = id;
        this.displayName = displayName;
        this.mode = mode;
        this.layouts = layouts;
        List<GameSetting> all = new ArrayList<>(GameSetting.common(10, 50, true));
        all.add(RESPAWN_SECONDS);
        all.add(EVENT_SECONDS);
        all.add(SUDDEN_DEATH_SECONDS);
        if (mode == BedWarsMode.SWAPPAGE) {
            all.add(SWAP_MIN_SECONDS);
            all.add(SWAP_MAX_SECONDS);
        }
        this.settings = List.copyOf(all);
    }

    @Override
    public Optional<String> validate(GameSettings values) {
        if (mode == BedWarsMode.SWAPPAGE && values.get(SWAP_MIN_SECONDS) > values.get(SWAP_MAX_SECONDS)) {
            return Optional.of("swap_min_seconds must not be more than swap_max_seconds.");
        }
        return Optional.empty();
    }

    public BedWarsMode mode() {
        return mode;
    }

    /** The generator events, in order. */
    enum Event {
        DIAMOND_II("Diamond II"),
        EMERALD_II("Emerald II"),
        DIAMOND_III("Diamond III"),
        EMERALD_III("Emerald III"),
        BED_DESTRUCTION("Bed Destruction"),
        SUDDEN_DEATH("Sudden Death"),
        GAME_END("Game End");

        final String title;

        Event(String title) {
            this.title = title;
        }
    }

    /** What each player has bought that outlives a death. */
    static final class PlayerState {
        /** 0 leather, 1 chainmail, 2 iron, 3 diamond leggings and boots. */
        int armor;
        /** 0 none, else the tier of the pickaxe (1 wooden to 4 diamond). */
        int pickaxe;
        int axe;
        boolean shears;
        int kills;
        int finalKills;
        int bedsBroken;
        /** How many of each limited item (by id) the player bought. */
        final Map<String, Integer> purchases = new HashMap<>();
    }

    /** A team's beds, upgrades and traps. */
    static final class TeamState {
        final int number;
        final List<Bed> beds;
        final Set<Bed> standing = new HashSet<>();
        final Map<Upgrade, Integer> upgrades = new EnumMap<>(Upgrade.class);
        final ArrayDeque<Trap> traps = new ArrayDeque<>();
        int trapCooldown;
        /** Castle: how many times the first trap in the queue went off. */
        int trapUses;
        boolean eliminated;

        TeamState(int number, List<Bed> beds) {
            this.number = number;
            this.beds = beds;
        }

        int level(Upgrade upgrade) {
            return upgrades.getOrDefault(upgrade, 0);
        }

        boolean bedStanding() {
            return !standing.isEmpty();
        }
    }

    /** A diamond or emerald generator. */
    static final class Generator {
        final Currency currency;
        final Vec3 position;
        int tier = 1;
        int timer;
        Display.@Nullable TextDisplay hologram;

        Generator(Currency currency, Vec3 position) {
            this.currency = currency;
            this.position = position;
        }

        int interval() {
            int[] seconds = currency == Currency.DIAMOND ? DIAMOND_SECONDS : EMERALD_SECONDS;
            return seconds[Math.clamp(tier, 1, 3) - 1] * 20;
        }
    }

    /** A team's island generator. */
    static final class Forge {
        final int team;
        final Vec3 position;
        int iron;
        int gold;
        int emerald;

        Forge(int team, Vec3 position) {
            this.team = team;
            this.position = position;
            // The first iron and gold come at once.
            this.iron = SOLO_IRON_TICKS;
            this.gold = GOLD_TICKS;
        }
    }

    /** What a shopkeeper sells. */
    enum Shopkeeper {
        ITEMS("ITEM SHOP"),
        UPGRADES("TEAM UPGRADES"),
        BANKER("BANKER"),
        STREAKS("STREAK POWERS"),
        /** Lucky Blocks' Jerry, who trades Miracle Lucky Blocks for emeralds, and its Resource Trader. */
        JERRY(BedWarsLucky.JERRY),
        TRADER(BedWarsLucky.TRADER);

        final String title;

        Shopkeeper(String title) {
            this.title = title;
        }
    }

    /** Something thrown or summoned that the game follows: a fireball, bridge egg, bedbug, TNT, golem or silverfish. */
    record Tracked(Entity entity, String kind, int team, @Nullable UUID owner, long expires) {}

    /** Per-match state. */
    static final class State {
        final BedWarsLayout layout;
        final Prices prices;
        final Map<Integer, TeamState> teams = new LinkedHashMap<>();
        final Map<UUID, PlayerState> players = new HashMap<>();
        final Map<UUID, Integer> respawning = new LinkedHashMap<>();
        final Map<UUID, Long> milk = new HashMap<>();
        final List<Generator> generators = new ArrayList<>();
        final List<Forge> forges = new ArrayList<>();
        final List<Entity> spawned = new ArrayList<>();
        final List<Tracked> tracked = new ArrayList<>();
        final Map<Entity, Vec3> lastPositions = new HashMap<>();
        final Map<UUID, Shopkeeper> shopkeepers = new HashMap<>();
        /** Where each shopkeeper stands; players bumping into one never move it. */
        final Map<Entity, Vec3> posts = new HashMap<>();
        /** What a killed player who keeps their resources (Kangaroo) gets back when they respawn. */
        final Map<UUID, List<ItemStack>> kept = new HashMap<>();
        /** Players whose armour the others are shown without, while they are invisible. */
        final Set<UUID> hidden = new HashSet<>();
        /** Rush: players who turned the expanding bridges off. */
        final Set<UUID> rushBridgingOff = new HashSet<>();
        /** A countdown the mode keeps: Swappage's next swap. */
        int modeTimer;
        /** Castle's banks, streak points and powers. */
        BedWarsCastle.@Nullable CastleState castle;
        /** Ultimate's chosen ultimates and their cooldowns. */
        final Map<UUID, BedWarsUltimates.Chosen> ultimates = new HashMap<>();
        /** Armed's guns: rounds left and reloads under way, per player. */
        final Map<UUID, BedWarsGuns.Magazine> magazines = new HashMap<>();
        /** The enemy team each player's compass tracks, until they die. */
        final Map<UUID, Integer> tracking = new HashMap<>();
        /** The rotating items this match sells: those of the week it started in. */
        List<String> rotation = List.of();
        /** Where Mega TNT is being placed, between the placement check and the placed block. */
        final Set<BlockPos> megaTnt = new HashSet<>();
        /** Lucky Blocks' lucky traps, by the block they lie on. */
        final Map<BlockPos, BedWarsLucky.LuckyTrap> luckyTraps = new HashMap<>();
        /** Lucky Blocks' timed effects on players (Blitz effects), by player and name, until a match tick. */
        final Map<UUID, Map<String, Integer>> luckyBuffs = new HashMap<>();
        /** Lucky Blocks' effects that happen later. */
        final List<BedWarsLucky.Timer> luckyTimers = new ArrayList<>();
        /** Lucky Blocks' gold and emerald blocks a lucky block turned into, which break into resources. */
        final Set<BlockPos> luckyTransforms = new HashSet<>();
        final Vec3 center;
        final double halfSize;
        int nextEvent;
        boolean bedsGone;
        boolean suddenDeath;
        int suddenDeathTicks;

        State(BedWarsLayout layout, Prices prices, Vec3 center, double halfSize) {
            this.layout = layout;
            this.prices = prices;
            this.center = center;
            this.halfSize = halfSize;
        }

        PlayerState player(UUID id) {
            return players.computeIfAbsent(id, ignored -> new PlayerState());
        }
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
    public String mapDirectory() {
        return mode.mapDirectory;
    }

    @Override
    public List<GameSetting> settings() {
        return settings;
    }

    @Override
    public Identifier defaultKit() {
        return BrainageMinigames.id("kits/bedwars");
    }

    @Override
    public GameType playerGameMode() {
        return GameType.SURVIVAL;
    }

    @Override
    public List<TeamLayout> layoutPresets(GameSettings settings) {
        return layouts.stream().map(layout -> TeamLayout.parse(layout).orElseThrow()).toList();
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings settings) throws MatchException {
        return MapArena.openRandom(server, mode.mapDirectory, 2);
    }

    /**
     * The map with the fewest bases that still holds every team, as Hypixel plays Solo and Doubles on
     * eight-team maps, 3v3v3v3 and 4v4v4v4 on four-team maps and 4v4 on two-team maps; a map a player
     * picked must hold them too. Free-for-all has no beds to defend.
     */
    @Override
    public Arena openArena(MinecraftServer server, GameSettings settings, TeamLayout layout) throws MatchException {
        if (layout.isFreeForAll()) {
            throw new MatchException(displayName + " is played in teams, such as Solo (" + TeamLayout.teamsOf(1, 8) + ").");
        }
        int teams = layout.teamSizes().size();
        MapArena arena;
        if (MapArena.hasChosenMap()) {
            arena = MapArena.openRandom(server, mode.mapDirectory, teams);
        } else {
            ServerLevel level = server.getLevel(ModDimensions.MINIGAMES);
            if (level == null) throw new MatchException("The minigames dimension is unavailable.");
            List<Identifier> maps = MapArena.maps(server, mode.mapDirectory);
            int fewest = maps.stream().mapToInt(map -> MapArena.teamSlots(server, map)).filter(slots -> slots >= teams)
                    .min().orElseThrow(() -> new MatchException("No %s map has bases for %d teams.".formatted(displayName, teams)));
            List<Identifier> fitting = maps.stream().filter(map -> MapArena.teamSlots(server, map) == fewest).sorted().toList();
            arena = MapArena.reserve(level, fitting.get(ThreadLocalRandom.current().nextInt(fitting.size())));
        }
        try {
            BedWarsLayout.read(arena, Math.min(teams, arena.teamSlots()), mode.shops());
        } catch (MatchException exception) {
            arena.close();
            throw exception;
        }
        return arena;
    }

    /** Solo/Doubles prices for teams of up to two, 3v3v3v3/4v4v4v4 prices for bigger teams, Castle's for Castle. */
    static Prices prices(BedWarsMode mode, TeamLayout layout) {
        if (mode == BedWarsMode.CASTLE) return Prices.CASTLE;
        int largest = layout.teamSizes().stream().mapToInt(Integer::intValue).max().orElse(1);
        return largest <= 2 ? Prices.SOLO : Prices.TEAMS;
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onStart(Match match) {
        MapArena arena = (MapArena) match.arena();
        BedWarsLayout layout;
        try {
            layout = BedWarsLayout.read(arena, match.teams().size(), mode.shops());
        } catch (MatchException exception) {
            throw new IllegalStateException("The map was checked when the match opened.", exception);
        }
        var bounds = arena.bounds();
        Vec3 center = new Vec3((bounds.minX() + bounds.maxX() + 1) / 2.0, bounds.minY(), (bounds.minZ() + bounds.maxZ() + 1) / 2.0);
        double half = Math.max(bounds.getXSpan(), bounds.getZSpan()) / 2.0;
        State state = new State(layout, prices(mode, match.layout()), center, half);
        states.put(match, state);
        ServerLevel level = arena.level();
        for (MatchTeam team : match.teams()) {
            TeamState teamState = new TeamState(team.number(), new ArrayList<>(layout.beds().getOrDefault(team.number(), List.of())));
            state.teams.put(team.number(), teamState);
            if (team.members().isEmpty()) {
                teamState.eliminated = true;
                continue;
            }
            DyeColor dye = dye(team);
            for (Bed bed : teamState.beds) {
                placeBed(level, bed, dye);
                teamState.standing.add(bed);
            }
            for (MapArena.Point point : layout.shops().getOrDefault(team.number(), List.of())) {
                spawnShopkeeper(match, state, point, team.number(), Shopkeeper.ITEMS);
            }
            for (MapArena.Point point : layout.upgrades().getOrDefault(team.number(), List.of())) {
                spawnShopkeeper(match, state, point, team.number(), Shopkeeper.UPGRADES);
            }
            for (Vec3 forge : layout.forges().getOrDefault(team.number(), List.of())) {
                state.forges.add(new Forge(team.number(), forge));
            }
        }
        for (Vec3 diamond : layout.diamonds()) state.generators.add(new Generator(Currency.DIAMOND, diamond));
        for (Vec3 emerald : layout.emeralds()) state.generators.add(new Generator(Currency.EMERALD, emerald));
        for (Generator generator : state.generators) {
            generator.timer = generator.interval();
            generator.hologram = hologram(level, generator.position.add(0, 2.5, 0));
            state.spawned.add(generator.hologram);
            label(generator);
        }
        state.rotation = BedWarsRotation.current(java.time.Instant.now());
        mode.onStart(this, match, state);
        for (ServerPlayer player : match.alivePlayers()) equip(match, state, player);
    }

    @Override
    public void onClose(Match match) {
        State state = states.remove(match);
        if (state == null) return;
        for (Entity entity : state.spawned) entity.discard();
        for (Tracked tracked : state.tracked) tracked.entity().discard();
        ServerLevel level = match.arena().level();
        AABB area = AABB.of(((MapArena) match.arena()).bounds()).inflate(16);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, area, entity -> entity.entityTags().contains(ENTITY_TAG)
                || entity instanceof ItemEntity || entity instanceof PrimedTnt || entity instanceof Projectile)) {
            entity.discard();
        }
    }

    @Override
    public void onRelease(Match match, ServerPlayer player) {
        State state = states.get(match);
        if (state == null) return;
        state.respawning.remove(player.getUUID());
        state.milk.remove(player.getUUID());
        BedWarsLucky.release(player);
    }

    @Override
    public void tick(Match match) {
        State state = states.get(match);
        if (state == null) return;
        int now = match.activeTicks();
        tickEvents(match, state, now);
        if (match.phase() != MatchPhase.ACTIVE) return;
        tickRespawns(match, state);
        tickGenerators(match, state);
        tickTracked(match, state, now);
        BedWarsTracker.tick(match, state, now);
        if (now % 10 == 0) {
            for (ServerPlayer player : match.alivePlayers()) {
                if (player.isSpectator()) continue;
                player.getFoodData().setFoodLevel(20);
                applyUpgrades(match, state, player);
            }
            tickTraps(match, state, now);
        }
        if (now % 2 == 0) tickInvisibility(match, state);
        if (now % 20 == 0) {
            state.posts.forEach((shopkeeper, post) -> {
                if (shopkeeper.position().distanceToSqr(post) > 0.01) shopkeeper.teleportTo(post.x, post.y, post.z);
            });
            tickBorder(match, state);
            announceEliminations(match, state);
        }
        mode.tick(this, match, state, now);
    }

    // ---------------------------------------------------------------- events

    /** The tick of {@code event} since the match began. */
    int eventTick(Match match, Event event) {
        int step = match.settings().get(EVENT_SECONDS) * 20;
        return switch (event) {
            case DIAMOND_II -> step;
            case EMERALD_II -> 2 * step;
            case DIAMOND_III -> 3 * step;
            case EMERALD_III -> 4 * step;
            case BED_DESTRUCTION -> 5 * step;
            case SUDDEN_DEATH -> 5 * step + match.settings().get(SUDDEN_DEATH_SECONDS) * 20;
            case GAME_END -> match.settings().get(GameSetting.TIME_LIMIT_MINUTES) * 60 * 20;
        };
    }

    private void tickEvents(Match match, State state, int now) {
        Event[] events = Event.values();
        while (state.nextEvent < events.length - 1 && now >= eventTick(match, events[state.nextEvent])) {
            Event event = events[state.nextEvent++];
            run(match, state, event);
            if (match.phase() != MatchPhase.ACTIVE) return;
        }
    }

    private void run(Match match, State state, Event event) {
        switch (event) {
            case DIAMOND_II, DIAMOND_III -> upgradeGenerators(match, state, Currency.DIAMOND, event == Event.DIAMOND_II ? 2 : 3);
            case EMERALD_II, EMERALD_III -> upgradeGenerators(match, state, Currency.EMERALD, event == Event.EMERALD_II ? 2 : 3);
            case BED_DESTRUCTION -> {
                state.bedsGone = true;
                for (TeamState team : state.teams.values()) {
                    for (Bed bed : List.copyOf(team.standing)) removeBed(match.arena().level(), bed);
                    team.standing.clear();
                }
                title(match.onlineMembers(), Component.literal("Bed Destruction").withStyle(ChatFormatting.RED),
                        Component.literal("All beds have been destroyed!").withStyle(ChatFormatting.WHITE));
                match.broadcast(Component.literal("BED DESTRUCTION > ").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                        .append(Component.literal("All beds have been destroyed!").withStyle(ChatFormatting.RED)));
            }
            case SUDDEN_DEATH -> {
                state.suddenDeath = true;
                if (match.teams().size() == 2 && mode != BedWarsMode.CASTLE) {
                    match.broadcast(Component.literal("SUDDEN DEATH > ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                            .append(Component.literal("The border is closing in!").withStyle(ChatFormatting.WHITE)));
                } else {
                    spawnDragons(match, state);
                    match.broadcast(Component.literal("SUDDEN DEATH > ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                            .append(Component.literal("The dragons have arrived!").withStyle(ChatFormatting.WHITE)));
                }
                title(match.onlineMembers(), Component.literal("Sudden Death").withStyle(ChatFormatting.RED), Component.empty());
            }
            case GAME_END -> {}
        }
    }

    private void upgradeGenerators(Match match, State state, Currency currency, int tier) {
        for (Generator generator : state.generators) {
            if (generator.currency == currency && generator.tier < tier) {
                generator.tier = tier;
                generator.timer = Math.min(generator.timer, generator.interval());
            }
        }
        match.broadcast(Component.literal(currency.displayName + " Generators").withStyle(currency.color)
                .append(Component.literal(" have been upgraded to Tier ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(BedWarsUpgrades.roman(tier)).withStyle(ChatFormatting.RED)));
    }

    /** The next event and the ticks until it, or empty once none is left. */
    Optional<Map.Entry<Event, Integer>> nextEvent(Match match, State state) {
        Event[] events = Event.values();
        if (state.nextEvent >= events.length - 1) return Optional.empty();
        Event event = events[state.nextEvent];
        if (event == Event.SUDDEN_DEATH && match.teams().size() == 2 && mode != BedWarsMode.CASTLE) {
            return Optional.of(Map.entry(event, eventTick(match, event) - match.activeTicks()));
        }
        return Optional.of(Map.entry(event, eventTick(match, event) - match.activeTicks()));
    }

    // ---------------------------------------------------------------- generators

    private void tickGenerators(Match match, State state) {
        ServerLevel level = match.arena().level();
        boolean teamsPrices = state.prices != Prices.SOLO;
        for (Generator generator : state.generators) {
            if (--generator.timer <= 0) {
                generator.timer = generator.interval();
                int cap = generator.currency == Currency.DIAMOND ? (teamsPrices ? 8 : 4) : (teamsPrices ? 5 : 2);
                if (drop(level, generator.position, generator.currency.item, cap)) {
                    mode.onDropped(this, match, state, generator.position, generator.currency);
                }
            }
            if (generator.timer % 20 == 0) label(generator);
        }
        for (Forge forge : state.forges) {
            splitForge(match, level, forge);
            TeamState team = state.teams.get(forge.team);
            if (team == null || team.eliminated) continue;
            int level2 = team.level(Upgrade.FORGE);
            double speed = switch (level2) {
                case 0 -> 1.0;
                case 1 -> 1.5;
                case 2, 3 -> 2.0;
                default -> 3.0;
            } * mode.forgeSpeed;
            int ironTicks = state.prices == Prices.SOLO ? SOLO_IRON_TICKS : IRON_TICKS;
            if (++forge.iron >= ironTicks / speed) {
                forge.iron = 0;
                if (dropAtForge(level, forge.position, Items.IRON_INGOT, IRON_CAP)) {
                    mode.onDropped(this, match, state, forge.position, Currency.IRON);
                } else {
                    overflow(match, state, forge, Currency.IRON);
                }
            }
            if (++forge.gold >= GOLD_TICKS / speed) {
                forge.gold = 0;
                if (dropAtForge(level, forge.position, Items.GOLD_INGOT, GOLD_CAP)) {
                    mode.onDropped(this, match, state, forge.position, Currency.GOLD);
                } else {
                    overflow(match, state, forge, Currency.GOLD);
                }
            }
            if (level2 >= 3 && ++forge.emerald >= FORGE_EMERALD_TICKS) {
                forge.emerald = 0;
                drop(level, forge.position, Items.EMERALD, FORGE_EMERALD_CAP);
            }
        }
    }

    /**
     * Hands out the island generator's iron and gold as Hypixel does: every player of its team standing
     * on it picks up the whole of it, each their own copy; an enemy standing there alone takes it as
     * usual. Vanilla never picks these up (they never merge with thrown items either, so nothing
     * dropped onto a generator is copied).
     */
    private void splitForge(Match match, ServerLevel level, Forge forge) {
        AABB area = new AABB(forge.position, forge.position).inflate(1.5, 1.0, 1.5);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, entity -> entity.entityTags().contains(FORGE_ITEM_TAG))) {
            List<ServerPlayer> reaching = new ArrayList<>();
            for (ServerPlayer player : match.alivePlayers()) {
                if (!player.isSpectator() && player.isAlive() && player.getBoundingBox().inflate(1.0, 0.5, 1.0).intersects(item.getBoundingBox())) {
                    reaching.add(player);
                }
            }
            if (reaching.isEmpty()) continue;
            List<ServerPlayer> team = reaching.stream().filter(player -> teamOf(match, player) == forge.team).toList();
            List<ServerPlayer> takers = team.isEmpty() ? List.of(reaching.getFirst()) : team;
            ItemStack stack = item.getItem();
            int left = 0;
            for (ServerPlayer taker : takers) {
                ItemStack copy = stack.copy();
                int count = copy.getCount();
                taker.getInventory().add(copy);
                if (copy.getCount() < count) taker.take(item, count - copy.getCount());
                left = Math.max(left, copy.getCount());
            }
            if (left == 0) {
                item.discard();
            } else {
                stack.setCount(Math.min(stack.getCount(), left));
                item.setItem(stack);
            }
        }
    }

    /**
     * As {@link #drop}, for an island generator's iron and gold: they gather in one stack per item that
     * vanilla never picks up, for {@link #splitForge} to hand out.
     */
    private static boolean dropAtForge(ServerLevel level, Vec3 position, Item item, int cap) {
        AABB area = new AABB(position, position).inflate(1.5, 1.0, 1.5);
        int lying = 0;
        ItemEntity pile = null;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area, entity -> entity.getItem().is(item))) {
            lying += entity.getItem().getCount();
            if (pile == null && entity.entityTags().contains(FORGE_ITEM_TAG) && entity.getItem().getCount() < entity.getItem().getMaxStackSize()) {
                pile = entity;
            }
        }
        if (lying >= cap) return false;
        if (pile != null) {
            ItemStack grown = pile.getItem().copy();
            grown.grow(1);
            pile.setItem(grown);
            return true;
        }
        ItemEntity entity = new ItemEntity(level, position.x, position.y + 0.1, position.z, new ItemStack(item), 0.0, 0.0, 0.0);
        entity.setNeverPickUp();
        entity.setUnlimitedLifetime();
        entity.addTag(FORGE_ITEM_TAG);
        level.addFreshEntity(entity);
        return true;
    }

    /** A full island generator's resource goes to the team's Banker in Castle. */
    private void overflow(Match match, State state, Forge forge, Currency currency) {
        if (state.castle != null) state.castle.bank(forge.team, currency, 1);
    }

    /** Drops one {@code item} at {@code position} unless {@code cap} of it already lie there; returns whether it did. */
    private static boolean drop(ServerLevel level, Vec3 position, Item item, int cap) {
        AABB area = new AABB(position, position).inflate(1.5, 1.0, 1.5);
        int lying = 0;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area, entity -> entity.getItem().is(item))) {
            lying += entity.getItem().getCount();
        }
        if (lying >= cap) return false;
        ItemEntity entity = new ItemEntity(level, position.x, position.y + 0.1, position.z, new ItemStack(item), 0.0, 0.0, 0.0);
        entity.setPickUpDelay(0);
        level.addFreshEntity(entity);
        return true;
    }

    private static Display.TextDisplay hologram(ServerLevel level, Vec3 position) {
        Display.TextDisplay hologram = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        hologram.snapTo(position.x, position.y, position.z);
        ((DisplayAccess) hologram).brainage_minigames$setBillboardConstraints(Display.BillboardConstraints.CENTER);
        hologram.addTag(ENTITY_TAG);
        level.addFreshEntity(hologram);
        return hologram;
    }

    private static void label(Generator generator) {
        if (generator.hologram == null) return;
        ((TextDisplayAccess) generator.hologram).brainage_minigames$setText(Component.empty()
                .append(Component.literal("Tier " + BedWarsUpgrades.roman(generator.tier) + "\n").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(generator.currency.displayName + "\n").withStyle(generator.currency.color, ChatFormatting.BOLD))
                .append(Component.literal("Spawns in ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(String.valueOf((generator.timer + 19) / 20)).withStyle(ChatFormatting.RED))
                .append(Component.literal(" seconds").withStyle(ChatFormatting.YELLOW)));
    }

    // ---------------------------------------------------------------- beds

    static void placeBed(ServerLevel level, Bed bed, DyeColor dye) {
        BlockState foot = Blocks.BED.pick(dye).defaultBlockState().setValue(BedBlock.FACING, bed.facing())
                .setValue(BedBlock.PART, BedPart.FOOT);
        level.setBlock(bed.foot(), foot, Block.UPDATE_CLIENTS);
        level.setBlock(bed.head(), foot.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_CLIENTS);
    }

    private static void removeBed(ServerLevel level, Bed bed) {
        level.setBlock(bed.head(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(bed.foot(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static @Nullable Bed bedAt(State state, BlockPos pos) {
        for (TeamState team : state.teams.values()) {
            for (Bed bed : team.beds) if (bed.covers(pos)) return bed;
        }
        return null;
    }

    /** Whether {@code pos} is half of any team's bed, standing or not. */
    static boolean isBed(State state, BlockPos pos) {
        return bedAt(state, pos) != null;
    }

    /** {@code breaker} breaks {@code bed}: it is gone, and its team respawns no more once none of its beds stands. */
    void breakBed(Match match, State state, Bed bed, @Nullable ServerPlayer breaker) {
        TeamState team = state.teams.get(bed.team());
        if (team == null || !team.standing.remove(bed)) return;
        ServerLevel level = match.arena().level();
        removeBed(level, bed);
        level.playSound(null, bed.foot(), SoundEvents.ENDER_DRAGON_GROWL, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        if (breaker != null) state.player(breaker.getUUID()).bedsBroken++;
        MutableComponent bedName = bedName(match, bed);
        match.broadcast(Component.literal("BED DESTRUCTION > ").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                .append(bedName).append(Component.literal(" was destroyed by ").withStyle(ChatFormatting.GRAY))
                .append(breaker == null ? Component.literal("someone") : name(match, breaker.getUUID()))
                .append(Component.literal("!").withStyle(ChatFormatting.GRAY)));
        List<ServerPlayer> owners = members(match, bed.team());
        if (team.bedStanding()) {
            title(owners, Component.literal("BED DESTROYED!").withStyle(ChatFormatting.RED),
                    Component.empty().append(bedName).append(Component.literal(" is gone!").withStyle(ChatFormatting.WHITE)));
        } else {
            title(owners, Component.literal("BED DESTROYED!").withStyle(ChatFormatting.RED),
                    Component.literal("You will no longer respawn!").withStyle(ChatFormatting.WHITE));
        }
        for (ServerPlayer player : match.onlineMembers()) {
            if (!owners.contains(player)) player.playSound(SoundEvents.WITHER_DEATH, 0.6F, 1.0F);
        }
        mode.onBedBroken(this, match, state, bed, breaker);
    }

    private static MutableComponent bedName(Match match, Bed bed) {
        MutableComponent team = teamName(match, bed.team());
        return bed.name().isEmpty() ? team.append(Component.literal(" Bed").withStyle(ChatFormatting.GRAY))
                : team.append(Component.literal(" " + capitalise(bed.name()) + " Bed").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean allowBreak(Match match, ServerPlayer player, BlockPos pos, BlockState block) {
        State state = states.get(match);
        if (state == null) return false;
        Boolean moded = mode.allowBreak(this, match, state, player, pos, block);
        if (moded != null) return moded;
        Bed bed = bedAt(state, pos);
        if (bed != null) {
            if (teamOf(match, player) == bed.team()) {
                player.sendSystemMessage(Component.literal("You can't destroy your own bed!").withStyle(ChatFormatting.RED), true);
            } else if (state.teams.get(bed.team()).standing.contains(bed)) {
                breakBed(match, state, bed, player);
            }
            return false;
        }
        return match.isPlacedBlock(pos);
    }

    @Override
    public boolean allowPlace(Match match, ServerPlayer player, BlockPos pos, BlockState block) {
        State state = states.get(match);
        if (state == null || !match.arena().canBuild(pos) || bedAt(state, pos) != null) return false;
        if (block.is(Blocks.OBSIDIAN) && !mode.obsidian(match)) return false;
        Vec3 center = Vec3.atBottomCenterOf(pos);
        for (Generator generator : state.generators) if (keptClear(generator.position, center)) return refuse(player);
        for (Forge forge : state.forges) if (keptClear(forge.position, center)) return refuse(player);
        for (Entity shopkeeper : state.spawned) {
            if (shopkeeper instanceof Villager && shopkeeper.isAlive() && keptClear(shopkeeper.position(), center)) return refuse(player);
        }
        if (!mode.allowPlace(this, match, state, player, pos, block)) return false;
        // Vanilla places the main hand's TNT before the offhand's, so offhand Mega TNT counts only without TNT in the main hand.
        if (block.is(Blocks.TNT) && (BedWarsShop.ability(player.getMainHandItem()).equals("mega_tnt")
                || !player.getMainHandItem().is(Items.TNT) && BedWarsShop.ability(player.getOffhandItem()).equals("mega_tnt"))) {
            state.megaTnt.add(pos.immutable());
        }
        return true;
    }

    private static boolean keptClear(Vec3 spot, Vec3 block) {
        return Math.abs(spot.x - block.x) < KEEP_CLEAR && Math.abs(spot.z - block.z) < KEEP_CLEAR
                && block.y >= spot.y - 1.0 && block.y < spot.y + 3.0;
    }

    private static boolean refuse(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("You can't place blocks here!").withStyle(ChatFormatting.RED), true);
        return false;
    }

    /**
     * Beds are never slept in (the held item is used on them instead, so a block is placed against
     * them), and a team's chest opens only for that team while any of it is still in the game.
     * Ability items keep their own use; the zappers work on the block clicked, and items that would
     * otherwise be placed (Throwable TNT, the Lucky Chest) are used instead.
     */
    @Override
    public InteractionResult onUseBlock(Match match, ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        State state = states.get(match);
        ItemStack stack = player.getItemInHand(hand);
        if (state == null) return InteractionResult.PASS;
        String ability = BedWarsShop.ability(stack);
        switch (ability) {
            case "block_zapper", "bridge_zapper" -> {
                boolean zapped = ability.equals("block_zapper") ? BedWarsRotation.blockZapper(match, state, hit.getBlockPos())
                        : BedWarsRotation.bridgeZapper(match, hit.getBlockPos());
                if (!zapped) return InteractionResult.FAIL;
                consume(player, stack);
                return InteractionResult.SUCCESS;
            }
            case "throwable_tnt", "lucky_chest", "creeper_egg", BedWarsTracker.ABILITY -> {
                return onUseItem(match, player, hand, stack);
            }
            case String lucky when BedWarsLucky.USED_ON_BLOCKS.contains(lucky) -> {
                return onUseItem(match, player, hand, stack);
            }
            default -> {
                if (!ability.isEmpty()) return InteractionResult.PASS;
            }
        }
        BlockPos pos = hit.getBlockPos();
        BlockState block = match.arena().level().getBlockState(pos);
        if (block.getBlock() instanceof BedBlock) {
            InteractionResult result = stack.isEmpty() ? InteractionResult.PASS
                    : stack.useOn(new UseOnContext(player, hand, hit));
            return result == InteractionResult.PASS ? InteractionResult.FAIL : result;
        }
        if (block.is(Blocks.CHEST)) {
            int own = teamOf(match, player);
            for (TeamState team : state.teams.values()) {
                if (team.number != own && !team.eliminated && state.layout.inBase(team.number, Vec3.atCenterOf(pos))) {
                    player.sendSystemMessage(Component.literal("You can't open another team's chest while they are alive!")
                            .withStyle(ChatFormatting.RED), true);
                    return InteractionResult.FAIL;
                }
            }
        }
        return InteractionResult.PASS;
    }

    /** TNT lights as it is placed. */
    @Override
    public void onBlockPlaced(Match match, ServerPlayer player, BlockPos pos) {
        State state = states.get(match);
        if (state == null) return;
        mode.onBlockPlaced(this, match, state, player, pos);
        ServerLevel level = match.arena().level();
        if (level.getBlockState(pos).is(Blocks.TNT)) {
            boolean mega = state.megaTnt.remove(pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            PrimedTnt tnt = new PrimedTnt(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player);
            int fuse = mega ? BedWarsRotation.MEGA_TNT_FUSE : TNT_FUSE;
            tnt.setFuse(fuse + 20);
            tnt.addTag(ENTITY_TAG);
            level.addFreshEntity(tnt);
            level.playSound(null, pos, SoundEvents.TNT_PRIMED, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            state.tracked.add(new Tracked(tnt, mega ? "mega_tnt" : "tnt", teamOf(match, player), player.getUUID(),
                    match.activeTicks() + fuse));
        }
    }

    // ---------------------------------------------------------------- deaths and respawns

    @Override
    public DeathResult onDeath(Match match, ServerPlayer victim, @Nullable ServerPlayer killer) {
        State state = states.get(match);
        if (state == null) return DeathResult.ELIMINATE;
        PlayerState victimState = state.player(victim.getUUID());
        mode.onDeath(this, match, state, victim);
        state.tracking.remove(victim.getUUID());
        if (killer != null && killer != victim) {
            if (!mode.keepsResources(match, state, victim)) giveResources(victim, killer);
            state.player(killer.getUUID()).kills++;
        }
        if (victimState.pickaxe > 1) victimState.pickaxe--;
        if (victimState.axe > 1) victimState.axe--;
        TeamState team = state.teams.get(teamOf(match, victim));
        if (team != null && team.bedStanding()) {
            int ticks = match.settings().get(RESPAWN_SECONDS) * 20;
            if (ticks > 0) state.respawning.put(victim.getUUID(), ticks);
            if (mode.keepsResources(match, state, victim)) state.kept.put(victim.getUUID(), resources(victim));
            if (killer != null && killer != victim) mode.onKill(this, match, state, killer, victim, false);
            return DeathResult.RESPAWN;
        }
        if (killer != null && killer != victim) {
            state.player(killer.getUUID()).finalKills++;
            mode.onKill(this, match, state, killer, victim, true);
        }
        return DeathResult.ELIMINATE;
    }

    /** A death after the bed is gone is a final kill. */
    @Override
    public Component deathMessage(Match match, ServerPlayer victim, Component message, boolean eliminated) {
        if (!eliminated) return message;
        return Component.empty().append(message).append(" ")
                .append(Component.literal("FINAL KILL!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
    }

    /** The killer takes the iron, gold, diamonds and emeralds the victim carried. */
    private static void giveResources(ServerPlayer victim, ServerPlayer killer) {
        Map<Currency, Integer> taken = new EnumMap<>(Currency.class);
        Inventory inventory = victim.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            Currency currency = Currency.of(stack);
            if (currency != null) {
                taken.merge(currency, stack.getCount(), Integer::sum);
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        taken.forEach((currency, count) -> {
            int left = count;
            while (left > 0) {
                int stack = Math.min(left, 64);
                PlayerUtils.giveOrDrop(killer, new ItemStack(currency.item, stack));
                left -= stack;
            }
            killer.sendSystemMessage(Component.literal("+" + count + " " + currency.displayName).withStyle(currency.color));
        });
    }

    /** The iron, gold, diamonds and emeralds the player carries. */
    private static List<ItemStack> resources(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : player.getInventory()) {
            if (!stack.isEmpty() && Currency.of(stack) != null) stacks.add(stack.copy());
        }
        return stacks;
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
                player.sendSystemMessage(Component.literal("You have respawned!").withStyle(ChatFormatting.YELLOW));
                continue;
            }
            entry.setValue(left);
            if (left % 20 == 0) {
                title(List.of(player), Component.literal("YOU DIED!").withStyle(ChatFormatting.RED),
                        Component.literal("You will respawn in ").withStyle(ChatFormatting.YELLOW)
                                .append(Component.literal(String.valueOf(left / 20)).withStyle(ChatFormatting.RED))
                                .append(Component.literal(" seconds!").withStyle(ChatFormatting.YELLOW)));
            }
        }
    }

    /** At the team's spawn with what they bought, or watching from above until the respawn timer ends. */
    @Override
    public void onRespawn(Match match, ServerPlayer player) {
        State state = states.get(match);
        if (state != null && state.respawning.containsKey(player.getUUID())) {
            player.getInventory().clearContent();
            player.setGameMode(GameType.SPECTATOR);
            PlayerUtils.teleport(player, match.arena().level(), match.arena().lobbyPosition(), player.getYRot());
            return;
        }
        if (state == null) return;
        equip(match, state, player);
        List<ItemStack> kept = state.kept.remove(player.getUUID());
        if (kept != null) kept.forEach(stack -> PlayerUtils.giveOrDrop(player, stack));
    }

    /**
     * Leather helmet and chestplate in the team's colour, the bought leggings and boots, the tools at
     * their tiers and the compass, all in the slots the player's Hotbar Manager prefers.
     */
    void equip(Match match, State state, ServerPlayer player) {
        PlayerState bought = state.player(player.getUUID());
        DyeColor dye = match.teamOf(player.getUUID()).map(BedWarsGame::dye).orElse(DyeColor.WHITE);
        player.setItemSlot(EquipmentSlot.HEAD, leather(Items.LEATHER_HELMET, dye));
        player.setItemSlot(EquipmentSlot.CHEST, leather(Items.LEATHER_CHESTPLATE, dye));
        wearArmor(player, bought.armor, dye);
        var registries = player.registryAccess();
        if (bought.pickaxe > 0) PlayerUtils.giveOrDrop(player, BedWarsShop.pickaxe(registries, bought.pickaxe));
        if (bought.axe > 0) PlayerUtils.giveOrDrop(player, BedWarsShop.axe(registries, bought.axe));
        if (bought.shears) PlayerUtils.giveOrDrop(player, BedWarsShop.unbreakable(new ItemStack(Items.SHEARS)));
        mode.onEquip(this, match, state, player);
        BedWarsTracker.giveCompass(player);
        BedWarsHotbar.arrange(player);
        applyUpgrades(match, state, player);
    }

    private static void wearArmor(ServerPlayer player, int tier, DyeColor dye) {
        switch (tier) {
            case 1 -> {
                player.setItemSlot(EquipmentSlot.LEGS, BedWarsShop.unbreakable(new ItemStack(Items.CHAINMAIL_LEGGINGS)));
                player.setItemSlot(EquipmentSlot.FEET, BedWarsShop.unbreakable(new ItemStack(Items.CHAINMAIL_BOOTS)));
            }
            case 2 -> {
                player.setItemSlot(EquipmentSlot.LEGS, BedWarsShop.unbreakable(new ItemStack(Items.IRON_LEGGINGS)));
                player.setItemSlot(EquipmentSlot.FEET, BedWarsShop.unbreakable(new ItemStack(Items.IRON_BOOTS)));
            }
            case 3 -> {
                player.setItemSlot(EquipmentSlot.LEGS, BedWarsShop.unbreakable(new ItemStack(Items.DIAMOND_LEGGINGS)));
                player.setItemSlot(EquipmentSlot.FEET, BedWarsShop.unbreakable(new ItemStack(Items.DIAMOND_BOOTS)));
            }
            default -> {
                player.setItemSlot(EquipmentSlot.LEGS, leather(Items.LEATHER_LEGGINGS, dye));
                player.setItemSlot(EquipmentSlot.FEET, leather(Items.LEATHER_BOOTS, dye));
            }
        }
    }

    private static ItemStack leather(Item item, DyeColor dye) {
        ItemStack stack = BedWarsShop.unbreakable(new ItemStack(item));
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(dye.getTextureDiffuseColor()));
        return stack;
    }

    // ---------------------------------------------------------------- shop

    /** Why {@code player} cannot shop now, or empty when they can. */
    private static Optional<String> cannotShop(Match match, ServerPlayer player) {
        if (!match.isActiveParticipant(player.getUUID()) || player.isSpectator()) {
            return Optional.of("You can only shop while you are playing.");
        }
        return Optional.empty();
    }

    /** The tier of {@code entry} the player would buy next (1 for items without tiers), or 0 when they have the best. */
    public int nextTier(Match match, ServerPlayer player, Entry entry) {
        State state = states.get(match);
        if (state == null) return 0;
        PlayerState bought = state.player(player.getUUID());
        return switch (entry.kind()) {
            case PICKAXE -> bought.pickaxe >= 4 ? 0 : bought.pickaxe + 1;
            case AXE -> bought.axe >= 4 ? 0 : bought.axe + 1;
            case ARMOR -> bought.armor >= entry.tier() ? 0 : 1;
            case SHEARS -> bought.shears ? 0 : 1;
            default -> 1;
        };
    }

    /** What {@code entry} costs the player now: the price of their next tier. */
    public Cost price(Match match, ServerPlayer player, Entry entry) {
        State state = states.get(match);
        Prices prices = state == null ? Prices.SOLO : state.prices;
        int tier = nextTier(match, player, entry);
        return mode.price(entry, BedWarsShop.cost(entry, prices, Math.max(0, tier - 1)));
    }

    /**
     * Whether the shop offers {@code entry} in this match: obsidian is missing from some modes, and a
     * rotating item is sold only in the weeks it is in the rotation.
     */
    public boolean sells(Match match, Entry entry) {
        if (entry.category() == BedWarsShop.Category.ROTATING) {
            State state = states.get(match);
            if (state == null || !state.rotation.contains(entry.id())) return false;
        }
        return (!entry.id().equals("obsidian") || mode.obsidian(match)) && mode.sells(entry);
    }

    /** The rotating items this match sells, in the order the Rotating Items tab shows them. */
    public List<Entry> rotation(Match match) {
        State state = states.get(match);
        if (state == null) return List.of();
        return state.rotation.stream().map(id -> BedWarsShop.find(id).orElseThrow()).filter(entry -> sells(match, entry)).toList();
    }

    /** How many of {@code currency} the player carries. */
    public static int count(ServerPlayer player, Currency currency) {
        return player.getInventory().countItem(currency.item);
    }

    /** What the player can spend of {@code currency}: their own and, in Castle, their team's bank. */
    public int available(Match match, ServerPlayer player, Currency currency) {
        State state = states.get(match);
        int own = count(player, currency);
        return state == null || state.castle == null ? own : own + state.castle.banked(teamOf(match, player), currency);
    }

    /** Takes {@code cost} from the player, the rest from their team's bank. */
    private static void pay(Match match, State state, ServerPlayer player, Cost cost) {
        int own = Math.min(cost.amount(), count(player, cost.currency()));
        take(player, new Cost(cost.currency(), own));
        if (own < cost.amount() && state.castle != null) {
            state.castle.bank(teamOf(match, player), cost.currency(), own - cost.amount());
        }
    }

    /** Takes {@code cost} from the player's inventory. */
    static void take(ServerPlayer player, Cost cost) {
        int left = cost.amount();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(cost.currency().item)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
    }

    /**
     * Buys {@code entry} for the player, exactly as clicking it in the Item Shop does; throws with the
     * reason when they cannot.
     */
    public void buy(Match match, ServerPlayer player, Entry entry) throws MatchException {
        State state = states.get(match);
        if (state == null) throw new MatchException("This match has no shop.");
        Optional<String> refusal = cannotShop(match, player);
        if (refusal.isPresent()) throw new MatchException(refusal.get());
        if (!sells(match, entry)) throw new MatchException("This item is not sold in this mode.");
        int tier = nextTier(match, player, entry);
        if (tier == 0) throw new MatchException("You already have this item!");
        PlayerState bought = state.player(player.getUUID());
        int limit = BedWarsShop.limit(entry);
        if (bought.purchases.getOrDefault(entry.id(), 0) >= limit) {
            throw new MatchException("You can only buy " + limit + " of this item per game!");
        }
        Cost cost = price(match, player, entry);
        spend(match, state, player, cost);
        if (limit != Integer.MAX_VALUE) bought.purchases.merge(entry.id(), 1, Integer::sum);
        Optional<BedWarsHotbar.Category> category = BedWarsHotbar.of(entry);
        DyeColor dye = match.teamOf(player.getUUID()).map(BedWarsGame::dye).orElse(DyeColor.WHITE);
        var registries = player.registryAccess();
        String shown = BedWarsShop.displayName(entry, tier);
        switch (entry.kind()) {
            case ARMOR -> {
                bought.armor = entry.tier();
                wearArmor(player, bought.armor, dye);
            }
            case PICKAXE -> {
                bought.pickaxe = tier;
                replace(player, stack -> stack.is(ItemTags.PICKAXES), BedWarsShop.pickaxe(registries, tier), category);
            }
            case AXE -> {
                bought.axe = tier;
                replace(player, stack -> stack.is(ItemTags.AXES), BedWarsShop.axe(registries, tier), category);
            }
            case SHEARS -> {
                bought.shears = true;
                BedWarsHotbar.give(player, BedWarsShop.unbreakable(new ItemStack(Items.SHEARS)), category);
            }
            case SWORD -> {
                Inventory inventory = player.getInventory();
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    if (inventory.getItem(slot).is(Items.WOODEN_SWORD)) inventory.setItem(slot, ItemStack.EMPTY);
                }
                BedWarsHotbar.give(player, BedWarsShop.stack(entry, dye, registries), category);
            }
            case ITEM -> BedWarsHotbar.give(player, BedWarsShop.stack(entry, dye, registries), category);
        }
        applyUpgrades(match, state, player);
        player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 2.0F);
        player.sendSystemMessage(Component.literal("You purchased ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(shown).withStyle(ChatFormatting.GOLD)));
    }

    /** Puts {@code replacement} where the item {@code old} matches was, else into a slot its category prefers. */
    static void replace(ServerPlayer player, java.util.function.Predicate<ItemStack> old, ItemStack replacement,
            Optional<BedWarsHotbar.Category> category) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (old.test(inventory.getItem(slot))) {
                inventory.setItem(slot, replacement);
                return;
            }
        }
        BedWarsHotbar.give(player, replacement, category);
    }

    /** Takes {@code cost} from the player and, in Castle, their team's bank; throws when they have too little. */
    void spend(Match match, State state, ServerPlayer player, Cost cost) throws MatchException {
        int have = available(match, player, cost.currency());
        if (have < cost.amount()) {
            player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.5F);
            throw new MatchException("You don't have enough " + cost.currency().displayName
                    + (cost.currency() == Currency.DIAMOND || cost.currency() == Currency.EMERALD ? "s" : "")
                    + "! Need " + (cost.amount() - have) + " more!");
        }
        pay(match, state, player, cost);
    }

    /** The tier of {@code upgrade} the player's team has (0 for none). */
    public int upgradeLevel(Match match, ServerPlayer player, Upgrade upgrade) {
        State state = states.get(match);
        TeamState team = state == null ? null : state.teams.get(teamOf(match, player));
        return team == null ? 0 : team.level(upgrade);
    }

    public Prices prices(Match match) {
        State state = states.get(match);
        return state == null ? Prices.SOLO : state.prices;
    }

    /** Buys the next tier of {@code upgrade} for the player's team with their diamonds. */
    public void buyUpgrade(Match match, ServerPlayer player, Upgrade upgrade) throws MatchException {
        State state = states.get(match);
        if (state == null) throw new MatchException("This match has no shop.");
        Optional<String> refusal = cannotShop(match, player);
        if (refusal.isPresent()) throw new MatchException(refusal.get());
        TeamState team = state.teams.get(teamOf(match, player));
        if (team == null) throw new MatchException("You are not on a team.");
        int tier = team.level(upgrade) + 1;
        if (!offers(upgrade)) throw new MatchException("This upgrade is not sold in this mode.");
        if (tier > upgrade.tiers()) throw new MatchException("Your team already has this upgrade at its highest tier!");
        if (upgrade == Upgrade.DRAGON_BUFF && match.teams().size() == 2 && mode != BedWarsMode.CASTLE) {
            throw new MatchException("Two-team matches have no dragons.");
        }
        int cost = upgrade.cost(state.prices, tier);
        spendDiamonds(match, state, player, cost);
        team.upgrades.put(upgrade, tier);
        for (ServerPlayer member : members(match, team.number)) {
            applyUpgrades(match, state, member);
            member.sendSystemMessage(name(match, player.getUUID()).append(Component.literal(" purchased ").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(upgrade.nameAt(tier)).withStyle(ChatFormatting.GOLD)));
        }
    }

    /** Whether the team upgrades shopkeeper sells {@code upgrade} in this mode: Deadshot only in Armed. */
    public boolean offers(Upgrade upgrade) {
        return upgrade != Upgrade.DEADSHOT || mode == BedWarsMode.ARMED;
    }

    /** Queues {@code trap} for the player's team, while its bed stands. */
    public void buyTrap(Match match, ServerPlayer player, Trap trap) throws MatchException {
        State state = states.get(match);
        if (state == null) throw new MatchException("This match has no shop.");
        Optional<String> refusal = cannotShop(match, player);
        if (refusal.isPresent()) throw new MatchException(refusal.get());
        TeamState team = state.teams.get(teamOf(match, player));
        if (team == null) throw new MatchException("You are not on a team.");
        if (!team.bedStanding()) throw new MatchException("You can't buy traps once your bed is gone!");
        if (team.traps.size() >= BedWarsUpgrades.TRAP_QUEUE) throw new MatchException("Trap queue full!");
        spendDiamonds(match, state, player, BedWarsUpgrades.trapCost(state.prices, team.traps.size()));
        team.traps.add(trap);
        for (ServerPlayer member : members(match, team.number)) {
            member.sendSystemMessage(name(match, player.getUUID()).append(Component.literal(" purchased ").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(trap.displayName).withStyle(ChatFormatting.GOLD)));
        }
    }

    private void spendDiamonds(Match match, State state, ServerPlayer player, int cost) throws MatchException {
        spend(match, state, player, new Cost(Currency.DIAMOND, cost));
        player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 2.0F);
    }

    /** The traps waiting in the player's team's queue, first first. */
    public List<Trap> traps(Match match, ServerPlayer player) {
        State state = states.get(match);
        TeamState team = state == null ? null : state.teams.get(teamOf(match, player));
        return team == null ? List.of() : List.copyOf(team.traps);
    }

    @Override
    public void onSwing(Match match, ServerPlayer player) {
        State state = states.get(match);
        if (state != null) mode.onSwing(this, match, state, player);
    }

    /** An arrow that hits a Lucky Blocks lucky trap clears it. */
    @Override
    public void onProjectileHitBlock(Match match, Projectile projectile, BlockHitResult hit) {
        State state = states.get(match);
        if (state == null) return;
        if (!state.luckyTraps.isEmpty()) BedWarsLucky.arrowHit(match, state, hit.getBlockPos());
        if (mode == BedWarsMode.LUCKY_BLOCKS) BedWarsLucky.arrowLanded(match, projectile, hit);
    }

    /** Opens the shop or the team upgrades for a shopkeeper; any team's shopkeepers serve anyone. */
    @Override
    public boolean onInteractEntity(Match match, ServerPlayer player, Entity entity) {
        State state = states.get(match);
        Shopkeeper kind = state == null ? null : state.shopkeepers.get(entity.getUUID());
        if (kind == null) return false;
        switch (kind) {
            case ITEMS -> BedWarsMenus.openShop(this, match, player, BedWarsShop.Category.QUICK_BUY);
            case UPGRADES -> BedWarsMenus.openUpgrades(this, match, player);
            case BANKER -> BedWarsCastle.openBanker(this, match, player);
            case STREAKS -> BedWarsCastle.openPowers(this, match, player);
            case JERRY, TRADER -> BedWarsLucky.openTrader(this, match, player, kind);
        }
        return true;
    }

    @Nullable Villager spawnShopkeeper(Match match, State state, MapArena.Point point, int team, Shopkeeper kind) {
        ServerLevel level = match.arena().level();
        Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.TRIGGERED);
        if (villager == null) return null;
        villager.snapTo(point.position().x, point.position().y, point.position().z, point.yaw(), 0.0F);
        villager.setYHeadRot(point.yaw());
        villager.setNoAi(true);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setPersistenceRequired();
        villager.setCustomName(Component.literal(kind.title).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        villager.setCustomNameVisible(true);
        villager.addTag(ENTITY_TAG);
        level.addFreshEntity(villager);
        state.spawned.add(villager);
        state.shopkeepers.put(villager.getUUID(), kind);
        state.posts.put(villager, villager.position());
        return villager;
    }

    // ---------------------------------------------------------------- upgrades and traps

    /**
     * Sharpness on swords and axes, Protection on armour, Feather Falling on boots and Haste, as the
     * player's team bought them.
     */
    void applyUpgrades(Match match, State state, ServerPlayer player) {
        TeamState team = state.teams.get(teamOf(match, player));
        if (team == null) return;
        var registries = player.registryAccess();
        var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        int sharpness = team.level(Upgrade.SHARPENED_SWORDS);
        int protection = team.level(Upgrade.REINFORCED_ARMOR);
        int featherFalling = team.level(Upgrade.CUSHIONED_BOOTS);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            if (sharpness > 0 && (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES))
                    && EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.SHARPNESS), stack) < sharpness) {
                stack.enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), sharpness);
            }
            if (protection > 0 && isArmor(stack)
                    && EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.PROTECTION), stack) < protection) {
                stack.enchant(enchantments.getOrThrow(Enchantments.PROTECTION), protection);
            }
            if (featherFalling > 0 && stack.is(ItemTags.FOOT_ARMOR)
                    && EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.FEATHER_FALLING), stack) < featherFalling) {
                stack.enchant(enchantments.getOrThrow(Enchantments.FEATHER_FALLING), featherFalling);
            }
        }
        int haste = team.level(Upgrade.MANIAC_MINER);
        MobEffectInstance current = player.getEffect(MobEffects.HASTE);
        if (haste > 0 && (current == null || current.getAmplifier() < haste - 1 || current.getDuration() < 40)) {
            player.addEffect(new MobEffectInstance(MobEffects.HASTE, 200, haste - 1, true, false));
        }
    }

    private static boolean isArmor(ItemStack stack) {
        return stack.is(ItemTags.HEAD_ARMOR) || stack.is(ItemTags.CHEST_ARMOR) || stack.is(ItemTags.LEG_ARMOR)
                || stack.is(ItemTags.FOOT_ARMOR);
    }

    /** Heal Pool regeneration, and the first enemy in a base setting off its team's next trap. */
    private void tickTraps(Match match, State state, int now) {
        for (TeamState team : state.teams.values()) {
            if (team.eliminated) continue;
            List<ServerPlayer> inside = new ArrayList<>();
            for (ServerPlayer player : match.alivePlayers()) {
                if (!player.isSpectator() && state.layout.inBase(team.number, player.position())) inside.add(player);
            }
            if (team.level(Upgrade.HEAL_POOL) > 0) {
                for (ServerPlayer player : inside) {
                    if (teamOf(match, player) != team.number) continue;
                    MobEffectInstance regeneration = player.getEffect(MobEffects.REGENERATION);
                    if (regeneration == null || regeneration.getDuration() < 25) {
                        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, true));
                    }
                }
            }
            if (team.traps.isEmpty() || !team.bedStanding() || now < team.trapCooldown) continue;
            ServerPlayer intruder = inside.stream()
                    .filter(player -> teamOf(match, player) != team.number)
                    .filter(player -> state.milk.getOrDefault(player.getUUID(), 0L) <= now)
                    .findFirst().orElse(null);
            if (intruder == null) continue;
            Trap trap = team.traps.peek();
            if (state.castle == null || ++team.trapUses >= BedWarsCastle.TRAP_USES) {
                team.traps.poll();
                team.trapUses = 0;
            }
            team.trapCooldown = now + TRAP_COOLDOWN;
            spring(match, state, team, trap, intruder, inside);
        }
    }

    private void spring(Match match, State state, TeamState team, Trap trap, ServerPlayer intruder, List<ServerPlayer> inside) {
        switch (trap) {
            case ITS_A_TRAP -> {
                intruder.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 8 * 20, 0));
                intruder.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 8 * 20, 0));
            }
            case COUNTER_OFFENSIVE -> {
                for (ServerPlayer player : inside) {
                    if (teamOf(match, player) != team.number) continue;
                    player.addEffect(new MobEffectInstance(MobEffects.SPEED, 15 * 20, 1));
                    player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 15 * 20, 1));
                }
            }
            case ALARM -> intruder.removeEffect(MobEffects.INVISIBILITY);
            case MINER_FATIGUE -> intruder.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 10 * 20, 0));
        }
        intruder.sendSystemMessage(Component.literal("You have triggered a trap!").withStyle(ChatFormatting.RED), true);
        MutableComponent who = trap == Trap.ALARM
                ? name(match, intruder.getUUID()).append(Component.literal(" of ").withStyle(ChatFormatting.GRAY))
                        .append(teamName(match, teamOf(match, intruder)))
                : Component.literal("An enemy").withStyle(ChatFormatting.RED);
        String place = state.castle == null ? "" : BedWarsCastle.trapPlace(state, team.number, intruder.position());
        for (ServerPlayer member : members(match, team.number)) {
            title(List.of(member), Component.literal("TRAP TRIGGERED!").withStyle(ChatFormatting.RED),
                    Component.literal("Your " + trap.displayName + " has been set off" + (place.isEmpty() ? "" : " at the " + place)
                            + "!").withStyle(ChatFormatting.WHITE));
            member.sendSystemMessage(Component.literal(trap.displayName + " was set off by ").withStyle(ChatFormatting.RED)
                    .append(who).append(Component.literal("!").withStyle(ChatFormatting.RED)));
            member.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 0.5F);
        }
    }

    @Override
    public void onDamaged(Match match, ServerPlayer victim, ServerPlayer attacker) {
        State state = states.get(match);
        if (state == null) return;
        BedWarsCastle.damaged(match, state, victim, attacker);
        if (mode == BedWarsMode.LUCKY_BLOCKS) BedWarsLucky.damaged(match, state, victim, attacker);
    }

    /**
     * Complete Invisibility, as Hypixel's potion: while a player is invisible every other player is shown them
     * without armour or held items, and with them again once it wears off. Vanilla would show the armour.
     */
    private static void tickInvisibility(Match match, State state) {
        List<ServerPlayer> online = match.onlineMembers();
        for (ServerPlayer player : match.alivePlayers()) {
            boolean invisible = player.hasEffect(MobEffects.INVISIBILITY) && !player.isSpectator();
            if (!invisible && !state.hidden.remove(player.getUUID())) continue;
            if (invisible) state.hidden.add(player.getUUID());
            List<com.mojang.datafixers.util.Pair<EquipmentSlot, ItemStack>> shown = new ArrayList<>();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() == EquipmentSlot.Type.HAND || slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                    shown.add(com.mojang.datafixers.util.Pair.of(slot, invisible ? ItemStack.EMPTY : player.getItemBySlot(slot).copy()));
                }
            }
            var packet = new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(player.getId(), shown);
            for (ServerPlayer viewer : online) {
                if (viewer != player) viewer.connection.send(packet);
            }
        }
    }

    // ---------------------------------------------------------------- items with abilities

    @Override
    public InteractionResult onUseItem(Match match, ServerPlayer player, InteractionHand hand, ItemStack stack) {
        State state = states.get(match);
        if (state == null) return InteractionResult.PASS;
        InteractionResult moded = mode.onUseItem(this, match, state, player, hand, stack);
        if (moded != InteractionResult.PASS) return moded;
        String ability = BedWarsShop.ability(stack);
        ServerLevel level = player.level();
        int team = teamOf(match, player);
        int now = match.activeTicks();
        switch (ability) {
            case "fireball" -> {
                if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
                Vec3 look = player.getLookAngle();
                LargeFireball fireball = new LargeFireball(level, player, look, 0);
                fireball.setPos(player.getX() + look.x, player.getEyeY() - 0.2 + look.y, player.getZ() + look.z);
                fireball.setDeltaMovement(look.scale(1.2));
                fireball.addTag(ENTITY_TAG);
                level.addFreshEntity(fireball);
                state.tracked.add(new Tracked(fireball, "fireball", team, player.getUUID(), now + 20 * 20));
                player.getCooldowns().addCooldown(stack, FIREBALL_COOLDOWN);
                consume(player, stack);
                return InteractionResult.SUCCESS;
            }
            case "bridge_egg", "bedbug" -> {
                Snowball thrown = new Snowball(level, player, new ItemStack(ability.equals("bridge_egg") ? Items.EGG : Items.SNOWBALL));
                thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 0.5F);
                thrown.addTag(ENTITY_TAG);
                level.addFreshEntity(thrown);
                state.tracked.add(new Tracked(thrown, ability, team, player.getUUID(),
                        now + (ability.equals("bridge_egg") ? BRIDGE_EGG_TICKS : 20 * 20)));
                consume(player, stack);
                return InteractionResult.SUCCESS;
            }
            case "dream_defender" -> {
                BlockHitResult hit = target(player);
                if (hit == null) return InteractionResult.FAIL;
                IronGolem golem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
                if (golem == null) return InteractionResult.FAIL;
                BlockPos at = hit.getBlockPos().relative(hit.getDirection());
                golem.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), 0.0F);
                golem.setPlayerCreated(true);
                golem.setPersistenceRequired();
                golem.setCustomName(teamName(match, team).append(Component.literal(" Dream Defender").withStyle(ChatFormatting.GRAY)));
                golem.setCustomNameVisible(true);
                own(golem, team);
                level.addFreshEntity(golem);
                state.tracked.add(new Tracked(golem, "dream_defender", team, player.getUUID(), now + GOLEM_TICKS));
                consume(player, stack);
                return InteractionResult.SUCCESS;
            }
            case "popup_tower" -> {
                BlockHitResult hit = target(player);
                if (hit == null) return InteractionResult.FAIL;
                BlockPos at = hit.getBlockPos().relative(hit.getDirection());
                if (!popUpTower(match, state, player, at)) return InteractionResult.FAIL;
                consume(player, stack);
                return InteractionResult.SUCCESS;
            }
            case "magic_milk" -> {
                state.milk.put(player.getUUID(), (long) now + MAGIC_MILK_TICKS);
                player.playSound(SoundEvents.GENERIC_DRINK.value(), 1.0F, 1.0F);
                player.sendSystemMessage(Component.literal("You will not trigger traps for 30 seconds!").withStyle(ChatFormatting.GREEN));
                consume(player, stack);
                return InteractionResult.SUCCESS;
            }
            case BedWarsTracker.ABILITY -> {
                BedWarsMenus.openTracker(this, match, player, null);
                return InteractionResult.SUCCESS;
            }
            case "sugar_cookie" -> {
                BedWarsRotation.sugarCookie(player);
                consume(player, stack);
                return InteractionResult.SUCCESS;
            }
            case "lucky_chest" -> {
                BedWarsRotation.luckyChest(player);
                consume(player, stack);
                return InteractionResult.SUCCESS;
            }
            case "throwable_tnt" -> {
                PrimedTnt tnt = BedWarsRotation.throwTnt(player);
                state.tracked.add(new Tracked(tnt, "tnt", team, player.getUUID(), now + BedWarsRotation.THROWN_TNT_FUSE));
                consume(player, stack);
                return InteractionResult.SUCCESS;
            }
            case "creeper_egg" -> {
                BlockHitResult hit = target(player);
                if (hit == null) return InteractionResult.FAIL;
                BlockPos at = hit.getBlockPos().relative(hit.getDirection());
                Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
                if (creeper == null) return InteractionResult.FAIL;
                creeper.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), 0.0F);
                creeper.setPersistenceRequired();
                creeper.setCustomName(teamName(match, team).append(Component.literal(" Creeper").withStyle(ChatFormatting.GRAY)));
                own(creeper, team);
                level.addFreshEntity(creeper);
                state.tracked.add(new Tracked(creeper, "creeper", team, player.getUUID(), now + CREEPER_TICKS));
                consume(player, stack);
                return InteractionResult.SUCCESS;
            }
            case "block_zapper", "bridge_zapper" -> {
                return InteractionResult.FAIL;
            }
            default -> {
                return InteractionResult.PASS;
            }
        }
    }

    private static void consume(ServerPlayer player, ItemStack stack) {
        if (!player.getAbilities().instabuild) stack.shrink(1);
    }

    private static @Nullable BlockHitResult target(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(player.blockInteractionRange()));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /**
     * A Compact Pop-up Tower: a ring of the team's wool four blocks across and five high around {@code
     * at}, a ladder up its inside facing the player, and a crenellated top; only into empty space the
     * player could build in.
     */
    boolean popUpTower(Match match, State state, ServerPlayer player, BlockPos at) {
        ServerLevel level = match.arena().level();
        Direction facing = player.getDirection();
        Direction side = facing.getClockWise();
        DyeColor dye = match.teamOf(player.getUUID()).map(BedWarsGame::dye).orElse(DyeColor.WHITE);
        BlockState wool = Blocks.WOOL.pick(dye).defaultBlockState();
        List<Map.Entry<BlockPos, BlockState>> blocks = new ArrayList<>();
        for (int y = 0; y < 6; y++) {
            for (int forward = -1; forward <= 2; forward++) {
                for (int across = -1; across <= 2; across++) {
                    boolean wall = forward == -1 || forward == 2 || across == -1 || across == 2;
                    if (!wall) continue;
                    if (y == 5 && (forward + across) % 2 != 0) continue;
                    BlockPos pos = at.relative(facing, forward).relative(side, across).above(y);
                    blocks.add(Map.entry(pos, wool));
                }
            }
            if (y < 5) {
                BlockPos ladder = at.relative(facing, 1).above(y);
                blocks.add(Map.entry(ladder, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing.getOpposite())));
            }
        }
        for (int forward = 0; forward <= 1; forward++) {
            for (int across = 0; across <= 1; across++) {
                blocks.add(Map.entry(at.relative(facing, forward).relative(side, across).above(4), wool));
            }
        }
        blocks.removeIf(entry -> entry.getKey().equals(at.relative(facing, 1).above(4)));
        boolean any = false;
        for (Map.Entry<BlockPos, BlockState> block : blocks) {
            BlockPos pos = block.getKey();
            if (!level.getBlockState(pos).isAir() || !match.arena().canBuild(pos) || bedAt(state, pos) != null) continue;
            level.setBlock(pos, block.getValue(), Block.UPDATE_ALL);
            match.markPlaced(pos);
            any = true;
        }
        if (any) level.playSound(null, at, SoundEvents.WOOL_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        return any;
    }

    /** Fireballs, eggs, bedbugs and TNT as they fly or burn, and the mobs the game summoned. */
    private void tickTracked(Match match, State state, int now) {
        List<Tracked> summoned = new ArrayList<>();
        for (Iterator<Tracked> iterator = state.tracked.iterator(); iterator.hasNext(); ) {
            Tracked tracked = iterator.next();
            Entity entity = tracked.entity();
            Vec3 last = state.lastPositions.getOrDefault(entity, entity.position());
            boolean gone = !entity.isAlive();
            switch (tracked.kind()) {
                case "fireball" -> {
                    if (gone || now >= tracked.expires()) {
                        explode(match, state, entity, owner(match, tracked), entity.position(), FIREBALL_POWER);
                        entity.discard();
                    }
                }
                case "wither_fireball" -> {
                    if (gone || now >= tracked.expires()) {
                        explode(match, state, entity, null, entity.position(), FIREBALL_POWER, false);
                        entity.discard();
                    }
                }
                case "tnt" -> {
                    if (now >= tracked.expires()) {
                        explode(match, state, entity, owner(match, tracked), entity.position(), TNT_POWER);
                        entity.discard();
                        gone = true;
                    }
                }
                case "mega_tnt" -> {
                    if (now >= tracked.expires()) {
                        explode(match, state, entity, owner(match, tracked), entity.position(), BedWarsRotation.MEGA_TNT_POWER, true, true);
                        entity.discard();
                        gone = true;
                    }
                }
                case "creeper" -> {
                    if (!gone && entity instanceof Creeper creeper && creeper.getSwelling(1.0F) >= 0.9F) {
                        // It goes off a moment before vanilla would, with the game's explosion rules.
                        explode(match, state, creeper, owner(match, tracked), creeper.position(), CREEPER_POWER);
                        creeper.discard();
                        gone = true;
                    } else if (gone || now >= tracked.expires()) {
                        entity.discard();
                        gone = true;
                    } else if (now % 10 == 0) {
                        hunt(match, (Mob) entity, tracked.team());
                    }
                }
                case "bridge_egg" -> {
                    if (!gone) bridge(match, state, tracked, entity);
                    if (now >= tracked.expires()) {
                        entity.discard();
                        gone = true;
                    }
                }
                case "bedbug" -> {
                    if (gone) {
                        summoned.addAll(summonSilverfish(match, tracked, last, now));
                    } else if (now >= tracked.expires()) {
                        entity.discard();
                        gone = true;
                    }
                }
                case "jerry" -> {
                    if (gone || now >= tracked.expires()) {
                        entity.discard();
                        state.spawned.remove(entity);
                        state.posts.remove(entity);
                        state.shopkeepers.remove(entity.getUUID());
                        gone = true;
                    }
                }
                case "dream_defender", "silverfish", "dragon" -> {
                    if (gone || now >= tracked.expires()) {
                        entity.discard();
                        gone = true;
                    } else if (now % 10 == 0 && entity instanceof Mob mob) {
                        hunt(match, mob, tracked.team());
                    }
                }
                default -> {
                    Boolean done = BedWarsLucky.tickTracked(this, match, state, tracked, last, now, summoned);
                    if (done != null && done) gone = true;
                }
            }
            if (gone || !entity.isAlive()) {
                iterator.remove();
                state.lastPositions.remove(entity);
            } else {
                state.lastPositions.put(entity, entity.position());
            }
        }
        state.tracked.addAll(summoned);
    }

    private static @Nullable ServerPlayer owner(Match match, Tracked tracked) {
        return tracked.owner() == null ? null : match.server().getPlayerList().getPlayer(tracked.owner());
    }

    /** Wool of the thrower's team under a bridge egg's path: three wide, two below it. */
    private void bridge(Match match, State state, Tracked tracked, Entity egg) {
        ServerLevel level = match.arena().level();
        DyeColor dye = match.teamNumbered(tracked.team()).map(BedWarsGame::dye).orElse(DyeColor.WHITE);
        BlockState wool = Blocks.WOOL.pick(dye).defaultBlockState();
        Vec3 motion = egg.getDeltaMovement();
        Direction across = Math.abs(motion.x) > Math.abs(motion.z) ? Direction.SOUTH : Direction.EAST;
        BlockPos below = BlockPos.containing(egg.getX(), egg.getY() - 2.0, egg.getZ());
        ServerPlayer owner = owner(match, tracked);
        if (owner != null && owner.position().distanceTo(egg.position()) < 3.0) return;
        for (int offset = -1; offset <= 1; offset++) {
            BlockPos pos = below.relative(across, offset);
            if (level.getBlockState(pos).isAir() && match.arena().canBuild(pos) && bedAt(state, pos) == null) {
                level.setBlock(pos, wool, Block.UPDATE_ALL);
                match.markPlaced(pos);
            }
        }
        level.playSound(null, below, SoundEvents.CHICKEN_EGG, net.minecraft.sounds.SoundSource.PLAYERS, 0.4F, 1.5F);
    }

    private List<Tracked> summonSilverfish(Match match, Tracked tracked, Vec3 at, int now) {
        ServerLevel level = match.arena().level();
        List<Tracked> summoned = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            Silverfish silverfish = EntityTypes.SILVERFISH.create(level, EntitySpawnReason.TRIGGERED);
            if (silverfish == null) continue;
            silverfish.snapTo(at.x, at.y, at.z, ThreadLocalRandom.current().nextFloat() * 360.0F, 0.0F);
            silverfish.setCustomName(teamName(match, tracked.team()).append(Component.literal(" Bedbug").withStyle(ChatFormatting.GRAY)));
            own(silverfish, tracked.team());
            level.addFreshEntity(silverfish);
            summoned.add(new Tracked(silverfish, "silverfish", tracked.team(), tracked.owner(), now + SILVERFISH_TICKS));
        }
        return summoned;
    }

    /** Points a summoned mob at the nearest enemy within 16 blocks, never at its own team. */
    private void hunt(Match match, Mob mob, int team) {
        LivingEntity target = mob.getTarget();
        if (target instanceof ServerPlayer player && (teamOf(match, player) == team || !match.isAlive(player.getUUID()))) {
            mob.setTarget(null);
            target = null;
        }
        if (target != null && target.isAlive()) return;
        ServerPlayer nearest = null;
        double best = 16.0 * 16.0;
        for (ServerPlayer player : match.alivePlayers()) {
            if (player.isSpectator() || teamOf(match, player) == team) continue;
            double distance = player.distanceToSqr(mob);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest != null) mob.setTarget(nearest);
    }

    static void own(Entity entity, int team) {
        entity.addTag(ENTITY_TAG);
        entity.addTag(TEAM_TAG + team);
    }

    /** The team a summoned mob or dragon fights for, or 0. */
    static int ownerTeam(@Nullable Entity entity) {
        if (entity == null) return 0;
        for (String tag : entity.entityTags()) {
            if (tag.startsWith(TEAM_TAG)) return Integer.parseInt(tag.substring(TEAM_TAG.length()));
        }
        return 0;
    }

    /** Summoned mobs and dragons never hurt their own team, and a Hay Bale landed on takes all the fall damage. */
    @Override
    public boolean allowDamage(Match match, ServerPlayer victim, DamageSource source) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) && landedOnHay(victim)) return false;
        int team = ownerTeam(source.getEntity());
        if (team == 0 && source.getDirectEntity() != null) team = ownerTeam(source.getDirectEntity());
        if (team == 0 && source.getEntity() instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragonPart part) {
            team = ownerTeam(part.parentMob);
        }
        return team == 0 || team != teamOf(match, victim);
    }

    /** Whether the player stands on a hay bale (or is sunk into its top). */
    private static boolean landedOnHay(ServerPlayer player) {
        BlockPos feet = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
        return player.level().getBlockState(feet).is(Blocks.HAY_BLOCK) || player.level().getBlockState(feet.above()).is(Blocks.HAY_BLOCK);
    }

    /**
     * An explosion that breaks only blocks placed during the match, never blast-proof glass (any stained
     * glass), beds or end stone near its edge, and hurts the player who set it off too.
     */
    void explode(Match match, State state, Entity source, @Nullable ServerPlayer owner, Vec3 at, float power) {
        explode(match, state, source, owner, at, power, true);
    }

    /** As above; with {@code hurts} false the explosion breaks blocks but hurts and throws nobody. */
    void explode(Match match, State state, Entity source, @Nullable ServerPlayer owner, Vec3 at, float power, boolean hurts) {
        explode(match, state, source, owner, at, power, hurts, false);
    }

    /** As above; with {@code glass} true (Mega TNT) blast-proof glass breaks too. */
    void explode(Match match, State state, Entity source, @Nullable ServerPlayer owner, Vec3 at, float power, boolean hurts,
            boolean glass) {
        ServerLevel level = match.arena().level();
        ExplosionDamageCalculator calculator = new ExplosionDamageCalculator() {
            @Override
            public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter getter, BlockPos pos,
                    BlockState blockState, FluidState fluid) {
                if (!blockState.isAir() && (!match.isPlacedBlock(pos) || bedAt(state, pos) != null || blastProof(blockState) && !glass)) {
                    return Optional.of(3_600_000.0F);
                }
                return super.getBlockExplosionResistance(explosion, getter, pos, blockState, fluid);
            }

            @Override
            public boolean shouldBlockExplode(Explosion explosion, BlockGetter getter, BlockPos pos, BlockState blockState,
                    float strength) {
                return match.isPlacedBlock(pos) && bedAt(state, pos) == null && (glass || !blastProof(blockState));
            }

            @Override
            public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
                return hurts && super.shouldDamageEntity(explosion, entity);
            }

            @Override
            public float getKnockbackMultiplier(Entity entity) {
                return hurts ? super.getKnockbackMultiplier(entity) : 0.0F;
            }
        };
        DamageSource damage = owner == null ? level.damageSources().explosion(source, null)
                : level.damageSources().explosion(source, owner);
        level.explode(source, damage, calculator, at.x, at.y, at.z, power, false, Level.ExplosionInteraction.TNT);
    }

    private static boolean blastProof(BlockState state) {
        return Blocks.STAINED_GLASS.asList().stream().anyMatch(state::is) || state.is(Blocks.GLASS);
    }

    // ---------------------------------------------------------------- sudden death

    private void spawnDragons(Match match, State state) {
        ServerLevel level = match.arena().level();
        BlockPos origin = BlockPos.containing(state.center.x, state.center.y + 20, state.center.z);
        int now = match.activeTicks();
        for (TeamState team : state.teams.values()) {
            if (team.eliminated || members(match, team.number).isEmpty()) continue;
            int count = 1 + team.level(Upgrade.DRAGON_BUFF) + mode.extraDragons;
            for (int index = 0; index < count; index++) {
                EnderDragon dragon = EntityTypes.ENDER_DRAGON.create(level, EntitySpawnReason.TRIGGERED);
                if (dragon == null) continue;
                dragon.snapTo(origin.getX() + ThreadLocalRandom.current().nextInt(-20, 21), origin.getY() + 10,
                        origin.getZ() + ThreadLocalRandom.current().nextInt(-20, 21), 0.0F, 0.0F);
                dragon.setFightOrigin(origin);
                dragon.setCustomName(teamName(match, team.number).append(Component.literal(" Dragon").withStyle(ChatFormatting.GRAY)));
                own(dragon, team.number);
                level.addFreshEntity(dragon);
                state.tracked.add(new Tracked(dragon, "dragon", team.number, null, Long.MAX_VALUE));
            }
        }
    }

    /**
     * In a two-team match Sudden Death closes a border towards the middle instead: over five minutes
     * from the map's edge to 10 blocks out (this mod's choice), a heart a second outside it.
     */
    private void tickBorder(Match match, State state) {
        if (!state.suddenDeath || match.teams().size() != 2 || mode == BedWarsMode.CASTLE) return;
        state.suddenDeathTicks += 20;
        double radius = Math.max(10.0, state.halfSize - (state.halfSize - 10.0) * state.suddenDeathTicks / (5.0 * 60 * 20));
        for (ServerPlayer player : match.alivePlayers()) {
            if (player.isSpectator()) continue;
            if (Math.abs(player.getX() - state.center.x) > radius || Math.abs(player.getZ() - state.center.z) > radius) {
                player.hurtServer(player.level(), player.damageSources().outOfBorder(), 2.0F);
                player.sendSystemMessage(Component.literal("You are outside the border!").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    /** "TEAM ELIMINATED" once the last player of a team is out. */
    private void announceEliminations(Match match, State state) {
        for (TeamState team : state.teams.values()) {
            if (team.eliminated) continue;
            boolean anyAlive = match.teamNumbered(team.number).map(t -> t.members().stream().anyMatch(match::isAlive)).orElse(false);
            if (anyAlive) continue;
            team.eliminated = true;
            for (Bed bed : List.copyOf(team.standing)) removeBed(match.arena().level(), bed);
            team.standing.clear();
            match.broadcast(Component.literal("TEAM ELIMINATED > ").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                    .append(teamName(match, team.number)).append(Component.literal(" has been eliminated!").withStyle(ChatFormatting.RED)));
        }
    }

    // ---------------------------------------------------------------- sidebar

    /** The next event and how long until it, as Hypixel's scoreboard shows it. */
    @Override
    public void addSidebarLines(Match match, List<Component> lines) {
        State state = states.get(match);
        if (state == null) return;
        nextEvent(match, state).ifPresent(next -> {
            int seconds = Math.max(0, (next.getValue() + 19) / 20);
            lines.add(Component.literal(next.getKey().title + " in ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal("%d:%02d".formatted(seconds / 60, seconds % 60)).withStyle(ChatFormatting.GREEN)));
        });
    }

    /** ✔ while the team's bed stands, ✘ once the team is out, else how many of it are left. */
    @Override
    public Component sidebarTeamSuffix(Match match, MatchTeam team) {
        State state = states.get(match);
        if (state == null) return Component.empty();
        TeamState teamState = state.teams.get(team.number());
        if (teamState == null) return Component.empty();
        if (teamState.bedStanding()) return Component.literal(" ✔").withStyle(ChatFormatting.GREEN);
        long alive = team.members().stream().filter(match::isAlive).count();
        return alive == 0 ? Component.literal(" ✘").withStyle(ChatFormatting.RED)
                : Component.literal(" " + alive).withStyle(ChatFormatting.YELLOW);
    }

    // ---------------------------------------------------------------- queries

    /** Whether team {@code number}'s bed (any of them, in Castle) still stands. */
    public boolean bedStanding(Match match, int number) {
        State state = states.get(match);
        TeamState team = state == null ? null : state.teams.get(number);
        return team != null && team.bedStanding();
    }

    /** Whether the player was killed and waits to respawn. */
    public boolean isRespawning(Match match, UUID player) {
        State state = states.get(match);
        return state != null && state.respawning.containsKey(player);
    }

    /** Tier of the diamond or emerald generators. */
    public int generatorTier(Match match, Currency currency) {
        State state = states.get(match);
        if (state == null) return 0;
        return state.generators.stream().filter(generator -> generator.currency == currency).mapToInt(generator -> generator.tier)
                .max().orElse(0);
    }

    /** Castle: what team {@code team}'s Banker holds of {@code currency}. */
    public int banked(Match match, int team, Currency currency) {
        State state = states.get(match);
        return state == null || state.castle == null ? 0 : state.castle.banked(team, currency);
    }

    /** Castle: the player's streak points. */
    public int streakPoints(Match match, UUID player) {
        State state = states.get(match);
        return state == null || state.castle == null ? 0 : state.castle.points.getOrDefault(player, 0);
    }

    /** Final kills of the player so far. */
    public int finalKills(Match match, UUID player) {
        State state = states.get(match);
        return state == null ? 0 : state.player(player).finalKills;
    }

    /** The map's beds, shopkeepers and generators while the match runs. */
    public Optional<BedWarsLayout> layout(Match match) {
        return Optional.ofNullable(states.get(match)).map(state -> state.layout);
    }

    /** The player's pickaxe and axe tiers and armour tier: {@code [pickaxe, axe, armor]}. */
    public int[] tiers(Match match, UUID player) {
        State state = states.get(match);
        if (state == null) return new int[3];
        PlayerState bought = state.player(player);
        return new int[] {bought.pickaxe, bought.axe, bought.armor};
    }

    // ---------------------------------------------------------------- bots

    /**
     * What a bot needs to play the match {@code player} is in, using only JDK and Minecraft types, or
     * null outside an active Bed Wars match: {@code "team"} (Integer), {@code "slot"} and {@code
     * "teamSize"} (Integer: the player's place in its team from 0, and its size), {@code "spawn"}
     * (Vec3), {@code "beds"} (List of Maps with {@code "team"} (Integer), {@code "foot"} and {@code
     * "head"} (BlockPos) and {@code "standing"} (Boolean)), {@code "shop"}, {@code "upgrades"} and
     * {@code "forge"} (Vec3, the nearest of the team's shopkeepers and island generators), {@code
     * "diamonds"} and {@code
     * "emeralds"} (List of Vec3), {@code "base"} (AABB), {@code "respawning"} (Boolean) and {@code
     * "resources"} (Map of currency name to Integer carried).
     */
    public static @Nullable Map<String, Object> botView(ServerPlayer player) {
        Match match = MatchManager.activeMatch(player.getUUID());
        if (match == null || !(match.game() instanceof BedWarsGame game)) return null;
        State state = game.states.get(match);
        MatchTeam team = match.teamOf(player.getUUID()).orElse(null);
        if (state == null || team == null) return null;
        List<Map<String, Object>> beds = new ArrayList<>();
        for (TeamState teamState : state.teams.values()) {
            for (Bed bed : teamState.beds) {
                beds.add(Map.of("team", bed.team(), "foot", bed.foot(), "head", bed.head(),
                        "standing", teamState.standing.contains(bed)));
            }
        }
        Map<String, Object> resources = new HashMap<>();
        for (Currency currency : Currency.values()) resources.put(currency.name().toLowerCase(java.util.Locale.ROOT), count(player, currency));
        Map<String, Object> view = new HashMap<>();
        view.put("team", team.number());
        view.put("slot", Math.max(0, team.members().indexOf(player.getUUID())));
        view.put("teamSize", team.members().size());
        view.put("spawn", ((MapArena) match.arena()).spawnsOf(team.number()).getFirst().position());
        view.put("beds", List.copyOf(beds));
        nearest(state.layout.shops().getOrDefault(team.number(), List.of()), player).ifPresent(shop -> view.put("shop", shop));
        nearest(state.layout.upgrades().getOrDefault(team.number(), List.of()), player).ifPresent(shop -> view.put("upgrades", shop));
        state.layout.forges().getOrDefault(team.number(), List.of()).stream().min(Comparator.comparingDouble(player::distanceToSqr))
                .ifPresent(forge -> view.put("forge", forge));
        view.put("diamonds", state.layout.diamonds());
        view.put("emeralds", state.layout.emeralds());
        List<AABB> bases = state.layout.bases().getOrDefault(team.number(), List.of()).stream().map(MapArena.Region::box).toList();
        if (!bases.isEmpty()) view.put("base", bases.getFirst());
        view.put("respawning", state.respawning.containsKey(player.getUUID()));
        view.put("resources", Map.copyOf(resources));
        return Map.copyOf(view);
    }

    private static Optional<Vec3> nearest(List<MapArena.Point> points, ServerPlayer player) {
        return points.stream().map(MapArena.Point::position).min(Comparator.comparingDouble(player::distanceToSqr));
    }

    /**
     * Buys item {@code item} (an id of {@link BedWarsShop#ITEMS}) for {@code player} through the same
     * purchase as the Item Shop's button, as a player standing at one of their team's item shopkeepers
     * does; returns whether it was bought. Bots shop through this.
     */
    public static boolean botBuy(ServerPlayer player, String item) {
        Match match = MatchManager.activeMatch(player.getUUID());
        if (match == null || !(match.game() instanceof BedWarsGame game)) return false;
        State state = game.states.get(match);
        Optional<Entry> entry = BedWarsShop.find(item);
        if (state == null || entry.isEmpty() || !game.atShopkeeper(state, player, true)) return false;
        try {
            game.buy(match, player, entry.get());
            return true;
        } catch (MatchException exception) {
            return false;
        }
    }

    /** As {@link #botBuy} for a team upgrade ({@link Upgrade} id) at the team upgrades shopkeeper. */
    public static boolean botUpgrade(ServerPlayer player, String upgrade) {
        Match match = MatchManager.activeMatch(player.getUUID());
        if (match == null || !(match.game() instanceof BedWarsGame game)) return false;
        State state = game.states.get(match);
        Optional<Upgrade> found = Upgrade.find(upgrade);
        if (state == null || found.isEmpty() || !game.atShopkeeper(state, player, false)) return false;
        try {
            game.buyUpgrade(match, player, found.get());
            return true;
        } catch (MatchException exception) {
            return false;
        }
    }

    /** Whether the player stands within reach of a shopkeeper of that kind. */
    private boolean atShopkeeper(State state, ServerPlayer player, boolean itemShop) {
        for (Entity entity : state.spawned) {
            if (entity instanceof Villager && (state.shopkeepers.get(entity.getUUID()) == Shopkeeper.ITEMS) == itemShop
                    && player.distanceToSqr(entity) <= player.entityInteractionRange() * player.entityInteractionRange() + 4.0) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- helpers

    static int teamOf(Match match, ServerPlayer player) {
        return match.teamOf(player.getUUID()).map(MatchTeam::number).orElse(0);
    }

    static DyeColor dye(MatchTeam team) {
        return team.scoreboardTeam().getColor().map(BridgeGame::dyeOf).orElse(DyeColor.WHITE);
    }

    static List<ServerPlayer> members(Match match, int team) {
        return match.onlineMembers().stream().filter(player -> teamOf(match, player) == team).toList();
    }

    static void title(List<ServerPlayer> players, Component title, Component subtitle) {
        for (ServerPlayer player : players) {
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    /** "Red", in the team's colour. */
    static MutableComponent teamName(Match match, int number) {
        MutableComponent name = Component.literal(Match.teamName(number));
        return match.teamNumbered(number).flatMap(MatchTeam::color).map(color -> name.withStyle(style -> style.withColor(color)))
                .orElse(name);
    }

    static MutableComponent name(Match match, UUID player) {
        String text = match.nameOf(player);
        return Component.literal(text).withStyle(style -> match.teamOf(player)
                .flatMap(MatchTeam::color).map(style::withColor).orElse(style));
    }

    private static String capitalise(String word) {
        return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1).replace('_', ' ');
    }
}
