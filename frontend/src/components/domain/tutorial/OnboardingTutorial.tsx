import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocation, useMatch, useNavigate } from 'react-router-dom';
import {
  completeTutorial,
  fetchMyProfile,
  type TutorialType,
  type UserProfile,
} from '@/services/auth';
import { useSpaces } from '@/hooks/useSpaces';

interface TutorialStep {
  selector: string;
  fallbackSelector?: string;
  actionView?: 'library' | 'map';
  label: string;
  title: string;
  description: string;
}

const personalSteps: TutorialStep[] = [
  {
    selector: '[data-tutorial="personal"]',
    fallbackSelector: '[data-tutorial="workspace-title"]',
    label: '개인 스페이스',
    title: '로그인하면 나만의 우주에서 시작해요',
    description: '개인 스페이스는 내 정보를 모아 관리하는\n기본 공간이에요.',
  },
  {
    selector: '[data-tutorial="save"]',
    label: '빠른 저장',
    title: '새로운 정보를 바로 저장해요',
    description: '＋ 버튼으로 링크·사진·메모를 저장할 수 있어요.',
  },
  {
    selector: '[data-tutorial-view="universe"]',
    label: '우주뷰',
    title: '저장한 정보들이 연결돼요',
    description:
      '저장한 정보는 별로 표현되고, 관련된 정보는 서로 이어져요.\n별을 누르면 저장한 내용을 바로 확인할 수 있어요.',
  },
  {
    selector: '[data-tutorial="search"]',
    label: '통합 검색',
    title: '필요한 정보를 빠르게 찾아요',
    description: '검색창에서는 단어 기반으로, AI 모드에서는 대화하듯이 검색할 수 있어요.',
  },
  {
    selector: '[data-tutorial-view="library"]',
    actionView: 'library',
    label: '대시보드뷰',
    title: '대시보드뷰를 눌러보세요',
    description: '대시보드뷰에서 저장한 정보를 한눈에 확인할 수 있어요.',
  },
  {
    selector: '[data-tutorial-page-content="library"]',
    fallbackSelector: '[data-tutorial-view="library"]',
    label: '대시보드뷰',
    title: '저장한 정보를 한눈에 확인해요',
    description: '링크·사진·메모를 한곳에서 확인하고 수정하거나 삭제할 수\n있어요.',
  },
  {
    selector: '[data-tutorial-view="map"]',
    actionView: 'map',
    label: '지도뷰',
    title: '지도뷰를 눌러보세요',
    description: '지도뷰에서 위치가 있는 정보를 확인할 수 있어요.',
  },
  {
    selector: '[data-tutorial-page-content="map"]',
    fallbackSelector: '[data-tutorial-view="map"]',
    label: '지도뷰',
    title: '장소 정보는 지도에서 확인해요',
    description: '위치 정보가 있는 링크와 사진은 지도에도 표시돼요.',
  },
  {
    selector: '[data-tutorial="workspaces"]',
    fallbackSelector: '[data-tutorial="workspace-title"]',
    label: '워크스페이스',
    title: '함께할 때는 워크스페이스를 만들어요',
    description:
      '공유 워크스페이스에는 멤버를 초대할 수 있어요.\n함께 정보를 모아 관리해요.',
  },
];

const sharedWorkspaceSteps: TutorialStep[] = [
  {
    selector: '[data-tutorial="workspace-title"]',
    fallbackSelector: '[data-tutorial="workspaces"]',
    label: '공유 워크스페이스',
    title: '저장한 정보를 멤버들과 함께 관리해요',
    description:
      '모든 멤버가 워크스페이스의 정보와 카테고리를 함께\n추가·수정·삭제할 수 있어요.',
  },
  {
    selector: '[data-tutorial="workspace-members"]',
    fallbackSelector: '[data-tutorial="workspace-share"]',
    label: '사용자',
    title: '참여 중인 멤버와 역할을 확인해요',
    description:
      '상단의 프로필을 누르면 참여 중인 멤버와 OWNER·MEMBER 역할을 확인할 수 있어요.',
  },
  {
    selector: '[data-tutorial="workspace-share"]',
    label: '초대와 관리',
    title: '공유 버튼에서 멤버를 초대해요',
    description:
      'OWNER는 초대 링크를 만들고 멤버를 내보낼 수 있어요.\nMEMBER는 워크스페이스에서 나갈 수 있어요.',
  },
  {
    selector: '[data-tutorial-view="universe"]',
    fallbackSelector: '[data-tutorial="workspace-title"]',
    label: '사용 방법',
    title: '나머지 기능은 개인 스페이스와 동일해요',
    description:
      '정보 저장과 우주뷰·대시보드뷰·지도뷰, 검색 기능을 같은 방법으로 사용할 수 있어요.',
  },
];

interface HighlightRect {
  top: number;
  left: number;
  width: number;
  height: number;
}

const OnboardingTutorial = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: profile, isSuccess: isProfileLoaded } = useQuery({
    queryKey: ['user', 'me'],
    queryFn: fetchMyProfile,
  });
  const completeMutation = useMutation({
    mutationFn: completeTutorial,
    onSuccess: (updatedProfile) => {
      queryClient.setQueryData<UserProfile>(['user', 'me'], updatedProfile);
    },
  });
  const [dismissedForCurrentVisit, setDismissedForCurrentVisit] = useState(false);
  const [stepIndex, setStepIndex] = useState(0);
  const [rect, setRect] = useState<HighlightRect | null>(null);
  const match = useMatch('/workspace/:workspaceId/*');
  const { personalSpaceId } = useSpaces();

  const workspaceId = match?.params.workspaceId ? Number(match.params.workspaceId) : undefined;
  const isPersonalSpace = personalSpaceId !== undefined && workspaceId === personalSpaceId;
  const isSharedWorkspace =
    personalSpaceId !== undefined && workspaceId !== undefined && workspaceId !== personalSpaceId;
  const isOpen =
    isProfileLoaded &&
    !dismissedForCurrentVisit &&
    ((isPersonalSpace && !profile.personalTutorialCompleted) ||
      (isSharedWorkspace && !profile.sharedWorkspaceTutorialCompleted));
  const steps = isSharedWorkspace ? sharedWorkspaceSteps : personalSteps;
  const step = steps[stepIndex];

  useEffect(() => {
    if (!isOpen || stepIndex !== 0 || workspaceId === undefined) return;
    if (!location.pathname.endsWith('/universe')) {
      navigate(`/workspace/${workspaceId}/universe`, { replace: true });
    }
  }, [isOpen, location.pathname, navigate, stepIndex, workspaceId]);

  useEffect(() => {
    if (!isOpen || !step.actionView) return;
    if (location.pathname.endsWith(`/${step.actionView}`)) {
      setStepIndex((index) => index + 1);
    }
  }, [isOpen, location.pathname, step.actionView]);

  useEffect(() => {
    setDismissedForCurrentVisit(false);
    setStepIndex(0);
  }, [workspaceId]);

  useEffect(() => {
    if (!isOpen) return;

    const updateRect = () => {
      const findVisible = (selector: string) =>
        [...document.querySelectorAll<HTMLElement>(selector)].find((element) => {
          const bounds = element.getBoundingClientRect();
          return bounds.width > 0 && bounds.height > 0;
        });
      const target =
        findVisible(step.selector) ??
        (step.fallbackSelector ? findVisible(step.fallbackSelector) : undefined);

      if (!target) {
        setRect(null);
        return;
      }

      const bounds = target.getBoundingClientRect();
      const padding = 8;
      setRect({
        top: Math.max(8, bounds.top - padding),
        left: Math.max(8, bounds.left - padding),
        width: Math.min(window.innerWidth - 16, bounds.width + padding * 2),
        height: bounds.height + padding * 2,
      });
    };

    updateRect();
    window.addEventListener('resize', updateRect);
    const observer = new ResizeObserver(updateRect);
    observer.observe(document.body);

    return () => {
      window.removeEventListener('resize', updateRect);
      observer.disconnect();
    };
  }, [isOpen, step]);

  const bubbleStyle = useMemo(() => {
    const width = Math.min(380, window.innerWidth - 32);
    if (!rect) {
      return {
        width,
        left: Math.max(16, (window.innerWidth - width) / 2),
        top: Math.max(80, (window.innerHeight - 260) / 2),
        direction: 'none' as const,
      };
    }

    const below = rect.top + rect.height + 18;
    const placeBelow = below + 230 < window.innerHeight;
    const left = Math.min(
      window.innerWidth - width - 16,
      Math.max(16, rect.left + rect.width / 2 - width / 2),
    );

    return {
      width,
      left,
      top: placeBelow ? below : Math.max(16, rect.top - 218),
      direction: placeBelow ? ('top' as const) : ('bottom' as const),
    };
  }, [rect]);

  if (!isOpen) return null;

  const finish = (moveToUniverse = false) => {
    const tutorialType: TutorialType = isSharedWorkspace ? 'SHARED_WORKSPACE' : 'PERSONAL';
    completeMutation.mutate(tutorialType);
    setDismissedForCurrentVisit(true);
    setStepIndex(0);
    if (moveToUniverse && workspaceId !== undefined) {
      navigate(`/workspace/${workspaceId}/universe`);
    }
  };

  const overlayPanels = rect
    ? [
        { top: 0, left: 0, right: 0, height: rect.top },
        { top: rect.top + rect.height, left: 0, right: 0, bottom: 0 },
        { top: rect.top, left: 0, width: rect.left, height: rect.height },
        {
          top: rect.top,
          left: rect.left + rect.width,
          right: 0,
          height: rect.height,
        },
      ]
    : [];

  return (
    <div className="pointer-events-none fixed inset-0 z-[100]" role="dialog" aria-modal="true">
      {overlayPanels.map((style, index) => (
        <div
          key={index}
          className={`pointer-events-auto fixed ${
            step.actionView ? 'bg-black/25 backdrop-blur-[1px]' : 'bg-black/55'
          }`}
          style={style}
          aria-hidden="true"
        />
      ))}

      {rect && (
        <div
          className="pointer-events-none fixed z-10 rounded-lg border-2 border-accent shadow-[0_0_18px_rgba(124,108,240,.55)]"
          style={rect}
        />
      )}
      {!rect && (
        <div className="pointer-events-auto absolute inset-0 bg-black/70" />
      )}

      {step.actionView && rect && (
        <div
          className="pointer-events-none fixed z-20 flex -translate-x-1/2 flex-col items-center gap-1 whitespace-nowrap text-center"
          style={{
            left: rect.left + rect.width / 2,
            top: rect.top < window.innerHeight / 2 ? rect.top + rect.height + 14 : rect.top - 64,
          }}
        >
          {rect.top >= window.innerHeight / 2 && (
            <span className="animate-bounce text-2xl leading-none text-accent" aria-hidden="true">
              ↓
            </span>
          )}
          <span className="rounded-full border border-accent/45 bg-surface/95 px-4 py-2 text-sm font-extrabold text-text-1 shadow-float">
            {step.title}
          </span>
          {rect.top < window.innerHeight / 2 && (
            <span className="animate-bounce text-2xl leading-none text-accent" aria-hidden="true">
              ↑
            </span>
          )}
        </div>
      )}

      {!step.actionView && (
        <div
          className="pointer-events-auto fixed z-20 rounded-lg border border-border bg-surface p-5 text-left shadow-modal"
          style={{ width: bubbleStyle.width, left: bubbleStyle.left, top: bubbleStyle.top }}
        >
          {bubbleStyle.direction !== 'none' && (
            <span
              className={`absolute left-1/2 h-0 w-0 -translate-x-1/2 border-x-[10px] border-x-transparent ${
                bubbleStyle.direction === 'top'
                  ? '-top-[10px] border-b-[10px] border-b-surface'
                  : '-bottom-[10px] border-t-[10px] border-t-surface'
              }`}
            />
          )}

          <div className="flex items-start gap-3">
            <div className="min-w-0">
              <p className="text-xs font-bold text-accent">{step.label}</p>
              <h2 className="mt-1 text-lg font-extrabold">{step.title}</h2>
            </div>
            <button
              type="button"
              onClick={() => finish()}
              className="ml-auto shrink-0 whitespace-nowrap text-xs text-text-3 hover:text-text-1"
            >
              건너뛰기
            </button>
          </div>

          <p className="mt-3 whitespace-pre-line break-keep text-sm leading-6 text-text-2">
            {step.description}
          </p>

          <div className="mt-5 flex items-center">
            <span className="text-xs font-semibold text-text-3">
              {stepIndex + 1} / {steps.length}
            </span>
            <div className="ml-auto flex">
              <button
                type="button"
                disabled={stepIndex === 0}
                onClick={() => setStepIndex((index) => index - 1)}
                className="rounded-l-md border border-border px-3 py-2 text-xs font-semibold text-text-2 enabled:hover:bg-surface-2 disabled:opacity-30"
              >
                이전
              </button>
              <button
                type="button"
                onClick={() =>
                  stepIndex === steps.length - 1
                    ? finish(true)
                    : setStepIndex((index) => index + 1)
                }
                className="rounded-r-md bg-accent px-4 py-2 text-xs font-semibold text-white hover:bg-accent-hover"
              >
                {stepIndex === steps.length - 1 ? '시작하기' : '다음'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default OnboardingTutorial;
