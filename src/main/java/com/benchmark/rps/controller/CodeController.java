package com.benchmark.rps.controller;

import com.benchmark.rps.model.CodeRecord;
import com.benchmark.rps.service.CodeService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CodeController {

  private final CodeService codeService;

  public CodeController(CodeService codeService) {
    this.codeService = codeService;
  }

  @GetMapping("/simple")
  public Map<String, String> simple() {
    return Map.of("message", "hi");
  }

  @PatchMapping("/update-something/{id}/{name}")
  public ResponseEntity<?> updateSomething(
      @PathVariable String id,
      @PathVariable String name,
      @RequestParam(required = false) String value1,
      @RequestParam(required = false) String value2,
      @RequestBody(required = false) Map<String, Object> body) {

    if (!isNumeric(id)) {
      return ResponseEntity.badRequest().body(Map.of("error", "id must be a number"));
    }

    if (name == null || name.length() < 3) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "name is required and must be at least 3 characters"));
    }

    Map<String, Object> payload = body == null ? Map.of() : body;
    List<Object> formattedFooValues = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      Object val = payload.get("foo" + i);
      Object formattedVal = val instanceof String s ? s + ". " : val;
      formattedFooValues.add(formattedVal);
    }

    StringBuilder totalFoo = new StringBuilder();
    for (Object v : formattedFooValues) {
      if (v != null) {
        totalFoo.append(v);
      }
    }

    long numericId = (long) Double.parseDouble(id);
    String meta =
        "This is a string intended to take up space to simulate a medium-sized production API response object."
            .repeat(2);

    List<Map<String, Object>> history = new ArrayList<>(100);
    for (int i = 0; i < 100; i++) {
      Map<String, Object> event = new HashMap<>();
      event.put("event_id", numericId + i);
      event.put("timestamp", Instant.now().toString());
      event.put("action", "Action performed by " + name);
      event.put("metadata", meta);
      event.put("status", i % 2 == 0 ? "success" : "pending");
      history.add(event);
    }

    Map<String, Object> response = new HashMap<>();
    response.put("id", id);
    response.put("name", name);
    response.put("value1", value1);
    response.put("value2", value2);
    response.put("total_foo", totalFoo.toString().toUpperCase());
    response.put("history", history);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/code")
  public ResponseEntity<?> createCode() {
    CodeRecord created = codeService.createInPostgres();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("created_code", toApiCode(created)));
  }

  @GetMapping("/code-v1")
  public Map<String, Object> codeV1() {
    return Map.of("data", toApiCode(codeService.getCodeV1()));
  }

  @GetMapping("/code-v2")
  public Map<String, Object> codeV2() {
    return Map.of("data", toApiCode(codeService.getCodeV2()));
  }

  @GetMapping("/code-v3")
  public Map<String, Object> codeV3() {
    return Map.of("data", toApiCode(codeService.getCodeV3()));
  }

  @GetMapping("/code-v4")
  public Map<String, Object> codeV4() {
    return Map.of("data", toApiCode(codeService.getCodeV4()));
  }

  @PostMapping("/code-fast")
  public ResponseEntity<?> createCodeFast() {
    CodeRecord created = codeService.createInRedis();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("created_code", toApiCode(created)));
  }

  @GetMapping("/code-fast")
  public Map<String, Object> getCodeFast() {
    return Map.of("data", toApiCode(codeService.getFromRedis()));
  }

  @GetMapping("/benchmark")
  public Map<String, Object> benchmarkSequentially(
      @RequestParam(defaultValue = "v1") String variant,
      @RequestParam(defaultValue = "100") int requests) {
    return codeService.benchmarkSequentially(variant, requests);
  }

  private static Map<String, Object> toApiCode(CodeRecord record) {
    Map<String, Object> map = new HashMap<>();
    map.put("id", record.id());
    map.put("code", record.code());
    map.put("created_at", record.createdAt() != null ? record.createdAt().toString() : null);
    return map;
  }

  private static boolean isNumeric(String id) {
    try {
      Double.parseDouble(id);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
