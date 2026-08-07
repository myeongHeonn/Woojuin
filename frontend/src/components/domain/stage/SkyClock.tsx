import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAtomValue } from 'jotai';
import { solarElevationAtom, themeAtom } from '@/stores/themeAtoms';
import { timeZoneAbbreviation } from '@/utils/timeZone';

/**
 * 현재시간 테마에서 뷰바 아래에 붙는 시각 표시.
 *
 * 이 모드는 화면이 지금 몇 시인지에 따라 바뀌는데, 정작 그 '지금'이 화면에 없으면 왜 이 색인지
 * 알 수 없다. 시각과 태양 고도를 같이 적어 두면 하늘색의 근거가 드러난다 — 관측 장비의
 * 계기판에 가깝다.
 *
 * 초까지 보여 준다. 분 단위면 멈춘 것처럼 보이고, 무엇보다 하늘 계산은 1분에 한 번만 도는지라
 * (themeRuntime) 그 값을 그대로 쓰면 표시가 최대 1분까지 뒤처진다. 그래서 표시용 시계는
 * 여기서 따로 1초마다 돌린다 — 하늘색까지 매초 다시 계산할 이유는 없다.
 */
const SECOND_MS = 1000;

const pad = (value: number) => String(value).padStart(2, '0');

const SkyClock = () => {
  const { pathname } = useLocation();
  const preference = useAtomValue(themeAtom);
  const elevation = useAtomValue(solarElevationAtom);
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    if (preference !== 'auto') return;
    const timer = setInterval(() => setNow(new Date()), SECOND_MS);
    return () => clearInterval(timer);
  }, [preference]);

  // 성좌뷰에만 띄운다. 밝은 글자로 고정했기 때문인데(index.css 의 .woojuin-on-sky),
  // 하늘이 없는 대시보드·지도는 라이트에서 배경이 밝아 같은 글자가 1.0:1 로 사라진다.
  // 애초에 태양 고도는 하늘을 설명하는 값이라 하늘이 없는 화면에 있을 이유도 없다.
  if (preference !== 'auto' || !pathname.endsWith('/universe')) return null;

  const clock = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
  // 빼기 기호(U+2212)를 쓴다 — 하이픈보다 폭이 넓어 숫자와 높이가 맞는다
  const altitude = `${elevation < 0 ? '−' : '+'}${Math.abs(elevation).toFixed(1)}°`;

  return (
    <div
      // 색·글로우는 index.css 의 .woojuin-on-sky 에 있다(왜 테마 토큰을 안 쓰는지도 거기에).
      // 계기판처럼 읽히려면 자릿수가 흔들리지 않아야 한다 — tabular-nums 가 그 역할이다.
      className="woojuin-on-sky pointer-events-none select-none pr-0.5 text-right tabular-nums"
      aria-hidden="true"
    >
      <div className="flex items-baseline justify-end gap-1.5">
        <span className="text-[15px] font-semibold leading-none tracking-[0.04em]">{clock}</span>
        {/* 색은 상속받고 투명도만 낮춘다 — 별도 색을 주면 하늘 위에서 다시 대비를 따져야 한다 */}
        <span className="text-[10px] font-bold leading-none tracking-[0.12em] opacity-70">
          {timeZoneAbbreviation(now)}
        </span>
      </div>
      <div className="mt-1.5 text-[10px] font-semibold leading-none tracking-[0.1em] opacity-70">
        태양 고도 {altitude}
      </div>
    </div>
  );
};

export default SkyClock;
