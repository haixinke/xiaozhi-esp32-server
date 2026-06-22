package xiaozhi.common.oss;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.OSSObject;

import cn.hutool.core.collection.ListUtil;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.config.AliyunOssProperties;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.validator.AssertUtils;

/**
 * 阿里云OSS操作封装Service
 */
@Slf4j
@Service
public class OssService {

    private final OSS ossClient;
    private final AliyunOssProperties ossProperties;

    public OssService(@Autowired(required = false) OSS ossClient,
            AliyunOssProperties ossProperties) {
        this.ossClient = ossClient;
        this.ossProperties = ossProperties;
    }

    /**
     * 判断OSS是否可用
     */
    public boolean isEnabled() {
        return ossClient != null && ossProperties.isConfigured();
    }

    /**
     * 上传字节数组到OSS
     *
     * @param ossKey 对象键
     * @param data   字节数据
     * @return ossKey
     */
    public String upload(String ossKey, byte[] data) {
        AssertUtils.isBlank(ossKey, ErrorCode.OSS_DELETE_FILE_ERROR, "ossKey");
        AssertUtils.isNull(data, ErrorCode.OSS_DELETE_FILE_ERROR, "data");
        ossClient.putObject(ossProperties.getBucketName(), ossKey,
                new ByteArrayInputStream(data));
        log.debug("OSS上传成功, ossKey={}, size={}", ossKey, data.length);
        return ossKey;
    }

    /**
     * 从OSS下载为字节数组
     *
     * @param ossKey 对象键
     * @return 字节数据
     */
    public byte[] download(String ossKey) {
        AssertUtils.isBlank(ossKey, ErrorCode.OSS_DOWNLOAD_FILE_ERROR, "ossKey");
        OSSObject ossObject = ossClient.getObject(ossProperties.getBucketName(), ossKey);
        try (InputStream is = ossObject.getObjectContent()) {
            return is.readAllBytes();
        } catch (IOException e) {
            log.error("OSS下载失败, ossKey={}", ossKey, e);
            throw new RenException(ErrorCode.OSS_DOWNLOAD_FILE_ERROR);
        }
    }

    /**
     * 删除单个OSS对象
     *
     * @param ossKey 对象键
     */
    public void delete(String ossKey) {
        AssertUtils.isBlank(ossKey, ErrorCode.OSS_DELETE_FILE_ERROR, "ossKey");
        ossClient.deleteObject(ossProperties.getBucketName(), ossKey);
        log.debug("OSS删除成功, ossKey={}", ossKey);
    }

    /**
     * 批量删除OSS对象
     *
     * @param ossKeys 对象键列表
     */
    public void deleteBatch(List<String> ossKeys) {
        if (ossKeys == null || ossKeys.isEmpty()) {
            return;
        }
        // OSS批量删除每次最多1000个
        List<List<String>> batches = ListUtil.split(ossKeys, 1000);
        for (List<String> batch : batches) {
            DeleteObjectsRequest request = new DeleteObjectsRequest(ossProperties.getBucketName());
            request.setKeys(batch);
            request.setQuiet(true);
            ossClient.deleteObjects(request);
        }
        log.debug("OSS批量删除成功, count={}", ossKeys.size());
    }

    /**
     * 构造音频OSS对象键
     *
     * @param audioId 音频ID
     * @return OSS对象键
     */
    public static String buildAudioOssKey(String audioId) {
        return "chat-audio/" + audioId + ".wav";
    }
}
