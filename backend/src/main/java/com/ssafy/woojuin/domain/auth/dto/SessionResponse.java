package com.ssafy.woojuin.domain.auth.dto;

import com.ssafy.woojuin.domain.auth.session.DeviceNameParser;
import com.ssafy.woojuin.domain.auth.session.UserSession;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 기기 관리 화면(S15P11C105-460)의 "연결된 기기" 한 행.
 *
 * current 를 서버가 판별하는 이유: "지금 쓰는 기기" 표시는 호출자의 sid 와 세션의 sid 비교인데,
 * 클라이언트는 자기 sid 를 모른다(토큰 안 클레임이라 파싱해야 한다). 서버가 이미 둘 다 안다.
 */
public record SessionResponse(
        String sessionId,
        String deviceName,
        OffsetDateTime createdAt,
        OffsetDateTime lastUsedAt,
        boolean current) {

    public static SessionResponse from(UserSession session, String currentSid) {
        return new SessionResponse(
                session.sid(),
                DeviceNameParser.parse(session.userAgent()),
                toOffsetDateTime(session.createdAt()),
                toOffsetDateTime(session.lastUsedAt()),
                session.sid().equals(currentSid));
    }

    private static OffsetDateTime toOffsetDateTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC);
    }
}
