package com.ssafy.woojuin.domain.integration.adapter.discord;

import com.ssafy.woojuin.domain.integration.dto.ChatCommandResult;
import com.ssafy.woojuin.domain.integration.service.ChatCommandService.ChatAccountNotLinkedException;
import com.ssafy.woojuin.domain.integration.service.ChatCommandService.DefaultWorkspaceNotSetException;
import com.ssafy.woojuin.domain.integration.service.ChatCommandService.InvalidCommandException;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class DiscordDeferredResponseService {

    private final RestClient restClient;
    private final String applicationId;

    public DiscordDeferredResponseService(
            RestClient.Builder restClientBuilder,
            @Value("${woojuin.integrations.discord.application-id:}") String applicationId) {
        this.restClient = restClientBuilder.baseUrl("https://discord.com/api/v10").build();
        this.applicationId = applicationId == null ? "" : applicationId.trim();
    }

    @Async
    public void complete(String interactionToken, Supplier<ChatCommandResult> operation) {
        String message;
        try {
            message = operation.get().message();
        } catch (ChatAccountNotLinkedException e) {
            message = "연결된 우주인 계정이 없어요. `/woojuin connect code:<코드>`로 먼저 연결해주세요.";
        } catch (DefaultWorkspaceNotSetException e) {
            message = "기본 워크스페이스가 없어요. `/woojuin workspace list`로 목록을 확인해주세요.";
        } catch (InvalidCommandException | IllegalArgumentException e) {
            message = e.getMessage();
        } catch (RuntimeException e) {
            message = "저장 중 문제가 발생했어요. 잠시 후 다시 시도해주세요.";
        }
        updateOriginal(interactionToken, message);
    }

    private void updateOriginal(String interactionToken, String message) {
        if (applicationId.isBlank() || interactionToken == null || interactionToken.isBlank()) return;
        try {
            restClient.patch()
                    .uri("/webhooks/{applicationId}/{token}/messages/@original", applicationId, interactionToken)
                    .body(Map.of("content", message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ignored) {
            // 최초 응답은 이미 성공했다. Discord 후속 응답 실패가 저장 자체를 롤백하면 안 된다.
        }
    }
}
