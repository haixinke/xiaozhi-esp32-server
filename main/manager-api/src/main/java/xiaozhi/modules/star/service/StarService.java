package xiaozhi.modules.star.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import xiaozhi.modules.star.entity.StarTransactionEntity;
import xiaozhi.modules.star.enums.StarConsumeBizType;
import xiaozhi.modules.star.enums.StarEarnBizType;
import xiaozhi.modules.star.vo.StarConsumeResult;

/**
 * 星星罐服务：用户级虚拟货币的赚取与消费。
 * 所有变动经本服务内部直调（Java），不开放 HTTP 写接口。
 * 幂等契约：earn/consume 强制 refId（调用方业务单号），
 * 重复提交经唯一索引 (user_id, biz_type, ref_id) 幂等返回，不重复入账。
 * 本模块只管资金变动；履约语义（状态/产物/原型）由 refId 关联的业务对象承载（ADR 0009）。
 */
public interface StarService {

    /**
     * 赚取星星（幂等）。重复 refId 返回已有流水，不重复入账。
     *
     * @param userId  用户ID
     * @param bizType 赚取渠道
     * @param refId   业务单号(幂等键)，如签到日期；必填
     * @param amount  数量，正整数
     * @param remark  备注，可空
     * @return 流水实体（新写入或已存在的幂等命中行）
     */
    StarTransactionEntity earn(Long userId, StarEarnBizType bizType, String refId, long amount, String remark);

    /**
     * 消费星星（幂等，原子扣减）。余额不足抛业务异常，账户与流水零写入。
     *
     * @param userId  用户ID
     * @param bizType 消费场景
     * @param refId   业务单号(幂等键)，格式由调用方自定（建议带场景前缀便于对账）
     * @param amount  数量，正整数
     * @param remark  备注，可空
     * @return 流水实体（新写入或已存在的幂等命中行）
     */
    StarTransactionEntity consume(Long userId, StarConsumeBizType bizType, String refId, long amount, String remark);

    /**
     * 在独立事务（REQUIRES_NEW）中消费星星（幂等，原子扣减）。
     *
     * <p>与 {@link #consume} 的差别：本方法显式挂起外层事务、扣费结果单独提交，
     * 外层业务回滚不会连带回滚已扣款；并显式区分"本次新扣费"与"幂等命中已有流水"，
     * 便于调用方（如游戏按局扣费）区分重试语义。
     *
     * <p>账户创建、流水写入、原子扣减在同一事务提交；余额不足抛业务异常，事务回滚
     * 连带流水，重试不会被误判为已扣款。
     *
     * @param userId  用户ID
     * @param bizType 消费场景
     * @param refId   业务单号(幂等键)，必填
     * @param amount  数量，正整数
     * @param remark  备注，可空
     * @return 资金变动结果，区分新扣费 / 幂等命中
     */
    StarConsumeResult consumeIndependent(Long userId, StarConsumeBizType bizType, String refId, long amount,
            String remark);

    /**
     * 查询用户星星余额；账户不存在视为 0。
     */
    long balance(Long userId);

    /**
     * 分页查询用户流水（earn/consume 混排，按创建时间倒序）。
     */
    IPage<StarTransactionEntity> transactions(Long userId, long page, long limit);
}
