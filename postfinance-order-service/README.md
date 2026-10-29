# Order Service — Event-driven Order Processing

A small Spring Boot service that accepts orders over REST, persists them, emits a business
event over JMS, and processes them asynchronously — exactly the flow described in the task:

```
POST /orders ──▶ persist (CREATED) ──▶ [AFTER COMMIT] publish order.created ──▶ JMS queue
                                                                                   │
                                                              consumer ◀───────────┘
                                                              start (PROCESSING)
                                                              simulate work
                                                              complete (PROCESSED)
```

---

## 1. How to run

### Option A — zero setup (H2 + embedded Artemis)
Everything runs in-process. No database or broker to install.

```bash
mvn spring-boot:run
```

- API: `http://localhost:8080`
- H2 console: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:orders`, user `sa`)
- Health/metrics: `http://localhost:8080/actuator/health`, `/actuator/prometheus`

### Option B — Postgres + Artemis via Docker Compose

```bash
docker compose up --build
```

This starts Postgres, Artemis and the app (with `SPRING_PROFILES_ACTIVE=postgres`).

### Try it

```bash
# Create an order
curl -i -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"12345","amount":99.99}'

# -> 201 Created, body contains "status":"CREATED" and an id

# Fetch it again a moment later — the consumer will have moved it to PROCESSED
curl http://localhost:8080/orders/<id>
```

### Run the tests

```bash
mvn test
```

The integration test (`OrderFlowIntegrationTest`) exercises the **entire** flow end to end:
create via REST, then wait until the order reaches `PROCESSED` through the real JMS path.

---

## 2. Architecture & package layout

```
api/            REST layer (controller, DTOs, error handling)  — talks HTTP, never JPA entities
service/        Application services + transaction boundaries
domain/         Order aggregate + OrderStatus state machine
repository/     Spring Data JPA repository
messaging/      JMS config, event, publisher, after-commit relay, consumer
```

Clear separation of concerns: the controller validates and delegates; the service owns the
transaction; the domain owns the state rules; messaging is isolated behind its own package.

---

## 3. Key technical decisions

| Decision | Why |
|---|---|
| **Order status as an explicit state machine** (`OrderStatus.canTransitionTo`) | The task stresses *modelling* the status. Illegal transitions (e.g. `PROCESSED → CREATED`) are rejected in the domain, keeping state changes auditable. |
| **Publish the JMS event only `AFTER_COMMIT`** (`OrderEventRelay` + `@TransactionalEventListener`) | DB write and broker send are two resources → classic dual-write risk. Publishing after the commit guarantees we never emit an event for an order that rolled back. |
| **Separate short transactions in the consumer** | The simulated work (delay) runs *outside* any DB transaction, so we never hold a row lock while "processing". |
| **Idempotent consumer** | On redelivery, an order already `PROCESSED` is skipped. JMS gives at-least-once delivery, so consumers must be idempotent. |
| **Transacted JMS session + DLQ** | On failure the message is rolled back and redelivered; after max attempts it goes to the Dead-Letter-Queue instead of looping forever. |
| **DTOs separate from entities** | API contract can evolve independently from the persistence model. |
| **RFC-7807 ProblemDetail errors** | Consistent, machine-readable error responses (400 on validation, 404 on missing order). |
| **UUID primary keys** | Safe to expose, no sequence coordination across instances. |
| **Optimistic locking (`@Version`)** | Protects against lost updates if an API read and the consumer write race. |

---

## 4. Assumptions & pragmatic simplifications

- **H2 + embedded Artemis by default** so a reviewer can run it with a single command.
  Switching to Postgres is just `--spring.profiles.active=postgres` (see
  `application-postgres.yml`) — the only change is datasource/broker coordinates, no code.
- **`ddl-auto: update`** for the demo. In production the schema would be owned by
  **Flyway/Liquibase** and `ddl-auto` set to `validate`.
- **Processing is simulated** with logging + a short random delay, as requested.
- **Security is out of scope** for this task and intentionally omitted.
- **The event payload** carries the order id (mandatory) plus a little context and an
  `eventId`/`occurredAt` to support idempotency and tracing.

---

## 5. Error handling

- **Validation** (`@Valid`) → `400` with field-level messages.
- **Unknown order** → `404` ProblemDetail.
- **Transient processing error** → listener throws → JMS rolls back → redelivery → DLQ after N attempts.
- **Business/permanent failure** → order is moved to an explicit `FAILED` state (visible via the API) instead of being redelivered forever.
- **Commit OK but broker send fails** (the one remaining dual-write gap) → logged loudly; the
  order stays `CREATED` and would be picked up by reconciliation. The clean fix is the outbox
  pattern (below).

---

## 6. Switching H2 → Postgres

1. Start a Postgres (or use `docker compose up postgres`).
2. Run with the `postgres` profile:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=postgres
   ```
   or set `SPRING_PROFILES_ACTIVE=postgres`.
3. Provide `POSTGRES_URL/USER/PASSWORD` (defaults point to `localhost:5432/orders`).

No code changes — only configuration.

---

## 7. Next steps towards production

- **Transactional Outbox + relay/CDC** (e.g. Debezium) to make "persist + publish" atomic and
  eliminate the last dual-write gap.
- **Schema migrations** with Flyway; `ddl-auto: validate`.
- **DLQ monitoring & alerting**; a re-drive/replay tool for operations.
- **Idempotency store** keyed on `eventId` to harden against duplicate processing.
- **Observability**: tracing (OpenTelemetry) with the `orderId` as correlation id; dashboards
  on the Prometheus metrics already exposed; structured JSON logging.
- **Resilience**: tune redelivery back-off; circuit breakers on downstream calls.
- **Security**: authn/authz on the REST endpoint (OAuth2/JWT), broker credentials via secrets.
- **API**: pagination/list endpoints, OpenAPI spec, versioning.
- **Testing**: Testcontainers for Postgres & Artemis in CI; contract tests for the event schema.

---

## 8. Observability endpoints

- `GET /actuator/health` — liveness/readiness
- `GET /actuator/metrics` — Micrometer metrics
- `GET /actuator/prometheus` — Prometheus scrape endpoint

A Kubernetes deployment sketch is in `k8s/deployment.yaml` (probes, Prometheus scrape
annotations, secrets, resource limits).

