package xiaozhi.modules.pdc.nfc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcBatchStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcWriteJobStatus;
import xiaozhi.modules.pdc.nfc.crypto.ClaimRefProtection;
import xiaozhi.modules.pdc.nfc.crypto.EncryptedField;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcOperationLogDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobItemDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcBatchEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobItemEntity;
import xiaozhi.modules.pdc.nfc.service.impl.PdcNfcWriteJobServiceImpl;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcWriteFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdcNfcWriteJobServiceTest {

    private static final String SCHEME = "weixin://dl/business/?t=immutable";
    private static final String SCHEME_SHA256 =
            "83188ae12215f06c177e41087886801c908bb1ae940eec85da008ac95f76551a";

    @Mock private PdcNfcWriteJobDao jobDao;
    @Mock private PdcNfcWriteJobItemDao jobItemDao;
    @Mock private PdcNfcAssetDao assetDao;
    @Mock private PdcNfcBatchDao batchDao;
    @Mock private PdcNfcOperationLogDao operationLogDao;
    @Mock private PdcNfcReadinessService readiness;
    @Mock private ClaimRefProtection claimRefProtection;

    private PdcNfcWriteJobServiceImpl service;

    @BeforeAll
    static void initializeMyBatisTableMetadata() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "pdc-nfc-write-job-test");
        TableInfoHelper.initTableInfo(assistant, PdcNfcWriteJobEntity.class);
        TableInfoHelper.initTableInfo(assistant, PdcNfcAssetEntity.class);
        TableInfoHelper.initTableInfo(assistant, PdcNfcWriteJobItemEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = new PdcNfcWriteJobServiceImpl(
                jobDao,
                jobItemDao,
                assetDao,
                batchDao,
                operationLogDao,
                readiness,
                new PdcNfcBatchStateMachine(),
                new PdcNfcWriteJobStateMachine(),
                new PdcNfcWriteCsvExporter(),
                claimRefProtection);
    }

    @Test
    void createSnapshotsAssetUriDigestWithoutDecryptingPlaintext() {
        PdcNfcBatchEntity batch = batch();
        PdcNfcAssetEntity asset = encryptedAsset();
        when(batchDao.selectById(10L)).thenReturn(batch);
        when(jobDao.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(assetDao.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(asset));
        when(jobDao.insert(any(PdcNfcWriteJobEntity.class))).thenAnswer(invocation -> {
            PdcNfcWriteJobEntity job = invocation.getArgument(0);
            job.setId(100L);
            return 1;
        });
        service.create(10L, 99L);

        ArgumentCaptor<PdcNfcWriteJobItemEntity> savedItemCaptor =
                ArgumentCaptor.forClass(PdcNfcWriteJobItemEntity.class);
        verify(jobItemDao).insert(savedItemCaptor.capture());
        PdcNfcWriteJobItemEntity savedItem = savedItemCaptor.getValue();
        assertThat(savedItem.getUriSha256()).isEqualTo(asset.getSchemeSha256());
        verify(claimRefProtection, never()).decrypt(any(), any(EncryptedField.class));
    }

    @Test
    void repeatedDownloadsDecryptImmutableAssetCiphertextIntoIdenticalCsv() {
        PdcNfcWriteJobEntity job = exportedJob();
        PdcNfcBatchEntity batch = batch();
        PdcNfcWriteJobItemEntity item = writeJobItem();
        PdcNfcAssetEntity asset = encryptedAsset();
        when(jobDao.selectById(100L)).thenReturn(job);
        when(batchDao.selectById(10L)).thenReturn(batch);
        when(jobItemDao.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));
        lenient().when(assetDao.selectById(501L)).thenReturn(asset);
        lenient().when(claimRefProtection.decrypt(any(), any(EncryptedField.class)))
                .thenReturn(SCHEME);

        PdcNfcWriteFile first = service.export(100L, 99L);
        String firstSha256 = PdcNfcWriteCsvExporter.sha256Hex(first.bytes());
        PdcNfcWriteFile second = service.export(100L, 99L);

        assertThat(new String(first.bytes(), StandardCharsets.UTF_8)).contains(SCHEME);
        assertThat(second.bytes()).isEqualTo(first.bytes());
        assertThat(PdcNfcWriteCsvExporter.sha256Hex(second.bytes())).isEqualTo(firstSha256);
        verify(claimRefProtection, org.mockito.Mockito.times(2))
                .decrypt(any(), any(EncryptedField.class));
    }

    private static PdcNfcBatchEntity batch() {
        PdcNfcBatchEntity batch = new PdcNfcBatchEntity();
        batch.setId(10L);
        batch.setBatchNo("B001");
        batch.setStatus(PdcNfcBatchStatus.READY_FOR_WRITE.name());
        return batch;
    }

    private static PdcNfcAssetEntity encryptedAsset() {
        PdcNfcAssetEntity asset = new PdcNfcAssetEntity();
        asset.setId(501L);
        asset.setBatchId(10L);
        asset.setItemNo("000001");
        asset.setAssetNo("A001");
        asset.setWechatSn("SN001");
        asset.setSkuCode("KOI");
        asset.setPrototype("锦鲤");
        asset.setStatus(PdcNfcAssetStatus.SCHEME_GENERATED.name());
        asset.setSchemeKeyVersion("v1");
        asset.setSchemeNonce(new byte[12]);
        asset.setSchemeCiphertext(new byte[]{1, 2, 3});
        asset.setSchemeSha256(SCHEME_SHA256);
        return asset;
    }

    private static PdcNfcWriteJobEntity exportedJob() {
        PdcNfcWriteJobEntity job = new PdcNfcWriteJobEntity();
        job.setId(100L);
        job.setJobNo("WRT-100");
        job.setBatchId(10L);
        job.setStatus(PdcNfcWriteJobStatus.EXPORTED.name());
        return job;
    }

    private static PdcNfcWriteJobItemEntity writeJobItem() {
        PdcNfcWriteJobItemEntity item = new PdcNfcWriteJobItemEntity();
        item.setJobId(100L);
        item.setAssetId(501L);
        item.setSequenceNo(1);
        item.setAssetNo("A001");
        item.setBatchNo("B001");
        item.setWechatSn("SN001");
        item.setSkuCode("KOI");
        item.setPrototype("锦鲤");
        item.setUriSha256(SCHEME_SHA256);
        return item;
    }
}
