# Configuration guide

## Storage

`storage.type` accepts `h2`, `sqlite`, `mysql`, `mariadb`, and `postgresql`. H2 is the safe default for one server. The local file is stored under `plugins/mBans`. Network engines use `host`, `port`, `name`, `user`, and `password`, unless `jdbc-url` is set.

Changing storage settings requires a restart. mBans versions its schema in `mbans_schema_version` and applies missing migrations during startup. A failed connection disables mBans instead of running without enforcement.

## Templates and escalation

A reason template contains a punishment type, optional duration, and reason:

```yaml
templates:
  cheat:
    type: BAN
    duration: 30d
    reason: "Unfair advantage"
```

Use it with `/ban Steve cheat` or through `/muser Steve`.

Escalation rules count source punishments inside a moving window:

```yaml
escalation:
  enabled: true
  rules:
    - type: WARN
      count: 3
      window: 7d
      action: MUTE
      duration: 1h
      reason: "Automatic escalation: repeated warnings"
```

Rules are evaluated from the lowest count upward. An action is not duplicated while an active punishment of the same type exists. `warns.auto-ban-threshold` is the older single-step rule; keep it at `0` when the escalation list defines all automatic actions.

## Immunity and exemptions

`immunity.levels` maps permissions to numeric levels. A moderator must have a strictly higher level than the target. Console has the highest level. mBans records the player's last known level on join so the same hierarchy also applies to offline UUID records. Changes made while the player is offline take effect in mBans after the player's next join.

`exemptions.worlds` prevents punishment of an online player in named worlds. `exemptions.ip-ranges` accepts IPv4 and IPv6 CIDR ranges. IP ranges are also ignored by IP-ban enforcement.

## Discord

Set `discord.enabled: true` and either a default `webhook-url` or individual URLs under `discord.webhooks`. A blank per-type URL falls back to the default. `<appeal_id>` in `discord.appeal-url` is replaced before the link button is sent.

Webhook errors never disable punishment enforcement. Silent punishments skip Discord delivery.

## Chat evidence

When enabled, mBans keeps a per-player in-memory ring buffer. `/mute Steve spam -last 10` copies the latest ten lines into the punishment record. The buffer is lost on restart; stored evidence is not. This feature does not require mChat.

Chat evidence and full IP addresses are personal data. Define an appropriate retention and access policy for the server's jurisdiction.

## GeoIP

Download a current `GeoLite2-Country.mmdb` from MaxMind and place it in `plugins/mBans`. Then configure ISO 3166-1 alpha-2 country codes:

```yaml
geoip:
  enabled: true
  database: GeoLite2-Country.mmdb
  allowed-countries: [DE, NL, PL]
  blocked-countries: []
```

An allow list takes precedence by denying countries outside the list. Private and loopback addresses are not blocked. A missing or unreadable database logs a warning and leaves country filtering disabled; punishment checks continue.

For Velocity enforcement, copy the MMDB beside `config.properties`, enable `geoip-enabled`, and use comma-separated country codes.

## REST API

The API is off by default. It will not start with an empty token. Bind it to localhost and put a TLS reverse proxy in front of it if remote access is required. See [REST API](REST_API.md).

## Privacy and cleanup

`alts.ip-retention-days` clears old IP values while keeping player-name history. Set `0` to disable automatic IP removal. Expired punishments are marked inactive by the cleanup task, but every enforcement query also checks the expiry timestamp directly.
