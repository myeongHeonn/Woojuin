import { Link } from 'react-router-dom';
import { useUser } from '@/hooks/useUser';
import { classNames } from '@/utils/classNames';
import { getSpacemanImage } from '@/utils/getSpacemanImage';
import { useSideBar } from '@/stores/context/SideBarContext';
import SideBarAiUsage from './SideBarAiUsage';

/** 사이드바 하단 사용자 영역. 누르면 마이페이지로 이동한다. */
const SideBarUser = () => {
  const { sideBarClosed } = useSideBar();
  const { nickName, email, avatarColor } = useUser();

  return (
    <section className={classNames('pt-3', sideBarClosed ? '' : 'px-2.5')}>
      {!sideBarClosed && <SideBarAiUsage />}

      <div className="border-t border-border-soft pt-3">
        <Link
          to="/my"
          aria-label="마이페이지로 이동"
          className={classNames(
            'flex w-full items-center gap-[11px] rounded-md py-1 text-left transition-colors hover:bg-surface-2',
            sideBarClosed ? 'justify-center px-0' : 'px-0',
          )}
        >
          <span className="h-9 w-9 shrink-0 overflow-hidden rounded-full bg-surface-3">
            <img
              src={getSpacemanImage(avatarColor)}
              alt={`${nickName} 프로필`}
              className="block h-full w-full object-cover"
            />
          </span>
          {!sideBarClosed && (
            <span className="min-w-0">
              <span className="block truncate text-sm font-bold text-text-1">{nickName}</span>
              <span className="block truncate text-xs text-text-3">{email}</span>
            </span>
          )}
        </Link>
      </div>
    </section>
  );
};

export default SideBarUser;
