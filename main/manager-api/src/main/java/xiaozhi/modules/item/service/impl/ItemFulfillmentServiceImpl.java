package xiaozhi.modules.item.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.modules.item.enums.ItemGrantSource;
import xiaozhi.modules.item.service.ItemFulfillmentService;
import xiaozhi.modules.item.service.ItemService;
import xiaozhi.modules.payment.entity.PaymentOrderEntity;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemFulfillmentServiceImpl implements ItemFulfillmentService {

    private final ItemService itemService;

    /** 道具履约：从订单快照提取skuCode并发放到用户库存 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void fulfill(PaymentOrderEntity order) {
        // 从快照中拿 sku_code（防 sku 表后续被改）
        JSONObject snapshot = parseSnapshot(order.getProductSnapshot());
        String skuCode = snapshot.getStr("skuCode");
        if (skuCode == null || skuCode.isBlank()) {
            log.error("订单快照缺少 skuCode: orderId={}", order.getId());
            return;
        }
        int count = order.getQuantity() != null ? order.getQuantity() : 1;
        itemService.grant(order.getUserId(), skuCode, count, ItemGrantSource.PURCHASE, order.getOutTradeNo());
        log.info("道具履约完成 userId={}, sku={}, count={}, ref={}",
                order.getUserId(), skuCode, count, order.getOutTradeNo());
    }

    /** 道具退款回滚（当前不自动减库存，留运营人工处理） */
    @Override
    public void rollback(PaymentOrderEntity order) {
        // 退款回退道具：当前实现不主动减库存（避免误伤已使用部分）；
        // 留待运营在管理后台基于 item_grant_log + item_consume_log 比对人工处理。
        log.info("道具退款回滚（人工处理建议）orderId={}, outTradeNo={}", order.getId(), order.getOutTradeNo());
    }

    private static JSONObject parseSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return new JSONObject();
        }
        try {
            return JSONUtil.parseObj(snapshot);
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
