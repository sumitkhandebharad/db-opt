package com.benchmark.rps.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class RedisStartupLogger implements ApplicationRunner {

  private final StringRedisTemplate redisTemplate;
  private final AppProperties props;

  public RedisStartupLogger(StringRedisTemplate redisTemplate, AppProperties props) {
    this.redisTemplate = redisTemplate;
    this.props = props;
  }

  @Override
  public void run(ApplicationArguments args) {
    // Seed only needs Postgres
    if ("seed".equalsIgnoreCase(props.getMode())) {
      return;
    }

    try {
      var connection = redisTemplate.getConnectionFactory().getConnection();
      try {
        connection.ping();
      } finally {
        connection.close();
      }
      if (props.isRedisCluster()) {
        System.out.println("[redis] cluster ready.");
      } else {
        System.out.println("[redis] standalone ready.");
      }
    } catch (Exception e) {
      System.err.println("Redis Error: " + e.getMessage());
      if ("server".equalsIgnoreCase(props.getMode()) || "migrate".equalsIgnoreCase(props.getMode())) {
        throw e;
      }
    }
  }
}
