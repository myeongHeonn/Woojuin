import { useParams, useNavigate } from 'react-router-dom';
import { useAtomValue, useSetAtom } from 'jotai';
import axios from 'axios';
import { accessTokenAtom, postLoginRedirectAtom } from '@/stores/authAtoms';
import type { ApiResponse } from '@/services/client';
import { useInvitation, useAcceptInvitation } from '@/hooks/useInvitation';
import Spinner from '@/components/ui/Spinner';

/**
 * 초대 수락 화면 — 공유 링크(/invite/:code)로 들어온다.
 * 로그인 전에도 워크스페이스 이름을 미리 보여주고, 참여는 로그인 후에만 된다.
 * (사이드바 없는 단독 라우트 — /share-target 과 같은 계층)
 */
const InvitePage = () => {
  const { code = '' } = useParams<{ code: string }>();
  const navigate = useNavigate();
  const token = useAtomValue(accessTokenAtom);
  const setPostLoginRedirect = useSetAtom(postLoginRedirectAtom);
  const { data: invite, isLoading, isError } = useInvitation(code);
  const accept = useAcceptInvitation(code);

  const handleAccept = () => {
    if (!token) {
      // /invite/:code는 AuthLayout 밖의 public 라우트라 그쪽의 리다이렉트 캡처를 안 탄다 —
      // 여기서 직접 저장해야 로그인 후 이 초대 페이지로 돌아온다.
      setPostLoginRedirect(`/invite/${code}`);
      navigate('/login');
      return;
    }
    accept.mutate(undefined, {
      onSuccess: (ws) => navigate(`/workspace/${ws.id}/universe`),
      onError: (err) => {
        // 이미 멤버면 백엔드가 400 "이미 가입" — 실패로 막지 말고 그 워크스페이스로 보낸다
        const message = axios.isAxiosError(err)
          ? (err.response?.data as ApiResponse<unknown> | undefined)?.message
          : undefined;
        if (invite && message?.includes('이미 가입')) {
          navigate(`/workspace/${invite.workspaceId}/universe`);
        }
      },
    });
  };

  return (
    <div className="grid min-h-screen place-items-center bg-space px-4">
      <div className="w-full max-w-sm rounded-xl border border-border bg-surface p-6 text-center shadow-modal">
        {isLoading ? (
          <Spinner className="mx-auto h-6 w-6" />
        ) : isError || !invite ? (
          <>
            <p className="text-base font-semibold text-text-1">유효하지 않은 초대예요</p>
            <p className="mt-1 text-sm text-text-3">링크가 만료됐거나 잘못됐어요.</p>
            <button
              type="button"
              onClick={() => navigate('/home')}
              className="mt-5 w-full rounded-lg border border-border py-2 text-[13px] text-text-2 hover:text-text-1"
            >
              홈으로
            </button>
          </>
        ) : (
          <>
            <p className="text-sm text-text-3">워크스페이스 초대</p>
            <h1 className="mt-1 text-lg font-bold text-text-1">{invite.workspaceName}</h1>
            <p className="mt-1 text-sm text-text-3">여기에 참여할까요?</p>

            {accept.isError && (
              <p className="mt-3 text-xs text-danger">참여하지 못했어요. 다시 시도해 주세요.</p>
            )}

            <button
              type="button"
              onClick={handleAccept}
              disabled={accept.isPending}
              className="mt-5 w-full rounded-lg bg-accent py-2 text-[13px] font-semibold text-white transition-colors hover:bg-accent-hover disabled:opacity-60"
            >
              {token ? '참여하기' : '로그인하고 참여하기'}
            </button>
          </>
        )}
      </div>
    </div>
  );
};

export default InvitePage;
