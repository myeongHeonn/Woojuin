package com.ssafy.woojuin.domain.ai.query;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * LLM 없이 동작하는 기본 플래너. API 키가 없을 때의 기본 빈이자, LLM 호출이 실패했을 때의
 * 폴백이다.
 *
 * <p>하는 일은 "검색에 방해되는 말 걷어내기"뿐이다 — 지시어("그", "저"), 질문 꼬리
 * ("어디였지", "찾아줘"), 조사만 남은 토큰 등을 버린다. <b>오타 교정도 동의어 확장도 못 한다</b>
 * — 그건 LLM만 할 수 있고, 이 구현은 AI 모드가 키 없이도 "그럭저럭 동작"하게 하는 수준이다.
 */
@Component
public class RuleBasedQueryPlanner implements AiQueryPlanner {

    /**
     * 자연어 질문에는 흔하지만 저장된 스크랩 본문에는 거의 없는 말들. 이걸 남겨두면
     * 모든 토큰을 만족해야 하는 AND 검색이 통째로 0건이 된다.
     */
    private static final Set<String> STOP_WORDS = Set.of(
            "그", "저", "이", "그거", "저거", "이거", "것", "거",
            "어디", "언제", "누가", "무엇", "뭐", "뭔가", "어떤", "어느",
            "찾아줘", "찾아", "찾기", "알려줘", "알려", "보여줘", "보여",
            "했더라", "였지", "었지", "인가", "일까", "있나", "있어", "없나",
            "관련", "관련된", "대한", "대해", "저장한", "저장했던", "내가", "나의",
            "좀", "주세요", "해줘", "해주세요", "please", "find", "search", "show");

    /**
     * 두 글자 이상이라 단어의 일부일 가능성이 낮은 조사·어미. 남는 어간이 2글자만 돼도 뗀다.
     */
    private static final List<String> LONG_SUFFIXES = List.of(
            "이었지", "였더라", "했더라", "였지", "었지", "이야", "인데", "은데", "는데",
            "에서", "에게", "으로", "까지", "부터", "이랑", "라고");

    /**
     * 한 글자 조사. 단어의 마지막 글자와 구분이 안 되는 게 많아("제주도"의 "도", "바다"의 "다")
     * 어간이 3글자 이상 남을 때만 뗀다. "제주도"→"제주", "맛집을"→"맛집"처럼 짧은 말이
     * 잘려나가면 사용자에게 보여줄 해석 문구까지 이상해진다.
     */
    private static final List<String> SHORT_SUFFIXES = List.of(
            "은", "는", "이", "가", "을", "를", "의", "에", "도", "만", "와", "과", "랑");

    /** 한 글자 조사를 떼려면 원본이 이만큼은 돼야 한다(어간 3글자 + 조사 1글자). */
    private static final int MIN_LENGTH_FOR_SHORT_SUFFIX = 4;

    @Override
    public AiQueryPlan plan(String question) {
        return AiQueryPlan.byRule(extractKeywords(question));
    }

    /** LLM 플래너가 실패했을 때도 같은 규칙을 쓰도록 공개한다. */
    public String extractKeywords(String question) {
        if (question == null || question.isBlank()) {
            return "";
        }
        // 물음표·쉼표 등은 검색어에 들어가면 부분일치를 방해하므로 공백으로 바꾼다.
        String normalized = question.replaceAll("[?!.,;:~\"'()\\[\\]{}]", " ");

        List<String> tokens = Arrays.stream(normalized.trim().split("\\s+"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .filter(token -> !STOP_WORDS.contains(token))
                .map(this::stripSuffix)
                .filter(token -> token.length() >= 2)   // 한 글자는 노이즈가 너무 많다
                .filter(token -> !STOP_WORDS.contains(token))
                .distinct()
                .toList();

        // 전부 걸러졌으면 원문을 그대로 넘긴다 — 빈손보다 낫고, 애초에 걸러낼 게
        // 아니었다는 뜻이기도 하다.
        return tokens.isEmpty() ? question.trim() : String.join(" ", tokens);
    }

    /**
     * 조사·어미를 떼어낸다. 길이 조건을 두는 이유는 "제주도"의 "도", "의자"의 "자"처럼
     * 단어의 일부를 조사로 오인해 자르면 사용자에게 보여줄 해석 문구까지 망가지기 때문이다.
     *
     * <p>덜 떼는 쪽이 안전하다 — 검색이 부분일치라 "맛집을"로 검색해도 "맛집을 정리"는
     * 그대로 걸린다. 반대로 잘못 자르면 엉뚱한 말로 검색하게 된다.
     */
    private String stripSuffix(String token) {
        for (String suffix : LONG_SUFFIXES) {
            if (token.length() >= suffix.length() + 2 && token.endsWith(suffix)) {
                return token.substring(0, token.length() - suffix.length());
            }
        }
        for (String suffix : SHORT_SUFFIXES) {
            if (token.length() >= MIN_LENGTH_FOR_SHORT_SUFFIX && token.endsWith(suffix)) {
                return token.substring(0, token.length() - suffix.length());
            }
        }
        return token;
    }
}
