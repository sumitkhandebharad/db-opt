package com.benchmark.rps.service;

import com.benchmark.rps.config.AppProperties;
import com.benchmark.rps.model.CodeRecord;
import com.benchmark.rps.redis.RedisCodeStore;
import com.benchmark.rps.repository.CodeRepository;
import com.benchmark.rps.util.CodeGenerator;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CodeService {

  private final CodeGenerator codeGenerator;
  private final ObjectProvider<CodeRepository> codeRepositoryProvider;
  private final RedisCodeStore redisCodeStore;
  private final AppProperties props;
  private final SecureRandom random = new SecureRandom();

  public CodeService(
      CodeGenerator codeGenerator,
      ObjectProvider<CodeRepository> codeRepositoryProvider,
      RedisCodeStore redisCodeStore,
      AppProperties props) {
    this.codeGenerator = codeGenerator;
    this.codeRepositoryProvider = codeRepositoryProvider;
    this.redisCodeStore = redisCodeStore;
    this.props = props;
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

  /** v4 — fixed-range PK lookup (assumes dense ids in range). */
  public CodeRecord getCodeV4() {
    long max = props.getCodeV4MaxId();
    long randomId = randomLongInclusive(1, max);
    return requireRepo()
        .findById(randomId)
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
