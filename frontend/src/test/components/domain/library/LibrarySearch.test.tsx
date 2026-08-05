import { describe, it, expect } from 'vitest';
import { render } from 'vitest-browser-react';
import { page, userEvent } from 'vitest/browser';
import { MemoryRouter, useLocation } from 'react-router-dom';
import LibrarySearch from '@/components/domain/library/LibrarySearch';
import { STAGE_PX } from '@/constants/stage';
import { classNames } from '@/utils/classNames';

/** 현재 URL 쿼리를 DOM 에 노출해 네비게이션 결과를 확인한다 */
function Loc() {
  const { search } = useLocation();
  return <span data-testid="loc">{search}</span>;
}
// location.search 는 한글이 URL 인코딩돼 오므로 파싱해서 값으로 비교한다
const params = (c: HTMLElement) =>
  new URLSearchParams(c.querySelector('[data-testid="loc"]')?.textContent ?? '');
const rawSearch = (c: HTMLElement) => c.querySelector('[data-testid="loc"]')?.textContent;

const renderAt = (entry: string) =>
  render(
    <MemoryRouter initialEntries={[entry]}>
      <LibrarySearch />
      <Loc />
    </MemoryRouter>,
  );

describe('LibrarySearch', () => {
  it('검색어를 입력하고 제출하면 ?q 로 이동한다', async () => {
    const { container } = await renderAt('/');
    await userEvent.fill(container.querySelector('input[aria-label="검색어"]')!, '파스타');
    await userEvent.keyboard('{Enter}');
    await expect.poll(() => params(container).get('q')).toBe('파스타');
    expect(params(container).get('ai')).toBeNull();
  });

  it('AI 토글을 켜면 ?ai=1 도 붙는다', async () => {
    const { container } = await renderAt('/');
    await userEvent.click(container.querySelector('button[aria-label="AI 모드"]')!);
    await userEvent.fill(container.querySelector('input[aria-label="검색어"]')!, '을지로');
    await userEvent.keyboard('{Enter}');
    await expect.poll(() => params(container).get('q')).toBe('을지로');
    expect(params(container).get('ai')).toBe('1');
  });

  // AiModeHint 는 토글 상태와 무관하게 항상 보인다 — 자리가 없어지면 검색창이
  // 위로 움직여 거슬리기 때문(AiModeHint.tsx 주석 참고)
  it('AI 모드를 켜도 권유 문구가 그대로 남는다', async () => {
    const { container } = await renderAt('/');
    expect(container.textContent).toContain('AI 모드로 바꿔보세요');

    await userEvent.click(container.querySelector('button[aria-label="AI 모드"]')!);
    await expect.poll(() => container.textContent).toContain('AI 모드로 바꿔보세요');
  });

  it('검색 상태에선 입력창에 검색어가 채워지고, 다 지우면 전체로 돌아간다', async () => {
    const { container } = await renderAt('/?q=파스타');
    const input = container.querySelector('input[aria-label="검색어"]') as HTMLInputElement;
    expect(input.value).toBe('파스타');

    // 입력을 다 지우면 ?q 가 빠져 전체 목록으로 돌아간다
    await userEvent.clear(input);
    await expect.poll(() => rawSearch(container)).toBe('');
  });

  /**
   * 부모(LibraryPage)가 flex-col 이라 ml-auto 만 주면 가로 stretch 가 깨져 내용 크기로
   * 줄어든다 — 좁은 화면에서 왼쪽에 100px 넘는 빈 공간이 생기고 검색창이 오른쪽 끝에
   * 붙어 잘린 것처럼 보였다. 모바일에서는 좌우 여백이 같아야 한다.
   */
  it('모바일에서 좌우 여백이 같다', async () => {
    await page.viewport(375, 812);
    const { container } = await render(
      <MemoryRouter>
        {/* LibraryPage 의 컨테이너 조건(px-5 + flex-col)을 재현한다 */}
        <div className={classNames('flex h-full w-full flex-col overflow-hidden', STAGE_PX)}>
          <LibrarySearch />
        </div>
      </MemoryRouter>,
    );

    const parent = container.querySelector('div.flex.h-full') as HTMLElement;
    const form = container.querySelector('form') as HTMLElement;
    const p = parent.getBoundingClientRect();
    const f = form.getBoundingClientRect();

    expect(f.left - p.left).toBeCloseTo(p.right - f.right, 0);
    expect(f.right).toBeLessThanOrEqual(375); // 뷰포트 밖으로 안 넘친다

    await page.viewport(1280, 800);
  });
});
