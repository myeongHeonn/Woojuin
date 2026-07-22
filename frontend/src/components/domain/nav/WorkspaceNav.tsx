import { useState } from 'react';
import NavItem from '@/components/ui/nav/NavItem';
import { PlanetIcon, WorkspacesIcon } from '@/assets/icons';
import { useUser } from '@/hooks/useUser';
import { classNames } from '@/utils/classNames';
import { useSideBar } from '@/stores/context/SideBarContext';

/** SPACE 라벨 + 워크스페이스 목록 (접기 가능) */
const WorkspaceNav = () => {
  const { sideBarClosed, activeId, setActiveId } = useSideBar();

  // 목록 펼침은 이 영역 안에서만 쓰이는 상태
  const [listOpen, setListOpen] = useState(true);

  // 서버 상태
  const { workSpaces } = useUser();

  const handleNewWorkspace = () => {
    // TODO: 워크스페이스 생성 모달 열기
  };

  return (
    <>
      {!sideBarClosed && (
        <div className="px-3.5 pt-5 pb-2 text-[11px] font-bold tracking-[0.16em] text-text-3">
          SPACE
        </div>
      )}

      <NavItem
        icon={<WorkspacesIcon />}
        label="Workspaces"
        collapsed={sideBarClosed}
        onClick={() => setListOpen((o) => !o)}
        trailing={
          !sideBarClosed && (
            <span
              className={classNames(
                'ml-auto text-[11px] transition-transform duration-200',
                !listOpen && 'rotate-180',
              )}
            >
              ⌃
            </span>
          )
        }
      />

      {listOpen && (
        /* 들여쓰기는 목록의 책임 — 컨테이너 패딩이라 자식 폭이 자동으로 좁아진다 */
        <div className={classNames(!sideBarClosed && 'pl-1')}>
          {workSpaces.map((ws) => (
            <NavItem
              key={ws.id}
              icon={<PlanetIcon />}
              label={ws.name}
              /* TODO: 화면이 생기면 `/universe/${ws.id}` 등 실제 경로로 교체 */
              to="/home"
              collapsed={sideBarClosed}
              active={activeId === ws.id}
              onClick={() => setActiveId(ws.id)}
            />
          ))}
          <NavItem
            icon={<span className="text-[17px] leading-none">＋</span>}
            label="New workspace"
            muted
            collapsed={sideBarClosed}
            onClick={handleNewWorkspace}
          />
        </div>
      )}
    </>
  );
};

export default WorkspaceNav;
