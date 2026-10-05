package io.github.brainage04.brainage_minigames;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
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
}
