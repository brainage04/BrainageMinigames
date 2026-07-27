package io.github.brainage04.brainage_minigames.neoforge;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Executes in the dedicated NeoForge GameTest server and verifies loader registration. */
@EventBusSubscriber(modid = BrainageMinigames.MOD_ID)
public final class NeoForgeCommandGameTest {
    @SubscribeEvent
    public static void registerTestFunctions(RegisterEvent event) {
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), Identifier.fromNamespaceAndPath(BrainageMinigames.MOD_ID, "commands"), () -> NeoForgeGameTestFunctions::commands);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), Identifier.fromNamespaceAndPath(BrainageMinigames.MOD_ID, "snapshot"), () -> NeoForgeGameTestFunctions::snapshot);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), Identifier.fromNamespaceAndPath(BrainageMinigames.MOD_ID, "kit"), () -> NeoForgeGameTestFunctions::kit);
    }

    private NeoForgeCommandGameTest() {
    }

    @SubscribeEvent
    public static void commandsAreRegistered(ServerStartedEvent event) {
        var root = event.getServer().getCommands().getDispatcher().getRoot();
        for (String command : new String[]{
                "startevent", "joinevent", "watchevent", "leaveevent", "nextphase", "stopevent",
                "stopcountdown", "teamup", "matchteams", "spawn", "uhc", "minigames"
        }) {
            if (root.getChild(command) == null) {
                throw new AssertionError("NeoForge did not register /" + command);
            }
        }
        if (root.getChild("uhc").getChild("open") == null
                || root.getChild("uhc").getChild("start") == null
                || root.getChild("uhc").getChild("join") == null
                || root.getChild("uhc").getChild("leave") == null
                || root.getChild("uhc").getChild("stop") == null) {
            throw new AssertionError("NeoForge UHC command tree is incomplete");
        }
        var kit = root.getChild("minigames").getChild("kit");
        if (kit == null || kit.getChild("edit") == null || kit.getChild("give") == null
                || kit.getChild("delete") == null || kit.getChild("list") == null) {
            throw new AssertionError("NeoForge kit command tree is incomplete");
        }
    }
}
