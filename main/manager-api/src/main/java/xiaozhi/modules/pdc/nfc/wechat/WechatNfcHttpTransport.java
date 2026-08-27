package xiaozhi.modules.pdc.nfc.wechat;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 微信 NFC Scheme HTTP 传输层。
 * 负责发送请求并解析响应，异常统一归类为 WechatNfcSchemeResult。
 */
@Slf4j
public class WechatNfcHttpTransport {

    private static final String GENERATE_URL = "https://api.weixin.qq.com/wxa/generatenfcscheme";
    private static final int TIMEOUT_MS = 10_000;

    /**
     * 发送 generatenfcscheme 请求。
     *
     * @param accessToken 微信 access_token
     * @param request     请求体
     * @return 调用结果
     */
    public WechatNfcSchemeResult post(String accessToken, WechatNfcSchemeRequest request) {
        String url = GENERATE_URL + "?access_token=" + accessToken;
        String jsonBody = toJson(request);

        String respBody;
        try {
            respBody = httpPost(url, jsonBody);
        } catch (Exception e) {
            log.warn("微信NFC Scheme网络请求失败 errcode={}", -1);
            return WechatNfcSchemeResult.networkError(truncate(e.getMessage()));
        }

        try {
            if (StringUtils.isBlank(respBody)) {
                log.warn("微信NFC Scheme响应为空 errcode={}", -1);
                return WechatNfcSchemeResult.networkError("响应为空");
            }
            JSONObject json = JSONUtil.parseObj(respBody);
            Integer errcode = json.getInt("errcode");
            if (errcode == null || errcode == 0) {
                // 成功响应的链接字段是 openlink，不是 scheme
                // （见 main/docs/egg-nfc-feature-spec.md 6.2；读错字段会拿到 null）
                String openlink = json.getStr("openlink");
                if (StringUtils.isBlank(openlink)) {
                    // errcode=0 说明微信已消耗该 sn，重试必得 9800010。
                    // 必须 fail-closed 停掉任务，不能让 null 流到加密环节抛 NPE。
                    log.error("微信NFC Scheme响应缺少 openlink errcode={}", errcode);
                    return WechatNfcSchemeResult.missingOpenlink();
                }
                return WechatNfcSchemeResult.ok(openlink);
            }
            String errmsg = json.getStr("errmsg");
            WechatNfcErrorAction action = WechatNfcErrorPolicy.classify(errcode);
            log.warn("微信NFC Scheme请求失败 errcode={}, errmsg={}", errcode, truncate(errmsg));
            return WechatNfcSchemeResult.fail(errcode, truncate(errmsg), action);
        } catch (Exception e) {
            log.warn("微信NFC Scheme响应解析失败 errcode={}", -1);
            return WechatNfcSchemeResult.networkError(truncate(e.getMessage()));
        }
    }

    /**
     * 对微信开放接口发起 POST 请求，返回响应体。
     * 包级可见，便于单测以子类重写的方式注入桩响应。
     */
    String httpPost(String url, String jsonBody) {
        try (HttpResponse response = HttpRequest.post(url)
                .body(jsonBody)
                .timeout(TIMEOUT_MS)
                .execute()) {
            return response.body();
        }
    }

    private String toJson(WechatNfcSchemeRequest request) {
        JSONObject jumpWxa = new JSONObject();
        jumpWxa.set("path", request.jumpWxa().path());
        jumpWxa.set("query", request.jumpWxa().query());
        jumpWxa.set("env_version", request.jumpWxa().envVersion());

        JSONObject body = new JSONObject();
        body.set("jump_wxa", jumpWxa);
        body.set("model_id", request.modelId());
        body.set("sn", request.sn());
        return body.toString();
    }

    /**
     * 截断错误消息，避免日志中泄露敏感信息。
     */
    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 100 ? s.substring(0, 100) : s;
    }
}
