package com.benchmark.rps.config;

import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
public class RedisConfig {

  @Bean
  @Primary
  @ConditionalOnProperty(name = "app.redis-cluster", havingValue = "true")
  public RedisConnectionFactory redisClusterConnectionFactory(AppProperties props) {
    RedisClusterConfiguration clusterConfig =
        new RedisClusterConfiguration(List.of("localhost:" + props.getRedisClusterPort()));

    ClusterTopologyRefreshOptions topologyRefreshOptions =
        ClusterTopologyRefreshOptions.builder()
            .enablePeriodicRefresh(Duration.ofSeconds(30))
            .enableAllAdaptiveRefreshTriggers()
            .build();

    LettuceClientConfiguration clientConfig =
        LettuceClientConfiguration.builder()
            .clientOptions(
                ClusterClientOptions.builder()
                    .topologyRefreshOptions(topologyRefreshOptions)
                    .build())
            .build();

    LettuceConnectionFactory factory = new LettuceConnectionFactory(clusterConfig, clientConfig);
    factory.afterPropertiesSet();
    System.out.println(
        "[redis] cluster connection factory ready (seed port "
            + props.getRedisClusterPort()
            + ").");
    return factory;
  }
}
