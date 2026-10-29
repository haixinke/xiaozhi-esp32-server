package xiaozhi.modules.dailynote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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

import xiaozhi.common.config.TestArkServiceConfig;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.modules.dailynote.dao.DailyNoteDao;
import xiaozhi.modules.dailynote.dto.DailyNoteSaveDTO;
import xiaozhi.modules.dailynote.entity.DailyNoteEntity;
import xiaozhi.modules.dailynote.service.DailyNoteService;
import xiaozhi.modules.dailynote.vo.DailyNoteVO;

/**
 * 小纸条管理 Service 集成测试（真实 dev 库）。
 * 覆盖：分页与日期过滤、新增编辑（trim、长度、日期冲突含排除自身）、物理删除。
 * 每个用例自造专用日期段（2160 年起）的小纸条，AfterEach 物理清理，避免污染 dev 库与后续用例。
 */
@Import(TestArkServiceConfig.class)
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("小纸条管理集成测试")
class DailyNoteServiceTest {

    @Autowired
    private DailyNoteService dailyNoteService;
    @Autowired
    private DailyNoteDao dailyNoteDao;

    /** 本用例创建的小纸条 id */
    private final List<Long> noteIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        noteIds.forEach(dailyNoteDao::deleteById);
        noteIds.clear();
    }

    /** 通过 Service 新增一份小纸条并登记清理，返回 id */
    private Long createNote(String date, String title, String content, String solarTerm) {
        DailyNoteSaveDTO dto = new DailyNoteSaveDTO();
        dto.setNoteDate(date);
        dto.setTitle(title);
        dto.setContent(content);
        dto.setSolarTerm(solarTerm);
        dailyNoteService.create(dto);
        // create 不回传 id（编辑自身冲突判定走数据库），按日期查回
        DailyNoteVO vo = dailyNoteService.page(paramsWithDate(date)).getList().get(0);
        noteIds.add(vo.getId());
        return vo.getId();
    }

    private Map<String, Object> paramsWithDate(String date) {
        Map<String, Object> params = new HashMap<>();
        params.put(Constant.PAGE, "1");
        params.put(Constant.LIMIT, "10");
        params.put("noteDate", date);
        return params;
    }

    private Map<String, Object> pageParams() {
        Map<String, Object> params = new HashMap<>();
        params.put(Constant.PAGE, "1");
        params.put(Constant.LIMIT, "100");
        return params;
    }

    @Test
    @DisplayName("新增后按日期精确过滤可查，三个文本字段去首尾空白、正文内部换行保留")
    void createTrimsFieldsAndPreservesInnerNewlines() {
        Long id = createNote("2160-01-01", "  标题  ", "  第一行\n第二行  ", "  霜降  ");

        DailyNoteVO vo = dailyNoteService.get(id);

        assertThat(vo.getNoteDate()).isEqualTo(java.sql.Date.valueOf("2160-01-01"));
        assertThat(vo.getTitle()).isEqualTo("标题");
        assertThat(vo.getContent()).isEqualTo("第一行\n第二行");
        assertThat(vo.getSolarTerm()).isEqualTo("霜降");
        // 日期精确过滤：命中当日，不命中相邻日
        assertThat(dailyNoteService.page(paramsWithDate("2160-01-01")).getTotal()).isEqualTo(1);
        assertThat(dailyNoteService.page(paramsWithDate("2160-01-02")).getTotal()).isZero();
    }

    @Test
    @DisplayName("分页列表默认按日期倒序")
    void pageOrdersByNoteDateDesc() {
        Long older = createNote("2160-02-01", "较早", "较早内容", null);
        Long newer = createNote("2160-02-03", "较新", "较新内容", null);
        createNote("2160-02-02", "中间", "中间内容", null);

        PageData<DailyNoteVO> page = dailyNoteService.page(pageParams());

        List<DailyNoteVO> created = page.getList().stream()
                .filter(vo -> noteIds.contains(vo.getId()))
                .toList();
        assertThat(created).hasSize(3);
        // 倒序：最新日期在前
        assertThat(created.get(0).getId()).isEqualTo(newer);
        assertThat(created.get(2).getId()).isEqualTo(older);
        assertThat(created.get(0).getUpdateDate()).isNotNull();
    }

    @Test
    @DisplayName("新增重复日期被拒绝；编辑改为其他已有日期被拒绝；编辑自身日期不算冲突")
    void duplicateDateIsRejectedExceptSelf() {
        Long first = createNote("2160-03-01", "甲", "甲内容", null);
        Long second = createNote("2160-03-02", "乙", "乙内容", null);

        // 新增同日期 → 拒绝
        DailyNoteSaveDTO duplicate = new DailyNoteSaveDTO();
        duplicate.setNoteDate("2160-03-01");
        duplicate.setTitle("丙");
        duplicate.setContent("丙内容");
        assertThatThrownBy(() -> dailyNoteService.create(duplicate))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_DATE_CONFLICT));

        // 编辑第二条改成第一条的日期 → 拒绝
        DailyNoteSaveDTO conflictingUpdate = new DailyNoteSaveDTO();
        conflictingUpdate.setNoteDate("2160-03-01");
        conflictingUpdate.setTitle("乙改");
        conflictingUpdate.setContent("乙改内容");
        assertThatThrownBy(() -> dailyNoteService.update(second, conflictingUpdate))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_DATE_CONFLICT));

        // 编辑自身日期不变 → 允许，内容更新生效
        DailyNoteSaveDTO selfUpdate = new DailyNoteSaveDTO();
        selfUpdate.setNoteDate("2160-03-01");
        selfUpdate.setTitle("甲改");
        selfUpdate.setContent("甲改内容");
        selfUpdate.setSolarTerm("立春");
        dailyNoteService.update(first, selfUpdate);
        DailyNoteVO updated = dailyNoteService.get(first);
        assertThat(updated.getTitle()).isEqualTo("甲改");
        assertThat(updated.getSolarTerm()).isEqualTo("立春");
    }

    @Test
    @DisplayName("编辑可改到新日期并更新内容字段")
    void updateMovesToNewDate() {
        Long id = createNote("2160-04-01", "原标题", "原内容", null);

        DailyNoteSaveDTO dto = new DailyNoteSaveDTO();
        dto.setNoteDate("2160-04-05");
        dto.setTitle("新标题");
        dto.setContent("新内容");
        dto.setSolarTerm("  ");
        dailyNoteService.update(id, dto);

        DailyNoteVO vo = dailyNoteService.get(id);
        assertThat(vo.getNoteDate()).isEqualTo(java.sql.Date.valueOf("2160-04-05"));
        assertThat(vo.getTitle()).isEqualTo("新标题");
        assertThat(vo.getContent()).isEqualTo("新内容");
        // 节气纯空白视为未填，存 null
        assertThat(vo.getSolarTerm()).isNull();
    }

    @Test
    @DisplayName("非法日期与不存在的日期被拒绝")
    void invalidDatesAreRejected() {
        DailyNoteSaveDTO notADate = new DailyNoteSaveDTO();
        notADate.setNoteDate("2026/01/01");
        notADate.setTitle("标题");
        notADate.setContent("内容");
        assertThatThrownBy(() -> dailyNoteService.create(notADate))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_INVALID_DATE));

        DailyNoteSaveDTO impossibleDate = new DailyNoteSaveDTO();
        impossibleDate.setNoteDate("2160-02-30");
        impossibleDate.setTitle("标题");
        impossibleDate.setContent("内容");
        assertThatThrownBy(() -> dailyNoteService.create(impossibleDate))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_INVALID_DATE));

        // 过滤参数中的非法日期同样明确拒绝，而不是静默当作无过滤
        assertThatThrownBy(() -> dailyNoteService.page(paramsWithDate("2160-13-01")))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_INVALID_DATE));
    }

    @Test
    @DisplayName("空白必填被拒绝；长度按 Unicode 字符数校验（边界值通过/超限拒绝）")
    void blankAndLengthBoundaries() {
        // 标题纯空白 → 拒绝
        DailyNoteSaveDTO blankTitle = new DailyNoteSaveDTO();
        blankTitle.setNoteDate("2160-05-01");
        blankTitle.setTitle("   ");
        blankTitle.setContent("内容");
        assertThatThrownBy(() -> dailyNoteService.create(blankTitle))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_TITLE_REQUIRED));

        // 正文缺失 → 拒绝
        DailyNoteSaveDTO blankContent = new DailyNoteSaveDTO();
        blankContent.setNoteDate("2160-05-01");
        blankContent.setTitle("标题");
        blankContent.setContent("");
        assertThatThrownBy(() -> dailyNoteService.create(blankContent))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_CONTENT_REQUIRED));

        // 标题 51 字 → 拒绝；恰好 50 字（含多字节字符按 Unicode 字符计）→ 通过
        DailyNoteSaveDTO longTitle = new DailyNoteSaveDTO();
        longTitle.setNoteDate("2160-05-01");
        longTitle.setTitle("标".repeat(51));
        longTitle.setContent("内容");
        assertThatThrownBy(() -> dailyNoteService.create(longTitle))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_TITLE_TOO_LONG));

        // 正文 201 字 → 拒绝
        DailyNoteSaveDTO longContent = new DailyNoteSaveDTO();
        longContent.setNoteDate("2160-05-01");
        longContent.setTitle("标题");
        longContent.setContent("正".repeat(201));
        assertThatThrownBy(() -> dailyNoteService.create(longContent))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_CONTENT_TOO_LONG));

        // 节气 51 字 → 拒绝
        DailyNoteSaveDTO longSolarTerm = new DailyNoteSaveDTO();
        longSolarTerm.setNoteDate("2160-05-01");
        longSolarTerm.setTitle("标题");
        longSolarTerm.setContent("内容");
        longSolarTerm.setSolarTerm("节".repeat(51));
        assertThatThrownBy(() -> dailyNoteService.create(longSolarTerm))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_SOLAR_TERM_TOO_LONG));

        // 边界值全部通过：标题 50、正文 200、节气 50
        Long id = createNote("2160-05-01", "标".repeat(50), "正".repeat(200), "节".repeat(50));
        DailyNoteVO vo = dailyNoteService.get(id);
        assertThat(vo.getTitle()).hasSize(50);
        assertThat(vo.getContent()).hasSize(200);
        assertThat(vo.getSolarTerm()).hasSize(50);
    }

    @Test
    @DisplayName("物理删除后按 ID 与日期过滤均查不到；重复删除与不存在的记录报错")
    void deleteIsPhysicalAndIdempotentFailureIsExplicit() {
        Long id = createNote("2160-06-01", "标题", "内容", null);

        dailyNoteService.delete(id);

        assertThatThrownBy(() -> dailyNoteService.get(id))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_NOT_FOUND));
        assertThat(dailyNoteService.page(paramsWithDate("2160-06-01")).getTotal()).isZero();
        // 物理删除：行不存在，不是软删标记
        assertThat(dailyNoteDao.selectById(id)).isNull();

        assertThatThrownBy(() -> dailyNoteService.delete(id))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_NOT_FOUND));
    }

    @Test
    @DisplayName("编辑不存在的记录报错")
    void updateMissingNoteIsRejected() {
        DailyNoteSaveDTO dto = new DailyNoteSaveDTO();
        dto.setNoteDate("2160-07-01");
        dto.setTitle("标题");
        dto.setContent("内容");
        assertThatThrownBy(() -> dailyNoteService.update(-1L, dto))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_NOT_FOUND));
    }
}
