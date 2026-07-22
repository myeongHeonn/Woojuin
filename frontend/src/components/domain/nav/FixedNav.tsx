import NavItem from '@/components/ui/nav/NavItem';
import { PlanetIcon, TrashIcon } from '@/assets/icons';
import { useSideBar } from '@/stores/context/SideBarContext';

/**
 * 워크스페이스와 무관하게 항상 있는 상단 고정 항목
 * TODO: 화면이 생기면 to 를 실제 경로로 (/universe · /trash) 교체하고,
 *       active 는 NavLink 의 isActive 로 대체한다 (URL 에서 파생)
 */
const FIXED_NAV = [
  { id: 'personal', label: 'Personal Space', Icon: PlanetIcon, to: '/home' },
  { id: 'trash', label: '휴지통', Icon: TrashIcon, to: '/home' },
] as const;

const FixedNav = () => {
  const { sideBarClosed, activeId, setActiveId } = useSideBar();

  return (
    <>
      {FIXED_NAV.map(({ id, label, Icon, to }) => (
        <NavItem
          key={id}
          icon={<Icon />}
          label={label}
          to={to}
          collapsed={sideBarClosed}
          active={activeId === id}
          onClick={() => setActiveId(id)}
        />
      ))}
    </>
  );
};

export default FixedNav;
