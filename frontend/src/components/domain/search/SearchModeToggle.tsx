import { classNames } from '@/utils/classNames';
import { SparkIcon } from '@/assets/icons';

interface SearchModeToggleProps {
  /** AI 모드 on/off — 상태는 부모가 소유(controlled) */
  aiMode: boolean;
  onToggle: () => void;
  className?: string;
}

/**
 * 일반 ↔ AI 검색 토글 — 성좌·대시보드가 공유하는 스파크 버튼.
 * on 이면 accent 로 강조. 무엇을 검색하는지는 모르고 켜짐/꺼짐만 알린다.
 */
const SearchModeToggle = ({ aiMode, onToggle, className }: SearchModeToggleProps) => (
  <button
    type="button"
    aria-pressed={aiMode}
    aria-label="AI 모드"
    onClick={onToggle}
    className={classNames(
      'inline-flex shrink-0 items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-semibold transition-colors [&>svg]:h-3.5 [&>svg]:w-3.5',
      aiMode
        ? 'border-accent bg-accent/15 text-accent'
        : // 꺼진 상태도 읽을 수 있어야 한다 — text-3 은 검색 바 배경(surface) 위에서
          // 대비가 4.01:1 로 AA(4.5:1) 에 못 미쳤다(라이트 모드는 3.21:1 로 더 낮다).
          // text-2 는 다크 7.62:1 · 라이트 6.93:1 로 양쪽 다 통과한다.
          'border-border text-text-2 hover:text-text-1',
      className,
    )}
  >
    <SparkIcon />
    AI
  </button>
);

export default SearchModeToggle;
