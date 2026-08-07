import { classNames } from '@/utils/classNames';

interface AiModeHintProps {
  /**
   * 배치(정렬·여백)와 **글자색**을 부모가 정한다.
   *
   * 색까지 넘기는 이유: 이 문구가 놓이는 바닥이 화면마다 다르다. 대시보드에서는 표면 위라
   * text-3 이 맞지만, 성좌뷰에서는 하늘 위이고 그것도 지평선 쪽이라 낮에는 밝고 밤에는
   * 어둡다 — 한 색으로 둘 다 만족시킬 수 없다(index.css 의 .woojuin-on-sky-sub 참고).
   */
  className?: string;
}

/**
 * AI 모드 권유 문구 — 검색창 우측 상단에 붙는 한 줄. 성좌·대시보드가 공유한다.
 * 스파크 버튼이 무슨 버튼인지 몰라 지나치는 사용자에게 AI 검색의 존재를 알린다.
 * AI 모드를 켜도 사라지지 않는다 — 자리가 없어지면 검색창이 위로 움직여 거슬린다.
 */
const AiModeHint = ({ className }: AiModeHintProps) => (
  <p className={classNames('text-[11px]', className)}>AI 버튼을 눌러 AI 모드로 바꿔보세요</p>
);

export default AiModeHint;
