# Todo

- Replace the generated starter maps with better custom ones (use maps on hypixel.net/minemen.club as layout references only). Parkour `canopy` is the weakest: a single line of jumps over a large pool.
- SparringBots UHC play (in SparringBots): bots now reach iron routinely (10 of 13 alive at the end of a 10-minute grace in the last 20-bot game) and sometimes diamonds, but mobs kill about half of them (zombies and creepers, mostly at night), no bot has yet made a book, enchanted, gone to the nether or brewed in a live match (each works in GameTests), and PvP is rare. The showcase director often holds static shots (a bot facing a wall) for a minute or more.

## Loader parity findings (2026-09-29)

From running the release NeoForge jar on a real NeoForge 26.2.0.41-beta server and client. Items marked *both loaders* come from shared code.

- [ ] Low: `/duel classic 2v2 Tester2` says "you listed 2" when one name was listed (`DuelRequests.java:153-154` counts the challenger).
