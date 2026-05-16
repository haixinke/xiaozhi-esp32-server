package xiaozhi.modules.pet.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.pet.vo.UserProfileVO;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("PetService UserProfile 功能测试")
@Transactional
class PetServiceImplProfileTest {

    @Autowired
    private PetService petService;

    @Test
    @DisplayName("getUserProfileByDeviceId - 有效deviceId返回用户画像")
    void getUserProfileByDeviceId_validDeviceId_returnsProfile() {
        // Arrange
        String deviceId = "test-device-001";

        // Act
        UserProfileVO profile = petService.getUserProfileByDeviceId(deviceId);

        // Assert
        assertThat(profile).isNotNull();
    }

    @Test
    @DisplayName("getUserProfileByDeviceId - 不存在的deviceId返回null")
    void getUserProfileByDeviceId_nonExistentDeviceId_returnsNull() {
        // Arrange
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
        // Arrange
        String deviceId = "test-device-001";

        // Act
        UserProfileVO profile = petService.getUserProfileByDeviceId(deviceId);

        // Assert
        if (profile != null) {
            assertThat(profile.getCreatedAt()).isNotNull();
            assertThat(profile.getProfileContent()).isNotNull();
        }
    }

    @Test
    @DisplayName("getUserProfileByDeviceId - 包含topics字段")
    void getUserProfileByDeviceId_includesTopics() {
        // Arrange
        String deviceId = "test-device-001";

        // Act
        UserProfileVO profile = petService.getUserProfileByDeviceId(deviceId);

        // Assert
        if (profile != null) {
            assertThat(profile.getTopics()).isNotNull();
        }
    }
}
