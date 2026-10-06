package xiaozhi.modules.pet.service;

import xiaozhi.modules.pet.entity.PetPostcardEntity;
import xiaozhi.modules.pet.vo.PetPostcardPublicVO;

/**
 * 宠物明信片服务。
 *
 * <p>明信片由宠物侧生成链路落库（照片须已过内容审核），
 * 本服务负责用户收件箱分页查询与对外公开分享查询。
 */
public interface PetPostcardService {

    /**
     * 保存明信片（宠物侧生成链路调用）。图片必须已通过内容审核，
     * 否则公开分享会把未过审内容透出给免授权访客。
     *
     * @param postcard 明信片实体（shareId 为空时自动生成）
     * @return 落库后的实体（含 id 与生成字段）
     */
    PetPostcardEntity createPostcard(PetPostcardEntity postcard);

    /**
     * 公开分享查询：好友免授权访问。
     *
     * <p>仅返回展示字段（宠物昵称/原型/图/文字/时间），不暴露 user_id/pet_id。
     * 按 share_id 查询，不存在与已删除统一抛 POSTCARD_NOT_FOUND，避免探测。
     *
     * @param shareId 对外分享随机串
     * @return 公开视图
     */
    PetPostcardPublicVO getPublicByShareId(String shareId);
}
