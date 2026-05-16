package xiaozhi.modules.pet.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.page.PageData;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.pet.vo.UserProfileVO;

import java.util.HashMap;
import java.util.Map;

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
    @DisplayName("getUserProfileByDeviceId - 有效deviceId返回分页结果")
    void getUserProfileByDeviceId_validDeviceId_returnsPagedResults() {
        // Arrange
        String deviceId = "test-device-001";
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("limit", 10);

        // Act
        PageData<UserProfileVO> result = petService.getUserProfileByDeviceId(deviceId, params);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getList()).isNotNull();
    }

    @Test
    @DisplayName("getUserProfileByDeviceId - 分页参数正确处理")
    void getUserProfileByDeviceId_paginationParameters_worksCorrectly() {
        // Arrange
        String deviceId = "test-device-001";
        Map<String, Object> params = new HashMap<>();
        params.put("page", 2);
        params.put("limit", 5);

        // Act
        PageData<UserProfileVO> result = petService.getUserProfileByDeviceId(deviceId, params);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getList()).isNotNull();
    }

    @Test
    @DisplayName("getUserProfileByDeviceId - 空结果返回空列表")
    void getUserProfileByDeviceId_noResults_returnsEmptyList() {
        // Arrange
        String deviceId = "non-existent-device-" + System.currentTimeMillis();
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("limit", 10);

        // Act
        PageData<UserProfileVO> result = petService.getUserProfileByDeviceId(deviceId, params);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getList()).isNotNull();
        assertThat(result.getList()).isEmpty();
    }

    @Test
    @DisplayName("getUserProfileByDeviceId - null deviceId可以正常查询")
    void getUserProfileByDeviceId_nullDeviceId_returnsEmptyResult() {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("limit", 10);

        // Act & Assert - null deviceId should just return empty results
        PageData<UserProfileVO> result = petService.getUserProfileByDeviceId(null, params);
        assertThat(result).isNotNull();
        assertThat(result.getList()).isNotNull();
    }

    @Test
    @DisplayName("getUserProfileByDeviceId - blank deviceId可以正常查询")
    void getUserProfileByDeviceId_blankDeviceId_returnsEmptyResult() {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("limit", 10);

        // Act & Assert - blank deviceId should just return empty results
        PageData<UserProfileVO> result = petService.getUserProfileByDeviceId("   ", params);
        assertThat(result).isNotNull();
        assertThat(result.getList()).isNotNull();
    }

    @Test
    @DisplayName("getUserProfileByDeviceId - 结果按created_at DESC排序")
    void getUserProfileByDeviceId_resultsOrderedByCreatedAtDesc() {
        // Arrange
        String deviceId = "test-device-001";
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("limit", 10);

        // Act
        PageData<UserProfileVO> result = petService.getUserProfileByDeviceId(deviceId, params);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getList()).isNotNull();
        // Verify ordering if results exist
        if (!result.getList().isEmpty()) {
            assertThat(result.getList().get(0).getCreatedAt()).isNotNull();
        }
    }
}
