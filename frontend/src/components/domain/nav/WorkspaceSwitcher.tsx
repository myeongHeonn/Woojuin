import { useState } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import Dropdown from '@/components/ui/Dropdown';
import CreateWorkspaceModal from '@/components/domain/nav/CreateWorkspaceModal';
import { useWorkspaces } from '@/hooks/useWorkspaces';
import { ChevronDownIcon, PlanetIcon, PlusIcon } from '@/assets/icons';
import { classNames } from '@/utils/classNames';
import { IS_INSTALLED_APP } from '@/utils/installedApp';

/**
 * 모바일 워크스페이스 전환기 — 데스크톱은 사이드바가 하지만 모바일엔 없어서,
 * 스테이지 헤더의 워크스페이스 이름을 탭하면 바로 아래로 목록이 떠 전환한다.
 * 현재 보고 있는 뷰(universe/library/map)를 유지하고, 새 워크스페이스도 여기서 만든다.
 */
const WorkspaceSwitcher = () => {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { data: workspaces = [] } = useWorkspaces();
  const navigate = useNavigate();
  const location = useLocation();
  const [createOpen, setCreateOpen] = useState(false);

  const current = workspaces.find((workspace) => workspace.id === Number(workspaceId));
  // /workspace/:id/(segment) — 전환해도 같은 뷰를 유지한다. 없으면 성좌로.
  const segment = location.pathname.split('/')[3] || 'universe';

  return (
    <>
      <Dropdown
        align="left"
        // 래퍼가 inline-flex(내용 크기)라 긴 이름이 안 줄어든다 — 폭을 제한해 … 로 잘리게
        className="w-full min-w-0"
        renderTrigger={(toggle) => (
          <button
            type="button"
            /*
             * 보이는 글자(워크스페이스 이름)를 aria-label 앞에 그대로 둔다.
             * "워크스페이스 전환"만 넣으면 화면에 보이는 이름과 접근성 이름이 어긋나,
             * 음성으로 "몽골 여행 눌러줘" 라고 해도 이 버튼이 안 잡힌다(WCAG Label in Name).
             */
            aria-label={`${current?.name ?? 'My Universe'} — 워크스페이스 전환`}
            onClick={toggle}
            className="flex w-full items-center gap-1 text-2xl font-extrabold tracking-[-0.01em] text-text-1 [&>svg]:h-5 [&>svg]:w-5 [&>svg]:text-text-3"
          >
            {/* min-w-0 이라야 flex 안에서 줄어들며 … 로 잘린다 (오른쪽 뷰바·액션과 겹침 방지) */}
            <span className="min-w-0 truncate">{current?.name ?? 'My Universe'}</span>
            <ChevronDownIcon className="shrink-0" />
          </button>
        )}
      >
        {(close) => (
          <div className="flex min-w-[220px] flex-col">
            {workspaces.map((workspace) => {
              const active = workspace.id === Number(workspaceId);
              return (
                <button
                  key={workspace.id}
                  type="button"
                  onClick={() => {
                    // 워크스페이스 전환도 뷰 전환과 같은 성격이라 설치된 앱에서는 히스토리를
                    // 쌓지 않는다 — 쌓이면 전환 뒤 뒤로가기가 앱을 닫지 않고 옛 워크스페이스로
                    // 돌아간다 (TabBar 의 같은 주석 참고)
                    navigate(`/workspace/${workspace.id}/${segment}`, {
                      replace: IS_INSTALLED_APP,
                    });
                    close();
                  }}
                  className={classNames(
                    'flex items-center gap-2.5 rounded-md px-2.5 py-2 text-left text-[13px] transition-colors hover:bg-surface-3 [&>svg]:h-4 [&>svg]:w-4',
                    active ? 'text-text-1' : 'text-text-2',
                  )}
                >
                  <PlanetIcon />
                  <span className="min-w-0 flex-1 truncate">{workspace.name}</span>
                  {active && <span className="text-xs text-accent">✓</span>}
                </button>
              );
            })}

            {/* 새 워크스페이스 — 사이드바의 New workspace 와 같은 동작 */}
            <button
              type="button"
              onClick={() => {
                setCreateOpen(true);
                close();
              }}
              className="mt-1 flex items-center gap-2.5 rounded-md px-2.5 py-2 text-left text-[13px] text-text-3 transition-colors hover:bg-surface-3 hover:text-text-1 [&>svg]:h-4 [&>svg]:w-4"
            >
              <PlusIcon />
              워크스페이스 추가
            </button>
          </div>
        )}
      </Dropdown>

      <CreateWorkspaceModal open={createOpen} onClose={() => setCreateOpen(false)} />
    </>
  );
};

export default WorkspaceSwitcher;
