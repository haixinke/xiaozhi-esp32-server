package xiaozhi.modules.pet.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.agent.dao.AiAgentChatHistoryDao;
import xiaozhi.modules.agent.dto.AgentCreateDTO;
import xiaozhi.modules.agent.entity.AgentChatHistoryEntity;
import xiaozhi.modules.agent.entity.AgentEntity;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.invite.service.InviteService;
import xiaozhi.modules.llm.service.LLMService;
import xiaozhi.modules.pet.constant.MoodLinePool;
import xiaozhi.modules.pet.constant.TodayMood;
import xiaozhi.modules.pet.dao.MemoryDao;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.dao.UserProfileDao;
import xiaozhi.modules.pet.dto.PetAdoptDTO;
import xiaozhi.modules.pet.entity.MemoryEntity;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.entity.UserProfileEntity;
import xiaozhi.modules.pet.config.PetAvatarProperties;
import xiaozhi.modules.pet.event.CollectionCardGenerationEvent;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.pet.util.MbtiParser;
import xiaozhi.modules.pet.util.MoodDecider;
import xiaozhi.modules.pet.util.PetBirthCalculator;
import xiaozhi.modules.pet.util.PetMood;
import xiaozhi.modules.pet.util.PetNicknameGenerator;
import xiaozhi.modules.pet.util.PetSystemPromptTemplate;
import xiaozhi.modules.pet.vo.ChatHistoryVO;
import xiaozhi.modules.pet.vo.MemoryVO;
import xiaozhi.modules.pet.vo.PetVO;
import xiaozhi.modules.pet.vo.UserProfileVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class PetServiceImpl extends BaseServiceImpl<PetDao, PetEntity> implements PetService {

    private final PetDao petDao;
    private final DeviceDao deviceDao;
    private final LLMService llmService;
    private final AiAgentChatHistoryDao chatHistoryDao;
    private final MemoryDao memoryDao;
    private final UserProfileDao userProfileDao;
    private final InviteService inviteService;
    private final AgentService agentService;
    private final ApplicationEventPublisher eventPublisher;
    private final PetAvatarProperties petAvatarProperties;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final long SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000;

    private static final String HATCH_STATUS_EGG = "EGG";
    private static final String HATCH_STATUS_HATCHED = "HATCHED";
    private static final String PROTOTYPE_KOI = "锦鲤";
    private static final String PROTOTYPE_RABBIT = "玉兔";
    private static final List<String> PROTOTYPES = List.of(PROTOTYPE_KOI, PROTOTYPE_RABBIT);

    private static final String BOARD_WECHAT_EGG = "wechat-egg-miniprogram";

    private static final List<String> PERSONALITY_BRIEF_POOL = List.of(
            "对零食很讲究，吃饱了才愿意思考人生。",
            "经常走错路，却总能发现隐藏宝藏。",
            "刚说完秘密，转头就问你刚才说了啥。",
            "思考三秒再行动，然后立刻推翻自己。",
            "相信童话，但会先检查糖果保质期。",
            "吵架绝不先低头，除非你先摸摸头。",
            "表面无所谓，背地里偷偷练习了一整晚。",
            "世界很大但小窝够睡，平凡也是种超能力。",
            "相信明天更好，所以先睡个午觉再说。",
            "对零食很讲究，吃饱了才有力气发呆。",
            "经常走错路，却总能撞见隐藏的夕阳。",
            "嘴上说着随便，尾巴却摇得很认真。",
            "记性不太好，但快乐的事情记得特别牢。",
            "计划总是泡汤，那就干脆享受意外。",
            "觉得童话太假，但还是会偷偷许愿。",
            "生气不会超过三秒，除非没吃到甜点。",
            "表面懒洋洋，心里却偷偷期待明天。",
            "梦想很远，但今天的布丁就在眼前。",
            "偶尔想努力，但躺平真的很舒服。",
            "对世界好奇，但出门需要心理建设。",
            "觉得长大麻烦，所以假装还没睡醒。",
            "烦恼很多，但一颗糖就能暂时忘记。",
            "想要被夸奖，却假装不在意地走开。",
            "计划赶不上变化，那就变化着计划。",
            "觉得人生艰难，但还是要好好吃饭。",
            "有时候很丧，但看到花开还是会笑。",
            "讨厌下雨天，但喜欢雨后的水坑。",
            "想变得勇敢，先从不怕黑开始练习。",
            "觉得世界复杂，所以选择简单相信。",
            "经常迷路，但迷路也有迷路的风景。",
            "想要很多爱，但给一点就能满足。",
            "觉得功课很难，但发呆是满分技能。",
            "嘴上抱怨冬天，心里期待第一场雪。",
            "相信努力有用，但更相信运气来了。",
            "有时候很固执，但给颗糖就能商量。",
            "觉得大人很累，所以慢慢长大就好。",
            "世界不完美，但今天的阳光很完美。",
            "想环游世界，但床以外都是远方。",
            "偶尔想减肥，但蛋糕不同意。",
            "觉得人生苦短，所以巧克力要快点吃。",
            "想要变聪明，但笨蛋也很快乐。",
            "觉得冬天很冷，但被窝很温柔。",
            "计划做大事，先从整理桌面开始。",
            "觉得世界吵闹，但安静也有安静的寂寞。",
            "想要很多玩具，但一个拥抱就够了。",
            "觉得长大很酷，但大人好像很累。",
            "偶尔想逃跑，但跑累了就回家。",
            "觉得星空很美，但仰望会脖子酸。",
            "想要交朋友，但社交需要充电。",
            "觉得时间很快，所以慢点吃冰淇淋。",
            "偶尔很勇敢，但怕黑是人之常情。",
            "觉得梦想很大，但小确幸更实在。",
            "想要被理解，但解释起来好麻烦。",
            "觉得夏天太热，但西瓜很懂事。",
            "偶尔想改变，但习惯真的很舒服。",
            "觉得人生无常，但今天的饭很香。",
            "觉得未来很远，但当下的风正正好。",
            "想要变厉害，但普通也有普通的可爱。",
            "觉得世界很吵，但沉默也是一种回答。",
            "偶尔想努力，但努力前先喝口热汤。",
            "觉得人生很难，但难不过没吃到甜点。",
            "想要被选中，但落选也能睡个好觉。",
            "觉得冬天太长，但春天总会来敲门。",
            "计划很多，但完成一个就很了不起。",
            "觉得大人很假，但自己也在慢慢学。",
            "想要很多星星，但手里这颗也很亮。",
            "觉得离别很痛，但重逢会更甜一点。",
            "偶尔很贪心，但分享后快乐会翻倍。",
            "觉得眼泪很咸，但哭完饭更香。",
            "想要被记住，但忘记也是种自由。",
            "觉得黑夜很长，但梦里有白天。",
            "偶尔想飞翔，但走路也能看风景。",
            "觉得承诺很重，所以只答应小事。",
            "想要变温柔，但生气时也很真实。",
            "觉得岁月无情，但皱纹里藏着故事。",
            "觉得终点很远，但每一步都算数。",
            "觉得考试很难，但倒数第一也是第一。",
            "想要变漂亮，但舒服的衣服更漂亮。",
            "觉得世界太快，所以慢慢系鞋带。",
            "偶尔想撒娇，但独立也很了不起。",
            "觉得眼泪没用，但流完会轻松一点。",
            "想要很多爱，但自爱才是必修课。",
            "觉得长大很痛，但痛完会多一点勇敢。",
            "计划赶不上，那就让计划追上来。",
            "觉得人生很空，但空杯子才能装水。",
            "想要被保护，但保护别人也很酷。",
            "觉得夏天很黏，但黏黏的西瓜很甜。",
            "偶尔很迷茫，但迷路也是种方向。",
            "觉得承诺很轻，所以不轻易说永远。",
            "想要变富有，但精神富有也算数。",
            "觉得黑夜很黑，但黑里才有星星。",
            "偶尔想放弃，但放弃前再睡一下。",
            "觉得朋友很少，但少而精很珍贵。",
            "想要被原谅，但先原谅自己再说。",
            "觉得岁月很慢，但回头已走了很远。",
            "偶尔很固执，但固执里藏着认真。",
            "觉得世界很小，小到一碗面就是家。",
            "想要变坚强，但脆弱也是种力量。",
            "觉得告别很苦，但苦后会有新甜。",
            "觉得人生很长，但此刻最重要。",
            "世界那么大，小窝够睡就好。",
            "梦想清单很长，午睡也是正事。",
            "今天的零食，明天减肥再说。",
            "发呆不是偷懒，大脑在放假。",
            "迷路也没关系，风景刚好不同。",
            "生气不过三秒，甜点来了就好。",
            "功课可以明天，拥抱必须现在。",
            "相信会有好事，所以先睡一觉。",
            "世界有点复杂，简单相信就好。",
            "眼泪流完以后，饭好像更香。",
            "想要很多星星，手里这颗也亮。",
            "冬天确实很冷，被窝足够温柔。",
            "计划常常泡汤，意外也算收获。",
            "觉得长大很累，那就慢慢长大。",
            "偶尔也想飞翔，走路也能看云。",
            "人生苦短没错，巧克力要慢吃。",
            "想要被夸奖，假装不在意走开。",
            "觉得童话太假，偷偷许愿也行。",
            "时间跑得很快，冰淇淋要慢舔。",
            "偶尔会很固执，固执里有认真。",
            "黑夜确实很长，梦里有白天。",
            "觉得离别很痛，重逢会更甜。",
            "世界不太完美，阳光今天很好。",
            "想要变勇敢，先从不怕黑开始。",
            "白日梦做久了，也会悄悄长出翅膀。",
            "总把明天挂在嘴边的人，其实最懂今天。",
            "躲在壳里看世界，不是害怕，是想看得更清。",
            "嘴上说着随便，心里早画好了小星星。",
            "慢吞吞不是错，是这个世界跑得太着急。",
            "记性差的人，反而把快乐存得更久。",
            "偶尔想逃跑，跑累了总会乖乖回家。",
            "觉得人生很难，所以更要好好吃饭。",
            "不是不想长大，是想把天真留久一点。",
            "眼泪流完以后，天空好像又亮了一点。",
            "对世界好奇，但出门确实需要勇气。",
            "计划常常泡汤，那就享受意外的汤。",
            "觉得童话太假，可还是会偷偷许愿。",
            "想要很多爱，一个拥抱就能充满。",
            "冬天确实很冷，被窝懂我的温柔。",
            "表面懒洋洋，心里早就期待明天。",
            "觉得星空很远，抬头这件事不花钱。",
            "烦恼堆成山，一颗糖就能撬开缺口。",
            "不是不勇敢，怕黑这件事需要慢慢来。",
            "觉得人生很空，空杯子才能装星光。",
            "偶尔很固执，固执里藏着我的认真。",
            "世界不完美，今天的阳光刚刚刚好。",
            "想要被记住，忘记也是一种自由。",
            "觉得岁月无情，皱纹里却住着故事。",
            "慢热但长情，认主就掏心掏肺。",
            "嘴上傲娇，行动诚实，偏爱被夸。",
            "记性好记仇少，温柔里藏着小倔强。",
            "情绪稳定的小太阳，专治雨天低气压。",
            "脑洞大开型选手，聊着聊着就跑题。",
            "务实派陪伴者，不画饼只兜底。",
            "社交牛杂症，独处也自洽。",
            "好奇星人，对一切新鲜事都想插嘴。"
    );

    private static final String MBTI_PROMPT = """
            根据以下八字和五行信息，推算这个AI宠物的MBTI人格类型。

            八字：年柱-%s，月柱-%s，日柱-%s，时柱-%s
            五行：%s

            请只回复四个字母的MBTI类型，不要其他内容。""";

    private static final String MOOD_SENTENCE_PROMPT = """
            你是一个AI陪伴宠物的内心独白写手。请根据以下信息，写一句它今天的状态文案。

            阶段：%s（孵化期=蛋，破壳后=宠物）
            今日心情：%s
            性格描述：%s
            昵称/原型：%s

            要求：
            1. 中文，20字以内，最多不超过30字
            2. 像宠物自己的状态，不像系统通知，不要鸡汤
            3. 孵化期只写壳里的动静/等待/被照顾/即将破壳，不要写尾巴/跑跳等破壳后动作
            4. 破壳后可写心情/行为/想念/今天在做什么
            5. 不要出现心情类型字样，不要emoji，不要引号
            请直接输出这一句话。""";

    private static final String MOOD_ZONE_ID = "Asia/Shanghai";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PetVO adopt(Long userId, PetAdoptDTO dto) {
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }

        // 1. 先建蛋(EGG)：不建 device/agent，不生成任何破壳档案
        //    device_id=NULL 已由 changeset 202607101500 放宽
        //    Model X: adopt 即为破壳时间基线，写 hatchStartTime=now, expectedHatchTime=now+7d
        String prototype = PROTOTYPES.get(ThreadLocalRandom.current().nextInt(PROTOTYPES.size()));
        Date now = new Date();
        PetEntity pet = new PetEntity();
        pet.setUserId(userId);
        pet.setPrototype(prototype);
        pet.setHatchStatus(HATCH_STATUS_EGG);
        pet.setHatchStartTime(now);
        pet.setExpectedHatchTime(new Date(now.getTime() + SEVEN_DAYS_MS));
        pet.setAcceleratedMinutes(0);
        pet.setCreator(userId);
        petDao.insert(pet);

        // 2. 核销邀请码(REQUIRES_NEW)。
        //    无效/过期/无剩余码会抛异常 → 外层事务回滚 → 蛋回滚，不会产生孤儿蛋。
        //    幂等：同一被邀请人对同一码重复消耗不重复扣减。
        String inviteCode = dto.getInviteCode() == null ? null : dto.getInviteCode().trim();
        if (inviteCode != null && !inviteCode.isBlank()) {
            inviteService.consume(inviteCode, userId);
        }

        log.info("蛋领养成功 userId={}, petId={}, prototype={}", userId, pet.getId(), prototype);
        return toVO(pet);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PetVO birth(String deviceId) {
        // 1. 校验设备存在且已绑定用户
        DeviceEntity device = deviceDao.selectById(deviceId);
        if (device == null || device.getUserId() == null) {
            throw new RenException(ErrorCode.PET_DEVICE_NOT_FOUND);
        }

        // 2. 使用当前时间作为出生时间
        LocalDateTime birthTime = LocalDateTime.now();

        // 3. 计算八字、五行、星座
        PetBirthCalculator.BirthResult calcResult = PetBirthCalculator.calculate(birthTime);

        // 4. 调用 LLM 推算 MBTI
        String mbti = deriveMbti(calcResult);

        // 5. 随机分配性别和血型
        String gender = ThreadLocalRandom.current().nextInt(2) == 0 ? "MALE" : "FEMALE";
        String bloodType = new String[]{"A", "B", "O", "AB"}[ThreadLocalRandom.current().nextInt(4)];

        // 6. 查询该设备是否已有宠物
        QueryWrapper<PetEntity> existWrapper = new QueryWrapper<>();
        existWrapper.eq("device_id", deviceId);
        PetEntity existingPet = petDao.selectOne(existWrapper);

        Date birthDate = Date.from(birthTime.atZone(ZoneId.systemDefault()).toInstant());

        if (existingPet != null) {
            // TODO 演示逻辑：宠物已存在时，根据当前时间重新生成昵称、五行、八字、星座和MBTI并更新，后期去掉
            String nickname = PetNicknameGenerator.generate();
            existingPet.setNickname(nickname);
            existingPet.setBirthDate(birthDate);
            existingPet.setBazi(calcResult.bazi());
            existingPet.setWuxing(calcResult.wuxing());
            existingPet.setZodiac(calcResult.zodiac());
            existingPet.setMbti(mbti);
            existingPet.setGender(gender);
            existingPet.setBloodType(bloodType);
            existingPet.setTodayMood(PetMood.random().name());
            existingPet.setUpdater(device.getUserId());
            petDao.updateById(existingPet);

            // 同步更新关联 agent 的角色设定
            updateAgentSystemPrompt(device.getAgentId(), existingPet, birthDate, calcResult, mbti);

            log.info("宠物信息已更新（演示），deviceId={}, petId={}, nickname={}", deviceId, existingPet.getId(), nickname);
            return toVO(existingPet);
        }

        // 7. 随机分配昵称
        String nickname = PetNicknameGenerator.generate();

        // 8. 创建宠物实体
        PetEntity pet = new PetEntity();
        pet.setUserId(device.getUserId());
        pet.setDeviceId(deviceId);
        pet.setNickname(nickname);
        pet.setBirthDate(birthDate);
        pet.setBazi(calcResult.bazi());
        pet.setWuxing(calcResult.wuxing());
        pet.setZodiac(calcResult.zodiac());
        pet.setMbti(mbti);
        pet.setGender(gender);
        pet.setBloodType(bloodType);
        pet.setTodayMood(PetMood.random().name());
        pet.setCreator(device.getUserId());

        petDao.insert(pet);

        // 9. 创建 agent 并注入角色设定
        AgentCreateDTO agentDto = new AgentCreateDTO();
        agentDto.setAgentName(nickname);
        String agentId = agentService.createAgent(agentDto);
        String systemPrompt = renderSystemPrompt(pet, birthDate, calcResult, mbti);
        agentService.update(null, new UpdateWrapper<AgentEntity>()
                .eq("id", agentId)
                .set("system_prompt", systemPrompt));

        log.info("宠物出生成功，deviceId={}, petId={}, nickname={}", deviceId, pet.getId(), nickname);

        return toVO(pet);
    }

    @Override
    public PetVO getByDeviceId(String deviceId) {
        QueryWrapper<PetEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("device_id", deviceId);
        PetEntity pet = petDao.selectOne(wrapper);
        if (pet == null) {
            throw new RenException(ErrorCode.PET_NOT_FOUND);
        }
        return toVO(pet);
    }

    @Override
    public List<PetVO> listByUserId(Long userId) {
        QueryWrapper<PetEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.orderByDesc("create_date");
        List<PetEntity> pets = petDao.selectList(wrapper);
        return pets.stream()
                .peek(this::refreshTodayMood)
                .map(this::toVO)
                .toList();
    }

    @Override
    public void updatePet(Long userId, String petId, String nickname) {
        PetEntity pet = petDao.selectById(petId);
        if (pet == null) {
            throw new RenException(ErrorCode.PET_NOT_FOUND);
        }
        if (!pet.getUserId().equals(userId)) {
            throw new RenException(ErrorCode.PET_NO_PERMISSION);
        }
        if (nickname != null && !nickname.isBlank()) {
            pet.setNickname(nickname);
            pet.setUpdater(userId);
            petDao.updateById(pet);
        }
    }

    @Override
    public PetVO getById(Long userId, String petId) {
        PetEntity pet = petDao.selectById(petId);
        if (pet == null) {
            throw new RenException(ErrorCode.PET_NOT_FOUND);
        }
        if (!userId.equals(pet.getUserId())) {
            throw new RenException(ErrorCode.PET_NO_PERMISSION);
        }
        refreshTodayMood(pet);
        return toVO(pet);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PetVO hatch(Long userId, String petId) {
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }

        PetEntity pet = petDao.selectByIdForUpdate(petId);
        if (pet == null) {
            throw new RenException(ErrorCode.PET_NOT_FOUND);
        }
        if (!userId.equals(pet.getUserId())) {
            throw new RenException(ErrorCode.PET_NO_PERMISSION);
        }
        if (!HATCH_STATUS_EGG.equals(pet.getHatchStatus())) {
            throw new RenException(ErrorCode.PET_ALREADY_HATCHED);
        }

        Date now = new Date();
        if (pet.getExpectedHatchTime() == null) {
            // 兜底：无动作且未设基线的蛋，按 createDate+7d 推算到点时间
            long baseTs = pet.getCreateDate() != null ? pet.getCreateDate().getTime() : now.getTime();
            pet.setExpectedHatchTime(new Date(baseTs + SEVEN_DAYS_MS));
        }
        if (now.before(pet.getExpectedHatchTime())) {
            throw new RenException(ErrorCode.PET_HATCH_TIME_NOT_REACHED);
        }

        // 命理 bazi 主导 → LLM 推 MBTI → 模板渲染 agent 系统提示词
        LocalDateTime hatchTime = LocalDateTime.now();
        PetBirthCalculator.BirthResult calc = PetBirthCalculator.calculate(hatchTime);
        String mbti = deriveMbti(calc);
        String brief = randomBrief();
        String gender = ThreadLocalRandom.current().nextInt(2) == 0 ? "MALE" : "FEMALE";
        String bloodType = new String[]{"A", "B", "O", "AB"}[ThreadLocalRandom.current().nextInt(4)];
        String avatarUrl = randomAvatarUrl(pet.getPrototype());

        // 回填宠物破壳档案（需在 agent 创建前写 gender/bloodType 以便模板渲染）
        pet.setHatchStatus(HATCH_STATUS_HATCHED);
        pet.setHatchedAt(now);
        pet.setBirthDate(now);
        pet.setBazi(calc.bazi());
        pet.setWuxing(calc.wuxing());
        pet.setZodiac(calc.zodiac());
        pet.setMbti(mbti);
        pet.setPersonalityBrief(brief);
        pet.setGender(gender);
        pet.setBloodType(bloodType);
        pet.setAvatarUrl(avatarUrl);
        pet.setUpdater(userId);

        // agent 个性注入：使用模板渲染系统提示词
        AgentCreateDTO agentDto = new AgentCreateDTO();
        agentDto.setAgentName(StringUtils.isBlank(pet.getNickname()) ? pet.getPrototype() : pet.getNickname());
        String agentId = agentService.createAgent(agentDto);
        String systemPrompt = renderSystemPrompt(pet, now, calc, mbti);
        agentService.update(null, new UpdateWrapper<AgentEntity>()
                .eq("id", agentId)
                .set("system_prompt", systemPrompt));

        // 手动建蛋设备：macAddress 必须等于 id，否则 OTA 查不到
        DeviceEntity device = new DeviceEntity();
        String deviceId = IdUtil.simpleUUID();
        device.setId(deviceId);
        device.setMacAddress(deviceId);
        device.setUserId(userId);
        device.setBoard(BOARD_WECHAT_EGG);
        device.setAlias(pet.getNickname());
        device.setAgentId(agentId);
        device.setAppVersion("1.0.0");
        device.setAutoUpdate(0);
        device.setCreator(userId);
        deviceDao.insert(device);

        // 回填设备ID
        pet.setDeviceId(deviceId);
        petDao.updateById(pet);

        // [暂时禁用] 破壳后异步调用豆包极梦(Seedream)生成动态收藏卡片并回写ai_pet表
        // eventPublisher.publishEvent(new CollectionCardGenerationEvent(pet.getId()));

        log.info("蛋破壳 userId={}, petId={}, deviceId={}, agentId={}", userId, petId, deviceId, agentId);
        return toVO(pet);
    }

    /**
     * 按原型从配置中随机取一张默认头像 URL。
     */
    private String randomAvatarUrl(String prototype) {
        return petAvatarProperties.randomAvatarUrl(prototype);
    }

    /**
     * 性格卡片语：内置一组不同卡片，随机取，不调 LLM，不绑 MBTI。
     */
    private String randomBrief() {
        return PERSONALITY_BRIEF_POOL.get(ThreadLocalRandom.current().nextInt(PERSONALITY_BRIEF_POOL.size()));
    }

    private String deriveMbti(PetBirthCalculator.BirthResult calcResult) {
        try {
            if (!llmService.isAvailable()) {
                log.warn("LLM服务不可用，使用默认MBTI");
                return "INFP";
            }

            JsonNode baziNode = MAPPER.readTree(calcResult.bazi());
            String year = baziNode.get("year").asText();
            String month = baziNode.get("month").asText();
            String day = baziNode.get("day").asText();
            String hour = baziNode.get("hour").asText();

            JsonNode wuxingNode = MAPPER.readTree(calcResult.wuxing());
            String wuxingDisplay = "金-" + wuxingNode.get("metal").asInt()
                    + "，木-" + wuxingNode.get("wood").asInt()
                    + "，水-" + wuxingNode.get("water").asInt()
                    + "，火-" + wuxingNode.get("fire").asInt()
                    + "，土-" + wuxingNode.get("earth").asInt();

            String prompt = String.format(MBTI_PROMPT, year, month, day, hour, wuxingDisplay);

            String response = llmService.generateSummary("", prompt);
            return MbtiParser.parse(response);
        } catch (Exception e) {
            log.error("LLM推算MBTI失败，使用默认值", e);
            return "INFP";
        }
    }

    private String renderSystemPrompt(PetEntity pet, Date birthDate, PetBirthCalculator.BirthResult calc, String mbti) {
        String nickname = StringUtils.isBlank(pet.getNickname()) ? pet.getPrototype() : pet.getNickname();
        return PetSystemPromptTemplate.render(
                nickname,
                birthDate,
                calc.bazi(),
                calc.wuxing(),
                calc.zodiac(),
                mbti,
                pet.getPrototype(),
                pet.getGender(),
                pet.getBloodType()
        );
    }

    private void updateAgentSystemPrompt(String agentId, PetEntity pet, Date birthDate,
                                         PetBirthCalculator.BirthResult calc, String mbti) {
        if (StringUtils.isBlank(agentId)) {
            log.warn("宠物无关联 agent，跳过角色设定更新，petId={}", pet.getId());
            return;
        }
        String systemPrompt = renderSystemPrompt(pet, birthDate, calc, mbti);
        agentService.update(null, new UpdateWrapper<AgentEntity>()
                .eq("id", agentId)
                .set("system_prompt", systemPrompt));
    }

    @Override
    public PetVO toVO(PetEntity pet) {
        PetVO vo = new PetVO();
        vo.setId(pet.getId());
        vo.setUserId(pet.getUserId());
        vo.setDeviceId(pet.getDeviceId());
        if (StringUtils.isNotBlank(pet.getDeviceId())) {
            DeviceEntity device = deviceDao.selectById(pet.getDeviceId());
            if (device != null) {
                vo.setAgentId(device.getAgentId());
            }
        }
        vo.setNickname(pet.getNickname());
        vo.setBirthDate(pet.getBirthDate());
        vo.setBazi(pet.getBazi());
        vo.setWuxing(pet.getWuxing());
        vo.setZodiac(pet.getZodiac());
        vo.setMbti(pet.getMbti());
        vo.setPersonality(pet.getPersonality());
        vo.setTodayMood(pet.getTodayMood());
        vo.setHatchStatus(pet.getHatchStatus());
        vo.setHatchStartTime(pet.getHatchStartTime());
        vo.setExpectedHatchTime(pet.getExpectedHatchTime());
        vo.setHatchedAt(pet.getHatchedAt());
        vo.setAcceleratedMinutes(pet.getAcceleratedMinutes());
        vo.setAvatarUrl(pet.getAvatarUrl());
        vo.setPrototype(pet.getPrototype());
        vo.setGender(pet.getGender());
        vo.setBloodType(pet.getBloodType());
        vo.setPersonalityBrief(pet.getPersonalityBrief());
        vo.setTodayMoodDate(pet.getTodayMoodDate());
        vo.setTodayMoodSentence(pet.getTodayMoodSentence());
        vo.setCreateDate(pet.getCreateDate());
        return vo;
    }

    @Override
    public void refreshTodayMood(PetEntity pet) {
        if (pet == null) {
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.of(MOOD_ZONE_ID));
        if (today.equals(pet.getTodayMoodDate()) && pet.getTodayMood() != null) {
            return;
        }

        long now = System.currentTimeMillis();
        long baselineMs = MoodDecider.baseline(pet, now);
        TodayMood mood = MoodDecider.decide(pet, baselineMs, now);
        String sentence = generateMoodSentence(pet, mood, today);

        // 幂等写回：仅当今日未生成时更新，防并发双写
        UpdateWrapper<PetEntity> uw = new UpdateWrapper<>();
        uw.eq("id", pet.getId())
                .and(w -> w.isNull("today_mood_date").or().ne("today_mood_date", today))
                .set("today_mood", mood.getLabel())
                .set("today_mood_date", today)
                .set("today_mood_sentence", sentence);
        petDao.update(null, uw);

        // 本地反射，保证本次返回的 VO 一致
        pet.setTodayMood(mood.getLabel());
        pet.setTodayMoodDate(today);
        pet.setTodayMoodSentence(sentence);
    }

    /**
     * 生成今日心情一句话：LLM 生成，失败/不可用则用静态文案池兜底（PRD §8.4）。
     */
    private String generateMoodSentence(PetEntity pet, TodayMood mood, LocalDate today) {
        boolean hatched = HATCH_STATUS_HATCHED.equals(pet.getHatchStatus());
        String stage = hatched ? "破壳后" : "孵化期";
        String personality = StringUtils.isNotBlank(pet.getPersonalityBrief())
                ? pet.getPersonalityBrief()
                : (StringUtils.isNotBlank(pet.getMbti()) ? pet.getMbti() : "未知");
        String identity = StringUtils.isNotBlank(pet.getNickname())
                ? pet.getNickname()
                : (StringUtils.isNotBlank(pet.getPrototype()) ? pet.getPrototype() : "蛋宝宝");

        try {
            if (llmService.isAvailable()) {
                String prompt = String.format(MOOD_SENTENCE_PROMPT, stage, mood.getLabel(), personality, identity);
                String resp = llmService.generateSummary("", prompt);
                if (resp != null && !resp.isBlank() && !resp.contains("失败")) {
                    String s = resp.trim().replaceAll("[\"“”‘’]", "");
                    if (s.length() > 30) {
                        s = s.substring(0, 30);
                    }
                    return s;
                }
            }
        } catch (Exception e) {
            log.warn("LLM生成今日心情文案失败，使用静态兜底", e);
        }
        return MoodLinePool.pick(hatched, mood, today.toString());
    }

    @Override
    public PageData<ChatHistoryVO> getChatHistoryByMacAddress(String macAddress, Map<String, Object> params) {
        int page = Integer.parseInt(params.get(Constant.PAGE).toString());
        int limit = Integer.parseInt(params.get(Constant.LIMIT).toString());

        // 构建查询条件
        QueryWrapper<AgentChatHistoryEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("mac_address", macAddress)
                .orderByDesc("created_at");

        // 执行分页查询
        Page<AgentChatHistoryEntity> pageParam = new Page<>(page, limit);
        IPage<AgentChatHistoryEntity> result = chatHistoryDao.selectPage(pageParam, wrapper);

        // 转换为VO
        List<ChatHistoryVO> records = result.getRecords().stream().map(entity -> {
            ChatHistoryVO vo = new ChatHistoryVO();
            vo.setId(entity.getId());
            vo.setSessionId(entity.getSessionId());
            vo.setChatType(entity.getChatType());
            vo.setContent(entity.getContent());
            vo.setAudioId(entity.getAudioId());
            vo.setCreatedAt(entity.getCreatedAt());
            return vo;
        }).toList();

        return new PageData<>(records, result.getTotal());
    }

    @Override
    public PageData<MemoryVO> getMemoryByDeviceId(String deviceId, Map<String, Object> params) {
        int page = Integer.parseInt(params.get(Constant.PAGE).toString());
        int limit = Integer.parseInt(params.get(Constant.LIMIT).toString());

        // 构建查询条件
        QueryWrapper<MemoryEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", deviceId)
                .orderByDesc("created_at");

        // 执行分页查询
        Page<MemoryEntity> pageParam = new Page<>(page, limit);
        IPage<MemoryEntity> result = memoryDao.selectPage(pageParam, wrapper);

        // 转换为VO
        List<MemoryVO> records = result.getRecords().stream()
                .map(this::toMemoryVO)
                .toList();

        return new PageData<>(records, result.getTotal());
    }

    /**
     * 转换MemoryEntity为MemoryVO
     *
     * @param entity MemoryEntity
     * @return MemoryVO
     */
    private MemoryVO toMemoryVO(MemoryEntity entity) {
        MemoryVO vo = new MemoryVO();
        vo.setId(entity.getId());
        vo.setCategory(entity.getCategory());
        vo.setDocument(entity.getDocument());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    @Override
    public UserProfileVO getUserProfileByDeviceId(String deviceId) {
        // 构建查询条件 - 查询最新的一条用户画像
        QueryWrapper<UserProfileEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", deviceId)
                .orderByDesc("created_at")
                .last("LIMIT 1");

        // 执行查询
        UserProfileEntity entity = userProfileDao.selectOne(wrapper);

        // 如果没有找到，返回null
        if (entity == null) {
            return null;
        }

        // 转换为VO
        return toUserProfileVO(entity);
    }

    /**
     * 转换UserProfileEntity为UserProfileVO
     *
     * @param entity UserProfileEntity
     * @return UserProfileVO
     */
    private UserProfileVO toUserProfileVO(UserProfileEntity entity) {
        UserProfileVO vo = new UserProfileVO();
        vo.setId(entity.getId());
        vo.setProfileContent(entity.getProfileContent());
        vo.setTopics(entity.getTopics());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
