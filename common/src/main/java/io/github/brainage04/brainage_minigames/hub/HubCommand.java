package io.github.brainage04.brainage_minigames.hub;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /hub} and {@code /spawn} take any player to the hub, leaving their match first.
 * Operators manage the hub with {@code /hub set [radius]}, {@code /hub radius <blocks>}, {@code /hub
 * build}, {@code /hub on}, {@code /hub off} and {@code /hub info}.
 */
public final class HubCommand {
    private static final String RADIUS = "radius";

    private HubCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("spawn").executes(context -> go(context.getSource())));
        dispatcher.register(literal("hub")
                .executes(context -> go(context.getSource()))
                .then(literal("info").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> info(context.getSource())))
                .then(literal("set").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> set(context.getSource(), 0))
                        .then(argument(RADIUS, IntegerArgumentType.integer(1, 1024))
                                .executes(context -> set(context.getSource(), IntegerArgumentType.getInteger(context, RADIUS)))))
                .then(literal("radius").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(argument(RADIUS, IntegerArgumentType.integer(1, 1024))
                                .executes(context -> radius(context.getSource(), IntegerArgumentType.getInteger(context, RADIUS)))))
                .then(literal("build").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> build(context.getSource())))
                .then(literal("on").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> enable(context.getSource(), true)))
                .then(literal("off").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> enable(context.getSource(), false))));
    }

    private static int go(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!Hub.enabled()) {
            source.sendFailure(Component.literal("This server has no hub."));
            return 0;
        }
        Optional<Match> match = MatchManager.matchOf(player.getUUID());
        if (match.isPresent()) {
            try {
                MatchManager.leave(player);
            } catch (MatchException exception) {
                source.sendFailure(Component.literal(exception.getMessage()));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("You left ").append(match.get().title()).append("."), false);
        }
        if (!Hub.sendToHub(player)) {
            source.sendFailure(Component.literal("The hub could not be reached."));
            return 0;
        }
        return 1;
    }

    private static int info(CommandSourceStack source) {
        Hub.Config config = Hub.config();
        if (config == null) {
            source.sendSuccess(() -> Component.literal("This world has no hub; /hub set or /hub build makes one."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Hub %s: spawn %.1f %.1f %.1f in %s, radius %d.".formatted(
                config.enabled() ? "on" : "off", config.spawn().x(), config.spawn().y(), config.spawn().z(),
                config.dimension().identifier(), config.radius())), false);
        return 1;
    }

    /** Makes the operator's position the hub spawn, keeping the radius unless one is given. */
    private static int set(CommandSourceStack source, int radius) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Hub.Config previous = Hub.config();
        int newRadius = radius > 0 ? radius : previous != null ? previous.radius() : Hub.DEFAULT_RADIUS;
        Hub.set(source.getServer(), new Hub.Config(player.level().dimension(), player.position(), player.getYRot(), newRadius, true));
        return info(source);
    }

    private static int radius(CommandSourceStack source, int radius) {
        Hub.Config config = Hub.config();
        if (config == null) return info(source);
        Hub.set(source.getServer(), config.withRadius(radius));
        return info(source);
    }

    /** Builds a hub around the operator, makes it the hub and moves them onto it. */
    private static int build(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Vec3 spawn = HubBuilder.build(player.level(), player.blockPosition());
        Hub.Config previous = Hub.config();
        int radius = previous != null ? previous.radius() : Hub.DEFAULT_RADIUS;
        Hub.set(source.getServer(), new Hub.Config(player.level().dimension(), spawn, HubBuilder.SPAWN_YAW, radius, true));
        Hub.sendToHub(player);
        return info(source);
    }

    private static int enable(CommandSourceStack source, boolean enabled) {
        Hub.Config config = Hub.config();
        if (config == null) return info(source);
        Hub.set(source.getServer(), config.withEnabled(enabled));
        return info(source);
    }
}
