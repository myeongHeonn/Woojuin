import { useEffect, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { SearchIcon } from '@/assets/icons';
import SearchModeToggle from '@/components/domain/search/SearchModeToggle';
import AiModeHint from '@/components/domain/search/AiModeHint';

/**
 * 대시보드 검색창 — 카테고리 바 아래 작은 입력. 검색 상태를 URL(?q &ai)로 옮긴다.
 * 검색 후에도 입력창을 그대로 두어(‹검색어 헤더로 바꾸지 않음) 바로 이어서 검색할 수 있고,
 * 입력을 다 지우면 ?q 를 떼어 전체 목록을 다시 부른다.
 * (성좌 검색과 로직은 같지만 UI·동작이 다르다 — 이건 URL 이동, 성좌는 제자리)
 */
const LibrarySearch = () => {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const aiMode = params.get('ai') === '1';

  const [text, setText] = useState(q);
  const [localAi, setLocalAi] = useState(aiMode);

  // 외부에서 URL 이 바뀌면(뒤로가기·링크 등) 입력·토글을 맞춘다
  useEffect(() => setText(q), [q]);
  useEffect(() => setLocalAi(aiMode), [aiMode]);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const query = text.trim();
    if (!query) {
      setParams({}, { replace: true });
      return;
    }
    setParams(localAi ? { q: query, ai: '1' } : { q: query });
  };

  const onChange = (value: string) => {
    setText(value);
    // 다 지우면 검색을 풀고 전체를 다시 불러온다
    if (value.trim() === '' && q) setParams({}, { replace: true });
  };

  return (
    // 우측 정렬·폭 제한은 래퍼가 갖는다 — 안내 문구가 검색창과 같은 폭 기준으로 오른쪽에 맞아야 하고,
    // ml-auto 는 부모(LibraryPage 의 flex-col)의 flex auto margin 으로 동작한다
    <div className="mb-2 ml-auto max-w-100">
      <AiModeHint aiMode={localAi} className="mb-1 pr-1 text-right" />

      <form
        onSubmit={submit}
        className="flex items-center gap-2 rounded-lg border border-border bg-surface-2 px-3 py-2"
      >
        <SearchIcon className="h-4 w-4 shrink-0 text-text-3" />
        <input
          value={text}
          onChange={(e) => onChange(e.target.value)}
          placeholder="검색"
          aria-label="검색어"
          className="min-w-0 flex-1 bg-transparent text-[13px] text-text-1 outline-none placeholder:text-text-3"
        />
        <SearchModeToggle aiMode={localAi} onToggle={() => setLocalAi((v) => !v)} />
      </form>
    </div>
  );
};

export default LibrarySearch;
