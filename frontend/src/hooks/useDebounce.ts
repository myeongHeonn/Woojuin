import { useEffect, useState } from 'react';

/** 값이 delayMs 동안 안 바뀌어야 반영되는 지연 버전을 돌려준다 (연속 입력 중 API 호출 방지용). */
export function useDebounce<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
