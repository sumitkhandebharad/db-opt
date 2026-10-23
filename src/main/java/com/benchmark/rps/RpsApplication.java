package com.benchmark.rps;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RpsApplication {

  private static final String DEFAULT_MODE = "server";

  public static void main(String[] args) {
    String mode = resolveMode(args);

    if ("seed".equalsIgnoreCase(mode)) {
      ensureDatabaseExists();
    }

    SpringApplication application = new SpringApplication(RpsApplication.class);

    if ("seed".equalsIgnoreCase(mode) || "migrate".equalsIgnoreCase(mode)) {
      application.setWebApplicationType(WebApplicationType.NONE);
    }

    application.run(args);
  }

  private static String resolveMode(String[] args) {
    // Match Spring Boot property precedence: explicit command-line arguments override env vars.
    for (String arg : args) {
      if (arg.startsWith("--app.mode=")) {
        return arg.substring("--app.mode=".length());
      }

      if (arg.startsWith("app.mode=")) {
        return arg.substring("app.mode=".length());
      }
    }

    String envMode = System.getenv("APP_MODE");
    if (envMode != null && !envMode.isBlank()) {
      return envMode.trim();
    }

    return DEFAULT_MODE;
  }

  /** Creates the benchmark database before the datasource connects. */
  private static void ensureDatabaseExists() {
    String host = firstNonBlank(System.getenv("DB_HOST"), "localhost");
    String port = firstNonBlank(System.getenv("DB_PORT"), "5432");
    String database = firstNonBlank(System.getenv("DB_DATABASE"), "testdb");
    String user =
        firstNonBlank(System.getenv("DB_USER"), System.getenv("USER"), "postgres");
    String password = firstNonBlank(System.getenv("DB_PASSWORD"), "postgres");

    String adminUrl = "jdbc:postgresql://" + host + ":" + port + "/postgres";
    String safeDatabase = database.replace("\"", "\"\"");

    try (Connection connection = DriverManager.getConnection(adminUrl, user, password);
        Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT 1 FROM pg_database WHERE datname = '"
                    + database.replace("'", "''")
                    + "'")) {

      if (!resultSet.next()) {
        statement.executeUpdate("CREATE DATABASE \"" + safeDatabase + "\"");
        System.out.println("[postgres] created database: " + database);
      }

    } catch (Exception e) {
      System.err.println(
          "[postgres] could not ensure database exists: " + e.getMessage());
    }
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }

    return null;
  }
}
