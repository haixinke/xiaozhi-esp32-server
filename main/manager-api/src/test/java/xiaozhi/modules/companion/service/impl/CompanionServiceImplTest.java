package xiaozhi.modules.companion.service.impl;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.common.user.UserDetail;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.agent.dao.AiAgentChatHistoryDao;
import xiaozhi.modules.agent.entity.AgentEntity;
import xiaozhi.modules.agent.service.AgentContextProviderService;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.companion.dao.CompanionDao;
import xiaozhi.modules.companion.dto.CompanionCreateDTO;
import xiaozhi.modules.companion.dto.CompanionUpdateDTO;
import xiaozhi.modules.companion.entity.CompanionEntity;
import xiaozhi.modules.companion.service.CompanionService;
import xiaozhi.modules.companion.util.CompanionMood;
import xiaozhi.modules.companion.util.IntimacyLevel;
import xiaozhi.modules.companion.vo.CompanionVO;
import xiaozhi.modules.device.service.DeviceService;
import xiaozhi.modules.item.service.ItemService;
import xiaozhi.modules.security.user.SecurityUser;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.transaction.PlatformTransactionManager;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CompanionServiceImpl 伴侣心情相关逻辑")
class CompanionServiceImplTest {

    @Mock
    private CompanionDao companionDao;

    @Mock
    private AgentService agentService;

    @Mock
    private AgentContextProviderService agentContextProviderService;

    @Mock
    private DeviceService deviceService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private ItemService itemService;

    @Mock
    private AiAgentChatHistoryDao chatHistoryDao;

    private CompanionService companionService;

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @BeforeEach
    void setUp() throws Exception {
        companionService = new CompanionServiceImpl(companionDao, agentService,
                agentContextProviderService, deviceService, transactionManager, itemService, chatHistoryDao);

        // BaseServiceImpl 使用 baseDao 执行 selectById / updateById
        Field baseDaoField = BaseServiceImpl.class.getDeclaredField("baseDao");
        baseDaoField.setAccessible(true);
        baseDaoField.set(companionService, companionDao);

        when(companionDao.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            CompanionEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        }).when(companionDao).insert(any(CompanionEntity.class));
        when(companionDao.updateById(any(CompanionEntity.class))).thenReturn(1);
        when(agentService.updateById(any(AgentEntity.class))).thenReturn(true);
    }

    @Test
    @DisplayName("create() 为 gf 伴侣自动生成生理期参数")
    void create_gfCompanion_generatesMenstrualCycle() {
        Long userId = 100L;
        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(userId);
            security.when(SecurityUser::getUser).thenReturn(userDetail(userId));

            when(deviceService.getAgentIdByDeviceId("device-123")).thenReturn(null);

            CompanionCreateDTO dto = createDto();
            dto.setType("gf");

            CompanionVO vo = companionService.create(dto);

            assertThat(vo.getType()).isEqualTo("gf");
            CompanionEntity captured = captureInsertedCompanion();
            assertThat(captured.getMenstrualCycleStart()).isNotNull();
            assertThat(captured.getMenstrualCycleLength()).isBetween(26, 32);
            assertThat(captured.getMenstrualPeriodLength()).isBetween(4, 6);
        }
    }

    @Test
    @DisplayName("create() 返回的 VO 包含经期状态")
    void create_gfCompanion_voContainsMenstrualStatus() {
        Long userId = 100L;
        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(userId);
            security.when(SecurityUser::getUser).thenReturn(userDetail(userId));

            when(deviceService.getAgentIdByDeviceId("device-123")).thenReturn(null);

            CompanionCreateDTO dto = createDto();
            dto.setType("gf");

            CompanionVO vo = companionService.create(dto);

            assertThat(vo.getMenstrualStatus()).isNotNull();
            assertThat(vo.getMenstrualStatus().getPhase()).isNotBlank();
            assertThat(vo.getMenstrualStatus().getCycleDay()).isBetween(1, 32);
        }
    }

    @Test
    @DisplayName("create() 为 bf 伴侣不生成生理期参数")
    void create_bfCompanion_doesNotGenerateMenstrualCycle() {
        Long userId = 100L;
        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(userId);
            security.when(SecurityUser::getUser).thenReturn(userDetail(userId));

            when(deviceService.getAgentIdByDeviceId("device-123")).thenReturn(null);

            CompanionCreateDTO dto = createDto();
            dto.setType("bf");

            companionService.create(dto);

            CompanionEntity captured = captureInsertedCompanion();
            assertThat(captured.getMenstrualCycleStart()).isNull();
            assertThat(captured.getMenstrualCycleLength()).isNull();
            assertThat(captured.getMenstrualPeriodLength()).isNull();
        }
    }

    @Test
    @DisplayName("create() 固定使用愉快心情初始化，并同步提示词到已有 agent")
    void create_assignsJoyMoodAndSyncsPrompt() {
        Long userId = 100L;
        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(userId);
            security.when(SecurityUser::getUser).thenReturn(userDetail(userId));

            String agentId = "agent-123";
            when(deviceService.getAgentIdByDeviceId("device-123")).thenReturn(agentId);
            when(agentService.selectById(agentId)).thenReturn(agentEntity(userId));

            CompanionCreateDTO dto = createDto();

            CompanionVO vo = companionService.create(dto);

            assertThat(vo.getMood()).isEqualTo("JOY");
            verify(companionDao).insert(any(CompanionEntity.class));
            verify(agentService).updateById(any(AgentEntity.class));
            // 提示词已同步到 agent，但不再烤入易变的心情值（心情改由实时上下文注入）
            AgentEntity updated = captureUpdatedAgent();
            assertThat(updated.getSystemPrompt()).contains("元气邻家妹");
            assertThat(updated.getSystemPrompt()).doesNotContain(CompanionMood.JOY.getLabel());
        }
    }

    @Test
    @DisplayName("create() 当设备未绑定 agent 时跳过同步")
    void create_noAgent_skipsSync() {
        Long userId = 100L;
        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(userId);
            security.when(SecurityUser::getUser).thenReturn(userDetail(userId));

            when(deviceService.getAgentIdByDeviceId("device-123")).thenReturn(null);

            CompanionVO vo = companionService.create(createDto());

            assertThat(vo.getMood()).isEqualTo("JOY");
            verify(agentService, never()).selectById(anyString());
            verify(agentService, never()).updateById(any(AgentEntity.class));
        }
    }

    @Test
    @DisplayName("update() 将心情转换为大写并持久化")
    void update_moodIsNormalizedToUpperCase() {
        Long userId = 100L;
        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(userId);

            CompanionEntity existing = companionEntity(1L, userId, "device-123", "CALM");
            when(companionDao.selectOne(any())).thenReturn(existing);

            CompanionUpdateDTO dto = new CompanionUpdateDTO();
            dto.setDeviceId("device-123");
            dto.setMood("joy");

            companionService.update(dto);

            org.mockito.ArgumentCaptor<CompanionEntity> captor = org.mockito.ArgumentCaptor.forClass(CompanionEntity.class);
            verify(companionDao).updateById(captor.capture());
            assertThat(captor.getValue().getMood()).isEqualTo("JOY");
        }
    }

    @Test
    @DisplayName("refreshAllMoods() 经期 gf 伴侣正常刷新心情")
    void refreshAllMoods_menstruatingGfCompanion_updatesMood() {
        CompanionEntity c1 = companionEntity(1L, 100L, "device-1", "CALM");
        c1.setType("gf");
        java.time.LocalDate startDate = java.time.LocalDate.now().minusDays(1);
        c1.setMenstrualCycleStart(java.util.Date.from(startDate.atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant()));
        c1.setMenstrualCycleLength(28);
        c1.setMenstrualPeriodLength(5);
        Page<CompanionEntity> page = new Page<>(1, 500);
        page.setRecords(List.of(c1));
        page.setTotal(1);
        when(companionDao.selectPage(any(Page.class), any())).thenReturn(page);

        when(deviceService.getAgentIdByDeviceId("device-1")).thenReturn(null);

        companionService.refreshAllMoods();

        verify(companionDao).updateById(any(CompanionEntity.class));
        assertThat(c1.getMood()).isIn(moodNames());
    }

    @Test
    @DisplayName("refreshAllMoods() 分页刷新所有伴侣心情并登记实时上下文源（不再重建提示词）")
    void refreshAllMoods_updatesAllCompanionsAndRegistersContext() {
        CompanionEntity c1 = companionEntity(1L, 100L, "device-1", "CALM");
        CompanionEntity c2 = companionEntity(2L, 101L, "device-2", "JOY");
        Page<CompanionEntity> page = new Page<>(1, 500);
        page.setRecords(List.of(c1, c2));
        page.setTotal(2);
        when(companionDao.selectPage(any(Page.class), any())).thenReturn(page);

        when(deviceService.getAgentIdByDeviceId("device-1")).thenReturn("agent-1");
        when(deviceService.getAgentIdByDeviceId("device-2")).thenReturn("agent-2");

        companionService.refreshAllMoods();

        verify(companionDao, times(2)).updateById(any(CompanionEntity.class));
        // 心情改由实时注入：定时任务不再重建 agent 提示词
        verify(agentService, never()).updateById(any(AgentEntity.class));
        // 但会为每个有 agent 的伴侣幂等登记实时上下文源
        verify(agentContextProviderService, times(2)).saveOrUpdateByAgentId(any());
        assertThat(c1.getMood()).isIn(moodNames());
        assertThat(c2.getMood()).isIn(moodNames());
    }

    @Test
    @DisplayName("refreshAllMoods() 某个伴侣无 agent 时跳过该条，不影响其他")
    void refreshAllMoods_missingAgent_skipsAndContinues() {
        CompanionEntity c1 = companionEntity(1L, 100L, "device-1", "CALM");
        CompanionEntity c2 = companionEntity(2L, 101L, "device-2", "JOY");
        Page<CompanionEntity> page = new Page<>(1, 500);
        page.setRecords(List.of(c1, c2));
        page.setTotal(2);
        when(companionDao.selectPage(any(Page.class), any())).thenReturn(page);

        when(deviceService.getAgentIdByDeviceId("device-1")).thenReturn(null);
        when(deviceService.getAgentIdByDeviceId("device-2")).thenReturn("agent-2");

        companionService.refreshAllMoods();

        verify(companionDao, times(2)).updateById(any(CompanionEntity.class));
        verify(agentService, never()).updateById(any(AgentEntity.class));
        // 仅 device-2 有 agent，登记一次实时上下文源
        verify(agentContextProviderService, times(1)).saveOrUpdateByAgentId(any());
    }

    @Test
    @DisplayName("syncPromptToAgent() 经期 gf 的静态提示词不再包含经期状态（改由实时注入）")
    void syncPromptToAgent_menstruatingGf_excludesMenstrualStateFromStaticPrompt() {
        Long userId = 100L;
        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(userId);

            java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
            CompanionEntity companion = companionEntity(1L, userId, "device-123", "JOY");
            companion.setType("gf");
            companion.setMenstrualCycleStart(java.util.Date.from(today.minusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant()));
            companion.setMenstrualCycleLength(28);
            companion.setMenstrualPeriodLength(5);
            when(companionDao.selectById(1L)).thenReturn(companion);

            String agentId = "agent-123";
            when(agentService.selectById(agentId)).thenReturn(agentEntity(userId));

            companionService.syncPromptToAgent(agentId, 1L);

            AgentEntity updated = captureUpdatedAgent();
            assertThat(updated.getSystemPrompt()).doesNotContain("经期");
            assertThat(updated.getSystemPrompt()).doesNotContain("{{menstrualState}}");
        }
    }

    @Test
    @DisplayName("syncPromptToAgent() 非经期 gf 提示词不包含经期状态")
    void syncPromptToAgent_nonMenstruatingGf_excludesMenstrualState() {
        Long userId = 100L;
        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(userId);

            java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
            CompanionEntity companion = companionEntity(1L, userId, "device-123", "JOY");
            companion.setType("gf");
            companion.setMenstrualCycleStart(java.util.Date.from(today.minusDays(10).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant()));
            companion.setMenstrualCycleLength(28);
            companion.setMenstrualPeriodLength(5);
            when(companionDao.selectById(1L)).thenReturn(companion);

            String agentId = "agent-123";
            when(agentService.selectById(agentId)).thenReturn(agentEntity(userId));

            companionService.syncPromptToAgent(agentId, 1L);

            AgentEntity updated = captureUpdatedAgent();
            assertThat(updated.getSystemPrompt()).doesNotContain("经期");
            assertThat(updated.getSystemPrompt()).doesNotContain("{{menstrualState}}");
        }
    }

    @Test
    @DisplayName("syncPromptToAgent() bf 提示词不包含经期状态")
    void syncPromptToAgent_bf_excludesMenstrualState() {
        Long userId = 100L;
        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(userId);

            CompanionEntity companion = companionEntity(1L, userId, "device-123", "JOY");
            companion.setType("bf");
            when(companionDao.selectById(1L)).thenReturn(companion);

            String agentId = "agent-123";
            when(agentService.selectById(agentId)).thenReturn(agentEntity(userId));

            companionService.syncPromptToAgent(agentId, 1L);

            AgentEntity updated = captureUpdatedAgent();
            assertThat(updated.getSystemPrompt()).doesNotContain("经期");
            assertThat(updated.getSystemPrompt()).doesNotContain("{{menstrualState}}");
        }
    }

    @Test
    @DisplayName("syncPromptToAgent() 静态提示词不再烤入今日心情值（改由实时注入）")
    void syncPromptToAgent_excludesMoodValueFromStaticPrompt() {
        Long userId = 100L;
        String agentId = "agent-123";
        CompanionEntity companion = companionEntity(1L, userId, "device-123", "EXCITEMENT");

        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(userId);

            when(companionDao.selectById(1L)).thenReturn(companion);
            when(agentService.selectById(agentId)).thenReturn(agentEntity(userId));

            companionService.syncPromptToAgent(agentId, 1L);

            AgentEntity updated = captureUpdatedAgent();
            assertThat(updated.getSystemPrompt()).doesNotContain(CompanionMood.EXCITEMENT.getLabel());
            assertThat(updated.getSystemPrompt()).doesNotContain("{{mood}}");
        }
    }

    @Test
    @DisplayName("create() 起步亲密度取自 IntimacyRule（心动档）")
    void create_startsIntimacyAtCrushTier() {
        Long userId = 100L;
        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(userId);
            security.when(SecurityUser::getUser).thenReturn(userDetail(userId));
            when(deviceService.getAgentIdByDeviceId("device-123")).thenReturn(null);

            CompanionCreateDTO dto = createDto();
            dto.setRelationType("childhood");

            companionService.create(dto);

            CompanionEntity captured = captureInsertedCompanion();
            assertThat(captured.getIntimacy()).isEqualTo(0.38f);
        }
    }

    @Test
    @DisplayName("update() 变更 relationType 不重置已养成的 intimacy")
    void update_relationTypeChange_keepsEarnedIntimacy() {
        Long userId = 100L;
        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(userId);

            CompanionEntity existing = companionEntity(1L, userId, "device-123", "JOY");
            existing.setRelationType("childhood");
            existing.setIntimacy(0.72f); // 已养成
            when(companionDao.selectOne(any())).thenReturn(existing);

            CompanionUpdateDTO dto = new CompanionUpdateDTO();
            dto.setDeviceId("device-123");
            dto.setRelationType("bickering");

            companionService.update(dto);

            org.mockito.ArgumentCaptor<CompanionEntity> captor =
                    org.mockito.ArgumentCaptor.forClass(CompanionEntity.class);
            verify(companionDao).updateById(captor.capture());
            assertThat(captor.getValue().getRelationType()).isEqualTo("bickering");
            assertThat(captor.getValue().getIntimacy()).isEqualTo(0.72f);
        }
    }

    @Test
    @DisplayName("buildRealtimeContext() 关系亲密度按 5 档取文案")
    void buildRealtimeContext_intimacyUsesFiveTierDescription() {
        CompanionEntity companion = companionEntity(1L, 100L, "device-123", "JOY");
        companion.setType("bf"); // 排除经期，聚焦亲密度
        companion.setIntimacy(0.35f); // 心动
        when(companionDao.selectOne(any())).thenReturn(companion);

        java.util.Map<String, String> ctx = companionService.buildRealtimeContext("device-123");

        assertThat(ctx.get("关系亲密度"))
                .isEqualTo(IntimacyLevel.CRUSH.getPromptDescription());
    }

    @Test
    @DisplayName("refreshAllIntimacy() 昨日活跃伴侣涨亲密度并累加连续天数")
    void refreshAllIntimacy_activeCompanion_growsAndStreaks() {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        java.time.LocalDate yesterday = today.minusDays(1);

        CompanionEntity c1 = companionEntity(1L, 100L, "device-1", "CALM");
        c1.setIntimacy(0.35f);
        c1.setActiveStreak(2);
        c1.setLastActiveDate(yesterday.minusDays(1)); // 昨天的前一天活跃 -> 连续
        Page<CompanionEntity> page = new Page<>(1, 500);
        page.setRecords(List.of(c1));
        page.setTotal(1);
        when(companionDao.selectPage(any(Page.class), any())).thenReturn(page);
        when(deviceService.getAgentIdByDeviceId("device-1")).thenReturn("agent-1");

        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("agentId", "agent-1");
        row.put("userMsgs", 10L);
        when(chatHistoryDao.selectMaps(any())).thenReturn(List.of(row));

        companionService.refreshAllIntimacy();

        org.mockito.ArgumentCaptor<CompanionEntity> captor =
                org.mockito.ArgumentCaptor.forClass(CompanionEntity.class);
        verify(companionDao).updateById(captor.capture());
        CompanionEntity saved = captor.getValue();
        assertThat(saved.getIntimacy()).isGreaterThan(0.35f);
        assertThat(saved.getActiveStreak()).isEqualTo(3);
        assertThat(saved.getLastActiveDate()).isEqualTo(yesterday);
        assertThat(saved.getIntimacyUpdatedDate()).isEqualTo(today);
    }

    @Test
    @DisplayName("refreshAllIntimacy() 冷落超宽限期衰减并清零连续天数")
    void refreshAllIntimacy_neglected_decaysAndResetsStreak() {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        java.time.LocalDate yesterday = today.minusDays(1);

        CompanionEntity c1 = companionEntity(1L, 100L, "device-1", "CALM");
        c1.setIntimacy(0.70f);
        c1.setActiveStreak(5);
        c1.setLastActiveDate(yesterday.minusDays(9)); // 早已冷落，超宽限
        Page<CompanionEntity> page = new Page<>(1, 500);
        page.setRecords(List.of(c1));
        page.setTotal(1);
        when(companionDao.selectPage(any(Page.class), any())).thenReturn(page);
        when(deviceService.getAgentIdByDeviceId("device-1")).thenReturn("agent-1");
        when(chatHistoryDao.selectMaps(any())).thenReturn(java.util.Collections.emptyList());

        companionService.refreshAllIntimacy();

        org.mockito.ArgumentCaptor<CompanionEntity> captor =
                org.mockito.ArgumentCaptor.forClass(CompanionEntity.class);
        verify(companionDao).updateById(captor.capture());
        CompanionEntity saved = captor.getValue();
        assertThat(saved.getIntimacy()).isLessThan(0.70f);
        assertThat(saved.getActiveStreak()).isEqualTo(0);
    }

    @Test
    @DisplayName("refreshAllIntimacy() 同日已处理则幂等跳过")
    void refreshAllIntimacy_alreadyProcessedToday_skips() {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));

        CompanionEntity c1 = companionEntity(1L, 100L, "device-1", "CALM");
        c1.setIntimacy(0.35f);
        c1.setIntimacyUpdatedDate(today);
        Page<CompanionEntity> page = new Page<>(1, 500);
        page.setRecords(List.of(c1));
        page.setTotal(1);
        when(companionDao.selectPage(any(Page.class), any())).thenReturn(page);
        when(chatHistoryDao.selectMaps(any())).thenReturn(java.util.Collections.emptyList());

        companionService.refreshAllIntimacy();

        verify(companionDao, never()).updateById(any(CompanionEntity.class));
    }

    private AgentEntity captureUpdatedAgent() {
        ArgumentCaptor<AgentEntity> captor = ArgumentCaptor.forClass(AgentEntity.class);
        verify(agentService).updateById(captor.capture());
        return captor.getValue();
    }

    private CompanionEntity captureInsertedCompanion() {
        ArgumentCaptor<CompanionEntity> captor = ArgumentCaptor.forClass(CompanionEntity.class);
        verify(companionDao).insert(captor.capture());
        return captor.getValue();
    }

    private CompanionCreateDTO createDto() {
        CompanionCreateDTO dto = new CompanionCreateDTO();
        dto.setDeviceId("device-123");
        dto.setType("gf");
        dto.setAvatar("http://avatar");
        dto.setDefaultImage("http://default");
        dto.setCharacter("linjiamei");
        dto.setOccupation("design");
        dto.setVoice("voice-1");
        dto.setSoulTraits("clingy");
        dto.setSoulQuirk("jealous");
        dto.setRelationType("childhood");
        dto.setPetType("cat");
        dto.setPetName("Miao");
        return dto;
    }

    private CompanionEntity companionEntity(Long id, Long userId, String deviceId, String mood) {
        CompanionEntity e = new CompanionEntity();
        e.setId(id);
        e.setUserId(userId);
        e.setDeviceId(deviceId);
        e.setType("gf");
        e.setAvatar("http://avatar");
        e.setDefaultImage("http://default");
        e.setCharacter("linjiamei");
        e.setOccupation("design");
        e.setVoice("voice-1");
        e.setSoulTraits("clingy");
        e.setSoulQuirk("jealous");
        e.setRelationType("childhood");
        e.setPetType("cat");
        e.setPetName("Miao");
        e.setMood(mood);
        e.setBirthday(new java.util.Date());
        return e;
    }

    private AgentEntity agentEntity(Long userId) {
        AgentEntity agent = new AgentEntity();
        agent.setId("agent-123");
        agent.setUserId(userId);
        agent.setSystemPrompt("");
        return agent;
    }

    private UserDetail userDetail(Long userId) {
        UserDetail detail = new UserDetail();
        detail.setId(userId);
        detail.setUsername("test");
        return detail;
    }

    private List<String> moodNames() {
        return java.util.Arrays.stream(CompanionMood.values()).map(Enum::name).toList();
    }
}
