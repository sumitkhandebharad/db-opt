package com.benchmark.rps.redis;

import com.benchmark.rps.model.CodeRecord;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis fast-path store mirroring Node redis keys: codes:unique, codes:seq, code:{id},
 * codes:sync_queue.
 */
@Component
public class RedisCodeStore {

  public static final String UNIQUE_SET = "codes:unique";
  public static final String SEQ_KEY = "codes:seq";
  public static final String SYNC_QUEUE = "codes:sync_queue";

  private static final long MAX_ID_CACHE_TTL_MS = 500L;

  private final StringRedisTemplate redis;

  private volatile long cachedMaxId = 0L;
  private volatile long lastCacheUpdate = 0L;

  public RedisCodeStore(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public static String codeKey(long id) {
    return "code:" + id;
  }

  /** SADD returns true if the code was new (added). */
  public boolean tryAddUnique(String code) {
    Long added = redis.opsForSet().add(UNIQUE_SET, code);
    return added != null && added > 0;
  }

  public long nextId() {
    Long id = redis.opsForValue().increment(SEQ_KEY);
    return id == null ? 0L : id;
  }

  public void storeAndEnqueue(long id, String code, Instant createdAt) {
    String createdAtIso = createdAt.toString();
    Map<String, String> hash = new HashMap<>();
    hash.put("id", String.valueOf(id));
    hash.put("code", code);
    hash.put("created_at", createdAtIso);

    redis.executePipelined(
        new SessionCallback<>() {
          @Override
          @SuppressWarnings("unchecked")
          public Object execute(RedisOperations operations) {
            operations.opsForHash().putAll(codeKey(id), hash);
            operations.opsForList().leftPush(SYNC_QUEUE, String.valueOf(id));
            return null;
          }
        });
  }

  public Optional<CodeRecord> findById(long id) {
    Map<Object, Object> raw = redis.opsForHash().entries(codeKey(id));
    if (raw == null || raw.isEmpty()) {
      return Optional.empty();
    }
    long recordId = Long.parseLong(String.valueOf(raw.get("id")));
    String code = String.valueOf(raw.get("code"));
    Instant createdAt = Instant.parse(String.valueOf(raw.get("created_at")));
    return Optional.of(new CodeRecord(recordId, code, createdAt));
  }

  /** Cached max id (codes:seq) refreshed at most every 500ms — same as Node getMaxId(). */
  public long getMaxIdCached() {
    long now = System.currentTimeMillis();
    if (now - lastCacheUpdate > MAX_ID_CACHE_TTL_MS) {
      String maxIdStr = redis.opsForValue().get(SEQ_KEY);
      if (maxIdStr != null && !maxIdStr.isBlank()) {
        cachedMaxId = Long.parseLong(maxIdStr);
        lastCacheUpdate = now;
      }
    }
    return cachedMaxId;
  }

  public void flushDb() {
    var connection = redis.getConnectionFactory().getConnection();
    try {
      connection.serverCommands().flushDb();
    } finally {
      connection.close();
    }
  }

  public void setSeq(long value) {
    redis.opsForValue().set(SEQ_KEY, String.valueOf(value));
  }

  public void storeBatch(List<CodeRecord> records) {
    redis.executePipelined(
        new SessionCallback<>() {
          @Override
          @SuppressWarnings("unchecked")
          public Object execute(RedisOperations operations) {
            for (CodeRecord row : records) {
              Map<String, String> hash = new HashMap<>();
              hash.put("id", String.valueOf(row.id()));
              hash.put("code", row.code());
              hash.put(
                  "created_at",
                  row.createdAt() != null ? row.createdAt().toString() : Instant.now().toString());
              operations.opsForHash().putAll(codeKey(row.id()), hash);
              operations.opsForSet().add(UNIQUE_SET, row.code());
            }
            return null;
          }
        });
  }

  /** Blocking pop from sync queue. Returns id string or empty if timed out. */
  public Optional<String> brpopSyncQueue(long timeoutSeconds) {
    String result = redis.opsForList().rightPop(SYNC_QUEUE, Duration.ofSeconds(timeoutSeconds));
    return Optional.ofNullable(result);
  }
}
