package com.ssafy.woojuin.domain.ai;

/**
 * 분류 후보 카테고리. ai-mix 분류 입력은 후보마다 판단 기준 설명이 필수라
 * (묶음 F가 "이름+설명" 조건으로 분류 정확도를 평가했다) 이름만으로는 부족하다.
 *
 * @param id          워크스페이스 내 카테고리 id
 * @param name        카테고리 이름
 * @param description 판단 기준 설명. 아직 생성 전이면 null — 분석기가 이름으로 폴백한다
 */
public record CategoryCandidate(Long id, String name, String description) {
}
