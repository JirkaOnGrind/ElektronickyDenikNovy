package com.example.authdemo.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserServicePasswordPolicyTest {
    private final UserService userService = new UserService();

    @Test
    void passwordMustBeAtLeastTwelveCharacters() {
        assertThat(userService.isPasswordAcceptable("short")).isFalse();
        assertThat(userService.isPasswordAcceptable("correct horse battery staple")).isTrue();
    }

    @Test
    void passwordCannotExceedBcryptByteLimit() {
        assertThat(userService.isPasswordAcceptable("a".repeat(72))).isTrue();
        assertThat(userService.isPasswordAcceptable("a".repeat(73))).isFalse();
    }
}
