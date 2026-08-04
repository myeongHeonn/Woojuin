package com.ssafy.woojuin.domain.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    /**
     * 세션 id 클레임 — 이 토큰이 어느 로그인(기기)에서 나왔는지.
     *
     * 이게 들어가면서 "같은 사용자가 같은 초에 두 번 로그인하면 토큰 문자열이 완전히
     * 동일"하던 문제도 사라진다 — 전에는 클레임이 sub·iat·exp 뿐이었다.
     */
    public static final String SESSION_ID_CLAIM = "sid";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, String sessionId) {
        return createToken(userId, sessionId, properties.accessTokenValidity());
    }

    public String createRefreshToken(Long userId, String sessionId) {
        return createToken(userId, sessionId, properties.refreshTokenValidity());
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    /**
     * 세션 id. **이 구조가 배포되기 전에 발급된 토큰은 null 이다** — 그런 access token 은
     * 남은 수명(최대 1시간) 동안 필터를 통과하지만 세션이 없어 개별 차단은 못 하고,
     * refresh 는 세션 조회에서 거부된다(전원 1회 재로그인, S15P11C105-459 의 배포 결정).
     */
    public String getSessionId(String token) {
        return parseClaims(token).get(SESSION_ID_CLAIM, String.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String createToken(Long userId, String sessionId, long validityMillis) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMillis);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(SESSION_ID_CLAIM, sessionId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }
}
