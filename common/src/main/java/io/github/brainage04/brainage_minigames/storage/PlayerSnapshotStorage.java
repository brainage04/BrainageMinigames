package io.github.brainage04.brainage_minigames.storage;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

public final class PlayerSnapshotStorage {
    private static final String PLAYERS_KEY = "players";
    private static final String DIMENSION_KEY = "brainage_return_dimension";
    private static final String POSITION_KEY = "brainage_return_position";
    private static final String ROTATION_KEY = "brainage_return_rotation";
    private static final String VELOCITY_KEY = "brainage_return_velocity";
    private static final String TEAM_KEY = "brainage_return_team";
    private static final String REWARDS_KEY = "brainage_rewards";

    /** Where {@link ServerPlayer#saveWithoutId} stores the game modes. */
    private static final String GAME_MODE_KEY = "playerGameType";

    private static final String PREVIOUS_GAME_MODE_KEY = "previousPlayerGameType";

    private PlayerSnapshotStorage() {}

    public static boolean save(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        CompoundTag root = server.getCommandStorage().get(BrainageMinigames.id("player_snapshots"));
        CompoundTag players = root.getCompoundOrEmpty(PLAYERS_KEY);
        String playerKey = player.getUUID().toString();
        if (players.contains(playerKey)) {
            BrainageMinigames.LOGGER.error(
                    "Refusing to overwrite the pending snapshot for {}",
                    player.getGameProfile().name());
            return false;
        }

        TagValueOutput output =
                TagValueOutput.createWithContext(
                        ProblemReporter.DISCARDING, server.registryAccess());
        player.saveWithoutId(output);
        CompoundTag snapshot = output.buildResult();
        snapshot.store(DIMENSION_KEY, Level.RESOURCE_KEY_CODEC, player.level().dimension());
        snapshot.store(POSITION_KEY, Vec3.CODEC, player.position());
        snapshot.store(ROTATION_KEY, Vec2.CODEC, new Vec2(player.getYRot(), player.getXRot()));
        snapshot.store(VELOCITY_KEY, Vec3.CODEC, player.getDeltaMovement());
        PlayerTeam team = player.getTeam();
        if (team != null) {
            snapshot.putString(TEAM_KEY, team.getName());
        }

        players.put(playerKey, snapshot);
        root.put(PLAYERS_KEY, players);
        server.getCommandStorage().set(BrainageMinigames.id("player_snapshots"), root);
        return true;
    }

    public static boolean hasSnapshot(MinecraftServer server, UUID playerId) {
        return snapshots(server).contains(playerId.toString());
    }

    public static boolean addRewards(ServerPlayer player, List<ItemStack> rewards) {
        if (rewards.isEmpty()) {
            return true;
        }

        MinecraftServer server = player.level().getServer();
        CompoundTag root = server.getCommandStorage().get(BrainageMinigames.id("player_snapshots"));
        CompoundTag players = root.getCompoundOrEmpty(PLAYERS_KEY);
        String playerKey = player.getUUID().toString();
        CompoundTag snapshot = players.getCompound(playerKey).orElse(null);
        if (snapshot == null) {
            return false;
        }

        List<ItemStack> combined =
                new ArrayList<>(
                        snapshot.read(REWARDS_KEY, ItemStack.CODEC.listOf()).orElse(List.of()));
        rewards.forEach(stack -> combined.add(stack.copy()));
        snapshot.store(REWARDS_KEY, ItemStack.CODEC.listOf(), combined);
        players.put(playerKey, snapshot);
        root.put(PLAYERS_KEY, players);
        server.getCommandStorage().set(BrainageMinigames.id("player_snapshots"), root);
        return true;
    }

    // Player data stores game modes with the legacy numeric codec, so the snapshot does too.
    @SuppressWarnings("deprecation")
    public static boolean restore(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        CompoundTag root = server.getCommandStorage().get(BrainageMinigames.id("player_snapshots"));
        CompoundTag players = root.getCompoundOrEmpty(PLAYERS_KEY);
        String playerKey = player.getUUID().toString();
        CompoundTag snapshot = players.getCompound(playerKey).orElse(null);
        if (snapshot == null) {
            return false;
        }

        ResourceKey<Level> dimensionKey =
                snapshot.read(DIMENSION_KEY, Level.RESOURCE_KEY_CODEC).orElse(Level.OVERWORLD);
        ServerLevel destination = server.getLevel(dimensionKey);
        if (destination == null) {
            destination = server.overworld();
        }
        Vec3 position =
                snapshot.read(POSITION_KEY, Vec3.CODEC)
                        .orElse(
                                Vec3.atBottomCenterOf(
                                        destination.getServer().getRespawnData().pos()));
        Vec2 rotation = snapshot.read(ROTATION_KEY, Vec2.CODEC).orElse(Vec2.ZERO);
        Vec3 velocity = snapshot.read(VELOCITY_KEY, Vec3.CODEC).orElse(Vec3.ZERO);
        List<ItemStack> rewards =
                snapshot.read(REWARDS_KEY, ItemStack.CODEC.listOf()).orElse(List.of());
        String previousTeamName = snapshot.getStringOr(TEAM_KEY, "");
        GameType gameMode =
                snapshot.read(GAME_MODE_KEY, GameType.LEGACY_ID_CODEC)
                        .orElse(player.gameMode.getGameModeForPlayer());

        // Loading a game mode changes it on the server without telling the client, which then keeps
        // the old mode's abilities and tab-list entry. Load the current mode instead and switch
        // with setGameMode below, which sends the change to everyone.
        CompoundTag loaded = snapshot.copy();
        loaded.store(
                GAME_MODE_KEY, GameType.LEGACY_ID_CODEC, player.gameMode.getGameModeForPlayer());
        loaded.remove(PREVIOUS_GAME_MODE_KEY);
        GameType previousGameMode = player.gameMode.getPreviousGameModeForPlayer();
        if (previousGameMode != null) {
            loaded.store(PREVIOUS_GAME_MODE_KEY, GameType.LEGACY_ID_CODEC, previousGameMode);
        }
        ValueInput input =
                TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), loaded);
        player.load(input);
        ServerPlayer restored =
                player.teleport(
                        new TeleportTransition(
                                destination,
                                position,
                                velocity,
                                rotation.x,
                                rotation.y,
                                TeleportTransition.DO_NOTHING));
        if (restored == null) {
            BrainageMinigames.LOGGER.error(
                    "Failed to return {} to {} at {}",
                    player.getGameProfile().name(),
                    dimensionKey.identifier(),
                    position);
            return false;
        }

        restored.setGameMode(gameMode);
        // Loading also replaced the abilities (fly speed, flying); send them even if the mode was
        // already right, as setGameMode then sends nothing.
        restored.onUpdateAbilities();

        String scoreboardName = restored.getScoreboardName();
        server.getScoreboard().removePlayerFromTeam(scoreboardName);
        if (!previousTeamName.isEmpty()) {
            PlayerTeam previousTeam = server.getScoreboard().getPlayerTeam(previousTeamName);
            if (previousTeam != null) {
                server.getScoreboard().addPlayerToTeam(scoreboardName, previousTeam);
            }
        }

        for (ItemStack reward : rewards) {
            PlayerUtils.giveOrDrop(restored, reward.copy());
        }
        if (!rewards.isEmpty()) {
            restored.sendSystemMessage(
                    Component.literal("Your minigame rewards have been added to your inventory."));
        }

        players.remove(playerKey);
        root.put(PLAYERS_KEY, players);
        server.getCommandStorage().set(BrainageMinigames.id("player_snapshots"), root);
        return true;
    }

    public static void restoreOnlinePlayers(MinecraftServer server) {
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (hasSnapshot(server, player.getUUID())) {
                restore(player);
            }
        }
    }

    private static CompoundTag snapshots(MinecraftServer server) {
        return server.getCommandStorage()
                .get(BrainageMinigames.id("player_snapshots"))
                .getCompoundOrEmpty(PLAYERS_KEY);
    }
}
