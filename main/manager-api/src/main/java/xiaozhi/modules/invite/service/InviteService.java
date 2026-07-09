package xiaozhi.modules.invite.service;

import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.common.service.BaseService;
import xiaozhi.modules.invite.dto.InviteCodeCreateDTO;
import xiaozhi.modules.invite.dto.InviteCodeUpdateDTO;
import xiaozhi.modules.invite.entity.InviteCodeEntity;
import xiaozhi.modules.invite.vo.InviteCodeVO;
import xiaozhi.modules.invite.vo.InviteConsumeVO;
import xiaozhi.modules.invite.vo.InviteStatsVO;
import xiaozhi.modules.invite.vo.InviteUsageVO;

public interface InviteService extends BaseService<InviteCodeEntity> {

    InviteCodeVO createPersonalCode(Long userId);

    InviteCodeVO getMine(Long userId);

    InviteConsumeVO consume(String code, Long inviteeUserId);

    InviteCodeVO createEnterprise(InviteCodeCreateDTO dto);

    void update(InviteCodeUpdateDTO dto);

    void delete(Long id);

    PageData<InviteCodeVO> page(Map<String, Object> params);

    PageData<InviteUsageVO> usageList(Long codeId, Map<String, Object> params);

    InviteStatsVO stats();
}
