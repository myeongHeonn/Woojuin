package com.ssafy.woojuin.domain.ai.usage;

import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceRole;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AiUsageService {

    public static final String UNLIMITED_USERS_KEY = "woojuin:ai-usage:unlimited-users";

    private static final String USAGE_KEY_PREFIX = "woojuin:ai-usage:monthly:";
    private static final String RESERVATION_KEY_PREFIX = "woojuin:ai-usage:reservation:";
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private static final DefaultRedisScript<String> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
                return 'UNLIMITED'
            end

            local used = tonumber(redis.call('GET', KEYS[1]) or '0')
            local limit = tonumber(ARGV[2])
            if used >= limit then
                return 'DENIED'
            end

            redis.call('INCR', KEYS[1])
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            redis.call('SET', KEYS[3], '1', 'EX', ARGV[4])
            return 'COUNTED'
            """, String.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('DEL', KEYS[1]) == 0 then
                return 0
            end

            local used = tonumber(redis.call('GET', KEYS[2]) or '0')
            if used > 0 then
                redis.call('DECR', KEYS[2])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final AiUsageProperties properties;

    public AiUsageService(
            StringRedisTemplate redisTemplate,
            WorkspaceMemberRepository workspaceMemberRepository,
            AiUsageProperties properties) {
        this.redisTemplate = redisTemplate;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.properties = properties;
    }

    /**
     * 아이템 저장 전에 대표 OWNER의 이번 달 사용량 한 건을 예약한다. 한도 초과 또는 Redis 장애면
     * 미완성 아이템을 남기지 않도록 생성 API 자체를 실패시킨다.
     */
    public AiUsageReservation reserveForItemCreation(Long workspaceId) {
        if (!properties.enabled()) {
            return AiUsageReservation.disabled();
        }

        Long ownerId = representativeOwnerId(workspaceId);
        if (ownerId == null) {
            throw new AiUsageUnavailableException(
                    "AI_USAGE_OWNER_NOT_FOUND: 워크스페이스의 활성 OWNER를 찾을 수 없습니다");
        }

        ZonedDateTime now = ZonedDateTime.now(SERVICE_ZONE);
        YearMonth period = YearMonth.from(now);
        String usageKey = usageKey(period, ownerId);
        String reservationKey = RESERVATION_KEY_PREFIX + UUID.randomUUID();
        String result;
        try {
            result = redisTemplate.execute(
                    RESERVE_SCRIPT,
                    List.of(usageKey, UNLIMITED_USERS_KEY, reservationKey),
                    String.valueOf(ownerId),
                    String.valueOf(properties.monthlyItemLimit()),
                    String.valueOf(usageTtlSeconds(now, period)),
                    String.valueOf(Duration.ofMinutes(properties.reservationTtlMinutes()).toSeconds()));
        } catch (RuntimeException e) {
            throw new AiUsageUnavailableException(
                    "AI_USAGE_UNAVAILABLE: 사용량을 확인할 수 없어 아이템을 생성하지 않았습니다", e);
        }

        if ("UNLIMITED".equals(result)) {
            return AiUsageReservation.unlimited(ownerId);
        }
        if ("COUNTED".equals(result)) {
            return AiUsageReservation.counted(ownerId, usageKey, reservationKey);
        }
        throw new AiUsageLimitExceededException(properties.monthlyItemLimit());
    }

    /**
     * DB 저장이 끝난 예약을 확정한다. 카운터는 예약 시 이미 증가했고, 여기서는 실패 시 되돌리기용
     * 임시 키만 제거하므로 Redis 일시 장애가 아이템 저장 성공을 뒤집지는 않는다.
     */
    public void commit(AiUsageReservation reservation) {
        if (!reservation.counted()) {
            return;
        }
        try {
            redisTemplate.delete(reservation.reservationKey());
        } catch (RuntimeException e) {
            log.warn("AI 사용량 예약 확정 키 정리 실패(카운터는 유지): ownerId={}, cause={}",
                    reservation.billedUserId(), e.toString());
        }
    }

    /**
     * S3 업로드나 DB 저장이 실패하면 예약한 한 건을 멱등적으로 되돌린다. 임시 키를 먼저 지운 호출만
     * 카운터를 감소시키므로 같은 실패 정리가 두 번 실행돼도 추가 차감 취소가 일어나지 않는다.
     */
    public void release(AiUsageReservation reservation) {
        if (!reservation.counted()) {
            return;
        }
        try {
            redisTemplate.execute(
                    RELEASE_SCRIPT,
                    List.of(reservation.reservationKey(), reservation.usageKey()));
        } catch (RuntimeException e) {
            log.error("실패한 아이템의 AI 사용량 예약 복구 실패: ownerId={}",
                    reservation.billedUserId(), e);
        }
    }

    public AiUsageResponse getUsage(Long userId) {
        ZonedDateTime now = ZonedDateTime.now(SERVICE_ZONE);
        YearMonth period = YearMonth.from(now);
        OffsetDateTime resetAt = period.plusMonths(1).atDay(1).atStartOfDay(SERVICE_ZONE).toOffsetDateTime();

        if (!properties.enabled()) {
            return new AiUsageResponse(
                    period.toString(), 0, properties.monthlyItemLimit(), null,
                    false, false, resetAt);
        }

        try {
            boolean unlimited = Boolean.TRUE.equals(
                    redisTemplate.opsForSet().isMember(UNLIMITED_USERS_KEY, String.valueOf(userId)));
            long used = parseUsage(redisTemplate.opsForValue().get(usageKey(period, userId)));
            Long remaining = unlimited ? null : Math.max(0, properties.monthlyItemLimit() - used);
            return new AiUsageResponse(
                    period.toString(), used, properties.monthlyItemLimit(), remaining,
                    unlimited, true, resetAt);
        } catch (RuntimeException e) {
            throw new AiUsageUnavailableException(
                    "AI_USAGE_UNAVAILABLE: 사용량을 조회할 수 없습니다", e);
        }
    }

    private Long representativeOwnerId(Long workspaceId) {
        return workspaceMemberRepository
                .findFirstByWorkspaceIdAndRoleAndUserDeletedAtIsNullOrderByJoinedAtAscIdAsc(
                        workspaceId, WorkspaceRole.OWNER)
                .map(WorkspaceMember::getUser)
                .map(user -> user.getId())
                .orElse(null);
    }

    private String usageKey(YearMonth period, Long userId) {
        return USAGE_KEY_PREFIX + period.format(PERIOD_FORMATTER) + ":" + userId;
    }

    private long usageTtlSeconds(ZonedDateTime now, YearMonth period) {
        ZonedDateTime expiresAt = period.plusMonths(1)
                .atDay(1)
                .atStartOfDay(SERVICE_ZONE)
                .plusDays(7);
        return Math.max(1, Duration.between(now, expiresAt).toSeconds());
    }

    private long parseUsage(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("잘못된 Redis AI 사용량 값을 0으로 처리: value={}", value);
            return 0;
        }
    }
}
