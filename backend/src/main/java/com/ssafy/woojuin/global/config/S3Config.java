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
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

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

    /**
     * IMAGE 아이템 원본을 브라우저가 직접 읽도록 presigned GET URL을 만든다. S3Client와
     * 같은 자격 증명/엔드포인트 분기를 따르되, presigner는 forcePathStyle 대신
     * S3Configuration(pathStyleAccessEnabled)로 path-style을 켠다 — MinIO는 path-style만
     * 지원하므로 이 설정이 빠지면 서명 URL 호스트가 virtual-hosted style로 나와 깨진다.
     *
     * <p>서명 주소는 {@code public-endpoint}(있으면)를 쓴다 — presigned URL 은 **브라우저**가
     * 여는 것이라 브라우저가 해석할 수 있는 호스트여야 한다. S3Client 의 endpoint(컨테이너
     * 배포에서 {@code minio:9000})로 서명하면 내부 호스트명이 URL 에 박혀
     * ERR_NAME_NOT_RESOLVED 가 난다(dev 실측). 파일 I/O 는 계속 내부 endpoint 로 —
     * 그쪽이 nginx 를 안 거쳐 빠르다. 서명은 순수 로컬 연산이라 어느 주소든 네트워크
     * 호출이 없고, MinIO 는 요청이 실제 도착할 때 Host 헤더로 서명을 검증한다
     * (그래서 nginx 가 Host 를 보존해야 한다 — 인프라 쪽 전제).
     */
    @Bean
    public S3Presigner s3Presigner(
            @Value("${aws.region}") String region,
            @Value("${aws.s3.endpoint:}") String endpoint,
            @Value("${aws.s3.public-endpoint:}") String publicEndpoint,
            @Value("${aws.s3.access-key:}") String accessKey,
            @Value("${aws.s3.secret-key:}") String secretKey) {
        // 비어 있으면(로컬) endpoint 폴백 — localhost 는 브라우저도 닿는다.
        String signEndpoint = (publicEndpoint == null || publicEndpoint.isBlank())
                ? endpoint : publicEndpoint;
        S3Presigner.Builder builder = S3Presigner.builder().region(Region.of(region));
        if (signEndpoint == null || signEndpoint.isBlank()) {
            return builder.credentialsProvider(DefaultCredentialsProvider.create()).build();
        }
        return builder
                .endpointOverride(URI.create(signEndpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
