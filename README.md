# sentinel-webhook-receiver

Zero-dependency Java 21 webhook receiver. It verifies GitHub-style HMAC-SHA256 signatures and forwards verified events to [`sentinel-triage`](../sentinel-triage). Part of the **Sentinel** system:

```
GitHub webhook ──▶ sentinel-webhook-receiver (Java) ──▶ sentinel-triage (Python) ──▶ sentinel-dashboard (TypeScript)
```

## Security properties

- **Fails closed**: refuses to start without `WEBHOOK_SECRET`.
- **Constant-time** signature comparison (`MessageDigest.isEqual`); missing or malformed headers never verify.
- Signature is checked over the **raw bytes** before any parsing.
- 1 MiB body limit, `POST`-only, JSON-object check, and event type/delivery id are JSON-escaped before forwarding.
- Tested against the official test vector from GitHub's webhook docs.

## Run

```bash
./build.sh
export WEBHOOK_SECRET=change-me
export TRIAGE_URL=http://localhost:8000   # default
export PORT=8080                          # default
java -cp out dev.sentinel.receiver.Main
```

Point a GitHub repository webhook at `https://<your-host>/webhook` (content type `application/json`, same secret). For local development, use a tunnel such as `ngrok` or `cloudflared`.

Responses: `202` accepted, `401` bad signature, `400` not a JSON object, `413` too large, `502` triage unavailable.

## Test

```bash
./test.sh
```

No Maven or Gradle needed; the project builds with plain `javac`.

## Why webhooks plus a thin receiver

AI agents and automation increasingly react to events rather than prompts. Keeping the public-facing endpoint tiny, dependency-free, and strict means the part of the system exposed to the internet has very little attack surface, while the smarter (and more permissive) analysis runs behind it.

## Ideas to extend

- Replay protection using `X-GitHub-Delivery` plus a short-lived cache
- Retry queue for triage outages
- Support other providers (Stripe, Slack) with per-provider verifiers

## License

MIT
