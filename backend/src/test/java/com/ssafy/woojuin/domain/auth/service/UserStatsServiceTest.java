package com.ssafy.woojuin.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.auth.dto.UserStatsResponse;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserStatsServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Test
    void getStats_returnsNonTrashedItemAndWorkspaceCounts() {
        UserStatsService service = new UserStatsService(itemRepository, workspaceMemberRepository);
        when(itemRepository.countByCreatedByAndDeletedAtIsNull(1L)).thenReturn(128L);
        when(workspaceMemberRepository.countByUserId(1L)).thenReturn(4L);
        when(itemRepository.countByCreatedByAndDeletedAtIsNullAndCreatedAtGreaterThanEqual(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(OffsetDateTime.class)))
                .thenReturn(12L);

        UserStatsResponse response = service.getStats(1L);

        assertThat(response).isEqualTo(new UserStatsResponse(128, 4, 12));
        verify(itemRepository).countByCreatedByAndDeletedAtIsNull(1L);
        verify(workspaceMemberRepository).countByUserId(1L);
    }

    @Test
    void startOfWeek_usesMondayMidnightInSeoul() {
        OffsetDateTime thursday =
                OffsetDateTime.parse("2026-07-30T18:45:00+09:00");

        OffsetDateTime result = UserStatsService.startOfWeek(thursday);

        assertThat(result).isEqualTo(OffsetDateTime.parse("2026-07-27T00:00:00+09:00"));
    }
}
