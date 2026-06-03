# echo-mock

A small, **config-driven** Spring Boot mock REST server for end-to-end testing.
Point your application's downstream/base URL at this service, and it will return
responses you define in a YAML file — **echoing back the tracking / transaction id**
it receives in the request body or headers.

No code changes are needed to add new scenarios: edit `config/mocks.yml` and call
`POST /__admin/reload`.

---

## Quick start

```bash
# build + test
mvn clean verify

# run (defaults to port 8080)
mvn spring-boot:run
#   or
java -jar target/echo-mock-1.0.0.war   # executable WAR; also deployable to an external servlet container

# change the port
MOCK_PORT=9090 mvn spring-boot:run
```

Health check:

```bash
curl localhost:8080/__admin/health      # {"status":"UP","mocks":3}
```

### Point your API at it

Set your application's downstream base URL to `http://localhost:8080` (or whatever
host/port you run this on). Every request your API makes is matched against the
mock definitions and answered accordingly.

### Run locally with Podman

The WAR ships **without** `application.yml` or `mocks.yml` — both are supplied externally.
The image expects them mounted at `/etc/echo-mock` (the image presets
`SPRING_CONFIG_ADDITIONAL_LOCATION` and `MOCK_CONFIG_PATH` to point there). Runs the same
way the Kubernetes pod does: non-root, read-only root filesystem.

```bash
# build the image
podman build -t echo-mock:1.0.0 .

# run it (mounts ./config, which holds application.yml + mocks.yml)
podman run -d --name echo-mock \
  -p 8080:8080 --user 10001:10001 --read-only --tmpfs /tmp \
  -v "$PWD/config":/etc/echo-mock:ro,Z \
  echo-mock:1.0.0

curl localhost:8080/__admin/health        # {"status":"UP","mocks":3}
```

- The `:ro,Z` on the volume is for SELinux relabeling inside Podman's Linux VM (harmless elsewhere).
- **macOS first-time only:** Podman runs in a VM — if `podman info` fails, run `podman machine init --now` once.
- If you run without mounting config, the app **fails fast** with a clear error — config is intentionally not baked in.
- Edit `config/mocks.yml`, then `curl -X POST localhost:8080/__admin/reload` to apply changes (reliable here since it's a single container).

```bash
podman logs -f echo-mock      # follow logs
podman stop echo-mock         # stop
podman rm -f echo-mock        # remove
```

> Docker works with the identical commands (`docker` in place of `podman`); the `Z`
> volume flag is simply ignored by Docker.

---

## The core use case — echo an id back

Send a transaction id in the body and a tracking id in a header:

```bash
curl -X POST http://localhost:8080/api/transactions \
  -H "Content-Type: application/json" \
  -H "X-Tracking-Id: TRK-ABC-001" \
  -d '{"transactionId":"TXN-555","amount":250}'
```

Response — both ids reflected straight back:

```json
{
  "status": "SUCCESS",
  "message": "Transaction accepted",
  "transactionId": "TXN-555",
  "trackingId": "TRK-ABC-001",
  "processedAt": "2026-06-03T10:52:13.542Z",
  "reference": "e7ed9ce6-8543-4d6d-a56c-482e895a1d2d"
}
```

If the `X-Tracking-Id` header is absent it falls back to `trackingId` from the body —
that's the `${header.X-Tracking-Id:-${body.trackingId}}` template in the config.

---

## How to customize responses

Edit **`config/mocks.yml`** (the external file — it is **not** packaged in the WAR).
It is reloaded on `POST /__admin/reload` — no restart. If the file is missing at the
configured path, the app fails fast with a clear error.

A definition matches a request by **method + path**, then returns the **first**
response whose `when` conditions all match. A response with no `when` is the default.

```yaml
mocks:
  - name: create-transaction
    request:
      method: POST              # GET/POST/... or ANY
      path: /api/transactions   # supports {var} segments, e.g. /accounts/{id}
    responses:
      - status: "200"
        headers:
          X-Tracking-Id: "${header.X-Tracking-Id:-${body.trackingId}}"
        body: |
          {
            "status": "SUCCESS",
            "transactionId": "${body.transactionId}",
            "trackingId": "${header.X-Tracking-Id:-${body.trackingId}}",
            "reference": "${uuid}"
          }
```

### Template placeholders

Usable in `status`, `headers`, and `body`:

| Placeholder              | Resolves to                                              |
|--------------------------|----------------------------------------------------------|
| `${body.<field>}`        | Field from the JSON body. Nested: `${body.payer.id}`. Also raw JsonPath: `${body.$.items[0].id}` |
| `${header.<name>}`       | Request header (case-insensitive)                        |
| `${query.<name>}`        | Query-string parameter                                   |
| `${path.<var>}`          | Path variable from a `{var}` segment                     |
| `${method}`              | HTTP method                                              |
| `${uuid}`                | A generated UUID                                         |
| `${timestamp}` / `${now}`| Epoch millis / ISO-8601 instant                          |
| `${a:-b}`                | Value `a`, falling back to `b` if `a` is absent (nestable) |

### Conditional responses (`when`)

```yaml
responses:
  - when:
      body.amount: ">10000"        # numeric comparison
    status: "422"
    body: '{"status":"REJECTED","reason":"AMOUNT_EXCEEDS_LIMIT","transactionId":"${body.transactionId}"}'
  - when:
      body.currency: "!=SGD"
    status: "400"
    body: '{"status":"REJECTED","reason":"UNSUPPORTED_CURRENCY"}'
  - status: "201"                   # default / fallback
    body: '{"status":"SUCCESS","transactionId":"${body.transactionId}"}'
```

Supported `when` operators on any reference (`body.*`, `header.*`, `query.*`, `path.*`):

`exact` · `!=value` · `>n` · `<n` · `>=n` · `<=n` · `regex:pattern` · `exists` · `missing`

### Other knobs

- `delayMs: 1500` on a response simulates a slow downstream.
- `headers:` lets you set/override any response header (templated).
- Status can itself be templated, e.g. `status: "${query.forceStatus:-200}"`.

---

## Admin endpoints

| Method & path           | Purpose                                         |
|-------------------------|-------------------------------------------------|
| `GET  /__admin/health`  | Liveness + count of loaded mocks                |
| `GET  /__admin/mocks`   | List loaded definitions (name/method/path)      |
| `POST /__admin/reload`  | Reload `config/mocks.yml` without restarting    |

Unmatched requests return `404` with a JSON `NO_MOCK_MATCHED` body describing the
method and path, so missing mappings are obvious during testing.

---

## Config — externalized, not packaged

Neither `application.yml` nor `mocks.yml` is bundled in the WAR. Both are supplied at
runtime so each environment owns its own config:

- **`application.yml`** — Spring Boot loads it from a config location. Locally, `./config/`
  is searched automatically (`mvn spring-boot:run` and `java -jar` from the project root
  just work). In a container, the image sets `SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/etc/echo-mock/`.
- **`mocks.yml`** — read from `mock.config-path` (default `./config/mocks.yml`; the image
  sets `MOCK_CONFIG_PATH=/etc/echo-mock/mocks.yml`). Override with `--mock.config-path=...`
  or the `MOCK_CONFIG_PATH` env var. If absent, the app fails fast with a clear error.

`src/test/resources/application.yml` mirrors these settings for tests only (never packaged).

---

## Project layout

```
echo-mock/
├── config/                               # external runtime config (NOT in the WAR)
│   ├── application.yml                    #   Spring Boot settings
│   └── mocks.yml                          #   editable mock definitions (reloadable)
├── pom.xml
├── src/main/java/com/example/echomock/
│   ├── EchoMockApplication.java
│   ├── config/MockConfigLoader.java      # loads + hot-reloads mocks.yml
│   ├── controller/MockController.java    # catch-all request matcher
│   ├── controller/AdminController.java   # /__admin endpoints
│   ├── engine/RequestContext.java        # extracts body/header/query/path values
│   ├── engine/TemplateResolver.java      # ${...} substitution
│   ├── engine/ConditionEvaluator.java    # `when` matching
│   ├── engine/PathMatcher.java           # {var} path matching
│   └── engine/ResponseRenderer.java      # builds the HTTP response
├── src/test/resources/application.yml    # test-only config (never packaged)
└── src/test/java/.../                    # unit + MockMvc tests
```
