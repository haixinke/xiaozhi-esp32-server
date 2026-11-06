package xiaozhi.modules.wechat.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.config.service.impl.ConfigServiceImpl;
import xiaozhi.modules.device.dto.DeviceReportReqDTO;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.device.service.DeviceService;
import xiaozhi.modules.device.service.impl.DeviceServiceImpl;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.wechat.dao.WechatUserDao;
import xiaozhi.modules.wechat.entity.WechatUserEntity;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** OTA 和旧 WebSocket 凭据都必须在实际设备归属校验后才可继续。 */
class PostHatchDeviceAccessTest {
    @Mock private WechatUserDao wechatUserDao;
    @Mock private PetDao petDao;
    @Mock private RedisUtils redisUtils;
    @Mock private DeviceService deviceService;
    @Mock private AgentService agentService;
    private DeviceServiceImpl otaTarget;
    private ConfigServiceImpl configService;
    private AutoCloseable mocks;

    @BeforeAll
    static void messages() {
        ApplicationContext context = mock(ApplicationContext.class);
        MessageSource messages = mock(MessageSource.class);
        when(context.getBean("messageSource")).thenReturn(messages);
        when(messages.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(call -> call.getArgument(2));
        SpringContextUtils.applicationContext = context;
    }

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        WechatPhoneGate phoneGate = new WechatPhoneGate(wechatUserDao, petDao);
        otaTarget = new DeviceServiceImpl(null, null, null, redisUtils, null, null, phoneGate);
        configService = new ConfigServiceImpl(null, deviceService, null, agentService, null,
                redisUtils, null, null, null, null, null, null, null, null, phoneGate);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocks.close();
    }

    @Test
    void unboundWechatDeviceCannotObtainFreshWebsocketCredentials() {
        DeviceEntity device = device();
        DeviceServiceImpl ota = spy(otaTarget);
        doReturn(device).when(ota).getDeviceByMacAddress("pet-device");
        unboundWechatAccount(1L);
        // 上报的 board 可以伪造，判断必须依据数据库的真实设备类型。
        DeviceReportReqDTO report = new DeviceReportReqDTO();
        DeviceReportReqDTO.BoardInfo board = new DeviceReportReqDTO.BoardInfo();
        board.setType("esp32");
        report.setBoard(board);

        assertThatThrownBy(() -> ota.checkDeviceActive("pet-device", "client", report))
                .isInstanceOfSatisfying(RenException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo(10228));
    }

    @Test
    void unboundWechatDeviceCannotFetchPrivateConfigurationWithOldCredentials() {
        when(deviceService.getDeviceByMacAddress("pet-device")).thenReturn(device());
        unboundWechatAccount(1L);

        assertThatThrownBy(() -> configService.getAgentModels("pet-device", Map.of()))
                .isInstanceOfSatisfying(RenException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo(10228));
        verifyNoInteractions(agentService);
    }

    @Test
    void temporaryAdminRegistrationFlagCannotBypassRealWechatDeviceGate() {
        when(deviceService.getDeviceByMacAddress("pet-device")).thenReturn(device());
        when(redisUtils.get(RedisKeys.getTmpRegisterMacKey("pet-device"))).thenReturn("true");
        unboundWechatAccount(1L);

        assertThatThrownBy(() -> configService.getAgentModels("pet-device", Map.of()))
                .isInstanceOfSatisfying(RenException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo(10228));
    }

    @Test
    void boundWechatDeviceContinuesToNormalAgentLookup() {
        when(deviceService.getDeviceByMacAddress("pet-device")).thenReturn(device());
        WechatUserEntity mapping = new WechatUserEntity();
        mapping.setPhone("13800138000");
        when(wechatUserDao.selectList(any())).thenReturn(List.of(mapping));

        assertThatThrownBy(() -> configService.getAgentModels("pet-device", Map.of()))
                .isInstanceOfSatisfying(RenException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getCode())
                                .isEqualTo(ErrorCode.AGENT_NOT_FOUND));
        verify(agentService).getAgentById("agent");
    }

    @Test
    void hardwareDeviceDoesNotNeedWechatPhoneBinding() {
        DeviceEntity hardware = device();
        hardware.setBoard("esp32");
        when(deviceService.getDeviceByMacAddress("pet-device")).thenReturn(hardware);

        assertThatThrownBy(() -> configService.getAgentModels("pet-device", Map.of()))
                .isInstanceOfSatisfying(RenException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getCode())
                                .isEqualTo(ErrorCode.AGENT_NOT_FOUND));
        verifyNoInteractions(wechatUserDao, petDao);
    }

    @Test
    void ordinaryAccountWithMiniProgramDeviceKeepsExistingAccess() {
        when(deviceService.getDeviceByMacAddress("pet-device")).thenReturn(device());
        when(wechatUserDao.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> configService.getAgentModels("pet-device", Map.of()))
                .isInstanceOfSatisfying(RenException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getCode())
                                .isEqualTo(ErrorCode.AGENT_NOT_FOUND));
        verify(agentService).getAgentById("agent");
    }

    @Test
    void unboundAccountWithNoActiveHatchedPetKeepsExistingDeviceAccess() {
        when(deviceService.getDeviceByMacAddress("pet-device")).thenReturn(device());
        unboundWechatAccount(0L);

        assertThatThrownBy(() -> configService.getAgentModels("pet-device", Map.of()))
                .isInstanceOfSatisfying(RenException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getCode())
                                .isEqualTo(ErrorCode.AGENT_NOT_FOUND));
    }

    /** 用真实门槛和模拟数据库事实验证服务入口，避免用 gate mock 掩盖适用范围差异。 */
    private void unboundWechatAccount(long activeHatchedPets) {
        WechatUserEntity mapping = new WechatUserEntity();
        mapping.setUserId(7L);
        when(wechatUserDao.selectList(any())).thenReturn(List.of(mapping));
        when(petDao.selectCount(any())).thenReturn(activeHatchedPets);
    }

    private DeviceEntity device() {
        DeviceEntity device = new DeviceEntity();
        device.setId("pet-device");
        device.setMacAddress("pet-device");
        device.setAgentId("agent");
        device.setUserId(7L);
        device.setBoard("wechat-egg-miniprogram");
        return device;
    }
}
