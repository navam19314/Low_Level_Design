package com.conceptcoding.interviewquestions.hello_all_questions.urlshortener;

/**
 * Base-62 encoder for integer ids → fixed-alphabet short codes.
 *
 * <p>Alphabet is {@code 0-9A-Za-z} — 62 characters, the standard URL-safe set.
 * With 62 chars, you get:
 * <ul>
 *   <li>62^6 ≈ 56 billion codes per 6-char output → plenty for most use cases</li>
 *   <li>62^7 ≈ 3.5 trillion per 7-char</li>
 *   <li>62^8 ≈ 218 trillion per 8-char</li>
 * </ul>
 *
 * <p>Why base-62 and not base-64? Base-64 includes {@code +} and {@code /} which
 * aren't URL-safe (they need percent-encoding) — defeats the point.
 */
public final class Base62Encoder {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    private Base62Encoder() {}     // utility class — no instances

    /** Encode a non-negative long to a base-62 string. id=0 → "0". */
    public static String encode(long id) {
        if (id < 0) throw new IllegalArgumentException("id must be >= 0");
        if (id == 0) return String.valueOf(ALPHABET.charAt(0));

        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(ALPHABET.charAt((int) (id % BASE)));
            id /= BASE;
        }
        return sb.reverse().toString();
    }

    /** Decode a base-62 string back to the original long id. */
    public static long decode(String code) {
        if (code == null || code.isEmpty()) throw new IllegalArgumentException("code required");
        long result = 0;
        for (int i = 0; i < code.length(); i++) {
            int digit = ALPHABET.indexOf(code.charAt(i));
            if (digit < 0) throw new IllegalArgumentException("invalid base62 char: " + code.charAt(i));
            result = result * BASE + digit;
        }
        return result;
    }
}
