package com.ssafy.woojuin.domain.ai.usage;

public class AiUsageLimitExceededException extends RuntimeException {

    public AiUsageLimitExceededException(int limit) {
        super("AI_USAGE_LIMIT_EXCEEDED: 이번 달 아이템 생성 한도(" + limit + "개)를 모두 사용했습니다");
    }
}
