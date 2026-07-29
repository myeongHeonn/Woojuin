import { useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { classNames } from '@/utils/classNames';
import { STAGE_PX } from '@/constants/stage';
import CategoryChipBar from '@/components/domain/library/CategoryChipBar';
import CategoryManage from '@/components/domain/library/CategoryManage';
import Items from '@/components/domain/library/Items';
import LibrarySearch from '@/components/domain/library/LibrarySearch';
import SearchItems from '@/components/domain/library/SearchItems';
import ItemModal from '@/components/domain/library/detail/ItemModal';
import { useCategories } from '@/hooks/useCategories';
import { useItems } from '@/hooks/useItems';
import { toggleCategorySelection } from '@/utils/categorySelection';

/**
 * 대시보드(보관함) — 상단 필터 바 + 아이템 리스트.
 * TODO: 아이템 리스트(무한 스크롤) 이어서
 */
const LibraryPage = () => {
  const { workspaceId } = useParams<{ workspaceId: string }>();

  // 카테고리는 서버 상태 — useState 가 아니라 useQuery(useCategories)로 받는다.
  // 로딩 전이나 실패 시에도 화면이 비지 않게 빈 배열로 시작한다
  const { data: chips = [] } = useCategories(Number(workspaceId));

  // 필터는 이 화면에서만 의미 있고 기억할 필요 없어 지역 상태로 둔다
  const [selected, setSelected] = useState<number[]>([]);
  const [favoriteActive, setFavoriteActive] = useState(false);

  // 상세 모달 — 열린 아이템 id(null 이면 닫힘)
  const [openItemId, setOpenItemId] = useState<number | null>(null);

  // 검색 상태는 URL 에 — 검색어가 있으면 목록 대신 검색 결과를 보인다(카테고리 필터는 검색 시 무시)
  const [params] = useSearchParams();
  const q = params.get('q') ?? '';
  const aiMode = params.get('ai') === '1';

  // 보관함이 완전히 비었으면(필터 무관 전체 0개) 검색창을 숨긴다.
  // 로딩 중(undefined)엔 숨김 판단을 유보해 깜빡임을 막고, 검색 중(q)이면 늘 보인다.
  const { data: baseItems } = useItems({ workspaceId: Number(workspaceId), size: 20 });
  const workspaceEmpty = baseItems !== undefined && (baseItems.pages[0]?.totalElements ?? 0) === 0;

  return (
    // 좌우 여백(STAGE_PX)은 헤더 제목과 같은 값을 공유해 칩 바·리스트가 한 선에 맞는다.
    // StageHeader 가 absolute 로 떠 있어(모바일 58·데스크톱 66px) 콘텐츠를 그 아래에서 시작.
    //
    // 세로 flex 로 칩 바는 고정하고 아래 리스트만 스크롤한다.
    // main 이 overflow-hidden(성좌 캔버스용)이라 스크롤은 이 안에서 일어난다.
    <div
      className={classNames(
        'flex h-full w-full flex-col overflow-hidden bg-space pt-16 desktop:pt-[72px]',
        STAGE_PX,
      )}
    >
      {/* 보관함이 완전히 비면 필터·검색 둘 다 의미 없어 숨긴다 (검색 중이면 검색창은 유지) */}
      {!workspaceEmpty && (
        <CategoryChipBar
          chips={chips}
          selected={selected}
          onSelectAll={() => setSelected(toggleCategorySelection(selected, 'all'))}
          onToggle={(id) => setSelected((current) => toggleCategorySelection(current, id))}
          favoriteActive={favoriteActive}
          onToggleFavorite={() => setFavoriteActive((v) => !v)}
          manage={<CategoryManage workspaceId={Number(workspaceId)} />}
        />
      )}

      {(!workspaceEmpty || q) && <LibrarySearch />}

      {/* 칩 바 아래만 스크롤 — flex-1 로 남은 높이를 채우고 min-h-0 이라야 넘칠 때 줄어든다 */}
      <div className="min-h-0 flex-1 overflow-y-auto scrollbar-none">
        {q ? (
          <SearchItems q={q} aiMode={aiMode} onOpenItem={setOpenItemId} />
        ) : (
          // 필터는 전부 서버 처리 — Items → useItems queryKey 로 내려간다
          <Items favorite={favoriteActive} categoryIds={selected} onOpenItem={setOpenItemId} />
        )}
      </div>

      {/* 상세 모달 — 타입(사진·링크·메모)에 맞는 바디를 셸이 골라 띄운다 */}
      <ItemModal
        workspaceId={Number(workspaceId)}
        itemId={openItemId}
        onClose={() => setOpenItemId(null)}
      />
    </div>
  );
};

export default LibraryPage;
