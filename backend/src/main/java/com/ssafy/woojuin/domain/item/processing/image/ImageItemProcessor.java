package com.ssafy.woojuin.domain.item.processing.image;

import com.ssafy.woojuin.domain.ai.AiAnalysis;
import com.ssafy.woojuin.domain.ai.AiAnalysisRequest;
import com.ssafy.woojuin.domain.ai.AiAnalyzer;
import com.ssafy.woojuin.domain.ai.AiSourceType;
import com.ssafy.woojuin.domain.ai.CategoryCandidate;
import com.ssafy.woojuin.domain.category.service.CategoryAssignmentService;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.event.ItemDoneEvent;
import com.ssafy.woojuin.domain.item.processing.ItemProcessingMessage;
import com.ssafy.woojuin.domain.item.processing.ItemProcessor;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import com.ssafy.woojuin.domain.item.service.S3Uploader;
import com.ssafy.woojuin.domain.location.LocationResolver;
import com.ssafy.woojuin.domain.location.ResolvedLocation;
import com.ssafy.woojuin.global.common.ItemStatus;
import com.ssafy.woojuin.global.sse.WorkspaceChangedEvent;
import com.ssafy.woojuin.global.sse.WorkspaceEventType;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이미지 아이템 가공 오케스트레이터 (묶음 D).
 *
 * <p>{@code createFromImage}는 S3 업로드 성공 후에만 Item을 만들기 때문에 이
 * 프로세서가 도는 시점엔 원본 이미지가 항상 존재한다 — URL의 트랙 A(미리보기)에
 * 대응하며 항상 성공한다고 볼 수 있다. OCR(트랙 B에 대응)로 텍스트를 확보하면
 * DONE, 실패·빈 결과면 PARTIAL. 이 프로세서에서 FAILED는 발생하지 않는다.
 *
 * <p>AI 보강은 OCR 결과 유무와 무관하게 항상 시도한다(텍스트가 없으면 title만으로
 * 분류 — UrlItemProcessor와 동일 계약).
 */
@Slf4j
@Component
public class ImageItemProcessor implements ItemProcessor {

    private final ItemRepository itemRepository;
    private final S3Uploader s3Uploader;
    private final ImageTextExtractor imageTextExtractor;
    private final ImageThumbnailGenerator thumbnailGenerator;
    private final AiAnalyzer aiAnalyzer;
    private final CategoryAssignmentService categoryAssignmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final ExifGpsReader exifGpsReader;
    private final LocationResolver locationResolver;

    public ImageItemProcessor(ItemRepository itemRepository, S3Uploader s3Uploader,
            ImageTextExtractor imageTextExtractor, ImageThumbnailGenerator thumbnailGenerator,
            AiAnalyzer aiAnalyzer, CategoryAssignmentService categoryAssignmentService,
            ApplicationEventPublisher eventPublisher,
            ExifGpsReader exifGpsReader, LocationResolver locationResolver) {
        this.itemRepository = itemRepository;
        this.s3Uploader = s3Uploader;
        this.imageTextExtractor = imageTextExtractor;
        this.thumbnailGenerator = thumbnailGenerator;
        this.aiAnalyzer = aiAnalyzer;
        this.categoryAssignmentService = categoryAssignmentService;
        this.eventPublisher = eventPublisher;
        this.exifGpsReader = exifGpsReader;
        this.locationResolver = locationResolver;
    }

    @Override
    public boolean supports(ItemType type) {
        return type == ItemType.IMAGE;
    }

    @Override
    @Transactional
    public void process(ItemProcessingMessage message) {
        Item item = itemRepository.findById(message.itemId()).orElse(null);
        if (item == null) {
            log.warn("가공할 아이템이 없음(삭제됨?): itemId={}", message.itemId());
            return;
        }
        if (item.getStatus() != ItemStatus.PROCESSING) {
            // at-least-once 큐 특성상 이미 끝난 메시지가 재배달될 수 있다.
            // AI를 또 호출하지 않도록 여기서 막는다.
            log.info("이미 처리된 아이템, 재처리 스킵: itemId={}, status={}", item.getId(), item.getStatus());
            return;
        }

        // 원본을 한 번만 내려받아 OCR과 썸네일 생성에 함께 쓴다.
        byte[] bytes = tryDownload(item.getS3Key());
        String text = tryExtract(bytes);
        boolean textAcquired = text != null;
        if (textAcquired) {
            item.applyContent(text);
        }

        tryGenerateThumbnail(item, bytes);
        // 위치 확보(FR-023). 이미 내려받은 bytes를 재사용하므로 추가 다운로드가 없다.
        tryApplyExifLocation(item, bytes);
        enrichWithAi(item, text);
        finalizeStatus(item, textAcquired);
    }

    /**
     * 사진 EXIF의 GPS 좌표를 읽어 반영하고, 역지오코딩으로 주소를 채운다 (FR-023).
     *
     * <p>썸네일 뒤에 두는 건 느린 역지오코딩이 목록 카드에 필요한 썸네일 생성을 지연시키지
     * 않게 하려는 것이다. 위치는 썸네일과 같은 등급의 부가 정보라 상태에는 영향이 없다.
     *
     * <p>역지오코딩이 실패해도 좌표는 저장된다 — 핀이 목적이고 주소는 장식이다.
     *
     * <p>모든 실패를 흡수한다. {@code process}가 {@code @Transactional}이라 예외가 새어나가면
     * 트랜잭션이 rollback-only로 찍혀 <b>이미 확보한 OCR 텍스트·썸네일이 버려지고</b>
     * 디스패처가 RETRYABLE로 판단해 파이프라인 전체가 재실행된다.
     */
    private void tryApplyExifLocation(Item item, byte[] bytes) {
        try {
            exifGpsReader.read(bytes).ifPresent(point -> {
                ResolvedLocation location = locationResolver.resolveForCoordinates(point);
                item.applyLocation(location.lat(), location.lng(), location.address());
            });
        } catch (Exception e) {
            log.warn("EXIF 위치 확보 실패(무시): itemId={}, cause={}", item.getId(), e.toString());
        }
    }

    /** S3 원본 바이트를 읽는다. 실패해도 이미지 자체는 S3에 있으므로 null만 반환하고 넘어간다. */
    private byte[] tryDownload(String s3Key) {
        try {
            return s3Uploader.download(s3Key);
        } catch (Exception e) {
            log.info("이미지 다운로드 실패: cause={}", e.getMessage());
            return null;
        }
    }

    /** OCR로 텍스트를 뽑는다. 바이트가 없거나 OCR이 실패/빈 결과면 null. */
    private String tryExtract(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            String text = imageTextExtractor.extract(bytes);
            return (text != null && !text.isBlank()) ? text : null;
        } catch (Exception e) {
            log.info("OCR 실패: cause={}", e.getMessage());
            return null;
        }
    }

    /**
     * 목록 카드용 webp 썸네일을 만들어 S3에 올리고 thumbnailS3Key를 저장한다. 최적화지
     * 필수 경로가 아니므로 어떤 실패도(리사이즈·업로드) 조용히 흡수한다 — 썸네일이 없으면
     * 목록은 원본 presigned로 폴백한다(ItemService.thumbnailImageUrlOf). 상태에는 영향 없다.
     */
    private void tryGenerateThumbnail(Item item, byte[] bytes) {
        if (bytes == null) {
            return;
        }
        try {
            byte[] thumbnail = thumbnailGenerator.toThumbnail(bytes);
            if (thumbnail != null) {
                item.applyThumbnail(s3Uploader.uploadThumbnail(thumbnail, item.getS3Key()));
            }
        } catch (Exception e) {
            log.warn("썸네일 생성 실패(무시), 목록은 원본으로 폴백: itemId={}, cause={}", item.getId(), e.toString());
        }
    }

    /**
     * AI 요약·분류를 반영한다. 어떤 실패도 이미 확보한 이미지·본문을 무효화하면
     * 안 되므로 조용히 흡수한다(UrlItemProcessor.enrichWithAi와 동일 패턴).
     */
    private void enrichWithAi(Item item, String text) {
        try {
            List<CategoryCandidate> candidates = categoryAssignmentService.candidates(item.getWorkspaceId());
            AiAnalysis analysis = aiAnalyzer.analyze(
                    new AiAnalysisRequest(AiSourceType.IMAGE, item.getTitle(), text, candidates));
            // 이미지 제목은 대부분 파일명이라 AI 제목의 효과가 가장 크다(null이면 기존 유지).
            item.update(analysis.title(), null);
            item.applySummary(analysis.summary());
            categoryAssignmentService.assign(item.getId(), item.getWorkspaceId(), analysis.categories());
        } catch (Exception e) {
            log.warn("AI 보강 실패(무시): itemId={}, cause={}", item.getId(), e.toString());
        }
    }

    private void finalizeStatus(Item item, boolean textAcquired) {
        if (textAcquired) {
            item.markDone();
        } else {
            item.markPartial();
        }
        eventPublisher.publishEvent(WorkspaceChangedEvent.of(item.getWorkspaceId(), WorkspaceEventType.ITEM));
        // PARTIAL도 사용자에게 알려줄 결과가 있다(썸네일·원본 이미지는 저장됨) — FAILED만 생략.
        eventPublisher.publishEvent(new ItemDoneEvent(item.getId()));
        log.info("이미지 가공 완료: itemId={}, status={}", item.getId(), item.getStatus());
    }
}
