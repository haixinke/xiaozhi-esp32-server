package xiaozhi.modules.wechat.service.impl;

import java.util.Date;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.TokenDTO;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.security.password.PasswordUtils;
import xiaozhi.modules.security.service.SysUserTokenService;
import xiaozhi.modules.sys.dao.SysUserDao;
import xiaozhi.modules.sys.entity.SysUserEntity;
import xiaozhi.modules.sys.enums.SuperAdminEnum;
import xiaozhi.modules.wechat.dao.WechatUserDao;
import xiaozhi.modules.wechat.dto.WechatLoginRespDTO;
import xiaozhi.modules.wechat.entity.WechatUserEntity;
import xiaozhi.modules.wechat.service.WechatService;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.agent.dto.AgentCreateDTO;

/**
 * 微信小程序登录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatServiceImpl extends BaseServiceImpl<WechatUserDao, WechatUserEntity> implements WechatService {

    private static final String JSCODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    private final SysUserDao sysUserDao;
    private final SysUserTokenService sysUserTokenService;
    private final AgentService agentService;

    @Value("${wechat.miniprogram.appid:}")
    private String appid;

    @Value("${wechat.miniprogram.secret:}")
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
            baseDao.insert(wechatUser);
            isNewUser = true;
        }

        // 5. 检查用户是否有智能体，如果没有则创建一个基于模板的智能体
        String agentId = getOrCreateUserAgent(userId, isNewUser ? username : null);

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
        resp.setIsNewUser(isNewUser);
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

    /**
     * 调用微信 jscode2session 接口
     */
    private JSONObject jscode2session(String code) {
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
        String prefix = openid.length() >= 8 ? openid.substring(0, 8) : openid;
        String username = "wx_" + prefix;
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
        return new UserCreationResult(user.getId(), username);
    }

    /**
     * 获取或创建用户智能体
     * 如果用户没有智能体，则根据模板创建一个新智能体，智能体名称为用户名
     *
     * @param userId 用户ID
     * @param username 用户名（仅在新用户时使用）
     * @return 智能体ID
     */
    private String getOrCreateUserAgent(Long userId, String username) {
        // 查询用户是否已有智能体
        java.util.List<xiaozhi.modules.agent.dto.AgentDTO> agents = agentService.getUserAgents(userId, null, null);
        if (agents != null && !agents.isEmpty()) {
            // 用户已有智能体，返回第一个智能体ID
            return agents.get(0).getId();
        }

        // 用户没有智能体，创建一个基于模板的智能体
        AgentCreateDTO dto = new AgentCreateDTO();
        // 使用用户名作为智能体名称，如果username为空则使用默认名称
        dto.setAgentName(StringUtils.isNotBlank(username) ? username : "我的助手");

        return agentService.createAgent(dto);
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
