import { describe, it, expect } from 'vitest';
import { getSpacemanImage, SPACEMAN_COLORS } from '@/utils/getSpacemanImage';

describe('getSpacemanImage', () => {
  it('색상마다 서로 다른 이미지를 반환한다', () => {
    const urls = SPACEMAN_COLORS.map(getSpacemanImage);
    expect(new Set(urls).size).toBe(SPACEMAN_COLORS.length);
  });

  it('모르는 색이면 white 로 대체한다', () => {
    // 서버가 새 색을 보내도 이미지가 깨지지 않아야 한다
    expect(getSpacemanImage('없는색')).toBe(getSpacemanImage('white'));
    expect(getSpacemanImage('')).toBe(getSpacemanImage('white'));
  });
});
