package xiaozhi.modules.sys.dto;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

/**
 * 操作日志展示对象
 */
@Data
public class SysOperationLogDTO implements Serializable {

    private Long id;
    private Long userId;
    private String username;
    private String operationType;
    private String operationDesc;
    private String requestUri;
    private String requestMethod;
    private String ip;
    private String detail;
    private Integer status;
    private String errorMsg;
    private Date createDate;
}
