package com.benchmark.rps.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

  private boolean pgConnect = true;
  private boolean redisCluster = false;
  private int redisClusterPort = 7000;
  private String mode = "server";
  private final Seed seed = new Seed();
  private final SyncWorker syncWorker = new SyncWorker();
  private int codeV4MaxId = 700_000;

  public boolean isPgConnect() {
    return pgConnect;
  }

  public void setPgConnect(boolean pgConnect) {
    this.pgConnect = pgConnect;
  }

  public boolean isRedisCluster() {
    return redisCluster;
  }

  public void setRedisCluster(boolean redisCluster) {
    this.redisCluster = redisCluster;
  }

  public int getRedisClusterPort() {
    return redisClusterPort;
  }

  public void setRedisClusterPort(int redisClusterPort) {
    this.redisClusterPort = redisClusterPort;
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public Seed getSeed() {
    return seed;
  }

  public SyncWorker getSyncWorker() {
    return syncWorker;
  }

  public int getCodeV4MaxId() {
    return codeV4MaxId;
  }

  public void setCodeV4MaxId(int codeV4MaxId) {
    this.codeV4MaxId = codeV4MaxId;
  }

  public static class Seed {
    private int records = 0;
    private int batchSize = 10_000;

    public int getRecords() {
      return records;
    }

    public void setRecords(int records) {
      this.records = records;
    }

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(int batchSize) {
      this.batchSize = batchSize;
    }
  }

  public static class SyncWorker {
    private boolean enabled = false;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }
}
