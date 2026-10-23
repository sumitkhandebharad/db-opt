package com.benchmark.rps.model;

import java.time.Instant;

public record CodeRecord(long id, String code, Instant createdAt) {}
