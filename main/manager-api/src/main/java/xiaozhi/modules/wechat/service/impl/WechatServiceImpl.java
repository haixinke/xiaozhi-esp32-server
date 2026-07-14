package xiaozhi.modules.wechat.service.impl;

import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.config.AliyunOssProperties;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.oss.OssService;
import xiaozhi.common.page.TokenDTO;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.pet.util.PetBirthCalculator;
import xiaozhi.modules.security.password.PasswordUtils;
import xiaozhi.modules.security.service.SysUserTokenService;
import xiaozhi.modules.sys.dao.SysUserDao;
import xiaozhi.modules.sys.entity.SysUserEntity;
import xiaozhi.modules.sys.enums.SuperAdminEnum;
import xiaozhi.modules.wechat.dao.WechatUserDao;
import xiaozhi.modules.wechat.dto.WechatBindPhoneRespDTO;
import xiaozhi.modules.wechat.dto.WechatLoginRespDTO;
import xiaozhi.modules.wechat.dto.WechatProfileUpdateDTO;
import xiaozhi.modules.wechat.entity.WechatUserEntity;
import xiaozhi.modules.wechat.service.WechatService;
import xiaozhi.modules.wechat.util.ProfileValidator;
import xiaozhi.modules.wechat.vo.WechatProfileVO;
import xiaozhi.modules.agent.service.AgentService;

/**
 * 微信小程序登录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatServiceImpl extends BaseServiceImpl<WechatUserDao, WechatUserEntity> implements WechatService {

    private static final String JSCODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";
    private static final String STABLE_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/stable_token";
    private static final String GET_PHONE_URL = "https://api.weixin.qq.com/wxa/business/getuserphonenumber";
    private static final String DEFAULT_USER_AVATAR_URL = "https://oss.eggbabe.com/default-avatar/user/user-avatar.png";
    private static final String DEFAULT_NICKNAME_PREFIX = "蛋友";
    private static final int DEFAULT_NICKNAME_RANDOM_LENGTH = 5;
    private static final java.util.random.RandomGenerator DEFAULT_NICKNAME_RANDOM = new java.util.Random();

    private final SysUserDao sysUserDao;
    private final SysUserTokenService sysUserTokenService;
    private final AgentService agentService;
    private final xiaozhi.modules.invite.service.InviteService inviteService;
    private final RedisUtils redisUtils;
    private final OssService ossService;
    private final AliyunOssProperties ossProperties;

    @Value("${eggbaby.miniprogram.appid:${wechat.miniprogram.appid:}}")
    private String appid;

    @Value("${eggbaby.miniprogram.secret:${wechat.miniprogram.secret:}}")
    private String secret;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WechatLoginRespDTO login(String code) {
        if (StringUtils.isBlank(code)) {
            throw new RenException("微信code不能为空");
        }
        if (StringUtils.isBlank(appid) || StringUtils.isBlank(secret)) {
            throw new RenException("微信小程序未配置appid/secret");
        }

        log.info("微信登录请求 appid={}, codePrefix={}", appid, StringUtils.left(code, 6));

        // 1. 调用微信 jscode2session 换取 openid + session_key
        JSONObject session = jscode2session(code);
        String openid = session.getStr("openid");
        String sessionKey = session.getStr("session_key");
        if (StringUtils.isBlank(openid)) {
            Integer errcode = session.getInt("errcode");
            String errmsg = session.getStr("errmsg");
            log.warn("微信登录失败 errcode={}, errmsg={}", errcode, errmsg);
            throw new RenException("微信登录失败: " + errmsg);
        }

        // 2. 查询是否已经绑定过
        WechatUserEntity wechatUser = baseDao.selectOne(
                new QueryWrapper<WechatUserEntity>().eq("openid", openid));

        boolean isNewUser = false;
        Long userId;
        String username = null;
        if (wechatUser != null) {
            userId = wechatUser.getUserId();
            // 同步会话密钥
            wechatUser.setSessionKey(sessionKey);
            baseDao.updateById(wechatUser);
        } else {
            // 3. 自动创建 sys_user
            var result = createSysUserForOpenid(openid);
            userId = result.getUserId();
            username = result.getUsername();
            // 4. 创建 ai_wechat_user 关联记录
            wechatUser = new WechatUserEntity();
            wechatUser.setOpenid(openid);
            wechatUser.setUserId(userId);
            wechatUser.setSessionKey(sessionKey);
            wechatUser.setNickname(generateDefaultNickname());
            wechatUser.setAvatarUrl(DEFAULT_USER_AVATAR_URL);
            baseDao.insert(wechatUser);
            isNewUser = true;
        }

        // 5. 获取用户最新的智能体ID（若有）
        String agentId = getLatestUserAgent(userId);

        // 6. 生成 token
        Result<TokenDTO> tokenResult = sysUserTokenService.createToken(userId);
        if (tokenResult.getCode() != 0 || tokenResult.getData() == null) {
            throw new RenException(ErrorCode.TOKEN_GENERATE_ERROR);
        }
        TokenDTO tokenDTO = tokenResult.getData();

        WechatLoginRespDTO resp = new WechatLoginRespDTO();
        resp.setToken(tokenDTO.getToken());
        resp.setExpire(tokenDTO.getExpire());
        resp.setOpenid(openid);
        resp.setUserId(userId);
        resp.setIsNewUser(isNewUser);
        resp.setHasPhone(StringUtils.isNotBlank(wechatUser.getPhone()));
        resp.setAgentId(agentId);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindAccount(Long currentUserId, String username, String password) {
        if (currentUserId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            throw new RenException(ErrorCode.NOT_NULL);
        }

        // 1. 校验已有账号
        List<SysUserEntity> users = sysUserDao.selectList(
                new QueryWrapper<SysUserEntity>().eq("username", username));
        if (users == null || users.isEmpty()) {
            throw new RenException(ErrorCode.ACCOUNT_PASSWORD_ERROR);
        }
        SysUserEntity targetUser = users.get(0);
        if (!PasswordUtils.matches(password, targetUser.getPassword())) {
            throw new RenException(ErrorCode.ACCOUNT_PASSWORD_ERROR);
        }
        if (targetUser.getId().equals(currentUserId)) {
            // 已是同一账号无需重复绑定
            return;
        }

        // 2. 找到当前登录态对应的微信记录
        List<WechatUserEntity> wechatUsers = baseDao.selectList(
                new QueryWrapper<WechatUserEntity>().eq("user_id", currentUserId));
        if (wechatUsers == null || wechatUsers.isEmpty()) {
            throw new RenException("当前账号不是微信登录账号，无法执行绑定");
        }

        // 3. 切换 user_id 到目标账号
        for (WechatUserEntity wechatUser : wechatUsers) {
            wechatUser.setUserId(targetUser.getId());
            baseDao.updateById(wechatUser);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WechatBindPhoneRespDTO bindPhone(Long currentUserId, String phoneCode) {
        if (currentUserId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        if (StringUtils.isBlank(phoneCode)) {
            throw new RenException(ErrorCode.NOT_NULL);
        }
        if (StringUtils.isBlank(appid) || StringUtils.isBlank(secret)) {
            throw new RenException("微信小程序未配置appid/secret");
        }

        // 1. 定位当前登录态对应的微信记录
        WechatUserEntity wechatUser = baseDao.selectOne(
                new QueryWrapper<WechatUserEntity>().eq("user_id", currentUserId));
        if (wechatUser == null) {
            throw new RenException("当前账号不是微信登录账号，无法绑定手机号");
        }

        // 2. 用 getPhoneNumber code 换取明文手机号
        String accessToken = getAccessToken();
        String phone = getPhoneNumber(accessToken, phoneCode);

        // 3. 写入 ai_wechat_user（按 openid 更新单条，避免误改其他账号）
        baseDao.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<WechatUserEntity>()
                        .eq("openid", wechatUser.getOpenid())
                        .set("phone", phone));

        // 4. 返回脱敏手机号，日志同样脱敏
        String masked = maskPhone(phone);
        log.info("微信用户绑定手机号成功 openid={}, phone={}", wechatUser.getOpenid(), masked);

        WechatBindPhoneRespDTO resp = new WechatBindPhoneRespDTO();
        resp.setPhone(masked);
        return resp;
    }

    @Override
    public WechatProfileVO getProfile(Long userId) {
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        WechatUserEntity entity = baseDao.selectOne(
                new QueryWrapper<WechatUserEntity>().eq("user_id", userId));
        if (entity == null) {
            throw new RenException("当前账号不是微信登录账号");
        }
        WechatProfileVO vo = new WechatProfileVO();
        vo.setNickname(entity.getNickname());
        vo.setAvatarUrl(entity.getAvatarUrl());
        vo.setGender(entity.getGender());
        vo.setBirthday(entity.getBirthday());
        vo.setCity(entity.getCity());
        vo.setMbti(entity.getMbti());
        vo.setZodiac(PetBirthCalculator.zodiacOf(entity.getBirthday()));
        vo.setPhone(maskPhone(entity.getPhone()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, WechatProfileUpdateDTO dto) {
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        ProfileValidator.validate(dto);

        UpdateWrapper<WechatUserEntity> wrapper = new UpdateWrapper<>();
        wrapper.eq("user_id", userId);
        if (StringUtils.isNotBlank(dto.getNickname())) {
            wrapper.set("nickname", dto.getNickname());
        }
        if (StringUtils.isNotBlank(dto.getAvatarUrl())) {
            wrapper.set("avatar_url", dto.getAvatarUrl());
        }
        if (StringUtils.isNotBlank(dto.getGender())) {
            wrapper.set("gender", dto.getGender());
        }
        if (dto.getBirthday() != null) {
            wrapper.set("birthday", dto.getBirthday());
        }
        if (StringUtils.isNotBlank(dto.getCity())) {
            wrapper.set("city", dto.getCity());
        }
        if (StringUtils.isNotBlank(dto.getMbti())) {
            wrapper.set("mbti", dto.getMbti());
        }
        baseDao.update(null, wrapper);
    }

    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        if (file == null || file.isEmpty()) {
            throw new RenException(ErrorCode.UPLOAD_FILE_EMPTY);
        }
        if (!ALLOWED_AVATAR_TYPES.contains(file.getContentType())) {
            throw new RenException(ErrorCode.AVATAR_FILE_TYPE_ERROR);
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new RenException(ErrorCode.FILE_SIZE_OVER_LIMIT);
        }
        if (!ossService.isEnabled()) {
            throw new RenException(ErrorCode.OSS_UPLOAD_FILE_ERROR);
        }

        String ext = extensionOf(file.getContentType());
        String uuid = IdUtil.fastSimpleUUID();
        String ossKey = "avatar/" + userId + "/" + uuid + "." + ext;
        try {
            ossService.upload(ossKey, file.getBytes());
        } catch (Exception e) {
            log.error("头像上传OSS失败 userId={}", userId, e);
            throw new RenException(ErrorCode.OSS_UPLOAD_FILE_ERROR);
        }
        return publicUrl(ossKey);
    }

    private String publicUrl(String ossKey) {
        String endpoint = ossProperties.getEndpoint();
        String clean = endpoint.replaceFirst("^https?://", "");
        return "https://" + ossProperties.getBucketName() + "." + clean + "/" + ossKey;
    }

    private static String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new RenException(ErrorCode.AVATAR_FILE_TYPE_ERROR);
        };
    }

    /**
     * 获取微信小程序普通 access_token（带 Redis 缓存）。
     * getuserphonenumber 等服务端接口需要该 token，有效期 7200s。
     */
    private String getAccessToken() {
        String key = RedisKeys.getWechatAccessTokenKey();
        Object cached = redisUtils.get(key);
        if (cached instanceof String s && !s.isBlank()) {
            return s;
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
            // 提前 300s 失效，留安全余量
            long ttl = (expiresIn == null ? 7200 : expiresIn) - 300L;
            redisUtils.set(key, accessToken, Math.max(ttl, 60L));
            return accessToken;
        } catch (RenException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析微信access_token响应失败", e);
            throw new RenException("解析微信access_token响应失败");
        }
    }

    /**
     * 调用微信 getuserphonenumber 接口换取明文手机号
     */
    private String getPhoneNumber(String accessToken, String phoneCode) {
        JSONObject body = new JSONObject();
        body.set("code", phoneCode);

        String respBody;
        try {
            respBody = httpPost(GET_PHONE_URL + "?access_token=" + accessToken, body.toString());
        } catch (Exception e) {
            log.error("调用微信getuserphonenumber失败", e);
            throw new RenException("调用微信手机号接口失败: " + e.getMessage());
        }

        try {
            if (StringUtils.isBlank(respBody)) {
                throw new RenException("微信手机号接口返回为空");
            }
            JSONObject json = JSONUtil.parseObj(respBody);
            Integer errcode = json.getInt("errcode");
            if (errcode == null || errcode != 0) {
                log.warn("获取微信手机号失败 errcode={}, errmsg={}", errcode, json.getStr("errmsg"));
                throw new RenException("获取微信手机号失败");
            }
            JSONObject phoneInfo = json.getJSONObject("phone_info");
            if (phoneInfo == null) {
                throw new RenException("微信手机号信息为空");
            }
            String phone = phoneInfo.getStr("phoneNumber");
            if (StringUtils.isBlank(phone)) {
                throw new RenException("微信返回的手机号为空");
            }
            return phone;
        } catch (RenException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析微信手机号响应失败", e);
            throw new RenException("解析微信手机号响应失败");
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

    /**
     * 手机号脱敏：保留前3位和后4位，如 138****1234
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 调用微信 jscode2session 接口。
     * 包级可见，便于单测以子类重写的方式注入桩响应。
     */
    JSONObject jscode2session(String code) {
        try (HttpResponse response = HttpRequest.get(JSCODE2SESSION_URL)
                .form("appid", appid)
                .form("secret", secret)
                .form("js_code", code)
                .form("grant_type", "authorization_code")
                .timeout(10_000)
                .execute()) {
            String body = response.body();
            if (StringUtils.isBlank(body)) {
                throw new RenException("微信接口返回为空");
            }
            return JSONUtil.parseObj(body);
        } catch (RenException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信jscode2session失败", e);
            throw new RenException("调用微信接口失败: " + e.getMessage());
        }
    }

    /**
     * 为指定 openid 自动创建一个 sys_user 账号
     */
    private UserCreationResult createSysUserForOpenid(String openid) {
        String username = openid;
        // 用户名冲突时追加随机后缀
        if (existsUsername(username)) {
            username = username + "_" + IdUtil.fastSimpleUUID().substring(0, 6);
        }

        SysUserEntity user = new SysUserEntity();
        user.setUsername(username);
        user.setPassword(PasswordUtils.encode(openid));
        user.setSuperAdmin(SuperAdminEnum.NO.value());
        user.setStatus(1);
        user.setCreateDate(new Date());
        user.setUpdateDate(new Date());
        sysUserDao.insert(user);
        // 自动为该 openid 用户生成个人邀请码（失败不阻断登录）
        try {
            inviteService.createPersonalCode(user.getId());
        } catch (Exception e) {
            log.warn("为新用户生成个人邀请码失败 userId={}, err={}", user.getId(), e.getMessage());
        }
        return new UserCreationResult(user.getId(), username);
    }

    /**
     * 获取用户最新的智能体ID
     * @param userId 用户ID
     * @return 最新智能体ID，若用户无智能体则返回null
     */
    private String getLatestUserAgent(Long userId) {
        try {
            java.util.List<xiaozhi.modules.agent.dto.AgentDTO> agents = agentService.getUserAgents(userId, null, null);
            if (agents != null && !agents.isEmpty()) {
                // getUserAgents 已按 created_at 降序排序，返回第一个即为最新
                return agents.get(0).getId();
            }
        } catch (Exception e) {
            log.warn("查询用户智能体失败: userId={}, error={}", userId, e.getMessage());
        }
        return null;
    }

    private String generateDefaultNickname() {
        StringBuilder sb = new StringBuilder(DEFAULT_NICKNAME_PREFIX.length() + DEFAULT_NICKNAME_RANDOM_LENGTH);
        sb.append(DEFAULT_NICKNAME_PREFIX);
        for (int i = 0; i < DEFAULT_NICKNAME_RANDOM_LENGTH; i++) {
            sb.append(DEFAULT_NICKNAME_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * 用户创建结果
     */
    private static class UserCreationResult {
        private final Long userId;
        private final String username;

        public UserCreationResult(Long userId, String username) {
            this.userId = userId;
            this.username = username;
        }

        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }
    }

    private boolean existsUsername(String username) {
        Long count = sysUserDao.selectCount(
                new QueryWrapper<SysUserEntity>().eq("username", username));
        return count != null && count > 0;
    }

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final Random RANDOM = new Random();

    /**
     * 生成符合强密码规则(包含大小写字母+数字)的随机密码
     */
    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder();
        sb.append("ABCDEFGHJKLMNPQRSTUVWXYZ".charAt(RANDOM.nextInt(24)));
        sb.append("abcdefghijkmnpqrstuvwxyz".charAt(RANDOM.nextInt(24)));
        sb.append("23456789".charAt(RANDOM.nextInt(8)));
        for (int i = 0; i < 13; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
