package xiaozhi.modules.subscription.service;

import xiaozhi.modules.subscription.vo.EntitlementVO;
import xiaozhi.modules.subscription.vo.SubscriptionPlanVO;
import xiaozhi.modules.subscription.vo.UserSubscriptionVO;

import java.util.List;

/**
 * 订阅服务
 */
public interface SubscriptionService {

    /** 列出上架中的订阅档位 */
    List<SubscriptionPlanVO> listActivePlans();

    /** 当前用户生效中的订阅（无则返回null） */
    UserSubscriptionVO getActiveSubscription(Long userId);

    /** 当前用户拥有的权益（用于小程序前置 UI 灰度） */
    EntitlementVO getEntitlements(Long userId);

    /**
     * 校验用户是否拥有某能力，未拥有抛 SUBSCRIPTION_FEATURE_DENIED
     */
    void requireFeature(Long userId, String featureCode);

    /** 用户是否拥有某能力（不抛异常） */
    boolean hasFeature(Long userId, String featureCode);
}
