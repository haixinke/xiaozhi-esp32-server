package xiaozhi.modules.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.entity.PetEntity;

import java.util.Date;
import java.util.List;

/** 领养名额与渠道互斥：集中活跃宠物查询、替换和建蛋，不涉及展示或心情生成。 */
@Service
@RequiredArgsConstructor
public class PetAdoptionModule {
    private static final long HATCH_DURATION_MS = 7L * 24 * 60 * 60 * 1000;
    private final PetDao petDao;

    /** NFC 领养结果；替换标记属于本次领养事实，不需要调用者重新扫描宠物列表。 */
    public record NfcEgg(PetEntity pet, boolean replacedInvitePet) { }

    /** 加入领取事务，先判定同原型 NFC 名额，再替换邀请码宠物并创建新蛋。 */
    @Transactional(rollbackFor = Exception.class)
    public NfcEgg claimNfcEgg(Long userId, String prototype) {
        validate(userId, prototype);
        lockUser(userId);
        List<PetEntity> owned = activeForUpdate(userId);
        if (owned.stream().anyMatch(pet -> !"INVITE_CODE".equals(pet.getSource()) && prototype.equals(pet.getPrototype()))) {
            throw new RenException(ErrorCode.PET_ALREADY_EXISTS);
        }
        List<PetEntity> invites = owned.stream().filter(pet -> "INVITE_CODE".equals(pet.getSource())).toList();
        for (PetEntity invite : invites) {
            logicallyDelete(userId, invite);
        }
        // 删除与建蛋保持同事务；同原型邀请码必须先被替换，才能释放唯一键名额。
        return new NfcEgg(insertEgg(userId, prototype, "NFC"), !invites.isEmpty());
    }

    /** 保留已有纯数据库建蛋入口的同原型名额语义。 */
    @Transactional(rollbackFor = Exception.class)
    public PetEntity createNfcEgg(Long userId, String prototype) {
        validate(userId, prototype);
        lockUser(userId);
        if (activeForUpdate(userId).stream().anyMatch(pet -> prototype.equals(pet.getPrototype()))) {
            throw new RenException(ErrorCode.PET_ALREADY_EXISTS);
        }
        return insertEgg(userId, prototype, "NFC");
    }

    /** 邀请码领养限一只；手机号在破壳后要求，所有渠道继续共用用户锁。 */
    @Transactional(rollbackFor = Exception.class)
    public PetEntity createInviteEgg(Long userId, String prototype) {
        validate(userId, prototype);
        lockUser(userId);
        if (!activeForUpdate(userId).isEmpty()) {
            throw new RenException(ErrorCode.PET_ALREADY_EXISTS);
        }
        return insertEgg(userId, prototype, "INVITE_CODE");
    }

    /** 查同原型 NFC 名额只读取事实；查询失败向外传播，不能降级成可领取。 */
    public PetEntity findOwnedNfcPet(Long userId, String prototype) {
        return petDao.selectOne(active(userId).eq("source", "NFC").eq("prototype", prototype));
    }

    /** NFC 重放读取已有心情；保持普通详情查询的归属与已删除语义。 */
    public PetEntity getStoredById(Long userId, String petId) {
        PetEntity pet = petDao.selectById(petId);
        if (pet == null || pet.isDeleted()) {
            throw new RenException(ErrorCode.PET_NOT_FOUND);
        }
        if (!userId.equals(pet.getUserId())) {
            throw new RenException(ErrorCode.PET_NO_PERMISSION);
        }
        return pet;
    }

    /** 用户主动删除与渠道替换共享时间戳碰撞重试规则。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByUserId(Long userId, String petId) {
        PetEntity pet = petDao.selectById(petId);
        if (pet == null || pet.isDeleted() || !userId.equals(pet.getUserId())) {
            throw new RenException(ErrorCode.PET_NOT_FOUND);
        }
        logicallyDelete(userId, pet);
    }

    private void logicallyDelete(Long userId, PetEntity pet) {
        // 仅标记宠物，关联记录保留；同毫秒重复删除撞唯一键时只重试一次。
        pet.setDeletedAt(System.currentTimeMillis());
        pet.setUpdater(userId);
        try {
            petDao.updateById(pet);
        } catch (DuplicateKeyException e) {
            pet.setDeletedAt(Math.max(System.currentTimeMillis(), pet.getDeletedAt() + 1));
            petDao.updateById(pet);
        }
    }

    private List<PetEntity> activeForUpdate(Long userId) {
        // 用户锁可能在事务已建立快照之后获得，必须当前读，才能看见等待期间提交的宠物。
        return petDao.selectList(active(userId).last("FOR UPDATE"));
    }

    private PetEntity insertEgg(Long userId, String prototype, String source) {
        Date now = new Date();
        PetEntity pet = new PetEntity();
        pet.setUserId(userId);
        pet.setPrototype(prototype);
        pet.setSource(source);
        pet.setHatchStatus("EGG");
        pet.setHatchStartTime(now);
        pet.setExpectedHatchTime(new Date(now.getTime() + HATCH_DURATION_MS));
        pet.setAcceleratedMinutes(0);
        pet.setCreator(userId);
        try {
            petDao.insert(pet);
        } catch (DuplicateKeyException e) {
            // 唯一索引继续兜底非本 module 写入或预检查后的竞态。
            throw new RenException(ErrorCode.PET_ALREADY_EXISTS, e);
        }
        return pet;
    }

    private QueryWrapper<PetEntity> active(Long userId) {
        return new QueryWrapper<PetEntity>().eq("user_id", userId).eq("deleted_at", 0);
    }

    private void validate(Long userId, String prototype) {
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        if (!"锦鲤".equals(prototype) && !"玉兔".equals(prototype)) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_PROTOTYPE);
        }
    }

    private void lockUser(Long userId) {
        // 同原型唯一键不足以约束跨原型的渠道互斥，按 sys_user.id 串行化领养写入。
        petDao.lockAdoptionUser(userId);
    }
}
