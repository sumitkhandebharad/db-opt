# Spring Boot database optimization 

A Java 21 / Spring Boot implementation of the database lookup benchmark, designed for side-by-side comparison with its Node.js counterpart. It exposes four PostgreSQL lookup strategies and a Redis-backed lookup, plus utilities to seed PostgreSQL and migrate data to Redis.

## Requirements

- Java 21
- PostgreSQL for seeding and the `code-v1` through `code-v4` endpoints
- Redis for `code-fast` and migration
- Maven Wrapper (`./mvnw`)

The HTTP server listens on port `3010` by default. Start PostgreSQL and Redis before running modes that use them.

## Run

Seed PostgreSQL with 10,000 generated records. **Seeding drops and recreates the `codes` table**, so it deletes existing data in that table:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.mode=seed --app.seed.records=10000"
```

Migrate all PostgreSQL records to Redis. **Migration flushes the selected Redis database** before copying data, and does not support Redis Cluster:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.mode=migrate"
```

Start the HTTP server:

```bash
./mvnw spring-boot:run
```

Seed and migrate modes exit after their operation; the default `server` mode starts the HTTP server.

## Configuration

Settings can be supplied as environment variables or Spring command-line properties.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_DATABASE` | `testdb` | PostgreSQL database name |
| `DB_USER` | `${USER}` or `postgres` | PostgreSQL username |
| `DB_PASSWORD` | `postgres` | PostgreSQL password |
| `HIKARI_MAX_POOL` | `50` | Maximum PostgreSQL connection pool size |
| `HIKARI_MIN_IDLE` | `10` | Minimum idle PostgreSQL connections |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_CLUSTER` | `false` | Enable Redis Cluster configuration |
| `REDIS_CLUSTER_PORT` | `7000` | Redis Cluster port |
| `APP_MODE` | `server` | Startup mode: `server`, `seed`, or `migrate` |
| `SEED_RECORDS` | `0` | Number of records to insert in seed mode |
| `SYNC_WORKER_ENABLED` | `false` | Enable the Redis-to-PostgreSQL sync worker |

`app.seed.batch-size` defaults to 10,000 and `app.code-v4-max-id` defaults to 700,000; both can be overridden with Spring properties. PostgreSQL can also be disabled with `--app.pg-connect=false`; PostgreSQL endpoints will then return `503 Service Unavailable`.

## HTTP endpoints

| Method | Path | Behavior |
| --- | --- | --- |
| `GET` | `/simple` | Returns `{ "message": "hi" }`. |
| `PATCH` | `/update-something/{id}/{name}` | Validates a numeric `id` and a `name` of at least three characters, then returns a simulated response with optional `value1`, `value2`, and `foo1`–`foo10` body fields. |
| `POST` | `/code` | Creates a generated code in PostgreSQL; returns `409` if the code already exists. |
| `GET` | [`/code-v1`](src/main/java/com/benchmark/rps/controller/CodeController.java#L99) | PostgreSQL lookup using `ORDER BY RANDOM()`. |
| `GET` | [`/code-v2`](src/main/java/com/benchmark/rps/controller/CodeController.java#L104) | PostgreSQL lookup using `COUNT(*)` followed by a random primary-key lookup. |
| `GET` | [`/code-v3`](src/main/java/com/benchmark/rps/controller/CodeController.java#L109) | PostgreSQL lookup using the maximum ID followed by a random primary-key lookup. |
| `GET` | [`/code-v4`](src/main/java/com/benchmark/rps/controller/CodeController.java#L114) | PostgreSQL lookup using a random ID between 1 and `app.code-v4-max-id` (700,000 by default). Assumes IDs are dense in this range. |
| `POST` | `/code-fast` | Creates a generated code in Redis. |
| `GET` | [`/code-fast`](src/main/java/com/benchmark/rps/controller/CodeController.java#L126) | Looks up a random ID in Redis. |
| `GET` | `/benchmark` | Runs one lookup implementation repeatedly and reports timing statistics. |

Successful code responses contain a `data` object with `id`, `code`, and `created_at`; create endpoints use `created_code`. Lookups return `404` when no record can be found. The Redis-backed create endpoint stores data in Redis and queues it for the optional sync worker.

### Sequential query benchmark

Run a lookup repeatedly, one request at a time, inside a single HTTP request:

```bash
curl 'http://localhost:3010/benchmark?variant=v1&requests=100'
```

`variant` accepts `v1`, `v2`, `v3`, `v4`, or `fast` (Redis); it defaults to `v1`. `requests` defaults to 100 and must be between 1 and 10,000. The JSON response includes the requested and completed query counts, total elapsed time, average/minimum/maximum lookup time, and queries per second. Each variant calls the same service implementation as its corresponding [`/code-v1`](src/main/java/com/benchmark/rps/controller/CodeController.java#L99), [`/code-v2`](src/main/java/com/benchmark/rps/controller/CodeController.java#L104), [`/code-v3`](src/main/java/com/benchmark/rps/controller/CodeController.java#L109), [`/code-v4`](src/main/java/com/benchmark/rps/controller/CodeController.java#L114), or [`/code-fast`](src/main/java/com/benchmark/rps/controller/CodeController.java#L126) lookup endpoint.

The benchmark executes lookups serially. It measures lookup time within the running application; it does not measure concurrent load or the additional HTTP overhead of making one request per lookup. Use the same request count when comparing variants. PostgreSQL lookups require a populated `codes` table, and `fast` requires Redis to contain records.
