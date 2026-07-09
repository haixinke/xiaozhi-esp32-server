package xiaozhi.modules.wechat.service;

import xiaozhi.common.service.BaseService;
import xiaozhi.modules.wechat.dto.WechatBindPhoneRespDTO;
import xiaozhi.modules.wechat.dto.WechatLoginRespDTO;
import xiaozhi.modules.wechat.entity.WechatUserEntity;

/**
 * 微信小程序登录服务
 */
public interface WechatService extends BaseService<WechatUserEntity> {

    /**
     * 微信小程序登录：用 jscode 换取 openid，若用户不存在则自动创建账号并返回 token
     *
     * @param code 微信小程序 wx.login 返回的 code
     * @return 包含 token / openid / isNewUser 的响应
     */
    WechatLoginRespDTO login(String code);

    /**
     * 将当前微信登录的临时账号切换绑定到指定的已有账号
     *
     * @param currentUserId 当前微信登录态对应的 sys_user.id
     * @param username      待绑定的已有账号用户名
     * @param password      待绑定的已有账号明文密码
     */
    void bindAccount(Long currentUserId, String username, String password);

    /**
     * 用微信 getPhoneNumber 授权 code 换取明文手机号并写入当前用户的 ai_wechat_user 记录
     *
     * @param currentUserId 当前登录态对应的 sys_user.id
     * @param phoneCode     小程序 getPhoneNumber 回调返回的动态 code（e.detail.code）
     * @return 脱敏后的手机号
     */
    WechatBindPhoneRespDTO bindPhone(Long currentUserId, String phoneCode);
}
