# Migration guide

Back up both databases before importing. Imports do not delete or modify source data.

Before a live import, mBans writes an H2 archive or SQLite database copy under `plugins/mBans/backups`. For an external SQL destination it writes a marker reminding the operator that a server-side database backup is still required; create that backup with the database provider before continuing.

## Vanilla and Essentials

The `vanilla` and `essentials` profiles read `banned-players.json` and `banned-ips.json` from a directory:

```text
/mbans import vanilla . --dry-run
```

The report shows rows read, accepted, and skipped. Repeat without `--dry-run` after checking the result. UUID, reason, source, creation time, and expiry are preserved when present.

## JDBC sources

Profiles are available for `litebans`, `libertybans`, `advancedban`, and `banmanager`:

```text
imports:
  litebans:
    source: "jdbc:mysql://127.0.0.1/legacy"
    user: "legacy_user"
    password: "private-password"
```

Restart or reload the config, then preview the import:

```text
/mbans import litebans --dry-run
```

The importer reads database metadata, selects punishment tables associated with the profile, and maps common UUID, name, IP, reason, issuer, type, active, created, and expiry columns. This approach supports common schema revisions without writing to the source.

Legacy schemas vary. A dry run that reads zero rows or skips unexpected rows means the source schema was not recognized. Do not run the live import in that case. Keep the source backup and provide the table definitions so a precise mapping can be added.

## Duplicate protection

mBans checks punishment type, target, issue timestamp, and reason before inserting. Repeating the same import skips matching records. Imported changes are also written to the network journal.

## After import

1. Compare history for several known players.
2. Check one permanent and one temporary punishment.
3. Check an IP ban and its exception behavior.
4. Restart the backend and proxy.
5. Keep the backup until the new installation has been used successfully.
