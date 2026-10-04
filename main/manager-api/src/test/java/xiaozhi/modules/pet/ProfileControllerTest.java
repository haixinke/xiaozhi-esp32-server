package xiaozhi.modules.pet;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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
@DisplayName("ProfileController 测试")
@Transactional
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysParamsService sysParamsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 服务器密钥（server.secret 参数），GET /pet/profile 在 ShiroConfig 中映射为 server 过滤器 */
    private String serverSecret;

    @BeforeEach
    void setUp() {
        // GET /pet/profile 走 ServerSecretFilter 鉴权（与 xiaozhi-server 机对机调用一致），
        // 因此请求需携带真实配置的服务器密钥。这里只读取当前环境的密钥，不回写 DB/Redis，无需额外清理。
        // 注意：不要打印该密钥（避免写入日志）。
        serverSecret = sysParamsService.getValue(Constant.SERVER_SECRET, true);
        assertThat(serverSecret)
                .as("dev 环境 sys_params 需配置 %s 参数", Constant.SERVER_SECRET)
                .isNotBlank();
    }

    /**
     * 自建单条画像 fixture，@Transactional 保证用例结束后自动回滚。
     * 注意：user_profiles.id 为无默认值的主键（Python 侧雪花ID写入），
     * MyBatis-Plus 的 IdType.AUTO 不会回写 id，因此这里用 JdbcTemplate 显式赋值插入。
     */
    private void insertProfile(String deviceId, String profileContent) {
        long id = System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
        String now = "2025-06-01T00:00:00Z";
        jdbcTemplate.update(
                "INSERT INTO user_profiles (id, user_id, profile_content, topics, created_at, updated_at) "
                        + "VALUES (?, ?, ?, CAST(? AS JSON), ?, ?)",
                id, deviceId, profileContent, "[\"编程\",\"音乐\"]", now, now);
    }

    @Test
    @DisplayName("GET /pet/profile - 有效deviceId返回用户画像")
    void getUserProfile_validDeviceId_returnsProfile() throws Exception {
        // Arrange - 使用带纳秒后缀的唯一 deviceId，避免与 dev 库中已有数据互相干扰
        String deviceId = "profile-ctrl-test-" + System.nanoTime();
        insertProfile(deviceId, "画像内容：喜欢编程和音乐");

        mockMvc.perform(get("/pet/profile")
                        .header("Authorization", "Bearer " + serverSecret)
                        .param("deviceId", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.profileContent").value("画像内容：喜欢编程和音乐"));
    }

    @Test
    @DisplayName("GET /pet/profile - 不存在的deviceId返回data为null")
    void getUserProfile_nonExistentDeviceId_returnsNullData() throws Exception {
        // Arrange - 使用带时间戳的 deviceId，确保库里一定不存在
        String deviceId = "non-existent-device-" + System.currentTimeMillis();

        mockMvc.perform(get("/pet/profile")
                        .header("Authorization", "Bearer " + serverSecret)
                        .param("deviceId", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("GET /pet/profile - 错误密钥返回401")
    void getUserProfile_wrongSecret_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/pet/profile")
                        .header("Authorization", "Bearer wrong-secret")
                        .param("deviceId", "any-device"))
                .andExpect(jsonPath("$.code").value(401));
    }
}
