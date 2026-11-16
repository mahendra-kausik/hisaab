package in.hisaab.health;

import in.hisaab.health.dto.HealthResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code @RestController} means: this class handles HTTP requests, and whatever its methods return
 * is written as JSON. {@code @RequestMapping} sets the URL prefix for the whole class.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

  private final HealthService service;

  public HealthController(HealthService service) {
    this.service = service;
  }

  // HTTP layer only: one service call, no logic here.
  @GetMapping
  public ResponseEntity<HealthResponse> health() {
    HealthResponse response = service.check();
    HttpStatus status = response.healthy() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
    return ResponseEntity.status(status).body(response);
  }
}
