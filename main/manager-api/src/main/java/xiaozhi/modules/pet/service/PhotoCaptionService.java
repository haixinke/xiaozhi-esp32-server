package xiaozhi.modules.pet.service;

/**
 * 写真文案生成服务。
 *
 * <p>文案双重用途：渲染进 Seedream 图片内 + 用作分享卡片标题。
 * 优先 LLM 动态生成，失败回退预置文案池，保证体验无损。
 */
public interface PhotoCaptionService {

    /**
     * 为指定宠物原型生成一条写真文案（佛系、幽默、阳光，不超过15字）。
     *
     * @param prototype 宠物原型（锦鲤/玉兔），未知原型按玉兔兜底
     * @return 文案快照，永不为 null
     */
    String drawCaption(String prototype);
}
