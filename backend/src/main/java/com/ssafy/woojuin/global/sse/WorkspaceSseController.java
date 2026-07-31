package com.ssafy.woojuin.global.sse;

import com.ssafy.woojuin.domain.workspace.exception.WorkspaceMemberRequiredException;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import com.ssafy.woojuin.global.common.ApiResponse;
import com.ssafy.woojuin.global.security.aop.AuthenticatedUser;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
    public SseEmitter subscribe(@PathVariable Long workspaceId, HttpServletResponse response) {
        Long userId = currentUserResolver.resolveUserId();
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceMemberRequiredException(workspaceId));

        // nginx가 이 응답을 버퍼에 모아두지 않게 한다. 없으면 이벤트가 버퍼가 찰 때까지
        // 클라이언트에 안 내려가 실시간이 아니게 된다(로컬 개발에선 프록시가 없어 멀쩡해 보인다).
        response.setHeader("X-Accel-Buffering", "no");
        return sseRegistry.subscribe(workspaceId);
    }

    /**
     * 이 컨트롤러의 에러 응답은 여기서 직접 JSON으로 만든다.
     *
     * <p>구독 요청의 Accept 는 text/event-stream 뿐이라, 전역 핸들러처럼 본문만 반환하면
     * Spring 이 JSON 컨버터를 협상으로 못 골라 에러 렌더링이 실패하고 — 원래 예외가
     * ServletException(500)으로 그대로 터진다. 그러면 클라이언트는 일시 장애로 보고
     * 3초마다 재연결을 되풀이한다. Content-Type 을 명시한 ResponseEntity 는 협상을
     * 건너뛰므로 Accept 와 무관하게 401/403 JSON 이 나간다.
     */
    @ExceptionHandler(WorkspaceMemberRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotMember(WorkspaceMemberRequiredException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.of(403, e.getMessage(), null));
    }

    /** 인증 없음(401) — @AuthenticatedUser 검사가 던진다. 사유는 위 핸들러 주석과 동일. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthenticated(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.of(401, e.getMessage(), null));
    }
}
