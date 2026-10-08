package xiaozhi.modules.star.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import xiaozhi.modules.star.entity.StarTransactionEntity;
import xiaozhi.modules.star.enums.StarConsumeBizType;
import xiaozhi.modules.star.enums.StarEarnBizType;

import java.util.Optional;

/**
 * 星星罐服务：用户级虚拟货币的赚取与消费。
 * 所有变动经本服务内部直调（Java），不开放 HTTP 写接口。
 * 幂等契约：earn/consume 强制 refId（调用方业务单号），
 * 重复提交经唯一索引 (user_id, biz_type, ref_id) 幂等返回，不重复入账。
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
     * TRAVEL 场景必填 petPrototype，流水写入 pending 履约状态；
     * 其余场景写入即 fulfilled。
     *
     * @param userId       用户ID
     * @param bizType      消费场景
     * @param refId        业务单号(幂等键)；TRAVEL 由调用方生成 tv- 前缀预订单号
     * @param amount       数量，正整数
     * @param petPrototype 宠物原型(KOI/RABBIT)，仅 TRAVEL 必填
     * @param remark       备注，可空
     * @return 流水实体（新写入或已存在的幂等命中行）
     */
    StarTransactionEntity consume(Long userId, StarConsumeBizType bizType, String refId, long amount,
                                  String petPrototype, String remark);

    /**
     * 消耗某用户某原型最早一笔 pending 旅行预订（履约）。
     * 供故事引擎在旅行结束时调用：命中则置 fulfilled 并回填日记ID，未命中返回空（不发日记）。
     * 原子操作，并发/重复调用安全，同一笔流水只履约一次。
     *
     * @param userId     用户ID
     * @param prototype  宠物原型(KOI/RABBIT)
     * @param diaryRefId 履约产物ID(旅行日记ID)
     * @return 被履约的流水；无 pending 预订时为 empty
     */
    Optional<StarTransactionEntity> consumePendingTravelPreorder(Long userId, String prototype, String diaryRefId);

    /**
     * 查询用户星星余额；账户不存在视为 0。
     */
    long balance(Long userId);

    /**
     * 分页查询用户流水（earn/consume 混排，按创建时间倒序）。
     */
    IPage<StarTransactionEntity> transactions(Long userId, long page, long limit);
}
