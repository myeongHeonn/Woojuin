package com.ssafy.woojuin.domain.item.service;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * items 조회 성능에 필요한 인덱스를 기동 시 한 번 맞춘다 (목록 조회 + 통합 검색 FR-030 +
 * 지도 조회 FR-032).
 *
 * <p>이 프로젝트는 ddl-auto=update만 쓰고 Flyway 같은 마이그레이션 도구가 없다. 부분 인덱스는
 * Hibernate가 만들어주지 못하므로, 기존 자가치유 러너(CategoryColorBackfill,
 * PersonalWorkspaceBackfillRunner)와 같은 방식으로 여기서 처리한다. 모든 DDL이
 * IF [NOT] EXISTS라 재기동해도 안전하다(idempotent).
 *
 * <p>Hibernate가 items 테이블을 만든 뒤에 돌아야 하므로 ApplicationReadyEvent에 건다.
 *
 * <p><b>주의</b>: 여기에 GENERATED 컬럼을 추가하면 안 된다. ddl-auto=update인 Hibernate가 매
 * 기동마다 {@code alter table items alter column content set data type text}를 다시 던지는데
 * PostgreSQL이 생성 컬럼 참조 컬럼의 타입 변경을 거부해, 두 번째 기동부터 앱이 뜨지 않는다.
 */
@Slf4j
@Component
public class ItemIndexInitializer {

    private static final List<String> STATEMENTS = List.of(
            // 활성 아이템 조회의 기본 인덱스. 목록(ORDER BY created_at DESC LIMIT n)과
            // 검색이 함께 쓴다.
            //
            // deleted_at을 인덱스 "컬럼"이 아니라 WHERE 조건으로 뺀 게 핵심이다. 컬럼으로
            // 넣으면(ERD 설계 노트의 3컬럼안) btree가 정렬 순서를 보장하지 못해 플래너가 항상
            // Bitmap 스캔 + Sort로 떨어지고, LIMIT 28인데도 워크스페이스의 활성 아이템을 전부
            // 읽는다. 부분 인덱스로 두면 정렬 컬럼이 (workspace_id, created_at)만 남아
            // Index Scan으로 앞 28건만 읽고 끝난다 — 20만 건/대상 1만 건 기준 6.4ms→0.19ms.
            //
            // 검색은 관련도로 정렬하므로 created_at 순서를 활용하지 못하지만, 대상을 해당
            // 워크스페이스의 활성 아이템(많아야 1000건 수준)으로 좁혀준다 — 검색이 텍스트
            // 인덱스 없이도 성립하는 건 전적으로 이 인덱스 덕분이다.
            //
            // 휴지통 조회(deleted_at IS NOT NULL)는 이 인덱스를 쓰지 못한다. 저트래픽 화면이라
            // 지금은 순차 스캔을 감수하고, 느려지면 반대 조건의 부분 인덱스를 따로 추가할 것.
            "CREATE INDEX IF NOT EXISTS idx_items_ws_active ON items "
                    + "(workspace_id, created_at DESC) WHERE deleted_at IS NULL",

            // 지도 조회(GET /items/geo FR-032) 전용 부분 인덱스. 좌표가 있는 아이템은
            // 워크스페이스 전체의 소수다(링크·메모 대부분은 위치가 없다) — 조건을 인덱스
            // WHERE로 밀어넣으면 인덱스가 아주 작아지고, 지도 조회가 그 아이템들만 정확히 집는다.
            // 좌표를 별도 테이블로 분리하지 않고 items 컬럼으로 둘 수 있는 근거가 이 인덱스다.
            //
            // 정렬 컬럼을 두지 않은 건 지도 조회에 ORDER BY도 페이지네이션도 없기 때문이다
            // (응답이 좌표 배열 하나). INCLUDE(lat, lng)도 두지 않는다 — title·type을 함께
            // 읽어야 해서 어차피 힙을 타므로 index-only scan이 성립하지 않는다.
            //
            // 클래스 javadoc의 경고대로 GENERATED 컬럼이나 text 컬럼 참조 표현식 인덱스는
            // 넣지 않는다. lat/lng/deleted_at은 모두 실제 컬럼이라 안전하다.
            "CREATE INDEX IF NOT EXISTS idx_items_ws_geo ON items (workspace_id) "
                    + "WHERE lat IS NOT NULL AND lng IS NOT NULL AND deleted_at IS NULL");

    private final JdbcTemplate jdbcTemplate;

    public ItemIndexInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * DDL이 실패해도 기동은 계속한다 — 인덱스 하나 때문에 저장·목록까지 죽는 게 더 나쁘다.
     * 대신 error 로그를 남긴다(이 상태로도 기능은 동작하지만 순차 스캔이라 느려진다).
     *
     * <p>실패한 문장에서 멈추지 않고 계속 진행한다. 문장이 서로 독립적이라, 하나가 깨졌다고
     * 뒤 인덱스까지 조용히 건너뛰면 원인이 훨씬 찾기 어려워진다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        int failed = 0;
        for (String statement : STATEMENTS) {
            try {
                jdbcTemplate.execute(statement);
            } catch (RuntimeException e) {
                failed++;
                log.error("아이템 인덱스 준비 실패 — 조회가 순차 스캔으로 떨어진다. statement={}, cause={}",
                        statement, e.toString());
            }
        }
        if (failed == 0) {
            log.info("아이템 인덱스 준비 완료 (활성 아이템 부분 인덱스 + 지도 좌표 부분 인덱스)");
        } else {
            log.warn("아이템 인덱스 준비 완료 (실패 {}/{}건)", failed, STATEMENTS.size());
        }
    }
}
