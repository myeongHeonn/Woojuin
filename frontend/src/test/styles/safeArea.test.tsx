import { describe, it, expect, afterEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { MemoryRouter } from 'react-router-dom';
import LandingHeader from '@/components/domain/landing/LandingHeader';
import { STAGE_PT } from '@/constants/stage';

/**
 * 안전영역(노치·상태바) 반영을 지킨다.
 *
 * iOS 홈 화면 앱은 `viewport-fit=cover` + `black-translucent` 조합 때문에 페이지 좌표 0 이
 * 상태바 뒤다. 상단에 붙는 요소가 그걸 더하지 않으면 버튼이 상태바에 먹혀 눌리지 않는다.
 * 실기기에서만 재현되는 증상이라, 되돌아가도 아무도 모르는 종류의 회귀다.
 *
 * env() 는 값을 주입할 방법이 없으므로 theme.css 가 `--safe-*` 변수로 한 겹 감싸고 있다.
 * 그 변수를 덮어써서 노치 기기를 재현한다 — 이 테스트가 성립하는 이유이자, env() 를 직접
 * 쓰지 않는 이유다.
 */
const setInsets = (top: string, bottom: string) => {
  document.documentElement.style.setProperty('--safe-top', top);
  document.documentElement.style.setProperty('--safe-bottom', bottom);
};

afterEach(() => {
  document.documentElement.style.removeProperty('--safe-top');
  document.documentElement.style.removeProperty('--safe-bottom');
});

/** 클래스만 받아 계산된 값을 읽는다 — 토큰 조합이 맞는지 보려는 것이다 */
const computed = (className: string) => {
  const el = document.createElement('div');
  el.className = `absolute ${className}`;
  document.body.appendChild(el);
  const { paddingTop, paddingBottom, bottom } = getComputedStyle(el);
  el.remove();
  return { paddingTop, paddingBottom, bottom };
};

describe('안전영역 토큰', () => {
  it('안전영역이 0 이면 기존 여백 그대로다 — 안드로이드 PWA·데스크톱·사파리 탭 회귀 방지', () => {
    setInsets('0px', '0px');

    expect(computed('pt-[calc(20px+var(--safe-top))]').paddingTop).toBe('20px');
    expect(computed('pb-[calc(14px+var(--safe-bottom))]').paddingBottom).toBe('14px');
    expect(computed('bottom-above-tabbar').bottom).toBe('88px');
  });

  it('안전영역이 있으면 그만큼 더한다', () => {
    // iPhone 14 Pro 세로 — 상단 59px / 하단 34px
    setInsets('59px', '34px');

    expect(computed('pt-[calc(20px+var(--safe-top))]').paddingTop).toBe('79px');
    expect(computed('pt-[calc(24px+var(--safe-top))]').paddingTop).toBe('83px');
    expect(computed('pt-[calc(40px+var(--safe-top))]').paddingTop).toBe('99px');
    expect(computed('pb-[calc(14px+var(--safe-bottom))]').paddingBottom).toBe('48px');
  });

  it('하단 탭바를 비켜 앉는 높이도 안전영역을 따라간다', () => {
    // 지도 배지·장소 패널·검색바·휴지통이 공유하는 값 — 전에는 네 곳에 각자 적혀 있었다
    setInsets('0px', '34px');

    expect(computed('bottom-above-tabbar').bottom).toBe('122px');
    expect(computed('pb-above-tabbar').paddingBottom).toBe('122px');
  });
});

describe('StageHeader 자리 예약 — 헤더와 같이 내려가야 한다', () => {
  /**
   * 실기기에서 났던 회귀다. 헤더(absolute)에만 안전영역을 더하고 콘텐츠의 예약 여백을
   * 그대로 뒀더니, 아이폰에서 헤더가 카테고리 칩 바(관리·즐겨찾기)를 53px 덮었다.
   *
   * 헤더 높이를 추정하지 않고 **증가량이 같은지**만 본다 — 그게 겹치지 않는 조건이다.
   */
  const paddingTopAt = (cls: string, inset: string) => {
    document.documentElement.style.setProperty('--safe-top', inset);
    const el = document.createElement('div');
    el.className = cls;
    document.body.appendChild(el);
    const value = parseFloat(getComputedStyle(el).paddingTop);
    el.remove();
    return value;
  };

  it('안전영역이 늘어난 만큼 헤더와 콘텐츠가 똑같이 내려간다', () => {
    const headerClass = 'pt-[calc(20px+var(--safe-top))]';
    // STAGE_PT 의 모바일 쪽 값 — 상수를 그대로 쓰면 desktop: 변형까지 섞여 폭에 따라 갈린다
    const contentClass = 'pt-[calc(64px+var(--safe-top))]';

    const headerDelta = paddingTopAt(headerClass, '59px') - paddingTopAt(headerClass, '0px');
    const contentDelta = paddingTopAt(contentClass, '59px') - paddingTopAt(contentClass, '0px');

    expect(headerDelta).toBe(59);
    expect(contentDelta).toBe(headerDelta);
  });

  it('STAGE_PT 가 안전영역을 포함한다 — 빼먹으면 헤더가 콘텐츠를 덮는다', () => {
    expect(STAGE_PT).toContain('var(--safe-top)');
  });
});

describe('당겨서 새로고침 차단', () => {
  it('문서에 overscroll-behavior-y: contain 이 걸려 있다', () => {
    // 앱 셸이 h-dvh overflow-hidden 이라 문서 스크롤이 항상 0 이고, 크롬은 그걸 "맨 위"로
    // 보고 pull-to-refresh 를 상시 켠다 — 리스트에서 아래로 끌면 앱이 새로고침됐다
    expect(getComputedStyle(document.documentElement).overscrollBehaviorY).toBe('contain');
  });
});

describe('LandingHeader — 상단 안전영역', () => {
  const renderHeader = () =>
    render(
      <MemoryRouter>
        <LandingHeader />
      </MemoryRouter>,
    );

  it('안전영역이 0 이면 14px 그대로다', async () => {
    setInsets('0px', '0px');
    const { container } = await renderHeader();

    expect(getComputedStyle(container.querySelector('nav')!).paddingTop).toBe('14px');
  });

  it('안전영역이 있으면 로그인 버튼이 상태바 밑으로 내려간다', async () => {
    // PWA 는 cold start 마다 start_url(`/`)로 들어오므로 로그인 전 사용자는 이 헤더를
    // 홈 화면 앱에서 먼저 만난다 — 14px 만 두면 로그인 버튼이 상태바에 가린다
    setInsets('59px', '0px');
    const { container } = await renderHeader();

    const nav = container.querySelector('nav')!;
    expect(getComputedStyle(nav).paddingTop).toBe('73px');

    const login = [...nav.querySelectorAll('a')].find((a) => a.textContent === '로그인')!;
    expect(login.getBoundingClientRect().top).toBeGreaterThanOrEqual(59);
  });
});
