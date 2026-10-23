# Spring Boot (Java 21)

Side-by-side port of the Node.js 1M RPS benchmark API.

## Run

```bash
# Seed (creates DB/table; optional record count)
./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.mode=seed --app.seed.records=10000"

# Migrate Postgres → Redis
./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.mode=migrate"

# HTTP server on :3010
./mvnw spring-boot:run
```

## Sequential query benchmark

Run one of the existing lookup implementations repeatedly, one request at a time, in a single
benchmark call:

```bash
curl 'http://localhost:3010/benchmark?variant=v1&requests=100'
```

`variant` accepts `v1`, `v2`, `v3`, `v4`, or `fast` (the Redis lookup). `requests` defaults to 100
and is limited to 10,000 per call. The response includes completed query count, total elapsed time,
average/minimum/maximum time per lookup, and queries per second. Each lookup uses the same service
implementation as its corresponding `/code-v1`…`/code-v4` or `/code-fast` endpoint. Run the same
request count for each variant when comparing them. The benchmark executes lookups serially; it does
not measure concurrent load or include the additional HTTP request per lookup that separate curl
calls would incur.

See the root [README](../README.md) for env vars and endpoint behavior.
