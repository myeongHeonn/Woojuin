package com.ssafy.woojuin.domain.integration.adapter.discord;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.woojuin.domain.integration.dto.ChatCommandResult;
import com.ssafy.woojuin.domain.integration.service.ChatCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DiscordInteractionController.class,
        excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DiscordCommandParser.class, DiscordContentParser.class})
class DiscordInteractionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DiscordSignatureVerifier signatureVerifier;

    @MockBean
    private ChatCommandService commandService;

    @MockBean
    private DiscordImageSaveService imageSaveService;

    @MockBean
    private DiscordDeferredResponseService deferredResponseService;

    @BeforeEach
    void configured() {
        when(signatureVerifier.isConfigured()).thenReturn(true);
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        when(signatureVerifier.verify(anyString(), anyString(), any())).thenReturn(false);

        mockMvc.perform(post("/api/integrations/discord/interactions")
                        .header("X-Signature-Ed25519", "bad")
                        .header("X-Signature-Timestamp", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":1}"))
                .andExpect(status().isUnauthorized());

        verify(commandService, never()).handle(any());
    }

    @Test
    void answersDiscordPing() throws Exception {
        when(signatureVerifier.verify(anyString(), anyString(), any())).thenReturn(true);

        mockMvc.perform(post("/api/integrations/discord/interactions")
                        .header("X-Signature-Ed25519", "valid")
                        .header("X-Signature-Timestamp", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value(1));
    }

    @Test
    void parsesCommandAndReturnsEphemeralResponse() throws Exception {
        when(signatureVerifier.verify(anyString(), anyString(), any())).thenReturn(true);
        when(commandService.handle(any())).thenReturn(ChatCommandResult.of("우주인으로 보냈어요."));
        String body = """
                {"id":"interaction-1","type":2,"member":{"user":{"id":"discord-user"}},
                 "data":{"name":"woojuin","options":[{"type":1,"name":"save","options":[
                   {"type":3,"name":"content","value":"https://example.com"}
                 ]}]}}
                """;

        mockMvc.perform(post("/api/integrations/discord/interactions")
                        .header("X-Signature-Ed25519", "valid")
                        .header("X-Signature-Timestamp", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value(4))
                .andExpect(jsonPath("$.data.flags").value(68))
                .andExpect(jsonPath("$.data.content").value("우주인으로 보냈어요."));

        verify(commandService).handle(any());
    }

    @Test
    void defersImageUploadBeforeDownloadingIt() throws Exception {
        when(signatureVerifier.verify(anyString(), anyString(), any())).thenReturn(true);
        String body = """
                {"id":"interaction-image","token":"response-token","type":2,
                 "member":{"user":{"id":"discord-user"}},
                 "data":{"type":1,"name":"woojuin","options":[{"type":1,"name":"image","options":[
                   {"type":11,"name":"attachment","value":"file-1"}
                 ]}],"resolved":{"attachments":{"file-1":{"id":"file-1","filename":"photo.png",
                   "content_type":"image/png","size":123,"url":"https://cdn.discordapp.com/attachments/1/2/photo.png"}}}}}
                """;

        mockMvc.perform(post("/api/integrations/discord/interactions")
                        .header("X-Signature-Ed25519", "valid")
                        .header("X-Signature-Timestamp", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value(5))
                .andExpect(jsonPath("$.data.flags").value(64));

        verify(deferredResponseService).complete(anyString(), any());
        verify(commandService, never()).handle(any());
    }
}
