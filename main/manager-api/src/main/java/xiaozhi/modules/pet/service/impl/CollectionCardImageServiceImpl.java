package xiaozhi.modules.pet.service.impl;

import com.volcengine.ark.runtime.model.images.generation.GenerateImagesRequest;
import com.volcengine.ark.runtime.model.images.generation.ImagesResponse;
import com.volcengine.ark.runtime.model.images.generation.ResponseFormat;
import com.volcengine.ark.runtime.service.ArkService;
import com.aliyun.oss.model.CannedAccessControlList;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import xiaozhi.common.oss.OssService;
import xiaozhi.modules.pet.config.SeedreamProperties;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.service.CollectionCardImageService;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 基于火山引擎 Ark SDK 的破壳收藏卡图片生成实现
 */
@Slf4j
@Service
public class CollectionCardImageServiceImpl implements CollectionCardImageService {

    private static final DateTimeFormatter BIRTHDAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String PROMPT_TEMPLATE = """
            A cute IP character introduction card, 3D rendered in a fluffy felted wool texture style.

            **Layout & Composition:**
            - Vertical card format with a wavy, cloud-like scalloped border (like frosting on a cake), with soft dimensional thickness and gentle drop shadows.
            - Creamy off-white background with dreamy translucent soap bubbles floating around, soft bokeh light spots, and subtle wave patterns at the bottom.
            - Centered large 3D IP character image in the upper-middle area, showing the full body of a cute plush toy-like creature.

            **Top Section:**
            - Title in bold, rounded, bubbly Chinese characters: "我的电子宠物搭子✨" with a soft golden outline and slight 3D extrusion.
            - Subtitle below in smaller, elegant rounded font: "{nickname}".

            **Bottom Info Section:**
            - Grid of rounded pill-shaped tags (two columns), each with a soft pastel background color (light pink, pale blue, lavender, cream) and a subtle white border + soft shadow.
            - Each tag contains: a small emoji icon on the left + label text in warm dark brown.
            - Tags content: "👑 昵称: {name}" | "🐶 原型: {prototype}" | "🎂 生日: {birthday}" | "♈ 星座: {zodiac}".
            - Gender tag: "♂ 性别: {gender}" — the label text is "性别:", but the value is displayed as a gender symbol only: ♂ for male input, ♀ for female input. NO Chinese characters "男" or "女" in the value field.
            - "⚡ MBTI: {mbti}".
            - One full-width horizontal tag: "☁️ 性格: {personality}".
            - Bottom left: a single pill tag "🩸 血型: {bloodType}".
            - Bottom right corner: small call-to-action text "来领养你的专属{name}吧🥰".

            **Color Palette:**
            - Background: warm cream (#FFF8F0), soft gradients.
            - Tag backgrounds: alternating pastel macaron colors (blush pink, baby blue, pale lilac, soft peach).
            - Text: warm dark brown (#5C4033) for readability.
            - Accents: soft gold, copper tones for decorative elements.

            **Lighting & Texture:**
            - Soft diffused lighting from upper left, no harsh shadows.
            - Everything should feel like it's made of soft wool, felt, or plush material.
            - Dreamy, cozy, kawaii aesthetic with a premium toy-brand quality.

            **Style References:**
            - Jellycat plush toy aesthetic meets Korean stationery design.
            - Pixar-level 3D rendering but with a handmade craft feel.
            - Clean, airy, Instagram-worthy product card.

            High resolution, 8K, studio lighting, centered composition.""";

    private final SeedreamProperties seedreamProperties;
    private final ArkService arkService;
    private final RestTemplate restTemplate;
    private final OssService ossService;

    public CollectionCardImageServiceImpl(SeedreamProperties seedreamProperties,
                                          ArkService arkService,
                                          RestTemplate restTemplate,
                                          OssService ossService) {
        this.seedreamProperties = seedreamProperties;
        this.arkService = arkService;
        this.restTemplate = restTemplate;
        this.ossService = ossService;
    }

    @Override
    public String generate(PetEntity pet) {
        if (pet == null || StringUtils.isBlank(pet.getId())) {
            log.warn("宠物信息为空，无法生成收藏卡图片");
            return null;
        }
        if (!seedreamProperties.isConfigured()) {
            log.warn("Seedream 未配置，跳过收藏卡图片生成，petId={}", pet.getId());
            return null;
        }
        if (!ossService.isEnabled()) {
            log.warn("OSS 未启用，无法保存收藏卡图片，petId={}", pet.getId());
            return null;
        }

        try {
            String prompt = buildPrompt(pet);
            String generatedImageUrl = callSeedream(prompt);
            if (StringUtils.isBlank(generatedImageUrl)) {
                log.warn("Seedream 未返回图片，petId={}", pet.getId());
                return null;
            }
            log.info("Seedream 返回临时图片URL，petId={}, temporaryUrl={}", pet.getId(), generatedImageUrl);

            byte[] imageBytes = downloadGeneratedImage(generatedImageUrl);
            if (imageBytes == null || imageBytes.length == 0) {
                log.warn("Seedream 图片下载为空，petId={}", pet.getId());
                return null;
            }

            String ossKey = "eggbabe/cards/" + pet.getId() + ".png";
            ossService.upload(ossKey, imageBytes, CannedAccessControlList.PublicRead);

            String imageUrl = buildOssUrl(ossKey);
            log.info("收藏卡图片生成成功，petId={}, url={}", pet.getId(), imageUrl);
            return imageUrl;
        } catch (RestClientException e) {
            log.error("Seedream 图片下载失败，petId={}", pet.getId(), e);
            return null;
        } catch (Exception e) {
            log.error("生成收藏卡图片时发生异常，petId={}", pet.getId(), e);
            return null;
        }
    }

    private String buildPrompt(PetEntity pet) {
        String nickname = StringUtils.defaultString(pet.getNickname(), pet.getPrototype());
        String name = StringUtils.defaultString(nickname, "蛋宝宝");
        String prototype = StringUtils.defaultString(pet.getPrototype(), "玉兔");
        String birthday = formatBirthday(pet);
        String zodiac = StringUtils.defaultString(pet.getZodiac(), "");
        String gender = "MALE".equalsIgnoreCase(pet.getGender()) ? "♂" : "♀";
        String mbti = StringUtils.defaultString(pet.getMbti(), "");
        String personality = StringUtils.defaultString(pet.getPersonalityBrief(), "");
        String bloodType = StringUtils.defaultString(pet.getBloodType(), "");

        return PROMPT_TEMPLATE
                .replace("{nickname}", nickname)
                .replace("{name}", name)
                .replace("{prototype}", prototype)
                .replace("{birthday}", birthday)
                .replace("{zodiac}", zodiac)
                .replace("{gender}", gender)
                .replace("{mbti}", mbti)
                .replace("{personality}", personality)
                .replace("{bloodType}", bloodType);
    }

    private String formatBirthday(PetEntity pet) {
        LocalDateTime hatchTime = pet.getHatchedAt() != null
                ? LocalDateTime.ofInstant(pet.getHatchedAt().toInstant(), ZoneId.systemDefault())
                : LocalDateTime.now();
        return hatchTime.format(BIRTHDAY_FORMATTER);
    }

    private String callSeedream(String prompt) {
        GenerateImagesRequest request = GenerateImagesRequest.builder()
                .model(seedreamProperties.getModel())
                .prompt(prompt)
                .responseFormat(ResponseFormat.Url)
                .size(seedreamProperties.getSize())
                .watermark(seedreamProperties.isWatermark())
                .build();

        ImagesResponse response = arkService.generateImages(request);
        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            log.warn("Seedream 响应为空");
            return null;
        }
        return response.getData().get(0).getUrl();
    }

    private byte[] downloadGeneratedImage(String imageUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.APPLICATION_OCTET_STREAM));
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<byte[]> response = restTemplate.exchange(URI.create(imageUrl), HttpMethod.GET, entity, byte[].class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Seedream 图片下载返回非成功状态码，status={}", response.getStatusCode());
            return null;
        }
        return response.getBody();
    }

    private String buildOssUrl(String ossKey) {
        return "https://oss.eggbabe.com/" + ossKey;
    }
}
