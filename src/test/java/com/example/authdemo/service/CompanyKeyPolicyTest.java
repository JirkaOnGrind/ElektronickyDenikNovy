package com.example.authdemo.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompanyKeyPolicyTest {
    @Test
    void acceptsLongKeyWithoutWhitespace() {
        assertThat(CompanyKeyPolicy.isAcceptable("company-key-with-entropy")).isTrue();
    }

    @Test
    void rejectsShortKey() {
        assertThat(CompanyKeyPolicy.isAcceptable("short")).isFalse();
    }

    @Test
    void rejectsWhitespace() {
        assertThat(CompanyKeyPolicy.isAcceptable("company key with spaces")).isFalse();
    }
}
