package com.benchmark.rps.worker;

import com.benchmark.rps.config.AppProperties;
import com.benchmark.rps.model.CodeRecord;
import com.benchmark.rps.redis.RedisCodeStore;
import com.benchmark.rps.repository.CodeRepository;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Background worker mirroring database/sync.js: BRPOP codes:sync_queue → insert into Postgres.
 * Enable with app.sync-worker.enabled=true (or SYNC_WORKER_ENABLED=true).
 */
@Component
@Order(50)
public class RedisToPostgresSyncWorker implements ApplicationRunner {

  private final AppProperties props;
  private final RedisCodeStore redisCodeStore;
  private final ObjectProvider<CodeRepository> codeRepositoryProvider;

  public RedisToPostgresSyncWorker(
      AppProperties props,
      RedisCodeStore redisCodeStore,
      ObjectProvider<CodeRepository> codeRepositoryProvider) {
    this.props = props;
    this.redisCodeStore = redisCodeStore;
    this.codeRepositoryProvider = codeRepositoryProvider;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!props.getSyncWorker().isEnabled()) {
      return;
    }
    if (!"server".equalsIgnoreCase(props.getMode())) {
      return;
    }

    CodeRepository repo = codeRepositoryProvider.getIfAvailable();
    if (repo == null) {
      System.err.println("[sync] Postgres is required for the sync worker.");
      return;
    }

    Thread.ofVirtual()
        .name("redis-postgres-sync")
        .start(() -> loop(repo));
    System.out.println("[sync] Worker started...");
  }

  private void loop(CodeRepository repo) {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        Optional<String> idOpt = redisCodeStore.brpopSyncQueue(5);
        if (idOpt.isEmpty()) {
          continue;
        }
        long id = Long.parseLong(idOpt.get());
        Optional<CodeRecord> data = redisCodeStore.findById(id);
        if (data.isEmpty() || data.get().code() == null) {
          System.err.println("Missing data for ID " + id);
          continue;
        }
        CodeRecord record = data.get();
        repo.insertIgnoringConflict(record.id(), record.code(), record.createdAt());
      } catch (Exception e) {
        System.err.println("Sync failed: " + e.getMessage());
      }
    }
  }
}
