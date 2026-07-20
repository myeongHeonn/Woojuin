package com.ssafy.woojuin.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

    /**
     * DefaultCredentialsProvider는 자격 증명을 기동 시점이 아니라 실제 호출 시점에
     * 지연 해석한다 (env var → ~/.aws/credentials → EC2 인스턴스 role 순). 로컬에서
     * AWS 키가 없어도 앱 자체는 정상 기동하고, 실제 S3 업로드를 시도할 때만 실패한다.
     */
    @Bean
    public S3Client s3Client(@Value("${aws.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
