package io.github.brainage04.brainage_minigames;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.network.CommonListenerCookie;

/** Players joined through the real player list, as a client that connected and loaded the world. */
public final class TestPlayers {
    private TestPlayers() {}

    /** A new player in the test's level; its client connection keeps every outbound packet. */
    public static ServerPlayer connect(GameTestHelper context, String name) {
        var cookie = cookie(name);
        var player = new ServerPlayer(context.getLevel().getServer(), context.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        connect(player, cookie);
        return player;
    }

    public static CommonListenerCookie cookie(String name) {
        return CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
    }

    /** Places {@code player} on a new connection; the returned channel holds the packets sent to it. */
    public static EmbeddedChannel connect(ServerPlayer player, CommonListenerCookie cookie) {
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        player.level().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        return channel;
    }

    /** A connected, loaded player named {@code name} that records the chat it is sent. */
    public static ChatPlayer chat(GameTestHelper context, String name) {
        var cookie = cookie(name);
        var player = new ChatPlayer(context.getLevel().getServer(), context.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        connect(player, cookie);
        return player;
    }

    /** A player that records every chat message (not action-bar text) it is sent. */
    public static final class ChatPlayer extends ServerPlayer {
        public final List<Component> messages = new ArrayList<>();

        private ChatPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation information) {
            super(server, level, profile, information);
        }

        @Override
        public void sendSystemMessage(Component message, boolean overlay) {
            if (!overlay) messages.add(message);
            super.sendSystemMessage(message, overlay);
        }
    }

    /** Removes the players still online, as their clients disconnecting would. */
    public static void disconnect(ServerPlayer... players) {
        for (ServerPlayer player : players) {
            var playerList = player.level().getServer().getPlayerList();
            if (playerList.getPlayer(player.getUUID()) == player) playerList.remove(player);
        }
    }

    /** Ops the player as a game master (permission level 2), or removes them from the op list. */
    public static void setOperator(ServerPlayer player, boolean operator) {
        var playerList = player.level().getServer().getPlayerList();
        var id = new NameAndId(player.getGameProfile());
        if (operator) {
            playerList.op(id, Optional.of(LevelBasedPermissionSet.GAMEMASTER), Optional.empty());
        } else {
            playerList.deop(id);
        }
    }
}
