package xiaozhi.modules.invite.service.impl;

import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.invite.constant.InviteCodeType;
import xiaozhi.modules.invite.dao.InviteCodeDao;
import xiaozhi.modules.invite.dao.InviteUsageDao;
import xiaozhi.modules.invite.dto.InviteCodeCreateDTO;
import xiaozhi.modules.invite.dto.InviteCodeUpdateDTO;
import xiaozhi.modules.invite.entity.InviteCodeEntity;
import xiaozhi.modules.invite.entity.InviteUsageEntity;
import xiaozhi.modules.invite.service.InviteService;
import xiaozhi.modules.invite.util.InviteCodeGenerator;
import xiaozhi.modules.invite.vo.InviteCodeVO;
import xiaozhi.modules.invite.vo.InviteConsumeVO;
import xiaozhi.modules.invite.vo.InviteStatsVO;
import xiaozhi.modules.invite.vo.InviteUsageVO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class InviteServiceImpl extends BaseServiceImpl<InviteCodeDao, InviteCodeEntity>
        implements InviteService {

    private static final int CODE_MAX_RETRY = 5;

    @Autowired
    private InviteUsageDao inviteUsageDao;

    @Value("${invite.personal.quota:5}")
    private int personalQuota;

    private Clock clock = Clock.systemDefaultZone();

    /** 供测试注入固定时钟 */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    private Date now() {
        return Date.from(clock.instant());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public InviteCodeVO createPersonalCode(Long userId) {
        if (userId == null) {
            throw new RenException(ErrorCode.NOT_NULL);
        }
        InviteCodeEntity existing = baseDao.selectOne(new QueryWrapper<InviteCodeEntity>()
                .eq("owner_user_id", userId).eq("type", InviteCodeType.PERSONAL));
        if (existing != null) {
            return toVO(existing);
        }
        InviteCodeEntity entity = new InviteCodeEntity();
        entity.setCode(generateUniqueCode());
        entity.setType(InviteCodeType.PERSONAL);
        entity.setOwnerUserId(userId);
        entity.setQuota(personalQuota);
        entity.setUsedCount(0);
        entity.setRemaining(personalQuota);
        entity.setStatus(1);
        entity.setCreateDate(now());
        baseDao.insert(entity);
        return toVO(entity);
    }

    @Override
    public InviteCodeVO getMine(Long userId) {
        InviteCodeEntity entity = baseDao.selectOne(new QueryWrapper<InviteCodeEntity>()
                .eq("owner_user_id", userId).eq("type", InviteCodeType.PERSONAL));
        if (entity == null) {
            throw new RenException("未找到个人邀请码");
        }
        return toVO(entity);
    }

    private String generateUniqueCode() {
        for (int i = 0; i < CODE_MAX_RETRY; i++) {
            String code = InviteCodeGenerator.generate();
            Long count = baseDao.selectCount(new QueryWrapper<InviteCodeEntity>().eq("code", code));
            if (count == null || count == 0) {
                return code;
            }
        }
        throw new RenException("邀请码生成失败，请重试");
    }

    private InviteCodeVO toVO(InviteCodeEntity e) {
        InviteCodeVO vo = new InviteCodeVO();
        vo.setId(e.getId());
        vo.setCode(e.getCode());
        vo.setType(e.getType());
        vo.setOwnerUserId(e.getOwnerUserId());
        vo.setQuota(e.getQuota());
        vo.setUsedCount(e.getUsedCount());
        vo.setRemaining(e.getRemaining());
        vo.setStatus(e.getStatus());
        vo.setExpireTime(e.getExpireTime());
        vo.setRemark(e.getRemark());
        vo.setCreateDate(e.getCreateDate());
        return vo;
    }

    // 以下方法在后续任务实现
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InviteConsumeVO consume(String code, Long inviteeUserId) {
        if (code == null || code.isBlank()) {
            throw new RenException(ErrorCode.NOT_NULL);
        }
        if (inviteeUserId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }

        InviteCodeEntity entity = baseDao.selectByCodeForUpdate(code);
        if (entity == null) {
            throw new RenException("邀请码无效");
        }
        if (entity.getStatus() == null || entity.getStatus() != 1) {
            throw new RenException("邀请码已失效");
        }
        if (entity.getExpireTime() != null && !entity.getExpireTime().after(now())) {
            throw new RenException("邀请码已过期");
        }
        if (entity.getRemaining() == null || entity.getRemaining() <= 0) {
            throw new RenException("邀请码已无剩余");
        }
        if (entity.getOwnerUserId() != null && entity.getOwnerUserId().equals(inviteeUserId)) {
            throw new RenException("不能使用自己的邀请码");
        }

        // 幂等：同一被邀请人对同一码重复消耗不扣减
        Long used = inviteUsageDao.selectCount(new QueryWrapper<InviteUsageEntity>()
                .eq("code_id", entity.getId()).eq("invitee_user_id", inviteeUserId));
        if (used != null && used > 0) {
            InviteConsumeVO vo = new InviteConsumeVO();
            vo.setCodeId(entity.getId());
            vo.setRemaining(entity.getRemaining());
            vo.setStatus(entity.getStatus());
            vo.setMessage("已使用过该邀请码");
            return vo;
        }

        int affected = baseDao.decrementRemaining(entity.getId());
        if (affected == 0) {
            throw new RenException("邀请码已无剩余");
        }

        InviteUsageEntity usage = new InviteUsageEntity();
        usage.setCodeId(entity.getId());
        usage.setInviteeUserId(inviteeUserId);
        usage.setCreateDate(now());
        inviteUsageDao.insert(usage);

        InviteConsumeVO vo = new InviteConsumeVO();
        vo.setCodeId(entity.getId());
        vo.setRemaining(entity.getRemaining() - 1);
        vo.setStatus(entity.getStatus());
        vo.setMessage("success");
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InviteCodeVO createEnterprise(InviteCodeCreateDTO dto) {
        if (dto.getQuota() == null || dto.getQuota() <= 0) {
            throw new RenException("配额必须大于0");
        }
        InviteCodeEntity entity = new InviteCodeEntity();
        entity.setCode(generateUniqueCode());
        entity.setType(InviteCodeType.ENTERPRISE);
        entity.setOwnerUserId(null);
        entity.setQuota(dto.getQuota());
        entity.setUsedCount(0);
        entity.setRemaining(dto.getQuota());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        entity.setExpireTime(dto.getExpireTime());
        entity.setRemark(dto.getRemark());
        entity.setCreateDate(now());
        baseDao.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(InviteCodeUpdateDTO dto) {
        InviteCodeEntity entity = baseDao.selectById(dto.getId());
        if (entity == null) {
            throw new RenException("邀请码不存在");
        }
        if (dto.getQuota() != null) {
            int used = entity.getUsedCount() == null ? 0 : entity.getUsedCount();
            if (dto.getQuota() < used) {
                throw new RenException("配额不能小于已使用数量");
            }
            entity.setQuota(dto.getQuota());
            entity.setRemaining(dto.getQuota() - used);
        }
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        if (dto.getExpireTime() != null) {
            entity.setExpireTime(dto.getExpireTime());
        }
        if (dto.getRemark() != null) {
            entity.setRemark(dto.getRemark());
        }
        entity.setUpdateDate(now());
        baseDao.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        InviteCodeEntity entity = baseDao.selectById(id);
        if (entity == null) {
            throw new RenException("邀请码不存在");
        }
        int used = entity.getUsedCount() == null ? 0 : entity.getUsedCount();
        if (used > 0) {
            throw new RenException("邀请码已被使用，无法删除");
        }
        baseDao.deleteById(id);
    }

    @Override
    public PageData<InviteCodeVO> page(Map<String, Object> params) {
        IPage<InviteCodeEntity> page = getPage(params, "create_date", false);
        QueryWrapper<InviteCodeEntity> wrapper = new QueryWrapper<>();
        if (params.get("type") != null) {
            wrapper.eq("type", params.get("type"));
        }
        if (params.get("status") != null) {
            wrapper.eq("status", params.get("status"));
        }
        if (params.get("ownerUserId") != null) {
            wrapper.eq("owner_user_id", params.get("ownerUserId"));
        }
        IPage<InviteCodeEntity> result = baseDao.selectPage(page, wrapper);
        List<InviteCodeVO> list = result.getRecords().stream()
                .map(this::toVO).collect(Collectors.toList());
        return new PageData<>(list, result.getTotal());
    }

    @Override
    public PageData<InviteUsageVO> usageList(Long codeId, Map<String, Object> params) {
        long cur = params.get("page") == null ? 1 : Long.parseLong(params.get("page").toString());
        long limit = params.get("limit") == null ? 10 : Long.parseLong(params.get("limit").toString());
        Page<InviteUsageEntity> page = new Page<>(cur, limit);
        QueryWrapper<InviteUsageEntity> wrapper =
                new QueryWrapper<InviteUsageEntity>().eq("code_id", codeId);
        IPage<InviteUsageEntity> result = inviteUsageDao.selectPage(page, wrapper);
        List<InviteUsageVO> list = result.getRecords().stream()
                .map(this::toUsageVO).collect(Collectors.toList());
        return new PageData<>(list, result.getTotal());
    }

    @Override
    public InviteStatsVO stats() {
        InviteStatsVO vo = new InviteStatsVO();
        vo.setTotalCodes(toInt(baseDao.selectCount(null)));
        vo.setTotalConsumed(toInt(inviteUsageDao.selectCount(null)));
        vo.setPersonalCount(toInt(baseDao.selectCount(
                new QueryWrapper<InviteCodeEntity>().eq("type", InviteCodeType.PERSONAL))));
        vo.setEnterpriseCount(toInt(baseDao.selectCount(
                new QueryWrapper<InviteCodeEntity>().eq("type", InviteCodeType.ENTERPRISE))));
        return vo;
    }

    private static int toInt(Long v) {
        return v == null ? 0 : Math.toIntExact(v);
    }

    private InviteUsageVO toUsageVO(InviteUsageEntity e) {
        InviteUsageVO vo = new InviteUsageVO();
        vo.setId(e.getId());
        vo.setCodeId(e.getCodeId());
        vo.setInviteeUserId(e.getInviteeUserId());
        vo.setCreateDate(e.getCreateDate());
        return vo;
    }
}
