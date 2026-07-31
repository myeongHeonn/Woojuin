import { describe, it, expect } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import { MemoryRouter, useLocation } from 'react-router-dom';
import LibrarySearch from '@/components/domain/library/LibrarySearch';

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
});
