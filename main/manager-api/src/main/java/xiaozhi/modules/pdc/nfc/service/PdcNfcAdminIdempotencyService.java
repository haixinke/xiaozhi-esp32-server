package xiaozhi.modules.pdc.nfc.service;

import xiaozhi.modules.pdc.nfc.constant.PdcNfcAdminOperationType;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 管理后台幂等服务：基于 (operationType, requestId) + 请求指纹 实现幂等。
 * <p>
 * 相同 requestId + 相同指纹 → 返回缓存响应；
 * 相同 requestId + 不同指纹 → 抛出 IDEMPOTENCY_CONFLICT；
 * 并发冲突 → 重试查找。
 */
public interface PdcNfcAdminIdempotencyService {

    /**
     * 幂等执行模板。
     *
     * @param operationType  操作类型
     * @param requestId      幂等请求 ID
     * @param canonicalRequest 用于计算指纹的规范化请求字符串
     * @param responseType   响应类型
     * @param action         实际业务逻辑
     * @return 响应对象
     */
    <T> T execute(
            PdcNfcAdminOperationType operationType,
            UUID requestId,
            String canonicalRequest,
            Class<T> responseType,
            Supplier<T> action
    );
}
