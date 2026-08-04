import { useEffect, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { classNames } from '@/utils/classNames';
import { STAGE_PT, STAGE_PX } from '@/constants/stage';
import CategoryChipBar from '@/components/domain/library/CategoryChipBar';
import CategoryManage from '@/components/domain/library/CategoryManage';
import Items from '@/components/domain/library/Items';
import LibrarySearch from '@/components/domain/library/LibrarySearch';
import SearchItems from '@/components/domain/library/SearchItems';
import ItemModal from '@/components/domain/library/detail/ItemModal';
import { useCategories } from '@/hooks/useCategories';
import { useStageMeta } from '@/hooks/useStageMeta';
import { toggleCategorySelection } from '@/utils/categorySelection';

/**
 * 대시보드(보관함) — 상단 필터 바 + 아이템 리스트.
 * TODO: 아이템 리스트(무한 스크롤) 이어서
 */
const LibraryPage = () => {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const [params, setParams] = useSearchParams();

  // 카테고리는 서버 상태 — useState 가 아니라 useQuery(useCategories)로 받는다.
  // 로딩 전이나 실패 시에도 화면이 비지 않게 빈 배열로 시작한다
  const { data: chips = [], isSuccess: categoriesLoaded } = useCategories(Number(workspaceId));

  // 필터는 이 화면에서만 의미 있고 기억할 필요 없어 지역 상태로 둔다
  const [selected, setSelected] = useState<number[]>([]);
  const [favoriteActive, setFavoriteActive] = useState(false);

  // 상세 모달 — 열린 아이템 id(null 이면 닫힘)
  const [openItemId, setOpenItemId] = useState<number | null>(null);

  // Mattermost 검색 결과의 딥링크(?item=42)로 들어오면 해당 상세를 바로 연다.
  const linkedItemId = Number(params.get('item'));
  useEffect(() => {
    if (Number.isSafeInteger(linkedItemId) && linkedItemId > 0) setOpenItemId(linkedItemId);
  }, [linkedItemId]);

  // 검색 상태는 URL 에 — 검색어가 있으면 목록 대신 검색 결과를 보인다(카테고리 필터는 검색 시 무시)
  const q = params.get('q') ?? '';
  const aiMode = params.get('ai') === '1';

  // 보관함이 완전히 비었으면(필터 무관 전체 0개) 필터·검색을 숨긴다.
  // 별도 폴링 쿼리를 두지 않고 Items 가 올려주는 전체 개수(onCount)를 재사용한다.
  // 필터가 걸려 있으면 그 개수는 "필터된 결과"이므로 빈 워크스페이스 판정에서 제외한다
  // (그 경우는 Items 가 "결과 없음"을 대신 보여준다). 로딩 중(undefined)엔 판단을 유보한다.
  const [itemCount, setItemCount] = useState<number>();
  const filtered = favoriteActive || selected.length > 0;
  const workspaceEmpty = !filtered && itemCount === 0;

  // 헤더 요약 — 현재 보이는 아이템 수 · 전체 카테고리 수. 지도·성좌와 같은 방식.
  // 둘 다 준비되기 전엔 비워 "0 items · 0 categories" 깜빡임을 막는다.
  useStageMeta(
    itemCount !== undefined && categoriesLoaded
      ? `${itemCount} items · ${chips.length} categories`
      : undefined,
  );

  return (
    // 좌우 여백(STAGE_PX)은 헤더 제목과 같은 값을 공유해 칩 바·리스트가 한 선에 맞는다.
    // 상단 여백(STAGE_PT)은 absolute 로 떠 있는 StageHeader 자리를 비운다 — 안전영역까지
    // 포함하므로 헤더가 상태바만큼 내려가도 칩 바를 덮지 않는다.
    //
    // 세로 flex 로 칩 바는 고정하고 아래 리스트만 스크롤한다.
    // main 이 overflow-hidden(성좌 캔버스용)이라 스크롤은 이 안에서 일어난다.
    <div
      className={classNames(
        'flex h-full w-full flex-col overflow-hidden bg-space',
        STAGE_PT,
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

      {/*
        칩 바 아래만 스크롤 — flex-1 로 남은 높이를 채우고 min-h-0 이라야 넘칠 때 줄어든다.
        탭바는 absolute 로 이 영역 위에 떠 있으므로 하단 여백으로 자리를 비워야 마지막
        아이템이 탭바에 가리지 않는다 (휴지통·마이페이지는 원래 그렇게 하고 있었다).
      */}
      <div
        data-tutorial-page-content="library"
        className="scrollbar-none min-h-0 flex-1 overflow-y-auto pb-above-tabbar desktop:pb-6"
      >
        {q ? (
          <SearchItems q={q} aiMode={aiMode} onOpenItem={setOpenItemId} />
        ) : (
          // 필터는 전부 서버 처리 — Items → useItems queryKey 로 내려간다.
          // 전체 개수는 onCount 로 올려 받아 빈 워크스페이스 판정에 쓴다(중복 쿼리 제거)
          <Items
            favorite={favoriteActive}
            categoryIds={selected}
            onOpenItem={setOpenItemId}
            onCount={setItemCount}
          />
        )}
      </div>

      {/* 상세 모달 — 타입(사진·링크·메모)에 맞는 바디를 셸이 골라 띄운다 */}
      <ItemModal
        workspaceId={Number(workspaceId)}
        itemId={openItemId}
        onClose={() => {
          setOpenItemId(null);
          if (params.has('item')) {
            const next = new URLSearchParams(params);
            next.delete('item');
            setParams(next, { replace: true });
          }
        }}
      />
    </div>
  );
};

export default LibraryPage;
