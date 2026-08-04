import { useId } from 'react';
import {
  LOGO_ORBIT_PATH,
  LOGO_RING_OUTER_RADIUS,
  LOGO_SPARKLE_HOME,
  LOGO_VIEWBOX,
} from '@/constants/logoGeometry';

interface LogoRingProps {
  /**
   * 링의 바깥 지름. 함께 놓이는 숫자 높이에 맞춘다.
   * `em` 으로 주면 글자 크기를 따라가므로 반응형 코드(clamp)에서도 저절로 맞는다.
   *
   * 기본값 0.71em 은 눈대중이 아니라 실측이다 — Pretendard 800 의 숫자 `4` 를 캔버스
   * TextMetrics 로 재면 잉크 높이가 폰트 크기의 0.7097 배다(124px 기준 88px).
   */
  diameter?: string;
}

/**
 * 로고의 중앙 링 — 에러 코드의 `0` 자리에 들어간다.
 *
 * ── 스파클을 원본과 같은 질로 맞춘 경위 ────────────────────────────────────────
 * 원본(mainIcon.svg)을 크게 띄워 대조해 보니 세 가지가 달랐다:
 *   1. 원본은 바늘처럼 가늘고 핀포인트로 흰 4갈래 별인데, 그냥 원본 그라데이션을 screen
 *      으로 얹으면 뭉개진 십자가 된다. 원본이 또렷한 건 **color-dodge** 때문이다 —
 *      backdrop / (1 - source) 라 소스가 밝아지면 급격히 흰색으로 포화된다
 *   2. dodge 는 backdrop 이 0 이면 결과도 0 이라, 원본은 스파클 자리에 #141414 짜리
 *      불투명 판(bgGlow)을 깔아 나눌 여지를 만든다
 *   3. 궤도가 링과 만나는 곳의 부드러운 헤일로(arrival glow)를 아예 빼먹고 있었다
 *
 * 그런데 여기서는 **원본 방식(dodge + 불투명 판)을 쓸 수 없다.** 스파클의 정위치가 링
 * 상자 바깥(오른쪽 위)이고, 이 자리에서는 그 바깥이 곧 **다음 숫자가 있는 자리**다.
 * 불투명 판을 깔면 옆의 `4` 가 검게 잘려 나간다(실제로 그렇게 나왔다).
 *
 * 그래서 dodge 의 **결과**를 그라데이션 정지점으로 직접 그린다 — 중심은 흰색으로 꽉 차고
 * 바로 급격히 떨어지는 stop 배치다(SPARKLE_STOPS). 두 갈래가 겹치는 교차점은 알파가
 * 쌓여 흰색으로 포화되므로 블렌드 모드가 따로 필요하지 않고, 불투명한 판이 없으니 무엇
 * 위에 놓아도 안전하다.
 *
 * ── 왜 기존 두 구현을 쓰지 않는가 (실측 비교) ────────────────────────────────
 * 랜딩(AnimatedLogoBackdrop): 시안 HTML 을 `?raw` 로 번들에 싣고 iframe 으로 띄운다.
 *   HTML 원본만 15.4KB 라 LandingPage 청크(38.8KB)의 40% 가 그 문자열이고, 띄우는 순간
 *   별도 Document 가 통째로 생긴다(자체 style·script 파싱 + 별도 레이아웃·rAF 루프).
 * 사이드바(InteractiveLogo): 같은 문서 안 인라인 SVG + rAF 로 훨씬 싸지만, 스파클을
 *   돌리려면 `hovered` 를 켜야 하고 그러면 링이 r15 → r7 로 작아져 숫자 모양이 무너진다.
 *   크기도 px 고정이라 clamp() 로 커지는 글자를 따라오지 못한다.
 * → 사이드바 쪽(인라인 SVG)을 따르되 JS 를 전혀 쓰지 않는다.
 */

/**
 * 십자 한 갈래의 감쇠 — 원본이 color-dodge 로 만들어 내던 곡선을 정지점으로 직접 그린다.
 * 중심 12% 까지 흰색으로 꽉 차고(핀포인트) 그 뒤로 급격히 떨어져 바늘처럼 가늘어진다.
 * 원본은 끝에 파란 기운(#3A71FF)을 남기는데, 크게 그리면 그 색이 도드라져서 흰색만 쓴다.
 */
const SPARKLE_STOPS = [
  { offset: 0, opacity: 1 },
  { offset: 0.12, opacity: 1 },
  { offset: 0.24, opacity: 0.62 },
  { offset: 0.42, opacity: 0.22 },
  { offset: 0.68, opacity: 0.06 },
  { offset: 1, opacity: 0 },
] as const;

const LogoRing = ({ diameter = '0.71em' }: LogoRingProps) => {
  // 같은 화면에 링이 둘 이상일 수 있다(예: 500 → 0 이 두 개). 내부 id 가 충돌하면
  // 그라데이션을 서로 훔쳐 쓴다.
  const uid = useId().replace(/:/g, '');
  const id = (name: string) => `${uid}-${name}`;

  // 링은 viewBox 안에서 지름 30/61 만 차지한다. 원하는 지름이 diameter 가 되도록 SVG 를
  // 그 비율만큼 크게 잡고, 링이 래퍼의 정확히 왼쪽 위 0,0 에서 시작하도록 끌어올린다.
  //   svg = diameter × 61/30,  offset = -(15/61) × svg = -diameter/2
  // 단위를 파싱하지 않으려고 계산은 calc 로 미룬다(px·em·rem 아무거나 받는다).
  const boxSize = `calc(${diameter} * ${LOGO_VIEWBOX / (LOGO_RING_OUTER_RADIUS * 2)})`;
  const offset = `calc(${diameter} * -0.5)`;

  return (
    // 레이아웃이 차지하는 건 링뿐이다 — 궤도와 스파클은 이 상자 밖으로 삐져나와 그려진다.
    // inline-block 의 베이스라인은 아래쪽 모서리라, 옆 숫자의 밑선과 저절로 맞는다.
    <span
      aria-hidden
      style={{ position: 'relative', display: 'inline-block', width: diameter, height: diameter }}
    >
      <svg
        viewBox={`0 0 ${LOGO_VIEWBOX} ${LOGO_VIEWBOX}`}
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        style={{
          position: 'absolute',
          left: offset,
          top: offset,
          width: boxSize,
          height: boxSize,
          overflow: 'visible',
        }}
      >
        <defs>
          {/*
            링 안쪽(반지름 15)은 비워 둔다 — 궤도선과 헤일로가 링을 관통해 보이면 안 된다.
            원본은 검은 원판을 깔아 가리지만, 그러면 배경이 검정이라는 전제가 생긴다.
            마스크로 가리면 무엇 위에 놓아도 같은 모양이다.
            영역을 viewBox 보다 넉넉히 잡는 이유: 스파클이 상자 밖에 있다.
          */}
          <mask
            id={id('outsideRing')}
            maskUnits="userSpaceOnUse"
            x={-LOGO_VIEWBOX}
            y={-LOGO_VIEWBOX}
            width={LOGO_VIEWBOX * 3}
            height={LOGO_VIEWBOX * 3}
          >
            <rect
              x={-LOGO_VIEWBOX}
              y={-LOGO_VIEWBOX}
              width={LOGO_VIEWBOX * 3}
              height={LOGO_VIEWBOX * 3}
              fill="white"
            />
            <circle cx="30" cy="30" r={LOGO_RING_OUTER_RADIUS} fill="black" />
          </mask>

          {/* 궤도가 링과 만나는 지점의 부드러운 헤일로 (원본 arrival glow) */}
          <radialGradient
            id={id('arrival')}
            cx="0"
            cy="0"
            r="1"
            gradientUnits="userSpaceOnUse"
            gradientTransform="translate(37.939 19.6898) rotate(90) scale(3.84292 5.94922)"
          >
            <stop offset="0.168269" stopColor="white" stopOpacity="0.55" />
            <stop offset="1" stopColor="white" stopOpacity="0" />
          </radialGradient>

          {/* 스파클 자리를 아주 옅게 들어 올린다 — 원본 bgGlow(#141414 판)를 투명하게 대체 */}
          <radialGradient
            id={id('sparkleHalo')}
            cx="0"
            cy="0"
            r="1"
            gradientUnits="userSpaceOnUse"
            gradientTransform={`translate(${LOGO_SPARKLE_HOME.x} ${LOGO_SPARKLE_HOME.y}) rotate(90) scale(9)`}
          >
            <stop stopColor="white" stopOpacity="0.14" />
            <stop offset="1" stopColor="white" stopOpacity="0" />
          </radialGradient>

          {/*
            유리 행성 — 링을 평평한 단색이 아니라 부피가 있는 유리로 보이게 하는 세 겹.
            색을 currentColor 로 두면 옆 숫자와 같은 색을 기준으로 밝기만 갈린다.

            검은 배경에서는 backdrop-filter 를 쓸 수 없다(뒤에 블러할 것이 없어서 아무
            효과가 없다). 그래서 글래스모피즘의 실제 단서인 **빛의 방향**으로 만든다 —
            좌상단이 밝고 우하단으로 어두워지는 몸통 + 얇은 스페큘러 테두리.
          */}
          <linearGradient
            id={id('glassRim')}
            x1="19"
            y1="16"
            x2="43"
            y2="45"
            gradientUnits="userSpaceOnUse"
          >
            <stop offset="0" stopColor="currentColor" stopOpacity="1" />
            <stop offset="0.55" stopColor="currentColor" stopOpacity="0.74" />
            <stop offset="1" stopColor="currentColor" stopOpacity="0.44" />
          </linearGradient>

          <radialGradient
            id={id('glassBody')}
            cx="0"
            cy="0"
            r="1"
            gradientUnits="userSpaceOnUse"
            gradientTransform="translate(24 23) scale(19)"
          >
            <stop offset="0" stopColor="currentColor" stopOpacity="0.16" />
            <stop offset="1" stopColor="currentColor" stopOpacity="0.02" />
          </radialGradient>

          <linearGradient
            id={id('glassSpec')}
            x1="20"
            y1="17"
            x2="35"
            y2="35"
            gradientUnits="userSpaceOnUse"
          >
            <stop offset="0" stopColor="currentColor" stopOpacity="0.95" />
            <stop offset="1" stopColor="currentColor" stopOpacity="0" />
          </linearGradient>

          {(['sparkleV', 'sparkleH'] as const).map((name, index) => (
            <radialGradient
              key={name}
              id={id(name)}
              cx="0"
              cy="0"
              r="1"
              gradientUnits="userSpaceOnUse"
              // 세로 갈래는 그대로, 가로 갈래는 90° 돌린 같은 모양
              gradientTransform={
                `translate(${LOGO_SPARKLE_HOME.x} ${LOGO_SPARKLE_HOME.y}) ` +
                `rotate(${index === 0 ? 90 : 0}) scale(8.5 0.885417)`
              }
            >
              {SPARKLE_STOPS.map((stop) => (
                <stop
                  key={stop.offset}
                  offset={stop.offset}
                  stopColor="white"
                  stopOpacity={stop.opacity}
                />
              ))}
            </radialGradient>
          ))}
        </defs>

        <g mask={`url(#${id('outsideRing')})`}>
          {/*
            궤도선. 굵기를 non-scaling-stroke 로 두는 이유: 원본의 0.1 유닛은 이 크기로
            렌더하면 0.3px 이 되어 뭉개진다. 화면 픽셀 기준 1px 로 고정하면 어떤 크기에서도
            원본처럼 또렷한 실선이 된다(InteractiveLogo 가 굵기를 역산하는 것과 같은 목적).
          */}
          <path
            d={LOGO_ORBIT_PATH}
            stroke="currentColor"
            strokeWidth="1"
            vectorEffect="non-scaling-stroke"
            opacity="0.75"
          />

          <ellipse
            cx="37.939"
            cy="19.6898"
            rx="5.94922"
            ry="3.84292"
            transform="rotate(35.1926 37.939 19.6898)"
            fill={`url(#${id('arrival')})`}
          />

          <circle
            cx={LOGO_SPARKLE_HOME.x}
            cy={LOGO_SPARKLE_HOME.y}
            r="9"
            fill={`url(#${id('sparkleHalo')})`}
          />

          {/*
            십자 스파클 — 세로 갈래와 가로 갈래.
            한때 각각 screen 블렌드로 얹었지만 뺐다. 감쇠를 정지점에 직접 그려 넣은 뒤로는
            보통의 알파 합성과 결과가 같았다(래스터 픽셀 비교: 평균 차 0.003/255, 최대 2).
            블렌드 모드는 별도 합성 단계를 강제하므로 값을 못 하면 지우는 쪽이 맞다.
          */}
          <ellipse
            cx={LOGO_SPARKLE_HOME.x}
            cy={LOGO_SPARKLE_HOME.y}
            rx="0.885417"
            ry="8.5"
            fill={`url(#${id('sparkleV')})`}
          />
          <ellipse
            cx={LOGO_SPARKLE_HOME.x}
            cy={LOGO_SPARKLE_HOME.y}
            rx="8.5"
            ry="0.885417"
            fill={`url(#${id('sparkleH')})`}
          />
        </g>

        {/* 조명층 — 빛이 좌상단에서 온다고 보고 세 겹을 겹친다. 전부 같은 중심의 원이다 */}

        {/* 유리 몸통 — 아주 옅게 채워 속이 빈 고리가 아니라 투명한 구체로 읽히게 한다.
            0 의 안쪽이 완전히 비어 있어야 숫자로 읽히므로 진하게 채우지 않는다 */}
        <circle cx="30" cy="30" r={LOGO_RING_OUTER_RADIUS} fill={`url(#${id('glassBody')})`} />

        {/* 링 본체 — 이 자리에서 숫자 0 을 대신한다 */}
        <circle cx="30" cy="30" r="11.5" stroke={`url(#${id('glassRim')})`} strokeWidth="7" />

        {/* 유리 두께를 만드는 스페큘러 — 바깥·안쪽 테두리에 얇게 얹는다 */}
        <circle
          cx="30"
          cy="30"
          r="14.7"
          stroke={`url(#${id('glassSpec')})`}
          strokeWidth="0.6"
          fill="none"
        />
        <circle
          cx="30"
          cy="30"
          r="8.3"
          stroke={`url(#${id('glassSpec')})`}
          strokeWidth="0.5"
          fill="none"
          opacity="0.7"
        />
      </svg>
    </span>
  );
};

export default LogoRing;
