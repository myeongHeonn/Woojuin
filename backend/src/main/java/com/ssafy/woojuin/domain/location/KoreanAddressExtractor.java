package com.ssafy.woojuin.domain.location;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 텍스트에서 한국 주소를 뽑는다 (FR-023). 순수 함수 — 지도 링크가 아닌 글(맛집 후기 등)에서
 * 좌표를 얻기 위한 <b>임시</b> 경로다.
 *
 * <p>정규식은 광역 단위로 <b>시작</b>하고 건물번호로 <b>끝나는</b> 것을 둘 다 요구한다.
 * 둘 중 하나만 요구하면 산문이 대량으로 걸린다 — "서울 강남 맛집 추천"은 번호가 없어서,
 * "123-4번지"는 광역 단위가 없어서 각각 탈락한다.
 *
 * <p><b>오탐보다 훨씬 큰 위험은 "틀린 주소"다.</b> 맛집 후기엔 주제 가게, "근처 다른 맛집",
 * 블로거 사무실, 광고 주소가 2~5개씩 섞여 있고 정규식은 어느 게 이 글의 주제인지 모른다.
 * 그래서 두 가지로 막는다.
 *
 * <ol>
 *   <li><b>소스 우선순위가 정규식 품질보다 중요하다.</b> 호출부가 og:description → title →
 *       본문 순으로 넘기고 첫 히트에서 멈춘다. og:description은 이 페이지의 주제를 설명하는
 *       텍스트라 주제 주소가 잡힐 확률이 본문보다 훨씬 높다.</li>
 *   <li><b>지오코더가 검증기 역할을 한다.</b> 뽑은 문자열로 좌표를 못 얻으면 그냥 버린다.
 *       덕분에 형태만 주소 같은 오탐이 무해해진다.</li>
 * </ol>
 *
 * <p>한 텍스트 안에서는 가장 긴 매칭이 아니라 <b>첫 매칭</b>을 취한다 — 한국 블로그는
 * 주소·영업시간 블록을 글 상단에 두는 관습이 있다.
 *
 * <p>묶음 F의 실제 AI 분석기가 오면 LLM이 <i>주제</i> 장소를 훨씬 정확히 뽑는다. 그때
 * {@link LocationResolver}에 소스 하나를 앞에 끼우면 되고 이 정규식은 폴백으로 남거나
 * 삭제된다.
 */
@Slf4j
@Component
public class KoreanAddressExtractor {

    private static final Pattern KOREAN_ADDRESS = Pattern.compile(
            // 1) 광역 단위로 시작 — 이게 없으면 오검출이 폭발한다
            "(?:서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충청북도|충남|충청남도|"
            + "전북|전라북도|전남|전라남도|경북|경상북도|경남|경상남도|제주)"
            + "(?:특별자치시|특별자치도|특별시|광역시|도)?"
            // 2) 시/군/구 0~2단위. '용인시 기흥구' 같은 2단계를 허용하고, 0단계도 허용하는 건
            //    세종특별자치시가 시/군/구 하위 단계 없이 바로 읍/면/동으로 가기 때문이다
            //    ("세종특별자치시 조치원읍 새롬로 12"). 0단계를 허용해도 아래 3)이 읍/면/동/도로명을
            //    최소 1단위 요구하므로 "서울 맛집 추천" 류는 그대로 탈락한다.
            + "\\s+(?:[가-힣]{1,10}(?:시|군|구)\\s+){0,2}"
            // 3) 읍/면/동/리 + 도로명 1~3단위
            + "(?:[가-힣0-9]{1,15}(?:읍|면|동|리|대로|로|길|가)\\s*){1,3}"
            // 4) 건물번호로 끝난다 (49 / 49-3 / 17번길 49)
            + "\\d{1,4}(?:-\\d{1,5})?(?:번길\\s*\\d{1,4}(?:-\\d{1,5})?)?");

    /** 이보다 짧으면 주소로 보기 어렵다. */
    private static final int MIN_LENGTH = 10;

    /**
     * 우선순위가 높은 텍스트부터 넘긴다 (og:description → title → 본문).
     *
     * @return 첫 히트. 아무것도 못 찾으면 empty (정상 결과다)
     */
    public Optional<String> extract(String... texts) {
        if (texts == null) {
            return Optional.empty();
        }
        for (String text : texts) {
            Optional<String> found = extractOne(text);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private Optional<String> extractOne(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = KOREAN_ADDRESS.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String address = matcher.group().trim();
        if (address.length() < MIN_LENGTH) {
            return Optional.empty();
        }
        // 스테이징 로그로 실제 오탐을 눈으로 보는 게 정규식 튜닝보다 품질에 기여한다.
        // 사용자 콘텐츠라서 info가 아니라 debug다.
        log.debug("본문에서 주소 후보 추출: '{}'", address);
        return Optional.of(address);
    }
}
