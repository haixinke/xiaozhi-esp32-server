package xiaozhi.modules.wechat.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.modules.wechat.service.WechatAccessTokenProvider;

/**
 * 微信小程序 access_token 提供者实现。
 * 使用 Redis 缓存（提前 300s 失效），调用 stable_token 接口。
 */
@Slf4j
@Component
public class WechatAccessTokenProviderImpl implements WechatAccessTokenProvider {

    private static final String STABLE_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/stable_token";
    private static final long DEFAULT_EXPIRES_IN = 7200L;
    private static final long SAFETY_MARGIN_SECONDS = 300L;
    private static final long MIN_TTL_SECONDS = 60L;

    private final RedisUtils redisUtils;

    @Value("${eggbaby.miniprogram.appid:${wechat.miniprogram.appid:}}")
    private String appid;

    @Value("${eggbaby.miniprogram.secret:${wechat.miniprogram.secret:}}")
    private String secret;

    public WechatAccessTokenProviderImpl(RedisUtils redisUtils) {
        this.redisUtils = redisUtils;
    }

    @Override
    public String getAccessToken() {
        String key = RedisKeys.getWechatAccessTokenKey();
        Object cached = redisUtils.get(key);
        if (cached instanceof String s && !s.isBlank()) {
            return s;
        }

        if (StringUtils.isBlank(appid) || StringUtils.isBlank(secret)) {
            throw new RenException("微信小程序未配置appid/secret");
        }

        JSONObject body = new JSONObject();
        body.set("grant_type", "client_credential");
        body.set("appid", appid);
        body.set("secret", secret);
        body.set("force_refresh", false);

        String respBody;
        try {
            respBody = httpPost(STABLE_TOKEN_URL, body.toString());
        } catch (Exception e) {
            log.error("调用微信stable_token失败", e);
            throw new RenException("调用微信access_token接口失败: " + e.getMessage());
        }

        try {
            if (StringUtils.isBlank(respBody)) {
                throw new RenException("微信access_token接口返回为空");
            }
            JSONObject json = JSONUtil.parseObj(respBody);
            Integer errcode = json.getInt("errcode");
            if (errcode != null && errcode != 0) {
                log.warn("获取微信access_token失败 errcode={}, errmsg={}", errcode, json.getStr("errmsg"));
                throw new RenException("获取微信access_token失败");
            }
            String accessToken = json.getStr("access_token");
            Integer expiresIn = json.getInt("expires_in");
            if (StringUtils.isBlank(accessToken)) {
                throw new RenException("微信access_token为空");
            }
            long ttl = (expiresIn == null ? DEFAULT_EXPIRES_IN : expiresIn) - SAFETY_MARGIN_SECONDS;
            redisUtils.set(key, accessToken, Math.max(ttl, MIN_TTL_SECONDS));
            return accessToken;
        } catch (RenException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析微信access_token响应失败", e);
            throw new RenException("解析微信access_token响应失败");
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
