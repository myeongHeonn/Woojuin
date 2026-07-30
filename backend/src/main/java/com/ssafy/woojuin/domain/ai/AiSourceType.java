package com.ssafy.woojuin.domain.ai;

/**
 * 분석 대상의 원천 타입. ai-mix가 타입별로 다른 제목·요약 프롬프트를 쓰므로
 * ({@code /v1/title-summary/{memo|url|image}}) 요청에 실어 보낸다.
 *
 * <p>{@code domain.item.ItemType}을 재사용하지 않는 건 의존 방향 때문이다 —
 * item.processing이 ai를 쓰는 구조라, ai가 item을 되참조하면 순환이 된다.
 */
public enum AiSourceType {
    MEMO, URL, IMAGE;

    /** ai-mix 엔드포인트 경로 조각. */
    public String pathSegment() {
        return name().toLowerCase();
    }
}
