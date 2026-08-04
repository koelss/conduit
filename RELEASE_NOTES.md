# Conduit 1.4.0

Conduit 1.4.0 makes the proxy present itself consistently as **Conduit** and adds a built-in,
modular **update checker**. There are no gameplay or protocol changes — existing configs, plugins,
and the supported client range are untouched.

## Conduit branding

The proxy now identifies itself as **Conduit** everywhere a user can see it, instead of
`Conduit-CTD` or `Velocity`:

* The startup banner (`Booting up Conduit …`), the server-list/ping identity, `/velocity info`, and
  the virtual-plugin descriptions all report `Conduit` / `Conduit Contributors`.
* The jar manifest carries `Implementation-Title: Conduit` and `Implementation-Vendor: Conduit
  Contributors`.
* User-facing links (the GitHub link in `/velocity info`, the bootstrap fat-jar download hint) point
  at `github.com/tame-gg/conduit`.
* Console and command strings that identify the running software — the invalid-config error, the
  `/velocity reload` messages, and the `/velocity info` update line — no longer say “Velocity”.

Package names, the internal `com.velocityctd` namespace, public APIs, license/copyright headers, the
`velocity.toml` filename, and the “Velocity forwarding” mode name are intentionally left unchanged
for compatibility and attribution.

The old **“You are running a development build of Velocity-CTD”** startup message is gone. The
`/velocity info` development-build notice now reads *“a development build of Conduit”* and only
appears for genuine development builds.

## Update checker

Conduit can now tell you when a newer release is available:

* Checks **GitHub Releases** asynchronously after startup — it never blocks the proxy.
* Caches the result (6 hours by default), fails quietly if GitHub is unreachable, and respects API
  rate limits.
* Compares **semantic versions** correctly and ignores pre-releases unless the running build is
  itself a pre-release.
* Tells staff holding `conduit.update.notify` when they join: the running version, the latest
  version, whether you’re outdated, exactly how many releases behind you are, and a link to the
  release. A single summary is also logged to the console at startup.
* Built around an `UpdateProvider` interface (with a `GitHubReleaseProvider` implementation) so a
  different update source can be added later without rewriting the checker.

Configure it in the new `[update]` section of `conduit.toml` (enable/disable, repository, startup
and join notifications, pre-release handling, and cache duration).
