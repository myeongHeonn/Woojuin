package com.ssafy.woojuin.domain.integration.adapter.discord;

import com.ssafy.woojuin.domain.ai.usage.AiUsageLimitExceededException;
import com.ssafy.woojuin.domain.integration.dto.ChatCommandResult;
import com.ssafy.woojuin.domain.integration.entity.ChatAccountConnection;
import com.ssafy.woojuin.domain.integration.entity.ChatPlatform;
import com.ssafy.woojuin.domain.integration.repository.ChatAccountConnectionRepository;
import com.ssafy.woojuin.domain.integration.service.ChatCommandDeduplicationService;
import com.ssafy.woojuin.domain.integration.service.ChatCommandService.ChatAccountNotLinkedException;
import com.ssafy.woojuin.domain.integration.service.ChatCommandService.DefaultWorkspaceNotSetException;
import com.ssafy.woojuin.domain.item.exception.WorkspaceAccessDeniedException;
import com.ssafy.woojuin.domain.item.dto.ItemCreateResponse;
import com.ssafy.woojuin.domain.item.service.ItemService;
import com.ssafy.woojuin.domain.workspace.entity.Workspace;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceRepository;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DiscordImageSaveService {

    static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_IMAGES_PER_MESSAGE = 5;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif");
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "cdn.discordapp.com", "media.discordapp.net");

    private final ChatAccountConnectionRepository connectionRepository;
    private final ChatCommandDeduplicationService deduplicationService;
    private final WorkspaceRepository workspaceRepository;
    private final ItemService itemService;
    private final String frontendBaseUrl;
    private final HttpClient httpClient;

    public DiscordImageSaveService(
            ChatAccountConnectionRepository connectionRepository,
            ChatCommandDeduplicationService deduplicationService,
            WorkspaceRepository workspaceRepository,
            ItemService itemService,
            @org.springframework.beans.factory.annotation.Value("${woojuin.integrations.chat.frontend-base-url:http://localhost:5173}")
            String frontendBaseUrl) {
        this.connectionRepository = connectionRepository;
        this.deduplicationService = deduplicationService;
        this.workspaceRepository = workspaceRepository;
        this.itemService = itemService;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public ChatCommandResult save(
            String externalUserId,
            String requestId,
            Long requestedWorkspaceId,
            List<DiscordAttachment> attachments) {
        if (!deduplicationService.acquire(ChatPlatform.DISCORD, requestId)) {
            return ChatCommandResult.of("이미 처리한 요청이에요. 우주인에서 결과를 확인해주세요.");
        }
        int saved = 0;
        List<Long> itemIds = new ArrayList<>();
        try {
            ChatAccountConnection connection = connectionRepository
                    .findByPlatformAndExternalUserId(ChatPlatform.DISCORD, externalUserId)
                    .orElseThrow(ChatAccountNotLinkedException::new);
            Long workspaceId = requestedWorkspaceId != null
                    ? requestedWorkspaceId
                    : defaultWorkspaceId(connection);
            List<DiscordAttachment> images = attachments.stream()
                    .filter(this::isImage)
                    .limit(MAX_IMAGES_PER_MESSAGE)
                    .toList();
            if (images.isEmpty()) {
                return ChatCommandResult.of("지원하는 이미지가 없어요. PNG, JPEG, WEBP, GIF 파일만 저장할 수 있어요.");
            }
            for (DiscordAttachment attachment : images) {
                validate(attachment);
            }
            for (DiscordAttachment attachment : images) {
                byte[] bytes = download(attachment);
                ItemCreateResponse item = itemService.createFromImage(workspaceId, connection.getUser().getId(),
                        new DiscordDownloadedFile(safeFilename(attachment.filename()), attachment.contentType(), bytes));
                itemIds.add(item.itemId());
                saved++;
            }
            String workspaceName = workspaceRepository.findById(workspaceId)
                    .map(Workspace::getName)
                    .orElse("워크스페이스 " + workspaceId);
            String count = saved == 1 ? "이미지" : "이미지 " + saved + "개";
            StringBuilder message = new StringBuilder("**우주인으로 보냈어요 🚀**")
                    .append("\n\n저장 공간: `").append(workspaceName).append('`')
                    .append("\n종류: `").append(count).append('`');
            for (int i = 0; i < itemIds.size(); i++) {
                String label = itemIds.size() == 1 ? "우주인에서 열기" : "이미지 " + (i + 1) + " 열기";
                message.append(i == 0 ? "\n\n" : "\n").append('[').append(label).append("](")
                        .append(frontendBaseUrl).append("/workspace/").append(workspaceId)
                        .append("/library?item=").append(itemIds.get(i)).append(')');
            }
            return ChatCommandResult.of(message.toString());
        } catch (WorkspaceAccessDeniedException e) {
            return ChatCommandResult.of(partial(saved, "해당 워크스페이스에 접근할 권한이 없어요."));
        } catch (AiUsageLimitExceededException e) {
            return ChatCommandResult.of(partial(saved, "이번 달 저장 횟수를 모두 사용했어요."));
        } catch (RuntimeException e) {
            if (saved == 0) deduplicationService.release(ChatPlatform.DISCORD, requestId);
            throw e;
        }
    }

    private Long defaultWorkspaceId(ChatAccountConnection connection) {
        if (connection.getDefaultWorkspace() == null) throw new DefaultWorkspaceNotSetException();
        return connection.getDefaultWorkspace().getId();
    }

    private boolean isImage(DiscordAttachment attachment) {
        return attachment.contentType() != null
                && ALLOWED_MIME_TYPES.contains(attachment.contentType().toLowerCase(Locale.ROOT));
    }

    private void validate(DiscordAttachment attachment) {
        if (attachment.size() <= 0 || attachment.size() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("이미지는 비어 있지 않은 10MB 이하 파일만 저장할 수 있어요.");
        }
        URI uri = safeDiscordUri(attachment.url());
        if (!uri.getPath().startsWith("/attachments/")) {
            throw new IllegalArgumentException("Discord 첨부파일 주소가 올바르지 않아요.");
        }
    }

    private byte[] download(DiscordAttachment attachment) {
        try {
            URI uri = safeDiscordUri(attachment.url());
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Woojuin/0.1 DiscordImageImporter")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IllegalArgumentException("Discord 이미지를 다운로드하지 못했어요.");
            }
            try (InputStream input = response.body(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > MAX_IMAGE_BYTES) {
                        throw new IllegalArgumentException("이미지는 10MB 이하만 저장할 수 있어요.");
                    }
                    output.write(buffer, 0, read);
                }
                if (total == 0) throw new IllegalArgumentException("빈 이미지는 저장할 수 없어요.");
                return output.toByteArray();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Discord 이미지 다운로드가 중단됐어요.", e);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Discord 이미지를 다운로드하지 못했어요.", e);
        }
    }

    private URI safeDiscordUri(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || !ALLOWED_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                    || uri.getUserInfo() != null
                    || uri.getPort() != -1) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Discord 첨부파일 주소가 올바르지 않아요.");
        }
    }

    private String safeFilename(String filename) {
        String normalized = filename == null ? "image" : filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return name.isBlank() ? "image" : name;
    }

    private String partial(int saved, String failure) {
        return saved == 0 ? failure : "이미지 " + saved + "개는 저장했지만, 나머지는 저장하지 못했어요. " + failure;
    }
}
