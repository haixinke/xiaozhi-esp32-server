package xiaozhi.modules.pet;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@DisplayName("MemoryController 测试")
@Transactional
class MemoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private Map<String, Object> baseParams;

    @BeforeEach
    void setUp() {
        baseParams = new HashMap<>();
        baseParams.put("page", 1);
        baseParams.put("limit", 10);
    }

    @Test
    @DisplayName("GET /pet/memory/list - 有效deviceId返回分页结果")
    void getMemoryList_validDeviceId_returnsPagedResults() throws Exception {
        mockMvc.perform(get("/pet/memory/list")
                        .param("deviceId", "test-device-001")
                        .param("page", "1")
                        .param("limit", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("GET /pet/memory/list - 分页参数正确处理")
    void getMemoryList_paginationParameters_worksCorrectly() throws Exception {
        mockMvc.perform(get("/pet/memory/list")
                        .param("deviceId", "test-device-001")
                        .param("page", "2")
                        .param("limit", "5"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("GET /pet/memory/list - 空结果返回空列表")
    void getMemoryList_noResults_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/pet/memory/list")
                        .param("deviceId", "non-existent-device")
                        .param("page", "1")
                        .param("limit", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists());
    }
}
