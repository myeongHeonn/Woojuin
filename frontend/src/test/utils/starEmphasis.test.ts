import { describe, it, expect } from 'vitest';
import { starEmphasis } from '@/utils/scene';

/**
 * 성좌 씬의 강조 규칙. 렌더 루프 안에서 매 프레임 불리지만 규칙 자체는 순수해서
 * 여기서 검증한다 — WebGL 머티리얼 값은 밖에서 읽을 수 없다.
 */

const emphasis = (over: Partial<Parameters<typeof starEmphasis>[0]> = {}) =>
  starEmphasis({
    isHighlighted: false,
    isCategoryActive: false,
    hasActiveCategory: false,
    isPointed: false,
    ...over,
  });

describe('starEmphasis', () => {
  it('아무것도 고르지 않았으면 모든 별이 보통이다', () => {
    expect(emphasis()).toBe('normal');
    expect(emphasis({ isPointed: true })).toBe('normal');
  });

  it('카테고리를 고르면 소속 별은 강조되고 나머지는 어두워진다', () => {
    expect(emphasis({ hasActiveCategory: true, isCategoryActive: true })).toBe('active');
    expect(emphasis({ hasActiveCategory: true })).toBe('dimmed');
  });

  // 가리키고 있는 별까지 어두워지면 커서를 따라오는 반응이 사라져 어색하다
  it('호버·포커스 중인 별은 어두워지지 않는다', () => {
    expect(emphasis({ hasActiveCategory: true, isPointed: true })).toBe('normal');
  });

  /**
   * 검색 하이라이트는 카테고리와 다른 축이다. 다른 별자리에 있는 검색 결과가
   * 카테고리 때문에 묻히면 검색이 제 역할을 못 한다.
   */
  it('검색 하이라이트는 카테고리보다 세고, 절대 어두워지지 않는다', () => {
    expect(emphasis({ isHighlighted: true, hasActiveCategory: true })).toBe('highlighted');
    expect(emphasis({ isHighlighted: true, hasActiveCategory: true, isCategoryActive: true })).toBe(
      'highlighted',
    );
  });
});
