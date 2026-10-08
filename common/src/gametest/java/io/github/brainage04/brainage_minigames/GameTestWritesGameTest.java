package io.github.brainage04.brainage_minigames;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * GameTest worlds are thrown away, so the server never asks their levels to write region files
 * synchronously; see {@code GameTestWritesMixin}.
 */
public final class GameTestWritesGameTest {
    private GameTestWritesGameTest() {}

    public static void worldsWriteRegionsAsynchronously(GameTestHelper context) {
        context.assertFalse(context.getLevel().getServer().forceSynchronousWrites(),
                "The server writes its disposable GameTest world's region files synchronously");
        context.succeed();
    }
}
