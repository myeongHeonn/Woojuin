package com.ssafy.woojuin.domain.item.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** pgvector 문자열 리터럴 직렬화·파싱 왕복 검증. DB 없이 순수 로직만 본다. */
class ItemEmbeddingJdbcRepositoryTest {

    @Test
    void 벡터_리터럴_직렬화와_파싱이_왕복한다() {
        float[] original = {0.25f, -1.5f, 0f, 3.14159f};

        String literal = ItemEmbeddingJdbcRepository.toVectorLiteral(original);
        float[] parsed = ItemEmbeddingJdbcRepository.parseVectorLiteral(literal);

        assertThat(literal).startsWith("[").endsWith("]");
        assertThat(parsed).containsExactly(original);
    }

    /** pgvector가 돌려주는 형식([0.1, 0.2] — 공백 포함 가능)도 파싱된다. */
    @Test
    void 공백이_섞인_리터럴도_파싱한다() {
        assertThat(ItemEmbeddingJdbcRepository.parseVectorLiteral("[0.1, 0.2, -0.3]"))
                .containsExactly(0.1f, 0.2f, -0.3f);
    }

    @Test
    void 빈_벡터는_빈_배열이다() {
        assertThat(ItemEmbeddingJdbcRepository.parseVectorLiteral("[]")).isEmpty();
    }
}
