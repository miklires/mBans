# REST API

The read-only API is intended for a private panel or Discord bot. It binds to `127.0.0.1:8766` by default and requires a bearer token on every request, including health checks.

```yaml
rest-api:
  enabled: true
  bind: 127.0.0.1
  port: 8766
  token: "replace-with-a-long-random-secret"
  requests-per-minute: 120
```

Restart after changing this section.

## Requests

```bash
curl -H "Authorization: Bearer TOKEN" http://127.0.0.1:8766/v1/health
curl -H "Authorization: Bearer TOKEN" http://127.0.0.1:8766/v1/punishments/42
curl -H "Authorization: Bearer TOKEN" "http://127.0.0.1:8766/v1/history?player=Steve"
```

`GET /v1/punishments/{id}` returns one punishment. `GET /v1/history?player=<name>` returns up to 100 recent records. Responses are JSON and include reason, issuer, timestamps, active state, appeal ID, and evidence. IP addresses and database credentials are never returned.

The API has no write endpoints. Keep the token out of URLs and logs. For access outside the host, use an authenticated reverse proxy with TLS and firewall the direct port.

Requests are limited per remote IP in one-minute windows. A client over the configured limit receives HTTP `429` and a `Retry-After: 60` header.
