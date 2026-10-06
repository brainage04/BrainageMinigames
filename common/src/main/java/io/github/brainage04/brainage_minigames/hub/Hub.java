package io.github.brainage04.brainage_minigames.hub;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchService;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The protected spawn area players return to between matches. Inside it, players who are not in
 * a match play in adventure mode, cannot break or place blocks, take no damage and stay fed;
 * operators in creative mode may build. {@code /hub} and {@code /spawn} lead there, and players
 * leaving a match are restored there. A world without a hub gets one built at its spawn on its
 * first start (see {@link HubBuilder}); operators move, resize or turn it off with {@code /hub}.
 * The settings live in the world's command storage under {@code brainage_minigames:hub}.
 */
public final class Hub {
    public static final int DEFAULT_RADIUS = 32;

    /** Marks players the hub put into adventure mode, so leaving it gives them survival back. */
    static final String ADVENTURE_TAG = "brainage_hub_adventure";

    private static final Identifier STORAGE_ID = BrainageMinigames.id("hub");
    private static final String CONFIG_KEY = "hub";
    private static final int UPDATE_INTERVAL = 5;

    /** Where the hub is and whether it applies; {@code spawn} is where players are sent. */
    public record Config(ResourceKey<Level> dimension, Vec3 spawn, float yaw, int radius, boolean enabled) {
        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Config::dimension),
                        Vec3.CODEC.fieldOf("spawn").forGetter(Config::spawn),
                        Codec.FLOAT.fieldOf("yaw").forGetter(Config::yaw),
                        Codec.intRange(1, 1024).fieldOf("radius").forGetter(Config::radius),
                        Codec.BOOL.fieldOf("enabled").forGetter(Config::enabled))
                .apply(instance, Config::new));

        public Config withRadius(int newRadius) {
            return new Config(dimension, spawn, yaw, newRadius, enabled);
        }

        public Config withEnabled(boolean newEnabled) {
            return new Config(dimension, spawn, yaw, radius, newEnabled);
        }
    }

    /** Where a player returning to the hub is placed. */
    public record ReturnPoint(ServerLevel level, Vec3 position, float yaw) {}

    private static @Nullable Config config;
    private static final Set<UUID> IN_HUB = new HashSet<>();
    private static final List<Consumer<ServerPlayer>> ENTER_LISTENERS = new ArrayList<>();

    private Hub() {}

    /**
     * Calls {@code listener} whenever a player enters the hub outside a match: joining the server
     * there, using {@code /hub}, walking in, or being returned after a match.
     */
    public static void onEnter(Consumer<ServerPlayer> listener) {
        ENTER_LISTENERS.add(listener);
    }

    public static @Nullable Config config() {
        return config;
    }

    public static boolean enabled() {
        return config != null && config.enabled();
    }

    /** Loads the hub, building one at the world spawn when the world has none yet. */
    public static void serverStarted(MinecraftServer server) {
        config = server.getCommandStorage().get(STORAGE_ID).read(CONFIG_KEY, Config.CODEC).orElse(null);
        // GameTest worlds start without a hub; tests that need one set it up themselves.
        if (config == null && !(server instanceof GameTestServer)) buildAtWorldSpawn(server);
    }

    /** Builds a hub at the world spawn and turns it on. */
    static void buildAtWorldSpawn(MinecraftServer server) {
        LevelData.RespawnData respawn = server.getRespawnData();
        ServerLevel level = server.getLevel(respawn.dimension());
        if (level == null) level = server.overworld();
        Vec3 spawn = HubBuilder.build(level, respawn.pos());
        set(server, new Config(level.dimension(), spawn, HubBuilder.SPAWN_YAW, DEFAULT_RADIUS, true));
        BrainageMinigames.LOGGER.info(
                "Built the hub at {} {} {} in {}; operators can move or turn it off with /hub",
                spawn.x(), spawn.y(), spawn.z(), level.dimension().identifier());
    }

    public static void serverStopped() {
        config = null;
        IN_HUB.clear();
    }

    /** Stores {@code newConfig} and makes the hub spawn the world spawn while the hub is on. */
    public static void set(MinecraftServer server, Config newConfig) {
        config = newConfig;
        CompoundTag root = server.getCommandStorage().get(STORAGE_ID);
        root.store(CONFIG_KEY, Config.CODEC, newConfig);
        server.getCommandStorage().set(STORAGE_ID, root);
        if (newConfig.enabled()) {
            server.setRespawnData(LevelData.RespawnData.of(
                    newConfig.dimension(), BlockPos.containing(newConfig.spawn()), newConfig.yaw(), 0.0F));
        }
        server.getPlayerList().getPlayers().forEach(Hub::update);
    }

    /** Forgets the hub entirely, as in a world that never had one; GameTests use this. */
    public static void remove(MinecraftServer server) {
        config = null;
        CompoundTag root = server.getCommandStorage().get(STORAGE_ID);
        root.remove(CONFIG_KEY);
        server.getCommandStorage().set(STORAGE_ID, root);
        server.getPlayerList().getPlayers().forEach(Hub::update);
        IN_HUB.clear();
    }

    /** Where players return to, or null when there is no hub or it is off. */
    public static @Nullable ReturnPoint returnPoint(MinecraftServer server) {
        if (!enabled()) return null;
        ServerLevel level = server.getLevel(config.dimension());
        return level == null ? null : new ReturnPoint(level, config.spawn(), config.yaw());
    }

    /** Whether {@code pos} in {@code level} is inside the hub while it is on. */
    public static boolean contains(Level level, BlockPos pos) {
        if (!enabled() || level.dimension() != config.dimension()) return false;
        double dx = pos.getX() + 0.5 - config.spawn().x();
        double dz = pos.getZ() + 0.5 - config.spawn().z();
        return dx * dx + dz * dz <= (double) config.radius() * config.radius();
    }

    /** Whether the player is in the hub and not in a match, so the hub's rules apply to them. */
    public static boolean inHub(ServerPlayer player) {
        return contains(player.level(), player.blockPosition())
                && MatchManager.matchOf(player.getUUID()).isEmpty();
    }

    /**
     * Whether the hub stops the player changing the block at {@code pos}: it is in the hub and
     * the player is not an operator in creative mode. Match rules apply to players in a match.
     */
    public static boolean protects(ServerPlayer player, BlockPos pos) {
        return contains(player.level(), pos) && !(player.isCreative() && MatchService.isOperator(player));
    }

    /** Players in the hub take no damage, except what bypasses invulnerability, such as /kill. */
    public static boolean allowDamage(ServerPlayer player, DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !inHub(player);
    }

    /** Sends the player to the hub spawn; false when there is no hub or it could not be reached. */
    public static boolean sendToHub(ServerPlayer player) {
        ReturnPoint point = returnPoint(player.level().getServer());
        if (point == null) return false;
        player.stopRiding();
        ServerPlayer moved = PlayerUtils.teleport(player, point.level(), point.position(), point.yaw());
        if (moved == null) return false;
        moved.resetFallDistance();
        update(moved);
        return true;
    }

    /** Applies the hub's rules every few ticks; also while it is off, to hand back survival mode. */
    public static void tick(MinecraftServer server) {
        if (config == null || server.getTickCount() % UPDATE_INTERVAL != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            update(player);
        }
    }

    public static void playerLeft(ServerPlayer player) {
        IN_HUB.remove(player.getUUID());
    }

    /** Applies or lifts the hub's rules for the player as they are inside or outside it. */
    static void update(ServerPlayer player) {
        if (MatchManager.matchOf(player.getUUID()).isPresent()) {
            IN_HUB.remove(player.getUUID());
            return;
        }
        if (!contains(player.level(), player.blockPosition())) {
            leave(player);
            return;
        }
        if (player.gameMode() == GameType.SURVIVAL) {
            player.setGameMode(GameType.ADVENTURE);
            player.addTag(ADVENTURE_TAG);
        }
        PlayerUtils.heal(player);
        if (IN_HUB.add(player.getUUID())) {
            for (Consumer<ServerPlayer> listener : ENTER_LISTENERS) listener.accept(player);
        }
    }

    /** Gives survival back to a player outside every match whom the hub put in adventure mode. */
    private static void leave(ServerPlayer player) {
        IN_HUB.remove(player.getUUID());
        if (player.removeTag(ADVENTURE_TAG) && player.gameMode() == GameType.ADVENTURE) {
            player.setGameMode(GameType.SURVIVAL);
        }
    }
}
