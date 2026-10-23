package com.benchmark.rps.cli;

import com.benchmark.rps.config.AppProperties;
import com.benchmark.rps.repository.CodeRepository;
import com.benchmark.rps.util.CodeGenerator;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Java equivalent of database/seed.js.
 *
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.mode=seed --app.seed.records=20000000"
 * </pre>
 */
@Component
@Order(100)
public class SeedCommand implements ApplicationRunner {

  private final AppProperties props;
  private final ObjectProvider<CodeRepository> codeRepositoryProvider;
  private final CodeGenerator codeGenerator;
  private final ConfigurableApplicationContext context;

  public SeedCommand(
      AppProperties props,
      ObjectProvider<CodeRepository> codeRepositoryProvider,
      CodeGenerator codeGenerator,
      ConfigurableApplicationContext context) {
    this.props = props;
    this.codeRepositoryProvider = codeRepositoryProvider;
    this.codeGenerator = codeGenerator;
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    if (!"seed".equalsIgnoreCase(props.getMode())) {
      return;
    }

    CodeRepository repo = codeRepositoryProvider.getIfAvailable();
    if (repo == null) {
      System.err.println("[seed] Postgres is required. Ensure PG_CONNECT is not false.");
      exit(1);
      return;
    }

    String schemaSql =
        StreamUtils.copyToString(
            new ClassPathResource("schema.sql").getInputStream(), StandardCharsets.UTF_8);

    System.out.println("\nDropping the tables...");
    repo.dropAndCreateTable(schemaSql);
    System.out.println("[postgres] codes table was created successfully.");

    int totalRecords = props.getSeed().getRecords();
    // Also accept -r like Node: -- -r 20000000 via app args
    if (args.containsOption("r")) {
      List<String> values = args.getOptionValues("r");
      if (values != null && !values.isEmpty()) {
        totalRecords = Integer.parseInt(values.getFirst());
      }
    }
    List<String> nonOption = args.getNonOptionArgs();
    for (int i = 0; i < nonOption.size(); i++) {
      if ("-r".equals(nonOption.get(i)) && i + 1 < nonOption.size()) {
        totalRecords = Integer.parseInt(nonOption.get(i + 1));
      }
    }

    if (totalRecords == 0) {
      System.out.println("No records to insert for seeding. Exiting.");
      exit(0);
      return;
    }

    int batchSize = props.getSeed().getBatchSize();
    System.out.println("\nSeeding the database with " + totalRecords + " records...");

    for (int offset = 0; offset < totalRecords; offset += batchSize) {
      int count = Math.min(batchSize, totalRecords - offset);
      List<String> codes = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        codes.add(codeGenerator.generateCode());
      }
      repo.batchInsertCodes(codes);
      System.out.println(
          "[postgres] inserted records: " + Math.min(offset + batchSize, totalRecords) + "/"
              + totalRecords);
    }

    System.out.println("\nDatabase seeding completed.");
    exit(0);
  }

  private void exit(int code) {
    System.exit(SpringApplication.exit(context, () -> code));
  }
}
