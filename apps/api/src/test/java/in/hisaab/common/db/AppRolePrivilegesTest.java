package in.hisaab.common.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.hisaab.PostgresTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Proves invariant 3: the runtime role never owns tables and never bypasses RLS. */
class AppRolePrivilegesTest extends PostgresTestBase {

  // Spring's JdbcTemplate uses the app datasource, i.e. the hisaab_app role.
  @Autowired private JdbcTemplate jdbc;

  @Test
  void appRunsAsTheRestrictedRole() {
    assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("hisaab_app");
  }

  @Test
  void appRoleCannotCreateATable() {
    assertThatThrownBy(() -> jdbc.execute("CREATE TABLE should_not_exist (id int)"))
        // Spring wraps Postgres' error, so look at the original cause.
        .rootCause()
        .hasMessageContaining("permission denied");
  }

  @Test
  void appRoleIsNotSuperuserAndCannotBypassRls() {
    Map<String, Object> role =
        jdbc.queryForMap(
            "SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user");
    assertThat(role).containsEntry("rolsuper", false).containsEntry("rolbypassrls", false);
  }

  @Test
  void baselineMigrationRanAsTheOwnerRole() {
    String owner =
        jdbc.queryForObject(
            "SELECT tableowner FROM pg_tables WHERE tablename = 'flyway_schema_history'",
            String.class);
    assertThat(owner).isEqualTo("hisaab_owner");
  }
}
