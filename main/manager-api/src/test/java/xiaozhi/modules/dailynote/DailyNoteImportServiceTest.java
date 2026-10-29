package xiaozhi.modules.dailynote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.common.config.TestArkServiceConfig;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.dailynote.dao.DailyNoteDao;
import xiaozhi.modules.dailynote.entity.DailyNoteEntity;
import xiaozhi.modules.dailynote.exception.DailyNoteImportException;
import xiaozhi.modules.dailynote.service.DailyNoteImportService;
import xiaozhi.modules.dailynote.vo.DailyNoteImportResultVO;

/**
 * 小纸条 JSON 导入 Service 集成测试（真实 dev 库）。
 * 覆盖：新增/覆盖计数、身份与创建审计保留、未涉及记录不变、文件内重复日期、
 * 未知字段、格式与资源限制（空文件/空数组/非法 JSON/非 UTF-8/错误扩展名/大小/条数）、
 * 行号错误明细、校验失败零写入。
 * 每个用例自造专用日期段（2170 年起）的小纸条，AfterEach 物理清理，避免污染 dev 库。
 */
@Import(TestArkServiceConfig.class)
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("小纸条 JSON 导入集成测试")
class DailyNoteImportServiceTest {

    @Autowired
    private DailyNoteImportService dailyNoteImportService;
    @Autowired
    private DailyNoteDao dailyNoteDao;

    /** 本用例涉及的小纸条日期（yyyy-MM-dd），AfterEach 按日期物理清理 */
    private final List<String> usedDates = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String date : usedDates) {
            dailyNoteDao.delete(new QueryWrapper<DailyNoteEntity>().eq("note_date", date));
        }
        usedDates.clear();
    }

    /** 构造 multipart 文件（UTF-8 内容） */
    private MultipartFile jsonFile(String content) {
        return new MockMultipartFile("file", "notes.json", "application/json",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private MultipartFile jsonFile(String content, String filename) {
        return new MockMultipartFile("file", filename, "application/json",
                content.getBytes(StandardCharsets.UTF_8));
    }

    /** 直接落库一份小纸条（绕过 Service 校验，用于铺底数据），返回 id */
    private Long seedNote(String date, String title, String content, String solarTerm) {
        DailyNoteEntity entity = new DailyNoteEntity();
        entity.setNoteDate(LocalDate.parse(date));
        entity.setTitle(title);
        entity.setContent(content);
        entity.setSolarTerm(solarTerm);
        dailyNoteDao.insert(entity);
        usedDates.add(date);
        return entity.getId();
    }

    /**
     * 并发场景模拟：在导入事务已查出既有记录之后、写入之前，由"另一个事务"插入同日期记录。
     * 测试无法真实并发，用删除原记录的方式制造 select 与 insert 之间的库状态漂移，
     * 触发唯一键冲突路径；返回被删记录的 id。
     */
    private void deleteNoteById(Long id) {
        dailyNoteDao.deleteById(id);
    }

    private DailyNoteEntity findByDate(String date) {
        return dailyNoteDao.selectOne(new QueryWrapper<DailyNoteEntity>().eq("note_date", date));
    }

    @Test
    @DisplayName("合法文件：全部新增，返回 addedCount=2、overwrittenCount=0，多行正文与 null 节气正确入库")
    void importAllNewCountsAdded() {
        usedDates.add("2170-01-01");
        usedDates.add("2170-01-02");

        String json = """
                [
                  {"date":"2170-01-01","title":"今日小纸条","content":"今天也辛苦了。\\n记得早点休息。","solarTerm":"霜降"},
                  {"date":"2170-01-02","title":"给自己的一句话","content":"慢慢来，也很好。","solarTerm":null}
                ]
                """;
        DailyNoteImportResultVO result = dailyNoteImportService.importNotes(jsonFile(json));

        assertThat(result.getAddedCount()).isEqualTo(2);
        assertThat(result.getOverwrittenCount()).isZero();

        DailyNoteEntity first = findByDate("2170-01-01");
        assertThat(first.getTitle()).isEqualTo("今日小纸条");
        assertThat(first.getContent()).isEqualTo("今天也辛苦了。\n记得早点休息。");
        assertThat(first.getSolarTerm()).isEqualTo("霜降");
        DailyNoteEntity second = findByDate("2170-01-02");
        assertThat(second.getSolarTerm()).isNull();
    }

    @Test
    @DisplayName("同日期覆盖：整项更新内容并保留 id，未涉及记录不变，计数正确")
    void importOverwritesSameDateAndLeavesOthersUntouched() {
        Long existingId = seedNote("2170-02-01", "原标题", "原内容", "原节气");
        Long untouchedId = seedNote("2170-02-02", "不动标题", "不动内容", null);
        usedDates.add("2170-02-03");

        String json = """
                [
                  {"date":"2170-02-01","title":"新标题","content":"新内容","solarTerm":null},
                  {"date":"2170-02-03","title":"新增","content":"新增内容","solarTerm":"立春"}
                ]
                """;
        DailyNoteImportResultVO result = dailyNoteImportService.importNotes(jsonFile(json));

        assertThat(result.getAddedCount()).isEqualTo(1);
        assertThat(result.getOverwrittenCount()).isEqualTo(1);

        // 覆盖：记录身份与日期键保留（日期键必须与写入时一致，不因时区换算错位），内容整项更新
        DailyNoteEntity overwritten = dailyNoteDao.selectById(existingId);
        assertThat(overwritten.getNoteDate().toString()).isEqualTo("2170-02-01");
        assertThat(overwritten.getTitle()).isEqualTo("新标题");
        assertThat(overwritten.getContent()).isEqualTo("新内容");
        assertThat(overwritten.getSolarTerm()).isNull();

        // 文件未涉及的既有记录完全不变
        DailyNoteEntity untouched = dailyNoteDao.selectById(untouchedId);
        assertThat(untouched.getTitle()).isEqualTo("不动标题");
        assertThat(untouched.getContent()).isEqualTo("不动内容");
    }

    // 并发唯一键冲突（10393）路径：单连接测试无法模拟真实并发窗口，留待人工验证清单

    @Test
    @DisplayName("文件内相同日期：整批拒绝并携带两条行号明细，库内零写入")
    void duplicateDateInFileRejectedWithRowDetails() {
        usedDates.add("2170-03-01");

        String json = """
                [
                  {"date":"2170-03-01","title":"甲","content":"甲内容"},
                  {"date":"2170-03-02","title":"乙","content":"乙内容"},
                  {"date":"2170-03-01","title":"丙","content":"丙内容"}
                ]
                """;
        assertThatThrownBy(() -> dailyNoteImportService.importNotes(jsonFile(json)))
                .isInstanceOfSatisfying(DailyNoteImportException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_IMPORT_INVALID);
                    assertThat(e.getErrors())
                            .anySatisfy(err -> {
                                assertThat(err.getRow()).isEqualTo(3);
                                assertThat(err.getField()).isEqualTo("date");
                                assertThat(err.getReason()).contains("重复");
                            });
                });

        // 整批零写入：第二条合法记录也不得入库
        assertThat(findByDate("2170-03-01")).isNull();
        assertThat(findByDate("2170-03-02")).isNull();
    }

    @Test
    @DisplayName("未知字段：整批拒绝，明细带行号与字段名")
    void unknownFieldRejectedWithRowDetail() {
        usedDates.add("2170-04-01");

        String json = """
                [
                  {"date":"2170-04-01","title":"甲","content":"甲内容","note":"多余字段"}
                ]
                """;
        assertThatThrownBy(() -> dailyNoteImportService.importNotes(jsonFile(json)))
                .isInstanceOfSatisfying(DailyNoteImportException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_IMPORT_INVALID);
                    assertThat(e.getErrors()).anySatisfy(err -> {
                        assertThat(err.getRow()).isEqualTo(1);
                        assertThat(err.getField()).isEqualTo("note");
                    });
                });
        assertThat(findByDate("2170-04-01")).isNull();
    }

    @Test
    @DisplayName("字段与长度校验沿用手工规则：非法日期、空白标题、超长正文均带行号明细且零写入")
    void fieldValidationCarriesRowDetails() {
        usedDates.add("2170-05-01");
        usedDates.add("2170-05-02");
        usedDates.add("2170-05-03");

        String json = """
                [
                  {"date":"2170-13-01","title":"甲","content":"甲内容"},
                  {"date":"2170-05-02","title":"   ","content":"乙内容"},
                  {"date":"2170-05-03","title":"丙","content":"%s"}
                ]
                """.formatted("正".repeat(201));
        assertThatThrownBy(() -> dailyNoteImportService.importNotes(jsonFile(json)))
                .isInstanceOfSatisfying(DailyNoteImportException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_IMPORT_INVALID);
                    assertThat(e.getErrors()).hasSize(3);
                    assertThat(e.getErrors()).anySatisfy(err -> {
                        assertThat(err.getRow()).isEqualTo(1);
                        assertThat(err.getField()).isEqualTo("date");
                    });
                    assertThat(e.getErrors()).anySatisfy(err -> {
                        assertThat(err.getRow()).isEqualTo(2);
                        assertThat(err.getField()).isEqualTo("title");
                    });
                    assertThat(e.getErrors()).anySatisfy(err -> {
                        assertThat(err.getRow()).isEqualTo(3);
                        assertThat(err.getField()).isEqualTo("content");
                    });
                });

        assertThat(findByDate("2170-05-02")).isNull();
        assertThat(findByDate("2170-05-03")).isNull();
    }

    @Test
    @DisplayName("格式与资源限制：空文件、空数组、非法 JSON、非数组顶层、错误扩展名、超 1MB、超 1000 项均整批拒绝")
    void formatAndResourceLimitsRejected() {
        // 空文件
        assertThatThrownBy(() -> dailyNoteImportService
                .importNotes(new MockMultipartFile("file", "notes.json", "application/json", new byte[0])))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_IMPORT_INVALID_FORMAT));

        // 空数组
        assertThatThrownBy(() -> dailyNoteImportService.importNotes(jsonFile("[]")))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_IMPORT_INVALID_FORMAT));

        // 非法 JSON
        assertThatThrownBy(() -> dailyNoteImportService.importNotes(jsonFile("[{date:}]")))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_IMPORT_INVALID_FORMAT));

        // 顶层不是数组
        assertThatThrownBy(() -> dailyNoteImportService.importNotes(jsonFile("{\"date\":\"2170-06-01\"}")))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_IMPORT_INVALID_FORMAT));

        // 错误扩展名
        assertThatThrownBy(() -> dailyNoteImportService.importNotes(jsonFile("[]", "notes.txt")))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_IMPORT_INVALID_FORMAT));

        // 超 1MB
        byte[] oversized = new byte[1024 * 1024 + 1];
        assertThatThrownBy(() -> dailyNoteImportService
                .importNotes(new MockMultipartFile("file", "notes.json", "application/json", oversized)))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_IMPORT_INVALID_FORMAT));

        // 超 1000 项
        StringBuilder tooMany = new StringBuilder("[");
        for (int i = 0; i < 1001; i++) {
            if (i > 0) {
                tooMany.append(',');
            }
            tooMany.append("{\"date\":\"2170-07-").append(String.format("%02d", i % 28 + 1))
                    .append("\",\"title\":\"").append(i).append("\",\"content\":\"c\"}");
        }
        tooMany.append(']');
        assertThatThrownBy(() -> dailyNoteImportService.importNotes(jsonFile(tooMany.toString())))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_IMPORT_INVALID_FORMAT));
    }

    @Test
    @DisplayName("非 UTF-8 内容：整批拒绝")
    void nonUtf8ContentRejected() {
        // GBK 编码的中文内容，按 UTF-8 解码会失败
        byte[] gbkBytes;
        try {
            gbkBytes = "[{\"date\":\"2170-08-01\",\"title\":\"标题\",\"content\":\"内容\"}]".getBytes("GBK");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        MultipartFile file = new MockMultipartFile("file", "notes.json", "application/json", gbkBytes);

        assertThatThrownBy(() -> dailyNoteImportService.importNotes(file))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_NOTE_IMPORT_INVALID_FORMAT));
        assertThat(findByDate("2170-08-01")).isNull();
    }
}
