# Todo

- Replace the remaining generated starter maps with better custom ones (use maps on hypixel.net/minemen.club as layout references only; render them with the map previews in the README's Maps section). Weakest first: Battle Rush `lilypond` (a bare island with a pond), the Bridge maps `grove`, `basalt` and `compass` (flat islands; no raised base or structure around the goal and cage), Pearl Fight `skyreach`, SkyWars `frostbite` (two small islands and a mid, little cover).
- SparringBots UHC play (in SparringBots): bots now reach iron routinely (10 of 13 alive at the end of a 10-minute grace in the last 20-bot game) and sometimes diamonds, but mobs kill about half of them (zombies and creepers, mostly at night), no bot has yet made a book, enchanted, gone to the nether or brewed in a live match (each works in GameTests), and PvP is rare. The showcase director often holds static shots (a bot facing a wall) for a minute or more.
- UHC teams: support team sizes 1-4 in UHC (Hypixel has solo and teams of 3). Check what the layout system already allows for UHC (README says every game supports every layout; verify spawns, team chat, friendly fire, win condition, anti-janitor and bot teammate behaviour).

## Loader parity findings (2026-09-29)

From running the release NeoForge jar on a real NeoForge 26.2.0.41-beta server and client. Items marked *both loaders* come from shared code.

- [ ] Low: `/duel classic 2v2 Tester2` says "you listed 2" when one name was listed (`DuelRequests.java:153-154` counts the challenger).
