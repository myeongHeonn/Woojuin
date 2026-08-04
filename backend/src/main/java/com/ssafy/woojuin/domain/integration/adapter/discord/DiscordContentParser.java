package com.ssafy.woojuin.domain.integration.adapter.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.ssafy.woojuin.domain.integration.service.ChatCommandService.InvalidCommandException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DiscordContentParser {

    private static final Pattern HTTP_URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

    public ImageCommand parseImageCommand(JsonNode data) {
        JsonNode action = firstOption(data.path("options"));
        if (!"image".equals(action.path("name").asText())) {
            throw new InvalidCommandException("지원하지 않는 Discord 이미지 명령이에요.");
        }
        String attachmentId = optionValue(action.path("options"), "attachment");
        if (attachmentId == null) throw new InvalidCommandException("저장할 이미지를 선택해주세요.");
        JsonNode attachment = data.path("resolved").path("attachments").path(attachmentId);
        if (attachment.isMissingNode()) throw new InvalidCommandException("Discord 첨부파일 정보가 없어요.");
        Long workspaceId = optionalLong(action.path("options"), "workspace");
        return new ImageCommand(List.of(toAttachment(attachment)), workspaceId);
    }

    public MessageContent parseMessage(JsonNode data) {
        String targetId = data.path("target_id").asText();
        JsonNode message = data.path("resolved").path("messages").path(targetId);
        if (targetId.isBlank() || message.isMissingNode()) {
            throw new InvalidCommandException("선택한 Discord 메시지 정보를 가져오지 못했어요.");
        }
        List<DiscordAttachment> attachments = new ArrayList<>();
        JsonNode values = message.path("attachments");
        if (values.isArray()) {
            for (JsonNode value : values) attachments.add(toAttachment(value));
        } else if (values.isObject()) {
            values.elements().forEachRemaining(value -> attachments.add(toAttachment(value)));
        }
        String content = message.path("content").asText("").trim();
        return new MessageContent(attachments, content);
    }

    public String textCommand(String content) {
        Matcher matcher = HTTP_URL.matcher(content == null ? "" : content);
        if (matcher.find()) return "save " + trimUrlPunctuation(matcher.group());
        if (content != null && !content.isBlank()) return "memo " + content.trim();
        throw new InvalidCommandException("선택한 메시지에 저장할 이미지, URL 또는 내용이 없어요.");
    }

    public boolean hasSupportedImage(List<DiscordAttachment> attachments) {
        return attachments.stream().anyMatch(attachment -> attachment.contentType() != null
                && attachment.contentType().toLowerCase(java.util.Locale.ROOT).matches(
                        "image/(png|jpeg|webp|gif)"));
    }

    private DiscordAttachment toAttachment(JsonNode value) {
        return new DiscordAttachment(
                value.path("id").asText(),
                value.path("filename").asText("image"),
                value.path("content_type").asText(""),
                value.path("size").asLong(-1),
                value.path("url").asText());
    }

    private JsonNode firstOption(JsonNode options) {
        if (!options.isArray() || options.isEmpty()) return MissingNode.getInstance();
        return options.get(0);
    }

    private String optionValue(JsonNode options, String name) {
        if (!options.isArray()) return null;
        for (JsonNode option : options) {
            if (name.equals(option.path("name").asText())) return option.path("value").asText(null);
        }
        return null;
    }

    private Long optionalLong(JsonNode options, String name) {
        String value = optionValue(options, name);
        if (value == null) return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException("워크스페이스 ID가 올바르지 않아요.");
        }
    }

    private String trimUrlPunctuation(String url) {
        return url.replaceFirst("[),.!?]+$", "");
    }

    public record ImageCommand(List<DiscordAttachment> attachments, Long workspaceId) {}
    public record MessageContent(List<DiscordAttachment> attachments, String content) {}
}
