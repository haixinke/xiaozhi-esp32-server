package xiaozhi.modules.agent.service;

/**
 * 聊天记录导出服务
 */
public interface ChatHistoryExportService {

    /**
     * 异步导出用户全部聊天记录并通过邮件发送
     * <p>
     * 立即返回，导出与发送在后台线程执行，失败仅记录日志。
     *
     * @param userId    用户ID
     * @param toAddress 收件邮箱
     */
    void exportAndEmailAsync(Long userId, String toAddress);
}
