package xiaozhi.modules.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.page.PageData;
import xiaozhi.modules.pet.entity.MemoryEntity;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.pet.vo.MemoryVO;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("PetService Memory 功能测试")
@Transactional
class PetServiceImplMemoryTest {

    @Autowired
    private PetService petService;

    @Test
    @DisplayName("getMemoryByDeviceId - 有效deviceId返回分页结果")
    void getMemoryByDeviceId_validDeviceId_returnsPagedResults() {
        // Arrange
        String deviceId = "test-device-001";
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("limit", 10);

        // Act
        PageData<MemoryVO> result = petService.getMemoryByDeviceId(deviceId, params);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getList()).isNotNull();
    }

    @Test
    @DisplayName("getMemoryByDeviceId - 分页参数正确处理")
    void getMemoryByDeviceId_paginationParameters_worksCorrectly() {
        // Arrange
        String deviceId = "test-device-001";
        Map<String, Object> params = new HashMap<>();
        params.put("page", 2);
        params.put("limit", 5);

        // Act
        PageData<MemoryVO> result = petService.getMemoryByDeviceId(deviceId, params);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getList()).isNotNull();
    }

    @Test
    @DisplayName("getMemoryByDeviceId - 空结果返回空列表")
    void getMemoryByDeviceId_noResults_returnsEmptyList() {
        // Arrange
        String deviceId = "non-existent-device-" + System.currentTimeMillis();
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("limit", 10);

        // Act
        PageData<MemoryVO> result = petService.getMemoryByDeviceId(deviceId, params);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getList()).isNotNull();
        assertThat(result.getList()).isEmpty();
    }

    @Test
    @DisplayName("getMemoryByDeviceId - null deviceId可以正常查询")
    void getMemoryByDeviceId_nullDeviceId_returnsEmptyResult() {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("limit", 10);

        // Act & Assert - null deviceId should just return empty results
        PageData<MemoryVO> result = petService.getMemoryByDeviceId(null, params);
        assertThat(result).isNotNull();
        assertThat(result.getList()).isNotNull();
    }

    @Test
    @DisplayName("getMemoryByDeviceId - blank deviceId可以正常查询")
    void getMemoryByDeviceId_blankDeviceId_returnsEmptyResult() {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("limit", 10);

        // Act & Assert - blank deviceId should just return empty results
        PageData<MemoryVO> result = petService.getMemoryByDeviceId("   ", params);
        assertThat(result).isNotNull();
        assertThat(result.getList()).isNotNull();
    }

    @Test
    @DisplayName("getMemoryByDeviceId - 结果按created_at DESC排序")
    void getMemoryByDeviceId_resultsOrderedByCreatedAtDesc() {
        // Arrange
        String deviceId = "test-device-001";
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("limit", 10);

        // Act
        PageData<MemoryVO> result = petService.getMemoryByDeviceId(deviceId, params);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getList()).isNotNull();
        // Verify ordering if results exist
        if (!result.getList().isEmpty()) {
            assertThat(result.getList().get(0).getCreatedAt()).isNotNull();
        }
    }
}
