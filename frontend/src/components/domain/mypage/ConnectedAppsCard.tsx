import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import ConfirmModal from '@/components/ui/ConfirmModal';
import ChatIntegrationCard from '@/components/domain/mypage/ChatIntegrationCard';
import {
  disconnectChatConnection,
  fetchChatConnections,
  type ChatConnection,
  type ChatPlatform,
} from '@/services/integrations';
import { formatRelativeDate } from '@/utils/formatDate';

const PLATFORM_LABEL: Record<ChatPlatform, string> = {
  DISCORD: 'Discord',
  MATTERMOST: 'Mattermost',
};

interface ConnectedAppsCardProps {
  onError: (message: string) => void;
}

const ConnectedAppsCard = ({ onError }: ConnectedAppsCardProps) => {
  const queryClient = useQueryClient();
  const [pendingDisconnect, setPendingDisconnect] = useState<ChatConnection | null>(null);

  // 기기 목록과 같은 이유로 캐시하지 않는다 — 오래된 목록으로 엉뚱한 연동을 끊으면 안 된다.
  const {
    data: connections,
    isPending,
    isError,
    refetch,
  } = useQuery({
    queryKey: ['integrations', 'connections'],
    queryFn: fetchChatConnections,
    staleTime: 0,
    gcTime: 0,
    refetchOnMount: 'always',
  });

  const disconnectMutation = useMutation({
    mutationFn: (connection: ChatConnection) => disconnectChatConnection(connection.id),
    onSuccess: () => {
      setPendingDisconnect(null);
      void queryClient.invalidateQueries({ queryKey: ['integrations', 'connections'] });
    },
    onError: () => {
      setPendingDisconnect(null);
      onError('연동을 해제하지 못했습니다. 다시 시도해 주세요.');
    },
  });

  return (
    // 상자 없는 섹션 — 기기 목록과 같은 문법(라벨 + 헤어라인 리스트)
    <section aria-labelledby="connected-apps-title" className="mb-10">
      <h2 id="connected-apps-title" className="px-2 text-xs font-bold tracking-[0.1em] text-text-3">
        연결된 앱
      </h2>

      {isPending && (
        <p className="mt-3 px-2 text-[13px] text-text-3">연동 목록을 불러오는 중입니다…</p>
      )}

      {isError && (
        <div className="mt-3 flex items-center justify-between gap-3 px-2">
          <p className="text-[13px] text-text-2">연동 목록을 불러오지 못했습니다.</p>
          <button
            type="button"
            onClick={() => refetch()}
            className="shrink-0 rounded-lg px-3 py-1.5 text-xs font-bold text-accent hover:bg-surface-2"
          >
            다시 시도
          </button>
        </div>
      )}

      {connections && connections.length === 0 && (
        <p className="mt-3 px-2 text-[13px] leading-relaxed text-text-3">
          연결된 채팅 앱이 없습니다. 아래에서 Mattermost·Discord를 연결하면 채팅에서 바로 저장할 수
          있습니다.
        </p>
      )}

      {connections && connections.length > 0 && (
        <ul className="mt-1">
          {connections.map((connection) => (
            <li
              key={connection.id}
              className="flex items-center gap-3 border-b border-border-soft px-2 py-3.5 last:border-b-0"
            >
              <div className="min-w-0 flex-1">
                <div className="text-sm font-semibold text-text-1">
                  {PLATFORM_LABEL[connection.platform] ?? connection.platform}
                </div>
                <div className="mt-0.5 truncate text-xs text-text-3">
                  {formatRelativeDate(connection.connectedAt) || '—'} 연결
                  {connection.defaultWorkspaceName && ` · 기본: ${connection.defaultWorkspaceName}`}
                </div>
              </div>
              <button
                type="button"
                disabled={disconnectMutation.isPending}
                onClick={() => setPendingDisconnect(connection)}
                className="shrink-0 rounded-lg px-2.5 py-1.5 text-xs font-bold text-text-3 hover:bg-surface-2 hover:text-text-1 disabled:opacity-50"
              >
                연결 해제
              </button>
            </li>
          ))}
        </ul>
      )}

      {/* 연결하는 길 — 링크 코드 발급·Discord 설치·연동 안내 모달이 이 안에 있다 */}
      <div className="mt-1">
        <ChatIntegrationCard
          onError={() => onError('연결 코드를 발급하지 못했습니다. 다시 시도해 주세요.')}
        />
      </div>

      <ConfirmModal
        open={pendingDisconnect !== null}
        title={`${pendingDisconnect ? (PLATFORM_LABEL[pendingDisconnect.platform] ?? pendingDisconnect.platform) : ''} 연동을 해제할까요?`}
        description="해제하면 그 채팅 앱에서 더 이상 저장할 수 없습니다. 다시 쓰려면 연결 코드를 새로 발급해 연결해야 합니다."
        confirmLabel={disconnectMutation.isPending ? '처리 중…' : '연결 해제'}
        onCancel={() => setPendingDisconnect(null)}
        onConfirm={() => {
          if (pendingDisconnect && !disconnectMutation.isPending) {
            disconnectMutation.mutate(pendingDisconnect);
          }
        }}
      />
    </section>
  );
};

export default ConnectedAppsCard;
