package io.github.brainage04.brainage_minigames.game;

public enum MatchPhase {
    /** Players join and wait in the arena, protected from all damage. */
    LOBBY,
    /** Teams are placed at their spawns and frozen. */
    COUNTDOWN,
    /** The game is being played. */
    ACTIVE,
    /** The result is announced; everyone is restored when it finishes. */
    ENDED
}
