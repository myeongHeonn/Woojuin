package com.ssafy.woojuin.domain.location;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 한국 주소 추출 단위 테스트.
 *
 * <p>정확도가 아니라 <b>안전성</b>을 본다 — 주소를 몇 개 놓치는 건 핀이 안 뜰 뿐이지만,
 * 주소가 아닌 걸 주소로 잡으면 엉뚱한 곳에 핀이 꽂힌다. 그래서 오탐 케이스를 정상 케이스보다
 * 더 많이 둔다.
 */
class KoreanAddressExtractorTest {

    private final KoreanAddressExtractor extractor = new KoreanAddressExtractor();

    // ---------- 잡아야 하는 것 ----------

    @Test
    void 도로명_주소를_잡는다() {
        assertThat(extractor.extract("주소: 서울 성동구 아차산로 49 1층"))
                .contains("서울 성동구 아차산로 49");
    }

    @Test
    void 번길이_들어간_도로명도_잡는다() {
        assertThat(extractor.extract("서울 성동구 아차산로17번길 49"))
                .contains("서울 성동구 아차산로17번길 49");
    }

    @Test
    void 지번_주소를_잡는다() {
        assertThat(extractor.extract("가게는 서울 성동구 성수동2가 333-12 에 있어요"))
                .contains("서울 성동구 성수동2가 333-12");
    }

    @Test
    void 특별시_광역시_표기를_붙여도_잡는다() {
        assertThat(extractor.extract("서울특별시 성동구 아차산로 49"))
                .contains("서울특별시 성동구 아차산로 49");
        assertThat(extractor.extract("부산광역시 해운대구 우동로 22"))
                .contains("부산광역시 해운대구 우동로 22");
    }

    @Test
    void 특별자치시도_표기를_잡는다() {
        assertThat(extractor.extract("제주특별자치도 서귀포시 성산읍 신풍리 123"))
                .contains("제주특별자치도 서귀포시 성산읍 신풍리 123");
    }

    @Test
    void 세종은_시군구_단계가_없어도_잡는다() {
        // 세종특별자치시는 하위 시/군/구가 없이 바로 읍/면/동으로 간다. 시/군/구를 필수로
        // 요구하면 세종 주소 전체를 놓친다.
        assertThat(extractor.extract("세종특별자치시 조치원읍 새롬로 12"))
                .contains("세종특별자치시 조치원읍 새롬로 12");
        assertThat(extractor.extract("세종특별자치시 한솔동 123-4"))
                .contains("세종특별자치시 한솔동 123-4");
    }

    @Test
    void 시_구_2단계도_잡는다() {
        assertThat(extractor.extract("경기 용인시 기흥구 동백중앙로 191"))
                .contains("경기 용인시 기흥구 동백중앙로 191");
    }

    // ---------- 잡으면 안 되는 것 ----------

    @Test
    void 건물번호가_없으면_주소로_보지_않는다() {
        assertThat(extractor.extract("서울 강남 맛집 추천")).isEmpty();
        assertThat(extractor.extract("이번 주말엔 경기 광주에 다녀왔다")).isEmpty();
        assertThat(extractor.extract("부산 해운대구 카페 투어")).isEmpty();
    }

    @Test
    void 광역_단위가_없으면_주소로_보지_않는다() {
        assertThat(extractor.extract("성동구 아차산로 49")).isEmpty();
        assertThat(extractor.extract("아차산로17번길 49")).isEmpty();
    }

    @Test
    void 전화번호를_주소로_잡지_않는다() {
        assertThat(extractor.extract("문의는 02-1234-5678 로 주세요")).isEmpty();
    }

    @Test
    void 지하철_노선_표기를_주소로_잡지_않는다() {
        assertThat(extractor.extract("지하철 3호선 타고 10분")).isEmpty();
        assertThat(extractor.extract("영업시간 11:00 - 22:00, 라스트오더 21:30")).isEmpty();
    }

    // ---------- 동작 계약 ----------

    @Test
    void 우선순위가_높은_텍스트에서_먼저_찾는다() {
        // og:description(주제 주소) 이 본문(다른 가게 주소)보다 앞선다.
        String ogDescription = "성수동 이탈리안, 서울 성동구 아차산로 49";
        String content = "근처 다른 맛집도 있어요 — 서울 마포구 동교로 12 도 추천";

        assertThat(extractor.extract(ogDescription, content))
                .contains("서울 성동구 아차산로 49");
    }

    @Test
    void 앞_텍스트에_없으면_뒤_텍스트로_넘어간다() {
        assertThat(extractor.extract("제목만 있는 글", null, "서울 성동구 아차산로 49 지하 1층"))
                .contains("서울 성동구 아차산로 49");
    }

    @Test
    void 한_텍스트_안에서는_첫_매칭을_쓴다() {
        // 한국 블로그는 주소 블록을 상단에 두는 관습이 있다.
        String content = "서울 성동구 아차산로 49 가 이 글의 주제고, 서울 마포구 동교로 12 는 다른 가게다";

        assertThat(extractor.extract(content)).contains("서울 성동구 아차산로 49");
    }

    @Test
    void null과_빈_입력을_견딘다() {
        assertThat(extractor.extract((String[]) null)).isEmpty();
        assertThat(extractor.extract()).isEmpty();
        assertThat(extractor.extract((String) null)).isEmpty();
        assertThat(extractor.extract("", "   ")).isEmpty();
    }
}
