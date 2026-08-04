package com.ssafy.woojuin.domain.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.MalformedURLException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * ensureBucketExists()의 로컬(MinIO)/운영(AWS S3) 분기와 동시 기동 레이스 흡수,
 * presignGet()의 URL 고정 캐시(목록 폴링 깜박임 방지)를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class S3UploaderTest {

    @Mock S3Client s3Client;
    @Mock S3Presigner s3Presigner;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock PresignedGetObjectRequest presignedRequest;

    private S3Uploader localUploader() {
        return new S3Uploader(s3Client, s3Presigner, redisTemplate, "woojuin-items", "http://localhost:9000");
    }

    @Test
    void 버킷이_이미_있으면_로컬이든_운영이든_아무것도_안한다() {
        S3Uploader uploader = localUploader();

        uploader.ensureBucketExists();

        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void 로컬모드에서_버킷이_없으면_생성한다() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().build());
        S3Uploader uploader = localUploader();

        uploader.ensureBucketExists();

        verify(s3Client).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void 로컬모드에서_동시기동으로_이미_생성됐으면_무시한다() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().build());
        when(s3Client.createBucket(any(CreateBucketRequest.class)))
                .thenThrow(BucketAlreadyOwnedByYouException.builder().build());
        S3Uploader uploader = localUploader();

        assertThatCode(uploader::ensureBucketExists).doesNotThrowAnyException();
    }

    @Test
    void 운영모드에서_버킷이_없으면_자동생성하지_않고_예외를_던진다() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().build());
        S3Uploader uploader = new S3Uploader(s3Client, s3Presigner, redisTemplate, "woojuin-items", "");

        assertThatThrownBy(uploader::ensureBucketExists)
                .isInstanceOf(IllegalStateException.class);

        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    // ---- presignGet: URL이 폴링마다 바뀌면 카드가 깜박여서, 캐시로 고정한다 ----

    private static final String KEY = "items/1/photo.png";
    private static final String CACHE_KEY = S3Uploader.PRESIGN_CACHE_PREFIX + KEY;

    private void stubPresignedUrl(String url) throws MalformedURLException {
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);
        when(presignedRequest.url()).thenReturn(URI.create(url).toURL());
    }

    @Test
    void 캐시에_있으면_새로_서명하지_않고_같은_URL을_돌려준다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn("https://s3.example/cached");

        String url = localUploader().presignGet(KEY);

        assertThat(url).isEqualTo("https://s3.example/cached");
        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void 캐시에_없으면_서명해서_돌려주고_캐시에_저장한다() throws MalformedURLException {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        stubPresignedUrl("https://s3.example/signed");

        String url = localUploader().presignGet(KEY);

        assertThat(url).isEqualTo("https://s3.example/signed");
        verify(valueOperations).set(eq(CACHE_KEY), eq("https://s3.example/signed"), any(Duration.class));
    }

    @Test
    void Redis가_죽어도_조회가_실패하면_안되니_새로_서명해서_돌려준다() throws MalformedURLException {
        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        stubPresignedUrl("https://s3.example/signed");

        assertThat(localUploader().presignGet(KEY)).isEqualTo("https://s3.example/signed");
    }
}
