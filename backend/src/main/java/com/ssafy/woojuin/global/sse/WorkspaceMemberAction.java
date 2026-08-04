package com.ssafy.woojuin.global.sse;

/**
 * {@link WorkspaceEventType#MEMBER} 신호에 실리는 세부 종류 — 역할 변경(null)과 구분해
 * 클라이언트가 활동 피드를 다시 불러올지(가입/탈퇴/추방) 멤버 목록만 다시 불러올지 고르게 한다.
 * 신호일 뿐 데이터는 아니다({@link WorkspaceEvent} 설계와 동일한 원칙) — 닉네임·시각 등은
 * 여전히 REST 재조회로 받는다.
 */
public enum WorkspaceMemberAction {
    JOINED,
    LEFT,
    KICKED,
}
