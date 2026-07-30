package xiaozhi.modules.pdc.nfc.dto;

/**
 * NFC 领取预览请求 DTO。
 * <p>
 * 用于预览领取状态（可领取 / 已领取 / 不可用），不产生副作用。
 *
 * @param userId   当前用户 ID
 * @param claimRef 领取引用（22 位 Base64URL 编码）
 */
public record PdcNfcClaimPreviewDTO(Long userId, String claimRef) {
}
