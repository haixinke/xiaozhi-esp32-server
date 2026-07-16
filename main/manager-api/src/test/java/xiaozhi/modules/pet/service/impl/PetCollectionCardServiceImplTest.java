package xiaozhi.modules.pet.service.impl;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.pet.config.PetCollectionCardProperties;
import xiaozhi.modules.pet.dao.PetCollectionCardDao;
import xiaozhi.modules.pet.entity.PetCollectionCardEntity;
import xiaozhi.modules.pet.vo.CollectionCardVO;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetCollectionCardService 测试")
class PetCollectionCardServiceImplTest {

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = org.mockito.Mockito.mock(ApplicationContext.class);
        MessageSource messageSource = org.mockito.Mockito.mock(MessageSource.class);
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @Mock private PetCollectionCardDao petCollectionCardDao;

    private PetCollectionCardServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        PetCollectionCardProperties properties = buildProperties();
        service = new PetCollectionCardServiceImpl(petCollectionCardDao, properties);
    }

    private PetCollectionCardProperties buildProperties() {
        PetCollectionCardProperties properties = new PetCollectionCardProperties();
        properties.setFallbackUrl("https://oss.eggbabe.com/default-card/fish/card-fish-0.webp");

        PetCollectionCardProperties.Prototype koi = new PetCollectionCardProperties.Prototype();
        koi.setBaseUrl("https://oss.eggbabe.com/default-card/fish/");
        koi.setPrefix("card-fish");
        koi.setCount(10);
        properties.setKoi(koi);

        PetCollectionCardProperties.Prototype rabbit = new PetCollectionCardProperties.Prototype();
        rabbit.setBaseUrl("https://oss.eggbabe.com/default-card/rabbit/");
        rabbit.setPrefix("card-rabbit");
        rabbit.setCount(10);
        properties.setRabbit(rabbit);

        return properties;
    }

    @Test
    @DisplayName("createCard - 首卡: source=HATCH, sortOrder=0")
    void createCard_firstCard_hatchSource() {
        when(petCollectionCardDao.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(petCollectionCardDao.insert(any(PetCollectionCardEntity.class))).thenReturn(1);

        CollectionCardVO result = service.createCard("pet-1", "锦鲤", "温暖好奇", "HATCH");

        assertThat(result.getSource()).isEqualTo("HATCH");
        assertThat(result.getBrief()).isEqualTo("温暖好奇");
        assertThat(result.getSortOrder()).isEqualTo(0);
        assertThat(result.getImageUrl()).startsWith("https://oss.eggbabe.com/default-card/fish/card-fish-");
    }

    @Test
    @DisplayName("createCard - 图片去重: 已有图片A, 新卡不应再选A")
    void createCard_imageDedup_noRepeat() {
        PetCollectionCardEntity existing = new PetCollectionCardEntity();
        existing.setImageUrl("https://oss.eggbabe.com/default-card/fish/card-fish-0.webp");
        existing.setSortOrder(0);
        when(petCollectionCardDao.selectList(any(QueryWrapper.class))).thenReturn(List.of(existing));
        when(petCollectionCardDao.insert(any(PetCollectionCardEntity.class))).thenReturn(1);

        CollectionCardVO result = service.createCard("pet-1", "锦鲤", "测试简介", "MILESTONE");

        assertThat(result.getImageUrl()).isNotEqualTo("https://oss.eggbabe.com/default-card/fish/card-fish-0.webp");
        assertThat(result.getSortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("createCard - 已有10张卡时拒绝创建")
    void createCard_maxLimit_reject() {
        // 构造 10 张已有卡片
        List<PetCollectionCardEntity> existing = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> {
                    PetCollectionCardEntity e = new PetCollectionCardEntity();
                    e.setImageUrl("https://oss.eggbabe.com/default-card/fish/card-fish-" + i + ".webp");
                    e.setSortOrder(i);
                    return e;
                })
                .toList();
        when(petCollectionCardDao.selectList(any(QueryWrapper.class))).thenReturn(existing);

        assertThatThrownBy(() -> service.createCard("pet-1", "锦鲤", "测试", "MILESTONE"))
                .isInstanceOf(xiaozhi.common.exception.RenException.class);
    }

    @Test
    @DisplayName("listByPetId - 按 sortOrder 升序返回")
    void listByPetId_sortedAsc() {
        PetCollectionCardEntity card2 = new PetCollectionCardEntity();
        card2.setId("card-2");
        card2.setSortOrder(1);
        card2.setImageUrl("url-2");
        card2.setBrief("brief-2");
        card2.setSource("HATCH");

        PetCollectionCardEntity card1 = new PetCollectionCardEntity();
        card1.setId("card-1");
        card1.setSortOrder(0);
        card1.setImageUrl("url-1");
        card1.setBrief("brief-1");
        card1.setSource("HATCH");

        // selectList 返回乱序，service 内部应按 sortOrder 排序
        when(petCollectionCardDao.selectList(any(QueryWrapper.class))).thenReturn(List.of(card2, card1));

        List<CollectionCardVO> result = service.listByPetId("pet-1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSortOrder()).isEqualTo(0);
        assertThat(result.get(1).getSortOrder()).isEqualTo(1);
    }
}
