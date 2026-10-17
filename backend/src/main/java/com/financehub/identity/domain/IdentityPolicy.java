package com.financehub.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

public final class IdentityPolicy {
    private static final SecureRandom RANDOM = new SecureRandom();
    private IdentityPolicy() {}
    public static String normalizeEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    public static void validatePassword(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must contain at least 12 characters and at most 72 UTF-8 bytes");
        }
    }
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static boolean validTokenShape(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }
    public static String hash(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
