import { describe, it, expect } from 'vitest';
import { isDaylight, skyColorsForElevation, solarElevation } from '@/utils/solar';

/** 서울 */
const SEOUL = { latitude: 37.5665, longitude: 126.978 };

describe('solarElevation', () => {
  it('한여름 정오에 해가 높이 뜬다', () => {
    // 2026-06-21 12:30 KST — 하지 무렵 남중. 서울 위도에서 75° 안팎이다.
    const elevation = solarElevation(new Date('2026-06-21T03:30:00Z'), SEOUL);
    expect(elevation).toBeGreaterThan(70);
    expect(elevation).toBeLessThan(80);
  });

  it('한겨울 정오에는 같은 곳에서도 훨씬 낮다', () => {
    // 계절에 따라 낮의 높이가 달라지는 것이 이 계산을 쓰는 이유다 —
    // 일출·일몰 시각을 고정값으로 박아 두면 이게 안 된다.
    const summer = solarElevation(new Date('2026-06-21T03:30:00Z'), SEOUL);
    const winter = solarElevation(new Date('2026-12-21T03:30:00Z'), SEOUL);
    expect(winter).toBeLessThan(summer - 40);
    expect(winter).toBeGreaterThan(0);
  });

  it('자정에는 지평선 아래다', () => {
    // 2026-06-21 00:00 KST
    const elevation = solarElevation(new Date('2026-06-20T15:00:00Z'), SEOUL);
    expect(elevation).toBeLessThan(-10);
  });

  it('고도는 항상 -90도와 90도 사이다', () => {
    for (let hour = 0; hour < 24; hour += 1) {
      const at = new Date(Date.UTC(2026, 2, 15, hour));
      const elevation = solarElevation(at, SEOUL);
      expect(elevation).toBeGreaterThanOrEqual(-90);
      expect(elevation).toBeLessThanOrEqual(90);
    }
  });
});

describe('skyColorsForElevation', () => {
  it('표 바깥 값은 양 끝 색으로 붙는다', () => {
    // 극야·백야나 계산 오차로 표를 벗어나도 색이 비면 안 된다
    expect(skyColorsForElevation(-90)).toEqual(skyColorsForElevation(-18));
    expect(skyColorsForElevation(90)).toEqual(skyColorsForElevation(40));
  });

  it('모든 고도에서 세 색이 유효한 hex 다', () => {
    for (let elevation = -90; elevation <= 90; elevation += 3) {
      const { top, middle, bottom } = skyColorsForElevation(elevation);
      for (const color of [top, middle, bottom]) {
        expect(color).toMatch(/^#[0-9a-f]{6}$/);
      }
    }
  });

  it('해가 높을수록 지평선 쪽이 밝아진다', () => {
    const brightness = (hex: string) =>
      parseInt(hex.slice(1, 3), 16) + parseInt(hex.slice(3, 5), 16) + parseInt(hex.slice(5, 7), 16);
    expect(brightness(skyColorsForElevation(30).bottom)).toBeGreaterThan(
      brightness(skyColorsForElevation(-15).bottom),
    );
  });
});

describe('isDaylight', () => {
  it('해가 진 직후 잠깐은 아직 밝은 것으로 본다', () => {
    // 0도에서 곧장 어두워지면 아직 환한 하늘에 야간 UI 가 얹힌다
    expect(isDaylight(-1)).toBe(true);
    expect(isDaylight(-10)).toBe(false);
  });
});
