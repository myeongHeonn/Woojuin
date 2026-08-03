package com.ssafy.woojuin.domain.integration.adapter.discord;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Discord의 구조화된 slash command options를 공통 채팅 명령 문자열로 변환한다. */
@Component
public class DiscordCommandParser {

    public String parse(JsonNode data) {
        if (data == null || !"woojuin".equals(data.path("name").asText())) {
            throw new IllegalArgumentException("지원하지 않는 Discord 명령이에요.");
        }
        JsonNode options = data.path("options");
        if (!options.isArray() || options.isEmpty()) return "help";

        JsonNode action = options.get(0);
        String actionName = action.path("name").asText();
        if (action.path("type").asInt() == 2) {
            return parseGroup(actionName, action.path("options"));
        }
        return parseAction(actionName, action.path("options"));
    }

    private String parseGroup(String group, JsonNode options) {
        if (!"workspace".equals(group) || !options.isArray() || options.isEmpty()) {
            throw new IllegalArgumentException("지원하지 않는 Discord 명령이에요.");
        }
        JsonNode action = options.get(0);
        return "workspace " + parseAction(action.path("name").asText(), action.path("options"));
    }

    private String parseAction(String action, JsonNode options) {
        return switch (action) {
            case "help", "account", "disconnect" -> action;
            case "connect" -> "connect " + required(options, "code");
            case "list" -> "list";
            case "set" -> "set " + required(options, "id");
            case "memo" -> "memo " + required(options, "content");
            case "search" -> "search " + required(options, "query");
            case "save" -> parseSave(options);
            default -> throw new IllegalArgumentException("지원하지 않는 Discord 명령이에요.");
        };
    }

    private String parseSave(JsonNode options) {
        List<String> parts = new ArrayList<>();
        parts.add("save");
        parts.add(required(options, "url"));
        String workspace = optional(options, "workspace");
        if (workspace != null) {
            parts.add("--workspace");
            parts.add(workspace);
        }
        return String.join(" ", parts);
    }

    private String required(JsonNode options, String name) {
        String value = optional(options, name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("필수 입력값이 없어요: " + name);
        }
        return value;
    }

    private String optional(JsonNode options, String name) {
        if (options == null || !options.isArray()) return null;
        for (JsonNode option : options) {
            if (name.equals(option.path("name").asText()) && option.has("value")) {
                return option.path("value").asText();
            }
        }
        return null;
    }
}
