import { useEffect, useState } from 'react';
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
  const preference = useAtomValue(themeAtom);
  const elevation = useAtomValue(solarElevationAtom);
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    if (preference !== 'auto') return;
    const timer = setInterval(() => setNow(new Date()), SECOND_MS);
    return () => clearInterval(timer);
  }, [preference]);

  if (preference !== 'auto') return null;

  const clock = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
  // 빼기 기호(U+2212)를 쓴다 — 하이픈보다 폭이 넓어 숫자와 높이가 맞는다
  const altitude = `${elevation < 0 ? '−' : '+'}${Math.abs(elevation).toFixed(1)}°`;

  return (
    <div
      /*
       * 자체 배경을 깐다. 이 시계는 **하늘 위**에 얹히는데, 하필 화면 최상단이라 하늘
       * 그라데이션에서 가장 진한 부분과 겹친다. 배경 없이 글자만 두면 낮에 대비가 1.3:1 까지
       * 떨어져 읽히지 않는다(글자는 밝은 UI 라 어둡고, 그 아래 하늘은 짙은 파랑이다).
       *
       * 불투명도가 뷰바(60%)보다 높은 85% 인 이유: 60% 로는 하늘이 비쳐 들어와 라벨(text-2)이
       * 다시 2.4:1 로 내려앉는다. 85% 면 어느 하늘색에서도 5.5:1 이상이 나온다.
       *
       * 계기판처럼 읽히려면 자릿수가 흔들리지 않아야 한다 — tabular-nums 가 그 역할이다.
       */
      className="pointer-events-none select-none rounded-[11px] border border-border bg-sidebar/85 px-2.5 py-1.5 text-right tabular-nums backdrop-blur-md"
      aria-hidden="true"
    >
      <div className="flex items-baseline justify-end gap-1.5">
        <span className="text-[15px] font-semibold leading-none tracking-[0.04em] text-text-1">
          {clock}
        </span>
        <span className="text-[10px] font-bold leading-none tracking-[0.12em] text-text-2">
          {timeZoneAbbreviation(now)}
        </span>
      </div>
      <div className="mt-1.5 text-[10px] font-semibold leading-none tracking-[0.1em] text-text-2">
        태양 고도 {altitude}
      </div>
    </div>
  );
};

export default SkyClock;
