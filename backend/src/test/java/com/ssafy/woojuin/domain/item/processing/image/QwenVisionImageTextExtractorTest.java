package com.ssafy.woojuin.domain.item.processing.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * HTTP 호출을 제외한 순수 로직(classificationText 조립·코드펜스 제거)을 검증한다.
 * 조립 형식은 묶음 F의 image_service.integration.build_classification_text와
 * 동일해야 한다 — F가 이 형식으로 분류 품질을 검증했다.
 */
class QwenVisionImageTextExtractorTest {

    private QwenVisionImageTextExtractor extractor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        extractor = new QwenVisionImageTextExtractor(
                "http://localhost:0", "test-key", "test-model",
                Duration.ofSeconds(1), "prompt", objectMapper);
    }

    @Test
    void 모든_필드가_있으면_네_줄로_조립한다() throws Exception {
        String text = extractor.toClassificationText(objectMapper.readTree("""
                {
                  "title": "카페 영수증",
                  "description": "카페에서 결제한 영수증 사진",
                  "tags": ["영수증", "카페", "결제"],
                  "ocr_text": "아메리카노 4,500원",
                  "objects": ["영수증", "테이블"],
                  "confidence": 0.95
                }
                """));

        assertThat(text).isEqualTo("""
                이미지 설명: 카페에서 결제한 영수증 사진
                OCR 텍스트: 아메리카노 4,500원
                태그: 영수증, 카페, 결제
                주요 객체: 영수증, 테이블""");
    }

    /** F의 계약: OCR이 없는 일반 사진은 빈 OCR 줄을 넣지 않는다. */
    @Test
    void 빈_필드는_줄을_생략한다() throws Exception {
        String text = extractor.toClassificationText(objectMapper.readTree("""
                {
                  "description": "노을 지는 해변 풍경",
                  "tags": ["해변", "노을"],
                  "ocr_text": "",
                  "objects": []
                }
                """));

        assertThat(text).isEqualTo("""
                이미지 설명: 노을 지는 해변 풍경
                태그: 해변, 노을""");
    }

    @Test
    void 필드가_전부_비면_빈_문자열이다() throws Exception {
        String text = extractor.toClassificationText(objectMapper.readTree("{}"));

        assertThat(text).isEmpty();
    }

    /** response_format을 무시하는 모델·프록시 대비(LlmQueryPlanner와 동일 방어). */
    @Test
    void 코드펜스로_감싼_JSON을_걷어낸다() {
        String content = "```json\n{\"description\": \"테스트\"}\n```";

        assertThat(QwenVisionImageTextExtractor.stripCodeFence(content))
                .isEqualTo("{\"description\": \"테스트\"}");
    }

    @Test
    void 코드펜스가_없으면_그대로_둔다() {
        assertThat(QwenVisionImageTextExtractor.stripCodeFence("  {\"a\": 1}  "))
                .isEqualTo("{\"a\": 1}");
    }
}
