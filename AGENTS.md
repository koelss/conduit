<claude-mem-context>
# Memory Context

# [conduit] recent context, 2026-05-17 11:53pm EDT

No previous sessions found.
</claude-mem-context>

## Cursor Cloud specific instructions

Conduit is a Minecraft **Velocity-CTD** proxy fork built with Gradle on **Java 25**. It is a headless
server/console application (no GUI); test and demo it via the terminal console.

**Generated source tree (important).** Only `overlays/`, `additions/`, `scripts/`, and the root Gradle
files are tracked. The actual build modules (`api/`, `proxy/`, `native/`, `bootstrap/`,
`permission-integration/`, `config/`, `build-logic/`, and the `gradlew` wrapper) are `.gitignore`d and
**materialized by `scripts/setup.sh`**, which shallow-clones upstream Velocity-CTD (cached in
`.upstream-velocity/`) and then overlays `overlays/` + `additions/` on top. The VM startup/update
script already runs `scripts/setup.sh`, so the tree is present when a session starts. If you `git pull`
new changes mid-session, re-run `./scripts/setup.sh` before building. Never edit files under the
generated module dirs — they are overwritten by `setup.sh`; put replacements in `overlays/` and new
files in `additions/` (see README "Adding new features").

**Build / lint / test:** `./gradlew build` — compiles all modules, runs Checkstyle (lint) and the
JUnit suite. Artifact: `proxy/build/libs/conduit-<conduit.version>.jar` (version from
`gradle.properties`).

**Run (dev):** from a working directory run
`java -Xms512m -Xmx512m -XX:+UseG1GC -jar proxy/build/libs/conduit-<version>.jar` (or `./gradlew
runShadow`, which uses `proxy/run/`). First run generates `conduit.toml` + `velocity.toml` and binds
`0.0.0.0:25565`. Useful console commands: `conduit diagnostics | health | doctor`, `conduit
maintenance on|off|status`, `velocity version`. It also installs the bundled LuckPerms + spark plugins
into `plugins/` on first run.

**Non-obvious gotcha — bundled plugin downloads rotate.** `gradle.properties` pins the bundled
LuckPerms and spark Velocity jars by exact URL **and sha256**, and the `:velocity-proxy:downloadBundled*`
tasks fail the whole build if a URL 404s. LuckPerms prunes old build numbers from
`download.luckperms.net`, so a pinned build eventually disappears and breaks the build/run. When that
happens, refresh `conduit.luckperms.velocity.url` + `.sha256` (and `.version`) from the current
`downloads.velocity` value at <https://metadata.luckperms.net/data/all>. This is an upstream dependency
rotation, not a code bug.