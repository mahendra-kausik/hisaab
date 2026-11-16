package in.hisaab.health.dto;

import jakarta.validation.constraints.NotNull;

/** Response of GET /api/v1/health. */
// @NotNull also tells springdoc the fields are always present, so the generated
// TypeScript type has them as required (not optional).
public record HealthResponse(@NotNull String status, @NotNull String db) {

  public static HealthResponse ok() {
    return new HealthResponse("ok", "ok");
  }

  public static HealthResponse dbDown() {
    return new HealthResponse("error", "down");
  }

  // Not named isOk()/getOk(): Jackson would add it to the JSON as an extra "ok" field.
  public boolean healthy() {
    return "ok".equals(status);
  }
}
