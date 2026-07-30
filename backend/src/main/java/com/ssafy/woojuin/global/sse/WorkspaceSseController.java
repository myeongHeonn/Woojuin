package com.ssafy.woojuin.global.sse;

import com.ssafy.woojuin.domain.workspace.exception.WorkspaceMemberRequiredException;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import com.ssafy.woojuin.global.security.aop.AuthenticatedUser;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 워크스페이스 변경 신호 스트림(SSE).
 *
 * <p>클라이언트는 워크스페이스에 들어갈 때 한 번 연결해두고, 아이템 처리 완료나 팀원의
 * 변경이 생기면 신호를 받아 캐시를 무효화한다. 폴링을 대신하는 자리다.
 *
 * <p>인증은 다른 API와 똑같이 Authorization 헤더를 쓴다 — 브라우저 표준 EventSource는
 * 헤더를 못 싣지만, 프론트가 fetch 기반 클라이언트를 쓰므로 별도 인증 경로가 필요 없다.
 * 남의 워크스페이스를 훔쳐볼 수 없도록 멤버 여부를 반드시 확인한다.
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class WorkspaceSseController {

    private final WorkspaceSseRegistry sseRegistry;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final CurrentUserResolver currentUserResolver;

    public WorkspaceSseController(WorkspaceSseRegistry sseRegistry,
            WorkspaceMemberRepository workspaceMemberRepository,
            CurrentUserResolver currentUserResolver) {
        this.sseRegistry = sseRegistry;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * 이 워크스페이스의 변경 신호를 구독한다.
     *
     * <p>응답을 ApiResponse 로 감싸지 않는다 — SSE 는 한 번 응답하고 끝나는 게 아니라
     * 연결을 열어둔 채 이벤트를 계속 흘려보내는 스트림이라 공통 포맷이 성립하지 않는다.
     */
    @AuthenticatedUser
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable Long workspaceId) {
        Long userId = currentUserResolver.resolveUserId();
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceMemberRequiredException(workspaceId));

        return sseRegistry.subscribe(workspaceId);
    }
}
