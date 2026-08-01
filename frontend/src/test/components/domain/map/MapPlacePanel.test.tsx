import { describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-react';
import MapPlacePanel from '@/components/domain/map/MapPlacePanel';
import type { Category } from '@/types/category';

const categories: Category[] = [{ categoryId: 1, name: '여행', color: '#8fb4ff' }];

const renderPanel = (isEmpty: boolean, selectedCategories = new Set<number>()) =>
  render(
    <MapPlacePanel
      categories={categories}
      places={[]}
      isEmpty={isEmpty}
      selectedCategories={selectedCategories}
      favoriteActive={false}
      collapsed={false}
      selectedPlaceId={null}
      onCollapsedChange={vi.fn()}
      onToggleCategory={vi.fn()}
      onToggleFavorite={vi.fn()}
      onSelectPlace={vi.fn()}
      onOpenItem={vi.fn()}
    />,
  );

const getFilterButtons = (container: HTMLElement) =>
  container.querySelectorAll<HTMLButtonElement>('button[aria-pressed]');

describe('MapPlacePanel', () => {
  it('지도 데이터가 없으면 캐릭터와 안내 문구만 표시한다', async () => {
    const { container } = await renderPanel(true);
    const text = container.textContent ?? '';

    expect(container.querySelector('img')).not.toBeNull();
    expect(text).toContain('아직 지도에 표시할 장소가 없어요');
    expect(text).toContain(
      '위치 정보가 있는 링크·사진·메모를 저장하면 지도에서 한눈에 볼 수 있어요.',
    );
    for (const hiddenText of ['저장한 장소', '즐겨찾기', '전체', '여행']) {
      expect(text).not.toContain(hiddenText);
    }
    expect(container.querySelector('[aria-label="데이터 타입 색상 안내"]')).toBeNull();
    expect(container.querySelector('[aria-label="장소 카테고리"]')).toBeNull();
  });

  it('필터 결과만 비었으면 기존 목록 정보와 카테고리를 유지한다', async () => {
    const { container } = await renderPanel(false);
    const text = container.textContent ?? '';

    expect(text).toContain('저장한 장소');
    expect(text).toContain('즐겨찾기');
    expect(text).toContain('여행');
    expect(text).toContain('표시할 카테고리를 선택해 주세요.');
    expect(text).not.toContain('아직 지도에 표시할 장소가 없어요');
  });

  it('전체 상태에서는 전체 칩만 선택 상태로 표시한다', async () => {
    const { container } = await renderPanel(false);
    const buttons = getFilterButtons(container);

    expect(buttons[1]?.getAttribute('aria-pressed')).toBe('true');
    expect(buttons[2]?.getAttribute('aria-pressed')).toBe('false');
  });

  it('카테고리를 선택하면 전체는 해제되고 해당 카테고리만 선택 상태로 표시한다', async () => {
    const { container } = await renderPanel(false, new Set([1]));
    const buttons = getFilterButtons(container);

    expect(buttons[1]?.getAttribute('aria-pressed')).toBe('false');
    expect(buttons[2]?.getAttribute('aria-pressed')).toBe('true');
  });
});
