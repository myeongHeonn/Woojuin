import { classNames } from '@/utils/classNames';

interface AiModeHintProps {
  /** AI 모드 on/off — 이미 켜져 있으면 권할 게 없어 안 그린다 */
  aiMode: boolean;
  /** 배치용(정렬/여백) — 여백은 부모가 정한다(SearchMeta 와 같은 규칙) */
  className?: string;
}

/**
 * AI 모드 권유 문구 — 검색창 우측 상단에 붙는 한 줄. 성좌·대시보드가 공유한다.
 * 스파크 버튼이 무슨 버튼인지 몰라 지나치는 사용자에게 AI 검색의 존재를 알린다.
 * aiMode 가 켜지면 사라진다(켠 사람에게 켜라고 하지 않는다) — 여백을 박지 않는 건
 * 성좌·대시보드의 배치가 달라서다(SearchMeta 주석 참고).
 */
const AiModeHint = ({ aiMode, className }: AiModeHintProps) => {
  if (aiMode) return null;

  return (
    <p className={classNames('text-[11px] text-text-3', className)}>
      AI 버튼을 눌러 AI 모드로 바꿔보세요
    </p>
  );
};

export default AiModeHint;
