import NavItem from '@/components/ui/nav/NavItem';
import { PlanetIcon, TrashIcon } from '@/assets/icons';
import { useSideBar } from '@/stores/context/SideBarContext';
import { useSpaces } from '@/hooks/useSpaces';

/**
 * 워크스페이스 목록 위에 항상 있는 고정 항목.
 *
 * Personal Space 도 결국 워크스페이스라 경로·뷰 네 개가 팀과 똑같다.
 * 다만 공유가 없고 늘 맨 위에 있어야 해서 목록에 섞지 않고 여기서 따로 그린다
 * (목록 쪽은 WorkspaceNav 가 useSpaces 의 teams 를 받아 이미 빠져 있다).
 */
const FixedNav = () => {
  const { sideBarClosed } = useSideBar();
  const { personalSpaceId } = useSpaces();

  return (
    <>
      <NavItem
        icon={<PlanetIcon />}
        label="Personal Space"
        to={`/workspace/${personalSpaceId}`}
        collapsed={sideBarClosed}
      />

      {/* TODO: /trash 화면이 생기면 to 를 연결한다 */}
      <NavItem icon={<TrashIcon />} label="휴지통" collapsed={sideBarClosed} />
    </>
  );
};

export default FixedNav;
