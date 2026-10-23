package com.benchmark.rps.cli;

import com.benchmark.rps.config.AppProperties;
import com.benchmark.rps.model.CodeRecord;
import com.benchmark.rps.redis.RedisCodeStore;
import com.benchmark.rps.repository.CodeRepository;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Java equivalent of database/migrate.js (Postgres → Redis). Does not target Redis Cluster.
 *
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.mode=migrate"
 * </pre>
 */
@Component
@Order(100)
public class MigrateCommand implements ApplicationRunner {

  private static final int BATCH_SIZE = 2000;

  private final AppProperties props;
  private final ObjectProvider<CodeRepository> codeRepositoryProvider;
  private final RedisCodeStore redisCodeStore;
  private final ConfigurableApplicationContext context;

  public MigrateCommand(
      AppProperties props,
      ObjectProvider<CodeRepository> codeRepositoryProvider,
      RedisCodeStore redisCodeStore,
      ConfigurableApplicationContext context) {
    this.props = props;
    this.codeRepositoryProvider = codeRepositoryProvider;
    this.redisCodeStore = redisCodeStore;
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!"migrate".equalsIgnoreCase(props.getMode())) {
      return;
    }

    if (props.isRedisCluster()) {
      System.err.println("[migrate] This does not work when Redis is clustered.");
      exit(1);
      return;
    }

    CodeRepository repo = codeRepositoryProvider.getIfAvailable();
    if (repo == null) {
      System.err.println("[migrate] Postgres is required.");
      exit(1);
      return;
    }

    System.out.println("Starting batch migration...");
    redisCodeStore.flushDb();
    System.out.println("Flushed existing Redis data.");

    long lastId = 0;
    long totalProcessed = 0;

    while (true) {
      List<CodeRecord> rows = repo.findAfterId(lastId, BATCH_SIZE);
      if (rows.isEmpty()) {
        break;
      }

      redisCodeStore.storeBatch(rows);
      lastId = rows.getLast().id();
      totalProcessed += rows.size();

      if (totalProcessed % 100_000 == 0) {
        System.out.println("Processed " + totalProcessed + " records...");
      }
    }

    redisCodeStore.setSeq(lastId);
    System.out.println("Migration Finished. Total records: " + totalProcessed);
    exit(0);
  }

  private void exit(int code) {
    System.exit(SpringApplication.exit(context, () -> code));
  }
}
