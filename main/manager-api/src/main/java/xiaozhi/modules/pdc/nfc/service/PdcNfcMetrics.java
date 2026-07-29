package xiaozhi.modules.pdc.nfc.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * PDC NFC 业务指标计数器。
 * <p>
 * 提供 scheme / write / inventory / claim / permission 五组操作的计数方法，
 * 所有指标名称以 {@code pdc.nfc.*} 为前缀，可通过 Spring Boot Actuator
 * {@code /actuator/metrics} 端点查询。
 */
@Component
public class PdcNfcMetrics {

    private final MeterRegistry registry;

    public PdcNfcMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // ── Scheme operations ──────────────────────────────────────────────────

    public void schemeRequest() {
        counter("pdc.nfc.scheme.requests");
    }

    public void schemeSuccess() {
        counter("pdc.nfc.scheme.successes");
    }

    public void schemeFailure(String errorCode) {
        counter("pdc.nfc.scheme.failures", "errorCode", errorCode);
    }

    public void schemeDeferred() {
        counter("pdc.nfc.scheme.deferred");
    }

    // ── Write operations ───────────────────────────────────────────────────

    public void writeImport() {
        counter("pdc.nfc.write.imports");
    }

    public void writeVerifyFailure() {
        counter("pdc.nfc.write.verify_failures");
    }

    public void writeScrapped() {
        counter("pdc.nfc.write.scrapped");
    }

    // ── Inventory ──────────────────────────────────────────────────────────

    public void inventoryStocked() {
        counter("pdc.nfc.inventory.stocked");
    }

    public void inventoryActivated() {
        counter("pdc.nfc.inventory.activated");
    }

    public void inventoryDisabled() {
        counter("pdc.nfc.inventory.disabled");
    }

    // ── Claim ──────────────────────────────────────────────────────────────

    public void claimPreview() {
        counter("pdc.nfc.claim.preview");
    }

    public void claimSuccess() {
        counter("pdc.nfc.claim.success");
    }

    public void claimConflict() {
        counter("pdc.nfc.claim.conflict");
    }

    public void claimInvalidRef() {
        counter("pdc.nfc.claim.invalid_ref");
    }

    public void claimMultiUserAlert() {
        counter("pdc.nfc.claim.multi_user_alert");
    }

    // ── Permission ─────────────────────────────────────────────────────────

    public void permissionDenied() {
        counter("pdc.nfc.permission.denied");
    }

    // ── Internal ───────────────────────────────────────────────────────────

    private void counter(String name, String... tags) {
        registry.counter(name, tags).increment();
    }
}
