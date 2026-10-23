package com.benchmark.rps.util;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class CodeGenerator {

  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * Generates a 500-character Base64 code (same approach as Node utils.js: 375 random bytes →
   * Base64, truncated to 500).
   */
  public String generateCode() {
    byte[] bytes = new byte[375];
    secureRandom.nextBytes(bytes);
    String encoded = Base64.getEncoder().encodeToString(bytes);
    return encoded.substring(0, Math.min(500, encoded.length()));
  }
}
