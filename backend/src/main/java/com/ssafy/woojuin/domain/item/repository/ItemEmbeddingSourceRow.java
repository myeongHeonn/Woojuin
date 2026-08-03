package com.ssafy.woojuin.domain.item.repository;

/**
 * {@link ItemRepository#findEmbeddingSourceRows} 프로젝션 — 임베딩 백필의 입력 행.
 * 카테고리는 백필 쪽이 별도 벌크 조회로 붙인다(임베딩 계약상 카테고리 1개 이상 필수).
 */
public record ItemEmbeddingSourceRow(
        Long itemId,
        String title,
        String summary) {
}
