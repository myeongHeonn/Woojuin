import { TYPE_LABEL, type Item } from '@/types/item';
import ItemSquare from './ItemSquare';

interface ItemCardProps {
  item: Item;
  onClick?: () => void;
}

/**
 * 보관함 아이템 카드 — 목업 dashboard.html 의 `.fitem`.
 *
 * 카드 레이아웃(88px 박스·제목·타입 라벨)과 클릭만 책임진다. 정사각 안에
 * 무엇을 그릴지는 ItemSquare 가, 클릭 후 무엇을 열지는 부모(Items)가 정한다.
 *
 * PROCESSING 이면 제목 자리에 "분석 중…". PARTIAL·FAILED 는 DONE 과 똑같이 그린다.
 * 분석 중(PROCESSING)엔 아직 볼 상세가 없으므로 카드를 비활성화 — 클릭해도 모달이 안 뜬다.
 */
const ItemCard = ({ item, onClick }: ItemCardProps) => {
  const processing = item.status === 'PROCESSING';

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={processing}
      className="group w-[124px] rounded-[14px] px-1 py-2.5 text-center hover:bg-white/[0.03] disabled:cursor-default disabled:hover:bg-transparent"
    >
      <div className="relative mx-auto h-[88px] w-[88px] overflow-hidden rounded-2xl border border-white/[0.07] bg-gradient-to-b from-surface-2 to-surface transition-transform group-hover:-translate-y-[3px]">
        <ItemSquare item={item} />
      </div>

      <p className="mt-[7px] truncate text-[13px] font-semibold text-text-1">
        {processing ? (
          <span className="font-medium text-text-3">분석 중…</span>
        ) : (
          (item.title ?? '제목 없음')
        )}
      </p>
      <p className="mt-px text-[11.5px] text-text-3">{TYPE_LABEL[item.type]}</p>
    </button>
  );
};

export default ItemCard;
