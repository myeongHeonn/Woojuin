package com.ssafy.woojuin.domain.item.service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Component
public class S3Uploader {

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    private final S3Client s3Client;
    private final String bucket;

    public S3Uploader(S3Client s3Client, @Value("${aws.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    /**
     * MinIO는 AWS S3와 달리 버킷을 미리 만들어주지 않아 로컬에선 기동 시점에 직접
     * 보장해야 한다. 운영(AWS S3)에서도 그대로 동작한다 — 버킷이 이미 있으면
     * headBucket이 성공해 아무 일도 안 하고, 없으면 생성을 시도한다. 그 외 예외
     * (권한 부족 등)는 그대로 던져 잘못된 설정이 첫 업로드 실패 시점까지 숨겨지지
     * 않고 기동 시점에 바로 드러나게 한다.
     */
    @PostConstruct
    void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("S3 버킷 생성: {}", bucket);
        }
    }

    public String upload(MultipartFile file, Long workspaceId) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다: " + contentType);
        }

        String key = "items/%d/%s-%s".formatted(workspaceId, UUID.randomUUID(), file.getOriginalFilename());

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("이미지 업로드에 실패했습니다", e);
        }

        return key;
    }

    /** IMAGE 아이템 가공(OCR) 시 원본 바이트를 읽어온다. 실패는 호출부가 판단한다. */
    public byte[] download(String key) {
        try (var obj = s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            return obj.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("이미지 다운로드에 실패했습니다: key=" + key, e);
        }
    }

    /**
     * 영구 삭제 시 S3 원본을 지운다(ERD 설계 노트). 호출 시점엔 DB 삭제가 이미 커밋된
     * 뒤라 여기서 예외를 던져 봐야 되돌릴 것이 없고, 사용자에겐 삭제가 실패한 것처럼
     * 보이기만 한다. 그래서 실패해도 요청은 성공으로 두고 고아 객체를 로그로 남긴다.
     */
    public void deleteQuietly(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException e) {
            log.warn("S3 원본 삭제 실패 — 고아 객체로 남음: key={}", key, e);
        }
    }
}
