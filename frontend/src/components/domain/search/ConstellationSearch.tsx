import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { useParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useSearch, searchKey } from '@/hooks/useSearch';
import { SearchIcon } from '@/assets/icons';
import SearchModeToggle from './SearchModeToggle';
import SearchMeta from './SearchMeta';
import AiModeHint from './AiModeHint';
import ItemCard from '@/components/domain/library/ItemCard';
import ItemModal from '@/components/domain/library/detail/ItemModal';
import CloseButton from '@/components/ui/button/CloseButton';
import Spinner from '@/components/ui/Spinner';
import AddBtn from '@/components/domain/header/AddBtn';

interface ConstellationSearchProps {
  /** 검색 결과 아이템 ID 목록 변경 알림 */
  onSearchResults?: (itemIds: number[]) => void;
  /**
   * 하단 플로팅 스택의 맨 위(결과 패널보다도 위)에 얹을 내용(예: 처리 중 배지) — 이
   * 컴포넌트는 "무엇을 얹는지" 모른다. 검색과 무관한 상태를 이 컴포넌트가 알 필요는
   * 없고, 하단 플로팅 스택 배치만 재사용한다.
   */
  aboveBar?: ReactNode;
}

/**
 * 성좌 검색 — 화면 하단 중앙 플로팅 바(v3.5). 검색은 제자리(URL 안 바꿈)로 바 위에 결과 패널이 뜬다.
 * 패널 X → 결과 초기화 + 진행 중 요청 취소(cancelQueries). AI 모드면 해석어를 SearchMeta 로 알린다.
 */
const ConstellationSearch = ({ onSearchResults, aboveBar }: ConstellationSearchProps) => {
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

  const itemIdsStr = items.map((item) => item.itemId).join(',');

  useEffect(() => {
    const parsedIds = itemIdsStr ? itemIdsStr.split(',').map(Number) : [];
    if (q.trim().length > 0 && parsedIds.length > 0) {
      onSearchResults?.(parsedIds);
    } else {
      onSearchResults?.([]);
    }
  }, [itemIdsStr, q, onSearchResults]);

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
      {/*
        가로 중앙 정렬을 transform(-translate-x-1/2) 이 아니라 mx-auto 로 한다 —
        transform 이 걸린 조상 안에서는 자식의 position:fixed 가 뷰포트가 아니라 이 상자
        기준으로 잡혀, AddModal(모바일에서 fixed 로 화면 중앙) 이 엉뚱한 곳에 떴다.
      */}
      <div className="absolute inset-x-0 bottom-above-tabbar z-30 mx-auto w-[min(520px,90%)] desktop:bottom-10">
        {aboveBar && <div className="mb-2.5 flex justify-center">{aboveBar}</div>}

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
                  // 결과는 가로로 늘어선다 — 좌우 버튼 대신 하단에 얇은 스크롤바로 넘긴다.
                  <div
                    role="group"
                    aria-label="검색 결과"
                    className="mt-2 flex gap-2 overflow-x-auto pb-2 [scrollbar-width:thin] [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-border [&::-webkit-scrollbar]:h-1.5"
                  >
                    {items.map((item) => (
                      <ItemCard key={item.itemId} item={item} onClick={setOpenItemId} />
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {/* 입력 바 바로 위 우측. 바가 하단 고정이라 검색해도 바는 안 움직이고 패널이 위로 쌓인다 */}
        <AiModeHint className="mb-1.5 pr-1.5 text-right" />

        {/* 모바일 전용 추가 버튼 + 검색 바. 헤더의 + 는 상단이라 엄지로 닿기 멀어
            하단 바 왼쪽에도 둔다(데스크톱은 헤더에만 있어 숨긴다) */}
        <div className="flex items-center gap-2.5">
          <AddBtn tutorial={false} className="shrink-0 desktop:hidden" />
          <form
            data-tutorial="search"
            onSubmit={submit}
            className="flex min-w-0 flex-1 items-center gap-2.5 rounded-2xl border border-border bg-surface/90 px-4 py-3 shadow-float backdrop-blur-xl"
          >
            <SearchIcon className="h-[18px] w-[18px] shrink-0 text-text-3" />
            <input
              value={text}
              onChange={(e) => onChange(e.target.value)}
              placeholder="무엇이든 검색"
              aria-label="검색어"
              // pointer-coarse:text-base — 터치 기기에서 16px 미만이면 iOS 가 포커스 시
              // 화면을 확대하고 키보드가 닫혀도 배율을 되돌리지 않는다(fieldStyles 주석 참고)
              className="min-w-0 flex-1 bg-transparent text-[15px] text-text-1 outline-none pointer-coarse:text-base placeholder:text-text-3"
            />
            <SearchModeToggle aiMode={localAi} onToggle={() => setLocalAi((v) => !v)} />
          </form>
        </div>
      </div>

      <ItemModal workspaceId={wsId} itemId={openItemId} onClose={() => setOpenItemId(null)} />
    </>
  );
};

export default ConstellationSearch;
