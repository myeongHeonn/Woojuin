import { useAtom } from 'jotai';
import { MoonIcon, SunIcon } from '@/assets/icons';
import { classNames } from '@/utils/classNames';
import { themeAtom } from '@/stores/themeAtoms';

/**
 * 테마 전환 버튼 — 스테이지 헤더에 둔다.
 *
 * 설정 화면이 아니라 여기인 이유: 라이트 전환의 볼거리가 **스테이지 자체**다(성좌뷰는 밤하늘이
 * 낮 하늘로 1.4초에 걸쳐 바뀌고, 지도는 베이스맵이 갈린다). 설정에서 켜고 돌아오면 그 전환은
 * 이미 끝나 있어 결과 화면만 보게 된다.
 *
 * 뷰바와 달리 모바일에서도 보인다 — 모바일은 뷰 전환을 하단 탭바가 대신하지만 테마는 대신할
 * 곳이 없다.
 *
 * 아이콘은 **지금 하늘**을 가리킨다(라이트=해, 다크=달). 무엇으로 바뀔지는 툴팁과 aria-label 이
 * 말해 준다. 화면은 낮인데 버튼에 달이 떠 있으면 그림과 배경이 어긋나 그게 먼저 눈에 걸린다.
 */
const ICON_TRANSITION =
  'absolute h-[17px] w-[17px] transition-[opacity,transform] duration-500 ease-[cubic-bezier(.2,.8,.2,1)] motion-reduce:transition-none';

const ThemeToggle = () => {
  const [theme, setTheme] = useAtom(themeAtom);
  const light = theme === 'light';
  const label = light ? '다크 모드로 전환' : '라이트 모드로 전환';

  return (
    <button
      type="button"
      role="switch"
      aria-checked={light}
      aria-label={label}
      title={label}
      onClick={() => setTheme(light ? 'dark' : 'light')}
      // 뷰바와 같은 껍데기(테두리·반투명 배경·블러)라 나란히 놓았을 때 한 벌로 보인다
      className="relative grid h-[38px] w-[40px] shrink-0 place-items-center rounded-[11px] border border-border bg-sidebar/60 text-text-2 backdrop-blur-md transition-colors hover:text-text-1"
    >
      <SunIcon
        aria-hidden="true"
        className={classNames(
          ICON_TRANSITION,
          light ? 'rotate-0 scale-100 opacity-100' : 'rotate-[80deg] scale-[.55] opacity-0',
        )}
      />
      <MoonIcon
        aria-hidden="true"
        className={classNames(
          ICON_TRANSITION,
          light ? '-rotate-[70deg] scale-[.55] opacity-0' : 'rotate-0 scale-100 opacity-100',
        )}
      />
    </button>
  );
};

export default ThemeToggle;
