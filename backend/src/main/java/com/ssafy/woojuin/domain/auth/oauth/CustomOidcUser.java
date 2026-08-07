package com.ssafy.woojuin.domain.auth.oauth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.Map;

/**
 * OidcUser를 감싸서 로컬 DB의 userId를 함께 노출한다.
 * 로그인 성공 핸들러가 이 userId로 JWT를 발급한다.
 */
public class CustomOidcUser implements OidcUser {

    private final Long userId;
    private final boolean newUser;
    private final OidcUser delegate;

    public CustomOidcUser(Long userId, boolean newUser, OidcUser delegate) {
        this.userId = userId;
        this.newUser = newUser;
        this.delegate = delegate;
    }

    public Long getUserId() {
        return userId;
    }

    /** 이번 로그인이 곧 최초 가입이었는가 — 로그인 성공 핸들러가 온보딩 리다이렉트 여부를 정한다. */
    public boolean isNewUser() {
        return newUser;
    }

    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
}
