package xiaozhi.modules.pdc.nfc.service;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;

import java.util.Locale;

/**
 * 自由文本字段的敏感内容守卫：拒绝任何疑似 Scheme/claimRef 明文，
 * 且异常消息绝不回显被拒绝的原文。
 */
public final class PdcNfcSensitiveTextGuard {

    private static final String[] SCHEME_MARKERS = {
            "weixin://", "wxlogin://", "weixin110://", "https://weixin"
    };

    private PdcNfcSensitiveTextGuard() {
    }

    /**
     * 若文本包含疑似 Scheme/claimRef 明文则抛出领域异常。
     * 允许空值和空白。
     */
    public static void requireNoSchemeLeakage(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String lowered = text.toLowerCase(Locale.ROOT);
        for (String marker : SCHEME_MARKERS) {
            if (lowered.contains(marker)) {
                throw new RenException(ErrorCode.PDC_NFC_CSV_FORMAT_ERROR);
            }
        }
    }
}
