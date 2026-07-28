import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * 가로 스크롤 컨테이너의 동작만 담는 훅 — "끝에 닿았나"(atStart/atEnd)와 스크롤 이동.
 * 좌우 화살표 버튼의 비활성 판단에 쓴다. UI 는 모르고 상태·동작만 돌려준다(동작/표현 분리).
 * resyncKey 가 바뀌면(예: 항목 수 변화) 넘침 여부를 다시 계산한다.
 */
export function useHorizontalScroll(resyncKey?: unknown) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [atStart, setAtStart] = useState(true);
  const [atEnd, setAtEnd] = useState(true);

  const sync = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    setAtStart(el.scrollLeft <= 0);
    // 소수점 오차로 끝에서 1px 남는 경우가 있어 여유를 둔다
    setAtEnd(el.scrollLeft + el.clientWidth >= el.scrollWidth - 1);
  }, []);

  useEffect(() => {
    sync();
  }, [sync, resyncKey]);

  const scrollBy = (amount: number) =>
    scrollRef.current?.scrollBy({ left: amount, behavior: 'smooth' });

  return { scrollRef, atStart, atEnd, onScroll: sync, scrollBy };
}
