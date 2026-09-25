package io.github.brainage04.brainage_minigames.game;

/** A request the match system refused; the message is shown to the player who made it. */
public final class MatchException extends Exception {
    public MatchException(String message) {
        super(message, null, false, false);
    }
}
