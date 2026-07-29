import NavItem from '@/components/ui/nav/NavItem';
import { PlanetIcon } from '@/assets/icons';
import { useSideBar } from '@/stores/context/SideBarContext';
import { useSpaces } from '@/hooks/useSpaces';

/**
 * 워크스페이스 목록 위에 항상 있는 고정 항목.
 *
 * Personal Space 도 결국 워크스페이스라 경로·뷰 네 개가 팀과 똑같다.
 * 다만 공유가 없고 늘 맨 위에 있어야 해서 목록에 섞지 않고 여기서 따로 그린다
 * (목록 쪽은 WorkspaceNav 가 useSpaces 의 teams 를 받아 이미 빠져 있다).
 *
 * 휴지통은 워크스페이스별이라 여기(전역)가 아니라 스테이지 헤더의 휴지통 아이콘으로 들어간다.
 */
const FixedNav = () => {
  const { sideBarClosed } = useSideBar();
  const { personalSpaceId } = useSpaces();

  return (
    <NavItem
      icon={<PlanetIcon />}
      label="Personal Space"
      // 목록 로드 전엔 id 를 모른다 — /home 이 로드 후 개인 우주로 넘겨준다
      to={personalSpaceId ? `/workspace/${personalSpaceId}` : '/home'}
      collapsed={sideBarClosed}
    />
  );
};

export default FixedNav;
