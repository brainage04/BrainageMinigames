package io.github.brainage04.brainage_minigames.game.bedwars;

import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsGame.State;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsGame.TeamState;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Cost;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsShop.Currency;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;

/**
 * Hypixel's enemy tracker: everyone spawns with a compass (in their Hotbar Manager's Compass slot)
 * that opens the Tracker Shop. Once every enemy bed is gone a player may buy tracking of one enemy
 * team for two emeralds; until they die their compass points at that team's nearest player and the
 * action bar says how far away they are.
 */
public final class BedWarsTracker {
    public static final String ABILITY = "tracker";
    public static final String TITLE = "Purchase Enemy Tracker";
    public static final Cost COST = new Cost(Currency.EMERALD, 2);
    /** Ticks between updates of the compasses' needles. */
    private static final int UPDATE_TICKS = 10;

    private BedWarsTracker() {}

    static ItemStack compass() {
        return BedWarsShop.tagged(Items.COMPASS, ABILITY, "Compass (Right Click)");
    }

    /** The compass into the first free slot the player's Hotbar Manager keeps for it; none without such a slot. */
    static void giveCompass(ServerPlayer player) {
        List<Integer> slots = BedWarsHotbar.slotsFor(player.level().getServer(), player.getUUID(), BedWarsHotbar.Category.COMPASS);
        if (slots.isEmpty()) return;
        var inventory = player.getInventory();
        for (int slot : slots) {
            if (inventory.getItem(slot).isEmpty()) {
                inventory.setItem(slot, compass());
                return;
            }
        }
        inventory.add(compass());
    }

    /** Why {@code player} cannot track {@code team} now, or empty when they can. */
    static Optional<String> refusal(Match match, State state, ServerPlayer player, int team) {
        int own = BedWarsGame.teamOf(match, player);
        if (team == own || !state.teams.containsKey(team)) return Optional.of("You can't track that team!");
        for (TeamState other : state.teams.values()) {
            if (other.number != own && other.bedStanding()) return Optional.of("Unlocks when all enemy beds are destroyed!");
        }
        if (Integer.valueOf(team).equals(state.tracking.get(player.getUUID()))) return Optional.of("You are already tracking this team!");
        return Optional.empty();
    }

    static void buy(BedWarsGame game, Match match, State state, ServerPlayer player, int team) throws MatchException {
        Optional<String> refusal = refusal(match, state, player, team);
        if (refusal.isPresent()) throw new MatchException(refusal.get());
        game.spend(match, state, player, COST);
        state.tracking.put(player.getUUID(), team);
        player.sendSystemMessage(Component.literal("Your compass is now tracking ").withStyle(ChatFormatting.GREEN)
                .append(BedWarsGame.teamName(match, team)).append(Component.literal("!").withStyle(ChatFormatting.GREEN)));
        player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 2.0F);
        update(match, state, player, team);
    }

    /** Points every tracking player's compass at the nearest player of the team they track. */
    static void tick(Match match, State state, int now) {
        if (now % UPDATE_TICKS != 0) return;
        for (Iterator<Map.Entry<UUID, Integer>> entries = state.tracking.entrySet().iterator(); entries.hasNext(); ) {
            Map.Entry<UUID, Integer> entry = entries.next();
            ServerPlayer player = match.server().getPlayerList().getPlayer(entry.getKey());
            if (player == null || !match.isAlive(entry.getKey())) {
                entries.remove();
                continue;
            }
            if (!update(match, state, player, entry.getValue())) entries.remove();
        }
    }

    /** Aims the player's compasses at the nearest player of {@code team}; false once that team has nobody left. */
    private static boolean update(Match match, State state, ServerPlayer player, int team) {
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer other : match.alivePlayers()) {
            if (other.isSpectator() || BedWarsGame.teamOf(match, other) != team || state.respawning.containsKey(other.getUUID())) continue;
            double distance = other.distanceToSqr(player);
            if (distance < best) {
                best = distance;
                nearest = other;
            }
        }
        if (nearest == null) {
            player.sendSystemMessage(Component.literal("Nobody of the team you tracked is left.").withStyle(ChatFormatting.RED), true);
            return false;
        }
        LodestoneTracker target = new LodestoneTracker(Optional.of(GlobalPos.of(nearest.level().dimension(), nearest.blockPosition())), false);
        for (ItemStack stack : player.getInventory()) {
            if (BedWarsShop.ability(stack).equals(ABILITY)) stack.set(DataComponents.LODESTONE_TRACKER, target);
        }
        player.sendSystemMessage(Component.literal("Tracking: ").withStyle(ChatFormatting.WHITE)
                .append(BedWarsGame.teamName(match, team))
                .append(Component.literal(" - Distance: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(Math.round(Math.sqrt(best)) + "m").withStyle(ChatFormatting.GREEN)), true);
        return true;
    }
}
