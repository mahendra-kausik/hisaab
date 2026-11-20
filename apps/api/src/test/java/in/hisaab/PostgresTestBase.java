package in.hisaab;

import java.net.URI;
import java.nio.file.Path;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

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

  // Stands in for Cloudflare R2: both are S3-compatible, so the app's storage code is unchanged.
  // s3mock creates the bucket itself and does not check credentials.
  static final String BUCKET = "hisaab-test";
  private static final String S3_KEY = "test-key";
  private static final String S3_SECRET = "test-secret";
  private static final GenericContainer<?> S3 =
      new GenericContainer<>("adobe/s3mock:5.2.3")
          .withExposedPorts(9090)
          .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", BUCKET)
          .waitingFor(Wait.forListeningPort());

  // The tests' own view of the bucket, to check what the app really stored or removed.
  private static final S3Client TEST_S3;

  static {
    // Started once per JVM; Testcontainers' Ryuk helper removes them when the tests end.
    POSTGRES.start();
    S3.start();
    TEST_S3 =
        S3Client.builder()
            .endpointOverride(URI.create(s3Url()))
            .region(Region.US_EAST_1)
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(S3_KEY, S3_SECRET)))
            .forcePathStyle(true)
            .httpClient(UrlConnectionHttpClient.create())
            .build();
  }

  private static String s3Url() {
    return "http://%s:%d".formatted(S3.getHost(), S3.getMappedPort(9090));
  }

  /** How many objects in the test bucket have a key starting with {@code prefix}. */
  protected static int objectCount(String prefix) {
    return TEST_S3.listObjectsV2(b -> b.bucket(BUCKET).prefix(prefix)).keyCount();
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
    registry.add("STORAGE_ENDPOINT", PostgresTestBase::s3Url);
    registry.add("STORAGE_REGION", () -> "us-east-1");
    registry.add("STORAGE_BUCKET", () -> BUCKET);
    registry.add("STORAGE_ACCESS_KEY", () -> S3_KEY);
    registry.add("STORAGE_SECRET_KEY", () -> S3_SECRET);
  }
}
