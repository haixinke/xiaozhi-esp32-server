package xiaozhi.modules.wechat.dao;

import org.apache.ibatis.annotations.Mapper;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.wechat.entity.WechatUserEntity;

/**
 * 微信小程序用户绑定 Dao
 */
@Mapper
public interface WechatUserDao extends BaseDao<WechatUserEntity> {
}
