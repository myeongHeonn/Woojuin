package com.ssafy.woojuin.global.sse;

/**
 * 변경 신호의 종류 — 리소스 단위로 묶는다.
 *
 * <p>동작마다(즐겨찾기 · 이름변경 · 삭제…) 타입을 쪼개면 발행 지점이 수십 개로 늘고
 * 클라이언트 분기도 그만큼 복잡해진다. 어차피 클라이언트가 하는 일은 "관련 캐시 무효화"라
 * 무효화 대상이 같은 것끼리 한 타입으로 묶는 편이 유지보수에 낫다.
 */
public enum WorkspaceEventType {

    /** 아이템 생성 · 처리완료 · 수정 · 삭제 · 즐겨찾기 · 휴지통 */
    ITEM,

    /** 카테고리 추가 · 이름변경 · 삭제 */
    CATEGORY,

    /** 워크스페이스 이름변경 · 삭제 */
    WORKSPACE,

    /** 초대 수락 · 멤버 제거 · 탈퇴 */
    MEMBER;

    /** SSE 이벤트 이름 — 클라이언트가 addEventListener 로 구독하는 값이라 소문자로 고정한다 */
    public String eventName() {
        return name().toLowerCase();
    }
}
