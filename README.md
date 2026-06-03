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
java -jar target/echo-mock-1.0.0.jar

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

Edit **`config/mocks.yml`** (the external, editable file). It is reloaded on
`POST /__admin/reload` — no restart. The bundled `src/main/resources/mocks.yml` is
only a fallback used when the external file is missing.

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

## Config file resolution

1. External file at `mock.config-path` (default `config/mocks.yml`) — edit this.
2. Classpath `mocks.yml` — bundled fallback.

Override the path: `--mock.config-path=/some/where/mocks.yml` or env `MOCK_CONFIG_PATH`.

---

## Project layout

```
echo-mock/
├── config/mocks.yml                      # editable mock definitions (reloadable)
├── pom.xml
├── src/main/java/com/example/echomock/
│   ├── EchoMockApplication.java
│   ├── config/MockConfigLoader.java      # loads + hot-reloads the YAML
│   ├── controller/MockController.java    # catch-all request matcher
│   ├── controller/AdminController.java   # /__admin endpoints
│   ├── engine/RequestContext.java        # extracts body/header/query/path values
│   ├── engine/TemplateResolver.java      # ${...} substitution
│   ├── engine/ConditionEvaluator.java    # `when` matching
│   ├── engine/PathMatcher.java           # {var} path matching
│   └── engine/ResponseRenderer.java      # builds the HTTP response
├── src/main/resources/mocks.yml          # classpath fallback config
└── src/test/java/.../MockControllerTest.java
```
