package io.github.brainage04.brainage_minigames;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;

/** Keeps asynchronous fixtures alive until completion and isolates their generation wait. */
public final class GameTestLifecycle {
    private static final long PREPARATION_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(3);
    private GameTestLifecycle() {}

    public static void afterTest(GameTestHelper context, Runnable cleanup) {
        info(context).addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo test) {}
            @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { cleanup.run(); }
            @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { cleanup.run(); }
            @Override public void testAddedForRerun(GameTestInfo previous, GameTestInfo next, GameTestRunner runner) {}
        });
    }

    /** Worker-thread generation is measured in wall time, not accelerated GameTest ticks. */
    public static void awaitPreparation(GameTestHelper context, BooleanSupplier ready, Runnable action) {
        GameTestInfo info = info(context);
        Field tickCount;
        try {
            tickCount = GameTestInfo.class.getDeclaredField("tickCount");
            tickCount.setAccessible(true);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot isolate GameTest preparation time", exception);
        }
        int startTick = info.getTick();
        long started = System.nanoTime();
        context.startSequence().thenWaitUntil(() -> {
            if (ready.getAsBoolean()) return;
            if (System.nanoTime() - started >= PREPARATION_TIMEOUT_NANOS) {
                var error = context.assertionException("Terrain preparation exceeded three real-time minutes");
                info.fail(error);
                throw error;
            }
            try {
                tickCount.setInt(info, startTick);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot hold GameTest preparation time", exception);
            }
            context.assertTrue(false, "Terrain preparation is not finished");
        }).thenExecute(action);
    }

    private static GameTestInfo info(GameTestHelper context) {
        try {
            Field field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            return (GameTestInfo) field.get(context);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot access GameTest lifecycle", exception);
        }
    }
}
