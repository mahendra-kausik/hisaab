package in.hisaab.attachments;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where attachment files are kept: any S3-compatible service. In production that is Cloudflare R2
 * ({@code https://<account>.r2.cloudflarestorage.com}, region {@code auto}); locally it is MinIO
 * from docker-compose. Bound from the {@code app.storage.*} keys, which read {@code STORAGE_*}
 * environment variables.
 */
@ConfigurationProperties(prefix = "app.storage")
@Validated
public record StorageProperties(
    @NotBlank String endpoint,
    @NotBlank String region,
    @NotBlank String bucket,
    @NotBlank String accessKey,
    @NotBlank String secretKey) {}
