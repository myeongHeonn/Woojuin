package com.ssafy.woojuin.global.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
public class S3Config {

    /**
     * endpoint가 비어 있으면 운영(AWS S3) — DefaultCredentialsProvider가 자격 증명을
     * 기동 시점이 아니라 실제 호출 시점에 지연 해석한다(env var → ~/.aws/credentials →
     * EC2 인스턴스 role 순). endpoint가 있으면 로컬(MinIO) — path-style이 필수이고
     * (virtual-hosted style 미지원), 고정 자격 증명을 쓴다.
     */
    @Bean
    public S3Client s3Client(
            @Value("${aws.region}") String region,
            @Value("${aws.s3.endpoint:}") String endpoint,
            @Value("${aws.s3.access-key:}") String accessKey,
            @Value("${aws.s3.secret-key:}") String secretKey) {
        S3ClientBuilder builder = S3Client.builder().region(Region.of(region));
        if (endpoint == null || endpoint.isBlank()) {
            return builder.credentialsProvider(DefaultCredentialsProvider.create()).build();
        }
        return builder
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build();
    }
}
