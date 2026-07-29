package com.ssafy.woojuin.domain.item.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.woojuin.domain.item.dto.ItemGeoResponse;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.exception.WorkspaceAccessDeniedException;
import com.ssafy.woojuin.domain.item.service.ItemGeoService;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceNotFoundException;
import com.ssafy.woojuin.global.security.aop.AuthenticationAspect;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 지도 조회 API 웹 계층 슬라이스 테스트 (FR-032).
 *
 * <p>프론트와 합의한 응답 형태를 여기서 고정한다 — 특히 목록 응답 필드(summary·preview 등)가
 * 섞여 들어오지 않는지까지 검증해서, 나중에 누가 ItemSummaryResponse로 갈아끼우는 걸 막는다.
 */
@WebMvcTest(controllers = ItemGeoController.class,
        excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({CurrentUserResolver.class, AuthenticationAspect.class})
@EnableAspectJAutoProxy
class ItemGeoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ItemGeoService itemGeoService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Long userId) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    @Test
    void 좌표_아이템_목록을_평면_배열로_응답한다() throws Exception {
        authenticateAs(10L);
        when(itemGeoService.geoItems(eq(1L), eq(10L))).thenReturn(List.of(
                new ItemGeoResponse(42L, ItemType.URL, "성수동 맛집", List.of(1L, 2L), true,
                        37.5445, 127.0561, "서울 성동구 아차산로17길 49")));

        mockMvc.perform(get("/api/workspaces/1/items/geo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data[0].itemId").value(42))
                .andExpect(jsonPath("$.data[0].type").value("URL"))
                .andExpect(jsonPath("$.data[0].title").value("성수동 맛집"))
                .andExpect(jsonPath("$.data[0].categoryIds[0]").value(1))
                .andExpect(jsonPath("$.data[0].categoryIds[1]").value(2))
                .andExpect(jsonPath("$.data[0].favorite").value(true))
                .andExpect(jsonPath("$.data[0].lat").value(37.5445))
                .andExpect(jsonPath("$.data[0].lng").value(127.0561))
                .andExpect(jsonPath("$.data[0].address").value("서울 성동구 아차산로17길 49"));
    }

    @Test
    void title과_address는_null로_내려갈_수_있다() throws Exception {
        // 프론트 MapPlace 타입이 address를 string으로 선언해 뒀는데, 지도 공유 링크는
        // 좌표만 주므로 실제로는 null이 내려간다. 계약을 여기서 명시해 둔다.
        authenticateAs(10L);
        when(itemGeoService.geoItems(eq(1L), eq(10L))).thenReturn(List.of(
                new ItemGeoResponse(42L, ItemType.IMAGE, null, List.of(), false,
                        37.5445, 127.0561, null)));

        mockMvc.perform(get("/api/workspaces/1/items/geo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value(nullValue()))
                .andExpect(jsonPath("$.data[0].address").value(nullValue()))
                .andExpect(jsonPath("$.data[0].favorite").value(false));
    }

    @Test
    void 목록_응답_전용_필드는_포함하지_않는다() throws Exception {
        authenticateAs(10L);
        when(itemGeoService.geoItems(eq(1L), eq(10L))).thenReturn(List.of(
                new ItemGeoResponse(42L, ItemType.URL, "성수동 맛집", List.of(), false,
                        37.5445, 127.0561, null)));

        mockMvc.perform(get("/api/workspaces/1/items/geo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].summary").doesNotExist())
                .andExpect(jsonPath("$.data[0].preview").doesNotExist())
                .andExpect(jsonPath("$.data[0].imageUrl").doesNotExist())
                .andExpect(jsonPath("$.data[0].status").doesNotExist())
                .andExpect(jsonPath("$.data[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].deletedAt").doesNotExist())
                // favorite은 지도의 즐겨찾기 필터가 쓰므로 포함된다(제외 목록이 아니다).
                .andExpect(jsonPath("$.data[0].favorite").exists())
                // categories(객체 배열)가 아니라 categoryIds(평면 배열)여야 한다.
                .andExpect(jsonPath("$.data[0].categories").doesNotExist())
                .andExpect(jsonPath("$.data[0].categoryIds").exists());
    }

    @Test
    void 좌표_아이템이_없으면_빈_배열() throws Exception {
        authenticateAs(10L);
        when(itemGeoService.geoItems(eq(1L), eq(10L))).thenReturn(List.of());

        mockMvc.perform(get("/api/workspaces/1/items/geo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void 미인증이면_401이고_서비스를_호출하지_않는다() throws Exception {
        mockMvc.perform(get("/api/workspaces/1/items/geo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verify(itemGeoService, never()).geoItems(eq(1L), eq(10L));
    }

    @Test
    void 멤버가_아니면_403() throws Exception {
        authenticateAs(10L);
        when(itemGeoService.geoItems(eq(1L), eq(10L)))
                .thenThrow(new WorkspaceAccessDeniedException(1L));

        mockMvc.perform(get("/api/workspaces/1/items/geo"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void 워크스페이스가_없으면_404() throws Exception {
        authenticateAs(10L);
        when(itemGeoService.geoItems(eq(1L), eq(10L)))
                .thenThrow(new WorkspaceNotFoundException(1L));

        mockMvc.perform(get("/api/workspaces/1/items/geo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
