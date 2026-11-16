package in.hisaab;

import java.nio.file.Path;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base class for tests that need the whole app and a real database. It starts one Postgres 16
 * container (shared by every test class in the run) using the same init-roles.sql as local Docker,
 * so the two roles and RLS behave exactly as in development.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestFixtures.class)
public abstract class PostgresTestBase {

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16-alpine")
          // Superuser account used only by the container's init script.
          .withUsername("postgres")
          .withPassword("postgres")
          .withDatabaseName("postgres")
          .withCopyFileToContainer(
              MountableFile.forHostPath(Path.of("../../docker/postgres/init-roles.sql")),
              "/docker-entrypoint-initdb.d/init-roles.sql");

  static {
    // Started once per JVM; Testcontainers' Ryuk helper removes it when the tests end.
    POSTGRES.start();
  }

  // Runs before Spring starts, so the app is pointed at the container. The
  // datasource uses hisaab_app and Flyway uses hisaab_owner, as in production.
  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    String url =
        "jdbc:postgresql://%s:%d/hisaab"
            .formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432));
    registry.add("DB_URL", () -> url);
    registry.add("DB_APP_USER", () -> "hisaab_app");
    registry.add("DB_APP_PASSWORD", () -> "hisaab_dev_app");
    registry.add("DB_OWNER_USER", () -> "hisaab_owner");
    registry.add("DB_OWNER_PASSWORD", () -> "hisaab_dev_owner");
  }
}
