package in.hisaab.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed app settings, bound from the {@code app.*} keys in application.yml (which read environment
 * variables). {@code @Validated} makes the app fail at startup if a value is missing, instead of
 * failing later at runtime.
 *
 * @param webOrigin the only origin CORS allows, e.g. {@code http://localhost:3000}
 */
@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(@NotBlank String webOrigin) {}
