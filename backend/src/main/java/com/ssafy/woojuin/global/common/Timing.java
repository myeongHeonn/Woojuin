package com.ssafy.woojuin.global.common;

import java.util.concurrent.TimeUnit;

/** 벽시계 변경의 영향을 받지 않는 구간 소요 시간 측정 유틸리티. */
public final class Timing {

    private Timing() {
    }

    public static long start() {
        return System.nanoTime();
    }

    public static long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
