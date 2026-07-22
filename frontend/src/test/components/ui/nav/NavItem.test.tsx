import { describe, it, expect } from 'vitest';
import { render } from 'vitest-browser-react';
import { MemoryRouter } from 'react-router-dom';
import NavItem from '@/components/ui/nav/NavItem';
import { PlanetIcon } from '@/assets/icons';

const wrap = (ui: React.ReactNode) => render(<MemoryRouter>{ui}</MemoryRouter>);

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

  describe('선택 표시', () => {
    const dotOf = (c: HTMLElement) => c.querySelector('span.bg-current');

    it('active 면 보라 점이 뜬다', async () => {
      const { container } = await wrap(
        <NavItem label="몽골 여행" icon={<PlanetIcon />} collapsed={false} active />,
      );
      expect(dotOf(container)).not.toBeNull();
    });

    it('active 가 아니면 점이 없다', async () => {
      const { container } = await wrap(
        <NavItem label="몽골 여행" icon={<PlanetIcon />} collapsed={false} />,
      );
      expect(dotOf(container)).toBeNull();
    });

    it('점은 v3.5 스펙대로 7px 원이다', async () => {
      const { container } = await wrap(
        <NavItem label="몽골 여행" icon={<PlanetIcon />} collapsed={false} active />,
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
