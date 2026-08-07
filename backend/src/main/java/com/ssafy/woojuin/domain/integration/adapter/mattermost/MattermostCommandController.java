package com.ssafy.woojuin.domain.integration.adapter.mattermost;

import com.ssafy.woojuin.domain.integration.dto.ChatCommand;
import com.ssafy.woojuin.domain.integration.dto.ChatCommandResult;
import com.ssafy.woojuin.domain.integration.entity.ChatPlatform;
import com.ssafy.woojuin.domain.integration.service.ChatCommandService;
import com.ssafy.woojuin.domain.integration.service.ChatCommandService.ChatAccountNotLinkedException;
import com.ssafy.woojuin.domain.integration.service.ChatCommandService.DefaultWorkspaceNotSetException;
import com.ssafy.woojuin.domain.integration.service.ChatCommandService.InvalidCommandException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/mattermost")
public class MattermostCommandController {

    private final ChatCommandService commandService;
    private final String expectedToken;

    public MattermostCommandController(
            ChatCommandService commandService,
            @Value("${woojuin.integrations.mattermost.slash-token:}") String expectedToken) {
        this.commandService = commandService;
        this.expectedToken = expectedToken;
    }

    @PostMapping(path = "/commands", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<MattermostCommandResponse> command(
            @RequestParam MultiValueMap<String, String> form) {
        if (expectedToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(MattermostCommandResponse.ephemeral("Mattermost 연동이 서버에 설정되지 않았어요."));
        }
        if (!tokensMatch(expectedToken, form.getFirst("token"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(MattermostCommandResponse.ephemeral("Mattermost 요청 인증에 실패했어요."));
        }
        String externalUserId = form.getFirst("user_id");
        if (externalUserId == null || externalUserId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(MattermostCommandResponse.ephemeral("Mattermost 사용자 정보가 없어요."));
        }

        try {
            ChatCommandResult result = commandService.handle(new ChatCommand(
                    ChatPlatform.MATTERMOST,
                    externalUserId,
                    firstNonBlank(form, "trigger_id", "request_id"),
                    form.getFirst("text")));
            return ResponseEntity.ok(MattermostCommandResponse.ephemeral(result.message()));
        } catch (ChatAccountNotLinkedException e) {
            return ResponseEntity.ok(MattermostCommandResponse.ephemeral(
                    "연결된 우주인 계정이 없어요. 우주인에서 연결 코드를 발급한 뒤 "
                            + "`/woojuin connect <코드>`를 입력해주세요."));
        } catch (DefaultWorkspaceNotSetException e) {
            return ResponseEntity.ok(MattermostCommandResponse.ephemeral(
                    "기본 워크스페이스가 없어요. `/woojuin workspace list`로 목록을 확인해주세요."));
        } catch (InvalidCommandException e) {
            return ResponseEntity.ok(MattermostCommandResponse.ephemeral(e.getMessage()));
        }
    }

    private String firstNonBlank(MultiValueMap<String, String> form, String... keys) {
        for (String key : keys) {
            String value = form.getFirst(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private boolean tokensMatch(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
