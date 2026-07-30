package com.ssafy.woojuin.domain.auth.service;

import com.ssafy.woojuin.domain.auth.dto.UserStatsResponse;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserStatsService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final ItemRepository itemRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public UserStatsService(
            ItemRepository itemRepository,
            WorkspaceMemberRepository workspaceMemberRepository) {
        this.itemRepository = itemRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    @Transactional(readOnly = true)
    public UserStatsResponse getStats(Long userId) {
        OffsetDateTime weekStartedAt = startOfWeek(OffsetDateTime.now(SERVICE_ZONE));

        long totalSaved = itemRepository.countByCreatedByAndDeletedAtIsNull(userId);
        long workspaceCount = workspaceMemberRepository.countByUserId(userId);
        long savedThisWeek =
                itemRepository.countByCreatedByAndDeletedAtIsNullAndCreatedAtGreaterThanEqual(
                        userId, weekStartedAt);

        return new UserStatsResponse(totalSaved, workspaceCount, savedThisWeek);
    }

    static OffsetDateTime startOfWeek(OffsetDateTime now) {
        return now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate()
                .atStartOfDay()
                .atZone(SERVICE_ZONE)
                .toOffsetDateTime();
    }
}
