# Conduit 1.5.0

Conduit 1.5.0 adds **native command forwarding** and makes `conduit.toml` **update itself** across
versions. Both changes are backwards-compatible: default behaviour is unchanged, existing configs
keep working untouched, and there are no protocol or client-range changes.

## Command forwarding (new, optional)

Backend servers can now ask the proxy to run a command — handy for Discord bots that act through a
backend, or plugins like TAB that need a proxy-level command. This is a built-in re-implementation
of the proxy side of the [VelocityCommandForward](https://github.com/ItsTauTvyDas/VelocityCommandForward)
plugin, so you no longer install a separate Velocity plugin for it.

* A backend player or console runs `/proxyexec <command>`; Conduit executes it on the proxy **as the
  proxy console** (console-originated) or **as the forwarding player** (player-originated, if still
  online).
* It speaks the **same plugin-messaging protocol** as VelocityCommandForward, so you keep that
  project's **backend** plugin and just remove its Velocity plugin.
* **Only genuine backend connections are honoured** — a client cannot forge these messages to run
  commands. Malformed messages are dropped and logged.

### Enabling / disabling it

The feature is **off by default** — a fresh or upgraded install does nothing until you opt in. Turn
it on in the new `[forwarding]` section of `conduit.toml`:

```toml
[forwarding]
command-forwarding     = true                          # off by default
channel                = "velocity_command_forward:main" # must match the backend plugin
require-permission     = false                          # true → player commands need conduit.forward.execute
log-forwarded-commands = true                           # echo the backend's log line to the console
```

To disable it again, set `command-forwarding = false` (or delete the section) and restart. With
`require-permission = true`, a command forwarded on behalf of a player only runs if that player holds
`conduit.forward.execute`; console-originated commands from a trusted backend console are always
allowed.

> Plugin messaging needs at least one player online for a console-originated backend command to reach
> the proxy — this is a Minecraft limitation, not a Conduit one.

## `conduit.toml` now updates itself

You no longer have to delete or regenerate `conduit.toml` after upgrading Conduit. On every start,
Conduit compares your file against the defaults bundled in the jar and:

* **Adds any options a newer version introduced** — including whole new sections such as this
  release's `[forwarding]` — each with its documented default value and explanatory comment.
* **Never overwrites your values.** Anything you already set (even a value equal to the default) is
  left exactly as-is, and your comments are preserved.
* **Leaves a complete file untouched** — if nothing is missing, the file is not rewritten at all.
* **Handles structural renames** through an internal migration table, so if an option is moved or
  renamed in a future version your existing value is carried across instead of resetting to the
  default.

Any options added are logged at startup, e.g.
`[Conduit] conduit.toml: added 4 new option(s) with defaults: forwarding.*`.

## Configuration changes at a glance

| Section | Key | Default | Notes |
|---------|-----|---------|-------|
| `[forwarding]` | `command-forwarding` | `false` | New. Master switch; off preserves current behaviour. |
| `[forwarding]` | `channel` | `velocity_command_forward:main` | New. Must match the backend plugin. |
| `[forwarding]` | `require-permission` | `false` | New. Gate player-context commands on `conduit.forward.execute`. |
| `[forwarding]` | `log-forwarded-commands` | `true` | New. Echo the backend log line to the console. |

Existing installations pick these up automatically thanks to the self-updating config described
above — no manual edits required.

## Upgrade notes

* Drop in the new jar and restart. `conduit.toml` gains the `[forwarding]` section automatically on
  first start; nothing else changes.
* If you currently run the **VelocityCommandForward Velocity plugin**, remove it and set
  `command-forwarding = true` instead — keep its **backend** plugin as-is.
* No changes to the supported client range, existing plugins, or the wire protocol.
