package io.github.brainage04.brainage_minigames.game.race;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.Match;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Hypixel's Parkour Duels: everyone runs the same course from a shared start line, through every
 * checkpoint in order, and the first to reach the finish wins. A fall sends a runner back to their
 * last checkpoint. The kit's feather is a boost that throws the runner forwards and up, on a
 * cooldown; the pressure plate returns them to their checkpoint at once.
 */
public final class ParkourGame extends RaceGame {
    public static final GameSetting BOOST_COOLDOWN =
            new GameSetting(
                    "boost_cooldown_seconds",
                    15,
                    1,
                    600,
                    "Seconds before a runner can use the boost feather again");

    /** Blocks below the last checkpoint a runner may drop before counting as fallen. */
    private static final double FALL_MARGIN = 5.0;

    private static final double BOOST_SPEED = 0.8;
    private static final double BOOST_LIFT = 0.5;

    public ParkourGame() {
        super("parkour", "Parkour", BrainageMinigames.id("kits/parkour"), List.of(BOOST_COOLDOWN));
    }

    @Override
    protected int laps(GameSettings settings) {
        return 1;
    }

    @Override
    protected double fallMargin() {
        return FALL_MARGIN;
    }

    @Override
    protected void returnToCheckpoint(Match match, ServerPlayer player, Racer racer) {
        teleportToCheckpoint(match, player, racer);
        player.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.5F, 1.2F);
    }

    @Override
    public InteractionResult onUseItem(
            Match match, ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (stack.is(Items.FEATHER)) {
            if (player.getCooldowns().isOnCooldown(stack)) {
                return InteractionResult.FAIL;
            }
            Vec3 look = player.getLookAngle();
            Vec3 horizontal = new Vec3(look.x(), 0.0, look.z());
            if (horizontal.lengthSqr() < 1.0E-4) {
                horizontal = Vec3.directionFromRotation(0.0F, player.getYRot());
            }
            Vec3 push = horizontal.normalize().scale(BOOST_SPEED);
            player.setDeltaMovement(push.x(), BOOST_LIFT, push.z());
            player.hurtMarked = true;
            player.getCooldowns().addCooldown(stack, match.settings().get(BOOST_COOLDOWN) * 20);
            player.playSound(SoundEvents.BREEZE_JUMP, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.HEAVY_WEIGHTED_PRESSURE_PLATE)) {
            progress(match, player.getUUID())
                    .ifPresent(
                            progress -> {
                                onRespawn(match, player);
                                player.sendSystemMessage(
                                        Component.literal("Back to your checkpoint.")
                                                .withStyle(ChatFormatting.GRAY),
                                        true);
                            });
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
