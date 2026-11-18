package in.hisaab.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.Json;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc builds the OpenAPI document with its own Jackson mapper, so it ignores {@code
 * spring.jackson.property-naming-strategy}. Without this bean the document says {@code
 * periodStartDay} while the API really sends {@code period_start_day}, and the web app's generated
 * types would be wrong. Providing our own {@link ModelResolver} makes springdoc name properties the
 * same way the real JSON does.
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public ModelResolver modelResolver() {
    ObjectMapper mapper = Json.mapper().copy();
    mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    return new ModelResolver(mapper);
  }
}
