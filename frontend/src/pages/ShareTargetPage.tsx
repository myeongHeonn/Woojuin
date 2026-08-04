import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import axios from 'axios';
import BrandMark from '@/components/ui/BrandMark';
import Spinner from '@/components/ui/Spinner';
import SpacePicker from '@/components/domain/shareTarget/SpacePicker';
import { useWorkspaces } from '@/hooks/useWorkspaces';
import { saveImage, saveMemo, saveUrl } from '@/services/items';
import { SHARE_FILES_FLAG } from '@/constants/shareTarget';
import { clearSharedFiles, readSharedFiles } from '@/utils/sharedFiles';
import { accessTokenAtom, postLoginRedirectAtom } from '@/stores/authAtoms';
import { lastShareSpaceIdAtom } from '@/stores/shareAtoms';
import { parseSharedContent } from '@/utils/sharedContent';
import { CheckIcon } from '@/assets/icons';

/**
 * OS 공유 시트에서 '우주인'을 골랐을 때 들어오는 화면 (FR-013).
 *
 * manifest 의 share_target.action 이 여기다. 사이드바 없는 단독 화면이고 AuthLayout 밖이라
 * 로그인 처리도 이 화면이 직접 한다.
 *
 * 흐름은 짧게 유지한다 — 공유는 "던져 놓고 원래 앱으로 돌아가기"라, 위치를 고르고 저장 한 번
 * 누르면 끝나야 한다. 분석(크롤·AI)이 끝나길 기다리지 않는다(서버가 PROCESSING 을 바로 준다).
 */
const ShareTargetPage = () => {
  const [params] = useSearchParams();
  const location = useLocation();
  const accessToken = useAtomValue(accessTokenAtom);
  const setPostLoginRedirect = useSetAtom(postLoginRedirectAtom);
  const [lastSpaceId, setLastSpaceId] = useAtom(lastShareSpaceIdAtom);
  const [spaceId, setSpaceId] = useState<number | null>(null);

  const shared = useMemo(
    () =>
      parseSharedContent({
        url: params.get('url'),
        text: params.get('text'),
        title: params.get('title'),
      }),
    [params],
  );

  /*
   * 사진 공유는 파일이 주소에 실리지 않는다 — 서비스워커가 캐시에 넣고 `?shared=files` 로
   * 보내므로(sw.ts) 여기서 꺼낸다. null 은 "아직 읽는 중"이라 빈 공유와 구분해야 한다.
   */
  const isFileShare = params.get('shared') === SHARE_FILES_FLAG;
  const [files, setFiles] = useState<File[] | null>(isFileShare ? null : []);

  useEffect(() => {
    if (!isFileShare) return;
    let alive = true;
    readSharedFiles()
      .then((loaded) => alive && setFiles(loaded))
      .catch(() => alive && setFiles([]));
    return () => {
      alive = false;
    };
  }, [isFileShare]);

  const isReadingFiles = files === null;
  const sharedFiles = files ?? [];

  /**
   * 로그인 전에 공유가 들어오면 **쿼리까지 담아** 둔다 — 여기 담긴 게 공유 내용 전부다.
   * 이걸 빠뜨리면 로그인 뒤 빈 화면으로 돌아와 사용자가 공유를 다시 해야 한다.
   * (sessionStorage 라 구글 OAuth 의 전체 페이지 이동을 넘어서도 남는다)
   */
  useEffect(() => {
    if (accessToken) return;
    setPostLoginRedirect(location.pathname + location.search);
  }, [accessToken, location.pathname, location.search, setPostLoginRedirect]);

  // 로그인 전에는 목록을 받으러 가지 않는다 — 401 을 맞고 토큰 갱신까지 헛돌 뿐이다
  const {
    data: workspaces = [],
    isLoading,
    isError,
    refetch,
  } = useWorkspaces({ enabled: Boolean(accessToken) });

  // 기억한 위치 → 개인 스페이스 → 목록 첫 번째. 기억한 id 가 목록에 없으면(탈퇴·추방) 버린다.
  useEffect(() => {
    if (spaceId !== null || workspaces.length === 0) return;
    const remembered = workspaces.find((workspace) => workspace.id === lastSpaceId);
    const fallback = workspaces.find((workspace) => workspace.type === 'PERSONAL') ?? workspaces[0];
    setSpaceId((remembered ?? fallback).id);
  }, [workspaces, lastSpaceId, spaceId]);

  /**
   * 사진을 몇 장까지 올렸는지. 실패해서 다시 시도할 때 **이미 올린 것을 건너뛴다** —
   * 처음부터 다시 보내면 같은 사진이 두 번 저장된다.
   */
  const savedFileCount = useRef(0);

  const save = useMutation({
    mutationFn: async () => {
      if (spaceId === null) throw new Error('저장할 곳을 고르지 않았습니다');

      if (sharedFiles.length > 0) {
        // 순차로 올린다 — 한 장이 실패하면 거기서 멈추고, 다시 시도가 그 자리부터 이어진다.
        // (동시에 올리면 어디까지 성공했는지 알 수 없어 재시도가 중복 저장이 된다)
        for (let index = savedFileCount.current; index < sharedFiles.length; index += 1) {
          await saveImage(spaceId, sharedFiles[index]);
          savedFileCount.current = index + 1;
        }
        // 저장에 성공한 뒤에만 비운다 — 실패한 채로 비우면 다시 시도할 대상이 사라진다
        await clearSharedFiles();
        return;
      }

      if (shared.kind === 'url') {
        await saveUrl(spaceId, shared.url);
        return;
      }
      if (shared.kind === 'memo') {
        await saveMemo(spaceId, shared.content);
        return;
      }
      throw new Error('저장할 내용이 없습니다');
    },
    onSuccess: () => {
      if (spaceId !== null) setLastSpaceId(spaceId);
    },
  });

  const errorMessage = axios.isAxiosError(save.error)
    ? (save.error.response?.data?.message ??
      // 응답이 없으면 서버가 거부한 게 아니라 닿지 못한 것이다 — 공유 내용은 화면에 그대로
      // 남아 있으니 다시 시도하면 된다는 걸 알려 준다 (S15P11C105-455 와 같은 구분)
      (save.error.response
        ? '저장에 실패했어요'
        : '지금 서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요'))
    : save.error
      ? save.error.message
      : null;

  // 캐시에서 파일을 꺼내는 동안은 "내용 없음"으로 단정하지 않는다
  if (isReadingFiles) {
    return (
      <Shell>
        <div className="flex items-center gap-2 text-sm text-text-3">
          <Spinner className="h-4 w-4" />
          공유된 사진을 읽는 중…
        </div>
      </Shell>
    );
  }

  if (sharedFiles.length === 0 && shared.kind === 'empty') {
    return (
      <Shell>
        <p className="break-keep text-sm leading-relaxed text-text-2">
          공유된 내용을 읽지 못했어요. 링크·텍스트·사진을 공유해 주세요.
        </p>
        <Link to="/home" className={primaryActionClass}>
          우주인 열기
        </Link>
      </Shell>
    );
  }

  if (!accessToken) {
    return (
      <Shell>
        <p className="break-keep text-sm leading-relaxed text-text-2">
          로그인하면 이 내용을 바로 저장할 수 있어요. 공유한 내용은 그대로 두고 기다립니다.
        </p>
        <SharedPreview shared={shared} files={sharedFiles} />
        {/* replace — 로그인은 지나가는 관문이라 히스토리에 남기지 않는다. 로그인 뒤에는
            postLoginRedirect 가 이 화면(쿼리 포함)으로 되돌려 준다 */}
        <Link to="/login" replace className={primaryActionClass}>
          로그인하고 저장하기
        </Link>
      </Shell>
    );
  }

  if (save.isSuccess) {
    return (
      <Shell>
        <div className="flex items-center gap-2 text-text-1 [&>svg]:h-5 [&>svg]:w-5">
          <CheckIcon className="text-accent" />
          <p className="text-base font-bold">
            {sharedFiles.length > 1 ? `사진 ${sharedFiles.length}장을 저장했어요` : '저장했어요'}
          </p>
        </div>
        <p className="break-keep text-sm leading-relaxed text-text-2">
          우주인이 내용을 정리하는 중이에요. 앱에서 바로 확인할 수 있어요.
        </p>
        <Link to={`/workspace/${spaceId}/library`} className={primaryActionClass}>
          저장한 곳 보기
        </Link>
        {/* 이어서 더 공유할 수 있게 — 공유 시트로 돌아가는 건 OS 가 하므로 안내만 한다 */}
        <p className="text-center text-xs text-text-3">
          계속 공유하려면 원래 앱으로 돌아가면 돼요.
        </p>
      </Shell>
    );
  }

  return (
    <Shell>
      <SharedPreview shared={shared} files={sharedFiles} />

      <div className="grid gap-1.5">
        {/* form 컨트롤이 아니라 버튼+패널 조합이라 label 이 아니라 div 다 — 이름은 선택기의
            aria-label 이 준다(빈 라벨이 되면 접근성 경고가 난다) */}
        <span className="text-label font-semibold text-text-2">저장할 곳</span>
        {isLoading ? (
          <div className="flex h-11 items-center gap-2 rounded-lg border border-border bg-surface-2 px-3 text-sm text-text-3">
            <Spinner className="h-4 w-4" />
            불러오는 중…
          </div>
        ) : isError ? (
          <div className="grid gap-2">
            <p className="text-sm text-danger">저장할 곳 목록을 받지 못했어요.</p>
            <button type="button" onClick={() => void refetch()} className={secondaryActionClass}>
              다시 불러오기
            </button>
          </div>
        ) : (
          <SpacePicker
            workspaces={workspaces}
            selectedId={spaceId}
            disabled={save.isPending}
            onSelect={setSpaceId}
          />
        )}
      </div>

      <button
        type="button"
        onClick={() => save.mutate()}
        disabled={save.isPending || spaceId === null}
        className={primaryActionClass}
      >
        {save.isPending ? (
          <>
            <Spinner className="h-4 w-4" />
            저장하는 중…
          </>
        ) : (
          '저장하기'
        )}
      </button>

      {errorMessage && (
        <div className="grid gap-2">
          <p className="break-keep text-sm text-danger">{errorMessage}</p>
          {/* 공유 내용은 화면에 남아 있으므로 다시 누르면 그대로 저장된다 */}
          <button
            type="button"
            onClick={() => save.mutate()}
            disabled={save.isPending}
            className={secondaryActionClass}
          >
            다시 시도
          </button>
        </div>
      )}
    </Shell>
  );
};

/** 공유로 들어온 내용 미리보기 — 무엇이 저장될지 먼저 보여 준다 */
const SharedPreview = ({
  shared,
  files,
}: {
  shared: ReturnType<typeof parseSharedContent>;
  files: File[];
}) => {
  if (files.length > 0) return <SharedPhotoPreview files={files} />;
  if (shared.kind === 'empty') return null;
  return (
    <div className="grid gap-1 rounded-lg border border-border bg-surface p-3">
      <span className="text-label font-semibold text-text-3">
        {shared.kind === 'url' ? '링크' : '메모'}
      </span>
      <p className="line-clamp-3 break-all text-[13px] leading-relaxed text-text-2">
        {shared.kind === 'url' ? shared.url : shared.content}
      </p>
    </div>
  );
};

/**
 * 공유된 사진 미리보기 — 무엇을 저장하는지 보여 준다.
 *
 * 사진은 이름만 적어 두면 맞게 왔는지 확인할 수 없으니 실제 그림을 보여 준다.
 * objectURL 은 만든 만큼 해제해야 한다 — 안 하면 이 화면을 여닫을 때마다 메모리에 쌓인다.
 */
const SharedPhotoPreview = ({ files }: { files: File[] }) => {
  const [urls, setUrls] = useState<string[]>([]);

  useEffect(() => {
    const created = files.map((file) => URL.createObjectURL(file));
    setUrls(created);
    return () => created.forEach((url) => URL.revokeObjectURL(url));
  }, [files]);

  return (
    <div className="grid gap-2 rounded-lg border border-border bg-surface p-3">
      <span className="text-label font-semibold text-text-3">사진 {files.length}장</span>
      <div className="flex gap-2 overflow-x-auto scrollbar-none">
        {urls.map((url, index) => (
          <img
            key={url}
            src={url}
            alt={`공유된 사진 ${index + 1}`}
            className="h-20 w-20 shrink-0 rounded-md object-cover"
          />
        ))}
      </div>
    </div>
  );
};

/**
 * 공유 화면 껍데기 — 사이드바·탭바가 없는 단독 화면이다.
 * 상단 안전영역을 반영한다(설치된 앱에서 상태바 아래로 내려와야 한다).
 */
const Shell = ({ children }: { children: React.ReactNode }) => (
  <div className="min-h-dvh bg-space px-5 pb-10 pt-[calc(24px+var(--safe-top))]">
    <div className="mx-auto flex w-full max-w-sm flex-col gap-4">
      <BrandMark size={28} />
      <h1 className="text-base font-bold text-text-1">우주인에 저장</h1>
      {children}
    </div>
  </div>
);

const primaryActionClass =
  'flex h-11 items-center justify-center gap-2 rounded-lg bg-accent px-4 text-sm font-bold text-white transition-colors hover:bg-accent-hover disabled:opacity-60';

const secondaryActionClass =
  'flex h-11 items-center justify-center gap-2 rounded-lg border border-border bg-surface-2 px-4 text-sm font-semibold text-text-1 transition-colors hover:bg-surface-3 disabled:opacity-60';

export default ShareTargetPage;
