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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.context.annotation.Import;
import xiaozhi.common.config.TestArkServiceConfig;
import xiaozhi.common.constant.Constant;
import xiaozhi.modules.sys.service.SysParamsService;

@Slf4j
@Import(TestArkServiceConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@DisplayName("MemoryController 测试")
@Transactional
class MemoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysParamsService sysParamsService;

    private Map<String, Object> baseParams;

    /** 服务器密钥（server.secret 参数），/pet/memory/list 走 ServerSecretFilter 鉴权 */
    private String serverSecret;

    @BeforeEach
    void setUp() {
        baseParams = new HashMap<>();
        baseParams.put("page", 1);
        baseParams.put("limit", 10);

        // /pet/memory/list 在 ShiroConfig 中映射为 server 过滤器（服务密钥鉴权，与 xiaozhi-server 机对机调用一致），
        // 因此请求需携带真实配置的服务器密钥。这里只读取当前环境的密钥，不回写 DB/Redis，无需额外清理。
        // 注意：不要打印该密钥（避免写入日志）。
        serverSecret = sysParamsService.getValue(Constant.SERVER_SECRET, true);
        assertThat(serverSecret)
                .as("dev 环境 sys_params 需配置 %s 参数", Constant.SERVER_SECRET)
                .isNotBlank();
    }

    @Test
    @DisplayName("GET /pet/memory/list - 有效deviceId返回分页结果")
    void getMemoryList_validDeviceId_returnsPagedResults() throws Exception {
        mockMvc.perform(get("/pet/memory/list")
                        .header("Authorization", "Bearer " + serverSecret)
                        .param("deviceId", "test-device-001")
                        .param("page", "1")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("GET /pet/memory/list - 分页参数正确处理")
    void getMemoryList_paginationParameters_worksCorrectly() throws Exception {
        mockMvc.perform(get("/pet/memory/list")
                        .header("Authorization", "Bearer " + serverSecret)
                        .param("deviceId", "test-device-001")
                        .param("page", "2")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("GET /pet/memory/list - 空结果返回空列表")
    void getMemoryList_noResults_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/pet/memory/list")
                        .header("Authorization", "Bearer " + serverSecret)
                        .param("deviceId", "non-existent-device")
                        .param("page", "1")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists());
    }
}
