# Changelog

All notable changes to mBans are documented in this file.

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
