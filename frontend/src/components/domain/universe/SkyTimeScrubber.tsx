import { useAtom, useAtomValue } from 'jotai';
import {
  effectiveNowAtom,
  solarElevationAtom,
  themeAtom,
  timeOverrideAtom,
} from '@/stores/themeAtoms';

/**
 * 개발용 시간 슬라이더 — '현재시간' 테마의 하늘색을 아무 시각에서나 확인한다.
 *
 * 실제 시각만 쓰면 노을을 보려고 해 질 때까지 기다려야 한다. 하루 두 번뿐이고 몇 분이면
 * 지나가서 색을 손볼 때마다 그 창을 노려야 한다.
 *
 * **개발 빌드에서만 렌더된다.** import.meta.env.DEV 는 프로덕션에서 상수 false 라 이 컴포넌트가
 * 통째로 번들에서 빠진다.
 */
const MINUTES_IN_DAY = 24 * 60;

const formatClock = (minuteOfDay: number) => {
  const hour = Math.floor(minuteOfDay / 60);
  const minute = minuteOfDay % 60;
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
};

const SkyTimeScrubber = () => {
  const preference = useAtomValue(themeAtom);
  const [override, setOverride] = useAtom(timeOverrideAtom);
  const effectiveNow = useAtomValue(effectiveNowAtom);
  const elevation = useAtomValue(solarElevationAtom);

  // 하늘색이 안 바뀌는 모드에서는 슬라이더가 아무것도 못 한다
  if (!import.meta.env.DEV || preference !== 'auto') return null;

  const shown = new Date(effectiveNow);
  const minuteOfDay = shown.getHours() * 60 + shown.getMinutes();

  const moveTo = (minute: number) => {
    // 날짜는 오늘로 두고 시각만 옮긴다 — 계절(태양 적위)까지 흔들면 무엇 때문에 색이 바뀐 건지
    // 알 수 없다
    const at = new Date();
    at.setHours(Math.floor(minute / 60), minute % 60, 0, 0);
    setOverride(at.getTime());
  };

  return (
    <div className="pointer-events-auto absolute bottom-above-tabbar left-4 z-40 w-[248px] rounded-[14px] border border-border bg-sidebar/85 px-3 py-2.5 backdrop-blur-md desktop:bottom-5 desktop:left-5">
      <div className="flex items-baseline justify-between gap-2">
        <span className="text-[11px] font-bold tracking-[0.08em] text-text-3">하늘 시각 (dev)</span>
        <span className="tabular-nums text-[13px] font-semibold text-text-1">
          {formatClock(minuteOfDay)}
        </span>
      </div>

      <input
        type="range"
        min={0}
        max={MINUTES_IN_DAY - 1}
        step={5}
        value={minuteOfDay}
        onChange={(event) => moveTo(Number(event.target.value))}
        aria-label="하늘 시각"
        className="mt-2 w-full accent-accent"
      />

      <div className="mt-1 flex items-center justify-between text-[11px] text-text-3">
        <span className="tabular-nums">태양 고도 {elevation.toFixed(1)}°</span>
        <button
          type="button"
          onClick={() => setOverride(null)}
          disabled={override === null}
          className="font-semibold text-accent disabled:text-text-3"
        >
          {override === null ? '실제 시각' : '실제 시각으로'}
        </button>
      </div>
    </div>
  );
};

export default SkyTimeScrubber;
