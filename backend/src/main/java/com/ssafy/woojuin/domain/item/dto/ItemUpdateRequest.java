package com.ssafy.woojuin.domain.item.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 아이템 수정 요청. null인 필드는 수정하지 않는다(부분 수정).
 *
 * <p>categoryIds는 보낸 집합으로 카테고리를 <b>교체</b>한다(추가가 아니다). 빈 배열은 400 —
 * 카테고리가 0개인 아이템은 카테고리 필터 화면에서 다시 찾을 방법이 없어져 최소 1개를
 * 프론트·서버 양쪽에서 강제한다. {@code @Size}는 null을 검사하지 않으므로
 * "미변경(null)"과 "빈 선택([])"이 자연히 갈린다.
 *
 * <p>태그 수정은 태그 기능 자체를 구현하지 않기로 결정되어 대상에서 제외됐다.
 */
public record ItemUpdateRequest(
        @Size(max = 500, message = "제목은 500자를 넘을 수 없습니다") String title,
        String content,
        @Size(min = 1, message = "카테고리는 최소 1개를 선택해야 합니다") List<Long> categoryIds) {
}
