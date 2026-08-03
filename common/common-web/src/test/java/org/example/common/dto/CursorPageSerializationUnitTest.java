package org.example.common.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.config.MessageConverterConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CursorPageSerializationUnitTest {

    // 프로덕션과 동일한 ObjectMapper(NON_EMPTY 포함)로 직렬화한다.
    // @WebMvcTest 슬라이스는 이 설정을 로드하지 않아 필드 누락을 재현하지 못한다.
    private final ObjectMapper mapper = new MessageConverterConfig().objectMapper();

    @Test
    @DisplayName("빈 결과여도 NON_EMPTY 매퍼에서 items/hasNext가 항상 직렬화된다")
    void empty_underNonEmptyMapper_keepsItemsAndHasNext() throws Exception {
        // given
        CursorPage<String> page = CursorPage.empty();

        // when
        String json = mapper.writeValueAsString(page);

        // then
        assertThat(json).contains("\"items\":[]");
        assertThat(json).contains("\"hasNext\":false");
    }

    @Test
    @DisplayName("항목이 있으면 items가 그대로 직렬화된다")
    void of_withItems_serializesItems() throws Exception {
        // given
        CursorPage<String> page = CursorPage.of(List.of("a", "b"), true);

        // when
        String json = mapper.writeValueAsString(page);

        // then
        assertThat(json).contains("\"items\":[\"a\",\"b\"]");
        assertThat(json).contains("\"hasNext\":true");
    }
}
