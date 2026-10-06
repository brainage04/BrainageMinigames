package io.github.brainage04.brainage_minigames;

import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestServers;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Renders previews of bundled maps for map authors; it does nothing unless the system property
 * {@value #PROPERTY} lists maps as {@code <game>/<map>} separated by commas (or {@code all}).
 * Each map is pasted into the minigames dimension and photographed from two raised corners,
 * straight above, its first spawn and, for races, every checkpoint and the finish.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MapPreviewClientGameTest implements FabricClientGameTest {
    public static final String PROPERTY = "brainage_minigames.map_previews";

    private static final double EYE = 1.62;

    /** The open map, closed again once photographed; touched on the server thread only. */
    private static final List<MapArena> OPEN = new ArrayList<>();

    /** One camera position: where it is and which way it looks. */
    private record View(String name, Vec3 eye, float yaw, float pitch) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        String requested = System.getProperty(PROPERTY, "").strip();
        if (requested.isEmpty()) {
            return;
        }
        Properties properties = ClientGameTestServers.flatServerProperties();
        properties.setProperty("view-distance", "12");
        context.runOnClient(client -> {
            client.options.renderDistance().set(12);
            if (!client.gui.hud.isHidden()) client.gui.hud.toggle();
        });
        try {
            ClientGameTestServers.withDedicatedServer(context, properties, "Brainage Minigames map previews", server -> {
                for (Identifier map : server.computeOnServer(minecraft -> maps(minecraft, requested))) {
                    preview(context, server, map);
                }
            });
        } finally {
            context.runOnClient(client -> {
                if (client.gui.hud.isHidden()) client.gui.hud.toggle();
            });
        }
    }

    private static List<Identifier> maps(MinecraftServer server, String requested) {
        List<Identifier> all = server.getStructureManager().listTemplates()
                .filter(id -> id.getNamespace().equals(BrainageMinigames.MOD_ID)
                        && id.getPath().startsWith("maps/")
                        && !id.getPath().startsWith("maps/test_map"))
                .distinct()
                .sorted()
                .toList();
        if (requested.equals("all")) {
            return all;
        }
        List<Identifier> chosen = new ArrayList<>();
        for (String name : Arrays.stream(requested.split(",")).map(String::strip).toList()) {
            Identifier id = BrainageMinigames.id("maps/" + name);
            if (!all.contains(id)) {
                throw new AssertionError("There is no bundled map " + name + ".");
            }
            chosen.add(id);
        }
        return chosen;
    }

    private static void preview(ClientGameTestContext context, TestDedicatedServerContext server, Identifier map) {
        List<View> views = server.computeOnServer(minecraft -> {
            ServerLevel level = minecraft.getLevel(ModDimensions.MINIGAMES);
            try {
                MapArena arena = MapArena.open(level, map);
                List<View> result = views(arena);
                OPEN.add(arena);
                return result;
            } catch (MatchException exception) {
                throw new AssertionError(exception.getMessage(), exception);
            }
        });
        String prefix = map.getPath().substring("maps/".length()).replace('/', '-');
        try {
            // The first view follows a change of dimension; later ones only move within the map.
            int wait = 120;
            for (View view : views) {
                server.runOnServer(minecraft -> look(minecraft, view));
                context.waitTicks(wait);
                wait = 40;
                Path shot = context.takeScreenshot(prefix + "-" + view.name());
                BrainageMinigames.LOGGER.info("Map preview {} {}: {}", map, view.name(), shot);
            }
        } finally {
            server.runOnServer(minecraft -> OPEN.removeFirst().close());
        }
    }

    private static List<View> views(MapArena arena) {
        BoundingBox box = arena.bounds();
        Arena.Spawn spawn = arena.spawnsOf(1).getFirst();
        // Aim at the middle of the map at the height of play, not of a lobby high above it.
        Vec3 center = new Vec3(box.getCenter().getX() + 0.5, spawn.position().y(), box.getCenter().getZ() + 0.5);
        double span = Math.max(box.getXSpan(), box.getZSpan());
        double reach = span * 0.42 + 4.0;
        List<View> views = new ArrayList<>();
        views.add(toward("corner-nw", center, center.add(-reach, reach * 0.6, -reach)));
        views.add(toward("corner-se", center, center.add(reach, reach * 0.6, reach)));
        views.add(new View("top", center.add(0.0, span * 0.62 + 4.0, 0.01), 0.0F, 90.0F));
        views.add(new View("spawn", spawn.position().add(0.0, EYE, 0.0), spawn.yaw(), 15.0F));
        List<MapArena.Point> gates = new ArrayList<>(arena.points("checkpoint_"));
        gates.sort((a, b) -> Integer.compare(number(a.name()), number(b.name())));
        arena.point("finish").ifPresent(gates::add);
        for (MapArena.Point gate : gates) {
            views.add(new View(gate.name(), gate.position().add(0.0, EYE, 0.0), gate.yaw(), 20.0F));
        }
        return views;
    }

    private static int number(String gate) {
        return Integer.parseInt(gate.substring(gate.lastIndexOf('_') + 1));
    }

    private static View toward(String name, Vec3 target, Vec3 eye) {
        Vec3 look = target.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x(), look.z()));
        float pitch = (float) Math.toDegrees(-Math.atan2(look.y(), Math.hypot(look.x(), look.z())));
        return new View(name, eye, yaw, pitch);
    }

    private static void look(MinecraftServer server, View view) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        player.setGameMode(GameType.SPECTATOR);
        player.teleport(new TeleportTransition(
                server.getLevel(ModDimensions.MINIGAMES),
                view.eye().subtract(0.0, EYE, 0.0),
                Vec3.ZERO,
                view.yaw(),
                view.pitch(),
                TeleportTransition.DO_NOTHING));
    }
}
