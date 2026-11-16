package in.hisaab.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import in.hisaab.common.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security setup. Every request passes through the filter chain built here before it reaches
 * a controller. The chain reads the access token, checks its signature, and either attaches the
 * user to the request or answers 401.
 */
@Configuration
public class SecurityConfig {

  private final AppProperties properties;

  public SecurityConfig(AppProperties properties) {
    this.properties = properties;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    AuthenticationEntryPoint unauthorized =
        (request, response, ex) -> {
          response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
          SecurityErrors.write(response, 401, "unauthorized", "Authentication required");
        };

    http
        // CSRF protection is off. A CSRF attack makes a victim's browser send a forged request
        // with their cookies. Here that cannot work, for three reasons: (1) the auth cookies
        // are SameSite=Lax, so browsers do not send them on cross-site POST/PATCH/DELETE;
        // (2) every request with a body must be application/json, which a cross-site HTML
        // form cannot send and a cross-site script could only send after a CORS check;
        // (3) CORS allows only our own web origin.
        .csrf(csrf -> csrf.disable())
        // Uses the CorsConfigurationSource bean below.
        .cors(Customizer.withDefaults())
        // No server-side session: identity comes from the token on each request.
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(
                    "/api/v1/health",
                    "/api/v1/auth/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/error")
                .permitAll()
                .anyRequest()
                .authenticated())
        // Verifies the signed JWT; JwtDecoder (below) holds the key.
        .oauth2ResourceServer(oauth ->
            oauth
                .bearerTokenResolver(new CookieBearerTokenResolver())
                .jwt(Customizer.withDefaults())
                .authenticationEntryPoint(unauthorized))
        // No token at all (as opposed to a bad one) is handled here.
        .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized))
        // Throttle login/signup before any token checking or password hashing.
        .addFilterBefore(new AuthRateLimitFilter(), BearerTokenAuthenticationFilter.class);
    return http.build();
  }

  /** Strength 12 means 2^12 hashing rounds: slow enough to make password guessing expensive. */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  // The same secret signs tokens (encoder) and checks them (decoder). HS256 = HMAC-SHA256.
  @Bean
  public JwtEncoder jwtEncoder() {
    return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
  }

  @Bean
  public JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder.withSecretKey(secretKey()).macAlgorithm(MacAlgorithm.HS256).build();
  }

  private SecretKey secretKey() {
    return new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  /**
   * CORS: lets a browser on another origin call the API. We allow only the web app's origin. (Day
   * to day the browser talks to Next.js, which proxies /api, so this is a second guard.)
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(List.of(properties.webOrigin()));
    cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of("*"));
    cors.setAllowCredentials(true);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cors);
    return source;
  }
}
