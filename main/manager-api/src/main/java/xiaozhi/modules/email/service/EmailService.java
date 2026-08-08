package xiaozhi.modules.email.service;

import xiaozhi.modules.email.dto.EmailSendDTO;

/**
 * 邮件发送服务
 */
public interface EmailService {

    /**
     * 发送邮件
     *
     * @param dto 邮件发送请求
     */
    void sendEmail(EmailSendDTO dto);
}
