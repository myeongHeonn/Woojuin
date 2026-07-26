import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { classNames } from '@/utils/classNames';
import { STAGE_PX } from '@/constants/stage';
import CategoryChipBar from '@/components/domain/library/CategoryChipBar';
import { useCategories } from '@/hooks/useCategories';

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

  return (
    // 좌우 여백(STAGE_PX)은 헤더 제목과 같은 값을 공유해 칩 바·리스트가 한 선에 맞는다.
    // StageHeader 가 absolute 로 떠 있어(모바일 58·데스크톱 66px) 콘텐츠를 그 아래에서
    // 시작시킨다. 여유를 둬 제목과 필터 사이 간격도 준다
    <div className={classNames('h-full w-full bg-space pt-16 desktop:pt-[72px]', STAGE_PX)}>
      <CategoryChipBar
        chips={chips}
        selected={selected}
        onSelectAll={() => setSelected([])}
        onToggle={(id) =>
          setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]))
        }
        favoriteActive={favoriteActive}
        onToggleFavorite={() => setFavoriteActive((v) => !v)}
        onManage={() => {}}
      />
    </div>
  );
};

export default LibraryPage;
