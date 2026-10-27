package xiaozhi.modules.smallobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.common.config.TestArkServiceConfig;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.modules.smallobject.dao.SmallObjectDao;
import xiaozhi.modules.smallobject.dto.SmallObjectDTO;
import xiaozhi.modules.smallobject.entity.SmallObjectEntity;
import xiaozhi.modules.smallobject.service.SmallObjectService;
import xiaozhi.modules.smallobject.vo.SmallObjectTypeVO;
import xiaozhi.modules.smallobject.vo.SmallObjectVO;
import xiaozhi.modules.sys.dao.SysDictDataDao;
import xiaozhi.modules.sys.dao.SysDictTypeDao;
import xiaozhi.modules.sys.entity.SysDictDataEntity;
import xiaozhi.modules.sys.entity.SysDictTypeEntity;

/**
 * 小物件只读列表 Service 集成测试（真实 dev 库）。
 * 每个用例自造字典小类与物件数据，AfterEach 物理清理（软删行同样清理），
 * 避免污染 dev 库与后续用例。
 */
@Import(TestArkServiceConfig.class)
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("小物件管理只读列表集成测试")
class SmallObjectServiceTest {

    @Autowired
    private SmallObjectService smallObjectService;
    @Autowired
    private SmallObjectDao smallObjectDao;
    @Autowired
    private SysDictDataDao sysDictDataDao;
    @Autowired
    private SysDictTypeDao sysDictTypeDao;
    @Autowired
    private RedisUtils redisUtils;

    /** 本用例创建的物件 id（含已软删的行，物理清理） */
    private final List<Long> objectIds = new ArrayList<>();
    /** 本用例创建的字典小类 id */
    private final List<Long> dictDataIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        objectIds.forEach(smallObjectDao::deleteById);
        objectIds.clear();
        dictDataIds.forEach(sysDictDataDao::deleteById);
        dictDataIds.clear();
        // 字典列表有 Redis 缓存，用例间必须清掉，否则后续用例读到旧的小类快照
        evictDictCache();
    }

    private void evictDictCache() {
        redisUtils.delete(RedisKeys.getDictDataByTypeKey(Constant.DictType.SMALL_OBJECT_TYPE.getValue()));
    }

    /** SMALL_OBJECT_TYPE 字典大类由 Liquibase 迁移创建，用例直接在其下挂小类 */
    private Long dictTypeId() {
        SysDictTypeEntity type = sysDictTypeDao.selectOne(
                new QueryWrapper<SysDictTypeEntity>().eq("dict_type", Constant.DictType.SMALL_OBJECT_TYPE.getValue()));
        assertThat(type).as("SMALL_OBJECT_TYPE 字典大类应随迁移自动创建").isNotNull();
        return type.getId();
    }

    /** 新建一个物件类型小类，返回其 dict_value */
    private String createObjectType(String dictValue, String dictLabel, int sort) {
        SysDictDataEntity entity = new SysDictDataEntity();
        entity.setDictTypeId(dictTypeId());
        entity.setDictValue(dictValue);
        entity.setDictLabel(dictLabel);
        entity.setSort(sort);
        sysDictDataDao.insert(entity);
        dictDataIds.add(entity.getId());
        evictDictCache();
        return dictValue;
    }

    /** 直接落库一条物件记录，返回 id；deleted=1 用于验证列表过滤 */
    private Long createObject(String objectType, int status, int deleted) {
        SmallObjectEntity entity = new SmallObjectEntity();
        entity.setObjectType(objectType);
        entity.setImageUrl("https://oss.example.com/small-object/" + objectType + ".png");
        entity.setStory("物件「" + objectType + "」背后的故事");
        entity.setStatus(status);
        entity.setDeleted(deleted);
        smallObjectDao.insert(entity);
        objectIds.add(entity.getId());
        return entity.getId();
    }

    private PageData<SmallObjectVO> pageAll() {
        Map<String, Object> params = new HashMap<>();
        params.put(Constant.PAGE, "1");
        params.put(Constant.LIMIT, "200");
        return smallObjectService.page(params);
    }

    // ---- 分页 ----

    @Test
    @DisplayName("page 空页请求：返回空列表而非报错")
    void page_emptyPage_returnsEmptyList() {
        // 请求远超数据量的页码，走空记录分支
        Map<String, Object> params = new HashMap<>();
        params.put(Constant.PAGE, "999999");
        params.put(Constant.LIMIT, "10");

        PageData<SmallObjectVO> page = smallObjectService.page(params);

        assertThat(page).isNotNull();
        assertThat(page.getList()).isEmpty();
        assertThat(page.getTotal()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("page 只返回未删除记录：deleted=1 不出现，total 也不计入")
    void page_excludesDeletedRecords() {
        // 基线：库里可能已有别的数据，只比较相对增量
        int totalBefore = pageAll().getTotal();
        String type = createObjectType("TEST_TYPE_VISIBLE", "可见类型", 1);
        Long visibleId = createObject(type, 1, 0);
        String deletedType = createObjectType("TEST_TYPE_DELETED", "已删类型", 2);
        Long deletedId = createObject(deletedType, 1, 1);

        PageData<SmallObjectVO> page = pageAll();

        List<Long> ids = page.getList().stream().map(SmallObjectVO::getId).toList();
        assertThat(ids).contains(visibleId);
        assertThat(ids).doesNotContain(deletedId);
        // 两条记录只应有一条计入总数，否则前端会多出永远翻不到数据的空白页
        assertThat(page.getTotal()).isEqualTo(totalBefore + 1);
    }

    @Test
    @DisplayName("page 按小类的字典 sort 升序排列，与创建顺序无关")
    void page_ordersByDictSort() {
        String typeC = createObjectType("TEST_TYPE_SORT_C", "类型C", 3);
        String typeA = createObjectType("TEST_TYPE_SORT_A", "类型A", 1);
        String typeB = createObjectType("TEST_TYPE_SORT_B", "类型B", 2);
        // 故意按 C、A、B 的顺序落库，验证排序依据是字典 sort 而不是 id
        Long idC = createObject(typeC, 1, 0);
        Long idA = createObject(typeA, 1, 0);
        Long idB = createObject(typeB, 1, 0);

        PageData<SmallObjectVO> page = pageAll();

        List<Long> sortedIds = page.getList().stream().map(SmallObjectVO::getId)
                .filter(id -> id.equals(idA) || id.equals(idB) || id.equals(idC))
                .toList();
        assertThat(sortedIds).containsExactly(idA, idB, idC);
    }

    @Test
    @DisplayName("调用方传入 orderField：不生效，排序仍由字典 sort 决定")
    void page_ignoresClientSuppliedOrderField() {
        String typeC = createObjectType("TEST_TYPE_ORDER_C", "排序C", 3);
        String typeA = createObjectType("TEST_TYPE_ORDER_A", "排序A", 1);
        Long idC = createObject(typeC, 1, 0);
        Long idA = createObject(typeA, 1, 0);

        Map<String, Object> params = new HashMap<>();
        params.put(Constant.PAGE, "1");
        params.put(Constant.LIMIT, "200");
        // 试图用 id 倒序改写列表顺序
        params.put(Constant.ORDER_FIELD, "id");
        params.put(Constant.ORDER, "desc");

        List<Long> sortedIds = smallObjectService.page(params).getList().stream()
                .map(SmallObjectVO::getId)
                .filter(id -> id.equals(idA) || id.equals(idC))
                .toList();

        // 字典 sort 决定顺序：A(1) 在 C(3) 之前，与传入的 id 倒序无关
        assertThat(sortedIds).containsExactly(idA, idC);
    }

    // ---- 详情 ----

    @Test
    @DisplayName("get 不存在的 id：抛出物件不存在")
    void get_missingId_throwsNotFound() {
        assertThatThrownBy(() -> smallObjectService.get(-1L))
                .isInstanceOf(RenException.class)
                .extracting(e -> ((RenException) e).getCode())
                .isEqualTo(ErrorCode.SMALL_OBJECT_NOT_FOUND);
    }

    @Test
    @DisplayName("get 已软删的记录：按不存在处理")
    void get_deletedRecord_throwsNotFound() {
        String type = createObjectType("TEST_TYPE_GET_DELETED", "已删详情类型", 1);
        Long deletedId = createObject(type, 1, 1);

        assertThatThrownBy(() -> smallObjectService.get(deletedId))
                .isInstanceOf(RenException.class)
                .extracting(e -> ((RenException) e).getCode())
                .isEqualTo(ErrorCode.SMALL_OBJECT_NOT_FOUND);
    }

    // ---- 类型名称翻译 ----

    @Test
    @DisplayName("get 类型名称由字典 dict_label 翻译")
    void get_translatesTypeNameFromDict() {
        String type = createObjectType("TEST_TYPE_TRANSLATE", "会翻译的类型", 1);
        Long id = createObject(type, 1, 0);

        SmallObjectVO vo = smallObjectService.get(id);

        assertThat(vo.getObjectType()).isEqualTo(type);
        assertThat(vo.getObjectTypeName()).isEqualTo("会翻译的类型");
        assertThat(vo.getImageUrl()).isNotBlank();
        assertThat(vo.getStory()).isNotBlank();
        assertThat(vo.getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("get 字典中不存在的 code：回落显示原始 dict_value")
    void get_unknownTypeCode_fallsBackToRawValue() {
        Long id = createObject("TEST_TYPE_UNKNOWN_CODE", 1, 0);

        SmallObjectVO vo = smallObjectService.get(id);

        assertThat(vo.getObjectTypeName()).isEqualTo("TEST_TYPE_UNKNOWN_CODE");
    }

    @Test
    @DisplayName("page 列表中的类型名称同样按字典翻译")
    void page_translatesTypeNameFromDict() {
        String type = createObjectType("TEST_TYPE_PAGE_TRANSLATE", "列表翻译类型", 1);
        Long id = createObject(type, 1, 0);

        SmallObjectVO vo = pageAll().getList().stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElseThrow();

        assertThat(vo.getObjectTypeName()).isEqualTo("列表翻译类型");
    }

    @Test
    @DisplayName("字典小类重命名后：列表随字典实时翻译，不落库存名称")
    void page_reflectsDictRename() {
        String type = createObjectType("TEST_TYPE_RENAME", "改名前", 1);
        Long id = createObject(type, 1, 0);

        // 运营在字典管理页把小类显示名改掉
        SysDictDataEntity dictData = sysDictDataDao.selectById(dictDataIds.get(0));
        dictData.setDictLabel("改名后");
        sysDictDataDao.updateById(dictData);
        evictDictCache();

        SmallObjectVO vo = pageAll().getList().stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElseThrow();

        assertThat(vo.getObjectTypeName()).isEqualTo("改名后");
    }

    @Test
    @DisplayName("字典里调整小类排序后：列表顺序随之变化，无需二次维护")
    void page_followsDictSortChange() {
        String typeFirst = createObjectType("TEST_TYPE_REORDER_A", "重排A", 1);
        String typeSecond = createObjectType("TEST_TYPE_REORDER_B", "重排B", 2);
        Long idFirst = createObject(typeFirst, 1, 0);
        Long idSecond = createObject(typeSecond, 1, 0);

        // 运营在字典管理页把 B 排到 A 前面
        SysDictDataEntity sortA = sysDictDataDao.selectById(dictDataIds.get(0));
        SysDictDataEntity sortB = sysDictDataDao.selectById(dictDataIds.get(1));
        sortA.setSort(2);
        sortB.setSort(1);
        sysDictDataDao.updateById(sortA);
        sysDictDataDao.updateById(sortB);
        evictDictCache();

        List<Long> sortedIds = pageAll().getList().stream().map(SmallObjectVO::getId)
                .filter(id -> id.equals(idFirst) || id.equals(idSecond))
                .toList();

        assertThat(sortedIds).containsExactly(idSecond, idFirst);
    }

    @Test
    @DisplayName("字典小类被删除后：物件仍在列表中，类型名称回落为原始 code")
    void page_survivesDictEntryDeletion() {
        String type = createObjectType("TEST_TYPE_DICT_GONE", "将被删除的类型", 1);
        Long id = createObject(type, 1, 0);

        // 模拟运营在字典管理里删掉该小类（系统不做引用检查，属已知缺口）
        sysDictDataDao.deleteById(dictDataIds.get(0));
        dictDataIds.clear();
        evictDictCache();

        SmallObjectVO vo = pageAll().getList().stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElseThrow();

        assertThat(vo.getObjectTypeName()).isEqualTo("TEST_TYPE_DICT_GONE");
    }

    // ---- 新增 ----

    /** 构造合法的新增 DTO，单个用例可按需覆盖字段 */
    private SmallObjectDTO validDTO(String objectType) {
        SmallObjectDTO dto = new SmallObjectDTO();
        dto.setObjectType(objectType);
        dto.setImageUrl("https://oss.example.com/small-object/42/abc.png");
        dto.setStory("这是一段物件背后的故事");
        return dto;
    }

    /** 保存并登记新行 id，供 AfterEach 物理清理 */
    private Long saveAndTrack(SmallObjectDTO dto) {
        smallObjectService.save(dto);
        SmallObjectEntity entity = smallObjectDao.selectOne(new QueryWrapper<SmallObjectEntity>()
                .eq("object_type", dto.getObjectType()).orderByDesc("id").last("LIMIT 1"));
        assertThat(entity).isNotNull();
        objectIds.add(entity.getId());
        return entity.getId();
    }

    @Test
    @DisplayName("save 正常新增：图片与故事正确落库，默认启用且未删除")
    void save_normal_persistsImageAndStory() {
        String type = createObjectType("TEST_TYPE_SAVE_OK", "可保存类型", 1);
        SmallObjectDTO dto = validDTO(type);

        Long id = saveAndTrack(dto);

        SmallObjectEntity saved = smallObjectDao.selectById(id);
        assertThat(saved.getObjectType()).isEqualTo(type);
        assertThat(saved.getImageUrl()).isEqualTo("https://oss.example.com/small-object/42/abc.png");
        assertThat(saved.getStory()).isEqualTo("这是一段物件背后的故事");
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getDeleted()).isZero();
    }

    @Test
    @DisplayName("save 同一类型第二次保存：被拒绝并返回类型已被占用")
    void save_duplicateType_rejected() {
        String type = createObjectType("TEST_TYPE_SAVE_DUP", "重复类型", 1);
        saveAndTrack(validDTO(type));

        assertThatThrownBy(() -> smallObjectService.save(validDTO(type)))
                .isInstanceOf(RenException.class)
                .extracting(e -> ((RenException) e).getCode())
                .isEqualTo(ErrorCode.SMALL_OBJECT_TYPE_ALREADY_USED);
    }

    @Test
    @DisplayName("save 类型不在 SMALL_OBJECT_TYPE 字典中：被拒绝并返回类型不存在")
    void save_unknownType_rejected() {
        SmallObjectDTO dto = validDTO("TEST_TYPE_NOT_IN_DICT");

        assertThatThrownBy(() -> smallObjectService.save(dto))
                .isInstanceOf(RenException.class)
                .extracting(e -> ((RenException) e).getCode())
                .isEqualTo(ErrorCode.SMALL_OBJECT_TYPE_NOT_EXIST);
    }

    @Test
    @DisplayName("save 故事超过 2000 字：被拒绝，且不落库")
    void save_storyTooLong_rejected() {
        String type = createObjectType("TEST_TYPE_SAVE_LONG", "超长故事类型", 1);
        SmallObjectDTO dto = validDTO(type);
        dto.setStory("字".repeat(2001));

        assertThatThrownBy(() -> smallObjectService.save(dto))
                .isInstanceOf(RenException.class);
        assertNoRecordSaved(type);
    }

    @Test
    @DisplayName("save 故事正好 2000 字：允许保存（边界值不被误杀）")
    void save_storyAtMaxLength_allowed() {
        String type = createObjectType("TEST_TYPE_SAVE_MAX", "边界故事类型", 1);
        SmallObjectDTO dto = validDTO(type);
        dto.setStory("字".repeat(2000));

        Long id = saveAndTrack(dto);

        assertThat(smallObjectDao.selectById(id).getStory()).hasSize(2000);
    }

    @Test
    @DisplayName("save 图片为空：被拒绝，且不落库")
    void save_blankImage_rejected() {
        String type = createObjectType("TEST_TYPE_SAVE_NO_IMG", "缺图类型", 1);
        SmallObjectDTO dto = validDTO(type);
        dto.setImageUrl(" ");

        assertThatThrownBy(() -> smallObjectService.save(dto))
                .isInstanceOf(RenException.class);
        assertNoRecordSaved(type);
    }

    @Test
    @DisplayName("save 故事为空：被拒绝，且不落库")
    void save_blankStory_rejected() {
        String type = createObjectType("TEST_TYPE_SAVE_NO_STORY", "缺故事类型", 1);
        SmallObjectDTO dto = validDTO(type);
        dto.setStory("  ");

        assertThatThrownBy(() -> smallObjectService.save(dto))
                .isInstanceOf(RenException.class);
        assertNoRecordSaved(type);
    }

    /** 断言某类型在未删除记录中没有落库，防止校验失败却写入了半条数据 */
    private void assertNoRecordSaved(String objectType) {
        Long count = smallObjectDao.selectCount(new QueryWrapper<SmallObjectEntity>()
                .eq("object_type", objectType).eq("deleted", 0));
        assertThat(count).isZero();
    }

    // ---- 可选物件类型 ----

    @Test
    @DisplayName("object-types 返回字典小类的 key/name，并标记未占用者为 used=false")
    void objectTypes_marksUnusedType() {
        String type = createObjectType("TEST_TYPE_OPT_FREE", "空闲类型", 1);

        SmallObjectTypeVO option = smallObjectService.listObjectTypes().stream()
                .filter(item -> type.equals(item.getKey()))
                .findFirst()
                .orElseThrow();

        assertThat(option.getName()).isEqualTo("空闲类型");
        assertThat(option.getUsed()).isFalse();
    }

    @Test
    @DisplayName("object-types 已被未删除物件占用的类型标记 used=true")
    void objectTypes_marksUsedType() {
        String type = createObjectType("TEST_TYPE_OPT_USED", "占用类型", 1);
        createObject(type, 1, 0);

        SmallObjectTypeVO option = smallObjectService.listObjectTypes().stream()
                .filter(item -> type.equals(item.getKey()))
                .findFirst()
                .orElseThrow();

        assertThat(option.getUsed()).isTrue();
    }

    @Test
    @DisplayName("object-types 已软删的记录不算占用：类型重新可选")
    void objectTypes_ignoresDeletedRecords() {
        String type = createObjectType("TEST_TYPE_OPT_DELETED", "已删占用类型", 1);
        createObject(type, 1, 1);

        SmallObjectTypeVO option = smallObjectService.listObjectTypes().stream()
                .filter(item -> type.equals(item.getKey()))
                .findFirst()
                .orElseThrow();

        assertThat(option.getUsed()).isFalse();
    }

    @Test
    @DisplayName("object-types 只包含 SMALL_OBJECT_TYPE 小类，不混入其他字典大类")
    void objectTypes_onlyContainsSmallObjectTypes() {
        String type = createObjectType("TEST_TYPE_OPT_SCOPE", "作用域类型", 1);
        // 在另一个字典大类下挂一个小类，验证它不会混进物件类型下拉
        SysDictTypeEntity otherType = sysDictTypeDao.selectOne(
                new QueryWrapper<SysDictTypeEntity>()
                        .ne("dict_type", Constant.DictType.SMALL_OBJECT_TYPE.getValue())
                        .last("LIMIT 1"));
        assertThat(otherType).as("dev 库应至少存在另一个字典大类").isNotNull();
        SysDictDataEntity foreign = new SysDictDataEntity();
        foreign.setDictTypeId(otherType.getId());
        foreign.setDictValue("TEST_TYPE_FOREIGN_SCOPE");
        foreign.setDictLabel("外域小类");
        foreign.setSort(1);
        sysDictDataDao.insert(foreign);
        dictDataIds.add(foreign.getId());

        List<SmallObjectTypeVO> options = smallObjectService.listObjectTypes();

        assertThat(options).extracting(SmallObjectTypeVO::getKey).contains(type);
        assertThat(options).extracting(SmallObjectTypeVO::getKey).doesNotContain("TEST_TYPE_FOREIGN_SCOPE");
    }
}
