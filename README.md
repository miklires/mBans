<div align="center">
  <h1>mBans</h1>
  <p>Cross-server punishment management for Paper, Purpur, Folia, and Velocity networks.</p>

  <p>
    <a href="https://papermc.io/software/paper"><img alt="Paper" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg"></a>
    <a href="https://purpurmc.org"><img alt="Purpur" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/purpur_vector.svg"></a>
    <a href="https://papermc.io/software/folia"><img alt="Folia" height="56" src="https://raw.githubusercontent.com/miklires/mBans/main/docs/assets/folia-available.png"></a>
    <a href="https://papermc.io/software/velocity"><img alt="Velocity" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/velocity_vector.svg"></a>
  </p>

  <p>
    <a href="https://github.com/miklires/mBans"><img alt="GitHub" src="https://tr7zw.github.io/uikit/social_buttons_icon/Github-Button-64.png"></a>
    <a href="https://modrinth.com/plugin/mbans"><img alt="Modrinth" src="https://tr7zw.github.io/uikit/social_buttons_icon/Modrinth-Button-64.png"></a>
  </p>

  <p>
    <a href="https://bstats.org/plugin/bukkit/mBans/33351"><img alt="bStats" src="https://img.shields.io/badge/bStats-33351-2F9BE6?style=for-the-badge"></a>
    <a href="https://github.com/miklires/mBans/releases/tag/v1.0.0"><img alt="Release 1.0.0" src="https://img.shields.io/github/v/release/miklires/mBans?style=for-the-badge"></a>
    <img alt="Java 25" src="https://img.shields.io/badge/Java-25-5382A1?style=for-the-badge">
  </p>
</div>

The backend plugin supports Paper, Purpur, and Folia 26.2. The proxy module blocks banned accounts before they enter a Velocity network.

## What it does

- permanent and temporary bans, IP bans, mutes, warnings, and kicks
- UUID-first records with online lookup, local player history, and an asynchronous Mojang fallback
- H2 and SQLite for a single server; MySQL, MariaDB, and PostgreSQL for shared networks
- polling-based cross-server synchronization and a separate Velocity login check
- punishment history, staff history, rollback, notes, IP exceptions, alt detection, and exports
- silent punishments, reason templates, evidence links, and `-last` chat evidence
- configurable warning escalation and moderator immunity levels
- offline warning delivery and generated appeal IDs
- Discord embeds with per-type webhooks and an optional appeal button
- optional local GeoLite2 country filtering and an authenticated read-only REST API
- vanilla JSON and adaptive JDBC migration with dry-run support
- English and Russian messages, bStats, and Modrinth update checks

## Requirements

- Java 25
- Paper, Purpur, or Folia 26.2
- Velocity 3.4 when using `mBans-Velocity-1.0.0.jar`

No external database is required for one backend. A network installation must use one shared MySQL, MariaDB, or PostgreSQL database. Do not share an H2 or SQLite file between processes.

## Install on one server

1. Put `mBans-1.0.0.jar` in the server `plugins` directory.
2. Start the server once.
3. Edit `plugins/mBans/config.yml` and restart when changing storage or network settings.

H2 is selected by default. Player messages are loaded from `lang/en_US.yml`; set `language.default: ru_RU` to use the bundled Russian file.

## Install on a Velocity network

1. Create one MySQL, MariaDB, or PostgreSQL database.
2. Install `mBans-1.0.0.jar` on every backend.
3. Configure the same database on every backend and give each one a different `network.server-name`.
4. Install `mBans-Velocity-1.0.0.jar` in the Velocity `plugins` directory.
5. Start Velocity once and configure `plugins/mbans-velocity/config.properties` with the same JDBC connection.
6. Restart the proxy and all backends.

The Velocity module reads the shared punishment tables directly. Install and initialize a backend first so that the schema exists. See [the network guide](docs/NETWORK.md) for complete examples.

## Configuration

The main sections are:

- `storage`: local file, database engine, connection credentials, pool limits, and optional complete JDBC URL
- `network`: unique backend name and database journal polling interval
- `templates` and `durations`: reusable reasons and GUI duration choices
- `escalation`: count, time window, resulting action, duration, and reason
- `immunity` and `exemptions`: permission levels, excluded worlds, and IPv4/IPv6 CIDR ranges
- `discord`: default or per-punishment webhooks and an appeal URL containing `<appeal_id>`
- `chat-evidence`: in-memory message count available to `-last N`
- `geoip`: local MMDB filename and ISO country allow/block lists
- `rest-api`: localhost bind, port, and bearer token
- `metrics` and `updates`: bStats and Modrinth update discovery

Missing defaults are added without replacing existing values. `/mbans reload` reloads messages and safe settings; storage, network, REST, and GeoIP changes require a restart. See [configuration](docs/CONFIGURATION.md).

## Commands

| Command | Purpose |
|---|---|
| `/ban <player> [duration] <reason>` | Ban a player permanently or temporarily |
| `/tempban <player> <duration> <reason>` | Require a temporary ban duration |
| `/unban <player> [reason]` | Remove an active ban |
| `/banip <player\|ip> [duration] <reason>` | Ban an IP address |
| `/unbanip <player\|ip>` | Remove an IP ban |
| `/mute`, `/tempmute`, `/unmute` | Manage chat mutes |
| `/warn <player> <reason>` | Add a warning and evaluate escalation rules |
| `/unwarn <player> <id\|all>` | Remove one or all active warnings |
| `/kick <player> <reason>` | Remove an online player |
| `/history <player> [page]` | View player punishment history |
| `/staffhistory <staff> [page]` | View actions issued by a moderator |
| `/check <player>` | Show active ban, mute, and warning state |
| `/banlist [page]` | List active bans |
| `/alts <player>` | Find accounts with the same recorded IP; alias `/dupeip` |
| `/muser <player>` | Open the moderation GUI, including offline records |
| `/mbans rollback <staff> [time]` | Revoke recent punishments from one moderator |
| `/mbans allow <player> [ip-ban-id]` | Exempt one UUID from its active IP ban |
| `/mbans note <player> <text>` | Add a private staff note |
| `/mbans notes <player>` | Read recent staff notes |
| `/mbans stats <staff>` | Show staff action counts |
| `/mbans import ...` | Preview or import legacy data |
| `/mbans export <player> [json\|csv]` | Export a player's history |
| `/mbans reload` | Reload safe configuration and language values |

Durations accept combined units such as `30m`, `2h`, `7d`, and `1mo`. Ban, mute, warning, and IP-ban commands accept `-s` and `--evidence=<url>`. Ban, mute, and warning commands also accept `-last <count>` to attach recent chat lines. A template name can replace the reason, for example `/ban Steve cheat`.

Every administrative command has a matching `mbans.command.<name>` permission. `mbans.admin` grants the complete command set. Sensitive IP output requires `mbans.view.ip`; alt notifications require `mbans.notify.alts`. Target exemptions use `mbans.bypass.ban`, `mbans.bypass.mute`, `mbans.bypass.warn`, and `mbans.bypass.kick`. Replacing an existing ban or mute requires `mbans.override`.

## PlaceholderAPI

When PlaceholderAPI is installed, mBans registers `%mbans_banned%`, `%mbans_muted%`, `%mbans_warnings%`, `%mbans_status%`, and `%mbans_version%`. Punishment state is loaded asynchronously and cached for five seconds. The first unresolved request returns an empty value instead of blocking the server thread.

## Migration

Always back up the source and destination before importing. Start with `--dry-run`:

```text
/mbans import vanilla . --dry-run
/mbans import litebans --dry-run
```

The vanilla profile reads `banned-players.json` and `banned-ips.json`. JDBC URLs and credentials are read from `imports.<profile>` so passwords do not enter command history. JDBC profiles inspect source table metadata and recognize common punishment field names. Review the counts before running the same command without `--dry-run`. Details and limitations are in [the migration guide](docs/MIGRATION.md).

## Artifacts

- `mBans-1.0.0.jar`: Paper, Purpur, and Folia backend
- `mBans-Velocity-1.0.0.jar`: Velocity login enforcement

## Telemetry and updates

mBans uses [bStats plugin ID 33351](https://bstats.org/plugin/bukkit/mBans/33351) for anonymous usage statistics. Disable collection with `metrics.enabled: false`. The update checker reads the public Modrinth project and can be disabled independently with `updates.enabled: false`.

## Build

```bash
./gradlew clean build :velocity:build
```

The project is licensed under the MIT License.
