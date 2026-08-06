import { describe, it, expect, afterEach, vi } from 'vitest';
import { timeZoneAbbreviation } from '@/utils/timeZone';

/** Intl 이 어떤 지역에 있다고 답하게 만든다 — 테스트 러너의 실제 타임존에 기대지 않는다. */
function pretendTimeZone(longName: string, offsetMinutes: number) {
  // 화살표 함수는 new 로 못 부른다 — Intl.DateTimeFormat 은 생성자로 쓰이므로 function 이어야 한다
  const fake = function FakeDateTimeFormat() {
    return {
      formatToParts: () => [{ type: 'timeZoneName', value: longName }],
    };
  };
  vi.spyOn(Intl, 'DateTimeFormat').mockImplementation(
    fake as unknown as typeof Intl.DateTimeFormat,
  );
  vi.spyOn(Date.prototype, 'getTimezoneOffset').mockReturnValue(offsetMinutes);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('timeZoneAbbreviation', () => {
  it('정식 명칭의 머리글자를 딴다', () => {
    // Intl 이 약칭을 직접 주지 않아(어디서나 "GMT+9") 이름에서 만들어야 한다
    pretendTimeZone('Korean Standard Time', -540);
    expect(timeZoneAbbreviation()).toBe('KST');
  });

  it('서머타임 이름도 그대로 따라간다', () => {
    pretendTimeZone('Pacific Daylight Time', 420);
    expect(timeZoneAbbreviation()).toBe('PDT');
  });

  it('UTC 는 예외다', () => {
    // 머리글자를 그대로 따면 CUT 이 되는데 아무도 그렇게 쓰지 않는다
    pretendTimeZone('Coordinated Universal Time', 0);
    expect(timeZoneAbbreviation()).toBe('UTC');
  });

  it('이름이 없는 지역은 UTC 오프셋으로 떨어진다', () => {
    // 시계 옆에 붙는 단위라 비어 있으면 안 된다
    pretendTimeZone('GMT+05:30', -330);
    expect(timeZoneAbbreviation()).toBe('UTC+5:30');
  });

  it('서쪽 지역은 부호가 음수다', () => {
    pretendTimeZone('GMT-03:00', 180);
    expect(timeZoneAbbreviation()).toBe('UTC-3');
  });
});
