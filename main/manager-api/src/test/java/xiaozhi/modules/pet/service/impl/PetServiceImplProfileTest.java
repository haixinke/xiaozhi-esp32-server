package xiaozhi.modules.pet.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.pet.vo.UserProfileVO;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.context.annotation.Import;
import xiaozhi.common.config.TestArkServiceConfig;

@Slf4j
@Import(TestArkServiceConfig.class)
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("PetService UserProfile 功能测试")
@Transactional
class PetServiceImplProfileTest {

    @Autowired
    private PetService petService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 生成带纳秒后缀的唯一 deviceId，避免与 dev 库中已有数据互相干扰
     */
    private String uniqueDeviceId() {
        return "profile-test-device-" + System.nanoTime();
    }

    /**
     * 自建单条画像 fixture，@Transactional 保证用例结束后自动回滚。
     * 注意：user_profiles.id 为无默认值的主键（Python 侧雪花ID写入），
     * MyBatis-Plus 的 IdType.AUTO 不会回写 id，因此这里用 JdbcTemplate 显式赋值插入。
     */
    private void insertProfile(String deviceId, String profileContent, String topics, String createdAt) {
        long id = System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
        jdbcTemplate.update(
                "INSERT INTO user_profiles (id, user_id, profile_content, topics, created_at, updated_at) "
                        + "VALUES (?, ?, ?, CAST(? AS JSON), ?, ?)",
                id, deviceId, profileContent, topics, createdAt, createdAt);
    }

    @Test
    @DisplayName("getUserProfileByDeviceId - 有效deviceId返回用户画像")
    void getUserProfileByDeviceId_validDeviceId_returnsProfile() {
        // Arrange
        String deviceId = uniqueDeviceId();
        insertProfile(deviceId, "画像内容：喜欢编程和音乐", "[\"编程\",\"音乐\"]", "2025-06-01T00:00:00Z");

        // Act
        UserProfileVO profile = petService.getUserProfileByDeviceId(deviceId);

        // Assert
        assertThat(profile).isNotNull();
        assertThat(profile.getProfileContent()).isEqualTo("画像内容：喜欢编程和音乐");
    }

    @Test
    @DisplayName("getUserProfileByDeviceId - 不存在的deviceId返回null")
    void getUserProfileByDeviceId_nonExistentDeviceId_returnsNull() {
        // Arrange - 使用带时间戳的 deviceId，确保库里一定不存在
        String deviceId = "non-existent-device-" + System.currentTimeMillis();

        // Act
        UserProfileVO profile = petService.getUserProfileByDeviceId(deviceId);

        // Assert
        assertThat(profile).isNull();
    }

    @Test
    @DisplayName("getUserProfileByDeviceId - null deviceId返回null")
    void getUserProfileByDeviceId_nullDeviceId_returnsNull() {
        // Act & Assert
        UserProfileVO profile = petService.getUserProfileByDeviceId(null);
        assertThat(profile).isNull();
    }

    @Test
    @DisplayName("getUserProfileByDeviceId - blank deviceId返回null")
    void getUserProfileByDeviceId_blankDeviceId_returnsNull() {
        // Act & Assert
        UserProfileVO profile = petService.getUserProfileByDeviceId("   ");
        assertThat(profile).isNull();
    }

    @Test
    @DisplayName("getUserProfileByDeviceId - 返回最新的画像记录")
    void getUserProfileByDeviceId_returnsLatestProfile() {
        // Arrange - 同一 deviceId 插入两条不同时间的画像，最新一条应被返回
        String deviceId = uniqueDeviceId();
        insertProfile(deviceId, "旧画像", "[\"旧\"]", "2025-01-01T00:00:00Z");
        insertProfile(deviceId, "最新画像", "[\"新\"]", "2025-06-01T00:00:00Z");

        // Act
        UserProfileVO profile = petService.getUserProfileByDeviceId(deviceId);

        // Assert
        assertThat(profile).isNotNull();
        assertThat(profile.getCreatedAt()).isEqualTo("2025-06-01T00:00:00Z");
        assertThat(profile.getProfileContent()).isEqualTo("最新画像");
    }

    @Test
    @DisplayName("getUserProfileByDeviceId - 包含topics字段")
    void getUserProfileByDeviceId_includesTopics() {
        // Arrange
        String deviceId = uniqueDeviceId();
        insertProfile(deviceId, "画像内容", "[\"旅行\",\"摄影\"]", "2025-06-01T00:00:00Z");

        // Act
        UserProfileVO profile = petService.getUserProfileByDeviceId(deviceId);

        // Assert - JSON 列读出时会被数据库规范化（逗号后补空格），故用 contains 校验内容
        assertThat(profile).isNotNull();
        assertThat(profile.getTopics()).contains("旅行", "摄影");
    }
}
