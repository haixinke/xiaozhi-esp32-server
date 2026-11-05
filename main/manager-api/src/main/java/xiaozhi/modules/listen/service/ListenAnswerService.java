package xiaozhi.modules.listen.service;

import xiaozhi.modules.listen.dto.ListenAnswerSubmitDTO;
import xiaozhi.modules.listen.vo.ListenDrawVO;
import xiaozhi.modules.listen.vo.ListenSubmitResultVO;

/**
 * 听你说·小程序答题 Service。
 * 面向小程序用户：随机抽未答过的题目分组、整组提交答案。
 */
public interface ListenAnswerService {

    /**
     * 随机抽取一个该用户未答过的题目分组，返回组内全部题目。
     * 「未答过」按组判定：用户在 ai_listen_answer 中已有该组任何一题的答案，整组即视为已答过。
     * 所有组都答过时返回 null（由调用方映射为业务码 LISTEN_ALL_ANSWERED）。
     *
     * @param userId 当前用户 ID
     * @return 抽题结果；无候选组时为 null
     */
    ListenDrawVO draw(Long userId);

    /**
     * 整组提交答案。已答组重提为整组空操作，不补写答案，也不重复占用每日次数。
     * 仅校验 customText 长度（≤200 字）；不校验文本必须对应 E 选项（信任前端只在选 E 时传文本）。
     * 提交成功后按游戏管理配置的 earnStars 结算当日首次完成奖励，结果随响应返回。
     *
     * @param userId 当前用户 ID
     * @param dto    组 ID + 组内各题答案
     * @return 星星结算结果（本次新增星星数 + 奖励语义）
     */
    ListenSubmitResultVO submitAnswers(Long userId, ListenAnswerSubmitDTO dto);
}
