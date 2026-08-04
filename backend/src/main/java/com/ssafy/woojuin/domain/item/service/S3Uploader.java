package com.ssafy.woojuin.domain.item.service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Slf4j
@Component
public class S3Uploader {

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    /** presigned GET URL 유효시간. 캐시(TTL 절반)에서 꺼낸 URL도 최소 절반은 유효하도록 여유를 둔다. */
    private static final Duration PRESIGN_TTL = Duration.ofMinutes(60);

    /**
     * presigned URL 캐시 유효시간 — 반드시 PRESIGN_TTL보다 짧아야 한다. 캐시 만료 직전에
     * 꺼낸 URL도 (PRESIGN_TTL - PRESIGN_CACHE_TTL)만큼은 유효하다는 게 이 관계로 보장된다.
     */
    private static final Duration PRESIGN_CACHE_TTL = Duration.ofMinutes(30);

    static final String PRESIGN_CACHE_PREFIX = "presigned-url:";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StringRedisTemplate redisTemplate;
    private final String bucket;
    private final boolean localMode;

    public S3Uploader(S3Client s3Client, S3Presigner s3Presigner, StringRedisTemplate redisTemplate,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.endpoint:}") String endpoint) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.redisTemplate = redisTemplate;
        this.bucket = bucket;
        this.localMode = endpoint != null && !endpoint.isBlank();
    }

    /**
     * 로컬(MinIO)은 버킷을 미리 만들어주지 않아 기동 시점에 직접 보장해야 한다.
     * 운영(AWS S3)에서는 버킷이 없다고 자동으로 만들면 안 된다 — AWS_S3_BUCKET 설정을
     * 깜빡했을 때 실제 계정에 의도치 않은 버킷이 생기는 사고로 이어질 수 있어서,
     * 대신 기동을 실패시켜 설정 실수를 바로 드러낸다. 버킷이 이미 있으면 두 경우 다
     * headBucket이 성공해 아무 일도 안 한다.
     */
    @PostConstruct
    void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            if (localMode) {
                createBucketIfMissing();
            } else {
                throw new IllegalStateException(
                        "S3 버킷이 존재하지 않습니다: " + bucket
                                + " — 운영에서는 자동 생성하지 않으니 미리 만들어두세요", e);
            }
        }
    }

    /**
     * 인스턴스 여러 개가 동시에 기동하면 headBucket에서 둘 다 "없음"을 보고 동시에
     * 여기 들어올 수 있다. 늦게 도착한 쪽은 상대가 이미 만든 버킷 때문에
     * BucketAlreadyOwnedByYouException(같은 계정)이나 BucketAlreadyExistsException을
     * 받는데, 버킷은 어차피 존재하게 됐으니 기동 실패로 취급하지 않고 무시한다.
     */
    private void createBucketIfMissing() {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("S3 버킷 생성: {}", bucket);
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException e) {
            log.debug("S3 버킷이 이미 존재함(동시 기동 레이스로 추정): {}", bucket);
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

    /**
     * IMAGE 목록 카드용 webp 썸네일을 올린다. 키는 원본 키에서 파생시켜(원본키 + ".thumb.webp")
     * 한 이미지의 원본/썸네일이 같은 접두어로 묶이게 한다. 원본과 달리 이미 메모리에 있는
     * 바이트라 InputStream이 아닌 fromBytes로 올린다.
     */
    public String uploadThumbnail(byte[] bytes, String originalKey) {
        String key = originalKey + ".thumb.webp";
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType("image/webp")
                        .build(),
                RequestBody.fromBytes(bytes));
        return key;
    }

    /**
     * IMAGE 아이템 원본을 브라우저가 직접 읽을 수 있는 presigned GET URL을 만든다.
     * URL엔 만료 시각이 서명돼 있어 컬럼에 저장하면 안 되고(만료되면 죽은 링크), 대신
     * Redis에 잠시 캐시한다. presign은 네트워크 없는 순수 서명 연산이라 비용 절감이
     * 목적이 아니다 — 서명에 발급 시각이 들어가 호출마다 URL 문자열이 달라지는데,
     * 목록 폴링(PROCESSING 중 3초 간격)마다 다른 URL이 내려가면 브라우저가 같은
     * 이미지를 새 리소스로 보고 다시 받아 카드가 깜박인다. URL을 고정해야 브라우저가
     * 재요청 자체를 안 한다. 인스턴스 여러 대가 번갈아 응답해도 같은 URL이 내려가도록
     * 로컬 맵이 아닌 Redis에 둔다.
     */
    public String presignGet(String key) {
        String cacheKey = PRESIGN_CACHE_PREFIX + key;
        String cached = readCacheQuietly(cacheKey);
        if (cached != null) {
            return cached;
        }

        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGN_TTL)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build();
        String url = s3Presigner.presignGetObject(request).url().toString();
        writeCacheQuietly(cacheKey, url);
        return url;
    }

    /**
     * 캐시는 URL 고정용일 뿐이라 Redis 장애가 목록 조회까지 죽이면 안 된다 —
     * 실패하면 그냥 새로 발급한다(깜박임이 500보다 낫다).
     */
    private String readCacheQuietly(String cacheKey) {
        try {
            return redisTemplate.opsForValue().get(cacheKey);
        } catch (RuntimeException e) {
            log.warn("presigned URL 캐시 조회 실패 — 새로 발급으로 대체: {}", cacheKey, e);
            return null;
        }
    }

    private void writeCacheQuietly(String cacheKey, String url) {
        try {
            redisTemplate.opsForValue().set(cacheKey, url, PRESIGN_CACHE_TTL);
        } catch (RuntimeException e) {
            log.warn("presigned URL 캐시 저장 실패 — 다음 조회에서 재시도: {}", cacheKey, e);
        }
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
