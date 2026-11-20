package in.hisaab.attachments;

import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.net.URI;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * The only class that talks to object storage. It wraps the AWS SDK's S3 client, which works
 * against R2, MinIO and AWS alike, so there is one code path in every environment. Callers pass
 * object keys; the bucket comes from configuration.
 */
@Component
public class ObjectStorage {

  private final S3Client s3;
  private final String bucket;

  public ObjectStorage(StorageProperties properties) {
    this.bucket = properties.bucket();
    this.s3 =
        S3Client.builder()
            .endpointOverride(URI.create(properties.endpoint()))
            .region(Region.of(properties.region()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
            // R2 and MinIO want http://host/bucket/key, not http://bucket.host/key.
            .forcePathStyle(true)
            // Newer SDKs add CRC checksum headers to every request; R2 rejects some of them.
            // "When required" turns that off unless the operation demands it.
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .httpClient(UrlConnectionHttpClient.create())
            .build();
  }

  /** Streams {@code length} bytes from {@code in} to the bucket without holding them in memory. */
  public void put(String key, String contentType, long length, InputStream in) {
    var request =
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(length)
            .build();
    s3.putObject(request, RequestBody.fromInputStream(in, length));
  }

  /** The caller must close the returned stream. */
  public ResponseInputStream<GetObjectResponse> get(String key) {
    return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
  }

  public void delete(String key) {
    s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
  }

  @PreDestroy
  void close() {
    s3.close();
  }
}
