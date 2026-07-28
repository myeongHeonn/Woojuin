package com.ssafy.woojuin.domain.item.repository;

/** 검색어를 여러 토큰으로 쪼갰을 때 몇 개나 맞아야 결과에 넣을지. */
public enum MatchMode {

    /** 모든 토큰이 등장해야 한다. 정확도 우선 — 기본 경로. */
    ALL,

    /**
     * 토큰 하나만 등장해도 된다. ALL이 0건일 때만 쓰는 폴백으로, 응답에 partialMatch=true가
     * 실려 클라이언트가 "일부만 일치하는 결과"임을 알릴 수 있다.
     */
    ANY
}
