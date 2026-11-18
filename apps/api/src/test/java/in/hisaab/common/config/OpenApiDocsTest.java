package in.hisaab.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import in.hisaab.PostgresTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class OpenApiDocsTest extends PostgresTestBase {

  @Autowired private MockMvc mockMvc;

  @Test
  void openApiDocumentUsesTheSameSnakeCaseNamesAsTheRealJson() throws Exception {
    String body =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // The web app's generated types come from this document, so it must match the wire format.
    Map<String, Object> properties =
        JsonPath.read(body, "$.components.schemas.MeResponse.properties");
    assertThat(properties.keySet()).contains("period_start_day", "dues_digest_enabled");
    assertThat(properties.keySet()).doesNotContain("periodStartDay");

    List<String> required = JsonPath.read(body, "$.components.schemas.MeResponse.required");
    assertThat(required).contains("id", "email", "workspaces");
  }
}
