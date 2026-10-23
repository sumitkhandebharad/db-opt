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

See the root [README](../README.md) for env vars and endpoint behavior.
