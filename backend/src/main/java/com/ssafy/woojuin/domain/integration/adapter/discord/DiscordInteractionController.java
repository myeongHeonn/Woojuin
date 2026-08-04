package com.ssafy.woojuin.domain.integration.adapter.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.woojuin.domain.integration.dto.ChatCommand;
import com.ssafy.woojuin.domain.integration.dto.ChatCommandResult;
import com.ssafy.woojuin.domain.integration.entity.ChatPlatform;
import com.ssafy.woojuin.domain.integration.service.ChatCommandService;
import com.ssafy.woojuin.domain.integration.service.ChatCommandService.ChatAccountNotLinkedException;
import com.ssafy.woojuin.domain.integration.service.ChatCommandService.DefaultWorkspaceNotSetException;
import com.ssafy.woojuin.domain.integration.service.ChatCommandService.InvalidCommandException;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/discord")
public class DiscordInteractionController {

    private static final int PING = 1;
    private static final int APPLICATION_COMMAND = 2;
    private static final int CHAT_INPUT_COMMAND = 1;
    private static final int MESSAGE_COMMAND = 3;

    private final DiscordSignatureVerifier signatureVerifier;
    private final DiscordCommandParser commandParser;
    private final DiscordContentParser contentParser;
    private final DiscordImageSaveService imageSaveService;
    private final DiscordDeferredResponseService deferredResponseService;
    private final ChatCommandService commandService;
    private final ObjectMapper objectMapper;

    public DiscordInteractionController(
            DiscordSignatureVerifier signatureVerifier,
            DiscordCommandParser commandParser,
            DiscordContentParser contentParser,
            DiscordImageSaveService imageSaveService,
            DiscordDeferredResponseService deferredResponseService,
            ChatCommandService commandService,
            ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.commandParser = commandParser;
        this.contentParser = contentParser;
        this.imageSaveService = imageSaveService;
        this.deferredResponseService = deferredResponseService;
        this.commandService = commandService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/interactions")
    public ResponseEntity<DiscordInteractionResponse> interactions(
            @RequestHeader(value = "X-Signature-Ed25519", required = false) String signature,
            @RequestHeader(value = "X-Signature-Timestamp", required = false) String timestamp,
            @RequestBody byte[] body) {
        if (!signatureVerifier.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(DiscordInteractionResponse.ephemeral("Discord 연동이 서버에 설정되지 않았어요."));
        }
        if (!signatureVerifier.verify(timestamp, signature, body)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            JsonNode interaction = objectMapper.readTree(body);
            int type = interaction.path("type").asInt();
            if (type == PING) return ResponseEntity.ok(DiscordInteractionResponse.pong());
            if (type != APPLICATION_COMMAND) {
                return ResponseEntity.badRequest()
                        .body(DiscordInteractionResponse.ephemeral("지원하지 않는 Discord 요청이에요."));
            }

            String memberUserId = interaction.path("member").path("user").path("id").asText();
            String externalUserId = memberUserId.isBlank()
                    ? interaction.path("user").path("id").asText()
                    : memberUserId;
            if (externalUserId.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(DiscordInteractionResponse.ephemeral("Discord 사용자 정보가 없어요."));
            }

            JsonNode data = interaction.path("data");
            int commandType = data.path("type").asInt(CHAT_INPUT_COMMAND);
            String requestId = interaction.path("id").asText();
            if (commandType == MESSAGE_COMMAND) {
                DiscordContentParser.MessageContent message = contentParser.parseMessage(data);
                if (contentParser.hasSupportedImage(message.attachments())) {
                    deferredResponseService.complete(interaction.path("token").asText(),
                            () -> imageSaveService.save(
                                    externalUserId, requestId, null, message.attachments()));
                    return ResponseEntity.ok(DiscordInteractionResponse.deferredEphemeral());
                }
                String text = contentParser.textCommand(message.content());
                ChatCommandResult result = commandService.handle(new ChatCommand(
                        ChatPlatform.DISCORD, externalUserId, requestId, text));
                return ResponseEntity.ok(DiscordInteractionResponse.ephemeral(result.message()));
            }
            if (commandType != CHAT_INPUT_COMMAND) {
                return ResponseEntity.badRequest()
                        .body(DiscordInteractionResponse.ephemeral("지원하지 않는 Discord 명령 유형이에요."));
            }
            if ("image".equals(firstOptionName(data))) {
                DiscordContentParser.ImageCommand image = contentParser.parseImageCommand(data);
                deferredResponseService.complete(interaction.path("token").asText(),
                        () -> imageSaveService.save(
                                externalUserId, requestId, image.workspaceId(), image.attachments()));
                return ResponseEntity.ok(DiscordInteractionResponse.deferredEphemeral());
            }

            String text = commandParser.parse(data);
            ChatCommandResult result = commandService.handle(new ChatCommand(
                    ChatPlatform.DISCORD,
                    externalUserId,
                    requestId,
                    text));
            return ResponseEntity.ok(DiscordInteractionResponse.ephemeral(result.message()));
        } catch (ChatAccountNotLinkedException e) {
            return ResponseEntity.ok(DiscordInteractionResponse.ephemeral(
                    "연결된 우주인 계정이 없어요. 우주인에서 연결 코드를 발급한 뒤 "
                            + "`/woojuin connect code:<코드>`를 실행해주세요."));
        } catch (DefaultWorkspaceNotSetException e) {
            return ResponseEntity.ok(DiscordInteractionResponse.ephemeral(
                    "기본 워크스페이스가 없어요. `/woojuin workspace list`로 목록을 확인해주세요."));
        } catch (InvalidCommandException | IllegalArgumentException e) {
            return ResponseEntity.ok(DiscordInteractionResponse.ephemeral(e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.badRequest()
                    .body(DiscordInteractionResponse.ephemeral("Discord 요청 형식이 올바르지 않아요."));
        }
    }

    private String firstOptionName(JsonNode data) {
        JsonNode options = data.path("options");
        return options.isArray() && !options.isEmpty() ? options.get(0).path("name").asText() : "";
    }
}
