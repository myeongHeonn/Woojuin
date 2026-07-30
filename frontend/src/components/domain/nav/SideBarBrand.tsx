import { useState } from 'react';
import { Link } from 'react-router-dom';
import InteractiveLogo from '@/components/ui/InteractiveLogo';
import LogoMiniText from '@/components/ui/LogoMiniText';
import { classNames } from '@/utils/classNames';
import { useSideBar } from '@/stores/context/SideBarContext';

/**
 * 로고 + 사이드바 열고 닫기 버튼.
 *
 * 로고와 서비스명은 하나의 링크다 — 호버하면 로고가 반응하고(스파클 공전, InteractiveLogo
 * 참고) 클릭하면 홈으로 간다. /home 은 개인 우주로 넘겨주는 라우트라 워크스페이스 id 를
 * 몰라도 된다(FixedNav 의 폴백과 같은 경로).
 */
const SideBarBrand = () => {
  const { sideBarClosed, toggleSideBar } = useSideBar();
  // 호버 감지는 링크(로고+이름 묶음)에서 하고 로고에 내려준다 — 이름에 올려도 로고가 돈다
  const [brandHovered, setBrandHovered] = useState(false);

  return (
    <section
      className={classNames(
        'flex items-center gap-2.5',
        sideBarClosed ? 'flex-col justify-center pt-1 pb-4' : 'px-2.5 pt-1 pb-5',
      )}
    >
      <Link
        to="/home"
        aria-label="홈으로 이동"
        className="flex items-center gap-2.5 rounded-sm"
        onPointerEnter={() => setBrandHovered(true)}
        onPointerLeave={() => setBrandHovered(false)}
        onFocus={() => setBrandHovered(true)}
        onBlur={() => setBrandHovered(false)}
      >
        <InteractiveLogo size={28} hovered={brandHovered} className="rounded-sm" />
        {!sideBarClosed && <LogoMiniText text="WooJuIn" />}
      </Link>
      <button
        type="button"
        onClick={toggleSideBar}
        aria-label={sideBarClosed ? '사이드바 펼치기' : '사이드바 접기'}
        className={classNames(
          'grid place-items-center w-[26px] h-[26px] rounded-sm text-[13px] cursor-pointer',
          'border border-border text-text-3 hover:text-text-1 hover:border-accent transition-colors',
          !sideBarClosed && 'ml-auto',
        )}
      >
        {sideBarClosed ? '»' : '«'}
      </button>
    </section>
  );
};

export default SideBarBrand;
