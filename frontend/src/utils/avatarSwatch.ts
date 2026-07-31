import type { SpacemanColor } from './getSpacemanImage';

/**
 * 우주인 색상 → 아바타 배경색.
 *
 * 마이페이지 색상 선택(AvatarColorPicker)의 스와치와 **같은 값이어야** 하므로 여기 한 곳에만 둔다.
 * 두 곳에 적어 두면 색을 고칠 때 선택 화면과 아바타가 서로 다른 색을 보여주게 된다.
 */
const SWATCH: Record<SpacemanColor, string> = {
  white: '#F5F1E8',
  black: '#111318',
  red: '#E96B6B',
  crimson: '#B73D5C',
  pink: '#F58AAB',
  orange: '#F5B08A',
  yellow: '#C7F36B',
  green: '#B8E6A3',
  blue: '#8FB4FF',
  navy: '#536A9E',
  purple: '#C9B8FF',
};

const FALLBACK: SpacemanColor = 'white';

/**
 * 색상 이름으로 아바타 배경색을 얻는다.
 * 서버는 대문자("BLUE")로 주므로 여기서 맞추고, 모르는 색이면 흰색으로 대체한다
 * (서버가 새 색을 보내도 화면이 깨지지 않게 — getSpacemanImage 와 같은 규칙).
 */
export const getAvatarSwatch = (color: string | null | undefined): string =>
  SWATCH[color?.toLowerCase() as SpacemanColor] ?? SWATCH[FALLBACK];

/** sRGB 상대 휘도(WCAG). #RRGGBB 만 받는다. */
const relativeLuminance = (hex: string): number => {
  const toLinear = (channel: number) =>
    channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
  const [r, g, b] = [1, 3, 5].map((at) => toLinear(parseInt(hex.slice(at, at + 2), 16) / 255));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};

/**
 * 그 배경 위에서 읽히는 글자색.
 *
 * 우주인 색에는 밝은 파스텔이 많아(화이트·라임·그린) 흰 글씨를 고정으로 쓰면 이니셜이 사라진다.
 * 특히 **기본값이 WHITE** 라 색을 한 번도 안 고른 사용자가 전부 안 보이게 된다.
 * 색마다 글자색을 손으로 적는 대신 휘도로 판단해, 스와치가 바뀌거나 색이 추가돼도 따라오게 한다.
 */
export const getAvatarTextColor = (color: string | null | undefined): string =>
  relativeLuminance(getAvatarSwatch(color)) > 0.5 ? '#1b1e26' : '#ffffff';
