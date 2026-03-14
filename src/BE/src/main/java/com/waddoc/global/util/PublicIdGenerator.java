package com.waddoc.global.util;

import java.security.SecureRandom;

/**
 * 외부 노출용 public_id 생성기. 접두사 + nanoid(8자리) 조합.
 * 예: "pat_" → "pat_V1StGXR8"
 */
public final class PublicIdGenerator {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_LENGTH = 8;

    private PublicIdGenerator() {
    }

    public static String generate(String prefix) {
        return prefix + nanoid(DEFAULT_LENGTH);
    }

    private static String nanoid(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
