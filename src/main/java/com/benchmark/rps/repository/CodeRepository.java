package com.benchmark.rps.repository;

import com.benchmark.rps.model.CodeRecord;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Raw SQL access for the codes table. Each lookup variant is intentional for query-optimization
 * comparison (mirrors Node /code-v1 … /code-v4).
 */
@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class CodeRepository {

  private static final RowMapper<CodeRecord> CODE_MAPPER =
      (rs, rowNum) ->
          new CodeRecord(
              rs.getLong("id"),
              rs.getString("code") != null ? rs.getString("code").trim() : null,
              toInstant(rs.getTimestamp("created_at")));

  private final JdbcTemplate jdbcTemplate;

  public CodeRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public CodeRecord insert(String code) {
    try {
      return jdbcTemplate.queryForObject(
          """
          INSERT INTO codes (code)
          VALUES (?)
          RETURNING id, code, created_at
          """,
          CODE_MAPPER,
          code);
    } catch (DuplicateKeyException e) {
      throw e;
    }
  }

  public void insertIgnoringConflict(long id, String code, Instant createdAt) {
    jdbcTemplate.update(
        """
        INSERT INTO codes (id, code, created_at) VALUES (?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        id,
        code,
        Timestamp.from(createdAt));
  }

  /** v1 — baseline slow path: full random sort. */
  public Optional<CodeRecord> findRandomOrderByRandom() {
    List<CodeRecord> rows =
        jdbcTemplate.query(
            """
            SELECT id, code, created_at
            FROM codes
            ORDER BY RANDOM()
            LIMIT 1
            """,
            CODE_MAPPER);
    return rows.stream().findFirst();
  }

  /** Used by v2: COUNT(*) then PK lookup. Expensive on large tables. */
  public long count() {
    Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM codes", Long.class);
    return count == null ? 0L : count;
  }

  /** Used by v3: max id via index-friendly DESC LIMIT 1. */
  public Optional<Long> findMaxId() {
    List<Long> rows =
        jdbcTemplate.query(
            "SELECT id FROM codes ORDER BY id DESC LIMIT 1", (rs, rowNum) -> rs.getLong("id"));
    return rows.stream().findFirst();
  }

  /** v2/v3/v4 — primary-key index lookup. */
  public Optional<CodeRecord> findById(long id) {
    List<CodeRecord> rows =
        jdbcTemplate.query(
            """
            SELECT id, code, created_at
            FROM codes
            WHERE id = ?
            """,
            CODE_MAPPER,
            id);
    return rows.stream().findFirst();
  }

  /** Keyset pagination for PG → Redis migrate. */
  public List<CodeRecord> findAfterId(long lastId, int limit) {
    return jdbcTemplate.query(
        """
        SELECT id, code, created_at FROM codes
        WHERE id > ?
        ORDER BY id ASC
        LIMIT ?
        """,
        CODE_MAPPER,
        lastId,
        limit);
  }

  public void dropAndCreateTable(String schemaSql) {
    jdbcTemplate.execute("DROP TABLE IF EXISTS codes");
    jdbcTemplate.execute(schemaSql);
  }

  public void batchInsertCodes(List<String> codes) {
    jdbcTemplate.batchUpdate(
        "INSERT INTO codes (code) VALUES (?) ON CONFLICT (code) DO NOTHING",
        codes,
        codes.size(),
        (ps, code) -> ps.setString(1, code));
  }

  private static Instant toInstant(Timestamp ts) {
    return ts == null ? null : ts.toInstant();
  }
}
