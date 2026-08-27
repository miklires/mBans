# Changelog

All notable changes to mBans are documented in this file.

## 1.2.0 - 2026-08-27

### Added

- IP mutes with `/ipmute` and `/unipmute`
- permanent and temporary shadow mutes with permission-gated staff visibility
- multi-account cache invalidation and regression coverage for network punishment updates
- UUID-bound in-game appeals and an administrative review queue

### Fixed

- rollback now removes affected local mute-cache entries immediately
- edited reasons are reflected in active mute messages without reconnecting
- removing a higher-priority mute restores any remaining applicable mute
- shadow-mute broadcasts are visible only to authorized staff

## 1.1.0 - 2026-08-27

### Added

- optional default reasons for all punishment commands
- cached mute enforcement and configurable blocked commands
- `/mutelist`, `/warns`, `/punishment`, and the `/blame` alias
- ID-based revocation and punishment reason correction
- separate permissions for silent actions and shortening overrides

### Fixed

- removed synchronous database reads from every chat message
- prevented mute bypass through private-message commands
- escaped punishment data before MiniMessage parsing
- blocked inventory dragging in moderation GUIs
- required GET for REST history and made history lookups case-insensitive

## 1.0.0 - 2026-08-14

### Added

- Paper, Purpur, Folia, and Velocity punishment enforcement for Minecraft 26.2
- bans, IP bans, mutes, warnings, kicks, history, rollback, notes, alt detection, and moderation GUI
- H2, SQLite, MySQL, MariaDB, and PostgreSQL storage with schema migrations
- shared-database network synchronization and Velocity pre-login checks
- templates, warning escalation, immunity levels, exemptions, silent actions, evidence, and appeal IDs
- Discord webhooks, GeoLite2 country rules, REST API, bStats, and Modrinth update checks
- Paper Brigadier commands and optional asynchronous PlaceholderAPI status placeholders
- vanilla and adaptive JDBC imports with dry runs, plus JSON and CSV history exports
- English and Russian localization
- registered bStats metrics and Modrinth update discovery
