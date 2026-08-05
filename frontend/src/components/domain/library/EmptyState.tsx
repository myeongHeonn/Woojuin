import spacemanNoBg from '@/assets/spacemans/spaceman_no_bg.webp';

interface EmptyStateProps {
  /** 필터(즐겨찾기·카테고리)가 걸린 상태에서 결과가 없음 — 문구가 달라진다 */
  filtered?: boolean;
}

/**
 * 보관함이 비었을 때 보여주는 안내 — 두 경우를 구분한다.
 *   filtered=false : 아직 아무것도 저장 안 함 → 첫 저장을 유도
 *   filtered=true  : 필터에 걸리는 게 없음 → 필터를 바꾸도록 유도
 * (같은 "0개"라도 원인이 다르므로 문구를 나눠 사용자가 헷갈리지 않게 한다)
 */
const EmptyState = ({ filtered = false }: EmptyStateProps) => {
  const title = filtered ? '조건에 맞는 항목이 없어요' : '아직 저장한 게 없어요';
  const desc = filtered
    ? '필터를 바꾸거나 즐겨찾기를 꺼 보세요.'
    : '링크·사진·메모를 저장하면 우주인이 알아서 분류해 드려요.';

  return (
    <div className="h-full flex flex-col items-center justify-center gap-5 py-24 text-center">
      <img
        src={spacemanNoBg}
        alt=""
        aria-hidden
        draggable={false}
        className="h-28 w-28 select-none opacity-90"
      />
      <div className="space-y-1.5">
        <p className="text-base font-semibold text-text-1">{title}</p>
        <p className="text-sm text-text-3">{desc}</p>
      </div>
    </div>
  );
};

export default EmptyState;
