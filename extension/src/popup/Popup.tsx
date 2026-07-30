import { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import { login, logout } from '@/api/auth';
import { ApiError } from '@/api/client';
import { saveUrl } from '@/api/items';
import { getWorkspaces, Workspace } from '@/api/workspaces';
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
    ? '로그인이 만료되었습니다. 다시 로그인해 주세요.'
    : error instanceof Error ? error.message : '요청을 처리하지 못했습니다.';

function isSavableUrl(value: string): boolean {
  try { return ['http:', 'https:'].includes(new URL(value).protocol); } catch { return false; }
}

export default function Popup() {
  const [url, setUrl] = useState('');
  const [authenticated, setAuthenticated] = useState<boolean | null>(null);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [workspaceId, setWorkspaceId] = useState<number | null>(null);
  const [status, setStatus] = useState<Status>('idle');
  const [message, setMessage] = useState('');
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
    Promise.all([
      chrome.tabs.query({ active: true, currentWindow: true }),
      getAccessToken(),
      getRefreshToken(),
    ]).then(async ([[tab], accessToken, refreshToken]) => {
        setUrl(tab?.url ?? '');
        setAuthenticated(Boolean(accessToken || refreshToken));
        if (accessToken || refreshToken) await loadWorkspaces();
      });
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

  async function handleLogin(event: FormEvent) {
    event.preventDefault();
    setStatus('loading');
    setMessage('');
    try {
      await login(email.trim(), password);
      setAuthenticated(true);
      await loadWorkspaces();
    } catch (error) {
      setStatus('error');
      setMessage(messageFrom(error));
    }
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
    setStatus('idle');
    setMessage('');
  }

  if (authenticated === null) return <main style={styles.main}>불러오는 중…</main>;
  if (!authenticated) {
    return (
      <main style={styles.main}>
        <h1 style={styles.title}>우주인 로그인</h1>
        <form onSubmit={handleLogin} style={styles.form}>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
            placeholder="이메일" required style={styles.input} />
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
            placeholder="비밀번호" required style={styles.input} />
          <button disabled={status === 'loading'} style={styles.primary}>
            {status === 'loading' ? '로그인 중…' : '로그인'}
          </button>
        </form>
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

const styles: Record<string, React.CSSProperties> = {
  main: { width: 320, padding: 18, fontFamily: 'system-ui, sans-serif', color: '#18212f' },
  header: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' },
  title: { margin: '0 0 14px', fontSize: 18 },
  form: { display: 'grid', gap: 10 },
  label: { display: 'grid', gap: 6, fontSize: 12, fontWeight: 600 },
  input: { boxSizing: 'border-box', width: '100%', padding: '9px 10px', border: '1px solid #cad2df', borderRadius: 8 },
  primary: { width: '100%', padding: '10px 12px', border: 0, borderRadius: 8, background: '#5b55e7', color: 'white', fontWeight: 700, cursor: 'pointer' },
  link: { border: 0, background: 'transparent', color: '#5b55e7', cursor: 'pointer' },
  urlBox: { margin: '14px 0', padding: 10, borderRadius: 8, background: '#f4f6fa' },
  url: { margin: 0, fontSize: 12, lineHeight: 1.5, wordBreak: 'break-all' },
  urlCollapsed: { margin: 0, fontSize: 12, lineHeight: 1.5, wordBreak: 'break-all', display: '-webkit-box', WebkitBoxOrient: 'vertical', WebkitLineClamp: 2, overflow: 'hidden' },
  more: { display: 'block', margin: '8px 0 0 auto', padding: 0, border: 0, background: 'transparent', color: '#5b55e7', fontSize: 12, fontWeight: 700, cursor: 'pointer' },
  completed: { width: '100%', padding: '10px 12px', border: 0, borderRadius: 8, background: '#16784b', color: 'white', fontWeight: 700 },
  error: { marginBottom: 0, color: '#c33030', fontSize: 12 },
  success: { marginBottom: 0, color: '#16784b', fontSize: 12 },
  pageSuccess: { marginBottom: 0, color: '#16784b', fontSize: 12, textAlign: 'center' as const },
};
