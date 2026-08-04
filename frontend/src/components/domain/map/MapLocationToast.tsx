import { MapIcon } from '@/assets/icons';

interface MapLocationToastProps {
  /** 표시할 문구. null 이면 렌더하지 않는다 — 사라짐도 부모가 타이머로 제어한다 */
  message: string | null;
}

/**
 * "위치 정보가 없어서 등록되지 않았어요" 토스트 — ProcessingBadge 와 같은 자리에 뜬다.
 * 지도 뷰에서 처리 완료된 아이템이 좌표가 없어 핀으로 안 뜰 때, 왜 안 뜨는지 알려준다.
 * 표시/숨김 타이밍은 부모(MapPage)가 소유한다 — 이 컴포넌트는 문구만 그린다.
 */
const MapLocationToast = ({ message }: MapLocationToastProps) => {
  if (!message) return null;

  return (
    <div className="inline-flex items-center gap-2 rounded-pill border border-border bg-surface/95 px-3.5 py-[7px] text-xs text-text-1 shadow-float backdrop-blur-xl">
      <MapIcon className="h-[13px] w-[13px] shrink-0 text-text-3" />
      {message}
    </div>
  );
};

export default MapLocationToast;
