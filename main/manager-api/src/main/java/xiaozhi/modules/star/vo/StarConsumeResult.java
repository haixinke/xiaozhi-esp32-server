package xiaozhi.modules.star.vo;

import xiaozhi.modules.star.entity.StarTransactionEntity;

/**
 * 星星资金变动结果。仅供服务端内部方法返回，不对客户端暴露。
 *
 * <p>区分两种语义：本次新写入 / 幂等命中已有流水。调用方据此区分"新扣费"与"重复请求"，
 * 无需再查一次流水。
 */
public record StarConsumeResult(boolean newCharge, StarTransactionEntity transaction) {

    /** 本次新写入 */
    public static StarConsumeResult newCharge(StarTransactionEntity txn) {
        return new StarConsumeResult(true, txn);
    }

    /** 幂等命中：返回已存在的原流水 */
    public static StarConsumeResult alreadyCharged(StarTransactionEntity txn) {
        return new StarConsumeResult(false, txn);
    }
}
