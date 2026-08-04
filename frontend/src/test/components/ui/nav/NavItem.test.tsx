import { describe, it, expect } from 'vitest';
import { render } from 'vitest-browser-react';
import { MemoryRouter } from 'react-router-dom';
import NavItem from '@/components/ui/nav/NavItem';
import { PlanetIcon } from '@/assets/icons';

const wrap = (ui: React.ReactNode, path = '/') =>
  render(<MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter>);

describe('NavItem', () => {
  describe('태그 분기', () => {
    it('to 가 있으면 링크(<a>)로 렌더한다', async () => {
      // 링크여야 새 탭 열기·가운데 클릭·주소 복사가 동작한다
      const { container } = await wrap(
        <NavItem to="/home" label="휴지통" icon={<PlanetIcon />} collapsed={false} />,
      );
      const el = container.querySelector('a');
      expect(el).not.toBeNull();
      expect(el).toHaveAttribute('href', '/home');
      expect(container.querySelector('button')).toBeNull();
    });

    it('to 가 없으면 버튼으로 렌더한다', async () => {
      // Workspaces 토글처럼 "이동이 아닌 동작"은 버튼이어야 한다
      const { container } = await wrap(
        <NavItem label="Workspaces" icon={<PlanetIcon />} collapsed={false} />,
      );
      expect(container.querySelector('button')).not.toBeNull();
      expect(container.querySelector('a')).toBeNull();
    });
  });

  describe('선택 표시 — 소비처가 아니라 현재 URL 이 정한다', () => {
    /**
     * 선택 여부는 NavLink 가 붙이는 aria-current 로 본다.
     *
     * 펼친 항목에는 점을 그리지 않는다 — 오른쪽 끝은 액션(⋮) 자리이고, 선택은 배경과
     * 글자색으로 이미 드러난다. aria-current 는 장식이 바뀌어도 흔들리지 않고 스크린리더가
     * 실제로 읽는 신호라 이 규칙(경로 대조)을 검증할 기준으로 더 알맞다.
     */
    const isSelected = (c: HTMLElement) =>
      c.querySelector('a')?.getAttribute('aria-current') === 'page';
    const dotOf = (c: HTMLElement) => c.querySelector('span.bg-current');
    const item = (
      <NavItem label="몽골 여행" to="/workspace/2" icon={<PlanetIcon />} collapsed={false} />
    );

    it('현재 경로와 맞으면 선택으로 표시된다', async () => {
      const { container } = await wrap(item, '/workspace/2');
      expect(isSelected(container)).toBe(true);
    });

    it('다른 경로면 선택이 아니다', async () => {
      const { container } = await wrap(item, '/workspace/3');
      expect(isSelected(container)).toBe(false);
    });

    it('id 앞자리만 같은 경로에 반응하지 않는다', async () => {
      // /workspace/2 가 /workspace/20 에서 켜지면 워크스페이스가 늘었을 때 오작동한다
      const { container } = await wrap(item, '/workspace/20');
      expect(isSelected(container)).toBe(false);
    });

    it('하위 뷰로 들어가도 선택이 유지된다', async () => {
      // 뷰바로 성좌 → 지도를 오갈 때 사이드바 선택이 깜빡이면 안 된다
      const { container } = await wrap(item, '/workspace/2/map');
      expect(isSelected(container)).toBe(true);
    });

    it('펼친 항목에는 점을 그리지 않는다', async () => {
      const { container } = await wrap(item, '/workspace/2');
      expect(dotOf(container)).toBeNull();
    });

    it('접히면 점이 뜬다 — 라벨이 없어 글자색 단서가 사라지므로', async () => {
      const { container } = await wrap(
        <NavItem label="몽골 여행" to="/workspace/2" icon={<PlanetIcon />} collapsed />,
        '/workspace/2',
      );
      expect(dotOf(container)).not.toBeNull();
    });

    it('점은 v3.5 스펙대로 7px 원이다', async () => {
      const { container } = await wrap(
        <NavItem label="몽골 여행" to="/workspace/2" icon={<PlanetIcon />} collapsed />,
        '/workspace/2',
      );
      const rect = dotOf(container)!.getBoundingClientRect();
      expect(rect.width).toBeCloseTo(7, 1);
      expect(rect.height).toBeCloseTo(7, 1);
    });
  });

  describe('접힘 상태', () => {
    it('접히면 라벨이 안 보이고 아이콘만 남는다', async () => {
      const { container } = await wrap(
        <NavItem label="Personal Space" icon={<PlanetIcon />} collapsed />,
      );
      // 라벨 텍스트는 호버 툴팁용으로 DOM 에 남지만 화면에는 보이지 않아야 한다
      const labels = [...container.querySelectorAll('span')].filter(
        (s) => s.textContent === 'Personal Space',
      );
      expect(labels).toHaveLength(1);
      expect(getComputedStyle(labels[0]).display).toBe('none');
      expect(container.querySelector('svg')).not.toBeNull();
    });

    it('펼치면 라벨이 보인다', async () => {
      const { container } = await wrap(
        <NavItem label="Personal Space" icon={<PlanetIcon />} collapsed={false} />,
      );
      const label = [...container.querySelectorAll('span')].find(
        (s) => s.textContent === 'Personal Space',
      )!;
      expect(getComputedStyle(label).display).not.toBe('none');
    });

    it('접히면 라벨을 title 로 노출해 호버 시 알 수 있게 한다', async () => {
      const { container } = await wrap(
        <NavItem label="Personal Space" icon={<PlanetIcon />} collapsed />,
      );
      expect(container.firstElementChild).toHaveAttribute('title', 'Personal Space');
    });
  });
});
