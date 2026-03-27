package com.secureai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JpaConfig Tests")
class JpaConfigTest {

    @Test
    @DisplayName("Should instantiate JpaConfig")
    void shouldInstantiate() {
        JpaConfig jpaConfig = new JpaConfig();
        assertThat(jpaConfig).isNotNull();
    }
}
