package com.benchmark.rps.service;

import com.benchmark.rps.model.CodeRecord;
import com.benchmark.rps.redis.RedisCodeStore;
import com.benchmark.rps.repository.CodeRepository;
import com.benchmark.rps.util.CodeGenerator;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CodeService {

  private static final int MAX_BENCHMARK_QUERIES = 10_000;

  private final CodeGenerator codeGenerator;
  private final ObjectProvider<CodeRepository> codeRepositoryProvider;
  private final RedisCodeStore redisCodeStore;
  private final SecureRandom random = new SecureRandom();

  public CodeService(
      CodeGenerator codeGenerator,
      ObjectProvider<CodeRepository> codeRepositoryProvider,
      RedisCodeStore redisCodeStore) {
    this.codeGenerator = codeGenerator;
    this.codeRepositoryProvider = codeRepositoryProvider;
    this.redisCodeStore = redisCodeStore;
  }

  public CodeRecord createInPostgres() {
    CodeRepository repo = requireRepo();
    String code = codeGenerator.generateCode();
    try {
      return repo.insert(code);
    } catch (DuplicateKeyException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Code already exists.");
    } catch (DataIntegrityViolationException e) {
      if (isUniqueViolation(e)) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Code already exists.");
      }
      throw e;
    }
  }

  /** v1 — ORDER BY RANDOM() (slow baseline). */
  public CodeRecord getCodeV1() {
    return requireRepo()
        .findRandomOrderByRandom()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No codes found."));
  }

  /** v2 — COUNT(*) then random PK lookup. */
  public CodeRecord getCodeV2() {
    CodeRepository repo = requireRepo();
    long count = repo.count();
    if (count == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No codes found.");
    }
    long randomId = randomLongInclusive(1, count);
    return repo.findById(randomId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "record not found"));
  }

  /** v3 — max id then random PK lookup. */
  public CodeRecord getCodeV3() {
    CodeRepository repo = requireRepo();
    long maxId =
        repo.findMaxId()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No codes found."));
    long randomId = randomLongInclusive(1, maxId);
    return repo.findById(randomId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "record not found"));
  }

  /** v4 — sample the primary-key range, then seek forward to an existing row. */
  public CodeRecord getCodeV4() {
    CodeRepository repo = requireRepo();
    long minId =
        repo.findMinId()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No codes found."));
    long maxId = repo.findMaxId().orElseThrow();
    long randomId = randomLongInclusive(minId, maxId);
    return repo
        .findByIdAtOrAbove(randomId)
        .or(() -> repo.findMinId().flatMap(repo::findById))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "record not found"));
  }

  public CodeRecord createInRedis() {
    String code = codeGenerator.generateCode();
    boolean isNew = redisCodeStore.tryAddUnique(code);
    if (!isNew) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Code already exists.");
    }
    long id = redisCodeStore.nextId();
    Instant createdAt = Instant.now();
    redisCodeStore.storeAndEnqueue(id, code, createdAt);
    return new CodeRecord(id, code, createdAt);
  }

  public CodeRecord getFromRedis() {
    long maxId = redisCodeStore.getMaxIdCached();
    if (maxId == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No codes found.");
    }
    long randomId = randomLongInclusive(1, maxId);
    return redisCodeStore
        .findById(randomId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "record not found"));
  }

  /** Runs the selected lookup repeatedly in sequence and reports its elapsed time. */
  public Map<String, Object> benchmarkSequentially(String variant, int requests) {
    String normalizedVariant = variant.toLowerCase(Locale.ROOT);
    if (!Map.of("v1", true, "v2", true, "v3", true, "v4", true, "fast", true)
        .containsKey(normalizedVariant)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "variant must be one of: v1, v2, v3, v4, fast");
    }
    if (requests < 1 || requests > MAX_BENCHMARK_QUERIES) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "requests must be between 1 and " + MAX_BENCHMARK_QUERIES);
    }

    long totalStart = System.nanoTime();
    long minQueryNanos = Long.MAX_VALUE;
    long maxQueryNanos = 0;
    long sumQueryNanos = 0;

    for (int i = 0; i < requests; i++) {
      long queryStart = System.nanoTime();
      getCodeForVariant(normalizedVariant);
      long queryNanos = System.nanoTime() - queryStart;
      sumQueryNanos += queryNanos;
      minQueryNanos = Math.min(minQueryNanos, queryNanos);
      maxQueryNanos = Math.max(maxQueryNanos, queryNanos);
    }

    long totalNanos = System.nanoTime() - totalStart;
    double totalMillis = totalNanos / 1_000_000.0;
    return Map.of(
        "variant", normalizedVariant,
        "requested_queries", requests,
        "completed_queries", requests,
        "total_time_ms", totalMillis,
        "average_query_time_ms", sumQueryNanos / (double) requests / 1_000_000.0,
        "min_query_time_ms", minQueryNanos / 1_000_000.0,
        "max_query_time_ms", maxQueryNanos / 1_000_000.0,
        "queries_per_second", requests / (totalNanos / 1_000_000_000.0));
  }

  private CodeRecord getCodeForVariant(String variant) {
    return switch (variant) {
      case "v1" -> getCodeV1();
      case "v2" -> getCodeV2();
      case "v3" -> getCodeV3();
      case "v4" -> getCodeV4();
      case "fast" -> getFromRedis();
      default -> throw new IllegalArgumentException("Unsupported benchmark variant: " + variant);
    };
  }

  private CodeRepository requireRepo() {
    CodeRepository repo = codeRepositoryProvider.getIfAvailable();
    if (repo == null) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Postgres connection is disabled.");
    }
    return repo;
  }

  private static boolean isUniqueViolation(DataIntegrityViolationException e) {
    Throwable cause = e;
    while (cause != null) {
      if (cause instanceof SQLException sqlEx && "23505".equals(sqlEx.getSQLState())) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  /** Inclusive random long in [min, max], matching Node crypto.randomInt(min, max+1). */
  private long randomLongInclusive(long min, long max) {
    if (max < min) {
      throw new IllegalArgumentException("max < min");
    }
    if (max == min) {
      return min;
    }
    long bound = max - min + 1;
    return min + (Math.abs(random.nextLong()) % bound);
  }
}
