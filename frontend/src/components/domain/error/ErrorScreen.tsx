import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import spacemanFloating from '@/assets/spacemans/spaceman-floating.png';
import { ArrowLeftIcon } from '@/assets/icons';
import ErrorCodeMark from '@/components/domain/error/ErrorCodeMark';
import StarField from '@/components/domain/error/StarField';
import { errorSecondaryActionClass } from '@/components/domain/error/errorActionStyles';
import BrandMark from '@/components/ui/BrandMark';
import { useGoBack } from '@/hooks/useGoBack';
import './errorScreen.css';

interface ErrorScreenProps {
  /** 표시할 상태 코드. 없으면 코드 없이 로고 링만 보여 준다 */
  code?: string;
  title: string;
  description: ReactNode;
  /** 버튼들 — 첫 번째를 주 동작으로 둔다 */
  actions: ReactNode;
  /**
   * 원인 요약. 개발 중 디버깅용이라 운영 빌드에서는 넘기지 않는다
   * (사용자에게는 의미 없고, 스택·내부 경로가 드러날 수 있다).
   */
  detail?: string;
}

/**
 * 에러 화면의 공통 껍데기 — 404 와 예상치 못한 오류가 같은 얼굴을 쓴다.
 *
 * 배경이 앱의 `bg-space`(#0e1017)가 아니라 **완전한 검정**인 이유:
 *  - 아무 빛도 없는 우주로 보여야 한다는 디자인 결정. 성운(accent) 글로우도 그래서 뺐다
 *  - 로고의 십자 스파클은 원래 검정 배경 기준으로 그려진 크기라, 배경이 검을 때 원본
 *    비율(링 지름의 0.57배)이 그대로 맞는다(LogoRing 의 SPARKLE_SCALE 참고)
 *
 * 상단 브랜드는 장식이 아니라 **탈출구**다 — 라우팅이 깨져 버튼이 안 먹는 최악의 경우에도
 * 링크 하나는 남는다.
 */
const ErrorScreen = ({ code, title, description, actions, detail }: ErrorScreenProps) => {
  const goBack = useGoBack('/');

  return (
    <div className="relative grid min-h-dvh place-items-center overflow-hidden bg-black px-6 py-16">
      <StarField />

      <div className="relative flex flex-col items-center text-center">
        <Link
          to="/"
          aria-label="우주인 홈으로"
          className="rounded-md outline-offset-4 transition-opacity hover:opacity-80"
        >
          <BrandMark size={26} />
        </Link>

        {/*
          작게 두고 여백을 넓게 둔다 — 우주인이 크게 나오면 캐릭터 일러스트 화면이 되고,
          작게 떠 있으면 텅 빈 공간이 주인공이 된다(디자인 의도).

          `spaceman-floating.png` 는 원본 `spaceman-floating-2.png`(1254×1254 / 856KB)를
          288px 로 줄인 것(68KB)이다. 80px 로 쓰는 그림에 856KB 를 받게 할 수 없다 —
          에러 화면은 이미 뭔가 잘못된 상황에서 뜨므로 특히 가벼워야 한다.
          그림을 바꿀 일이 생기면 원본을 갈아 끼우고 같은 비율로 다시 줄이면 된다.
        */}
        <img
          src={spacemanFloating}
          alt=""
          aria-hidden
          draggable={false}
          className="error-spaceman mt-16 h-20 w-auto select-none opacity-85"
        />

        <div className="mt-12">
          <ErrorCodeMark code={code} />
        </div>

        {/*
          break-keep(word-break: keep-all) — 한국어는 기본 규칙으로 어절 중간에서 잘린다.
          실제로 "홈에서"가 "홈에 / 서"로 갈렸다. 낱말 단위로만 넘기게 한다.
        */}
        <h1 className="mt-7 break-keep text-xl font-extrabold tracking-tight text-text-1 desktop:text-2xl">
          {title}
        </h1>
        <p className="mt-2.5 max-w-md break-keep text-body leading-relaxed text-text-2">
          {description}
        </p>

        {detail && (
          <p className="mt-4 max-w-md break-words rounded-md border border-border-soft bg-surface/70 px-3 py-2 text-left text-[12px] leading-relaxed text-text-3">
            {detail}
          </p>
        )}

        <div className="mt-8 flex flex-wrap items-center justify-center gap-2.5">
          {actions}
          {/*
            뒤로가기는 페이지가 아니라 여기서 붙인다 — 두 화면에서 항상 주 동작 **오른쪽**
            같은 자리에 오게 하려고. 히스토리가 없는 진입(공유 링크·북마크·주소 직접
            입력)이면 useGoBack 이 홈으로 폴백하므로 막히는 경우가 없다.
          */}
          <button type="button" onClick={goBack} className={errorSecondaryActionClass}>
            <ArrowLeftIcon />
            뒤로 가기
          </button>
        </div>
      </div>
    </div>
  );
};

export default ErrorScreen;
