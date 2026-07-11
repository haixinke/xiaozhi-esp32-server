package xiaozhi.modules.wechat.service;

import xiaozhi.common.service.BaseService;
import xiaozhi.modules.wechat.dto.WechatBindPhoneRespDTO;
import xiaozhi.modules.wechat.dto.WechatLoginRespDTO;
import xiaozhi.modules.wechat.dto.WechatProfileUpdateDTO;
import xiaozhi.modules.wechat.entity.WechatUserEntity;
import xiaozhi.modules.wechat.vo.WechatProfileVO;

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

    /**
     * 查询当前用户资料
     *
     * @param userId 当前登录态对应的 sys_user.id
     * @return 用户资料视图（含脱敏手机号、星座）
     */
    WechatProfileVO getProfile(Long userId);

    /**
     * 更新当前用户资料（字段全可选，部分更新）
     *
     * @param userId 当前登录态对应的 sys_user.id
     * @param dto    更新请求
     */
    void updateProfile(Long userId, WechatProfileUpdateDTO dto);

    /**
     * 上传头像到 OSS，返回公开访问 URL
     *
     * @param userId 当前登录态对应的 sys_user.id
     * @param file   头像文件
     * @return 公开 URL
     */
    String uploadAvatar(Long userId, org.springframework.web.multipart.MultipartFile file);
}
