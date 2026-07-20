package com.ssafy.woojuin.domain.item;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.woojuin.domain.item.dto.ItemCreateRequest;
import com.ssafy.woojuin.domain.item.dto.ItemCreateResponse;
import com.ssafy.woojuin.global.common.ItemStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 웹 계층 슬라이스 테스트. 순수 단위 테스트로는 잡히지 않는 것들을 여기서 막는다:
 * 예외 핸들러 매핑 충돌(앱이 기동조차 못 하게 만듦), 요청 바디 검증, 공통 응답 형식.
 */
@WebMvcTest(ItemController.class)
@AutoConfigureMockMvc(addFilters = false)
class ItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ItemService itemService;

    @Test
    void type이_없으면_500이_아니라_400과_공통형식으로_응답() throws Exception {
        mockMvc.perform(post("/api/workspaces/1/items")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(itemService, never()).createFromRequest(any(), any(), any());
    }

    @Test
    void 필수_헤더가_없으면_공통형식_400() throws Exception {
        mockMvc.perform(post("/api/workspaces/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"MEMO\",\"content\":\"메모\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void 정상_저장은_201과_PROCESSING() throws Exception {
        when(itemService.createFromRequest(eq(1L), eq(1L), any(ItemCreateRequest.class)))
                .thenReturn(new ItemCreateResponse(42L, ItemStatus.PROCESSING, Instant.now()));

        mockMvc.perform(post("/api/workspaces/1/items")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"URL\",\"url\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.itemId").value(42))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    void 없는_아이템_조회는_공통형식_404() throws Exception {
        when(itemService.list(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new ItemNotFoundException(999L));

        mockMvc.perform(get("/api/workspaces/1/items"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
