package xiaozhi.common.upload;

import java.util.Set;

/**
 * 通用图片上传场景枚举。
 *
 * <p>白名单控制每个场景的 OSS key 路径前缀、允许的图片类型与大小上限，
 * 避免调用方传入任意路径或绕过校验。新增上传业务时在此追加枚举值即可复用
 * {@link ImageUploadService}，无需各自实现校验与 key 生成逻辑。
 */
public enum UploadScene {

    /**
     * 破壳前蛋壳涂鸦作品。存放到 doodle/{userId}/ 路径下，与用户头像隔离。
     */
    DOODLE("doodle", Set.of("image/png", "image/jpeg", "image/webp"), 5 * 1024 * 1024L);

    /**
     * OSS key 路径前缀，最终 key 形如 {pathPrefix}/{userId}/{uuid}.{ext}。
     */
    private final String pathPrefix;

    /**
     * 允许上传的 content-type 白名单。
     */
    private final Set<String> allowedTypes;

    /**
     * 单文件大小上限（字节）。
     */
    private final long maxSize;

    UploadScene(String pathPrefix, Set<String> allowedTypes, long maxSize) {
        this.pathPrefix = pathPrefix;
        this.allowedTypes = allowedTypes;
        this.maxSize = maxSize;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public Set<String> getAllowedTypes() {
        return allowedTypes;
    }

    public long getMaxSize() {
        return maxSize;
    }

    /**
     * 大小写不敏感地解析场景码。
     *
     * <p>对客户端友好：前端常按自然语义传小写（如 {@code doodle}），而枚举常量名为大写。
     * 匹配不到时返回 null，由调用方决定如何报错。
     *
     * @param code 场景码，允许任意大小写
     * @return 匹配的枚举值；为空或不匹配时返回 null
     */
    public static UploadScene fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return UploadScene.valueOf(code.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
