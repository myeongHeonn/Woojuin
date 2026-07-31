import { useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useSearch, searchKey } from '@/hooks/useSearch';
import { useHorizontalScroll } from '@/hooks/useHorizontalScroll';
import { ChevronLeftIcon, ChevronRightIcon, SearchIcon } from '@/assets/icons';
import SearchModeToggle from './SearchModeToggle';
import SearchMeta from './SearchMeta';
import AiModeHint from './AiModeHint';
import ItemCard from '@/components/domain/library/ItemCard';
import ItemModal from '@/components/domain/library/detail/ItemModal';
import CloseButton from '@/components/ui/button/CloseButton';
import ScrollButton from '@/components/ui/button/ScrollButton';
import Spinner from '@/components/ui/Spinner';

/**
 * 성좌 검색 — 화면 하단 중앙 플로팅 바(v3.5). 검색은 제자리(URL 안 바꿈)로 바 위에 결과 패널이 뜬다.
 * 패널 X → 결과 초기화 + 진행 중 요청 취소(cancelQueries). AI 모드면 해석어를 SearchMeta 로 알린다.
 */
const ConstellationSearch = () => {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const wsId = Number(workspaceId);
  const qc = useQueryClient();

  const [text, setText] = useState('');
  const [q, setQ] = useState('');
  // aiMode 는 "제출로 확정된" 값 — useSearch(실제 요청)는 이것만 본다.
  // localAi 는 토글 버튼이 지금 켜져 있는지(다음 제출에 반영될 값)만 나타낸다.
  // 이 둘을 분리하지 않으면 AI 버튼을 누르는 순간 aiMode 가 바뀌어 queryKey 가 바뀌고,
  // q 는 이미 채워져 있어 제출(Enter) 없이 즉시 재검색이 나간다.
  const [aiMode, setAiMode] = useState(false);
  const [localAi, setLocalAi] = useState(false);
  const [openItemId, setOpenItemId] = useState<number | null>(null);

  const { items, isLoading, partialMatch, interpretedQuery, aiPlanned } = useSearch(
    wsId,
    q,
    aiMode,
  );

  // 결과 개수가 바뀌면 넘침 여부를 다시 계산해야 화살표 활성 상태가 맞는다.
  const { scrollRef, atStart, atEnd, onScroll, scrollBy } = useHorizontalScroll(items.length);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setQ(text.trim());
    setAiMode(localAi);
  };

  // X — 결과 패널을 닫고(q 비움 → enabled:false 로 정지) 진행 중 요청도 취소한다
  const reset = () => {
    qc.cancelQueries({ queryKey: searchKey(wsId, aiMode, q.trim()) });
    setQ('');
    setText('');
  };

  const showPanel = q.trim().length > 0;

  const onChange = (value: string) => {
    setText(value);
    //다 지우면 검색 모달을 닫는다.
    if (value.trim() === '' && q) {
      reset();
    }
  };

  return (
    <>
      <div className="absolute bottom-[calc(88px+env(safe-area-inset-bottom))] left-1/2 z-30 w-[min(520px,90%)] -translate-x-1/2 desktop:bottom-10">
        {showPanel && (
          <div className="relative mb-2.5 rounded-2xl border border-border bg-surface/95 p-3 shadow-float backdrop-blur-xl">
            <CloseButton onClick={reset} className="absolute right-2.5 top-2.5 z-10" />

            {isLoading ? (
              <div className="grid place-items-center gap-2 py-7 text-xs text-text-3">
                <Spinner className="h-6 w-6" />
                검색 중…
              </div>
            ) : (
              <div className="pr-8">
                <SearchMeta
                  partialMatch={partialMatch}
                  interpretedQuery={interpretedQuery}
                  aiPlanned={aiPlanned}
                />
                {items.length === 0 ? (
                  <p className="py-6 text-center text-sm text-text-3">검색 결과가 없어요</p>
                ) : (
                  // 결과는 가로로 늘어서므로 좌우 이동 버튼이 필요하다 — 스크롤바를 숨겨서
                  // (scrollbar-none) 마우스만 쓰는 데스크톱에는 넘어갈 방법이 이것뿐이다.
                  <div className="mt-2 flex items-center gap-1">
                    <ScrollButton
                      icon={ChevronLeftIcon}
                      label="이전 검색 결과"
                      disabled={atStart}
                      onClick={() => scrollBy(-220)}
                    />

                    <div
                      ref={scrollRef}
                      onScroll={onScroll}
                      role="group"
                      aria-label="검색 결과"
                      className="scrollbar-none flex min-w-0 flex-1 gap-2 overflow-x-auto"
                    >
                      {items.map((item) => (
                        <ItemCard
                          key={item.itemId}
                          item={item}
                          onClick={() => setOpenItemId(item.itemId)}
                        />
                      ))}
                    </div>

                    <ScrollButton
                      icon={ChevronRightIcon}
                      label="다음 검색 결과"
                      disabled={atEnd}
                      onClick={() => scrollBy(220)}
                    />
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {/* 입력 바 바로 위 우측. 바가 하단 고정이라 검색해도 바는 안 움직이고 패널이 위로 쌓인다 */}
        <AiModeHint className="mb-1.5 pr-1.5 text-right" />

        <form
          data-tutorial="search"
          onSubmit={submit}
          className="flex items-center gap-2.5 rounded-2xl border border-border bg-surface/90 px-4 py-3 shadow-float backdrop-blur-xl"
        >
          <SearchIcon className="h-[18px] w-[18px] shrink-0 text-text-3" />
          <input
            value={text}
            onChange={(e) => onChange(e.target.value)}
            placeholder="무엇이든 검색"
            aria-label="검색어"
            className="min-w-0 flex-1 bg-transparent text-[15px] text-text-1 outline-none placeholder:text-text-3"
          />
          <SearchModeToggle aiMode={localAi} onToggle={() => setLocalAi((v) => !v)} />
        </form>
      </div>

      <ItemModal workspaceId={wsId} itemId={openItemId} onClose={() => setOpenItemId(null)} />
    </>
  );
};

export default ConstellationSearch;
