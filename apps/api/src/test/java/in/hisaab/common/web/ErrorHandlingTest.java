package in.hisaab.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.hisaab.PostgresTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

class ErrorHandlingTest extends PostgresTestBase {

  @Autowired private MockMvc mockMvc;

  @Test
  void unknownRouteReturnsTheErrorShape() throws Exception {
    mockMvc
        .perform(get("/api/v1/does-not-exist"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("not_found"))
        .andExpect(jsonPath("$.error.message").value("Not Found"))
        .andExpect(jsonPath("$.error.details").isEmpty());
  }

  @Test
  void wrongMethodReturnsTheErrorShape() throws Exception {
    mockMvc
        .perform(post("/api/v1/health").contentType(MediaType.APPLICATION_JSON).content("{ nope"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.error.code").value("method_not_allowed"));
  }

  // Plain unit test, no Spring: the handler is just a class.
  @Test
  void domainExceptionMapsToItsStatusAndCode() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();

    ResponseEntity<ErrorResponse> notFound = handler.handleDomain(new NotFoundException("No such"));
    assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(notFound.getBody().error().code()).isEqualTo("not_found");

    ResponseEntity<ErrorResponse> conflict =
        handler.handleDomain(new ConflictException("overpayment", "Too much"));
    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(conflict.getBody().error().code()).isEqualTo("overpayment");

    ResponseEntity<ErrorResponse> invalid =
        handler.handleDomain(
            new ValidationException("bad_amount", "Bad", Map.of("field", "amount_paise")));
    assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(invalid.getBody().error().details()).containsEntry("field", "amount_paise");

    assertThat(handler.handleDomain(new ForbiddenException("No")).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void unexpectedExceptionReturnsGeneric500WithoutLeakingDetails() {
    ResponseEntity<ErrorResponse> response =
        new GlobalExceptionHandler().handleUnexpected(new IllegalStateException("secret detail"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().error().code()).isEqualTo("internal_error");
    assertThat(response.getBody().error().message()).doesNotContain("secret");
  }
}
