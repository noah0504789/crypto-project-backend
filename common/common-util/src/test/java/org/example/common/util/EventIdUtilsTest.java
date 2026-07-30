package org.example.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventIdUtilsTest {

    @Test
    @DisplayName("ULID는 호출할 때마다 새로운 26자 식별자를 생성한다")
    void generateUlid() {
        String first = EventIdUtils.generateUlid();
        String second = EventIdUtils.generateUlid();

        assertThat(first).hasSize(26);
        assertThat(second).hasSize(26);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("UUID는 호출할 때마다 새로운 36자 식별자를 생성한다")
    void generateUUID() {
        String first = EventIdUtils.generateUUID();
        String second = EventIdUtils.generateUUID();

        assertThat(first).hasSize(36);
        assertThat(second).hasSize(36);
        assertThat(second).isNotEqualTo(first);
    }
}
