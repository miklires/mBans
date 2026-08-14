# Network setup

Use a shared SQL server for a multi-server installation. MySQL, MariaDB, and PostgreSQL are supported. H2 and SQLite are local storage formats and must not be shared between processes.

## Backend configuration

Install the backend JAR on every Paper, Purpur, or Folia server. Configure the same connection and a unique server name:

```yaml
storage:
  type: mysql
  host: 127.0.0.1
  port: 3306
  name: mbans
  user: mbans
  password: "use-a-private-password"

network:
  server-name: survival
  sync-enabled: true
  poll-interval-ticks: 40
```

MariaDB uses `type: mariadb` and PostgreSQL uses `type: postgresql`, normally with port `5432`. `storage.jdbc-url` overrides the generated URL when it is not empty.

Start one backend first. It creates and upgrades the schema. Confirm that the log contains `mBans 1.0.0 enabled` before starting the proxy module.

## Velocity configuration

Install `mBans-Velocity-1.0.0.jar`, start Velocity once, and edit `plugins/mbans-velocity/config.properties`:

```properties
jdbc-url=jdbc:mysql://127.0.0.1:3306/mbans?useSSL=false&serverTimezone=UTC
user=mbans
password=use-a-private-password
pool-size=3
bstats-id=0
```

The module rejects active UUID and IP bans during `LoginEvent`. UUID exceptions created with `/mbans allow` are honored. If the database cannot be queried, login is denied because accepting an unchecked player would bypass network bans.

Use a database account restricted to the mBans database. Keep the database port on a private network or behind a firewall. Do not put credentials in source control.

## Synchronization

Backends append changes to `mbans_punishment_log`. Each backend polls rows after its last seen ID and immediately removes matching online players for new network bans. Mute checks read the shared table when chat is sent, so they do not depend on an in-memory event arriving.

Choose a unique `network.server-name` for each backend. A shorter poll interval reacts faster but creates more queries. The default of 40 ticks is suitable for a small network.
