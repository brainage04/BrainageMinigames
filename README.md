# Brainage Minigames

A server-side mod for Minecraft 26.2 that provides configurable custom events and an Ultra Hardcore (UHC) minigame. Clients do not need to install the mod.

## Requirements

- Minecraft 26.2
- Either Fabric Loader 0.19.3 or newer with Fabric API, or NeoForge 26.2.0.23-beta or newer
  - Install the Fabric API only with the Fabric release.
- Java 25 or newer

## Migrating from the Fabric-only release

Choose exactly one matching loader JAR: the Fabric JAR requires Fabric Loader and Fabric API; the NeoForge JAR requires NeoForge and no Fabric API. Remove the old Brainage Minigames JAR before switching loaders—never install both variants together. The mod ID remains `brainage_minigames`, so its existing configuration and world data paths are preserved. This remains server-side on both loaders; vanilla clients do not install either JAR. A root `./gradlew build` emits both loader-specific artifacts.

## Custom events

Create a custom free-for-all or tournament event with:

```text
/startevent custom <minTeams> <maxTeams> <minPlayersPerTeam> <maxPlayersPerTeam> <isFreeForAll> [kit]
```

The optional `kit` is a resource identifier such as `brainage_minigames:duels`. If it is omitted, the event uses the empty kit. Players can then use:

```text
/joinevent
/watchevent
/leaveevent
```

Operators can control the event with:

```text
/nextphase
/stopevent
/stopcountdown
/teamup <players>
/matchteams add <team1> <team2>
/matchteams list
/matchteams clear
/spawn
```

The mod saves each participant's position, dimension, rotation, velocity, game mode, inventory, status effects, health, hunger, experience, and scoreboard team before moving them into an event. That state is restored when they leave or when the event finishes. Earned rewards are added after restoration.

### In-game kit editor

All kit-management commands require game-master permission:

```text
/minigames kit edit <kit>
/minigames kit give <kit> [players]
/minigames kit delete <kit>
/minigames kit list
```

`edit` opens a six-row container. Put the desired item stacks in the container and close it to save the kit. Use a fully qualified identifier for custom kits, for example:

```text
/minigames kit edit brainage_minigames:duels
/startevent custom 2 4 1 2 false brainage_minigames:duels
```

Edited kits are stored persistently in the world. Editing a bundled kit identifier creates a world-specific override; deleting that override restores the bundled loot-table kit. Bundled identifiers offered by command completion are:

- `brainage_minigames:empty`
- `brainage_minigames:kits/barebones`
- `brainage_minigames:kits/bow`
- `brainage_minigames:kits/classic`
- `brainage_minigames:kits/instant_crossbow`
- `brainage_minigames:kits/instant_firework_crossbow`
- `brainage_minigames:kits/uhc`

## UHC

Players and operators use the `/uhc` command tree:

```text
/uhc
/uhc status
/uhc open
/uhc join
/uhc leave
/uhc start
/uhc stop
```

`open`, `start`, and `stop` require game-master permission. Each UHC session uses a fresh, widely separated area of its dedicated dimension. The dimension is scheduled for regeneration when the server stops. The game preserves and restores player state, starts once at least two lobby participants are ready, shrinks the world border during play, eliminates players on death, and ends when one participant remains.

## Building and verification

```shell
./gradlew build
./gradlew runProductionServerGameTest
```

## License

Brainage Minigames is available under the [MIT License](LICENSE).
