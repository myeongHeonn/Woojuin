import { useAtom } from 'jotai';
import { ClockIcon, MoonIcon, SunIcon } from '@/assets/icons';
import { classNames } from '@/utils/classNames';
import { themeAtom, type ThemePreference } from '@/stores/themeAtoms';

/**
 * 테마 전환 버튼 — 스테이지 헤더에 둔다.
 *
 * 설정 화면이 아니라 여기인 이유: 전환의 볼거리가 **스테이지 자체**다(성좌뷰는 밤하늘이
 * 낮 하늘로 바뀌고, 지도는 베이스맵이 갈린다). 설정에서 켜고 돌아오면 그 전환은 이미 끝나
 * 있어 결과 화면만 보게 된다.
 *
 * 뷰바와 달리 모바일에서도 보인다 — 모바일은 뷰 전환을 하단 탭바가 대신하지만 테마는
 * 대신할 곳이 없다.
 *
 * 세 상태를 버튼 하나로 돌린다. 스테이지 헤더는 캔버스를 가리는 자리라 폭을 더 쓰기 어렵고,
 * 선택지가 셋뿐이라 한 바퀴가 짧다. 지금 무엇이고 다음이 무엇인지는 툴팁이 말해 준다.
 */
const ORDER: ThemePreference[] = ['light', 'dark', 'auto'];

const LABEL: Record<ThemePreference, string> = {
  dark: '밤',
  light: '낮',
  auto: '현재 시간',
};

const ICON_TRANSITION =
  'absolute h-[17px] w-[17px] transition-[opacity,transform] duration-500 ease-[cubic-bezier(.2,.8,.2,1)] motion-reduce:transition-none';

/** 지금 상태의 아이콘만 보이고 나머지는 접혀 있다 */
const iconState = (active: boolean) =>
  active ? 'rotate-0 scale-100 opacity-100' : 'rotate-[70deg] scale-[.55] opacity-0';

const ThemeToggle = () => {
  const [preference, setPreference] = useAtom(themeAtom);
  const next = ORDER[(ORDER.indexOf(preference) + 1) % ORDER.length];
  const label = `화면: ${LABEL[preference]} — 눌러서 ${LABEL[next]}(으)로`;

  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={() => setPreference(next)}
      // 뷰바와 같은 껍데기(테두리·반투명 배경·블러)라 나란히 놓았을 때 한 벌로 보인다
      className="relative grid h-[38px] w-[40px] shrink-0 place-items-center rounded-[11px] border border-border bg-sidebar/60 text-text-2 backdrop-blur-md transition-colors hover:text-text-1"
    >
      <MoonIcon
        aria-hidden="true"
        className={classNames(ICON_TRANSITION, iconState(preference === 'dark'))}
      />
      <SunIcon
        aria-hidden="true"
        className={classNames(ICON_TRANSITION, iconState(preference === 'light'))}
      />
      <ClockIcon
        aria-hidden="true"
        className={classNames(ICON_TRANSITION, iconState(preference === 'auto'))}
      />
    </button>
  );
};

export default ThemeToggle;
