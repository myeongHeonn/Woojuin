import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { SearchIcon, SparkIcon, ChevronLeftIcon } from '@/assets/icons';
import SearchModeToggle from '@/components/domain/search/SearchModeToggle';

/**
 * 대시보드 검색창 — 카테고리 바 아래 작은 입력. 검색 상태를 URL(?q &ai)로 옮긴다.
 *   입력 모드   : 돋보기 + 입력 + AI 토글 → 제출하면 ?q 로 이동(검색 결과 표시)
 *   검색 모드(q): ‹ 검색어 → ‹ 누르면 ?q 를 떼고 대시보드로 복귀
 * (성좌 검색과 로직은 같지만 UI·동작이 다르다 — 이건 URL 이동, 성좌는 제자리)
 */
const LibrarySearch = () => {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const aiMode = params.get('ai') === '1';

  const [text, setText] = useState('');
  const [localAi, setLocalAi] = useState(false);

  if (q) {
    return (
      <div className="flex items-center gap-2">
        <button
          type="button"
          aria-label="검색 닫기"
          onClick={() => {
            // 뒤로 가면 검색어·AI 모드를 입력창에 채워 바로 이어서 수정할 수 있게 한다
            setText('');
            setLocalAi(aiMode);
            setParams({}, { replace: true });
          }}
          className="grid h-8 w-8 shrink-0 place-items-center rounded-lg text-text-2 transition-colors hover:text-text-1 [&>svg]:h-5 [&>svg]:w-5"
        >
          <ChevronLeftIcon />
        </button>
        <span className="truncate text-[15px] font-semibold text-text-1">{q}</span>
        {aiMode && <SparkIcon className="h-4 w-4 shrink-0 text-accent" />}
      </div>
    );
  }

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const query = text.trim();
    if (!query) return;
    setParams(localAi ? { q: query, ai: '1' } : { q: query });
  };

  return (
    <form
      onSubmit={submit}
      className="mb-2 ml-auto flex items-center gap-2 rounded-lg border border-border bg-surface-2 px-3 py-2 max-w-100"
    >
      <SearchIcon className="h-4 w-4 shrink-0 text-text-3" />
      <input
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder="검색"
        aria-label="검색어"
        className="min-w-0 flex-1 bg-transparent text-[13px] text-text-1 outline-none placeholder:text-text-3"
      />
      <SearchModeToggle aiMode={localAi} onToggle={() => setLocalAi((v) => !v)} />
    </form>
  );
};

export default LibrarySearch;
