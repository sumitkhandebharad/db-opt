package com.benchmark.rps.config;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
@ConditionalOnBean(DataSource.class)
public class PostgresStartupLogger implements ApplicationRunner {

  private final JdbcTemplate jdbcTemplate;

  public PostgresStartupLogger(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      jdbcTemplate.queryForObject("SELECT 1", Integer.class);
      System.out.println("[postgres] connected successfully.");
    } catch (Exception e) {
      System.err.println("[postgres] connection failed.");
      System.err.println("Error details:\n" + e.getMessage());
      throw e;
    }
  }
}
