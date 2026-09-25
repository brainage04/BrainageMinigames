package io.github.brainage04.brainage_minigames.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Pending {@code /duel} challenges. A challenger invites the other participants; once every invitee
 * has accepted, a match opens with the participants on their teams in the order they were listed
 * (challenger first) and starts straight away.
 */
public final class DuelRequests {
    /** Players a challenger may list besides themselves. */
    public static final int MAX_INVITEES = 15;

    public static final int EXPIRY_SECONDS = 60;
    private static final Map<UUID, Request> BY_CHALLENGER = new LinkedHashMap<>();

    private DuelRequests() {}

    private static final class Request {
        private final MinecraftServer server;
        private final Minigame game;
        private final TeamLayout layout;
        private final MatchManager.ArenaFactory arenaFactory;

        /** Challenger first, then the invitees in the order they were listed. */
        private final List<UUID> participants;

        private final List<String> names;
        private final Set<UUID> accepted = new HashSet<>();
        private final int expiresAtTick;

        private Request(
                MinecraftServer server,
                Minigame game,
                TeamLayout layout,
                MatchManager.ArenaFactory arenaFactory,
                List<ServerPlayer> participants) {
            this.server = server;
            this.game = game;
            this.layout = layout;
            this.arenaFactory = arenaFactory;
            this.participants = participants.stream().map(ServerPlayer::getUUID).toList();
            this.names = participants.stream().map(ServerPlayer::getScoreboardName).toList();
            this.expiresAtTick = server.getTickCount() + EXPIRY_SECONDS * 20;
        }

        private UUID challenger() {
            return participants.getFirst();
        }

        private String challengerName() {
            return names.getFirst();
        }

        private int invitees() {
            return participants.size() - 1;
        }

        private boolean isInvitee(UUID playerId) {
            return participants.indexOf(playerId) > 0;
        }

        private String title() {
            return game.displayName() + " " + layout;
        }

        /** The teams as they will be filled, e.g. {@code Alice, Bob vs Carol, Dave}. */
        private String lineup() {
            if (layout.isFreeForAll()) {
                return String.join(", ", names) + ", free-for-all";
            }
            List<String> teams = new ArrayList<>();
            int start = 0;
            for (int size : layout.teamSizes()) {
                teams.add(String.join(", ", names.subList(start, start + size)));
                start += size;
            }
            return String.join(" vs ", teams);
        }

        /** Team number for the participant at {@code index}; 0 (no choice) in free-for-all. */
        private int teamNumber(int index) {
            if (layout.isFreeForAll()) {
                return 0;
            }
            int end = 0;
            for (int team = 0; team < layout.teamSizes().size(); team++) {
                end += layout.teamSizes().get(team);
                if (index < end) {
                    return team + 1;
                }
            }
            throw new IllegalStateException("Participant " + index + " does not fit " + layout);
        }

        private void notifyAll(Component message) {
            for (UUID playerId : participants) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    player.sendSystemMessage(message);
                }
            }
        }
    }

    /**
     * Challenges {@code invitees} to a match of {@code game}; {@code arenaFactory} opens the arena
     * once everyone has accepted.
     */
    public static void challenge(
            ServerPlayer challenger,
            Minigame game,
            TeamLayout layout,
            List<ServerPlayer> invitees,
            MatchManager.ArenaFactory arenaFactory)
            throws MatchException {
        MinecraftServer server = challenger.level().getServer();
        expire(server);
        if (invitees.isEmpty() || invitees.size() > MAX_INVITEES) {
            throw new MatchException(
                    "List between 1 and %d other players.".formatted(MAX_INVITEES));
        }
        List<ServerPlayer> participants = new ArrayList<>(invitees.size() + 1);
        participants.add(challenger);
        Set<UUID> seen = new HashSet<>();
        seen.add(challenger.getUUID());
        for (ServerPlayer invitee : invitees) {
            if (invitee.getUUID().equals(challenger.getUUID())) {
                throw new MatchException(
                        "You are already in the duel; list only the other players.");
            }
            if (!seen.add(invitee.getUUID())) {
                throw new MatchException(
                        invitee.getScoreboardName() + " is listed more than once.");
            }
            participants.add(invitee);
        }
        if (!layout.isFreeForAll() && participants.size() != layout.capacity()) {
            throw new MatchException(
                    "A %s duel needs %d players including you; you listed %d."
                            .formatted(layout, layout.capacity(), participants.size()));
        }
        Optional<String> invalid = game.validate(SettingsStorage.resolve(server, game));
        if (invalid.isPresent()) {
            throw new MatchException(
                    "The %s settings are invalid: %s".formatted(game.displayName(), invalid.get()));
        }
        for (ServerPlayer participant : participants) {
            ensureAvailable(participant);
        }

        Request request = new Request(server, game, layout, arenaFactory, participants);
        BY_CHALLENGER.put(challenger.getUUID(), request);

        challenger.sendSystemMessage(
                Component.empty()
                        .append(
                                Component.literal(
                                                "Duel request sent: %s (%s). It expires in %d seconds."
                                                        .formatted(
                                                                request.title(),
                                                                request.lineup(),
                                                                EXPIRY_SECONDS))
                                        .withStyle(ChatFormatting.GOLD))
                        .append(" ")
                        .append(button("[Cancel]", ChatFormatting.RED, "/duel cancel")));

        Component invite =
                Component.empty()
                        .append(
                                Component.literal(
                                                request.challengerName()
                                                        + " challenged you to "
                                                        + request.title())
                                        .withStyle(ChatFormatting.GOLD))
                        .append(
                                Component.literal(" (" + request.lineup() + ")")
                                        .withStyle(ChatFormatting.GRAY))
                        .append(" ")
                        .append(
                                Component.literal("[Accept]")
                                        .withStyle(
                                                style ->
                                                        style.withColor(ChatFormatting.GREEN)
                                                                .withClickEvent(
                                                                        new ClickEvent.RunCommand(
                                                                                "/duel accept "
                                                                                        + request
                                                                                                .challengerName()))
                                                                .withHoverEvent(
                                                                        new HoverEvent.ShowText(
                                                                                Component.literal(
                                                                                        "Players: "
                                                                                                + String
                                                                                                        .join(
                                                                                                                ", ",
                                                                                                                request.names))))))
                        .append(" ")
                        .append(
                                button(
                                        "[Deny]",
                                        ChatFormatting.RED,
                                        "/duel deny " + request.challengerName()));
        for (ServerPlayer invitee : invitees) {
            invitee.sendSystemMessage(invite);
        }
    }

    private static Component button(String label, ChatFormatting color, String command) {
        return Component.literal(label)
                .withStyle(
                        style ->
                                style.withColor(color)
                                        .withClickEvent(new ClickEvent.RunCommand(command)));
    }

    /**
     * Rejects a player who could not join the match right now: unavailable to {@link
     * MatchManager#join} or already part of another pending duel.
     */
    private static void ensureAvailable(ServerPlayer player) throws MatchException {
        String name = player.getScoreboardName();
        Optional<String> reason = MatchManager.unavailableReason(player);
        if (reason.isPresent()) {
            throw new MatchException(name + " " + reason.get() + ".");
        }
        Optional<Request> pending = requestOf(player.getUUID());
        if (pending.isPresent()) {
            throw new MatchException(
                    pending.get().challenger().equals(player.getUUID())
                            ? name + " already has a pending duel request."
                            : "%s is already invited to %s's duel."
                                    .formatted(name, pending.get().challengerName()));
        }
    }

    private static Optional<Request> requestOf(UUID playerId) {
        return BY_CHALLENGER.values().stream()
                .filter(request -> request.participants.contains(playerId))
                .findFirst();
    }

    /** Names of the players whose pending challenge includes {@code invitee}. */
    public static List<String> challengersOf(UUID invitee) {
        return BY_CHALLENGER.values().stream()
                .filter(request -> request.isInvitee(invitee))
                .map(Request::challengerName)
                .toList();
    }

    private static Request invitation(ServerPlayer invitee, ServerPlayer challenger)
            throws MatchException {
        expire(invitee.level().getServer());
        Request request = BY_CHALLENGER.get(challenger.getUUID());
        if (request == null || !request.isInvitee(invitee.getUUID())) {
            throw new MatchException(
                    "You have no pending duel request from "
                            + challenger.getScoreboardName()
                            + ".");
        }
        return request;
    }

    /**
     * Accepts a challenge; the last acceptance opens the match, puts everyone on their teams and
     * starts it.
     */
    public static void accept(ServerPlayer invitee, ServerPlayer challenger) throws MatchException {
        Request request = invitation(invitee, challenger);
        if (!request.accepted.add(invitee.getUUID())) {
            throw new MatchException(
                    "You already accepted " + request.challengerName() + "'s duel request.");
        }
        if (request.accepted.size() < request.invitees()) {
            request.notifyAll(
                    Component.literal(
                                    "%s accepted %s's duel request (%d/%d)."
                                            .formatted(
                                                    invitee.getScoreboardName(),
                                                    request.challengerName(),
                                                    request.accepted.size(),
                                                    request.invitees()))
                            .withStyle(ChatFormatting.GREEN));
            return;
        }
        BY_CHALLENGER.remove(request.challenger());
        try {
            List<ServerPlayer> players = new ArrayList<>(request.participants.size());
            for (int index = 0; index < request.participants.size(); index++) {
                ServerPlayer player =
                        request.server.getPlayerList().getPlayer(request.participants.get(index));
                if (player == null) {
                    throw new MatchException(request.names.get(index) + " is no longer online.");
                }
                ensureAvailable(player);
                players.add(player);
            }
            startMatch(request, players);
        } catch (MatchException exception) {
            request.notifyAll(
                    Component.literal(
                                    "%s's duel was cancelled: %s"
                                            .formatted(
                                                    request.challengerName(),
                                                    exception.getMessage()))
                            .withStyle(ChatFormatting.RED));
        }
    }

    private static void startMatch(Request request, List<ServerPlayer> players)
            throws MatchException {
        Match match =
                MatchManager.openPrivate(
                        request.server,
                        request.game,
                        request.layout,
                        request.arenaFactory,
                        request.participants);
        try {
            for (int index = 0; index < players.size(); index++) {
                MatchManager.join(players.get(index), match, request.teamNumber(index));
            }
            // Fixed layouts start themselves once the last slot fills.
            if (match.phase() == MatchPhase.LOBBY) {
                match.start();
            }
        } catch (MatchException exception) {
            MatchManager.stop(match);
            throw exception;
        }
    }

    public static void deny(ServerPlayer invitee, ServerPlayer challenger) throws MatchException {
        Request request = invitation(invitee, challenger);
        cancel(
                request,
                "%s denied %s's duel request."
                        .formatted(invitee.getScoreboardName(), request.challengerName()));
    }

    /** Withdraws the challenger's pending request. */
    public static void cancel(ServerPlayer challenger) throws MatchException {
        expire(challenger.level().getServer());
        Request request = BY_CHALLENGER.get(challenger.getUUID());
        if (request == null) {
            throw new MatchException("You have no pending duel request.");
        }
        cancel(request, request.challengerName() + " cancelled their duel request.");
    }

    private static void cancel(Request request, String reason) {
        BY_CHALLENGER.remove(request.challenger());
        request.notifyAll(Component.literal(reason).withStyle(ChatFormatting.RED));
    }

    private static void expire(MinecraftServer server) {
        for (Request request : List.copyOf(BY_CHALLENGER.values())) {
            if (server.getTickCount() >= request.expiresAtTick) {
                cancel(request, request.challengerName() + "'s duel request expired.");
            }
        }
    }

    public static void tick(MinecraftServer server) {
        expire(server);
    }

    /** A participant leaving the server cancels the request they are part of. */
    public static void handleDisconnect(ServerPlayer player) {
        requestOf(player.getUUID())
                .ifPresent(
                        request ->
                                cancel(
                                        request,
                                        "%s's duel request was cancelled: %s left the game."
                                                .formatted(
                                                        request.challengerName(),
                                                        player.getScoreboardName())));
    }

    public static void clear() {
        BY_CHALLENGER.clear();
    }
}
