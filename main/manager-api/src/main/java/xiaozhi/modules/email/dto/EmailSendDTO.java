package xiaozhi.modules.email.dto;

import java.io.InputStream;

import lombok.Data;

/**
 * 邮件发送请求 DTO
 */
@Data
public class EmailSendDTO {

    /**
     * 收件人邮箱地址
     */
    private String toAddress;

    /**
     * 邮件主题
     */
    private String subject;

    /**
     * 邮件 HTML 正文
     */
    private String htmlBody;

    /**
     * 附件名称（可为空，为空则不携带附件）
     */
    private String attachmentName;

    /**
     * 附件内容输入流（与 attachmentName 配合使用）
     */
    private InputStream attachmentStream;
}
