package xiaozhi.modules.subscription.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.security.user.SecurityUser;
import xiaozhi.modules.subscription.service.SubscriptionService;
import xiaozhi.modules.subscription.vo.EntitlementVO;
import xiaozhi.modules.subscription.vo.SubscriptionPlanVO;
import xiaozhi.modules.subscription.vo.UserSubscriptionVO;

import java.util.List;

@Tag(name = "订阅管理")
@RestController
@RequestMapping("/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/plans")
    @Operation(summary = "列出可购买的订阅档位（公开）")
    public Result<List<SubscriptionPlanVO>> plans() {
        return new Result<List<SubscriptionPlanVO>>().ok(subscriptionService.listActivePlans());
    }

    @GetMapping("/me")
    @Operation(summary = "我当前的订阅")
    public Result<UserSubscriptionVO> me() {
        Long userId = SecurityUser.getUserId();
        return new Result<UserSubscriptionVO>().ok(subscriptionService.getActiveSubscription(userId));
    }

    @GetMapping("/entitlements")
    @Operation(summary = "我当前拥有的能力点")
    public Result<EntitlementVO> entitlements() {
        Long userId = SecurityUser.getUserId();
        return new Result<EntitlementVO>().ok(subscriptionService.getEntitlements(userId));
    }
}
