package xiaozhi.modules.wechat.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.wechat.service.WechatAccessTokenProvider;
import xiaozhi.modules.wechat.service.WechatMediaCheckService;

/**
 * 微信内容安全检测服务实现（security.mediaCheckAsync 2.0）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatMediaCheckServiceImpl implements WechatMediaCheckService {

    private static final String MEDIA_CHECK_ASYNC_URL = "https://api.weixin.qq.com/wxa/media_check_async";

    /** media_type=2 固定为图片 */
    private static final int MEDIA_TYPE_IMAGE = 2;

    /** version=2 走新版异步审核 */
    private static final int CHECK_VERSION = 2;

    /** scene=1 资料场景（用户上传照片 + AI 生成图均为资料类展示图） */
    private static final int SCENE_PROFILE = 1;

    private final WechatAccessTokenProvider accessTokenProvider;

    @Override
    public String mediaCheckAsync(String mediaUrl, String openid) {
        if (StringUtils.isBlank(mediaUrl) || StringUtils.isBlank(openid)) {
            throw new RenException(ErrorCode.IMAGE_GEN_CHECK_SUBMIT_FAILED);
        }
        String accessToken = accessTokenProvider.getAccessToken();

        JSONObject body = new JSONObject();
        body.set("media_url", mediaUrl);
        body.set("media_type", MEDIA_TYPE_IMAGE);
        body.set("version", CHECK_VERSION);
        body.set("scene", SCENE_PROFILE);
        body.set("openid", openid);

        String respBody;
        try {
            respBody = httpPost(MEDIA_CHECK_ASYNC_URL + "?access_token=" + accessToken, body.toString());
        } catch (Exception e) {
            log.error("调用微信mediaCheckAsync失败", e);
            throw new RenException(ErrorCode.IMAGE_GEN_CHECK_SUBMIT_FAILED);
        }

        try {
            if (StringUtils.isBlank(respBody)) {
                throw new RenException(ErrorCode.IMAGE_GEN_CHECK_SUBMIT_FAILED);
            }
            JSONObject json = JSONUtil.parseObj(respBody);
            Integer errcode = json.getInt("errcode");
            if (errcode == null || errcode != 0) {
                log.warn("mediaCheckAsync提交失败 errcode={}, errmsg={}", errcode, json.getStr("errmsg"));
                throw new RenException(ErrorCode.IMAGE_GEN_CHECK_SUBMIT_FAILED);
            }
            String traceId = json.getStr("trace_id");
            if (StringUtils.isBlank(traceId)) {
                throw new RenException(ErrorCode.IMAGE_GEN_CHECK_SUBMIT_FAILED);
            }
            return traceId;
        } catch (RenException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析mediaCheckAsync响应失败", e);
            throw new RenException(ErrorCode.IMAGE_GEN_CHECK_SUBMIT_FAILED);
        }
    }

    /**
     * 对微信开放接口发起 POST 请求，返回响应体。
     * 包级可见，便于单测以子类重写的方式注入桩响应。
     */
    String httpPost(String url, String jsonBody) {
        try (HttpResponse response = HttpRequest.post(url)
                .body(jsonBody)
                .timeout(10_000)
                .execute()) {
            return response.body();
        }
    }
}
