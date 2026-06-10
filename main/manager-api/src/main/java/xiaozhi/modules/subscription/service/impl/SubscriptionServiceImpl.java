package xiaozhi.modules.subscription.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.subscription.dao.SubscriptionPlanDao;
import xiaozhi.modules.subscription.dao.UserSubscriptionDao;
import xiaozhi.modules.subscription.entity.SubscriptionPlanEntity;
import xiaozhi.modules.subscription.entity.UserSubscriptionEntity;
import xiaozhi.modules.subscription.enums.SubscriptionStatus;
import xiaozhi.modules.subscription.service.SubscriptionService;
import xiaozhi.modules.subscription.vo.EntitlementVO;
import xiaozhi.modules.subscription.vo.SubscriptionPlanVO;
import xiaozhi.modules.subscription.vo.UserSubscriptionVO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionPlanDao planDao;
    private final UserSubscriptionDao subscriptionDao;

    /** 查询所有上架的订阅档位 */
    @Override
    public List<SubscriptionPlanVO> listActivePlans() {
        QueryWrapper<SubscriptionPlanEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1).orderByAsc("sort");
        List<SubscriptionPlanEntity> entities = planDao.selectList(wrapper);
        List<SubscriptionPlanVO> result = new ArrayList<>(entities.size());
        for (SubscriptionPlanEntity entity : entities) {
            result.add(SubscriptionPlanVO.toVO(entity, parseFeatures(entity.getFeatures()), parseBonusItems(entity.getBonusItems())));
        }
        return result;
    }

    /** 获取用户当前生效的订阅 */
    @Override
    public UserSubscriptionVO getActiveSubscription(Long userId) {
        UserSubscriptionEntity active = findActiveEntity(userId);
        if (active == null) {
            return null;
        }
        return UserSubscriptionVO.toVO(active, parseFeatures(active.getFeaturesSnapshot()));
    }

    /** 获取用户权益概览 */
    @Override
    public EntitlementVO getEntitlements(Long userId) {
        EntitlementVO vo = new EntitlementVO();
        UserSubscriptionEntity active = findActiveEntity(userId);
        if (active == null) {
            vo.setActive(false);
            vo.setFeatures(Collections.emptyList());
            return vo;
        }
        vo.setActive(true);
        vo.setPlanCode(active.getPlanCode());
        vo.setFeatures(parseFeatures(active.getFeaturesSnapshot()));
        vo.setEndAt(active.getEndAt());
        return vo;
    }

    /** 校验用户是否拥有指定能力，无则抛异常 */
    @Override
    public void requireFeature(Long userId, String featureCode) {
        if (!hasFeature(userId, featureCode)) {
            throw new RenException(ErrorCode.SUBSCRIPTION_FEATURE_DENIED);
        }
    }

    /** 判断用户是否拥有指定能力 */
    @Override
    public boolean hasFeature(Long userId, String featureCode) {
        UserSubscriptionEntity active = findActiveEntity(userId);
        if (active == null) {
            return false;
        }
        return parseFeatures(active.getFeaturesSnapshot()).contains(featureCode);
    }

    /**
     * 查询当前生效中的订阅（status=1 且 end_at>now）
     */
    private UserSubscriptionEntity findActiveEntity(Long userId) {
        QueryWrapper<UserSubscriptionEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("status", SubscriptionStatus.ACTIVE.getValue())
                .gt("end_at", new Date())
                .orderByDesc("end_at")
                .last("LIMIT 1");
        return subscriptionDao.selectOne(wrapper);
    }

    /** 解析权益JSON数组 */
    public static List<String> parseFeatures(String featuresJson) {
        if (StringUtils.isBlank(featuresJson)) {
            return Collections.emptyList();
        }
        try {
            JSONArray arr = JSONUtil.parseArray(featuresJson);
            List<String> result = new ArrayList<>(arr.size());
            for (Object item : arr) {
                result.add(String.valueOf(item));
            }
            return result;
        } catch (Exception e) {
            log.warn("解析 features 失败: {}", featuresJson, e);
            return Collections.emptyList();
        }
    }

    /** 解析附赠道具JSON数组 */
    public static List<SubscriptionPlanVO.BonusItem> parseBonusItems(String bonusJson) {
        if (StringUtils.isBlank(bonusJson)) {
            return Collections.emptyList();
        }
        try {
            JSONArray arr = JSONUtil.parseArray(bonusJson);
            List<SubscriptionPlanVO.BonusItem> result = new ArrayList<>(arr.size());
            for (Object o : arr) {
                JSONObject obj = (JSONObject) o;
                SubscriptionPlanVO.BonusItem item = new SubscriptionPlanVO.BonusItem();
                item.setSkuCode(obj.getStr("skuCode"));
                item.setCount(obj.getInt("count"));
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            log.warn("解析 bonus_items 失败: {}", bonusJson, e);
            return Collections.emptyList();
        }
    }
}
