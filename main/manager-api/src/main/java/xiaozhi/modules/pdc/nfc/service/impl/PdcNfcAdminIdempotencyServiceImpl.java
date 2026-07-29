package xiaozhi.modules.pdc.nfc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAdminOperationType;
import xiaozhi.modules.pdc.nfc.crypto.RequestFingerprint;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAdminRequestDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAdminRequestEntity;
import xiaozhi.modules.pdc.nfc.service.PdcNfcAdminIdempotencyService;

import java.util.Date;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 管理后台幂等服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdcNfcAdminIdempotencyServiceImpl implements PdcNfcAdminIdempotencyService {

    private final PdcNfcAdminRequestDao adminRequestDao;
    private final RequestFingerprint requestFingerprint;
    private final ObjectMapper objectMapper;

    @Override
    public <T> T execute(
            PdcNfcAdminOperationType operationType,
            UUID requestId,
            String canonicalRequest,
            Class<T> responseType,
            Supplier<T> action
    ) {
        String fingerprint = requestFingerprint.sha256Canonical(canonicalRequest);
        String opType = operationType.name();
        String reqIdStr = requestId.toString();

        // Step 1: 查找已有记录
        PdcNfcAdminRequestEntity existing = findByOperationAndRequestId(opType, reqIdStr);
        if (existing != null) {
            return handleExisting(existing, fingerprint, responseType);
        }

        // Step 2: 执行业务逻辑
        T result = action.get();

        // Step 3: 存储幂等记录
        try {
            String responseJson = objectMapper.writeValueAsString(result);
            PdcNfcAdminRequestEntity entity = new PdcNfcAdminRequestEntity();
            entity.setOperationType(opType);
            entity.setRequestId(reqIdStr);
            entity.setRequestFingerprint(fingerprint);
            entity.setResponseJson(responseJson);
            entity.setStatus("SUCCESS");
            entity.setCreateDate(new Date());
            adminRequestDao.insert(entity);
        } catch (DuplicateKeyException e) {
            // 并发冲突：另一线程先插入了，查找并返回
            log.warn("Idempotency concurrent insert for opType={}, requestId={}", opType, reqIdStr);
            existing = findByOperationAndRequestId(opType, reqIdStr);
            if (existing != null) {
                return handleExisting(existing, fingerprint, responseType);
            }
            // 极端情况：插入后又被删除（不应发生）
            throw new IllegalStateException("Idempotency record disappeared", e);
        } catch (Exception e) {
            log.warn("Failed to store idempotency record: {}", e.getMessage());
            // 业务已成功，幂等记录存储失败不影响结果
        }

        return result;
    }

    private PdcNfcAdminRequestEntity findByOperationAndRequestId(String opType, String reqIdStr) {
        return adminRequestDao.selectOne(
                new LambdaQueryWrapper<PdcNfcAdminRequestEntity>()
                        .eq(PdcNfcAdminRequestEntity::getOperationType, opType)
                        .eq(PdcNfcAdminRequestEntity::getRequestId, reqIdStr));
    }

    private <T> T handleExisting(PdcNfcAdminRequestEntity existing, String fingerprint,
                                  Class<T> responseType) {
        if (!fingerprint.equals(existing.getRequestFingerprint())) {
            throw new RenException(ErrorCode.PDC_NFC_IDEMPOTENCY_CONFLICT);
        }
        // 相同指纹 → 返回缓存响应
        try {
            return objectMapper.readValue(existing.getResponseJson(), responseType);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize cached response", e);
        }
    }
}
