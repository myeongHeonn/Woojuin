import { classNames } from '@/utils/classNames';

interface AiModeHintProps {
  /** 배치용(정렬/여백) — 여백은 부모가 정한다(SearchMeta 와 같은 규칙) */
  className?: string;
}

/**
 * AI 모드 권유 문구 — 검색창 우측 상단에 붙는 한 줄. 성좌·대시보드가 공유한다.
 * 스파크 버튼이 무슨 버튼인지 몰라 지나치는 사용자에게 AI 검색의 존재를 알린다.
 * AI 모드를 켜도 사라지지 않는다 — 자리가 없어지면 검색창이 위로 움직여 거슬린다.
 */
const AiModeHint = ({ className }: AiModeHintProps) => (
  <p className={classNames('text-[11px] text-text-3', className)}>
    AI 버튼을 눌러 AI 모드로 바꿔보세요
  </p>
);

export default AiModeHint;
