package xiaozhi.common.upload;

import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.aliyun.oss.model.CannedAccessControlList;

import cn.hutool.core.util.IdUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.oss.OssService;

/**
 * 通用图片上传服务。
 *
 * <p>按 {@link UploadScene} 白名单完成类型/大小校验、OSS key 生成与上传，
 * 返回公网可访问 URL。供 {@link ImageUploadController} 及后续其他上传入口复用，
 * 避免各业务各自重复实现校验与 key 生成逻辑。
 */
@Slf4j
@Service
@AllArgsConstructor
public class ImageUploadService {

    private final OssService ossService;

    /**
     * 上传图片到 OSS。
     *
     * @param userId 当前用户 ID，用于按用户隔离 key 路径；为空视为未登录
     * @param file   multipart 文件
     * @param scene  上传场景，决定 key 前缀与校验规则
     * @return OSS 公网可访问 URL
     */
    public String uploadImage(Long userId, MultipartFile file, UploadScene scene) {
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        if (file == null || file.isEmpty()) {
            throw new RenException(ErrorCode.UPLOAD_FILE_EMPTY);
        }
        String contentType = file.getContentType();
        if (contentType == null || !scene.getAllowedTypes().contains(contentType)) {
            throw new RenException(ErrorCode.IMAGE_FILE_TYPE_ERROR);
        }
        if (file.getSize() > scene.getMaxSize()) {
            throw new RenException(ErrorCode.FILE_SIZE_OVER_LIMIT);
        }
        if (!ossService.isEnabled()) {
            throw new RenException(ErrorCode.OSS_UPLOAD_FILE_ERROR);
        }

        String ext = extensionOf(contentType);
        String uuid = IdUtil.fastSimpleUUID();
        // key 形如 {scene}/{userId}/{uuid}.{ext}，按场景与用户双重隔离
        String ossKey = scene.getPathPrefix() + "/" + userId + "/" + uuid + "." + ext;
        try {
            ossService.upload(ossKey, file.getBytes(), CannedAccessControlList.PublicRead);
        } catch (IOException e) {
            // file.getBytes() 读取本地临时文件失败，按上传失败处理
            log.error("图片上传读取失败 userId={}, ossKey={}", userId, ossKey, e);
            throw new RenException(ErrorCode.OSS_UPLOAD_FILE_ERROR);
        } catch (Exception e) {
            log.error("图片上传OSS失败 userId={}, ossKey={}", userId, ossKey, e);
            throw new RenException(ErrorCode.OSS_UPLOAD_FILE_ERROR);
        }
        return ossService.buildPublicUrl(ossKey);
    }

    /**
     * content-type 到文件扩展名映射，仅覆盖白名单内类型。
     */
    private static String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> throw new RenException(ErrorCode.IMAGE_FILE_TYPE_ERROR);
        };
    }
}
