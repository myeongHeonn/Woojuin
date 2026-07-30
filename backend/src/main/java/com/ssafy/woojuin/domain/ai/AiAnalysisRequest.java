package com.ssafy.woojuin.domain.ai;

import java.util.List;

/**
 * AI 분석 입력. 세 아이템 타입이 모두 "텍스트"로 수렴한 뒤 이 형태로 들어온다.
 * URL은 본문 추출 결과(묶음 D), 이미지는 OCR·설명 추출 결과, 메모는 사용자가 쓴 내용 그대로.
 *
 * @param itemId              파이프라인 타이밍 로그를 한 아이템으로 묶는 식별자
 * @param sourceType          원천 타입. ai-mix가 타입별 제목·요약 프롬프트를 갈아끼우는 기준
 * @param title               아이템 제목. 본문이 짧거나 없을 때 분류 정확도를 올려준다 (nullable)
 * @param text                분석 대상 본문. 본문 확보에 실패한 아이템(PARTIAL, 예: 영상)은
 *                            null/blank일 수 있으며, 그땐 title만으로 진행한다
 * @param candidateCategories 이 아이템이 속한 워크스페이스에 <b>현재 존재하는</b> 카테고리
 *                            목록(id·이름·설명). AI는 이 중에서만 골라야 하며 새 카테고리를
 *                            만들지 않는다. 워크스페이스마다 기본 11개 + 사용자가 추가/수정/삭제한
 *                            결과라 요청마다 다르다
 */
public record AiAnalysisRequest(Long itemId, AiSourceType sourceType, String title, String text,
        List<CategoryCandidate> candidateCategories) {
}
