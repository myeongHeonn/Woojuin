package com.ssafy.woojuin.domain.category.event;

/**
 * 카테고리에 AI 분류용 설명이 필요해졌음을 알리는 이벤트 — 사용자가 카테고리를 새로
 * 만들었거나 이름을 바꿔 기존 설명이 비워졌을 때 발행된다. 수신 측
 * ({@code CategoryDescriptionGenerator})이 커밋 후 비동기로 설명을 생성해 채운다.
 */
public record CategoryDescriptionNeededEvent(Long categoryId, String name) {
}
