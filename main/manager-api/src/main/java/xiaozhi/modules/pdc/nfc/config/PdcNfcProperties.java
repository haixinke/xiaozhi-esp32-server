package xiaozhi.modules.pdc.nfc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * PDC NFC 实物生产域配置。
 * <p>
 * 所有功能默认关闭（fail-closed），需通过环境变量显式开启。
 * 开关按业务环节分三道门：Scheme 生成（生产）、出库激活、用户领取，任一环节可独立灰度或熔断。
 * 对应流程文档 main/docs/egg-nfc-lifecycle-flow.md 模块三的“三把锁”。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "pdc.nfc")
public class PdcNfcProperties {

    /** NFC 功能总开关：关闭时批次创建、出库激活、领取等全部入口拒绝访问 */
    private boolean enabled = false;

    /** 微信审核通过的设备类型编号（model_id），所有蛋宝宝共用一个；为空或尖括号包裹的占位符形式时禁止调用 generatenfcscheme */
    private String modelId;

    /** 当前线上小程序版本号，须与后台登记的发布证据版本一致，否则视为发布未就绪 */
    private String releaseVersion;

    /** 小程序发布就绪标志：证明领取页 /pages/nfc-claim/nfc-claim 已随正式版发布；Scheme 生成、激活、领取均要求开启 */
    private boolean releaseReady = false;

    /** Scheme 生成任务开关（生产环节灰度/熔断） */
    private boolean schemeGenerationEnabled = false;

    /**
     * generatenfcscheme 请求的 env_version：
     * release（正式版）/ trial（体验版）/ develop（开发版）。
     * 默认 release；小程序正式版上线前可切 trial 验证真实链路，正式投放前须切回 release。
     */
    private String schemeEnvVersion = "release";

    /** 出库激活开关（激活环节灰度/熔断）：控制资产能否从 IN_STOCK 推进到 ACTIVE */
    private boolean activationEnabled = false;

    /** 用户领取开关（领取环节灰度/熔断）：控制小程序 preview/confirm 接口可用性 */
    private boolean claimEnabled = false;

    /** 单个生产批次的数量上限，创建批次时 plannedQuantity 必须在 1 ~ 此值之间 */
    private int maxBatchQuantity = 10000;

    /** claimRef 保护密钥（active/previous 双版本，支持密钥轮换） */
    private ClaimRef claimRef = new ClaimRef();

    /**
     * claimRef 加密与查找密钥。
     * <p>
     * active 为当前密钥；previous 仅为轮换过渡期保留：NFC 标签锁卡后无法召回，
     * 领取时需同时用新旧 HMAC 各算一个哈希查库（见 ClaimRefProtection#lookupHashes），
     * 旧标签才能继续领取。轮换完成后清空 previous 三项即完成切换。
     */
    @Data
    public static class ClaimRef {
        /** 当前密钥版本号，生成资产时写入 claim_ref_hash_version 标记所用密钥 */
        private String activeVersion;

        /** 当前 HMAC-SHA-256 密钥（Base64）：claimRef 明文不落库，按此哈希查找资产；须与 AES 密钥不同 */
        private String activeHmacKeyBase64;

        /** 当前 AES-256-GCM 密钥（Base64，解码后须 32 字节）：加密 claimRef 与 Scheme URL，assetId 作 AAD 绑定防跨资产重放 */
        private String activeAesKeyBase64;

        /** 上一版密钥版本号，密钥轮换完成后清空 */
        private String previousVersion;

        /** 上一版 HMAC-SHA-256 密钥（Base64）：仅轮换过渡期用于旧标签查找 */
        private String previousHmacKeyBase64;

        /** 上一版 AES-256-GCM 密钥（Base64）：仅用于解密旧版本密文 */
        private String previousAesKeyBase64;
    }
}
