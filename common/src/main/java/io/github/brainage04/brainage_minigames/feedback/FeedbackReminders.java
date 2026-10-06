package io.github.brainage04.brainage_minigames.feedback;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;

/**
 * Points players at {@code /feedback}: a welcome on their first join, then a reminder every {@link
 * #INTERVAL_MINUTES} of play unless they turned reminders off. Reminders wait while the player is
 * playing in a match. Who has been welcomed and who turned reminders off is stored per UUID in the
 * world's command storage.
 */
public final class FeedbackReminders {
    /** {@code brainage_minigames:feedback_reminder_minutes}; 0 turns the reminders off for everyone. */
    public static final GameRule<Integer> INTERVAL_MINUTES = new GameRule<>(
            GameRuleCategory.MISC, GameRuleType.INT, IntegerArgumentType.integer(0),
            GameRuleTypeVisitor::visitInteger, Codec.intRange(0, Integer.MAX_VALUE),
            Integer::intValue, 60, FeatureFlagSet.of());

    private static final Identifier STORAGE_ID = BrainageMinigames.id("feedback");
    private static final String WELCOMED_KEY = "welcomed";
    private static final String REMINDERS_OFF_KEY = "reminders_off";
    private static final Codec<List<UUID>> UUIDS = UUIDUtil.STRING_CODEC.listOf();

    /** Server tick at which each online player is next reminded. */
    private static final Map<UUID, Integer> DUE = new HashMap<>();

    private FeedbackReminders() {}

    public static void register(BiConsumer<Identifier, GameRule<?>> registry) {
        registry.accept(BrainageMinigames.id("feedback_reminder_minutes"), INTERVAL_MINUTES);
    }

    /** Whether the player receives the periodic reminders; on until they turn them off. */
    public static boolean enabled(MinecraftServer server, UUID playerId) {
        return !read(server, REMINDERS_OFF_KEY).contains(playerId);
    }

    public static void setEnabled(MinecraftServer server, UUID playerId, boolean enabled) {
        List<UUID> off = read(server, REMINDERS_OFF_KEY);
        off.remove(playerId);
        if (!enabled) off.add(playerId);
        write(server, REMINDERS_OFF_KEY, off);
    }

    /** Welcomes a player joining for the first time and schedules their first reminder. */
    public static void playerJoined(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        List<UUID> welcomed = read(server, WELCOMED_KEY);
        if (!welcomed.contains(player.getUUID())) {
            welcomed.add(player.getUUID());
            write(server, WELCOMED_KEY, welcomed);
            player.sendSystemMessage(welcome());
        }
        schedule(player);
    }

    public static void playerLeft(ServerPlayer player) {
        DUE.remove(player.getUUID());
    }

    public static void clear() {
        DUE.clear();
    }

    public static void tick(MinecraftServer server) {
        if (DUE.isEmpty()) return;
        int now = server.getTickCount();
        for (Map.Entry<UUID, Integer> due : List.copyOf(DUE.entrySet())) {
            if (now < due.getValue()) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(due.getKey());
            if (player == null) {
                DUE.remove(due.getKey());
                continue;
            }
            if (MatchManager.activeMatch(player.getUUID()) != null) continue;
            if (enabled(server, player.getUUID())) player.sendSystemMessage(reminder());
            schedule(player);
        }
    }

    /** Schedules the player's next reminder one interval from now; none while the interval is 0. */
    static void schedule(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        int minutes = server.getGameRules().get(INTERVAL_MINUTES);
        if (minutes <= 0) {
            DUE.remove(player.getUUID());
        } else {
            DUE.put(player.getUUID(), server.getTickCount() + minutes * 60 * 20);
        }
    }

    /** Makes the player's next reminder due at {@code tick}; GameTests use this to skip the wait. */
    static void dueAt(ServerPlayer player, int tick) {
        DUE.put(player.getUUID(), tick);
    }

    static Integer dueTick(UUID playerId) {
        return DUE.get(playerId);
    }

    static Component welcome() {
        return Component.empty()
                .append(Component.literal("Welcome to the Brainage Minigames playtest! ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(
                                "Type /minigames to find a game and /hub to come back here. "
                                        + "Found a bug or have an idea? Tell us with /feedback <message>. ")
                        .withStyle(ChatFormatting.YELLOW))
                .append(sendButton())
                .append(" ")
                .append(turnOffButton());
    }

    static Component reminder() {
        return Component.empty()
                .append(Component.literal("Enjoying the playtest? Send bugs and ideas with /feedback <message>. ")
                        .withStyle(ChatFormatting.YELLOW))
                .append(sendButton())
                .append(" ")
                .append(turnOffButton());
    }

    static MutableComponent turnOffButton() {
        return button("[Turn off reminders]", ChatFormatting.GRAY, "/feedback reminders off",
                "Stop the hourly /feedback reminder; /feedback reminders on turns it back on",
                true);
    }

    static MutableComponent turnOnButton() {
        return button("[Turn reminders back on]", ChatFormatting.GREEN, "/feedback reminders on",
                "Remind me about /feedback every hour", true);
    }

    private static MutableComponent sendButton() {
        return button("[Send feedback]", ChatFormatting.GREEN, "/feedback ",
                "Start typing /feedback <message>", false);
    }

    private static MutableComponent button(String label, ChatFormatting color, String command, String hover, boolean run) {
        return Component.literal(label).withStyle(style -> style.withColor(color)
                .withClickEvent(run ? new ClickEvent.RunCommand(command) : new ClickEvent.SuggestCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    private static List<UUID> read(MinecraftServer server, String key) {
        return new ArrayList<>(server.getCommandStorage().get(STORAGE_ID).read(key, UUIDS).orElse(List.of()));
    }

    private static void write(MinecraftServer server, String key, List<UUID> ids) {
        CompoundTag root = server.getCommandStorage().get(STORAGE_ID);
        root.store(key, UUIDS, ids);
        server.getCommandStorage().set(STORAGE_ID, root);
    }
}
