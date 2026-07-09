package xiaozhi.modules.invite.util;

import java.security.SecureRandom;

/**
 * 邀请码生成器：8 位，去歧义字符集（无 0/1/I/O），32 字母表。
 */
public final class InviteCodeGenerator {

    private static final char[] ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteCodeGenerator() {}

    public static String generate() {
        char[] buf = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            buf[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(buf);
    }
}
