package com.example.authdemo.service;

public final class CompanyKeyPolicy {
    public static final int MINIMUM_LENGTH = 20;

    private CompanyKeyPolicy() {
    }

    public static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static boolean isAcceptable(String value) {
        return value != null
                && value.length() >= MINIMUM_LENGTH
                && value.chars().noneMatch(Character::isWhitespace);
    }
}
