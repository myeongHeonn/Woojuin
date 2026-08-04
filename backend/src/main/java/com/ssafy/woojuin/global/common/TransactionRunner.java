package com.ssafy.woojuin.global.common;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 트랜잭션이 필요한 구간만 람다로 감싸 실행하는 헬퍼.
 *
 * <p>아이템 가공 파이프라인이 쓴다 — 프로세서 process() 전체에 @Transactional을 걸면
 * 크롤링·LLM 호출(수십 초) 내내 DB 커넥션 하나를 점유해서, 컨슈머 3 + 회수기 1이 겹치면
 * 풀(기본 10)의 절반 가까이가 놀면서 잠긴다. 2026-07-31 dev 최종 테스트에서 이 구조로
 * 커넥션 타임아웃 15건이 실제로 발생했다. 외부 호출은 트랜잭션 밖에서 하고, DB 반영만
 * 이 헬퍼로 짧게 감싼다.
 *
 * <p>별도 빈인 이유: @Transactional은 프록시 기반이라 같은 클래스 안에서 자기 메서드를
 * 부르면(자기 호출) 적용되지 않는다. 순수 Mockito 단위 테스트에서는 {@code new
 * TransactionRunner()}로 만들면 람다가 트랜잭션 없이 인라인 실행된다 — 기존 테스트
 * 방식과 동일하게 동작한다.
 */
@Component
public class TransactionRunner {

    /** 쓰기 트랜잭션 안에서 work를 실행한다. work가 던지면 전부 롤백된다. */
    @Transactional
    public void write(Runnable work) {
        work.run();
    }
}
