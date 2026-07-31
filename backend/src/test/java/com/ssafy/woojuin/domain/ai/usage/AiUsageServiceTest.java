package com.ssafy.woojuin.domain.ai.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceRole;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class AiUsageServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AiUsageService service;

    @BeforeEach
    void setUp() {
        service = new AiUsageService(
                redisTemplate, workspaceMemberRepository, new AiUsageProperties(true, 50, 60));
    }

    @Test
    void MEMBER가_생성해도_워크스페이스_OWNER에게_한_건을_예약한다() {
        mockOwner(10L, 99L);
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn("COUNTED");

        AiUsageReservation reservation = service.reserveForItemCreation(10L);

        assertThat(reservation.counted()).isTrue();
        assertThat(reservation.billedUserId()).isEqualTo(99L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(
                any(RedisScript.class), keys.capture(), any(), any(), any(), any());
        assertThat(keys.getValue())
                .anyMatch(key -> key.startsWith("woojuin:ai-usage:monthly:") && key.endsWith(":99"))
                .contains(AiUsageService.UNLIMITED_USERS_KEY);
    }

    @Test
    void 월_50건에_도달하면_아이템_생성을_거부한다() {
        mockOwner(10L, 99L);
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn("DENIED");

        assertThatThrownBy(() -> service.reserveForItemCreation(10L))
                .isInstanceOf(AiUsageLimitExceededException.class)
                .hasMessageContaining("50개");
    }

    @Test
    void 무제한_OWNER는_카운터를_예약하지_않는다() {
        mockOwner(10L, 99L);
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn("UNLIMITED");

        AiUsageReservation reservation = service.reserveForItemCreation(10L);

        assertThat(reservation.counted()).isFalse();
        assertThat(reservation.status()).isEqualTo(AiUsageReservation.Status.UNLIMITED);
        assertThat(reservation.billedUserId()).isEqualTo(99L);
    }

    @Test
    void 제한을_끄면_OWNER나_Redis를_조회하지_않고_생성을_허용한다() {
        service = new AiUsageService(
                redisTemplate, workspaceMemberRepository, new AiUsageProperties(false, 50, 60));

        AiUsageReservation reservation = service.reserveForItemCreation(10L);

        assertThat(reservation.status()).isEqualTo(AiUsageReservation.Status.DISABLED);
        verifyNoInteractions(redisTemplate, workspaceMemberRepository);
    }

    @Test
    void 사용량_조회는_월_50건과_남은_횟수를_반환한다() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(setOperations.isMember(AiUsageService.UNLIMITED_USERS_KEY, "99")).thenReturn(false);
        when(valueOperations.get(any())).thenReturn("12");

        AiUsageResponse response = service.getUsage(99L);

        assertThat(response.used()).isEqualTo(12);
        assertThat(response.limit()).isEqualTo(50);
        assertThat(response.remaining()).isEqualTo(38);
        assertThat(response.unlimited()).isFalse();
    }

    @Test
    void 활성_OWNER가_없으면_아이템_생성을_거부한다() {
        when(workspaceMemberRepository
                .findFirstByWorkspaceIdAndRoleAndUserDeletedAtIsNullOrderByJoinedAtAscIdAsc(
                        10L, WorkspaceRole.OWNER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserveForItemCreation(10L))
                .isInstanceOf(AiUsageUnavailableException.class)
                .hasMessageContaining("OWNER");
    }

    private void mockOwner(Long workspaceId, Long ownerId) {
        WorkspaceMember membership = mock(WorkspaceMember.class);
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerId);
        when(membership.getUser()).thenReturn(owner);
        when(workspaceMemberRepository
                .findFirstByWorkspaceIdAndRoleAndUserDeletedAtIsNullOrderByJoinedAtAscIdAsc(
                        workspaceId, WorkspaceRole.OWNER))
                .thenReturn(Optional.of(membership));
    }
}
