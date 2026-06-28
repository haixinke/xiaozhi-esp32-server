package xiaozhi.modules.payment.vo;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import xiaozhi.modules.payment.entity.PaymentOrderEntity;

import java.util.Date;

@Data
@Schema(description = "订单信息")
public class OrderVO {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "商户订单号")
    private String outTradeNo;

    @Schema(description = "商品类型: SUBSCRIPTION/ITEM")
    private String productType;

    @Schema(description = "商品关联ID")
    private Long productRefId;

    @Schema(description = "购买数量")
    private Integer quantity;

    @Schema(description = "订单金额(分)")
    private Long amountFen;

    @Schema(description = "支付渠道")
    private String payChannel;

    @Schema(description = "订单状态: 0待支付 1已支付 2已发货 3已取消 4已退款 5已超时")
    private Integer status;

    @Schema(description = "微信支付交易号")
    private String transactionId;

    @Schema(description = "支付成功时间")
    private Date paidAt;

    @Schema(description = "履约完成时间")
    private Date fulfilledAt;

    @Schema(description = "创建时间")
    private Date createdAt;

    @Schema(description = "商品名称（从快照解析）")
    private String productName;

    /** 将 Entity 转换为 VO（隐藏内部字段如 prepayId、clientIp 等） */
    public static OrderVO toVO(PaymentOrderEntity entity) {
        OrderVO vo = new OrderVO();
        vo.setId(entity.getId());
        vo.setOutTradeNo(entity.getOutTradeNo());
        vo.setProductType(entity.getProductType());
        vo.setProductRefId(entity.getProductRefId());
        vo.setQuantity(entity.getQuantity());
        vo.setAmountFen(entity.getAmountFen());
        vo.setPayChannel(entity.getPayChannel());
        vo.setStatus(entity.getStatus());
        vo.setTransactionId(entity.getTransactionId());
        vo.setPaidAt(entity.getPaidAt());
        vo.setFulfilledAt(entity.getFulfilledAt());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setProductName(extractProductName(entity.getProductSnapshot(), entity.getProductType()));
        return vo;
    }

    /**
     * 从订单商品快照JSON中提取商品名称。
     * SUBSCRIPTION 类型取 planName，ITEM 类型取 skuName。
     * 解析失败时回退为类型兜底文案。
     */
    private static String extractProductName(String snapshot, String productType) {
        String fallback = "SUBSCRIPTION".equals(productType) ? "订阅套餐" : "道具";
        if (StringUtils.isBlank(snapshot)) {
            return fallback;
        }
        try {
            JSONObject snap = JSONUtil.parseObj(snapshot);
            String name = snap.getStr("planName");
            if (StringUtils.isBlank(name)) {
                name = snap.getStr("skuName");
            }
            return StringUtils.isBlank(name) ? fallback : name;
        } catch (Exception e) {
            return fallback;
        }
    }
}
