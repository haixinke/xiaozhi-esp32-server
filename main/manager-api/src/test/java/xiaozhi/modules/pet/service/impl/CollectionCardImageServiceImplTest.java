package xiaozhi.modules.pet.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import xiaozhi.common.config.AliyunOssProperties;
import xiaozhi.common.oss.OssService;
import xiaozhi.modules.pet.config.SeedreamProperties;
import xiaozhi.modules.pet.entity.PetEntity;

import java.net.URI;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollectionCardImageService 图片生成测试")
class CollectionCardImageServiceImplTest {

    private static final String IMAGE_GENERATION_URL = "https://ark.example.com/api/v3/images/generations";
    private static final String MODEL = "doubao-seedream-test";
    private static final String GENERATED_IMAGE_URL = "https://seedream.example.com/card.png"
            + "?X-Tos-Credential=AKLT%2F20260712%2Fcn-beijing%2Ftos%2Frequest"
            + "&X-Tos-Signature=abc123";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RestTemplate imageDownloadRestTemplate;

    @Mock
    private OssService ossService;

    private CollectionCardImageServiceImpl service;

    @BeforeEach
    void setUp() {
        SeedreamProperties seedreamProperties = new SeedreamProperties();
        seedreamProperties.setKey("test-key");
        seedreamProperties.setUrl(IMAGE_GENERATION_URL);
        seedreamProperties.setModel(MODEL);
        seedreamProperties.setSize("2K");
        seedreamProperties.setWatermark(true);

        AliyunOssProperties ossProperties = new AliyunOssProperties();
        ossProperties.setEndpoint("https://oss-cn.example.com");
        ossProperties.setBucketName("egg-bucket");

        service = new CollectionCardImageServiceImpl(
                seedreamProperties,
                restTemplate,
                imageDownloadRestTemplate,
                ossService,
                ossProperties,
                new ObjectMapper());

        when(ossService.isEnabled()).thenReturn(true);
    }

    @Test
    @DisplayName("generate - 按图像生成接口请求并上传返回的图片URL")
    @SuppressWarnings("unchecked")
    void generate_usesImageGenerationApiAndUploadsReturnedUrl() {
        byte[] imageBytes = new byte[] { 1, 2, 3 };
        String responseBody = """
                {
                  "data": [
                    {
                      "url": "%s"
                    }
                  ]
                }
                """.formatted(GENERATED_IMAGE_URL);

        when(restTemplate.exchange(eq(IMAGE_GENERATION_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseBody));
        when(imageDownloadRestTemplate.exchange(eq(URI.create(GENERATED_IMAGE_URL)), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(imageBytes));

        PetEntity pet = pet();
        String result = service.generate(pet);

        assertThat(result).isEqualTo("https://egg-bucket.oss-cn.example.com/eggbabe/cards/pet-1.png");

        ArgumentCaptor<HttpEntity<Map<String, Object>>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(IMAGE_GENERATION_URL), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));

        HttpEntity<Map<String, Object>> entity = entityCaptor.getValue();
        assertThat(entity.getHeaders().getContentType().toString()).isEqualTo("application/json");
        assertThat(entity.getHeaders().getFirst("Authorization")).isEqualTo("Bearer test-key");
        assertThat(entity.getBody())
                .containsEntry("model", MODEL)
                .containsEntry("response_format", "url")
                .containsEntry("size", "2K")
                .containsEntry("stream", false)
                .containsEntry("watermark", true);
        assertThat(entity.getBody()).containsKey("prompt");
        assertThat(entity.getBody()).doesNotContainKey("messages");
        assertThat((String) entity.getBody().get("prompt")).contains("昵称: 小蛋");

        ArgumentCaptor<HttpEntity<Void>> downloadEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(imageDownloadRestTemplate).exchange(eq(URI.create(GENERATED_IMAGE_URL)), eq(HttpMethod.GET),
                downloadEntityCaptor.capture(), eq(byte[].class));
        assertThat(downloadEntityCaptor.getValue().getHeaders().getFirst("Authorization")).isNull();
        verify(ossService).upload("eggbabe/cards/pet-1.png", imageBytes);
    }

    private PetEntity pet() {
        PetEntity pet = new PetEntity();
        pet.setId("pet-1");
        pet.setNickname("小蛋");
        pet.setPrototype("玉兔");
        pet.setZodiac("Aries");
        pet.setGender("MALE");
        pet.setMbti("ENFP");
        pet.setPersonalityBrief("温暖好奇");
        pet.setBloodType("O");
        pet.setHatchedAt(new Date(1_700_000_000_000L));
        return pet;
    }
}
