package io.github.brainage04.brainage_minigames.uhc;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.event.custom.core.MinigameState;
import io.github.brainage04.brainage_minigames.storage.PlayerSnapshotStorage;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class UhcManager {
    public static final ResourceKey<Level> LEVEL_KEY =
            ResourceKey.create(Registries.DIMENSION, BrainageMinigames.id("uhc"));

    private static final int COUNTDOWN_TICKS = 10 * 20;
    private static final int PVP_ENABLE_TICKS = 10 * 60 * 20;
    private static final int FIRST_SHRINK_TICKS = 30 * 60 * 20;
    private static final int FINAL_PHASE_TICKS = 40 * 60 * 20;
    private static final int FINAL_SURFACE_TICKS = 50 * 60 * 20;
    private static final int GAME_LIMIT_TICKS = 60 * 60 * 20;
    private static final long SHRINK_DURATION_TICKS = 10L * 60L * 20L;

    private static final Set<UUID> LOBBY_PLAYERS = new HashSet<>();
    private static final Set<UUID> ACTIVE_PLAYERS = new HashSet<>();
    private static State state = State.IDLE;
    private static int stateTicks;
    private static int centerX;
    private static int centerZ;

    private UhcManager() {
    }

    public enum State {
        IDLE,
        LOBBY,
        COUNTDOWN,
        ACTIVE
    }

    public static boolean openLobby(MinecraftServer server) {
        ServerLevel level = server.getLevel(LEVEL_KEY);
        if (state != State.IDLE || MinigameState.event != null || level == null) {
            return false;
        }
        if (!UhcWorldCleanup.markForReset(server)) {
            return false;
        }

        chooseFreshCenter(level);
        configureBorder(level, 1_000.0);
        LOBBY_PLAYERS.clear();
        ACTIVE_PLAYERS.clear();
        state = State.LOBBY;
        stateTicks = 0;
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("A UHC lobby is open. Use /uhc join to participate!")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                false
        );
        return true;
    }

    public static boolean join(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel level = server.getLevel(LEVEL_KEY);
        if (state != State.LOBBY || level == null || LOBBY_PLAYERS.contains(player.getUUID())
                || PlayerSnapshotStorage.hasSnapshot(server, player.getUUID())) {
            return false;
        }
        if (!PlayerSnapshotStorage.save(player, PlayerSnapshotStorage.UHC_SCOPE)) {
            return false;
        }

        LOBBY_PLAYERS.add(player.getUUID());
        preparePlayer(player, GameType.ADVENTURE);
        PlayerUtils.makeInvulnerable(player);
        ServerPlayer teleported = player.teleport(transition(level, surfacePosition(level, centerX, centerZ), 0.0F));
        if (teleported == null) {
            LOBBY_PLAYERS.remove(player.getUUID());
            PlayerSnapshotStorage.restore(player);
            return false;
        }
        broadcast(server, Component.literal("%s joined the UHC (%d players)."
                .formatted(player.getName().getString(), LOBBY_PLAYERS.size())).withStyle(ChatFormatting.GREEN));
        if (LOBBY_PLAYERS.size() == 2) {
            for (ServerPlayer operator : PlayerUtils.getOperators(server.getPlayerList())) {
                operator.sendSystemMessage(Component.literal(
                        "The UHC has enough players. Run /uhc start when everyone is ready."
                ).withStyle(ChatFormatting.YELLOW));
            }
        }
        return true;
    }

    public static boolean leave(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        UUID id = player.getUUID();
        boolean enrolled = LOBBY_PLAYERS.remove(id) | ACTIVE_PLAYERS.remove(id);
        if (!enrolled || !PlayerSnapshotStorage.hasSnapshot(server, id, PlayerSnapshotStorage.UHC_SCOPE)) {
            return false;
        }
        boolean restored = PlayerSnapshotStorage.restore(player);
        if (restored) {
            broadcast(server, Component.literal("%s left the UHC (%d remaining)."
                    .formatted(player.getName().getString(), participantCount())).withStyle(ChatFormatting.RED));
            checkForWinner(server);
        }
        return restored;
    }

    public static boolean startCountdown(MinecraftServer server) {
        ServerLevel level = server.getLevel(LEVEL_KEY);
        if (state != State.LOBBY || level == null || LOBBY_PLAYERS.size() < 2) {
            return false;
        }
        ACTIVE_PLAYERS.clear();
        ACTIVE_PLAYERS.addAll(LOBBY_PLAYERS);
        spreadPlayers(level);
        for (ServerPlayer player : onlinePlayers(level, ACTIVE_PLAYERS)) {
            freeze(player);
        }
        state = State.COUNTDOWN;
        stateTicks = 0;
        broadcast(server, Component.literal("UHC starts in 10 seconds!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        return true;
    }

    public static boolean stop(MinecraftServer server) {
        if (state == State.IDLE) {
            return false;
        }
        finish(server, List.of(), false, "The UHC was stopped.");
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (state == State.IDLE) {
            return;
        }
        if (state == State.LOBBY) {
            stateTicks++;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (LOBBY_PLAYERS.contains(player.getUUID())) {
                    player.setHealth(player.getMaxHealth());
                    player.getFoodData().setFoodLevel(20);
                    player.getFoodData().setSaturation(20.0F);
                }
            }
            return;
        }
        ServerLevel level = server.getLevel(LEVEL_KEY);
        if (level == null) {
            finish(server, List.of(), false, "The UHC dimension became unavailable.");
            return;
        }

        stateTicks++;
        if (state == State.COUNTDOWN) {
            if (stateTicks % 20 == 0) {
                int seconds = (COUNTDOWN_TICKS - stateTicks) / 20;
                if (seconds > 0) {
                    broadcast(server, Component.literal("UHC starts in %d second%s."
                            .formatted(seconds, seconds == 1 ? "" : "s")).withStyle(ChatFormatting.GOLD));
                }
            }
            if (stateTicks >= COUNTDOWN_TICKS) {
                startGame(level);
            }
            return;
        }

        if (stateTicks == PVP_ENABLE_TICKS) {
            broadcast(server, Component.literal("PvP is now enabled!").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        } else if (stateTicks == FIRST_SHRINK_TICKS) {
            level.getWorldBorder().lerpSizeBetween(1_000.0, 100.0, SHRINK_DURATION_TICKS, level.getGameTime());
            broadcast(server, Component.literal("The border is shrinking to a 50-block radius.")
                    .withStyle(ChatFormatting.YELLOW));
        } else if (stateTicks == FINAL_PHASE_TICKS) {
            teleportAllToSurface(level);
            level.getWorldBorder().lerpSizeBetween(100.0, 20.0, SHRINK_DURATION_TICKS, level.getGameTime());
            broadcast(server, Component.literal("Final phase: the border is shrinking to a 10-block radius!")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        } else if (stateTicks == FINAL_SURFACE_TICKS) {
            teleportAllToSurface(level);
            broadcast(server, Component.literal("10 minutes remain.").withStyle(ChatFormatting.GOLD));
        } else if (stateTicks >= GAME_LIMIT_TICKS) {
            List<ServerPlayer> remaining = onlinePlayers(level, ACTIVE_PLAYERS);
            finish(server, remaining, true, null);
            return;
        }
        checkForWinner(server);
    }

    public static boolean allowDamage(ServerPlayer victim, DamageSource source) {
        UUID victimId = victim.getUUID();
        if (!isParticipant(victimId)) {
            return true;
        }
        if (state == State.LOBBY || state == State.COUNTDOWN) {
            return false;
        }
        if (state == State.ACTIVE && stateTicks < PVP_ENABLE_TICKS && source.getEntity() instanceof ServerPlayer attacker
                && ACTIVE_PLAYERS.contains(attacker.getUUID())) {
            return false;
        }
        return true;
    }

    public static boolean eliminate(ServerPlayer player) {
        if (state != State.ACTIVE || !ACTIVE_PLAYERS.remove(player.getUUID())) {
            return false;
        }
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.setGameMode(GameType.SPECTATOR);
        broadcast(player.level().getServer(), Component.literal("%s was eliminated (%d remaining)."
                .formatted(player.getName().getString(), ACTIVE_PLAYERS.size())).withStyle(ChatFormatting.RED));
        return true;
    }

    public static void handleDisconnect(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean removed = LOBBY_PLAYERS.remove(id) | ACTIVE_PLAYERS.remove(id);
        if (!removed) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        BrainageMinigames.LOGGER.info("{} left an active UHC session", player.getGameProfile().name());
        broadcast(server, Component.literal("%s disconnected from the UHC (%d remaining)."
                .formatted(player.getName().getString(), participantCount())).withStyle(ChatFormatting.RED));
        checkForWinner(server);
    }

    public static void handleConnect(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (PlayerSnapshotStorage.hasSnapshot(server, player.getUUID(), PlayerSnapshotStorage.UHC_SCOPE)
                && !isParticipant(player.getUUID())) {
            PlayerSnapshotStorage.restore(player);
        }
    }

    public static boolean isParticipant(UUID playerId) {
        return LOBBY_PLAYERS.contains(playerId) || ACTIVE_PLAYERS.contains(playerId);
    }

    public static boolean isRunning() {
        return state != State.IDLE;
    }

    public static State state() {
        return state;
    }

    public static int participantCount() {
        return state == State.LOBBY ? LOBBY_PLAYERS.size() : ACTIVE_PLAYERS.size();
    }

    public static int elapsedSeconds() {
        return state == State.ACTIVE ? stateTicks / 20 : 0;
    }

    private static void startGame(ServerLevel level) {
        state = State.ACTIVE;
        stateTicks = 0;
        for (ServerPlayer player : onlinePlayers(level, ACTIVE_PLAYERS)) {
            preparePlayer(player, GameType.SURVIVAL);
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 10 * 60 * 20, 0, false, true));
            giveStarterKit(player);
        }
        broadcast(level.getServer(), Component.literal("The UHC has started!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    private static void checkForWinner(MinecraftServer server) {
        if (state != State.ACTIVE || ACTIVE_PLAYERS.size() > 1) {
            return;
        }
        ServerLevel level = server.getLevel(LEVEL_KEY);
        List<ServerPlayer> winners = level == null ? List.of() : onlinePlayers(level, ACTIVE_PLAYERS);
        finish(server, winners, false, null);
    }

    private static void finish(
            MinecraftServer server,
            List<ServerPlayer> winners,
            boolean tie,
            String explicitMessage
    ) {
        Component message;
        if (explicitMessage != null) {
            message = Component.literal(explicitMessage).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        } else if (tie && !winners.isEmpty()) {
            String names = String.join(", ", winners.stream().map(player -> player.getName().getString()).toList());
            message = Component.literal("The UHC ended in a tie between: " + names)
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        } else if (winners.size() == 1) {
            message = Component.literal(winners.getFirst().getName().getString() + " won the UHC!")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        } else {
            message = Component.literal("The UHC ended with no winner.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        }
        broadcast(server, message);
        PlayerSnapshotStorage.restoreOnlinePlayers(server, PlayerSnapshotStorage.UHC_SCOPE);
        ServerLevel level = server.getLevel(LEVEL_KEY);
        if (level != null) {
            resetBorder(level);
        }
        LOBBY_PLAYERS.clear();
        ACTIVE_PLAYERS.clear();
        state = State.IDLE;
        stateTicks = 0;
    }

    private static void preparePlayer(ServerPlayer player, GameType gameType) {
        player.setGameMode(gameType);
        player.getInventory().clearContent();
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        FoodData food = player.getFoodData();
        food.setFoodLevel(20);
        food.setSaturation(20.0F);
    }

    private static void freeze(ServerPlayer player) {
        player.setGameMode(GameType.ADVENTURE);
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, COUNTDOWN_TICKS + 40, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, COUNTDOWN_TICKS + 40, 255, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, COUNTDOWN_TICKS + 40, 255, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, COUNTDOWN_TICKS + 40, 255, false, false));
    }

    private static void giveStarterKit(ServerPlayer player) {
        for (ItemStack stack : List.of(
                new ItemStack(Items.STONE_PICKAXE),
                new ItemStack(Items.STONE_AXE),
                new ItemStack(Items.STONE_SHOVEL),
                new ItemStack(Items.STONE_SWORD),
                new ItemStack(Items.COOKED_BEEF, 16),
                new ItemStack(Items.LEATHER_HELMET),
                new ItemStack(Items.LEATHER_CHESTPLATE),
                new ItemStack(Items.LEATHER_LEGGINGS),
                new ItemStack(Items.LEATHER_BOOTS)
        )) {
            PlayerUtils.giveOrDrop(player, stack);
        }
    }

    private static void spreadPlayers(ServerLevel level) {
        List<ServerPlayer> players = onlinePlayers(level, ACTIVE_PLAYERS);
        double rotation = level.getRandom().nextDouble() * Math.PI * 2.0;
        double radius = Math.min(400.0, Math.max(80.0, players.size() * 20.0));
        for (int index = 0; index < players.size(); index++) {
            double angle = rotation + (Math.PI * 2.0 * index / players.size());
            int x = (int) Math.round(centerX + Math.cos(angle) * radius);
            int z = (int) Math.round(centerZ + Math.sin(angle) * radius);
            players.get(index).teleport(transition(level, surfacePosition(level, x, z), (float) Math.toDegrees(angle + Math.PI)));
        }
    }

    private static void teleportAllToSurface(ServerLevel level) {
        for (ServerPlayer player : onlinePlayers(level, ACTIVE_PLAYERS)) {
            player.teleport(transition(level, surfacePosition(level, player.getBlockX(), player.getBlockZ()), player.getYRot()));
        }
    }

    private static Vec3 surfacePosition(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new Vec3(x + 0.5, y, z + 0.5);
    }

    private static TeleportTransition transition(ServerLevel level, Vec3 position, float yaw) {
        return new TeleportTransition(level, position, Vec3.ZERO, yaw, 0.0F, TeleportTransition.DO_NOTHING);
    }

    private static List<ServerPlayer> onlinePlayers(ServerLevel level, Set<UUID> ids) {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.level() == level) {
                players.add(player);
            }
        }
        return players;
    }

    private static void chooseFreshCenter(ServerLevel level) {
        centerX = (level.getRandom().nextInt(4_001) - 2_000) * 2_000;
        centerZ = (level.getRandom().nextInt(4_001) - 2_000) * 2_000;
    }

    private static void configureBorder(ServerLevel level, double size) {
        WorldBorder border = level.getWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(size);
        border.setDamagePerBlock(0.2);
        border.setSafeZone(5.0);
        border.setWarningBlocks(10);
        border.setWarningTime(15);
    }

    private static void resetBorder(ServerLevel level) {
        WorldBorder border = level.getWorldBorder();
        border.setCenter(0.0, 0.0);
        border.setSize(WorldBorder.MAX_SIZE);
    }

    private static void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }
}
