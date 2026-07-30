import { useEffect, useId, useRef } from 'react';

interface InteractiveLogoProps {
  /** 로고 한 변 (px) */
  size?: number;
  /**
   * 호버 상태 — 부모가 내려준다. 로고 자신이 아니라 "로고+서비스명" 묶음(사이드바 브랜드)에
   * 마우스를 올려도 반응해야 해서, 호버 감지는 부모의 몫으로 뺐다.
   */
  hovered?: boolean;
  className?: string;
}

/**
 * 인터랙티브 로고 — 디자인 시안(would-you-in-hover-orbit)의 rAF 엔진을 React 로 포팅.
 *
 * 동작:
 *  - 마운트(앱 진입) 시 인트로 1회: 중앙 링이 스프링 이징으로 커지고, 스파클이 궤도를
 *    따라 좌하단 → 우상단 홈 위치로 날아와 방사광과 함께 박힌다 (~1.3초)
 *  - hovered=true: 방사광이 꺼지고 링이 작아지며 스파클이 궤도를 공전한다 (1바퀴 ~2.8초)
 *  - hovered=false: 링이 소프트 백 이징으로 복귀, 스파클은 최단 방향으로 홈에 돌아온 뒤
 *    방사광이 다시 켜진다
 *
 * SMIL(mainIconMoving.svg)이 아니라 JS 인 이유: SMIL 은 1회 재생 전용이라 호버 공전처럼
 * 시작·정지·복귀가 얽힌 상태 전이를 표현할 수 없다. 원본 시안도 같은 이유로 rAF 를 쓴다.
 *
 * 작은 크기(사이드바 28px) 보정 — 원본 시안은 340px 기준이라 그대로 줄이면 궤도가 안 보인다:
 *  - 궤도선을 stroke 0.1 → 0.9 로 굵히고, 평소엔 거의 숨겼다가 호버 때만 드러낸다
 *  (호버 확대는 넣었다가 뺐다 — 접힌 사이드바 등에서 로고가 커지는 게 어색하다는 피드백)
 *
 * prefers-reduced-motion 이면 인트로·공전 없이 완성된 모양으로 고정된다.
 */
const InteractiveLogo = ({ size = 28, hovered = false, className }: InteractiveLogoProps) => {
  // 같은 화면에 로고가 두 개 떠도 SVG 내부 id(그라데이션·필터)가 충돌하지 않게 한다
  const uid = useId().replace(/:/g, '');
  const id = (name: string) => `${uid}-${name}`;
  const url = (name: string) => `url(#${uid}-${name})`;

  const orbitPathRef = useRef<SVGPathElement>(null);
  const sparkleRef = useRef<SVGGElement>(null);
  const ringFillRef = useRef<SVGCircleElement>(null);
  const ringStrokeRef = useRef<SVGCircleElement>(null);
  const glowRef = useRef<SVGEllipseElement>(null);

  const setHoverRef = useRef<(hovering: boolean) => void>(() => {});

  useEffect(() => {
    const orbitPath = orbitPathRef.current;
    const sparkle = sparkleRef.current;
    const ringFill = ringFillRef.current;
    const ringStroke = ringStrokeRef.current;
    const arrivalGlow = glowRef.current;
    if (!orbitPath || !sparkle || !ringFill || !ringStroke || !arrivalGlow) return;

    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    // 궤도 위 좌표. HOME = 우상단 십자 광채의 정위치(스파클 원본 좌표와 일치해야 translate 0 이 홈이다)
    const HOME_POINT = { x: 46.5, y: 16.5 };
    const INTRO_POINT = { x: 13.502, y: 42.6794 };

    const NORMAL_FILL_R = 15;
    const NORMAL_STROKE_R = 11.5;
    const HOVER_FILL_R = 7;
    const HOVER_STROKE_R = 3.5;

    const pathLength = orbitPath.getTotalLength();

    let homeDistance = 0;
    let currentDistance = 0;
    let introFinished = false;
    let isHovering = false;
    let orbitRaf: number | null = null;
    let orbitLastTime = 0;
    // 진행 중 애니메이션 취소는 토큰 비교로 한다 — 새 요청이 토큰을 올리면 옛 프레임은 스스로 멈춘다
    let ringToken = 0;
    let sparkleToken = 0;
    let glowToken = 0;
    let disposed = false;
    const timeouts: ReturnType<typeof setTimeout>[] = [];

    const clamp = (v: number, min: number, max: number) => Math.max(min, Math.min(max, v));
    const mod = (v: number, n: number) => ((v % n) + n) % n;
    const easeOutCubic = (t: number) => 1 - Math.pow(1 - t, 3);
    const easeInOutCubic = (t: number) =>
      t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    const easeOutBackSoft = (t: number) => {
      const c1 = 0.55;
      const c3 = c1 + 1;
      return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
    };

    /** 궤도 위에서 target 과 가장 가까운 지점까지의 거리(경로 시작 기준) */
    const nearestDistance = (target: { x: number; y: number }) => {
      let bestDistance = 0;
      let bestSquared = Infinity;
      const samples = 1200;
      for (let i = 0; i <= samples; i += 1) {
        const distance = (pathLength * i) / samples;
        const point = orbitPath.getPointAtLength(distance);
        const dx = point.x - target.x;
        const dy = point.y - target.y;
        const squared = dx * dx + dy * dy;
        if (squared < bestSquared) {
          bestSquared = squared;
          bestDistance = distance;
        }
      }
      return bestDistance;
    };

    const setRing = (fillRadius: number, strokeRadius: number) => {
      ringFill.setAttribute('r', fillRadius.toFixed(4));
      ringStroke.setAttribute('r', strokeRadius.toFixed(4));
    };

    const placeSparkle = (distance: number) => {
      currentDistance = mod(distance, pathLength);
      const point = orbitPath.getPointAtLength(currentDistance);
      sparkle.setAttribute(
        'transform',
        `translate(${point.x - HOME_POINT.x} ${point.y - HOME_POINT.y})`,
      );
    };

    const animateRing = (
      targetFill: number,
      targetStroke: number,
      duration: number,
      easing: (t: number) => number,
    ) => {
      const token = ++ringToken;
      const fromFill = Number(ringFill.getAttribute('r'));
      const fromStroke = Number(ringStroke.getAttribute('r'));
      const started = performance.now();
      const frame = (now: number) => {
        if (token !== ringToken) return;
        const progress = clamp((now - started) / duration, 0, 1);
        const eased = easing(progress);
        setRing(
          fromFill + (targetFill - fromFill) * eased,
          fromStroke + (targetStroke - fromStroke) * eased,
        );
        if (progress < 1) requestAnimationFrame(frame);
      };
      requestAnimationFrame(frame);
    };

    /** 인트로 전용 링 확대 — 오버슛(15.4/11.9) 후 정착하는 스프링 느낌 */
    const runRingIntro = () => {
      const token = ++ringToken;
      const started = performance.now();
      const duration = 820;
      const frame = (now: number) => {
        if (token !== ringToken) return;
        const progress = clamp((now - started) / duration, 0, 1);
        let fill: number;
        let stroke: number;
        if (progress <= 0.8) {
          const local = easeOutCubic(progress / 0.8);
          fill = 8 + (15.4 - 8) * local;
          stroke = 4.5 + (11.9 - 4.5) * local;
        } else {
          const local = easeInOutCubic((progress - 0.8) / 0.2);
          fill = 15.4 + (NORMAL_FILL_R - 15.4) * local;
          stroke = 11.9 + (NORMAL_STROKE_R - 11.9) * local;
        }
        setRing(fill, stroke);
        if (progress < 1) requestAnimationFrame(frame);
      };
      requestAnimationFrame(frame);
    };

    const animateSparkle = (
      delta: number,
      duration: number,
      easing: (t: number) => number,
      done?: () => void,
    ) => {
      const token = ++sparkleToken;
      const startDistance = currentDistance;
      const started = performance.now();
      const frame = (now: number) => {
        if (token !== sparkleToken) return;
        const progress = clamp((now - started) / duration, 0, 1);
        placeSparkle(startDistance + delta * easing(progress));
        if (progress < 1) {
          requestAnimationFrame(frame);
        } else if (done) {
          done();
        }
      };
      requestAnimationFrame(frame);
    };

    const animateGlow = (targetOpacity: number, duration = 240) => {
      const token = ++glowToken;
      const fromOpacity = Number(arrivalGlow.getAttribute('opacity'));
      const started = performance.now();
      const frame = (now: number) => {
        if (token !== glowToken) return;
        const progress = clamp((now - started) / duration, 0, 1);
        const value = fromOpacity + (targetOpacity - fromOpacity) * easeOutCubic(progress);
        arrivalGlow.setAttribute('opacity', value.toFixed(4));
        if (progress < 1) requestAnimationFrame(frame);
      };
      requestAnimationFrame(frame);
    };

    const startOrbit = () => {
      if (!introFinished || !isHovering || orbitRaf !== null || reduceMotion) return;
      ++sparkleToken; // 진행 중이던 스파클 이동을 끊고 공전이 이어받는다
      orbitLastTime = performance.now();
      const speed = pathLength / 2800; // 1바퀴 ~2.8초, 인트로와 같은 반시계방향
      const frame = (now: number) => {
        if (!isHovering || disposed) {
          orbitRaf = null;
          return;
        }
        const elapsed = Math.min(now - orbitLastTime, 40);
        orbitLastTime = now;
        placeSparkle(currentDistance + speed * elapsed);
        orbitRaf = requestAnimationFrame(frame);
      };
      orbitRaf = requestAnimationFrame(frame);
    };

    const stopOrbit = () => {
      if (orbitRaf !== null) {
        cancelAnimationFrame(orbitRaf);
        orbitRaf = null;
      }
    };

    const returnSparkleHome = (done?: () => void) => {
      stopOrbit();
      let delta = homeDistance - currentDistance;
      if (delta > pathLength / 2) delta -= pathLength;
      if (delta < -pathLength / 2) delta += pathLength;
      const ratio = Math.abs(delta) / pathLength;
      const duration = clamp(360 + ratio * 800, 360, 720);
      animateSparkle(delta, duration, easeInOutCubic, () => {
        placeSparkle(homeDistance);
        done?.();
      });
    };

    setHoverRef.current = (hovering: boolean) => {
      isHovering = hovering;
      if (!introFinished || reduceMotion) return;
      if (hovering) {
        animateGlow(0, 180);
        animateRing(HOVER_FILL_R, HOVER_STROKE_R, 360, easeOutCubic);
        startOrbit();
      } else {
        animateRing(NORMAL_FILL_R, NORMAL_STROKE_R, 520, easeOutBackSoft);
        returnSparkleHome(() => {
          if (!isHovering) animateGlow(1, 240);
        });
      }
    };

    homeDistance = nearestDistance(HOME_POINT);

    if (reduceMotion) {
      setRing(NORMAL_FILL_R, NORMAL_STROKE_R);
      placeSparkle(homeDistance);
      arrivalGlow.setAttribute('opacity', '1');
      introFinished = true;
    } else {
      const introDistance = nearestDistance(INTRO_POINT);
      setRing(8, 4.5);
      placeSparkle(introDistance);
      arrivalGlow.setAttribute('opacity', '0');

      timeouts.push(setTimeout(runRingIntro, 50));
      timeouts.push(setTimeout(() => animateGlow(1, 280), 700));
      timeouts.push(
        setTimeout(() => {
          // 좌하단 → 좌상단 → 우상단: 반시계방향
          const delta = mod(homeDistance - introDistance, pathLength);
          animateSparkle(delta, 1150, easeOutCubic, () => {
            placeSparkle(homeDistance);
            introFinished = true;
            if (isHovering) setHoverRef.current(true);
          });
        }, 140),
      );
    }

    return () => {
      disposed = true;
      // 토큰을 올리면 떠 있는 rAF 프레임들이 다음 호출에서 전부 자멸한다
      ++ringToken;
      ++sparkleToken;
      ++glowToken;
      stopOrbit();
      timeouts.forEach(clearTimeout);
      setHoverRef.current = () => {};
    };
  }, []);

  useEffect(() => {
    setHoverRef.current(hovered);
  }, [hovered]);

  return (
    <svg
      viewBox="0 0 61 61"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      role="img"
      aria-label="우주인 로고"
      // 호버 확대는 넣었다가 뺐다 — 접힌 사이드바 등에서 로고가 커지는 게 어색하다는
      // 피드백. 작은 크기 보정은 궤도선 굵기(0.1→0.9)와 호버 시 표시만으로 한다.
      className={className}
      style={{
        width: size,
        height: size,
        display: 'block',
        flexShrink: 0,
        overflow: 'visible',
      }}
    >
      <rect width="60" height="60" rx="6" fill="black" />

      {/* 우측 상단 원형 보조 그라데이션 (고정) */}
      <rect x="38" y="8" width="17" height="17" fill={url('bgGlow')} />

      {/* 궤도선 — 원본은 0.1 로 사실상 가이드였지만, 작은 로고에선 호버 시 보이도록 굵히고
          평소엔 숨긴다 (opacity 는 CSS 전환) */}
      <path
        ref={orbitPathRef}
        d="M26.8904 25.2747C31.5656 21.7491 36.1933 19.0839 39.8947 17.6243C41.7456 16.8944 43.3628 16.4672 44.6365 16.3833C45.9128 16.2992 46.831 16.5603 47.3045 17.1881C47.7777 17.816 47.7759 18.7701 47.3442 19.9738C46.9133 21.1754 46.058 22.6128 44.8473 24.1917C42.4263 27.3492 38.5915 31.0657 33.9163 34.5914C29.2409 38.1172 24.6127 40.7828 20.9112 42.2425C19.0602 42.9724 17.4431 43.3995 16.1693 43.4835C14.8933 43.5676 13.9755 43.3069 13.502 42.6794C13.0285 42.0515 13.0299 41.0969 13.4616 39.893C13.8926 38.6913 14.7478 37.254 15.9585 35.675C18.3797 32.5175 22.215 28.8006 26.8904 25.2747Z"
        stroke="white"
        strokeWidth="0.9"
        style={{ opacity: hovered ? 0.38 : 0, transition: 'opacity 0.3s' }}
      />

      {/* 스파클 도착 방사광 — 인트로 완료 후 켜지고 호버 중엔 꺼진다 */}
      <ellipse
        ref={glowRef}
        cx="37.939"
        cy="19.6898"
        rx="5.94922"
        ry="3.84292"
        transform="rotate(35.1926 37.939 19.6898)"
        fill={url('arrival')}
        opacity="0"
      />

      {/* 이동 대상: 십자 스파클 (translate 로 궤도를 따라 움직인다) */}
      <g ref={sparkleRef}>
        <g style={{ mixBlendMode: 'color-dodge' }}>
          <rect width="1.77083" height="17" transform="translate(45.6147 8)" fill="black" />
          <ellipse cx="46.5002" cy="16.5" rx="0.885417" ry="8.5" fill={url('sparkleV')} />
        </g>
        <g style={{ mixBlendMode: 'color-dodge' }}>
          <rect
            width="1.77083"
            height="17"
            transform="translate(38 17.3854) rotate(-90)"
            fill="black"
          />
          <ellipse
            cx="46.5"
            cy="16.5"
            rx="0.885416"
            ry="8.5"
            transform="rotate(-90 46.5 16.5)"
            fill={url('sparkleH')}
          />
        </g>
      </g>

      {/* 중앙 링 — 반지름만 애니메이션, stroke-width 7 유지 */}
      <g filter={url('ringShadow')}>
        <circle ref={ringFillRef} cx="30" cy="30" r="15" fill="black" />
        <circle ref={ringStrokeRef} cx="30" cy="30" r="11.5" stroke="white" strokeWidth="7" />
      </g>

      <defs>
        <filter
          id={id('ringShadow')}
          x="11.2"
          y="11.2"
          width="37.6"
          height="37.6"
          filterUnits="userSpaceOnUse"
          colorInterpolationFilters="sRGB"
        >
          <feFlood floodOpacity="0" result="BackgroundImageFix" />
          <feColorMatrix
            in="SourceAlpha"
            type="matrix"
            values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 127 0"
            result="hardAlpha"
          />
          <feOffset dx="-1" dy="1" />
          <feGaussianBlur stdDeviation="1.4" />
          <feComposite in2="hardAlpha" operator="out" />
          <feColorMatrix type="matrix" values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 1 0" />
          <feBlend mode="normal" in2="BackgroundImageFix" result="effect1_dropShadow" />
          <feBlend mode="normal" in="SourceGraphic" in2="effect1_dropShadow" result="shape" />
        </filter>

        <radialGradient
          id={id('bgGlow')}
          cx="0"
          cy="0"
          r="1"
          gradientUnits="userSpaceOnUse"
          gradientTransform="translate(46.5 16.5) rotate(90) scale(8.5)"
        >
          <stop stopColor="#141414" />
          <stop offset="1" />
        </radialGradient>

        <radialGradient
          id={id('arrival')}
          cx="0"
          cy="0"
          r="1"
          gradientUnits="userSpaceOnUse"
          gradientTransform="translate(37.939 19.6898) rotate(90) scale(3.84292 5.94922)"
        >
          <stop offset="0.168269" stopColor="white" />
          <stop offset="1" stopColor="white" stopOpacity="0" />
        </radialGradient>

        <radialGradient
          id={id('sparkleV')}
          cx="0"
          cy="0"
          r="1"
          gradientUnits="userSpaceOnUse"
          gradientTransform="translate(46.5002 16.5) rotate(90) scale(8.5 0.885417)"
        >
          <stop offset="0.0364583" stopColor="#FFF1E4" />
          <stop offset="1" stopColor="#3A71FF" stopOpacity="0" />
        </radialGradient>

        <radialGradient
          id={id('sparkleH')}
          cx="0"
          cy="0"
          r="1"
          gradientUnits="userSpaceOnUse"
          gradientTransform="translate(46.5 16.5) rotate(90) scale(8.5 0.885416)"
        >
          <stop offset="0.0364583" stopColor="#FFF1E4" />
          <stop offset="1" stopColor="#3A71FF" stopOpacity="0" />
        </radialGradient>
      </defs>
    </svg>
  );
};

export default InteractiveLogo;
