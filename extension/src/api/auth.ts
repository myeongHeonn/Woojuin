import { clearTokens } from '@/storage/authStorage';

/**
 * 로그인은 웹앱의 구글 OAuth 를 쓰고 그 세션을 물려받는다 — 이유는 auth/webSession.ts 참고.
 * 그래서 여기에 /auth/login 을 호출하는 함수가 없다: 웹앱이 이메일·비밀번호 로그인을 쓰지
 * 않으므로 실사용자는 password_hash 가 없고, 그 경로로는 애초에 통과할 수 없었다.
 *
 * 로그아웃은 확장이 가진 토큰만 지운다 — 확장에서 로그아웃했다고 브라우저의 우주인 탭까지
 * 로그아웃되면 놀란다. 다시 로그인하려면 열려 있는 우주인 탭에서 세션을 다시 물려받는다.
 */
export { clearTokens as logout };
