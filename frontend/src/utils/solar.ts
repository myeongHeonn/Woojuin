/**
 * 태양 고도로 지금 하늘색을 구한다.
 *
 * 일출·일몰 시각을 표로 들고 있지 않고 고도를 직접 계산하는 이유: 하늘색은 "해가 떴는가"의
 * 두 값이 아니라 해가 지평선에서 얼마나 떨어져 있는가에 따라 연속으로 바뀐다. 고도 하나면
 * 계절과 위도에 따라 달라지는 일출·일몰 시각이 저절로 따라온다.
 */

const RAD = Math.PI / 180;

/**
 * 위치를 브라우저에 묻지 않는다 — 테마 하나 때문에 위치 권한을 요구할 일은 아니다.
 * 경도는 표준시 오프셋에서 뽑는다(1시간 = 15°). 오차는 최대 ±7.5°, 시간으로는 30분쯤인데
 * 하늘색이 그 사이에 눈에 띄게 달라지지 않는다.
 *
 * 위도는 오프셋으로 알 수 없어 기본값을 둔다. 서비스 사용자가 국내라 서울 위도로 잡았다.
 * 위도가 틀리면 낮의 길이가 실제와 달라지지만, 색이 변하는 순서와 모양은 그대로다.
 */
const DEFAULT_LATITUDE = 37.5;

export interface Coordinates {
  latitude: number;
  longitude: number;
}

export function approximateCoordinates(date: Date = new Date()): Coordinates {
  // getTimezoneOffset 은 UTC 기준 분이고 동쪽이 음수다 — 부호를 뒤집어야 경도와 방향이 맞는다
  const offsetHours = -date.getTimezoneOffset() / 60;
  return { latitude: DEFAULT_LATITUDE, longitude: offsetHours * 15 };
}

/**
 * 태양 고도(도). 지평선 위가 양수다.
 *
 * NOAA 저정밀 알고리즘이다 — 오차 ±0.5° 안쪽이고, 이 용도(색 고르기)에는 충분하다.
 */
export function solarElevation(date: Date, { latitude, longitude }: Coordinates): number {
  const julianDay = date.getTime() / 86_400_000 + 2_440_587.5;
  const n = julianDay - 2_451_545; // J2000 이후 경과일

  const meanLongitude = (280.46 + 0.9856474 * n) % 360;
  const meanAnomaly = ((357.528 + 0.9856003 * n) % 360) * RAD;
  // 황경 — 지구 궤도가 타원이라 평균값에 보정을 더한다
  const eclipticLongitude =
    (meanLongitude + 1.915 * Math.sin(meanAnomaly) + 0.02 * Math.sin(2 * meanAnomaly)) * RAD;
  const obliquity = (23.439 - 0.0000004 * n) * RAD;

  const declination = Math.asin(Math.sin(obliquity) * Math.sin(eclipticLongitude));
  const rightAscension = Math.atan2(
    Math.cos(obliquity) * Math.sin(eclipticLongitude),
    Math.cos(eclipticLongitude),
  );

  // 그리니치 항성시 → 지방 항성시. 시간각은 태양이 남중에서 얼마나 벗어났는지다.
  const siderealHours = (18.697374558 + 24.06570982441908 * n) % 24;
  const localSidereal = (siderealHours * 15 + longitude) * RAD;
  const hourAngle = localSidereal - rightAscension;

  const lat = latitude * RAD;
  const sinElevation =
    Math.sin(lat) * Math.sin(declination) +
    Math.cos(lat) * Math.cos(declination) * Math.cos(hourAngle);

  return Math.asin(Math.max(-1, Math.min(1, sinElevation))) / RAD;
}

/** 하늘 그라데이션의 세 지점 — 위·중간·아래(지평선). */
export interface SkyColors {
  top: string;
  middle: string;
  bottom: string;
}

/**
 * 고도별 기준색. 사이 값은 선형 보간한다.
 *
 * 경계값은 천문학의 박명 구분을 따랐다 — -18° 천문박명(완전한 밤), -12° 항해박명,
 * -6° 시민박명(맨눈으로 사물 구분), 0° 일출·일몰. 그 위는 광선의 각도가 낮아 붉게 보이는
 * 골든아워다. 실제 하늘이 색을 바꾸는 지점이라 눈이 자연스럽다고 느낀다.
 *
 * 40°(한낮) 값은 라이트 테마의 하늘과 같다 — 두 모드가 낮에 같은 화면이 되어야 어색하지 않다.
 */
const SKY_STOPS: { elevation: number; colors: SkyColors }[] = [
  { elevation: -18, colors: { top: '#070a12', middle: '#0a1020', bottom: '#111a2e' } },
  { elevation: -12, colors: { top: '#080d1f', middle: '#0e1730', bottom: '#1a2747' } },
  { elevation: -6, colors: { top: '#0f1a3c', middle: '#1d2a55', bottom: '#3d3a63' } },
  { elevation: -2, colors: { top: '#17306a', middle: '#43427a', bottom: '#a55c68' } },
  { elevation: 0, colors: { top: '#1b3570', middle: '#5b4d84', bottom: '#e08a5a' } },
  { elevation: 4, colors: { top: '#2a5ea8', middle: '#5e8fc9', bottom: '#f0b083' } },
  { elevation: 12, colors: { top: '#0f63cf', middle: '#3585de', bottom: '#9fd0f2' } },
  { elevation: 40, colors: { top: '#0c68d8', middle: '#3389e2', bottom: '#8cc4f1' } },
];

function mixChannel(from: number, to: number, ratio: number): number {
  return Math.round(from + (to - from) * ratio);
}

function mixHex(from: string, to: string, ratio: number): string {
  const parse = (hex: string) => [
    parseInt(hex.slice(1, 3), 16),
    parseInt(hex.slice(3, 5), 16),
    parseInt(hex.slice(5, 7), 16),
  ];
  const [r1, g1, b1] = parse(from);
  const [r2, g2, b2] = parse(to);
  const channel = (a: number, b: number) => mixChannel(a, b, ratio).toString(16).padStart(2, '0');
  return `#${channel(r1, r2)}${channel(g1, g2)}${channel(b1, b2)}`;
}

/** 고도에 해당하는 하늘색. 표의 양 끝을 벗어나면 끝 값으로 붙인다. */
export function skyColorsForElevation(elevation: number): SkyColors {
  const first = SKY_STOPS[0];
  const last = SKY_STOPS[SKY_STOPS.length - 1];
  if (elevation <= first.elevation) return first.colors;
  if (elevation >= last.elevation) return last.colors;

  const upperIndex = SKY_STOPS.findIndex((stop) => stop.elevation >= elevation);
  const upper = SKY_STOPS[upperIndex];
  const lower = SKY_STOPS[upperIndex - 1];
  const ratio = (elevation - lower.elevation) / (upper.elevation - lower.elevation);

  return {
    top: mixHex(lower.colors.top, upper.colors.top, ratio),
    middle: mixHex(lower.colors.middle, upper.colors.middle, ratio),
    bottom: mixHex(lower.colors.bottom, upper.colors.bottom, ratio),
  };
}

/**
 * 화면 UI(버튼·글자)를 밝게 쓸지 어둡게 쓸지.
 *
 * 하늘색과 달리 UI 는 연속으로 바뀔 수 없다 — 대비가 보장돼야 읽히기 때문이다. 그래서 여기만
 * 한 지점에서 가른다. 기준을 0°(일출·일몰)가 아니라 -4° 로 둔 이유: 해가 막 진 직후에도
 * 한동안은 바깥이 밝다. 0° 에서 곧장 어두워지면 아직 환한 하늘에 야간 UI 가 얹힌다.
 */
export function isDaylight(elevation: number): boolean {
  return elevation > -4;
}
