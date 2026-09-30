package xiaozhi.modules.pdc.nfc.wechat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;
import xiaozhi.modules.wechat.service.WechatAccessTokenProvider;

import java.util.regex.Pattern;

/**
 * 微信 NFC Scheme 生成客户端。
 * 封装参数校验、token 获取和 HTTP 调用。
 */
@Slf4j
@RequiredArgsConstructor
public class WechatNfcSchemeClient {

    private static final Pattern CLAIM_REF_PATTERN = Pattern.compile("[A-Za-z0-9_-]{22}");
    private static final int MAX_QUERY_LENGTH = 1024;

    private final WechatAccessTokenProvider accessTokens;
    private final WechatNfcHttpTransport transport;
    private final PdcNfcProperties properties;

    /**
     * 为指定 wechatSn 和 claimRef 生成 NFC Scheme。
     *
     * @param wechatSn 微信 NFC 序列号
     * @param claimRef 22 字符领取引用
     * @return 调用结果（成功时含 scheme URL）
     */
    public WechatNfcSchemeResult generate(String wechatSn, String claimRef) {
        requireClientConfiguration(properties.getModelId(), wechatSn, claimRef);

        WechatNfcSchemeRequest request = WechatNfcSchemeRequest.of(
                properties.getModelId(), wechatSn, claimRef, properties.getSchemeEnvVersion());

        String accessToken = accessTokens.getAccessToken();
        return transport.post(accessToken, request);
    }

    private void requireClientConfiguration(String modelId, String wechatSn, String claimRef) {
        if (StringUtils.isBlank(modelId) || StringUtils.isBlank(modelId.trim())) {
            throw new RenException(ErrorCode.PDC_NFC_FEATURE_DISABLED);
        }
        if (modelId.contains("<") || modelId.contains(">")) {
            throw new RenException(ErrorCode.PDC_NFC_FEATURE_DISABLED);
        }
        if (StringUtils.isBlank(wechatSn)) {
            throw new RenException(ErrorCode.PDC_NFC_FEATURE_DISABLED);
        }
        if (claimRef == null || !CLAIM_REF_PATTERN.matcher(claimRef).matches()) {
            throw new RenException(ErrorCode.PDC_NFC_FEATURE_DISABLED);
        }
        String query = "v=1&ref=" + claimRef;
        if (query.length() >= MAX_QUERY_LENGTH) {
            throw new RenException(ErrorCode.PDC_NFC_FEATURE_DISABLED);
        }
    }
}
