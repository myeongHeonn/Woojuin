import { useUser } from '@/hooks/useUser';
import { classNames } from '@/utils/classNames';
import { getSpacemanImage } from '@/utils/getSpacemanImage';
import { useSideBar } from '@/stores/context/SideBarContext';

/** 하단 유저 푸터 — 접힘 상태에서는 아바타만 남는다 */
const SideBarUser = () => {
  const { sideBarClosed } = useSideBar();

  // 서버 상태
  const { nickName, email, avatarColor } = useUser();

  return (
    <section
      className={classNames(
        'flex items-center gap-[11px] border-t border-border-soft py-3',
        sideBarClosed ? 'justify-center' : 'px-2.5',
      )}
    >
      <div className="w-9 h-9 rounded-full bg-surface-3 shrink-0 overflow-hidden">
        <img
          src={getSpacemanImage(avatarColor)}
          alt={`${nickName} 프로필`}
          className="w-full h-full object-cover block"
        />
      </div>
      {!sideBarClosed && (
        <>
          <div className="min-w-0">
            <div className="text-sm font-bold text-text-1 truncate">{nickName}</div>
            <div className="text-xs text-text-3 truncate">{email}</div>
          </div>
          <button
            type="button"
            aria-label="계정 메뉴"
            className="ml-auto text-text-3 hover:text-text-1 cursor-pointer"
          >
            ···
          </button>
        </>
      )}
    </section>
  );
};

export default SideBarUser;
