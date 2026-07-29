package xiaozhi.modules.pdc.nfc.constant;

/**
 * 蛋宝宝原型。
 */
public enum PdcNfcPrototype {
    KOI("锦鲤"),
    RABBIT("玉兔");

    private final String code;

    PdcNfcPrototype(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean isValid(String code) {
        if (code == null) return false;
        for (PdcNfcPrototype p : values()) {
            if (p.code.equals(code)) return true;
        }
        return false;
    }
}
