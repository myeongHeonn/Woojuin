package com.ssafy.woojuin.domain.integration.adapter.mattermost;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.woojuin.domain.integration.dto.ChatCommandResult;
import com.ssafy.woojuin.domain.integration.service.ChatCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MattermostCommandController.class,
        excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "woojuin.integrations.mattermost.slash-token=valid-token")
class MattermostCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatCommandService commandService;

    @Test
    void rejectsInvalidToken() throws Exception {
        mockMvc.perform(post("/api/integrations/mattermost/commands")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", "wrong-token")
                        .param("user_id", "mm-user")
                        .param("text", "help"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.response_type").value("ephemeral"))
                .andExpect(jsonPath("$.text").value("Mattermost 요청 인증에 실패했어요."));

        verify(commandService, never()).handle(any());
    }

    @Test
    void parsesValidRequestAndReturnsEphemeralResponse() throws Exception {
        when(commandService.handle(any())).thenReturn(ChatCommandResult.of("우주인으로 보냈어요."));

        mockMvc.perform(post("/api/integrations/mattermost/commands")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", "valid-token")
                        .param("user_id", "mm-user")
                        .param("trigger_id", "retry-key")
                        .param("text", "save https://example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response_type").value("ephemeral"))
                .andExpect(jsonPath("$.text").value("우주인으로 보냈어요."));

        verify(commandService).handle(any());
    }
}
