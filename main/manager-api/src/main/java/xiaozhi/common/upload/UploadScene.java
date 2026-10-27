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
    DOODLE("doodle", Set.of("image/png", "image/jpeg", "image/webp"), 5 * 1024 * 1024L),

    /**
     * AI生图用户照片。存放到 ai-gen/{userId}/ 路径下，与涂鸦作品隔离。
     */
    AI_GEN("ai-gen", Set.of("image/png", "image/jpeg", "image/webp"), 5 * 1024 * 1024L),

    /**
     * 游戏管理 icon。存放到 game-icon/{userId}/ 路径下，支持 svg 矢量图标。
     */
    GAME_ICON("game-icon", Set.of("image/png", "image/jpeg", "image/webp", "image/svg+xml"), 2 * 1024 * 1024L),

    /**
     * 小物件管理图片。存放到 small-object/{userId}/ 路径下，与游戏图标、涂鸦作品隔离。
     * 不支持 svg：小物件图片是内容展示图，且 ImageUploadService 的扩展名映射不含 svg，
     * 放开白名单会产出缺扩展名的 OSS key。
     */
    SMALL_OBJECT("small-object", Set.of("image/png", "image/jpeg", "image/webp"), 2 * 1024 * 1024L);

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
     * 按 pathPrefix 匹配而非枚举名——pathPrefix 含连字符（game-icon）时
     * toUpperCase 无法映射到下划线枚举名（GAME_ICON）。
     * 匹配不到时返回 null，由调用方决定如何报错。
     *
     * @param code 场景码，允许任意大小写
     * @return 匹配的枚举值；为空或不匹配时返回 null
     */
    public static UploadScene fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim();
        for (UploadScene scene : values()) {
            if (scene.pathPrefix.equalsIgnoreCase(normalized)) {
                return scene;
            }
        }
        return null;
    }
}
