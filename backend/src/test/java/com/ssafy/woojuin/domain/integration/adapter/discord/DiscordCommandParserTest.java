package com.ssafy.woojuin.domain.integration.adapter.discord;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DiscordCommandParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DiscordCommandParser parser = new DiscordCommandParser();
    private final DiscordContentParser contentParser = new DiscordContentParser();

    @Test
    void parsesSaveWithOptionalWorkspace() throws Exception {
        var data = objectMapper.readTree("""
                {"name":"woojuin","options":[{"type":1,"name":"save","options":[
                  {"type":3,"name":"content","value":"https://example.com"},
                  {"type":4,"name":"workspace","value":42}
                ]}]}
                """);

        assertThat(parser.parse(data)).isEqualTo("save https://example.com --workspace 42");
    }

    @Test
    void parsesWorkspaceGroup() throws Exception {
        var data = objectMapper.readTree("""
                {"name":"woojuin","options":[{"type":2,"name":"workspace","options":[
                  {"type":1,"name":"set","options":[{"type":4,"name":"id","value":7}]}
                ]}]}
                """);

        assertThat(parser.parse(data)).isEqualTo("workspace set 7");
    }

    @Test
    void parsesWorkspaceNumber() throws Exception {
        var data = objectMapper.readTree("""
                {"name":"woojuin","options":[{"type":1,"name":"workspace","options":[
                  {"type":4,"name":"number","value":11}
                ]}]}
                """);

        assertThat(parser.parse(data)).isEqualTo("workspace 11");
    }

    @Test
    void parsesWorkspaceWithoutTargetAsList() throws Exception {
        var data = objectMapper.readTree("""
                {"name":"woojuin","options":[{"type":1,"name":"workspace"}]}
                """);

        assertThat(parser.parse(data)).isEqualTo("workspace list");
    }

    @Test
    void convertsMessageUrlToSaveCommand() {
        assertThat(contentParser.textCommand("참고 자료 https://example.com/docs 확인"))
                .isEqualTo("save https://example.com/docs");
    }

    @Test
    void convertsPlainMessageToMemoCommand() {
        assertThat(contentParser.textCommand("다음 회의에서 API 일정 확인"))
                .isEqualTo("memo 다음 회의에서 API 일정 확인");
    }
}
