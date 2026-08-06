import { memo } from 'react';
import { TYPE_LABEL, type Item } from '@/types/item';
import { StarIcon } from '@/assets/icons';
import ItemSquare from './ItemSquare';

interface ItemCardProps {
  item: Item;
  /**
   * 누른 아이템 id 를 받는다 — 호출부가 `() => open(item.itemId)` 로 감싸지 않아도 되게.
   * 감싸면 매 렌더 새 함수가 되어 아래 memo 가 무력화된다(목록 전체가 다시 그려진다).
   */
  onClick?: (itemId: number) => void;
}

/**
 * 보관함 아이템 카드 — 목업 dashboard.html 의 `.fitem`.
 *
 * 카드 레이아웃(88px 박스·제목·타입 라벨)과 클릭만 책임진다. 정사각 안에
 * 무엇을 그릴지는 ItemSquare 가, 클릭 후 무엇을 열지는 부모(Items)가 정한다.
 *
 * PROCESSING 이면 제목 자리에 "분석 중…". PARTIAL·FAILED 는 DONE 과 똑같이 그린다.
 * 분석 중(PROCESSING)엔 아직 볼 상세가 없으므로 카드를 비활성화 — 클릭해도 모달이 안 뜬다.
 *
 * **memo 로 감싼다.** 저장 항목이 처리 중이면 목록이 3초마다 다시 조회되는데,
 * react-query 의 structural sharing 덕에 값이 안 바뀐 아이템은 같은 객체 참조를 유지한다.
 * 그래서 memo 가 실제로 걸린다 — 40장 중 1건만 바뀐 상황에서 리렌더 40회 → 1회
 * (2026-08-05 실측, 4.0ms → 1.1ms).
 */
const ItemCardView = ({ item, onClick }: ItemCardProps) => {
  const processing = item.status === 'PROCESSING';

  return (
    <button
      type="button"
      onClick={() => onClick?.(item.itemId)}
      disabled={processing}
      className="group w-[124px] rounded-[14px] px-1 py-2.5 text-center hover:bg-surface-2/50 disabled:cursor-default disabled:hover:bg-transparent"
    >
      <div className="relative mx-auto h-[88px] w-[88px] overflow-hidden rounded-2xl border border-border-soft bg-gradient-to-b from-surface-2 to-surface transition-transform group-hover:-translate-y-[3px]">
        <ItemSquare item={item} />
      </div>

      <div className="mt-[7px] flex items-center justify-center gap-1">
        {/* 즐겨찾기면 제목 옆에 노란 별 — 상세 모달과 같은 표식으로 한눈에 구분된다 */}
        {item.favorite && <StarIcon className="h-3 w-3 shrink-0 fill-current text-star-yellow" />}
        <p className="min-w-0 truncate text-[13px] font-semibold text-text-1">
          {processing ? (
            <span className="font-medium text-text-3">분석 중…</span>
          ) : (
            (item.title ?? '제목 없음')
          )}
        </p>
      </div>
      <p className="mt-px text-[11.5px] text-text-3">{TYPE_LABEL[item.type]}</p>
    </button>
  );
};

const ItemCard = memo(ItemCardView);
ItemCard.displayName = 'ItemCard';

export default ItemCard;
