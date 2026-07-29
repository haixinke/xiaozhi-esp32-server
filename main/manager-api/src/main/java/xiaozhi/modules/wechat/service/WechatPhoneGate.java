package xiaozhi.modules.wechat.service;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.modules.wechat.dao.WechatUserDao;
import xiaozhi.modules.wechat.entity.WechatUserEntity;

@Service
public class WechatPhoneGate {

    private final WechatUserDao wechatUserDao;

    public WechatPhoneGate(WechatUserDao wechatUserDao) {
        this.wechatUserDao = wechatUserDao;
    }

    public boolean canAccess(Long userId) {
        if (userId == null) {
            return false;
        }

        List<WechatUserEntity> mappings = wechatUserDao.selectList(
                new QueryWrapper<WechatUserEntity>()
                        .select("phone")
                        .eq("user_id", userId));

        return mappings == null
                || mappings.isEmpty()
                || mappings.stream()
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(mapping -> StringUtils.isNotBlank(mapping.getPhone()));
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
