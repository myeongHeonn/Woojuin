package com.ssafy.woojuin.domain.item.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 지도 좌표 반영 규칙(FR-023) 단위 테스트.
 *
 * <p>여기서 검증하는 세 가지가 이 도메인 메서드의 존재 이유다 — null은 기존 값을 지우지 않고,
 * 좌표는 쌍으로만 반영되며, 범위를 벗어난 값은 조용히 버려진다(투영 좌표 오독 방어선).
 */
class ItemLocationTest {

    private static final double SEOUL_LAT = 37.5445;
    private static final double SEOUL_LNG = 127.0561;

    private Item urlItem() {
        return Item.builder().workspaceId(1L).createdBy(1L).type(ItemType.URL)
                .url("https://example.com").build();
    }

    private Item itemAtSeoul() {
        Item item = urlItem();
        item.applyLocation(SEOUL_LAT, SEOUL_LNG, "서울 성동구 아차산로17길 49");
        return item;
    }

    @Test
    void applyLocation_좌표와_주소를_반영한다() {
        Item item = urlItem();
        assertThat(item.hasCoordinates()).isFalse();

        item.applyLocation(SEOUL_LAT, SEOUL_LNG, "서울 성동구 아차산로17길 49");

        assertThat(item.getLat()).isEqualTo(SEOUL_LAT);
        assertThat(item.getLng()).isEqualTo(SEOUL_LNG);
        assertThat(item.getAddress()).isEqualTo("서울 성동구 아차산로17길 49");
        assertThat(item.hasCoordinates()).isTrue();
    }

    @Test
    void applyLocation_null은_기존값을_지우지_않는다() {
        Item item = itemAtSeoul();

        item.applyLocation(null, null, null);

        assertThat(item.getLat()).isEqualTo(SEOUL_LAT);
        assertThat(item.getLng()).isEqualTo(SEOUL_LNG);
        assertThat(item.getAddress()).isEqualTo("서울 성동구 아차산로17길 49");
    }

    @Test
    void applyLocation_한쪽만_있는_좌표는_무시한다() {
        Item item = urlItem();

        item.applyLocation(SEOUL_LAT, null, null);
        assertThat(item.hasCoordinates()).isFalse();
        assertThat(item.getLat()).isNull();

        item.applyLocation(null, SEOUL_LNG, null);
        assertThat(item.hasCoordinates()).isFalse();
        assertThat(item.getLng()).isNull();
    }

    @Test
    void applyLocation_주소는_좌표와_독립적으로_반영된다() {
        // 역지오코딩이 실패해 주소만 없는 경우 — 핀이 목적이므로 좌표는 살아야 한다.
        Item item = urlItem();
        item.applyLocation(SEOUL_LAT, SEOUL_LNG, null);

        assertThat(item.hasCoordinates()).isTrue();
        assertThat(item.getAddress()).isNull();

        // 반대로 좌표 없이 주소만 오는 경우 — 주소는 남지만 지도엔 뜨지 않는다.
        Item other = urlItem();
        other.applyLocation(null, null, "서울 성동구 성수동");

        assertThat(other.hasCoordinates()).isFalse();
        assertThat(other.getAddress()).isEqualTo("서울 성동구 성수동");
    }

    @Test
    void applyLocation_빈_주소는_기존값을_지우지_않는다() {
        Item item = itemAtSeoul();

        item.applyLocation(SEOUL_LAT, SEOUL_LNG, "   ");

        assertThat(item.getAddress()).isEqualTo("서울 성동구 아차산로17길 49");
    }

    @Test
    void applyLocation_범위를_벗어난_좌표는_무시한다() {
        Item item = urlItem();

        item.applyLocation(91.0, SEOUL_LNG, null);
        item.applyLocation(-91.0, SEOUL_LNG, null);
        item.applyLocation(SEOUL_LAT, 181.0, null);
        item.applyLocation(SEOUL_LAT, -181.0, null);

        assertThat(item.hasCoordinates()).isFalse();
    }

    @Test
    void applyLocation_투영좌표를_잘못_읽은_값은_거부된다() {
        // 카카오 WCONGNAMUL(urlX/urlY) 같은 투영 좌표를 lat/lng로 오독한 사고.
        // 파서에서 먼저 걸러내지만 엔티티가 마지막 방어선이다.
        Item item = urlItem();

        item.applyLocation(1120000.0, 507000.0, null);

        assertThat(item.hasCoordinates()).isFalse();
    }

    @Test
    void applyLocation_0_0은_실제_좌표로_취급하지_않는다() {
        // EXIF 누락·파싱 실패의 전형적 산출물이다.
        Item item = urlItem();

        item.applyLocation(0.0, 0.0, null);

        assertThat(item.hasCoordinates()).isFalse();
    }

    @Test
    void applyLocation_경계값은_허용한다() {
        Item item = urlItem();

        item.applyLocation(-90.0, 180.0, null);

        assertThat(item.getLat()).isEqualTo(-90.0);
        assertThat(item.getLng()).isEqualTo(180.0);
    }

    @Test
    void applyLocation_잘못된_좌표가_와도_기존_좌표는_유지된다() {
        Item item = itemAtSeoul();

        item.applyLocation(0.0, 0.0, null);

        assertThat(item.getLat()).isEqualTo(SEOUL_LAT);
        assertThat(item.getLng()).isEqualTo(SEOUL_LNG);
    }
}
