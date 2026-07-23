package com.ssafy.woojuin.domain.item.processing.image;

import com.ssafy.woojuin.domain.ai.AiAnalysis;
import com.ssafy.woojuin.domain.ai.AiAnalysisRequest;
import com.ssafy.woojuin.domain.ai.AiAnalyzer;
import com.ssafy.woojuin.domain.category.service.CategoryAssignmentService;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.processing.ItemProcessingMessage;
import com.ssafy.woojuin.domain.item.processing.ItemProcessor;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import com.ssafy.woojuin.domain.item.service.S3Uploader;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
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
    private final AiAnalyzer aiAnalyzer;
    private final CategoryAssignmentService categoryAssignmentService;

    public ImageItemProcessor(ItemRepository itemRepository, S3Uploader s3Uploader,
            ImageTextExtractor imageTextExtractor, AiAnalyzer aiAnalyzer,
            CategoryAssignmentService categoryAssignmentService) {
        this.itemRepository = itemRepository;
        this.s3Uploader = s3Uploader;
        this.imageTextExtractor = imageTextExtractor;
        this.aiAnalyzer = aiAnalyzer;
        this.categoryAssignmentService = categoryAssignmentService;
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

        String text = downloadAndExtract(item.getS3Key());
        boolean textAcquired = text != null;
        if (textAcquired) {
            item.applyContent(text);
        }

        enrichWithAi(item, text);
        finalizeStatus(item, textAcquired);
    }

    /**
     * S3에서 원본을 읽어 OCR로 텍스트를 뽑는다. 다운로드·OCR 어느 쪽이 실패해도
     * 이미지 자체는 이미 S3에 있으므로 null만 반환하고 조용히 넘어간다.
     */
    private String downloadAndExtract(String s3Key) {
        try {
            byte[] bytes = s3Uploader.download(s3Key);
            String text = imageTextExtractor.extract(bytes);
            return (text != null && !text.isBlank()) ? text : null;
        } catch (Exception e) {
            log.info("이미지 다운로드/OCR 실패: cause={}", e.getMessage());
            return null;
        }
    }

    /**
     * AI 요약·분류를 반영한다. 어떤 실패도 이미 확보한 이미지·본문을 무효화하면
     * 안 되므로 조용히 흡수한다(UrlItemProcessor.enrichWithAi와 동일 패턴).
     */
    private void enrichWithAi(Item item, String text) {
        try {
            List<String> candidates = categoryAssignmentService.candidateNames(item.getWorkspaceId());
            AiAnalysis analysis = aiAnalyzer.analyze(
                    new AiAnalysisRequest(item.getTitle(), text, candidates));
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
        log.info("이미지 가공 완료: itemId={}, status={}", item.getId(), item.getStatus());
    }
}
