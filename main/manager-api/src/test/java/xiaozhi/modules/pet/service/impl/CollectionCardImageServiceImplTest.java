package xiaozhi.modules.pet.service.impl;

import com.volcengine.ark.runtime.model.images.generation.GenerateImagesRequest;
import com.volcengine.ark.runtime.model.images.generation.ImagesResponse;
import com.volcengine.ark.runtime.model.images.generation.ResponseFormat;
import com.volcengine.ark.runtime.service.ArkService;
import com.aliyun.oss.model.CannedAccessControlList;
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
import xiaozhi.common.oss.OssService;
import xiaozhi.modules.pet.config.SeedreamProperties;
import xiaozhi.modules.pet.entity.PetEntity;

import java.net.URI;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollectionCardImageService 图片生成测试")
class CollectionCardImageServiceImplTest {

    private static final String MODEL = "doubao-seedream-test";
    private static final String GENERATED_IMAGE_URL = "https://seedream.example.com/card.png"
            + "?X-Tos-Credential=AKLT%2F20260712%2Fcn-beijing%2Ftos%2Frequest"
            + "&X-Tos-Signature=abc123";

    @Mock
    private ArkService arkService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private OssService ossService;

    private CollectionCardImageServiceImpl service;

    @BeforeEach
    void setUp() {
        SeedreamProperties seedreamProperties = new SeedreamProperties();
        seedreamProperties.setKey("test-key");
        seedreamProperties.setModel(MODEL);
        seedreamProperties.setSize("2K");
        seedreamProperties.setWatermark(true);

        service = new CollectionCardImageServiceImpl(
                seedreamProperties,
                arkService,
                restTemplate,
                ossService);

        when(ossService.isEnabled()).thenReturn(true);
        when(ossService.buildPublicUrl(anyString())).thenAnswer(inv -> "https://oss.eggbabe.com/" + inv.getArgument(0));
    }

    @Test
    @DisplayName("generate - 按图像生成接口请求并上传返回的图片URL")
    void generate_usesImageGenerationApiAndUploadsReturnedUrl() {
        byte[] imageBytes = new byte[] { 1, 2, 3 };

        ImagesResponse response = new ImagesResponse();
        ImagesResponse.Image image = new ImagesResponse.Image();
        image.setUrl(GENERATED_IMAGE_URL);
        response.setData(List.of(image));

        when(arkService.generateImages(any(GenerateImagesRequest.class))).thenReturn(response);
        when(restTemplate.exchange(eq(URI.create(GENERATED_IMAGE_URL)), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(imageBytes));

        PetEntity pet = pet();
        String result = service.generate(pet);

        assertThat(result).isEqualTo("https://oss.eggbabe.com/eggbabe/cards/pet-1.png");

        ArgumentCaptor<GenerateImagesRequest> requestCaptor = ArgumentCaptor.forClass(GenerateImagesRequest.class);
        verify(arkService).generateImages(requestCaptor.capture());

        GenerateImagesRequest request = requestCaptor.getValue();
        assertThat(request.getModel()).isEqualTo(MODEL);
        assertThat(request.getResponseFormat()).isEqualTo(ResponseFormat.Url);
        assertThat(request.getSize()).isEqualTo("2K");
        assertThat(request.getWatermark()).isTrue();
        assertThat(request.getPrompt()).contains("昵称: 小蛋");
        assertThat(request.getImage()).contains("https://oss.eggbabe.com/cards-bg/card-rabbit.png");

        verify(ossService).upload("eggbabe/cards/pet-1.png", imageBytes, CannedAccessControlList.PublicRead);
    }

    @Test
    @DisplayName("generate - 锦鲤原型使用鱼类参考图")
    void generate_koiPrototype_usesFishReferenceImage() {
        byte[] imageBytes = new byte[] { 1, 2, 3 };

        ImagesResponse response = new ImagesResponse();
        ImagesResponse.Image image = new ImagesResponse.Image();
        image.setUrl(GENERATED_IMAGE_URL);
        response.setData(List.of(image));

        when(arkService.generateImages(any(GenerateImagesRequest.class))).thenReturn(response);
        when(restTemplate.exchange(eq(URI.create(GENERATED_IMAGE_URL)), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(imageBytes));

        PetEntity pet = pet();
        pet.setPrototype("锦鲤");
        service.generate(pet);

        ArgumentCaptor<GenerateImagesRequest> requestCaptor = ArgumentCaptor.forClass(GenerateImagesRequest.class);
        verify(arkService).generateImages(requestCaptor.capture());

        GenerateImagesRequest request = requestCaptor.getValue();
        assertThat(request.getImage()).contains("https://oss.eggbabe.com/cards-bg/card-fish.png");
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
