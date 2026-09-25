package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Minemen's Final UHC: the end of a UHC as a duel. Everyone gets the same late-game gear and
 * fights, building, bucketing and burning as they like, on generated terrain inside a small border
 * rather than in a flat box, with no natural regeneration.
 */
public final class FinalUhcGame implements Minigame {
    public static final GameSetting BORDER_SIZE =
            new GameSetting("border_size", 100, 16, 1_000, "Side length of the arena's border");

    private static final Identifier KIT = BrainageMinigames.id("kits/final_uhc");

    private final List<GameSetting> settings;

    public FinalUhcGame() {
        List<GameSetting> all = new ArrayList<>(GameSetting.common(5, 15, false));
        all.add(BORDER_SIZE);
        this.settings = List.copyOf(all);
    }

    @Override
    public String id() {
        return "final_uhc";
    }

    @Override
    public String displayName() {
        return "FinalUHC";
    }

    @Override
    public List<GameSetting> settings() {
        return settings;
    }

    @Override
    public Identifier defaultKit() {
        return KIT;
    }

    @Override
    public GameType playerGameMode() {
        return GameType.SURVIVAL;
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings values) throws MatchException {
        return NaturalArena.open(server, values.get(BORDER_SIZE));
    }

    @Override
    public void tick(Match match) {
        ((NaturalArena) match.arena()).tick(match.onlineMembers(), match.alivePlayers());
    }

    @Override
    public void onRelease(Match match, ServerPlayer player) {
        NaturalArena.restoreBorder(player);
    }

    @Override
    public boolean allowBreak(Match match, ServerPlayer player, BlockPos pos, BlockState state) {
        return match.arena().canBuild(pos);
    }

    @Override
    public void addSidebarLines(Match match, List<Component> lines) {
        lines.add(UhcRules.borderLine(((NaturalArena) match.arena()).size()));
    }
}
