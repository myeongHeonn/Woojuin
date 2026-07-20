package com.ssafy.woojuin.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * @EnableJpaAuditing을 메인 애플리케이션 클래스가 아닌 여기에 둔다. 메인 클래스에 붙이면
 * @WebMvcTest 같은 슬라이스 테스트도 JPA 메타모델을 요구하게 돼서
 * "JPA metamodel must not be empty"로 전부 실패한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
