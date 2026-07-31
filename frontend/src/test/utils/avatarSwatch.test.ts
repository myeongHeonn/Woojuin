import { describe, it, expect } from 'vitest';
import { getAvatarSwatch, getAvatarTextColor } from '@/utils/avatarSwatch';
import { SPACEMAN_COLORS } from '@/utils/getSpacemanImage';

describe('getAvatarSwatch', () => {
  it('우주인 색상 전부에 색값이 있다', () => {
    // 선택 가능한 색인데 스와치가 빠지면 그 색을 고른 사람만 흰 아바타가 된다
    for (const color of SPACEMAN_COLORS) {
      expect(getAvatarSwatch(color)).toMatch(/^#[0-9A-Fa-f]{6}$/);
    }
  });

  it('색상마다 서로 다른 색값이다', () => {
    const swatches = SPACEMAN_COLORS.map(getAvatarSwatch);
    expect(new Set(swatches).size).toBe(SPACEMAN_COLORS.length);
  });

  it('대문자 색 이름도 찾는다 — 서버는 avatarColor: "BLUE" 로 준다', () => {
    for (const color of SPACEMAN_COLORS) {
      expect(getAvatarSwatch(color.toUpperCase())).toBe(getAvatarSwatch(color));
    }
  });

  it('모르는 색·빈 값이면 white 로 대체한다', () => {
    expect(getAvatarSwatch('없는색')).toBe(getAvatarSwatch('white'));
    expect(getAvatarSwatch('')).toBe(getAvatarSwatch('white'));
    expect(getAvatarSwatch(null)).toBe(getAvatarSwatch('white'));
    expect(getAvatarSwatch(undefined)).toBe(getAvatarSwatch('white'));
  });
});

describe('getAvatarTextColor — 이니셜이 배경에 묻히지 않아야 한다', () => {
  it('밝은 배경엔 어두운 글자색', () => {
    // white 는 avatarColor 기본값이다. 흰 글씨를 고정으로 쓰면 색을 안 고른 사용자
    // 전원의 이니셜이 사라진다 — 이 케이스가 이 함수가 존재하는 이유다.
    expect(getAvatarTextColor('white')).toBe('#1b1e26');
    expect(getAvatarTextColor('yellow')).toBe('#1b1e26');
    expect(getAvatarTextColor('green')).toBe('#1b1e26');
  });

  it('어두운 배경엔 흰 글자색', () => {
    expect(getAvatarTextColor('black')).toBe('#ffffff');
    expect(getAvatarTextColor('navy')).toBe('#ffffff');
    expect(getAvatarTextColor('crimson')).toBe('#ffffff');
  });

  it('모든 색이 두 글자색 중 하나로 결정된다', () => {
    for (const color of SPACEMAN_COLORS) {
      expect(['#1b1e26', '#ffffff']).toContain(getAvatarTextColor(color));
    }
  });
});
