package com.ssafy.woojuin.domain.item.service;

import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemActivityServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private ItemActivityService itemActivityService;

    @Test
    @DisplayName("마지막 확인 시각 이후 아이템 활동이 있으면 true를 반환한다")
    void hasActivitySince_activityExists_returnsTrue() {
        OffsetDateTime lastSeenAt = OffsetDateTime.now().minusHours(1);
        when(itemRepository.existsByWorkspaceIdAndUpdatedAtAfter(10L, lastSeenAt)).thenReturn(true);

        boolean result = itemActivityService.hasActivitySince(10L, lastSeenAt);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("마지막 확인 시각 이후 아이템 활동이 없으면 false를 반환한다")
    void hasActivitySince_noActivity_returnsFalse() {
        OffsetDateTime lastSeenAt = OffsetDateTime.now().minusHours(1);
        when(itemRepository.existsByWorkspaceIdAndUpdatedAtAfter(10L, lastSeenAt)).thenReturn(false);

        boolean result = itemActivityService.hasActivitySince(10L, lastSeenAt);

        assertThat(result).isFalse();
    }
}
