package xiaozhi.modules.email.service.impl;

import com.aliyun.dm20151123.Client;
import com.aliyun.dm20151123.models.SingleSendMailAdvanceRequest;
import com.aliyun.dm20151123.models.SingleSendMailRequest;
import com.aliyun.dm20151123.models.SingleSendMailResponse;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.email.dto.EmailSendDTO;
import xiaozhi.modules.email.service.EmailService;
import xiaozhi.modules.sys.service.SysParamsService;

/**
 * 阿里云邮件推送（DirectMail）实现
 */
@Slf4j
@Service
@AllArgsConstructor
public class AliyunDirectMailService implements EmailService {

    private final SysParamsService sysParamsService;

    @Override
    public void sendEmail(EmailSendDTO dto) {
        Client client = createClient();
        try {
            RuntimeOptions runtime = new RuntimeOptions();
            SingleSendMailResponse response;
            if (StringUtils.isNotBlank(dto.getAttachmentName()) && dto.getAttachmentStream() != null) {
                response = sendWithAttachment(client, dto, runtime);
            } else {
                response = sendPlain(client, dto, runtime);
            }
            log.info("邮件发送成功，requestId={}, to={}", response.getBody().getRequestId(), dto.getToAddress());
        } catch (Exception e) {
            log.error("邮件发送失败，to={}", dto.getToAddress(), e);
            throw new RenException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    private SingleSendMailResponse sendPlain(Client client, EmailSendDTO dto, RuntimeOptions runtime) throws Exception {
        SingleSendMailRequest request = new SingleSendMailRequest()
                .setAccountName(getParam(Constant.SysEmailParam.ALIYUN_DM_ACCOUNT_NAME))
                .setAddressType(1)
                .setReplyToAddress(Boolean.parseBoolean(getParam(Constant.SysEmailParam.ALIYUN_DM_REPLY_TO_ADDRESS)))
                .setToAddress(dto.getToAddress())
                .setSubject(dto.getSubject())
                .setHtmlBody(dto.getHtmlBody())
                .setFromAlias(getParam(Constant.SysEmailParam.ALIYUN_DM_FROM_ALIAS))
                .setTagName(getParam(Constant.SysEmailParam.ALIYUN_DM_TAG_NAME));
        return client.singleSendMailWithOptions(request, runtime);
    }

    private SingleSendMailResponse sendWithAttachment(Client client, EmailSendDTO dto, RuntimeOptions runtime)
            throws Exception {
        SingleSendMailAdvanceRequest.SingleSendMailAdvanceRequestAttachments attachment = new SingleSendMailAdvanceRequest.SingleSendMailAdvanceRequestAttachments();
        attachment.setAttachmentName(dto.getAttachmentName());
        attachment.setAttachmentUrlObject(dto.getAttachmentStream());

        SingleSendMailAdvanceRequest request = new SingleSendMailAdvanceRequest()
                .setAccountName(getParam(Constant.SysEmailParam.ALIYUN_DM_ACCOUNT_NAME))
                .setAddressType(1)
                .setReplyToAddress(Boolean.parseBoolean(getParam(Constant.SysEmailParam.ALIYUN_DM_REPLY_TO_ADDRESS)))
                .setToAddress(dto.getToAddress())
                .setSubject(dto.getSubject())
                .setHtmlBody(dto.getHtmlBody())
                .setFromAlias(getParam(Constant.SysEmailParam.ALIYUN_DM_FROM_ALIAS))
                .setTagName(getParam(Constant.SysEmailParam.ALIYUN_DM_TAG_NAME))
                .setAttachments(java.util.List.of(attachment));
        return client.singleSendMailAdvance(request, runtime);
    }

    /**
     * 创建阿里云 DM 连接
     */
    private Client createClient() {
        String accessKeyId = getParam(Constant.SysEmailParam.ALIYUN_DM_ACCESS_KEY_ID);
        String accessKeySecret = getParam(Constant.SysEmailParam.ALIYUN_DM_ACCESS_KEY_SECRET);
        try {
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret);
            config.endpoint = "dm.aliyuncs.com";
            config.regionId = "cn-hangzhou";
            return new Client(config);
        } catch (Exception e) {
            log.error("建立邮件连接失败", e);
            throw new RenException(ErrorCode.EMAIL_CONNECTION_FAILED);
        }
    }

    private String getParam(Constant.SysEmailParam param) {
        return sysParamsService.getValue(param.getValue(), true);
    }
}
