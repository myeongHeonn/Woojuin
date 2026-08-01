import { useCallback, useEffect, useRef, useState } from 'react';
import { logout } from '@/api/auth';
import { ApiError } from '@/api/client';
import { saveUrl } from '@/api/items';
import { getWorkspaces, Workspace } from '@/api/workspaces';
import { harvestWebSession, openWebLogin } from '@/auth/webSession';
import { getAccessToken, getRefreshToken } from '@/storage/authStorage';
import { clearSelectedWorkspaceId, getSelectedWorkspaceId, setSelectedWorkspaceId } from '@/storage/workspaceStorage';

type Status = 'idle' | 'loading' | 'saving' | 'success' | 'error';
interface ContextSaveFeedback {
  success: boolean;
  message: string;
  createdAt: number;
}

const CONTEXT_FEEDBACK_KEY = 'contextSaveFeedback';
const messageFrom = (error: unknown) =>
  error instanceof ApiError && error.status === 401
    ? '로그인이 만료되었습니다. 우주인에서 다시 로그인해 주세요.'
    : error instanceof Error ? error.message : '요청을 처리하지 못했습니다.';

function isSavableUrl(value: string): boolean {
  try { return ['http:', 'https:'].includes(new URL(value).protocol); } catch { return false; }
}

/** 구글 4색 "G" 마크 — 공식 자산이라 색·형태를 바꾸지 않는다 (웹앱 GoogleAuthButton 과 동일) */
const GoogleMark = () => (
  <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true" style={{ flexShrink: 0 }}>
    <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
    <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
    <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
    <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
  </svg>
);

export default function Popup() {
  const [url, setUrl] = useState('');
  const [authenticated, setAuthenticated] = useState<boolean | null>(null);
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [workspaceId, setWorkspaceId] = useState<number | null>(null);
  const [status, setStatus] = useState<Status>('idle');
  const [message, setMessage] = useState('');
  const [loginOpened, setLoginOpened] = useState(false);
  const [urlExpanded, setUrlExpanded] = useState(false);
  const [urlOverflowing, setUrlOverflowing] = useState(false);
  const [contextFeedback, setContextFeedback] = useState<ContextSaveFeedback | null>(null);
  const urlRef = useRef<HTMLParagraphElement>(null);

  const loadWorkspaces = useCallback(async () => {
    setStatus('loading');
    try {
      const [items, previousId] = await Promise.all([getWorkspaces(), getSelectedWorkspaceId()]);
      const selected = items.find((item) => item.id === previousId)
        ?? items.find((item) => item.type === 'PERSONAL') ?? items[0] ?? null;
      setWorkspaces(items);
      setWorkspaceId(selected?.id ?? null);
      if (selected) await setSelectedWorkspaceId(selected.id);
      setStatus('idle');
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) setAuthenticated(false);
      setStatus('error');
      setMessage(messageFrom(error));
    }
  }, []);

  useEffect(() => {
    void (async () => {
      const [[tab], accessToken, refreshToken] = await Promise.all([
        chrome.tabs.query({ active: true, currentWindow: true }),
        getAccessToken(),
        getRefreshToken(),
      ]);
      setUrl(tab?.url ?? '');
      // 확장에 토큰이 없어도 우주인 탭이 열려 있으면 그 세션을 물려받는다 —
      // 웹에서 로그인하고 돌아온 사용자가 아무 조작 없이 바로 저장할 수 있다.
      const hasSession = Boolean(accessToken || refreshToken) || await harvestWebSession();
      setAuthenticated(hasSession);
      if (hasSession) await loadWorkspaces();
    })();
  }, [loadWorkspaces]);

  useEffect(() => {
    void chrome.storage.local.get(CONTEXT_FEEDBACK_KEY).then((result) => {
      const feedback = result[CONTEXT_FEEDBACK_KEY] as ContextSaveFeedback | undefined;
      if (feedback && Date.now() - feedback.createdAt < 60_000) setContextFeedback(feedback);
    });
    const handleStorageChange = (changes: Record<string, chrome.storage.StorageChange>) => {
      const feedback = changes[CONTEXT_FEEDBACK_KEY]?.newValue as ContextSaveFeedback | undefined;
      if (feedback) setContextFeedback(feedback);
    };
    chrome.storage.onChanged.addListener(handleStorageChange);
    return () => chrome.storage.onChanged.removeListener(handleStorageChange);
  }, []);

  useEffect(() => {
    setUrlExpanded(false);
    const frame = requestAnimationFrame(() => {
      const element = urlRef.current;
      setUrlOverflowing(Boolean(element && element.scrollHeight > element.clientHeight + 1));
    });
    return () => cancelAnimationFrame(frame);
  }, [url]);

  async function handleWebLogin() {
    setStatus('idle');
    setMessage('');
    await openWebLogin();
    setLoginOpened(true);
  }

  /** 웹에서 로그인한 뒤 팝업으로 돌아왔을 때 — 새 탭의 세션을 다시 확인한다. */
  async function handleRecheck() {
    setStatus('loading');
    setMessage('');
    if (await harvestWebSession()) {
      setAuthenticated(true);
      await loadWorkspaces();
      return;
    }
    setStatus('error');
    setMessage('아직 로그인이 확인되지 않았어요. 우주인 탭에서 로그인을 마친 뒤 다시 눌러 주세요.');
  }

  async function handleSave() {
    if (!workspaceId) {
      setStatus('error'); setMessage('저장할 워크스페이스를 선택해 주세요.'); return;
    }
    if (!isSavableUrl(url)) {
      setStatus('error'); setMessage('이 페이지 주소는 저장할 수 없습니다.'); return;
    }
    setStatus('saving');
    setMessage('');
    try {
      await saveUrl(workspaceId, url);
      setStatus('success');
      setMessage('AI가 내용을 정리하고 있어요.');
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) setAuthenticated(false);
      setStatus('error');
      setMessage(messageFrom(error));
    }
  }

  async function handleLogout() {
    await Promise.all([logout(), clearSelectedWorkspaceId()]);
    setAuthenticated(false);
    setWorkspaces([]);
    setWorkspaceId(null);
    setLoginOpened(false);
    setStatus('idle');
    setMessage('');
  }

  if (authenticated === null) {
    return <main style={styles.main}><p style={styles.muted}>불러오는 중…</p></main>;
  }

  if (!authenticated) {
    return (
      <main style={styles.main}>
        <h1 style={styles.title}>우주인에 저장</h1>
        <p style={styles.muted}>
          우주인 계정으로 로그인하면 지금 보는 페이지를 바로 담을 수 있어요.
        </p>
        <button onClick={handleWebLogin} style={styles.google}>
          <GoogleMark />
          Google 계정으로 로그인
        </button>
        {/* 구글은 첫 로그인이 곧 가입이다(웹앱 LoginForm 과 같은 고지) */}
        <p style={styles.hint}>
          {loginOpened
            ? '새 탭에서 로그인을 마친 뒤 아래를 눌러 주세요.'
            : '계정이 없어도 구글 로그인으로 바로 시작할 수 있어요.'}
        </p>
        {loginOpened && (
          <button onClick={handleRecheck} disabled={status === 'loading'} style={styles.secondary}>
            {status === 'loading' ? '확인 중…' : '로그인 확인'}
          </button>
        )}
        {message && <p style={styles.error}>{message}</p>}
      </main>
    );
  }

  return (
    <main style={styles.main}>
      <div style={styles.header}>
        <h1 style={styles.title}>우주인에 저장</h1>
        <button onClick={handleLogout} style={styles.link}>로그아웃</button>
      </div>
      <label style={styles.label}>워크스페이스
        <select value={workspaceId ?? ''} disabled={status === 'loading' || !workspaces.length}
          onChange={(e) => {
            const id = Number(e.target.value);
            setWorkspaceId(id);
            void setSelectedWorkspaceId(id);
          }} style={styles.input}>
          {workspaces.map((workspace) =>
            <option key={workspace.id} value={workspace.id}>{workspace.name}</option>)}
        </select>
      </label>
      <div style={styles.urlBox}>
        <p ref={urlRef} style={urlExpanded ? styles.url : styles.urlCollapsed}>
          {url || '현재 탭의 주소를 읽지 못했습니다.'}
        </p>
        {urlOverflowing && (
          <button onClick={() => setUrlExpanded((expanded) => !expanded)} style={styles.more}>
            {urlExpanded ? '접기' : '더보기'}
          </button>
        )}
      </div>
      <button onClick={handleSave}
        disabled={status === 'saving' || status === 'loading' || status === 'success' || !workspaceId}
        style={status === 'success' ? styles.completed : styles.primary}>
        {status === 'saving' ? '보내는 중…' : status === 'success' ? '우주인으로 보냈어요' : '현재 페이지 저장'}
      </button>
      {!workspaces.length && status !== 'loading' &&
        <p style={styles.error}>사용 가능한 워크스페이스가 없습니다.</p>}
      {message && <p style={status === 'success' ? styles.pageSuccess : styles.error}>{message}</p>}
      {contextFeedback && (
        <p style={contextFeedback.success ? styles.success : styles.error}>
          우클릭 저장: {contextFeedback.message}
        </p>
      )}
    </main>
  );
}

// 색은 웹앱 테마 토큰(frontend/src/styles/theme.css 다크 값)을 그대로 옮긴 것이다 —
// 확장은 Tailwind 를 쓰지 않으므로 변수 대신 값으로 박는다.
const SPACE = '#0e1017';
const SURFACE = '#20242f';
const SURFACE_2 = '#2a2e3a';
const SURFACE_3 = '#343947';
const BORDER = '#313543';
const TEXT_1 = '#f0f2f6';
const TEXT_2 = '#b0b6c3';
const TEXT_3 = '#7b8290';
const ACCENT = '#7c6cf0';
const DANGER = '#ef7a72';
const SUCCESS = '#b8e6a3';

const buttonBase: React.CSSProperties = {
  boxSizing: 'border-box',
  width: '100%',
  height: 40,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  gap: 12,
  border: 0,
  borderRadius: 6,
  fontSize: 13,
  fontWeight: 600,
  cursor: 'pointer',
};

const styles: Record<string, React.CSSProperties> = {
  main: {
    width: 320, padding: 18, background: SPACE, color: TEXT_1,
    fontFamily: 'system-ui, sans-serif',
    display: 'flex', flexDirection: 'column', gap: 10,
  },
  header: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' },
  title: { margin: 0, fontSize: 16, fontWeight: 700 },
  muted: { margin: 0, fontSize: 12, lineHeight: 1.6, color: TEXT_2 },
  hint: { margin: 0, fontSize: 11, lineHeight: 1.6, color: TEXT_3, textAlign: 'center' },
  label: { display: 'grid', gap: 6, fontSize: 12, fontWeight: 600, color: TEXT_2 },
  input: {
    boxSizing: 'border-box', width: '100%', height: 36, padding: '0 10px',
    border: `1px solid ${BORDER}`, borderRadius: 6,
    background: SURFACE_2, color: TEXT_1, fontSize: 13,
  },
  // 웹앱 GoogleAuthButton 과 같은 껍데기 — 흰 버튼은 다크 테마에서 혼자 튄다
  google: { ...buttonBase, border: `1px solid ${BORDER}`, background: SURFACE_2, color: TEXT_1 },
  secondary: { ...buttonBase, border: `1px solid ${BORDER}`, background: SURFACE_3, color: TEXT_1 },
  primary: { ...buttonBase, background: ACCENT, color: '#ffffff', fontWeight: 700 },
  completed: { ...buttonBase, background: '#2f6b4f', color: '#ffffff', fontWeight: 700 },
  link: { border: 0, background: 'transparent', color: TEXT_3, fontSize: 12, cursor: 'pointer' },
  urlBox: { padding: 10, borderRadius: 6, background: SURFACE, border: `1px solid ${BORDER}` },
  url: { margin: 0, fontSize: 12, lineHeight: 1.5, color: TEXT_2, wordBreak: 'break-all' },
  urlCollapsed: {
    margin: 0, fontSize: 12, lineHeight: 1.5, color: TEXT_2, wordBreak: 'break-all',
    display: '-webkit-box', WebkitBoxOrient: 'vertical', WebkitLineClamp: 2, overflow: 'hidden',
  },
  more: {
    display: 'block', margin: '8px 0 0 auto', padding: 0, border: 0,
    background: 'transparent', color: ACCENT, fontSize: 12, fontWeight: 700, cursor: 'pointer',
  },
  error: { margin: 0, color: DANGER, fontSize: 12, lineHeight: 1.5 },
  success: { margin: 0, color: SUCCESS, fontSize: 12 },
  pageSuccess: { margin: 0, color: SUCCESS, fontSize: 12, textAlign: 'center' },
};
