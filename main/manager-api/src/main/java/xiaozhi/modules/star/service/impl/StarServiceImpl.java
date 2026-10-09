package xiaozhi.modules.star.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.star.dao.StarAccountDao;
import xiaozhi.modules.star.dao.StarTransactionDao;
import xiaozhi.modules.star.entity.StarAccountEntity;
import xiaozhi.modules.star.entity.StarTransactionEntity;
import xiaozhi.modules.star.enums.StarConsumeBizType;
import xiaozhi.modules.star.enums.StarEarnBizType;
import xiaozhi.modules.star.service.StarService;

/**
 * 星星罐服务实现。
 *
 * 幂等设计（照 item 模块 grant 模式）：流水先写，唯一索引 (user_id, biz_type, ref_id) 撞键
 * 即幂等返回——此时事务尚未触碰余额，无需回滚。余额变动在流水插入成功后执行；
 * 余额不足/异常时事务回滚，连带已插入的流水一起撤销，保证账户与流水始终一致。
 *
 * 本模块只管资金变动（earn/consume/balance/transactions），不承载履约状态（ADR 0009）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StarServiceImpl implements StarService {

    private final StarAccountDao starAccountDao;
    private final StarTransactionDao starTransactionDao;

    /**
     * 赚取星星（幂等）。账户行并发安全创建 → 写流水(撞唯一索引=幂等返回) →
     * 原子加余额 → 回填 balance_after。同事务。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StarTransactionEntity earn(Long userId, StarEarnBizType bizType, String refId, long amount, String remark) {
        validate(userId, refId, amount);
        ensureAccount(userId);

        StarTransactionEntity txn = buildTxn(userId, "earn", bizType.getCode(), refId, amount, remark);
        if (!insertTxnOrIdempotent(userId, bizType.getCode(), refId, txn)) {
            return txn; // 幂等命中，txn 为已有流水
        }

        int affected = starAccountDao.addBalance(userId, amount);
        if (affected <= 0) {
            throw new IllegalStateException("星星账户加余额失败: userId=" + userId);
        }
        fillBalanceAfter(txn, userId);
        return txn;
    }

    /**
     * 消费星星（幂等，原子扣减）。写流水(撞唯一索引=幂等返回) → 原子扣减
     * (余额不足抛异常,事务回滚连带流水) → 回填 balance_after。同事务。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StarTransactionEntity consume(Long userId, StarConsumeBizType bizType, String refId, long amount,
                                         String remark) {
        validate(userId, refId, amount);

        StarTransactionEntity txn = buildTxn(userId, "consume", bizType.getCode(), refId, -amount, remark);
        if (!insertTxnOrIdempotent(userId, bizType.getCode(), refId, txn)) {
            return txn; // 幂等命中，txn 为已有流水
        }

        int affected = starAccountDao.deductBalance(userId, amount);
        if (affected <= 0) {
            throw new RenException(ErrorCode.STAR_BALANCE_INSUFFICIENT);
        }
        fillBalanceAfter(txn, userId);
        return txn;
    }

    @Override
    public long balance(Long userId) {
        StarAccountEntity account = starAccountDao.selectOne(
                new QueryWrapper<StarAccountEntity>().eq("user_id", userId));
        return account == null || account.getBalance() == null ? 0L : account.getBalance();
    }

    @Override
    public IPage<StarTransactionEntity> transactions(Long userId, long page, long limit) {
        return starTransactionDao.selectPage(new Page<>(page, limit),
                new QueryWrapper<StarTransactionEntity>()
                        .eq("user_id", userId)
                        .orderByDesc("id"));
    }

    /** 参数校验：refId 强制非空(幂等键)，amount 必须为正 */
    private void validate(Long userId, String refId, long amount) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (StringUtils.isBlank(refId)) {
            throw new RenException(ErrorCode.STAR_REF_ID_REQUIRED);
        }
        if (amount <= 0) {
            throw new RenException(ErrorCode.STAR_AMOUNT_INVALID);
        }
    }

    /** 确保账户行存在；并发插入冲突时忽略（另一事务已建） */
    private void ensureAccount(Long userId) {
        StarAccountEntity account = starAccountDao.selectOne(
                new QueryWrapper<StarAccountEntity>().eq("user_id", userId));
        if (account != null) {
            return;
        }
        try {
            StarAccountEntity created = new StarAccountEntity();
            created.setUserId(userId);
            created.setBalance(0L);
            starAccountDao.insert(created);
        } catch (DuplicateKeyException e) {
            log.info("星星账户并发插入冲突，忽略：userId={}", userId);
        }
    }

    /**
     * 插入流水；撞唯一索引 (user_id, biz_type, ref_id) 时按幂等命中处理：
     * 同事务内重查已有流水（该流水由先前已提交事务写入，当前事务尚未加/扣余额，
     * 可安全读到并直接返回），填充传入的 txn 引用后返回 false。
     *
     * @return true=本次新插入；false=幂等命中（txn 已被替换为已有流水内容）
     */
    private boolean insertTxnOrIdempotent(Long userId, String bizType, String refId, StarTransactionEntity txn) {
        try {
            starTransactionDao.insert(txn);
            return true;
        } catch (DuplicateKeyException dup) {
            log.info("星星流水已存在，幂等返回：userId={}, biz={}, ref={}", userId, bizType, refId);
            StarTransactionEntity existing = starTransactionDao.selectOne(
                    new QueryWrapper<StarTransactionEntity>()
                            .eq("user_id", userId)
                            .eq("biz_type", bizType)
                            .eq("ref_id", refId));
            if (existing == null) {
                throw new IllegalStateException("幂等命中但流水不存在: userId=" + userId + ", ref=" + refId);
            }
            // 把已有流水内容拷贝到传入引用，保持方法返回语义统一
            txn.setId(existing.getId());
            txn.setAmount(existing.getAmount());
            txn.setBalanceAfter(existing.getBalanceAfter());
            txn.setRemark(existing.getRemark());
            txn.setCreateDate(existing.getCreateDate());
            return false;
        }
    }

    /** 余额变动后回填流水的操作后余额快照 */
    private void fillBalanceAfter(StarTransactionEntity txn, Long userId) {
        long after = balance(userId);
        txn.setBalanceAfter(after);
        StarTransactionEntity update = new StarTransactionEntity();
        update.setId(txn.getId());
        update.setBalanceAfter(after);
        starTransactionDao.updateById(update);
    }

    private StarTransactionEntity buildTxn(Long userId, String type, String bizType, String refId, long amount,
                                           String remark) {
        StarTransactionEntity txn = new StarTransactionEntity();
        txn.setUserId(userId);
        txn.setType(type);
        txn.setBizType(bizType);
        txn.setRefId(refId);
        txn.setAmount(amount);
        txn.setBalanceAfter(0L); // 占位，余额变动后由 fillBalanceAfter 回填
        txn.setRemark(remark);
        return txn;
    }
}
