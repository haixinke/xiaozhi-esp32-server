package xiaozhi.modules.wechat.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.user.UserDetail;
import xiaozhi.modules.security.user.SecurityUser;
import xiaozhi.modules.wechat.service.WechatPhoneGate;

/** 登录后的统一业务门槛；手机号绑定和宠物状态恢复必须始终可达。 */
@Component
@RequiredArgsConstructor
public class WechatPhoneInterceptor implements HandlerInterceptor {
    private final WechatPhoneGate phoneGate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        if ("OPTIONS".equals(request.getMethod()) || isRecoveryOrIncubation(request.getMethod(), path)) {
            return true;
        }
        UserDetail user = SecurityUser.getUser();
        // 服务密钥接口和匿名接口维持现有鉴权；设备链路另按数据库设备归属校验。
        if (user.getId() == null || Integer.valueOf(1).equals(user.getSuperAdmin())) {
            return true;
        }
        if (phoneGate.requiresPhoneBinding(user.getId())) {
            throw new RenException(ErrorCode.WECHAT_PHONE_REQUIRED);
        }
        return true;
    }

    private boolean isRecoveryOrIncubation(String method, String path) {
        // 精确区分读写，避免把资料读取的恢复豁免扩展到后续业务操作。
        if ("GET".equals(method)) {
            return "/wechat/profile".equals(path) || "/pet/list".equals(path)
                    || path.matches("/pet/[^/]+") || path.matches("/pet/[^/]+/hatch-actions")
                    || "/pdc/nfc/claim/preview".equals(path);
        }
        if ("POST".equals(method)) {
            return "/wechat/login".equals(path) || "/wechat/bindPhone".equals(path)
                    || "/pet/adopt".equals(path) || path.matches("/pet/[^/]+/hatch")
                    || path.matches("/pet/[^/]+/hatch-action")
                    || "/pdc/nfc/claim/confirm".equals(path);
        }
        return false;
    }
}
