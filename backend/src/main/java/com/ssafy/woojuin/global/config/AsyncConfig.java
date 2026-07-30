package com.ssafy.woojuin.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @Async} 활성화. 현재 사용처는 카테고리 설명 생성
 * ({@code CategoryDescriptionGenerator}) 하나다 — 사용자 요청(카테고리 생성/이름 변경)
 * 트랜잭션이 LLM 호출을 기다리지 않게 커밋 후 백그라운드로 돌린다.
 *
 * <p>별도 스레드풀을 정의하지 않고 Boot 기본 executor를 쓴다 — 호출 빈도가 낮아
 * (사용자가 카테고리를 만들 때뿐) 튜닝할 이유가 생기면 그때 붙인다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
