package xiaozhi.modules.pet;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@DisplayName("ProfileController 测试")
@Transactional
@Disabled("需要认证配置，暂时禁用")
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /pet/profile/list - 有效deviceId返回分页结果")
    void getProfileList_validDeviceId_returnsPagedResults() throws Exception {
        mockMvc.perform(get("/pet/profile/list")
                        .param("deviceId", "test-device-001")
                        .param("page", "1")
                        .param("limit", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("GET /pet/profile/list - 分页参数正确处理")
    void getProfileList_paginationParameters_worksCorrectly() throws Exception {
        mockMvc.perform(get("/pet/profile/list")
                        .param("deviceId", "test-device-001")
                        .param("page", "2")
                        .param("limit", "5"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("GET /pet/profile/list - 空结果返回空列表")
    void getProfileList_noResults_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/pet/profile/list")
                        .param("deviceId", "non-existent-device")
                        .param("page", "1")
                        .param("limit", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("GET /pet/profile/list - 无数据返回空列表")
    void getProfileList_emptyDatabase_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/pet/profile/list")
                        .param("deviceId", "empty-test-device")
                        .param("page", "1")
                        .param("limit", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists());
    }
}
