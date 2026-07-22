package org.psint.beyosclothing.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * AWS S3 Configuration
 * Configures the S3 client used by the reseller image module.
 * <p>
 * Product images are stored on the local filesystem, so S3 credentials are
 * optional: when they are not configured the beans are still created with
 * anonymous credentials so the application can start. Any actual S3 call will
 * then fail until valid credentials are provided.
 */
@Configuration
@Slf4j
public class AwsS3Config {

    @Value("${app.aws.s3.region:us-east-1}")
    private String awsRegion;

    @Value("${app.aws.s3.access-key:}")
    private String accessKey;

    @Value("${app.aws.s3.secret-key:}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        log.info("Initializing AWS S3 Client with region: {}", awsRegion);

        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(resolveCredentialsProvider())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        log.info("Initializing AWS S3 Presigner with region: {}", awsRegion);

        return S3Presigner.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(resolveCredentialsProvider())
                .build();
    }

    /**
     * Build a credentials provider from the configured keys. When either key is
     * blank (e.g. local development using filesystem storage), fall back to
     * anonymous credentials so bean creation does not fail at startup.
     */
    private AwsCredentialsProvider resolveCredentialsProvider() {
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }

        log.warn("AWS S3 credentials are not configured; using anonymous credentials. " +
                "S3 operations will fail until valid credentials are provided.");
        return AnonymousCredentialsProvider.create();
    }
}
