package in.hisaab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point. {@code @SpringBootApplication} turns on three things: auto-configuration (Spring
 * configures Web, JPA, Flyway... from the libraries on the classpath), component scanning (Spring
 * finds every class annotated {@code @RestController}, {@code @Service}, ... in this package and
 * below), and this class as a source of configuration.
 *
 * <p>{@code @ConfigurationPropertiesScan} finds typed settings classes such as {@code
 * AppProperties}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class HisaabApplication {

  public static void main(String[] args) {
    SpringApplication.run(HisaabApplication.class, args);
  }
}
