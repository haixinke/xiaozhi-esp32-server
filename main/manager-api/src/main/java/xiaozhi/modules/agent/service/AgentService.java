package xiaozhi.modules.agent.service;

import java.util.List;
import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.common.service.BaseService;
import xiaozhi.modules.agent.dto.AgentCreateDTO;
import xiaozhi.modules.agent.dto.AgentDTO;
import xiaozhi.modules.agent.dto.AgentUpdateDTO;
import xiaozhi.modules.agent.entity.AgentEntity;
import xiaozhi.modules.agent.vo.AgentInfoVO;

/**
 * 智能体表处理service
 *
 * @author Goody
 * @version 1.0, 2025/4/30
 * @since 1.0.0
 */
public interface AgentService extends BaseService<AgentEntity> {
    /**
     * 获取管理员智能体列表
     *
     * @param params 查询参数
     * @return 分页数据
     */
    PageData<AgentEntity> adminAgentList(Map<String, Object> params);

    /**
     * 根据ID获取智能体
     *
     * @param id 智能体ID
     * @return 智能体实体
     */
    AgentInfoVO getAgentById(String id);

    /**
     * 插入智能体
     *
     * @param entity 智能体实体
     * @return 是否成功
     */
    boolean insert(AgentEntity entity);

    /**
     * 根据用户ID删除智能体
     *
     * @param userId 用户ID
     */
    void deleteAgentByUserId(Long userId);

    /**
     * 获取用户智能体列表
     *
     * @param userId 用户ID
     * @param keyword 搜索关键词
     * @param searchType 搜索类型（name - 按名称搜索，mac - 按MAC地址搜索）
     * @return 智能体列表
     */
    List<AgentDTO> getUserAgents(Long userId, String keyword, String searchType);

    /**
     * 根据智能体ID获取设备数量
     *
     * @param agentId 智能体ID
     * @return 设备数量
     */
    Integer getDeviceCountByAgentId(String agentId);

    /**
     * 根据设备MAC地址查询对应设备的默认智能体信息
     *
     * @param macAddress 设备MAC地址
     * @return 默认智能体信息，不存在时返回null
     */
    AgentEntity getDefaultAgentByMacAddress(String macAddress);

    /**
     * 检查用户是否有权限访问智能体
     *
     * @param agentId 智能体ID
     * @param userId  用户ID
     * @return 是否有权限
     */
    boolean checkAgentPermission(String agentId, Long userId);

    /**
     * 更新智能体
     *
     * @param agentId 智能体ID
     * @param dto     更新智能体所需的信息
     */
    void updateAgentById(String agentId, AgentUpdateDTO dto);

    /**
     * 创建智能体
     *
     * @param dto 创建智能体所需的信息
     * @return 创建的智能体ID
     */
    String createAgent(AgentCreateDTO dto);

    /**
     * 按订阅档位应用 agent 配置：
     * silver → chat_history_conf=2；gold → chat_history_conf=2 + intent=function_call。
     * 通过 userId → 伴侣 → 设备 → agent 定位，单列更新，幂等。
     *
     * @param userId   用户ID
     * @param planCode 档位编码 bronze/silver/gold
     */
    void applySubscriptionConfig(Long userId, String planCode);

    /**
     * 将用户伴侣对应的 agent 重置为初始态：chat_history_conf=0 + intent=Intent_nointent。
     * 订阅到期且无后续生效订阅时调用。定位失败则安全跳过。
     *
     * @param userId 用户ID
     */
    void resetToInitial(Long userId);


}
