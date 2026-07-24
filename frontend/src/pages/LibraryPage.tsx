import { useState } from 'react';
import { classNames } from '@/utils/classNames';
import { STAGE_PX } from '@/constants/stage';
import CategoryChipBar, { type CategoryChip } from '@/components/domain/library/CategoryChipBar';

// 임시 목업 — 화살표 스크롤이 보이도록 넉넉히 둔다. 실제로는 useCategories 로 교체
const MOCK_CHIPS: CategoryChip[] = [
  { categoryId: 1, name: '카페', count: 12 },
  { categoryId: 2, name: '여행', count: 9 },
  { categoryId: 3, name: '개발', count: 21 },
  { categoryId: 4, name: '디자인', count: 7 },
  { categoryId: 5, name: '음식', count: 15 },
  { categoryId: 6, name: '운동', count: 4 },
  { categoryId: 7, name: '음악', count: 8 },
  { categoryId: 8, name: '영화', count: 6 },
  { categoryId: 9, name: '독서', count: 11 },
];

/**
 * 대시보드(보관함) — 상단 필터 바 + 아이템 리스트.
 * TODO: 아이템 리스트(무한 스크롤) 이어서
 */
const LibraryPage = () => {
  // 필터는 이 화면에서만 의미 있고 기억할 필요 없어 지역 상태로 둔다
  const [selected, setSelected] = useState<number[]>([]);
  const [favoriteActive, setFavoriteActive] = useState(false);

  return (
    // 좌우 여백(STAGE_PX)은 헤더 제목과 같은 값을 공유해 칩 바·리스트가 한 선에 맞는다.
    // StageHeader 가 absolute 로 떠 있어(모바일 58·데스크톱 66px) 콘텐츠를 그 아래에서
    // 시작시킨다. 여유를 둬 제목과 필터 사이 간격도 준다
    <div className={classNames('h-full w-full bg-space pt-16 desktop:pt-[72px]', STAGE_PX)}>
      <CategoryChipBar
        chips={MOCK_CHIPS}
        totalCount={93}
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
