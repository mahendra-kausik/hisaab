package in.hisaab.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@code @Configuration} marks a class that sets up Spring behaviour. CORS lets a browser on
 * another origin call the API; we allow only the web app's origin. (Day to day the browser talks to
 * Next.js, which proxies /api, so this is a second guard.)
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final AppProperties properties;

  // Constructor injection: Spring sees the single constructor and passes in the
  // AppProperties bean it built from application.yml.
  public WebConfig(AppProperties properties) {
    this.properties = properties;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOrigins(properties.webOrigin())
        .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
        .allowCredentials(true);
  }
}
