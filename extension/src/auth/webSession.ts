import { WEB_ORIGIN } from '@/api/client';
import { saveTokens } from '@/storage/authStorage';

/**
 * 웹앱 로그인 세션 물려받기.
 *
 * 확장에 자체 로그인 폼을 두지 않는 이유:
 *  - 웹앱은 이메일·비밀번호 로그인을 쓰지 않는다(LoginForm.tsx 에서 주석 처리). 구글 OAuth 뿐이고,
 *    구글은 첫 로그인이 곧 가입이다(OAuthAccountService.findOrCreateUser).
 *  - 그래서 실사용자는 password_hash 가 아예 없어 /auth/login 으로는 절대 통과할 수 없다.
 *  - OAuth 를 확장 안에서 직접 하려면(chrome.identity) 백엔드가 확장용 redirect_uri 를
 *    받아주고 구글 콘솔에도 등록해야 한다. 웹 세션을 물려받으면 그 변경이 전부 불필요하다.
 *
 * 토큰은 웹앱이 localStorage 에 둔다(jotai atomWithStorage → JSON 문자열).
 * 확장 페이지·서비스워커는 host_permissions 가 있으면 그 탭에 스크립트를 넣어 읽을 수 있다.
 */
const ACCESS_STORAGE_KEY = 'woojuin:accessToken';
const REFRESH_STORAGE_KEY = 'woojuin:refreshToken';

/** atomWithStorage 는 값을 JSON 으로 넣는다 — 원문이 `"eyJhbGc..."` 처럼 따옴표째 들어 있다. */
function parseStoredToken(raw: string | null | undefined): string | null {
  if (!raw) return null;
  try {
    const value: unknown = JSON.parse(raw);
    return typeof value === 'string' && value.length > 0 ? value : null;
  } catch {
    return null;
  }
}

/** 웹앱 로그인 화면을 새 탭으로 연다. 계정이 없어도 구글 로그인이 곧 가입이다. */
export async function openWebLogin(): Promise<void> {
  await chrome.tabs.create({ url: `${WEB_ORIGIN}/login` });
}

/**
 * 열려 있는 우주인 탭에서 토큰을 가져와 확장 저장소에 넣는다.
 *
 * @param tabId 특정 탭만 볼 때(백그라운드의 탭 갱신 감지). 없으면 우주인 탭 전체를 훑는다.
 * @returns 세션을 얻었는지
 */
export async function harvestWebSession(tabId?: number): Promise<boolean> {
  const targets = tabId === undefined
    ? (await chrome.tabs.query({ url: `${WEB_ORIGIN}/*` }))
        .map((tab) => tab.id)
        .filter((id): id is number => id !== undefined)
    : [tabId];

  for (const target of targets) {
    try {
      const [injection] = await chrome.scripting.executeScript({
        target: { tabId: target },
        // 페이지 컨텍스트에서 실행된다 — 확장 코드의 상수를 참조할 수 없어 키를 그대로 적는다.
        func: () => ({
          access: localStorage.getItem('woojuin:accessToken'),
          refresh: localStorage.getItem('woojuin:refreshToken'),
        }),
      });
      const accessToken = parseStoredToken(injection?.result?.access);
      const refreshToken = parseStoredToken(injection?.result?.refresh);
      // 액세스 토큰은 1시간짜리라 리프레시가 없으면 곧 만료된다 — 둘 다 있을 때만 인정한다.
      if (accessToken && refreshToken) {
        await saveTokens(accessToken, refreshToken);
        return true;
      }
    } catch {
      // 탭이 닫혔거나(권한 회수 포함) 주입이 막힌 경우 — 다음 탭을 본다.
    }
  }
  return false;
}

export { ACCESS_STORAGE_KEY, REFRESH_STORAGE_KEY };
