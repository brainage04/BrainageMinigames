package io.github.brainage04.brainage_minigames.storage;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerSnapshotStorage {
    public static final String CUSTOM_EVENT_SCOPE = "custom_event";
    public static final String UHC_SCOPE = "uhc";

    private static final String PLAYERS_KEY = "players";
    private static final String SCOPE_KEY = "brainage_scope";
    private static final String DIMENSION_KEY = "brainage_return_dimension";
    private static final String POSITION_KEY = "brainage_return_position";
    private static final String ROTATION_KEY = "brainage_return_rotation";
    private static final String VELOCITY_KEY = "brainage_return_velocity";
    private static final String TEAM_KEY = "brainage_return_team";
    private static final String REWARDS_KEY = "brainage_rewards";

    private PlayerSnapshotStorage() {
    }

    public static boolean save(ServerPlayer player, String scope) {
        MinecraftServer server = player.level().getServer();
        CompoundTag root = server.getCommandStorage().get(BrainageMinigames.id("player_snapshots"));
        CompoundTag players = root.getCompoundOrEmpty(PLAYERS_KEY);
        String playerKey = player.getUUID().toString();
        if (players.contains(playerKey)) {
            BrainageMinigames.LOGGER.error("Refusing to overwrite the pending snapshot for {}", player.getGameProfile().name());
            return false;
        }

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
        player.saveWithoutId(output);
        CompoundTag snapshot = output.buildResult();
        snapshot.putString(SCOPE_KEY, scope);
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

    public static boolean hasSnapshot(MinecraftServer server, UUID playerId, String scope) {
        CompoundTag snapshot = snapshots(server).getCompound(playerId.toString()).orElse(null);
        return snapshot != null && scope.equals(snapshot.getStringOr(SCOPE_KEY, ""));
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

        List<ItemStack> combined = new ArrayList<>(snapshot.read(REWARDS_KEY, ItemStack.CODEC.listOf()).orElse(List.of()));
        rewards.forEach(stack -> combined.add(stack.copy()));
        snapshot.store(REWARDS_KEY, ItemStack.CODEC.listOf(), combined);
        players.put(playerKey, snapshot);
        root.put(PLAYERS_KEY, players);
        server.getCommandStorage().set(BrainageMinigames.id("player_snapshots"), root);
        return true;
    }

    public static boolean restore(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        CompoundTag root = server.getCommandStorage().get(BrainageMinigames.id("player_snapshots"));
        CompoundTag players = root.getCompoundOrEmpty(PLAYERS_KEY);
        String playerKey = player.getUUID().toString();
        CompoundTag snapshot = players.getCompound(playerKey).orElse(null);
        if (snapshot == null) {
            return false;
        }

        ResourceKey<Level> dimensionKey = snapshot.read(DIMENSION_KEY, Level.RESOURCE_KEY_CODEC).orElse(Level.OVERWORLD);
        ServerLevel destination = server.getLevel(dimensionKey);
        if (destination == null) {
            destination = server.overworld();
        }
        Vec3 position = snapshot.read(POSITION_KEY, Vec3.CODEC)
                .orElse(Vec3.atBottomCenterOf(destination.getServer().getRespawnData().pos()));
        Vec2 rotation = snapshot.read(ROTATION_KEY, Vec2.CODEC).orElse(Vec2.ZERO);
        Vec3 velocity = snapshot.read(VELOCITY_KEY, Vec3.CODEC).orElse(Vec3.ZERO);
        List<ItemStack> rewards = snapshot.read(REWARDS_KEY, ItemStack.CODEC.listOf()).orElse(List.of());
        String previousTeamName = snapshot.getStringOr(TEAM_KEY, "");

        ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), snapshot);
        player.load(input);
        ServerPlayer restored = player.teleport(new TeleportTransition(
                destination,
                position,
                velocity,
                rotation.x,
                rotation.y,
                TeleportTransition.DO_NOTHING
        ));
        if (restored == null) {
            BrainageMinigames.LOGGER.error("Failed to return {} to {} at {}", player.getGameProfile().name(), dimensionKey.identifier(), position);
            return false;
        }

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
            restored.sendSystemMessage(Component.literal("Your minigame rewards have been added to your inventory."));
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
    public static void restoreOnlinePlayers(MinecraftServer server, String scope) {
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (hasSnapshot(server, player.getUUID(), scope)) {
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
