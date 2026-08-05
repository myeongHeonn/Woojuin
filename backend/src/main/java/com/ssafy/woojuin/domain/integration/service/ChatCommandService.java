package com.ssafy.woojuin.domain.integration.service;

import com.ssafy.woojuin.domain.ai.usage.AiUsageLimitExceededException;
import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.auth.repository.UserRepository;
import com.ssafy.woojuin.domain.integration.dto.ChatCommand;
import com.ssafy.woojuin.domain.integration.dto.ChatCommandResult;
import com.ssafy.woojuin.domain.integration.entity.ChatAccountConnection;
import com.ssafy.woojuin.domain.integration.entity.ChatPlatform;
import com.ssafy.woojuin.domain.integration.repository.ChatAccountConnectionRepository;
import com.ssafy.woojuin.domain.item.dto.ItemCreateRequest;
import com.ssafy.woojuin.domain.item.dto.ItemCreateResponse;
import com.ssafy.woojuin.domain.item.dto.ItemSearchResponse;
import com.ssafy.woojuin.domain.item.dto.ItemSummaryResponse;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.exception.WorkspaceAccessDeniedException;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import com.ssafy.woojuin.domain.item.service.ItemSearchService;
import com.ssafy.woojuin.domain.item.service.ItemService;
import com.ssafy.woojuin.domain.workspace.entity.Workspace;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

/** 플랫폼 어댑터와 무관한 채팅 명령 처리. 저장과 검색은 기존 도메인 서비스를 그대로 사용한다. */
@Service
public class ChatCommandService {

    private static final int SEARCH_RESULT_SIZE = 5;

    private final ChatAccountConnectionRepository connectionRepository;
    private final ChatLinkCodeService linkCodeService;
    private final ChatCommandDeduplicationService deduplicationService;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ItemService itemService;
    private final ItemSearchService itemSearchService;
    private final ItemRepository itemRepository;
    private final String frontendBaseUrl;

    public ChatCommandService(ChatAccountConnectionRepository connectionRepository,
            ChatLinkCodeService linkCodeService,
            ChatCommandDeduplicationService deduplicationService,
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            ItemService itemService,
            ItemSearchService itemSearchService,
            ItemRepository itemRepository,
            @Value("${woojuin.integrations.chat.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.connectionRepository = connectionRepository;
        this.linkCodeService = linkCodeService;
        this.deduplicationService = deduplicationService;
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.itemService = itemService;
        this.itemSearchService = itemSearchService;
        this.itemRepository = itemRepository;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    public ChatCommandResult handle(ChatCommand command) {
        if (!deduplicationService.acquire(command.platform(), command.requestId())) {
            return ChatCommandResult.of("이미 처리한 요청이에요. 우주인에서 결과를 확인해주세요.");
        }

        try {
            String text = command.text() == null ? "" : command.text().trim();
            if (text.isEmpty() || text.equalsIgnoreCase("help")) {
                return help(command.platform());
            }
            String[] parts = text.split("\\s+", 2);
            String action = parts[0].toLowerCase(Locale.ROOT);
            String arguments = parts.length > 1 ? parts[1].trim() : "";
            return switch (action) {
                case "connect" -> connect(command, arguments);
                case "workspace" -> workspace(command, arguments);
                case "save" -> save(command, arguments);
                case "memo" -> memo(command, arguments);
                case "search" -> search(command, arguments);
                case "account" -> account(command);
                case "disconnect" -> disconnect(command);
                default -> ChatCommandResult.of("알 수 없는 명령이에요. `/woojuin help`로 사용법을 확인해주세요.");
            };
        } catch (RuntimeException e) {
            deduplicationService.release(command.platform(), command.requestId());
            throw e;
        }
    }

    private ChatCommandResult connect(ChatCommand command, String code) {
        Long userId = linkCodeService.consume(code);
        if (userId == null) {
            return ChatCommandResult.of("연결 코드가 올바르지 않거나 만료됐어요. 우주인에서 새 코드를 발급해주세요.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 우주인 계정이에요."));
        Optional<ChatAccountConnection> existing = connectionRepository
                .findByPlatformAndExternalUserId(command.platform(), command.externalUserId());
        if (existing.isPresent() && !existing.get().getUser().getId().equals(userId)) {
            return ChatCommandResult.of("이미 다른 우주인 계정에 연결된 사용자예요.");
        }
        Optional<ChatAccountConnection> userExistingConnection = connectionRepository
                .findByPlatformAndUserId(command.platform(), userId);
        if (userExistingConnection.isPresent()
                && !userExistingConnection.get().getExternalUserId().equals(command.externalUserId())) {
            return ChatCommandResult.of("이 우주인 계정은 이미 다른 채팅 계정과 연결되어 있어요. 기존 연동을 먼저 해제해주세요.");
        }
        ChatAccountConnection connection = existing.orElseGet(
                () -> new ChatAccountConnection(command.platform(), command.externalUserId(), user));
        if (connection.getDefaultWorkspace() == null && user.getPersonalWorkspaceId() != null) {
            workspaceMemberRepository.findByWorkspaceIdAndUserId(user.getPersonalWorkspaceId(), userId)
                    .map(WorkspaceMember::getWorkspace)
                    .ifPresent(connection::changeDefaultWorkspace);
        }
        connectionRepository.save(connection);
        String nickname = user.getNickname() == null || user.getNickname().isBlank()
                ? user.getEmail()
                : user.getNickname();
        return ChatCommandResult.of("`" + markdownCode(nickname) + "`님의 우주인 계정이 연결됐어요.\n\n"
                + workspaceListMessage(connection));
    }

    private ChatCommandResult workspace(ChatCommand command, String arguments) {
        ChatAccountConnection connection = requireConnection(command);
        if (arguments.equalsIgnoreCase("list")) {
            return ChatCommandResult.of(workspaceListMessage(connection));
        }
        String selector = arguments.toLowerCase(Locale.ROOT).startsWith("set ")
                ? arguments.substring(4).trim()
                : arguments.trim();
        if (!selector.isBlank()) {
            Long workspaceId = parseWorkspaceId(selector);
            WorkspaceMember membership = workspaceMemberRepository
                    .findByWorkspaceIdAndUserId(workspaceId, connection.getUser().getId())
                    .orElse(null);
            if (membership == null) {
                return ChatCommandResult.of("해당 번호의 워크스페이스에 접근할 수 없어요. "
                        + "`/woojuin workspace list`로 번호를 확인해주세요.");
            }
            connection.changeDefaultWorkspace(membership.getWorkspace());
            connectionRepository.save(connection);
            return ChatCommandResult.of("기본 저장 공간을 `" + membership.getWorkspace().getName() + "`(으)로 변경했어요.");
        }
        return ChatCommandResult.of("사용법: `/woojuin workspace list` 또는 `/woojuin workspace <번호>`");
    }

    private String workspaceListMessage(ChatAccountConnection connection) {
        List<WorkspaceMember> memberships = workspaceMemberRepository.findByUserId(connection.getUser().getId());
        if (memberships.isEmpty()) {
            return "접근할 수 있는 워크스페이스가 없어요.";
        }
        StringBuilder message = new StringBuilder("**접근 가능한 워크스페이스**\n\n");
        for (WorkspaceMember member : memberships) {
            Workspace workspace = member.getWorkspace();
            boolean selected = connection.getDefaultWorkspace() != null
                    && connection.getDefaultWorkspace().getId().equals(workspace.getId());
            message.append(selected ? "`[✓]` " : "`[ ]` ")
                    .append(workspace.getId()).append(". ")
                    .append(markdownLabel(workspace.getName()));
            if (selected) message.append("  (기본 저장 공간)");
            message.append('\n');
        }
        message.append("\n변경하려면 `/woojuin workspace <번호>`를 입력하세요.");
        return message.toString();
    }

    private ChatCommandResult save(ChatCommand command, String arguments) {
        ChatAccountConnection connection = requireConnection(command);
        SaveArguments parsed = parseSaveArguments(arguments);
        if (parsed.content().isBlank()) {
            return ChatCommandResult.of("저장할 URL이나 메모를 입력해주세요. 예: `/woojuin save 다음 회의 일정 확인`");
        }
        Long workspaceId = parsed.workspaceId() != null
                ? parsed.workspaceId()
                : defaultWorkspaceId(connection);
        try {
            boolean url = isValidHttpUrl(parsed.content());
            ItemCreateResponse item = itemService.createFromRequest(workspaceId, connection.getUser().getId(),
                    url
                            ? new ItemCreateRequest(ItemType.URL, parsed.content(), null)
                            : new ItemCreateRequest(ItemType.MEMO, null, parsed.content()));
            return savedMessage(url ? "링크" : "메모", workspaceId, item.itemId());
        } catch (WorkspaceAccessDeniedException e) {
            return ChatCommandResult.of("해당 워크스페이스에 접근할 권한이 없어요.");
        } catch (AiUsageLimitExceededException e) {
            return ChatCommandResult.of("이번 달 저장 횟수를 모두 사용했어요. 다음 달에 다시 이용해주세요.");
        }
    }

    private ChatCommandResult search(ChatCommand command, String query) {
        ChatAccountConnection connection = requireConnection(command);
        if (query.isBlank()) {
            return ChatCommandResult.of("검색어를 입력해주세요. 예: `/woojuin search 제주도 맛집`");
        }
        Long workspaceId = defaultWorkspaceId(connection);
        ItemSearchResponse response = itemSearchService.search(
                workspaceId, connection.getUser().getId(), query, 0, SEARCH_RESULT_SIZE);
        if (response.content().isEmpty()) {
            return ChatCommandResult.of("`" + query + "` 검색 결과가 없어요.");
        }
        List<Item> memoItems = itemRepository.findAllById(response.content().stream()
                .filter(item -> item.type() == ItemType.MEMO)
                .map(ItemSummaryResponse::itemId)
                .toList());
        StringBuilder message = new StringBuilder("🔎 **‘").append(markdownLabel(query))
                .append("’ 검색 결과**\n\n");
        int index = 1;
        for (ItemSummaryResponse item : response.content()) {
            String label = item.type() == ItemType.MEMO
                    ? originalMemoLabel(memoItems, item.itemId())
                    : fallbackTitle(item);
            String deepLink = frontendBaseUrl + "/workspace/" + workspaceId
                    + "/library?item=" + item.itemId();
            message.append(index++).append(". [")
                    .append(markdownLabel(label)).append("](").append(deepLink).append(") · ")
                    .append('`').append(itemTypeLabel(item.type())).append('`');
            if (item.type() == ItemType.URL && item.url() != null) {
                message.append(" · [원문](").append(item.url()).append(')');
            }
            message.append('\n');
        }
        return ChatCommandResult.of(message.toString().trim());
    }

    private String originalMemoLabel(List<Item> memoItems, Long itemId) {
        return memoItems.stream()
                .filter(item -> item.getId().equals(itemId))
                .map(Item::getContent)
                .filter(content -> content != null && !content.isBlank())
                .map(this::shorten)
                .findFirst()
                .orElse("메모 보기");
    }

    private String fallbackTitle(ItemSummaryResponse item) {
        if (item.title() != null && !item.title().isBlank()) return shorten(item.title());
        if (item.url() != null && !item.url().isBlank()) return shorten(item.url());
        return "저장 항목 보기";
    }

    private String shorten(String value) {
        String singleLine = value.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= 90 ? singleLine : singleLine.substring(0, 87) + "…";
    }

    private String markdownLabel(String value) {
        return value.replace("[", "\\[").replace("]", "\\]");
    }

    private String markdownCode(String value) {
        return value.replace('`', '\'');
    }

    private ChatCommandResult memo(ChatCommand command, String content) {
        ChatAccountConnection connection = requireConnection(command);
        if (content == null || content.isBlank()) {
            return ChatCommandResult.of("메모 내용을 입력해주세요. 예: `/woojuin memo 다음 회의에서 API 일정 확인하기`");
        }
        Long workspaceId = defaultWorkspaceId(connection);
        try {
            ItemCreateResponse item = itemService.createFromRequest(
                    workspaceId,
                    connection.getUser().getId(),
                    new ItemCreateRequest(ItemType.MEMO, null, content));
            return savedMessage("메모", workspaceId, item.itemId());
        } catch (WorkspaceAccessDeniedException e) {
            return ChatCommandResult.of("해당 워크스페이스에 접근할 권한이 없어요.");
        } catch (AiUsageLimitExceededException e) {
            return ChatCommandResult.of("이번 달 저장 횟수를 모두 사용했어요. 다음 달에 다시 이용해주세요.");
        }
    }

    private ChatCommandResult account(ChatCommand command) {
        ChatAccountConnection connection = requireConnection(command);
        String workspace = connection.getDefaultWorkspace() == null
                ? "미지정"
                : connection.getDefaultWorkspace().getName();
        return ChatCommandResult.of("**연결 정보**\n\n"
                + "계정  `" + connection.getUser().getEmail() + "`\n"
                + "기본 저장 공간  `" + workspace + "`");
    }

    private ChatCommandResult disconnect(ChatCommand command) {
        ChatAccountConnection connection = requireConnection(command);
        connectionRepository.delete(connection);
        return ChatCommandResult.of("우주인 계정 연동을 해제했어요. 다시 사용하려면 새 연결 코드를 발급해주세요.");
    }

    private ChatAccountConnection requireConnection(ChatCommand command) {
        return connectionRepository.findByPlatformAndExternalUserId(
                        command.platform(), command.externalUserId())
                .orElseThrow(() -> new ChatAccountNotLinkedException());
    }

    private Long defaultWorkspaceId(ChatAccountConnection connection) {
        if (connection.getDefaultWorkspace() == null) {
            throw new DefaultWorkspaceNotSetException();
        }
        return connection.getDefaultWorkspace().getId();
    }

    private String workspaceName(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .map(Workspace::getName)
                .orElse("워크스페이스 " + workspaceId);
    }

    private Long parseWorkspaceId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException("워크스페이스 번호는 숫자로 입력해주세요. "
                    + "`/woojuin workspace list`로 번호를 확인할 수 있어요.");
        }
    }

    private SaveArguments parseSaveArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return new SaveArguments("", null);
        }
        String trimmed = arguments.trim();
        String content = trimmed;
        Long workspaceId = null;
        int optionIndex = trimmed.toLowerCase(Locale.ROOT).lastIndexOf(" --workspace ");
        if (optionIndex >= 0) {
            content = trimmed.substring(0, optionIndex).trim();
            String selector = trimmed.substring(optionIndex + " --workspace ".length()).trim();
            if (selector.isBlank()) {
                throw new InvalidCommandException("사용법: `/woojuin save <URL 또는 메모> [--workspace <ID>]`");
            }
            workspaceId = parseWorkspaceId(selector);
        }
        return new SaveArguments(content, workspaceId);
    }

    private boolean isValidHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            return uri.isAbsolute()
                    && uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private ChatCommandResult help(ChatPlatform platform) {
        String title = platform == ChatPlatform.MATTERMOST
                ? ":woojuin_white1: **우주인 명령어**"
                : "**우주인 명령어**";
        String common = title + "\n\n" + """

                - `/woojuin save <URL 또는 메모>` 링크·메모 저장
                - `/woojuin search <검색어>` 정보 검색
                - `/woojuin workspace list` 워크스페이스 목록
                - `/woojuin workspace <번호>` 워크스페이스 변경
                - `/woojuin account` 연결 계정 확인
                - `/woojuin connect <코드>` 계정 연결
                - `/woojuin disconnect` 연결 계정 해제
                """.trim();
        if (platform == ChatPlatform.DISCORD) {
            common += "\n- `/woojuin image attachment:<이미지>` 이미지 저장"
                    + "\n- 메시지 우클릭 → `앱` → `우주인에 저장`";
        }
        return ChatCommandResult.of(common);
    }

    private ChatCommandResult savedMessage(String type, Long workspaceId, Long itemId) {
        String deepLink = frontendBaseUrl + "/workspace/" + workspaceId + "/library?item=" + itemId;
        return ChatCommandResult.of("**우주인으로 보냈어요 🚀**"
                + "\n\n저장 공간: `" + markdownLabel(workspaceName(workspaceId)) + "`"
                + "\n종류: `" + type + "`"
                + "\n\n[우주인에서 열기](" + deepLink + ")");
    }

    private String itemTypeLabel(ItemType type) {
        return switch (type) {
            case URL -> "링크";
            case IMAGE -> "이미지";
            case MEMO -> "메모";
        };
    }

    private record SaveArguments(String content, Long workspaceId) {
    }

    public static class ChatAccountNotLinkedException extends RuntimeException {
    }

    public static class DefaultWorkspaceNotSetException extends RuntimeException {
    }

    public static class InvalidCommandException extends RuntimeException {
        public InvalidCommandException(String message) {
            super(message);
        }
    }
}
