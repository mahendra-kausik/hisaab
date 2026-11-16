package in.hisaab.health;

import in.hisaab.health.dto.HealthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code @Service} registers this class as a Spring bean that holds business logic. Here the
 * "logic" is a real round trip to Postgres, so a green health check means the database is reachable
 * with the runtime role.
 */
@Service
public class HealthService {

  private static final Logger log = LoggerFactory.getLogger(HealthService.class);

  private final JdbcTemplate jdbc;

  // Constructor injection: Spring supplies the JdbcTemplate it auto-configured
  // from the datasource settings in application.yml.
  public HealthService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // readOnly = true: tells Spring/Postgres this method only reads.
  @Transactional(readOnly = true)
  public HealthResponse check() {
    try {
      jdbc.queryForObject("SELECT 1", Integer.class);
      return HealthResponse.ok();
    } catch (DataAccessException e) {
      log.error("Health check database query failed", e);
      return HealthResponse.dbDown();
    }
  }
}
