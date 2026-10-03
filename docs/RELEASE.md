# Create a new release

1. Update `mod_version` in `gradle.properties`.
2. Commit that change.
3. Push that commit.
4. Create a matching annotated git tag in the form `vX.Y.Z` so the tag message becomes the GitHub release notes.
5. For a short release note, run `git tag -a v1.0.1 -m "Summarise the release here"`.
6. For longer release notes, put them in a file and run `git tag -a v1.0.1 -F RELEASE_NOTES.md`.
7. Push the tag with `git push origin v1.0.1`.

The release workflow reads the annotated tag message and uses it as the GitHub release body.
If the tag has no annotation text, GitHub auto-generated release notes are used as a fallback.
GitHub Actions checks out tag pushes in a way that can obscure annotated tag contents, so the workflow fetches the remote tag object before reading the notes.

When updating `fabricmoddingconventions_version`, verify that the matching FabricModdingConventions release is published on Maven Central. Keep every reusable workflow reference in `.github/workflows/` pinned to that version. Validate Brainage Minigames with `flock /tmp/brainage-minigames-gametest.lock ./gradlew --no-daemon build runAllGameTests` before releasing.

`runAllGameTests` runs the Fabric and NeoForge development server suites
(`:fabric:runGameTest`, `:neoforge:runGameTest`), followed by the Fabric production
server and client suites (`:fabric:runProductionServerGameTest`,
`:fabric:runProductionClientGameTest`) and the installed NeoForge production server
suite (`:neoforge:runProductionServerGameTest`). The production runs test the release
JARs, not just the development classpath. Keep NeoForge tests registered through
`RegisterEvent` and `test_instance` data; `RegisterGameTestsEvent` is not posted in
production. If a required mod dependency is added to `neoforge.mods.toml`, declare
it in `neoforge/build.gradle`'s `productionRuntimeMods` as well (except Minecraft and
NeoForge, which the installed server provides).

The release workflow builds both loader JARs and attaches the Fabric and NeoForge artifacts to the GitHub Release.
If `MODRINTH_TOKEN` is configured, the same workflow creates or updates the Modrinth project and publishes both loader JARs. If both `CURSEFORGE_TOKEN` and `CURSEFORGE_PROJECT_ID` are configured, it publishes both loader JARs to CurseForge.
All destinations use the same tag notes. Missing third-party credentials skip only that destination; the GitHub Release still proceeds.
