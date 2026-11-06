package xiaozhi.modules.wechat.service;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.modules.wechat.dao.WechatUserDao;
import xiaozhi.modules.wechat.entity.WechatUserEntity;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;

/** 微信账号手机号约束：领取孵化可体验，破壳后才要求绑定。 */
@Service
public class WechatPhoneGate {

    private final WechatUserDao wechatUserDao;
    private final PetDao petDao;

    public WechatPhoneGate(WechatUserDao wechatUserDao, PetDao petDao) {
        this.wechatUserDao = wechatUserDao;
        this.petDao = petDao;
    }

    /** 从持久状态判定整个账号的门槛，不信任客户端当前宠物或绑定标志。 */
    public boolean requiresPhoneBinding(Long userId) {
        if (userId == null) {
            return false;
        }
        List<WechatUserEntity> mappings = wechatUserDao.selectList(
                new QueryWrapper<WechatUserEntity>().select("phone").eq("user_id", userId));
        // 普通智控台账号没有微信映射；已绑定任一映射的账号也不重复要求绑定。
        if (mappings == null || mappings.isEmpty() || mappings.stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(mapping -> StringUtils.isNotBlank(mapping.getPhone()))) {
            return false;
        }
        return petDao.selectCount(new QueryWrapper<PetEntity>()
                .eq("user_id", userId).eq("deleted_at", 0).eq("hatch_status", "HATCHED")) > 0;
    }

    /** 匿名 OTA 和服务密钥配置按真实设备类型与归属校验，不能信任上报的 board。 */
    public void requireDeviceAccess(DeviceEntity device) {
        // 与登录后的账号门槛保持同一适用范围，普通账号及无活跃破壳宠物的账号保持原行为。
        if (device != null && "wechat-egg-miniprogram".equals(device.getBoard())
                && requiresPhoneBinding(device.getUserId())) {
            throw new RenException(ErrorCode.WECHAT_PHONE_REQUIRED);
        }
    }

    public boolean hasBoundWechatPhone(Long userId) {
        if (userId == null) {
            return false;
        }

        List<WechatUserEntity> mappings = wechatUserDao.selectList(
                new QueryWrapper<WechatUserEntity>()
                        .select("phone")
                        .eq("user_id", userId));

        return mappings != null
                && !mappings.isEmpty()
                && mappings.stream()
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(mapping -> StringUtils.isNotBlank(mapping.getPhone()));
    }
}
