package com.ssafy.woojuin.domain.workspace.entity;

/** 멤버 활동 피드에 표시되는 사건 종류 — 가입/자진 탈퇴/강제 추방을 구분한다. */
public enum WorkspaceMemberActivityType {
    JOINED,
    LEFT,
    KICKED,
}
